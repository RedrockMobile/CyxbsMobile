package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.model.NpmDownloadException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * 从 App 私有 debug 目录按需读取本地 npm tgz，并将其投影为 registry metadata。
 *
 * ```text
 * metadata 请求 ──> 查找 debug/<scope>/<name>.tgz ──> 读取 package.json 与计算 SRI
 *       │                                                │
 *       └────────────── 合并远端 metadata <───────────────┘
 *                                  │
 *                                  ▼
 *                    latest 指向 semver 更高的候选版本
 *
 * 虚拟 tarball 请求 https://cyxbs.local.debug/... ──> 直接返回同一 tgz
 * ```
 *
 * 本类不扫描目录、不持有包索引，也不删除文件。ADB 每次覆盖同包名文件，HTTP 请求到达时再读取，
 * 因而安装新的 debug bundle 后重启 App 即可进入正常 npm 解析链路。
 */
internal class DebugNpmPackageSource(
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val rootDirectory: Path = DEFAULT_ROOT_DIRECTORY,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {

  /** metadata 请求使用的 abbreviated Accept 值，其他 JSON 请求不会被本地 bundle 劫持。 */
  fun isMetadataRequest(headers: Map<String, String>): Boolean {
    return headers.entries.any { (name, value) ->
      name.equals("Accept", ignoreCase = true) && METADATA_ACCEPT in value
    }
  }

  /** 从 registry metadata URL 提取包名；URL 结构异常时返回 null 并继续普通网络请求。 */
  fun packageNameFromMetadataUrl(url: String): String? {
    val encoded = url.substringBefore('?').substringAfterLast('/', missingDelimiterValue = "")
    return encoded.takeIf(String::isNotEmpty)?.let(::decodeUrlComponent)
  }

  /** 虚拟 URL 只承担把已合成 metadata 中的 tarball 地址映射回本地 tgz。 */
  fun packageNameFromLocalTarballUrl(url: String): String? {
    if (!url.startsWith(LOCAL_TARBALL_BASE_URL)) return null
    val encoded = url.removePrefix(LOCAL_TARBALL_BASE_URL)
      .substringBefore('?')
      .removeSuffix(TARBALL_SUFFIX)
    return encoded.takeIf(String::isNotEmpty)?.let(::decodeUrlComponent)
  }

  /**
   * 读取本地 tgz；不存在时返回 null，存在但 package.json 非法时直接失败，避免静默运行线上旧包。
   */
  fun read(packageName: String): DebugNpmPackage? {
    val archivePath = archivePath(packageName) ?: return null
    if (!fileSystem.exists(archivePath)) return null
    return try {
      val archiveBytes = fileSystem.read(archivePath) { readByteArray() }
      val packageJsonBytes = readNpmPackageFiles(fileSystem, archivePath)[PACKAGE_JSON]
        ?: throw NpmDownloadException("Local debug npm package has no package.json.")
      val packageJson = json.parseToJsonElement(packageJsonBytes.decodeToString()).jsonObject
      val actualName = packageJson["name"]?.jsonPrimitive?.content
      val version = packageJson["version"]?.jsonPrimitive?.content
      if (actualName != packageName || version == null || !DEBUG_VERSION.matches(version) ||
        NpmSemver.parseOrNull(version) == null
      ) {
        throw NpmDownloadException(
          "Local debug npm package identity or version is invalid for '$packageName'.",
        )
      }
      DebugNpmPackage(
        name = packageName,
        version = version,
        integrity = "sha512-${archiveBytes.toByteString().sha512().base64()}",
        archiveBytes = archiveBytes,
        packageJson = packageJson,
      )
    } catch (exception: NpmDownloadException) {
      throw exception
    } catch (throwable: Throwable) {
      throw NpmDownloadException("Failed to read local debug npm package '$packageName'.", throwable)
    }
  }

  /**
   * 将本地版本加入远端 metadata，并让 latest 指向两者中 semver 更高的版本。
   *
   * 远端不可用时 [remoteMetadata] 可以为空；此时返回仅包含本地版本的完整 metadata。
   */
  fun mergeMetadata(
    packageInfo: DebugNpmPackage,
    remoteMetadata: ByteArray?,
  ): ByteArray {
    val remote = remoteMetadata?.let { bytes ->
      try {
        json.parseToJsonElement(bytes.decodeToString()).jsonObject
      } catch (throwable: Throwable) {
        throw NpmDownloadException("Npm registry returned invalid package metadata.", throwable)
      }
    }
    val remoteVersions = remote?.get("versions") as? JsonObject ?: JsonObject(emptyMap())
    val remoteTags = remote?.get("dist-tags") as? JsonObject ?: JsonObject(emptyMap())
    val remoteLatest = remoteTags["latest"]?.jsonPrimitive?.content
    val latest = listOfNotNull(remoteLatest, packageInfo.version)
      .maxBy { value -> NpmSemver.parseOrNull(value) ?: MINIMUM_SEMVER }
    val localVersion = buildJsonObject {
      put("name", packageInfo.name)
      put("version", packageInfo.version)
      packageInfo.packageJson["dependencies"]?.let { put("dependencies", it) }
      put("dist", buildJsonObject {
        put("integrity", packageInfo.integrity)
        put("tarball", localTarballUrl(packageInfo.name))
      })
    }
    return buildJsonObject {
      remote?.forEach { (key, value) ->
        if (key != "name" && key != "versions" && key != "dist-tags") put(key, value)
      }
      put("name", packageInfo.name)
      put("dist-tags", buildJsonObject {
        remoteTags.forEach { (key, value) -> put(key, value) }
        put("latest", latest)
      })
      put("versions", buildJsonObject {
        remoteVersions.forEach { (key, value) -> put(key, value) }
        put(packageInfo.version, localVersion)
      })
    }.toString().encodeToByteArray()
  }

  /** 包名只允许 npm 作用域结构，拒绝利用路径片段越出固定 debug 目录。 */
  private fun archivePath(packageName: String): Path? {
    if (!PACKAGE_NAME.matches(packageName)) return null
    val segments = packageName.split('/')
    return when (segments.size) {
      1 -> rootDirectory / "${segments[0]}$TARBALL_SUFFIX"
      2 -> rootDirectory / segments[0] / "${segments[1]}$TARBALL_SUFFIX"
      else -> null
    }
  }

  private fun localTarballUrl(packageName: String): String {
    return "$LOCAL_TARBALL_BASE_URL${packageName.encodeNpmPathSegment()}$TARBALL_SUFFIX"
  }

  private companion object {
    val DEFAULT_ROOT_DIRECTORY: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
      "cyxbs-code" / "npm" / "debug"
    const val LOCAL_TARBALL_BASE_URL = "https://cyxbs.local.debug/"
    const val PACKAGE_JSON = "package.json"
    const val TARBALL_SUFFIX = ".tgz"
    const val METADATA_ACCEPT = "application/vnd.npm.install-v1+json"
    val MINIMUM_SEMVER = NpmSemver(0, 0, 0, listOf("0"))
    val DEBUG_VERSION = Regex("""(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)-debug\.\d{14}""")
    val PACKAGE_NAME = Regex("""(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*""")
  }
}

/** 已从本地 tgz 校验出的 debug 包；SRI 基于实际归档字节生成。 */
internal class DebugNpmPackage(
  val name: String,
  val version: String,
  val integrity: String,
  val archiveBytes: ByteArray,
  val packageJson: JsonObject,
)

/** 解码 npm metadata 路径中的百分号编码；非法编码返回原文本，随后会被包名校验拒绝。 */
private fun decodeUrlComponent(value: String): String {
  val output = mutableListOf<Byte>()
  var index = 0
  while (index < value.length) {
    val char = value[index]
    if (char == '%' && index + 2 < value.length) {
      val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
      if (byte != null) {
        output += byte.toByte()
        index += 3
        continue
      }
    }
    output += char.toString().encodeToByteArray().asList()
    index++
  }
  return output.toByteArray().decodeToString()
}
