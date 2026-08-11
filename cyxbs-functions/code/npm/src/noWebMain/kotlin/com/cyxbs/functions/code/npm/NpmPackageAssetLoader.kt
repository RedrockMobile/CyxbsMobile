package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.diagnostic.NpmPackagePoolMetrics
import com.cyxbs.functions.code.npm.internal.readNpmPackageFile
import com.cyxbs.functions.code.npm.model.NpmEntryRequest
import com.cyxbs.functions.code.npm.model.NpmEntryVersion
import com.cyxbs.functions.code.npm.model.NpmException
import com.cyxbs.functions.code.npm.model.NpmPackageAssetException
import com.cyxbs.functions.code.npm.model.NpmRefreshPolicy
import com.cyxbs.functions.code.npm.pool.NpmPackagePool
import com.cyxbs.functions.code.npm.pool.NpmPreparedEntryLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.FileSystem

/**
 * 下载并读取 npm 根包中的静态资源，不构建 JavaScript ModuleGraph 或 Runtime。
 *
 * Registry 选择、`latest` 刷新、SRI、tgz 缓存、入口使用时间、GC 与 debug 本地源全部复用
 * [NpmPackagePool]。资源内容会在入口租约内复制到内存，返回前释放租约；调用方无需管理生命周期。
 *
 * @param packagePool App 级长生命周期包池，默认使用 [NpmPackagePool.Default]。
 * @param fileSystem 读取包池归档使用的文件系统；自定义包池存储时必须传入与其相同的实例。
 * @param ioDispatcher tgz 扫描和解压使用的调度器。
 */
class NpmPackageAssetLoader internal constructor(
  private val entryProvider: NpmPackageAssetEntryProvider,
  private val fileSystem: FileSystem,
  private val ioDispatcher: CoroutineDispatcher,
) {

  /** 使用指定或默认包池创建静态资源加载器。 */
  constructor(
    packagePool: NpmPackagePool = NpmPackagePool.Default,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  ) : this(
    entryProvider = NpmPackageAssetEntryProvider { request, refreshPolicy, metrics ->
      packagePool.acquireEntry(request, refreshPolicy, metrics)
    },
    fileSystem = fileSystem,
    ioDispatcher = ioDispatcher,
  )

  /**
   * 加载根包中的一个二进制资源。
   *
   * @param packageName npm 完整包名。
   * @param assetPath 包根目录下使用 `/` 分隔的相对路径，例如 `catalog.json`。
   * @param version `latest` 或完整精确 semver；默认遵循包池的 latest 刷新策略。
   * @param refreshPolicy 控制本次是否强制检查远端版本。
   * @param metrics 可选的包解析与下载耗时记录对象。
   * @return 与归档内文件内容一致的独立字节数组。
   * @throws NpmPackageAssetException 资源路径非法、根包归档缺失或目标文件不存在。
   * @throws NpmException npm 解析、下载、SRI 或存储失败。
   * @throws CancellationException 调用协程被取消；已取得的入口租约仍会先完成释放。
   */
  @Throws(NpmException::class, CancellationException::class)
  suspend fun load(
    packageName: String,
    assetPath: String,
    version: String = LATEST_VERSION,
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
    metrics: NpmPackagePoolMetrics? = null,
  ): ByteArray {
    val normalizedPath = normalizeAssetPath(assetPath)
    val request = NpmEntryRequest(
      packageName = packageName,
      version = version.toEntryVersion(),
      entryName = "asset|$packageName|$version",
    )
    val lease = entryProvider.acquire(request, refreshPolicy, metrics)
    var failure: Throwable? = null
    try {
      val preparedEntry = lease.preparedEntry
      val rootArchive = preparedEntry.archives.singleOrNull { archive ->
        archive.packageName == preparedEntry.entryPackage.name &&
          archive.version == preparedEntry.entryPackage.version
      } ?: throw NpmPackageAssetException(
        "Prepared npm asset package '${preparedEntry.entryPackage.name}@" +
            "${preparedEntry.entryPackage.version}' has no unique root archive.",
      )
      return try {
        withContext(ioDispatcher) {
          readNpmPackageFile(fileSystem, rootArchive.archivePath, normalizedPath)
        } ?: throw NpmPackageAssetException(
          "npm package '${rootArchive.packageName}@${rootArchive.version}' " +
              "does not contain asset '$normalizedPath'.",
        )
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: NpmPackageAssetException) {
        throw exception
      } catch (exception: NpmException) {
        throw NpmPackageAssetException(
          "Failed to read npm package asset '$normalizedPath'.",
          exception,
        )
      } catch (throwable: Throwable) {
        throw NpmPackageAssetException(
          "Failed to read npm package asset '$normalizedPath'.",
          throwable,
        )
      }
    } catch (throwable: Throwable) {
      failure = throwable
      throw throwable
    } finally {
      // 即使调用协程已经取消，也必须解除包池 GC 保护，避免短生命周期资源读取留下永久租约。
      try {
        withContext(NonCancellable) { lease.release() }
      } catch (cleanupFailure: Throwable) {
        val primaryFailure = failure
        if (primaryFailure == null) throw cleanupFailure
        if (cleanupFailure !== primaryFailure) primaryFailure.addSuppressed(cleanupFailure)
      }
    }
  }

  /**
   * 加载 UTF-8 文本资源。
   *
   * 参数、缓存语义与异常边界均与 [load] 相同。
   */
  @Throws(NpmException::class, CancellationException::class)
  suspend fun loadText(
    packageName: String,
    assetPath: String,
    version: String = LATEST_VERSION,
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
    metrics: NpmPackagePoolMetrics? = null,
  ): String {
    return load(packageName, assetPath, version, refreshPolicy, metrics).decodeToString()
  }

  /** 将公开字符串版本转换为包池显式版本策略。 */
  private fun String.toEntryVersion(): NpmEntryVersion {
    return if (this == LATEST_VERSION) NpmEntryVersion.Latest else NpmEntryVersion.Exact(this)
  }

  /** 规范化包内相对路径并拒绝绝对路径、空段与路径穿越。 */
  private fun normalizeAssetPath(assetPath: String): String {
    val normalized = assetPath.replace('\\', '/').trim()
    val segments = normalized.split('/')
    if (normalized.isEmpty() || normalized.startsWith('/') ||
      segments.any { it.isEmpty() || it == "." || it == ".." }
    ) {
      throw NpmPackageAssetException("Invalid npm package asset path '$assetPath'.")
    }
    return segments.joinToString("/")
  }

  private companion object {
    const val LATEST_VERSION = "latest"
  }
}

/** 测试可替换、生产委托给 [NpmPackagePool] 的入口租约获取边界。 */
internal fun interface NpmPackageAssetEntryProvider {
  /** 按指定刷新策略取得已经完整下载并校验的入口租约。 */
  suspend fun acquire(
    request: NpmEntryRequest,
    refreshPolicy: NpmRefreshPolicy,
    metrics: NpmPackagePoolMetrics?,
  ): NpmPreparedEntryLease
}
