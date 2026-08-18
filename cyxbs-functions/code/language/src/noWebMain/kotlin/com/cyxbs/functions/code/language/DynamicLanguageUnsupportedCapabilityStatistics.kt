package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnostic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/**
 * 一项不支持能力的本地聚合结果。
 *
 * 统计只保存语言、npm 包版本和稳定诊断 code，不保存源码、诊断消息、文件路径或符号名。
 * [affectedCompilationCount] 对同一次编译中的相同 code 只增加一次；
 * [diagnosticOccurrenceCount] 保留该次编译实际返回的诊断条数。
 */
@Serializable
data class DynamicLanguageUnsupportedCapabilityStatistic(
  val languageId: String,
  val npmPackageName: String,
  val npmPackageVersion: String,
  val diagnosticCode: String,
  val affectedCompilationCount: Long,
  val diagnosticOccurrenceCount: Long,
)

/** 收集、排序并持久化动态语言不支持诊断；普通存储失败不能影响编译功能。 */
internal class DynamicLanguageUnsupportedCapabilityStatistics(
  private val store: DynamicLanguageUnsupportedCapabilityStatisticsStore,
) {
  private val mutex = Mutex()
  private var loaded = false
  private var entries = emptyMap<StatisticKey, DynamicLanguageUnsupportedCapabilityStatistic>()

  /** 记录一次编译；没有不支持诊断时不触发磁盘读取或写入。 */
  suspend fun record(
    language: DynamicLanguageInfo,
    npmPackageVersion: String,
    diagnostics: List<DynamicCompilationDiagnostic>,
  ) {
    val unsupportedByCode = diagnostics
      .map(DynamicCompilationDiagnostic::code)
      .filter(::isUnsupportedCapabilityDiagnosticCode)
      .groupingBy(String::trim)
      .eachCount()
    if (unsupportedByCode.isEmpty()) return

    mutex.withLock {
      ensureLoaded()
      val next = entries.toMutableMap()
      unsupportedByCode.forEach { (diagnosticCode, occurrenceCount) ->
        val key = StatisticKey(
          languageId = language.languageId,
          npmPackageName = language.npmPackageName,
          npmPackageVersion = npmPackageVersion,
          diagnosticCode = diagnosticCode,
        )
        val previous = next[key]
        next[key] = DynamicLanguageUnsupportedCapabilityStatistic(
          languageId = key.languageId,
          npmPackageName = key.npmPackageName,
          npmPackageVersion = key.npmPackageVersion,
          diagnosticCode = key.diagnosticCode,
          affectedCompilationCount = previous?.affectedCompilationCount.safeIncrement(),
          diagnosticOccurrenceCount = previous?.diagnosticOccurrenceCount.safeAdd(occurrenceCount),
        )
      }
      entries = next.values
        .sortedWith(STATISTIC_PRIORITY)
        .take(MAX_STATISTIC_ENTRIES)
        .associateBy(::keyOf)
      persistBestEffort()
    }
  }

  /** 返回按受影响编译次数、出现次数和稳定身份排序的不可变快照。 */
  suspend fun snapshot(languageId: String? = null): List<DynamicLanguageUnsupportedCapabilityStatistic> {
    return mutex.withLock {
      ensureLoaded()
      entries.values
        .asSequence()
        .filter { languageId == null || it.languageId == languageId }
        .sortedWith(STATISTIC_PRIORITY)
        .toList()
    }
  }

  /** 清空全部语言或指定语言的本地统计。 */
  suspend fun clear(languageId: String? = null) = mutex.withLock {
    ensureLoaded()
    entries = if (languageId == null) {
      emptyMap()
    } else {
      entries.filterKeys { it.languageId != languageId }
    }
    persistBestEffort()
  }

  /** 首次访问时恢复一次；损坏或不兼容文件按空统计继续。 */
  private suspend fun ensureLoaded() {
    if (loaded) return
    entries = try {
      store.read().entries
        .filter { isUnsupportedCapabilityDiagnosticCode(it.diagnosticCode) }
        .sortedWith(STATISTIC_PRIORITY)
        .take(MAX_STATISTIC_ENTRIES)
        .associateBy(::keyOf)
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Throwable) {
      emptyMap()
    }
    loaded = true
  }

  /** 统计是产品决策的辅助派生数据，持久化失败时保留进程内结果即可。 */
  private suspend fun persistBestEffort() {
    try {
      store.write(
        DynamicLanguageUnsupportedCapabilityStatisticsState(
          entries = entries.values.sortedWith(STATISTIC_PRIORITY),
        ),
      )
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Throwable) {
      // 不允许统计功能阻断编译或运行。
    }
  }

  companion object {
    /** 多个编辑器页面共享的 App 级统计。 */
    val Default: DynamicLanguageUnsupportedCapabilityStatistics by lazy {
      DynamicLanguageUnsupportedCapabilityStatistics(
        OkioDynamicLanguageUnsupportedCapabilityStatisticsStore(),
      )
    }

    /** 测试与隔离 Manager 使用的纯内存统计。 */
    fun inMemory(): DynamicLanguageUnsupportedCapabilityStatistics {
      return DynamicLanguageUnsupportedCapabilityStatistics(
        InMemoryDynamicLanguageUnsupportedCapabilityStatisticsStore(),
      )
    }
  }
}

