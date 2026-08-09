package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.model.NpmBundleClearResult
import com.cyxbs.functions.code.npm.model.NpmBundleDeleteResult
import com.cyxbs.functions.code.npm.model.NpmBundleInUseException
import com.cyxbs.functions.code.npm.model.NpmBundleSnapshot
import com.cyxbs.functions.code.npm.model.NpmDownloadException
import com.cyxbs.functions.code.npm.model.NpmIntegrityException
import com.cyxbs.functions.code.npm.model.NpmPackageId
import com.cyxbs.functions.code.npm.model.NpmStorageException
import com.cyxbs.functions.code.npm.pool.NpmPackagePool
import kotlinx.coroutines.CancellationException

/**
 * npm 全局 Bundle 池的管理入口。
 *
 * 默认实例与 [NpmJsServiceLoader] 共享 [NpmPackagePool.Default]，因此查询、删除、重新下载和清空
 * 都受同一把包池锁及运行租约保护。自定义包池的业务可显式传入对应 [packagePool]。
 */
class NpmBundleManager(
  private val packagePool: NpmPackagePool = NpmPackagePool.Default,
) {

  /**
   * 获取 Bundle、入口关系、精确依赖图和磁盘占用的一致性快照。
   *
   * 读取时会重新验证每个 tgz 的 SRI；损坏文件会按归档存储语义移除并标记为不可用。
   *
   * @throws NpmStorageException 包池状态或归档读取失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmStorageException::class, CancellationException::class)
  suspend fun getSnapshot(): NpmBundleSnapshot = packagePool.getBundleSnapshot()

  /**
   * 删除指定 Bundle，并使所有引用它的入口精确图失效。
   *
   * 操作随后执行可达性 GC，因此只被失效入口引用的依赖也会一起删除；共享依赖会继续保留。下次
   * 使用受影响入口时，包池会按业务传入的版本策略重新解析和下载。
   *
   * @throws NpmBundleInUseException Bundle 正被运行中的 Service/Runtime 使用。
   * @throws NpmStorageException 状态或归档删除失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmBundleInUseException::class,
    NpmStorageException::class,
    CancellationException::class,
  )
  suspend fun deleteBundle(id: NpmPackageId): NpmBundleDeleteResult {
    return packagePool.deleteBundle(id)
  }

  /**
   * 按已保存的 tarball URL 和 SRI 重新下载同一 Bundle 版本并原子替换磁盘文件。
   *
   * 当前已经创建的 Runtime 不会被热替换；它继续使用构建 Runtime 时得到的 Module 图。后续新建
   * Runtime 会读取替换后的文件。远端内容若与原 SRI 不一致则拒绝覆盖。
   *
   * @throws NpmDownloadException 下载失败。
   * @throws NpmIntegrityException 下载内容与保存的 SRI 不一致。
   * @throws NpmStorageException Bundle 不存在或归档写入失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmDownloadException::class,
    NpmIntegrityException::class,
    NpmStorageException::class,
    CancellationException::class,
  )
  suspend fun redownloadBundle(id: NpmPackageId) {
    packagePool.redownloadBundle(id)
  }

  /**
   * 清空当前包池的全部入口、Bundle 与 tgz。
   *
   * registry 选源记录不属于 Bundle 数据，不会被清理。只要仍存在运行租约，本次操作就整体失败，
   * 不会形成半清空状态。
   *
   * @throws NpmBundleInUseException 仍有 Service/Runtime 使用包池。
   * @throws NpmStorageException 状态或归档删除失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmBundleInUseException::class,
    NpmStorageException::class,
    CancellationException::class,
  )
  suspend fun clearAll(): NpmBundleClearResult = packagePool.clearBundles()

  companion object {

    /** 与默认 Service Loader 共享包池的进程级管理器。 */
    val Default: NpmBundleManager by lazy { NpmBundleManager() }
  }
}
