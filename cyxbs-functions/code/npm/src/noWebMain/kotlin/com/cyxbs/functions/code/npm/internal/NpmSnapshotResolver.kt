package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.NpmSnapshotException
import com.cyxbs.functions.code.npm.model.NpmLockedPackage
import com.cyxbs.functions.code.npm.model.NpmReleaseSnapshot
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** 下载器内部使用的已校验精确包。 */
internal data class ResolvedNpmPackage(
  val name: String,
  val version: String,
  val integrity: NpmIntegrity,
)

/** 按依赖优先顺序排列、并携带快照公共下载源的入口下载计划。 */
internal data class NpmDependencyPlan(
  val releaseTime: String,
  val entryPackage: String,
  val entryModule: String,
  val urls: List<String>,
  val packages: List<ResolvedNpmPackage>,
)

/**
 * 校验后端快照并按入口计算依赖闭包。
 *
 * 校验只信任快照自身声明的依赖关系，不读取 npm 元数据中的 dependencies。遍历允许依赖环，
 * visited 会保证每个包只进入下载计划一次。
 */
internal class NpmSnapshotResolver(
  private val snapshot: NpmReleaseSnapshot,
) {

  /** 校验整个快照，使未被当前入口触达的错误也不会潜伏到下一次按需加载。 */
  @Throws(NpmSnapshotException::class)
  fun validate() {
    if (!RELEASE_TIME_REGEX.matches(snapshot.releaseTime)) {
      throw NpmSnapshotException(
        "releaseTime must use yyyy.MM.dd HH:mm:ss, but was '${snapshot.releaseTime}'.",
      )
    }
    if (snapshot.entries.isEmpty()) {
      throw NpmSnapshotException("Npm release snapshot must contain at least one entry.")
    }
    if (snapshot.packages.isEmpty()) {
      throw NpmSnapshotException("Npm release snapshot must contain at least one package.")
    }
    validateDownloadUrls()
    snapshot.packages.forEach { (name, lockedPackage) ->
      validatePackageName(name)
      validateLockedPackage(name, lockedPackage)
    }
    snapshot.entries.forEach { (packageName, entryModule) ->
      validatePackageName(packageName)
      if (packageName !in snapshot.packages) {
        throw NpmSnapshotException("Entry package '$packageName' is missing from packages.")
      }
      if (!isValidModuleName(entryModule)) {
        throw NpmSnapshotException("Entry module '$entryModule' is invalid.")
      }
    }
  }

  /** 返回 [entryPackage] 的依赖闭包；不属于该闭包的包不会触发缓存或网络访问。 */
  @Throws(NpmSnapshotException::class)
  fun resolve(entryPackage: String): NpmDependencyPlan {
    validate()
    val entryModule = snapshot.entries[entryPackage]
      ?: throw NpmSnapshotException("Unknown npm entry package '$entryPackage'.")
    val visiting = mutableSetOf<String>()
    val visited = mutableSetOf<String>()
    val ordered = mutableListOf<ResolvedNpmPackage>()

    fun visit(packageName: String) {
      if (packageName in visited) return
      if (!visiting.add(packageName)) return
      val lockedPackage = snapshot.packages[packageName]
        ?: throw NpmSnapshotException("Dependency '$packageName' is missing from packages.")
      lockedPackage.dependencies.forEach(::visit)
      visiting.remove(packageName)
      visited.add(packageName)
      ordered += ResolvedNpmPackage(
        name = packageName,
        version = lockedPackage.version,
        integrity = NpmIntegrity.parse(lockedPackage.integrity, packageName),
      )
    }

    visit(entryPackage)
    return NpmDependencyPlan(
      releaseTime = snapshot.releaseTime,
      entryPackage = entryPackage,
      entryModule = entryModule,
      urls = snapshot.urls.map { it.trimEnd('/') },
      packages = ordered,
    )
  }

  /** 校验一个锁定包的版本、依赖和 SRI。 */
  private fun validateLockedPackage(name: String, lockedPackage: NpmLockedPackage) {
    if (!SEMVER_REGEX.matches(lockedPackage.version)) {
      throw NpmSnapshotException(
        "Package '$name' version '${lockedPackage.version}' is not an exact semantic version.",
      )
    }
    if (lockedPackage.dependencies.distinct().size != lockedPackage.dependencies.size) {
      throw NpmSnapshotException("Package '$name' contains duplicate dependencies.")
    }
    lockedPackage.dependencies.forEach { dependency ->
      validatePackageName(dependency)
      if (dependency !in snapshot.packages) {
        throw NpmSnapshotException("Package '$name' dependency '$dependency' is missing.")
      }
    }
    NpmIntegrity.parse(lockedPackage.integrity, name)
  }

  /** 校验整个依赖计划共用的下载源，并拒绝规范化后重复的基础地址。 */
  private fun validateDownloadUrls() {
    if (snapshot.urls.isEmpty()) {
      throw NpmSnapshotException("Npm release snapshot must contain a download URL.")
    }
    val normalizedUrls = snapshot.urls.map { it.trimEnd('/') }
    if (normalizedUrls.distinct().size != normalizedUrls.size) {
      throw NpmSnapshotException("Npm release snapshot contains duplicate download URLs.")
    }
    snapshot.urls.forEach { url ->
      if (!isValidDownloadBaseUrl(url)) {
        throw NpmSnapshotException("Npm release snapshot contains an invalid HTTPS download URL.")
      }
    }
  }

  /** 按 npm 包命名规则拒绝范围、坐标和路径歧义。 */
  private fun validatePackageName(name: String) {
    if (!PACKAGE_NAME_REGEX.matches(name) || name.length > MAX_PACKAGE_NAME_LENGTH) {
      throw NpmSnapshotException("Invalid npm package name '$name'.")
    }
  }

  private companion object {
    const val MAX_PACKAGE_NAME_LENGTH = 214

    val RELEASE_TIME_REGEX = Regex(
      """\d{4}\.(0[1-9]|1[0-2])\.(0[1-9]|[12]\d|3[01]) ([01]\d|2[0-3]):[0-5]\d:[0-5]\d""",
    )
    val PACKAGE_NAME_REGEX = Regex(
      """(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*""",
    )
    val SEMVER_REGEX = Regex(
      """(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
        """(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""" +
        """(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""",
    )

    fun isValidModuleName(name: String): Boolean {
      return name.isNotBlank() &&
        !name.startsWith('/') &&
        '\u0000' !in name &&
        '\\' !in name &&
        name.split('/').none { it == ".." }
    }

    /** 基础地址必须是无用户信息、query 和 fragment 的 HTTPS URL，确保后续路径拼接无歧义。 */
    fun isValidDownloadBaseUrl(value: String): Boolean {
      val url = runCatching { Url(value) }.getOrNull() ?: return false
      return url.protocol == URLProtocol.HTTPS &&
        url.host.isNotBlank() &&
        url.user == null &&
        url.password == null &&
        url.parameters.isEmpty() &&
        url.fragment.isEmpty() &&
        !url.trailingQuery
    }
  }
}
