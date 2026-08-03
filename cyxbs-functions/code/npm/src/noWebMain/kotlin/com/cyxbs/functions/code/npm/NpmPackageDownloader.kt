package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.internal.NpmRegistryClient
import com.cyxbs.functions.code.npm.internal.NpmSnapshotResolver
import com.cyxbs.functions.code.npm.internal.ResolvedNpmPackage
import com.cyxbs.functions.code.npm.internal.buildNpmPackageDownloadUrl
import com.cyxbs.functions.code.npm.model.NpmReleaseSnapshot
import com.cyxbs.functions.code.npm.storage.NpmPackageArchive
import com.cyxbs.functions.code.npm.storage.NpmPackageArchiveStore
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 根据后端精确快照按需准备 npm tarball 依赖闭包。
 *
 * 每次 [prepareEntry] 先完成快照校验和缓存检查，再对全部缺失包执行 registry 预检；只有所有包的名称、
 * 精确版本和 SRI 均一致后才开始下载。下载地址只使用后端 [NpmReleaseSnapshot] 中的 urls，npm
 * registry 返回的 dependencies 和 tarball 地址均不会参与解析。
 *
 * 发布快照请求不属于该类职责：[prepareEntry] 的 [NpmReleaseSnapshot] 应由业务层提前获取。该类只会为
 * 缺失缓存依次发起精确版本元数据请求和 tarball 下载请求。
 *
 * 同一实例会串行处理准备请求，避免多个入口并发重复写入同一缓存。下载失败时可能保留此前已经完整校验
 * 并原子写入的包，但不会返回 [NpmPreparedEntry]，因此业务不能执行不完整入口。
 *
 * @param transport 执行精确版本元数据和 tarball GET 请求的 HTTP 实现。
 * @param archiveStore tarball 缓存。
 * @param registryBaseUrl npm 精确版本元数据服务根地址。
 */
class NpmPackageDownloader(
  private val transport: NpmHttpTransport,
  private val archiveStore: NpmPackageArchiveStore,
  registryBaseUrl: String = DEFAULT_REGISTRY_BASE_URL,
  json: Json = Json { ignoreUnknownKeys = true },
) {
  private val mutex = Mutex()
  private val registryClient = NpmRegistryClient(
    transport = transport,
    registryBaseUrl = registryBaseUrl,
    json = json,
  )

  /**
   * 按需准备 [entryPackage] 的完整依赖闭包。
   *
   * 该方法不会请求发布快照。对缓存缺失的包，会先请求
   * `{registryBaseUrl}/{encodedPackageName}/{encodedVersion}`；全部预检通过后再请求
   * `{baseUrl}/{encodedPackageName}/-/{unscopedName}-{encodedVersion}.tgz`。缓存命中的包不会产生网络请求。
   *
   * @return 全部归档均重新校验或下载成功后的入口描述，归档按依赖优先顺序排列。
   * @throws NpmSnapshotException 后端快照、入口或依赖闭包无效。
   * @throws NpmRegistryMismatchException registry 与快照的名称、版本或 SRI 不一致。
   * @throws NpmDownloadException registry/tarball 请求失败或全部 URL 不可用。
   * @throws NpmIntegrityException tarball 内容与快照 SRI 不一致。
   * @throws NpmStorageException 缓存读取、损坏文件清理或原子写入失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmSnapshotException::class,
    NpmRegistryMismatchException::class,
    NpmDownloadException::class,
    NpmIntegrityException::class,
    NpmStorageException::class,
    CancellationException::class,
  )
  suspend fun prepareEntry(
    snapshot: NpmReleaseSnapshot,
    entryPackage: String,
  ): NpmPreparedEntry {
    return mutex.withLock {
      val plan = NpmSnapshotResolver(snapshot).resolve(entryPackage)
      val archivesByPackage = linkedMapOf<String, NpmPackageArchive>()
      val missingPackages = mutableListOf<ResolvedNpmPackage>()

      plan.packages.forEach { packageInfo ->
        val cached = archiveStore.find(
          packageName = packageInfo.name,
          version = packageInfo.version,
          integrity = packageInfo.integrity.encoded,
        )
        if (cached == null) {
          missingPackages += packageInfo
        } else {
          archivesByPackage[packageInfo.name] = cached
        }
      }

      // 请求精确版本元数据：GET {registryBaseUrl}/{encodedPackageName}/{encodedVersion}。
      // 先完成所有缺失包的预检，任何一个不一致都不会触发 tarball 下载。
      missingPackages.forEach { packageInfo ->
        registryClient.verify(packageInfo)
      }

      missingPackages.forEach { packageInfo ->
        val bytes = downloadPackage(packageInfo, plan.urls)
        archivesByPackage[packageInfo.name] = archiveStore.write(
          packageName = packageInfo.name,
          version = packageInfo.version,
          integrity = packageInfo.integrity.encoded,
          bytes = bytes,
        )
      }

      NpmPreparedEntry(
        releaseTime = plan.releaseTime,
        entryPackage = plan.entryPackage,
        entryModule = plan.entryModule,
        archives = plan.packages.map { packageInfo ->
          checkNotNull(archivesByPackage[packageInfo.name]) {
            "Prepared npm package '${packageInfo.name}' is missing from the completed transaction."
          }
        },
      )
    }
  }

  /**
   * 按公共下载源顺序请求 npm registry 标准 tarball 路径。
   *
   * 只有响应通过 SRI 校验后才返回 tarball；当前地址失败时继续尝试下一个基础地址。
   */
  private suspend fun downloadPackage(
    packageInfo: ResolvedNpmPackage,
    baseUrls: List<String>,
  ): ByteArray {
    var firstFailure: Throwable? = null
    var integrityFailure: NpmIntegrityException? = null
    baseUrls.forEach { baseUrl ->
      val url = buildNpmPackageDownloadUrl(
        baseUrl = baseUrl,
        packageName = packageInfo.name,
        version = packageInfo.version,
      )
      try {
        val bytes = transport.get(url)
        if (!packageInfo.integrity.matches(bytes)) {
          throw NpmIntegrityException(
            "Downloaded npm package '${packageInfo.name}@${packageInfo.version}' " +
              "does not match the release snapshot integrity.",
          )
        }
        return bytes
      } catch (exception: CancellationException) {
        throw exception
      } catch (throwable: Throwable) {
        if (firstFailure == null) firstFailure = throwable
        if (throwable is NpmIntegrityException && integrityFailure == null) {
          integrityFailure = throwable
        }
      }
    }

    val failure = integrityFailure ?: NpmDownloadException(
      "All download URLs failed for npm package " +
        "'${packageInfo.name}@${packageInfo.version}'.",
      firstFailure,
    )
    firstFailure?.takeIf { it !== failure }?.let { throwable ->
      failure.addSuppressed(throwable)
    }
    throw failure
  }

  private companion object {
    const val DEFAULT_REGISTRY_BASE_URL = "https://registry.npmjs.org"
  }
}
