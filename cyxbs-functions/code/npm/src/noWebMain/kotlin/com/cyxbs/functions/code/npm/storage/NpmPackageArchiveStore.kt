package com.cyxbs.functions.code.npm.storage

import com.cyxbs.functions.code.npm.NpmIntegrityException
import com.cyxbs.functions.code.npm.NpmStorageException
import kotlinx.coroutines.CancellationException
import okio.Path

/** 已通过快照 SRI 校验并保存到本地的 npm tarball。 */
data class NpmPackageArchive(
  val packageName: String,
  val version: String,
  val integrity: String,
  val archivePath: Path,
)

/**
 * npm tarball 的持久化边界。
 *
 * 实现必须在 [find] 返回前重新确认缓存仍与 [integrity] 匹配，并保证 [write] 不会暴露半写入文件。
 * 同一坐标和 SRI 的重复写入应当幂等。
 */
interface NpmPackageArchiveStore {

  /**
   * 查找并验证缓存。
   *
   * @return 校验通过的 tarball；不存在或内容损坏时返回 null。
   * @throws NpmStorageException 文件读取或损坏缓存清理失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmStorageException::class, CancellationException::class)
  suspend fun find(
    packageName: String,
    version: String,
    integrity: String,
  ): NpmPackageArchive?

  /**
   * 校验并原子保存一个完整 tarball。
   *
   * @throws NpmIntegrityException [bytes] 与 [integrity] 不一致。
   * @throws NpmStorageException 文件写入或原子替换失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmIntegrityException::class,
    NpmStorageException::class,
    CancellationException::class,
  )
  suspend fun write(
    packageName: String,
    version: String,
    integrity: String,
    bytes: ByteArray,
  ): NpmPackageArchive
}
