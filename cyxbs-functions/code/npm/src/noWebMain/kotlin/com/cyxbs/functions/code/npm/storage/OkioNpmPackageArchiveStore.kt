package com.cyxbs.functions.code.npm.storage

import com.cyxbs.functions.code.npm.model.NpmIntegrityException
import com.cyxbs.functions.code.npm.model.NpmResolutionException
import com.cyxbs.functions.code.npm.model.NpmStorageException
import com.cyxbs.functions.code.npm.internal.NpmIntegrity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/**
 * 基于 Okio 的跨平台 npm tarball 缓存。
 *
 * Android、iOS 和 Desktop 调用方应传入 App 私有目录下的 [rootDirectory]。文件名由包名、版本和 SRI
 * 的 SHA-256 派生，不会把远端字符串直接拼进路径；写入采用同目录临时文件和原子移动。
 *
 */
class OkioNpmPackageArchiveStore(
  private val rootDirectory: Path,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : NpmPackageArchiveStore {
  private val mutex = Mutex()
  private val archiveDirectory = rootDirectory / "archives"

  override suspend fun find(
    packageName: String,
    version: String,
    integrity: String,
  ): NpmPackageArchive? {
    return withFileLock("read") {
      val path = archivePath(packageName, version, integrity)
      val metadata = fileSystem.metadataOrNull(path) ?: return@withFileLock null
      if (metadata.isDirectory) {
        fileSystem.delete(path, mustExist = false)
        return@withFileLock null
      }
      val bytes = fileSystem.read(path) { readByteArray() }
      val parsedIntegrity = NpmIntegrity.parse(integrity, packageName)
      if (!parsedIntegrity.matches(bytes)) {
        fileSystem.delete(path, mustExist = false)
        return@withFileLock null
      }
      NpmPackageArchive(
        packageName = packageName,
        version = version,
        integrity = parsedIntegrity.encoded,
        archivePath = path,
        sizeBytes = metadata.size ?: bytes.size.toLong(),
        downloadedAtEpochMillis = metadata.lastModifiedAtMillis ?: metadata.createdAtMillis,
      )
    }
  }

  override suspend fun write(
    packageName: String,
    version: String,
    integrity: String,
    bytes: ByteArray,
  ): NpmPackageArchive {
    val parsedIntegrity = NpmIntegrity.parse(integrity, packageName)
    if (!parsedIntegrity.matches(bytes)) {
      throw NpmIntegrityException(
        "Npm package '$packageName@$version' does not match the registry integrity.",
      )
    }
    return withFileLock("write") {
      val target = archivePath(packageName, version, parsedIntegrity.encoded)
      writeAtomically(target, bytes)
      val metadata = fileSystem.metadata(target)
      NpmPackageArchive(
        packageName = packageName,
        version = version,
        integrity = parsedIntegrity.encoded,
        archivePath = target,
        sizeBytes = metadata.size ?: bytes.size.toLong(),
        downloadedAtEpochMillis = metadata.lastModifiedAtMillis ?: metadata.createdAtMillis,
      )
    }
  }

  /** 删除 GC 已确认不再被任何入口依赖的归档。 */
  override suspend fun remove(
    packageName: String,
    version: String,
    integrity: String,
  ) {
    withFileLock("remove") {
      fileSystem.delete(
        archivePath(packageName, version, integrity),
        mustExist = false,
      )
    }
  }

  /** 将文件系统异常统一转换为稳定存储异常，并保留协程取消语义。 */
  private suspend fun <T> withFileLock(
    operation: String,
    block: () -> T,
  ): T {
    return try {
      withContext(ioDispatcher) {
        mutex.withLock {
          block()
        }
      }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: NpmIntegrityException) {
      throw exception
    } catch (exception: NpmResolutionException) {
      throw NpmStorageException("Invalid integrity was supplied to npm archive cache.", exception)
    } catch (exception: NpmStorageException) {
      throw exception
    } catch (throwable: Throwable) {
      throw NpmStorageException("Failed to $operation npm archive cache.", throwable)
    }
  }

  /** 使用坐标与 SRI 的摘要生成稳定文件名，避免路径穿越和不同内容相互覆盖。 */
  private fun archivePath(
    packageName: String,
    version: String,
    integrity: String,
  ): Path {
    val digest = "$packageName\u0000$version\u0000$integrity"
      .encodeUtf8()
      .sha256()
      .hex()
    return archiveDirectory / "$digest.tgz"
  }

  /** 先完整写入同目录临时文件，再原子替换目标归档。 */
  private fun writeAtomically(target: Path, bytes: ByteArray) {
    val parent = requireNotNull(target.parent) { "Archive target must have a parent directory." }
    fileSystem.createDirectories(parent)
    val temporary = parent / ".${target.name}.${Random.nextLong().toString(16)}.tmp"
    try {
      fileSystem.write(temporary, mustCreate = true) {
        write(bytes)
      }
      fileSystem.atomicMove(source = temporary, target = target)
    } finally {
      fileSystem.delete(temporary, mustExist = false)
    }
  }
}
