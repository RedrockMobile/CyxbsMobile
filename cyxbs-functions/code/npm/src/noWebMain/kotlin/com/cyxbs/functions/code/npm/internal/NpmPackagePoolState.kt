package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.NpmStorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/**
 * npm 全局包池的持久化状态。
 *
 * ```
 * PoolState
 * ├── packages: 已下载版本的不可变 registry 元数据（依赖仍是 semver 范围）
 * └── entries:  每个入口最近一次可执行的精确依赖图
 *      └── nodes: 父包 -> 本次解析选中的具体依赖版本
 * ```
 *
 * 包元数据与入口解析结果分离，防止一个入口重新选版本时改写另一个入口仍在使用的依赖边。
 */
@Serializable
internal data class NpmPackagePoolState(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val generation: Long = 0,
  val lastGcAtEpochMillis: Long = 0,
  val packages: List<PersistedNpmPackage> = emptyList(),
  val entries: List<PersistedNpmEntry> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
  }
}

/** registry 中一个已经下载并校验的具体包版本。 */
@Serializable
internal data class PersistedNpmPackage(
  val name: String,
  val version: String,
  val integrity: String,
  val tarballUrl: String,
  val dependencySpecs: Map<String, String>,
)

/** 一个入口最后一次完整解析成功的结果。 */
@Serializable
internal data class PersistedNpmEntry(
  val entryName: String,
  val packageName: String,
  val versionSpec: String,
  val entryModule: String?,
  val rootName: String,
  val rootVersion: String,
  val resolvedAtEpochMillis: Long,
  val lastUsedAtEpochMillis: Long,
  val poolGeneration: Long,
  val nodes: List<PersistedNpmResolvedNode>,
)

/** 入口解析结果中的包节点；依赖值编码为精确的 name/version。 */
@Serializable
internal data class PersistedNpmResolvedNode(
  val name: String,
  val version: String,
  val dependencies: Map<String, PersistedNpmPackageId>,
)

/** JSON 可稳定序列化的包身份。 */
@Serializable
internal data class PersistedNpmPackageId(
  val name: String,
  val version: String,
)

/** 全局包池状态的持久化边界，写入必须对读者保持原子。 */
internal interface NpmPackagePoolStateStore {

  /** 状态不存在时返回空状态；结构损坏时抛出稳定存储异常。 */
  suspend fun read(): NpmPackagePoolState

  /** 原子替换完整状态；调用方只会传入已经完成事务计算的新状态。 */
  suspend fun write(state: NpmPackagePoolState)
}

/**
 * 基于 Okio 的跨平台 JSON 状态存储。
 *
 * 状态体只保存坐标、依赖边和时间，不保存 tgz 或源码；写入使用同目录临时文件和原子移动。
 */
internal class OkioNpmPackagePoolStateStore(
  rootDirectory: Path,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  },
) : NpmPackagePoolStateStore {
  private val statePath = rootDirectory / "pool-state.json"

  override suspend fun read(): NpmPackagePoolState {
    return withStorageErrors("read") {
      if (!fileSystem.exists(statePath)) return@withStorageErrors NpmPackagePoolState()
      val state = try {
        json.decodeFromString<NpmPackagePoolState>(
          fileSystem.read(statePath) { readUtf8() },
        )
      } catch (exception: SerializationException) {
        throw NpmStorageException("Npm package pool state is invalid.", exception)
      }
      if (state.schemaVersion != NpmPackagePoolState.CURRENT_SCHEMA_VERSION) {
        NpmPackagePoolState()
      } else {
        state
      }
    }
  }

  override suspend fun write(state: NpmPackagePoolState) {
    withStorageErrors("write") {
      val parent = requireNotNull(statePath.parent)
      fileSystem.createDirectories(parent)
      val temporary = parent / ".${statePath.name}.${Random.nextLong().toString(16)}.tmp"
      try {
        fileSystem.write(temporary, mustCreate = true) {
          writeUtf8(json.encodeToString(state))
        }
        fileSystem.atomicMove(temporary, statePath)
      } finally {
        fileSystem.delete(temporary, mustExist = false)
      }
    }
  }

  /** 保留协程取消，并把平台文件系统异常统一映射为 npm 存储异常。 */
  private suspend fun <T> withStorageErrors(
    operation: String,
    block: () -> T,
  ): T {
    return try {
      withContext(ioDispatcher) { block() }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: NpmStorageException) {
      throw exception
    } catch (throwable: Throwable) {
      throw NpmStorageException("Failed to $operation npm package pool state.", throwable)
    }
  }
}
