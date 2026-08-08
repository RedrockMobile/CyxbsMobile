package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.model.NpmModuleResolutionException
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.gzip

/** 从已完成 SRI 校验的 npm tgz 中读取普通文件，不落地解包目录。 */
internal fun readNpmPackageFiles(
  fileSystem: FileSystem,
  archivePath: Path,
): Map<String, ByteArray> {
  val source = fileSystem.source(archivePath).gzip().buffer()
  return try {
    readTarEntries(source)
  } finally {
    source.close()
  }
}

/**
 * 读取 npm 使用的 ustar/pax 归档。
 *
 * 只接受 `package/` 下的普通文件；目录和链接不会进入结果，也不会在宿主文件系统上创建目标路径。
 */
private fun readTarEntries(source: BufferedSource): Map<String, ByteArray> {
  val files = linkedMapOf<String, ByteArray>()
  var pendingLongPath: String? = null
  var pendingPaxPath: String? = null
  while (!source.exhausted()) {
    if (!source.request(TAR_BLOCK_SIZE)) {
      throw NpmModuleResolutionException("Npm tar archive ended inside an entry header.")
    }
    val header = source.readByteArray(TAR_BLOCK_SIZE)
    if (header.all { it == 0.toByte() }) break

    val size = parseTarSize(header)
    val payload = source.readByteArray(size)
    val padding = (TAR_BLOCK_SIZE - size % TAR_BLOCK_SIZE) % TAR_BLOCK_SIZE
    if (padding > 0) source.skip(padding)

    val type = header[TYPE_OFFSET].toInt().toChar()
    when (type) {
      TYPE_GNU_LONG_PATH -> pendingLongPath = payload.decodeToString().trimEnd('\u0000', '\n')
      TYPE_PAX_HEADER -> pendingPaxPath = parsePaxPath(payload) ?: pendingPaxPath
      TYPE_GLOBAL_PAX_HEADER -> Unit
      else -> {
        val archivePath = pendingPaxPath ?: pendingLongPath ?: readTarPath(header)
        pendingPaxPath = null
        pendingLongPath = null
        if (type == TYPE_REGULAR_FILE || type == TYPE_OLD_REGULAR_FILE) {
          val packagePath = normalizeNpmArchivePath(archivePath)
          if (files.put(packagePath, payload) != null) {
            throw NpmModuleResolutionException(
              "Npm tar archive contains duplicate file '$packagePath'.",
            )
          }
        }
      }
    }
  }
  return files
}

/** 组合 ustar 的 prefix 与 name 字段。 */
private fun readTarPath(header: ByteArray): String {
  val name = header.readTarText(NAME_OFFSET, NAME_LENGTH)
  val prefix = header.readTarText(PREFIX_OFFSET, PREFIX_LENGTH)
  return if (prefix.isEmpty()) name else "$prefix/$name"
}

/** 读取 tar 八进制文件长度；npm 包不需要 GNU base-256 长度扩展。 */
private fun parseTarSize(header: ByteArray): Long {
  val encoded = header.readTarText(SIZE_OFFSET, SIZE_LENGTH).trim()
  if (encoded.isEmpty()) return 0L
  return encoded.toLongOrNull(radix = 8)
    ?: throw NpmModuleResolutionException("Npm tar archive contains an invalid file size.")
}

/** 读取 pax 扩展头中的 path，其他键由当前 Module 场景忽略。 */
private fun parsePaxPath(payload: ByteArray): String? {
  return payload.decodeToString().lineSequence().firstNotNullOfOrNull { record ->
    val value = record.substringAfter(' ', missingDelimiterValue = "")
    value.removePrefix("path=").takeIf { candidate ->
      value.startsWith("path=") && candidate.isNotEmpty()
    }
  }
}

/** 移除 npm 固定的 package 根目录并拒绝绝对路径与路径穿越。 */
private fun normalizeNpmArchivePath(path: String): String {
  val normalizedSeparators = path.replace('\\', '/')
  if (!normalizedSeparators.startsWith(NPM_ARCHIVE_ROOT)) {
    throw NpmModuleResolutionException("Npm tar entry is outside the package root.")
  }
  val relative = normalizedSeparators.removePrefix(NPM_ARCHIVE_ROOT)
  val segments = relative.split('/')
  if (relative.isEmpty() || segments.any { it.isEmpty() || it == "." || it == ".." }) {
    throw NpmModuleResolutionException("Npm tar entry contains an invalid package path.")
  }
  return segments.joinToString("/")
}

/** 读取以 NUL 结尾的 tar ASCII/UTF-8 文本字段。 */
private fun ByteArray.readTarText(offset: Int, length: Int): String {
  val end = (offset until (offset + length)).firstOrNull { this[it] == 0.toByte() }
    ?: offset + length
  return copyOfRange(offset, end).decodeToString()
}

private const val TAR_BLOCK_SIZE = 512L
private const val NAME_OFFSET = 0
private const val NAME_LENGTH = 100
private const val SIZE_OFFSET = 124
private const val SIZE_LENGTH = 12
private const val TYPE_OFFSET = 156
private const val PREFIX_OFFSET = 345
private const val PREFIX_LENGTH = 155
private const val NPM_ARCHIVE_ROOT = "package/"
private const val TYPE_OLD_REGULAR_FILE = '\u0000'
private const val TYPE_REGULAR_FILE = '0'
private const val TYPE_PAX_HEADER = 'x'
private const val TYPE_GLOBAL_PAX_HEADER = 'g'
private const val TYPE_GNU_LONG_PATH = 'L'
