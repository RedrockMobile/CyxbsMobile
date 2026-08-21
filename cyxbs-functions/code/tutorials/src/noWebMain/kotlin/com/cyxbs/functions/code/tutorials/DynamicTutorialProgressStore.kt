package com.cyxbs.functions.code.tutorials

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/** 教程进度文件的完整快照；不兼容 schema 会被视为空状态。 */
@Serializable
internal data class DynamicTutorialProgressState(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val entries: List<DynamicTutorialProgress> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
  }
}

/** 教程进度持久化边界，单元测试可注入纯内存实现。 */
internal interface DynamicTutorialProgressStore {
  suspend fun read(): DynamicTutorialProgressState
  suspend fun write(state: DynamicTutorialProgressState)
}

/** 可观察最终状态的内存教程进度存储。 */
internal class InMemoryDynamicTutorialProgressStore(
  initialState: DynamicTutorialProgressState = DynamicTutorialProgressState(),
) : DynamicTutorialProgressStore {
  var state = initialState
    private set

  override suspend fun read(): DynamicTutorialProgressState = state

  override suspend fun write(state: DynamicTutorialProgressState) {
    this.state = state
  }
}

/** 使用 Okio JSON 临时文件加原子移动保存教程进度，避免写入中断留下半份状态。 */
internal class OkioDynamicTutorialProgressStore(
  rootDirectory: Path = DEFAULT_DYNAMIC_TUTORIAL_PROGRESS_DIRECTORY,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  },
) : DynamicTutorialProgressStore {
  private val statePath = rootDirectory / "tutorial-progress.json"

  override suspend fun read(): DynamicTutorialProgressState = withContext(ioDispatcher) {
    if (!fileSystem.exists(statePath)) return@withContext DynamicTutorialProgressState()
    val state = try {
      json.decodeFromString<DynamicTutorialProgressState>(fileSystem.read(statePath) { readUtf8() })
    } catch (_: SerializationException) {
      return@withContext DynamicTutorialProgressState()
    }
    state.takeIf { it.schemaVersion == DynamicTutorialProgressState.CURRENT_SCHEMA_VERSION }
      ?: DynamicTutorialProgressState()
  }

  override suspend fun write(state: DynamicTutorialProgressState) = withContext(ioDispatcher) {
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

/** 默认目录遵循现有动态语言缓存约定；系统清理缓存后教程可从 npm 包重新建立。 */
private val DEFAULT_DYNAMIC_TUTORIAL_PROGRESS_DIRECTORY =
  FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cyxbs-code" / "tutorial-progress" / "v1"