/** 是否属于可用于能力规划的不支持诊断；其他编译错误不应混入统计。 */
internal fun isUnsupportedCapabilityDiagnosticCode(code: String): Boolean {
  val normalized = code.trim()
  return normalized.length in 1..MAX_DIAGNOSTIC_CODE_LENGTH &&
    normalized.lowercase().contains(UNSUPPORTED_CODE_TOKEN)
}

@Serializable
internal data class DynamicLanguageUnsupportedCapabilityStatisticsState(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val entries: List<DynamicLanguageUnsupportedCapabilityStatistic> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
  }
}

/** 统计持久化边界，测试不需要访问平台文件系统。 */
internal interface DynamicLanguageUnsupportedCapabilityStatisticsStore {
  suspend fun read(): DynamicLanguageUnsupportedCapabilityStatisticsState
  suspend fun write(state: DynamicLanguageUnsupportedCapabilityStatisticsState)
}

/** 可观察写入结果的纯内存统计存储。 */
internal class InMemoryDynamicLanguageUnsupportedCapabilityStatisticsStore(
  initialState: DynamicLanguageUnsupportedCapabilityStatisticsState =
    DynamicLanguageUnsupportedCapabilityStatisticsState(),
) : DynamicLanguageUnsupportedCapabilityStatisticsStore {
  var state = initialState
    private set

  override suspend fun read(): DynamicLanguageUnsupportedCapabilityStatisticsState = state

  override suspend fun write(state: DynamicLanguageUnsupportedCapabilityStatisticsState) {
    this.state = state
  }
}

/** 使用 Okio JSON 文件原子保存跨平台不支持能力统计。 */
internal class OkioDynamicLanguageUnsupportedCapabilityStatisticsStore(
  rootDirectory: Path = DEFAULT_UNSUPPORTED_CAPABILITY_STATISTICS_DIRECTORY,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  },
) : DynamicLanguageUnsupportedCapabilityStatisticsStore {
  private val statePath = rootDirectory / "unsupported-capabilities.json"

  override suspend fun read(): DynamicLanguageUnsupportedCapabilityStatisticsState =
    withContext(ioDispatcher) {
      if (!fileSystem.exists(statePath)) {
        return@withContext DynamicLanguageUnsupportedCapabilityStatisticsState()
      }
      val state = try {
        json.decodeFromString<DynamicLanguageUnsupportedCapabilityStatisticsState>(
          fileSystem.read(statePath) { readUtf8() },
        )
      } catch (_: SerializationException) {
        return@withContext DynamicLanguageUnsupportedCapabilityStatisticsState()
      }
      if (state.schemaVersion == DynamicLanguageUnsupportedCapabilityStatisticsState.CURRENT_SCHEMA_VERSION) {
        state
      } else {
        DynamicLanguageUnsupportedCapabilityStatisticsState()
      }
    }

  override suspend fun write(state: DynamicLanguageUnsupportedCapabilityStatisticsState) =
    withContext(ioDispatcher) {
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

private data class StatisticKey(
  val languageId: String,
  val npmPackageName: String,
  val npmPackageVersion: String,
  val diagnosticCode: String,
)

private fun keyOf(statistic: DynamicLanguageUnsupportedCapabilityStatistic): StatisticKey {
  return StatisticKey(
    languageId = statistic.languageId,
    npmPackageName = statistic.npmPackageName,
    npmPackageVersion = statistic.npmPackageVersion,
    diagnosticCode = statistic.diagnosticCode,
  )
}

private fun Long?.safeIncrement(): Long = safeAdd(1)

private fun Long?.safeAdd(delta: Int): Long {
  val value = this ?: 0L
  return if (value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta
}

private val STATISTIC_PRIORITY =
  compareByDescending<DynamicLanguageUnsupportedCapabilityStatistic> { it.affectedCompilationCount }
    .thenByDescending { it.diagnosticOccurrenceCount }
    .thenBy(DynamicLanguageUnsupportedCapabilityStatistic::languageId)
    .thenBy(DynamicLanguageUnsupportedCapabilityStatistic::npmPackageVersion)
    .thenBy(DynamicLanguageUnsupportedCapabilityStatistic::diagnosticCode)

private const val UNSUPPORTED_CODE_TOKEN = "unsupported"
private const val MAX_DIAGNOSTIC_CODE_LENGTH = 160
private const val MAX_STATISTIC_ENTRIES = 256

/** 默认统计目录与语言图标缓存并列，清理系统缓存后会自然重建。 */
private val DEFAULT_UNSUPPORTED_CAPABILITY_STATISTICS_DIRECTORY =
  FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cyxbs-code" / "language-capabilities" / "v1"
