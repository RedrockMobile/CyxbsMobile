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
 * 从平台 debug 目录按需读取本地 npm tgz，并将其投影为 registry metadata。
 *
 * ```text
 * metadata 请求 ──> 查找 debug/<scope>/<name>/<version>.tgz ──> 读取 package.json 与计算 SRI
 *       │                                                │
 *       └────────────── 合并远端 metadata <───────────────┘
 *                                  │
 *                                  ▼
 *                    latest 指向 semver 更高的候选版本
 *
 * 虚拟 tarball 请求 https://cyxbs.local.debug/... ──> 直接返回同一 tgz
 * ```
 *
 * 本类不持有常驻包索引，也不删除文件。Android 由 ADB 写入 App 私有目录；Desktop 直接读取
 * 根项目的构建目录。每次 metadata 请求只扫描目标包的版本目录，因此多个入口在不同时间打包时，
 * 它们依赖的历史 debug 版本仍能共存并被精确下载。
 */
internal class DebugNpmPackageSource(
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val rootDirectory: Path? = defaultDebugNpmPackageRootDirectory(),
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

  /** 虚拟 URL 同时携带包名和精确版本，避免下载阶段重新选择到同包的其他本地版本。 */
  fun packageFromLocalTarballUrl(url: String): DebugNpmPackageCoordinate? {
    if (!url.startsWith(LOCAL_TARBALL_BASE_URL)) return null
    val path = url.removePrefix(LOCAL_TARBALL_BASE_URL).substringBefore('?')
    if (!path.endsWith(TARBALL_SUFFIX)) return null
    val encodedName = path.substringBeforeLast('/', missingDelimiterValue = "")
    val encodedVersion = path.substringAfterLast('/').removeSuffix(TARBALL_SUFFIX)
    if (encodedName.isEmpty() || encodedVersion.isEmpty()) return null
    val packageName = decodeUrlComponent(encodedName)
    val version = decodeUrlComponent(encodedVersion)
    if (!PACKAGE_NAME.matches(packageName) || !LOCAL_VERSION.matches(version)) return null
    return DebugNpmPackageCoordinate(packageName, version)
  }

  /**
   * 读取指定版本的本地 tgz；不存在时返回 null，归档身份非法时直接失败，避免静默运行错误版本。
   */
  fun read(packageName: String, version: String): DebugNpmPackage? {
    val archivePath = archivePath(packageName, version) ?: return null
    if (!fileSystem.exists(archivePath)) return null
    return readArchive(packageName, version, archivePath)
  }

  /**
   * 按版本文件名稳定排序读取目标包的全部归档；其他包目录不会被扫描。
   *
   * 目录中非 tgz 文件会被忽略，存在但损坏或身份不一致的 tgz 会直接失败，防止 metadata
   * 部分发布后继续回退到网络版本而掩盖本地构建问题。
   */
  fun readAll(packageName: String): List<DebugNpmPackage> {
    val directory = archiveDirectory(packageName) ?: return emptyList()
    return fileSystem.listOrNull(directory)
      .orEmpty()
      .filter { path -> path.name.endsWith(TARBALL_SUFFIX) }
      .sortedBy(Path::name)
      .map { archivePath ->
        val version = archivePath.name.removeSuffix(TARBALL_SUFFIX)
        if (!LOCAL_VERSION.matches(version)) {
          throw NpmDownloadException(
            "Local debug npm archive has an invalid version filename for '$packageName'.",
          )
        }
        readArchive(packageName, version, archivePath)
      }
  }

  /** 从一个已定位的归档读取并交叉校验目录坐标与 package.json 身份。 */
  private fun readArchive(
    packageName: String,
    expectedVersion: String,
    archivePath: Path,
  ): DebugNpmPackage {
    return try {
      val archiveBytes = fileSystem.read(archivePath) { readByteArray() }
      val packageJsonBytes = readNpmPackageFiles(fileSystem, archivePath)[PACKAGE_JSON]
        ?: throw NpmDownloadException("Local debug npm package has no package.json.")
      val packageJson = json.parseToJsonElement(packageJsonBytes.decodeToString()).jsonObject
      val actualName = packageJson["name"]?.jsonPrimitive?.content
      val version = packageJson["version"]?.jsonPrimitive?.content
      if (actualName != packageName || version != expectedVersion ||
        !LOCAL_VERSION.matches(expectedVersion) ||
        NpmSemver.parseOrNull(version) == null
      ) {
        throw NpmDownloadException(
          "Local debug npm package identity is invalid for '$packageName@$expectedVersion'.",
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
      throw NpmDownloadException(
        "Failed to read local debug npm package '$packageName@$expectedVersion'.",
        throwable,
      )
    }
  }

  /**
   * 将本地全部版本加入远端 metadata，并让 latest 指向所有候选中 semver 更高的版本。
   *
   * 远端不可用时 [remoteMetadata] 可以为空；此时返回仅包含本地版本的完整 metadata。
   */
  fun mergeMetadata(
    packageInfos: List<DebugNpmPackage>,
    remoteMetadata: ByteArray?,
  ): ByteArray {
    require(packageInfos.isNotEmpty()) { "At least one local debug npm package is required." }
    val packageName = packageInfos.first().name
    require(packageInfos.all { it.name == packageName }) {
      "Local debug npm metadata can only merge versions of the same package."
    }
    require(packageInfos.map(DebugNpmPackage::version).distinct().size == packageInfos.size) {
      "Local debug npm metadata contains duplicate versions for '$packageName'."
    }
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
    val latest = (listOfNotNull(remoteLatest) + packageInfos.map(DebugNpmPackage::version))
      .maxBy { value -> NpmSemver.parseOrNull(value) ?: MINIMUM_SEMVER }
    return buildJsonObject {
      remote?.forEach { (key, value) ->
        if (key != "name" && key != "versions" && key != "dist-tags") put(key, value)
      }
      put("name", packageName)
      put("dist-tags", buildJsonObject {
        remoteTags.forEach { (key, value) -> put(key, value) }
        put("latest", latest)
      })
      put("versions", buildJsonObject {
        remoteVersions.forEach { (key, value) -> put(key, value) }
        packageInfos.forEach { packageInfo ->
          put(packageInfo.version, buildJsonObject {
            put("name", packageInfo.name)
            put("version", packageInfo.version)
            packageInfo.packageJson["dependencies"]?.let { put("dependencies", it) }
            put("dist", buildJsonObject {
              put("integrity", packageInfo.integrity)
              put("tarball", localTarballUrl(packageInfo.name, packageInfo.version))
            })
          })
        }
      })
    }.toString().encodeToByteArray()
  }

  /** 包名只允许 npm 作用域结构，拒绝利用路径片段越出固定 debug 目录。 */
  private fun archiveDirectory(packageName: String): Path? {
    if (!PACKAGE_NAME.matches(packageName)) return null
    val root = rootDirectory ?: return null
    val segments = packageName.split('/')
    return when (segments.size) {
      1 -> root / segments[0]
      2 -> root / segments[0] / segments[1]
      else -> null
    }
  }

  /** 包名与版本都必须通过白名单校验，生成的路径才能用于本地文件访问。 */
  private fun archivePath(packageName: String, version: String): Path? {
    if (!LOCAL_VERSION.matches(version)) return null
    return archiveDirectory(packageName)?.let { directory ->
      directory / "$version$TARBALL_SUFFIX"
    }
  }

  private fun localTarballUrl(packageName: String, version: String): String {
    return "$LOCAL_TARBALL_BASE_URL${packageName.encodeNpmPathSegment()}/" +
      "${version.encodeNpmPathSegment()}$TARBALL_SUFFIX"
  }

  private companion object {
    const val LOCAL_TARBALL_BASE_URL = "https://cyxbs.local.debug/"
    const val PACKAGE_JSON = "package.json"
    const val TARBALL_SUFFIX = ".tgz"
    const val METADATA_ACCEPT = "application/vnd.npm.install-v1+json"
    val MINIMUM_SEMVER = NpmSemver(0, 0, 0, listOf("0"))
    val LOCAL_VERSION = Regex(
      """(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-debug\.\d{14})?""",
    )
    val PACKAGE_NAME = Regex("""(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*""")
  }
}

/** 本地虚拟 tarball URL 解出的精确 npm 包坐标。 */
internal data class DebugNpmPackageCoordinate(
  val name: String,
  val version: String,
)

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
