package com.cyxbs.functions.code.npm.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * registry 请求策略。
 *
 * [ProbeAll] 表示尚未积累足够样本，需要并发探测全部源；[Preferred] 表示探测完成，按稳定顺序先请求
 * 主源，仅在失败时继续请求后续备用源。
 */
internal sealed interface NpmRegistryRequestPlan {
  /** 本次请求占用一个探测名额，完成或取消时必须归还对应 [reservationId]。 */
  data class ProbeAll(val reservationId: Long) : NpmRegistryRequestPlan

  data class Preferred(val registryBaseUrls: List<String>) : NpmRegistryRequestPlan
}

/**
 * 根据少量并发探测选择稳定 registry，并跨进程保存选择结果。
 *
 * ```
 * 无选择结果 ──最多 3 个请求并发竞速全部源──> 按胜出次数 / 失败数 / 耗时排序并保存
 *     │                                      │
 *     └──其余依赖请求等待探测完成                └──健康时只请求第一名
 *                                                └──失败才顺序请求备用源
 *                                                    ├──主源连续失败 2 次后重探测
 *                                                    └──选择满 7 天后重探测
 * ```
 *
 * 有效期只从完成选优时计算，主源成功不会续期。过期不会启动后台任务，而是在下一次真实 metadata 请求
 * 到达时重新探测；registry 配置变化、持久化 schema 不兼容或系统时间回拨也会直接废弃旧选择。
 */
internal class NpmRegistrySelector(
  private val registryBaseUrls: List<String>,
  private val store: NpmRegistrySelectionStore,
  private val clock: () -> Long,
) {
  private val mutex = Mutex()
  private var loaded = false
  private var state = PersistedNpmRegistrySelection(registryBaseUrls = registryBaseUrls)
  private var nextProbeReservationId = 0L
  private val activeProbeReservations = mutableSetOf<Long>()
  private var probeStateChanged = CompletableDeferred<Unit>()

  /**
   * 获取本次请求计划；首次读取失败或配置变化时按空统计重新探测。
   *
   * 同一进程最多允许 [REQUIRED_PROBE_COUNT] 个探测请求在途。依赖图的其他并发请求会等待这些探测
   * 完成，然后直接复用固定源，避免初次加载时按依赖数量放大 registry 请求。
   */
  suspend fun requestPlan(): NpmRegistryRequestPlan {
    while (true) {
      var waitForProbe: CompletableDeferred<Unit>? = null
      val plan = mutex.withLock {
        ensureLoaded()
        invalidateExpiredPreference()
        when {
          state.preferredOrder.isNotEmpty() -> {
            NpmRegistryRequestPlan.Preferred(state.preferredOrder)
          }
          state.completedProbeCount + activeProbeReservations.size < REQUIRED_PROBE_COUNT -> {
            val reservationId = nextProbeReservationId++
            activeProbeReservations += reservationId
            NpmRegistryRequestPlan.ProbeAll(reservationId)
          }
          else -> {
            waitForProbe = probeStateChanged
            null
          }
        }
      }
      if (plan != null) return plan
      checkNotNull(waitForProbe).await()
    }
  }

  /**
   * 记录一次全部源竞速结果。
   *
   * @param reservationId [requestPlan] 分配的探测名额，用于并发场景下准确完成对应请求。
   * @param winnerBaseUrl 完成全部校验并获胜的源；全部失败时为 null。
   * @param winnerDuration 获胜源完整请求耗时，仅用于同胜出次数下的排序。
   * @param failedBaseUrls 已经明确失败的源；被获胜请求取消的较慢源不记为失败。
   */
  suspend fun recordProbe(
    reservationId: Long,
    winnerBaseUrl: String?,
    winnerDuration: Duration?,
    failedBaseUrls: Set<String>,
  ) {
    mutex.withLock {
      ensureLoaded()
      if (!activeProbeReservations.remove(reservationId)) return
      val scores = state.scores.associateByTo(linkedMapOf()) { it.registryBaseUrl }
      registryBaseUrls.forEach { registryBaseUrl ->
        if (registryBaseUrl !in scores) {
          scores[registryBaseUrl] = PersistedNpmRegistryScore(registryBaseUrl = registryBaseUrl)
        }
      }
      winnerBaseUrl?.let { winner ->
        val previous = scores.getValue(winner)
        scores[winner] = previous.copy(
          winCount = previous.winCount + 1,
          totalWinnerDurationMicros = previous.totalWinnerDurationMicros +
            checkNotNull(winnerDuration).inWholeMicroseconds,
        )
      }
      failedBaseUrls.forEach { failed ->
        scores[failed]?.let { previous ->
          scores[failed] = previous.copy(failureCount = previous.failureCount + 1)
        }
      }

      val completedProbeCount = state.completedProbeCount + 1
      val preferredOrder = if (completedProbeCount >= REQUIRED_PROBE_COUNT &&
        scores.values.any { it.winCount > 0 }
      ) {
        scores.values.sortedWith(
          compareByDescending<PersistedNpmRegistryScore> { it.winCount }
            .thenBy { it.failureCount }
            .thenBy { score ->
              if (score.winCount == 0) {
                Long.MAX_VALUE
              } else {
                score.totalWinnerDurationMicros / score.winCount
              }
            }
            .thenBy { registryBaseUrls.indexOf(it.registryBaseUrl) },
        ).map { it.registryBaseUrl }
      } else {
        emptyList()
      }
      state = if (completedProbeCount >= REQUIRED_PROBE_COUNT && preferredOrder.isEmpty()) {
        // 整轮均失败时不固定一个无成功样本的源；释放等待者，由下一批请求重新探测。
        PersistedNpmRegistrySelection(registryBaseUrls = registryBaseUrls)
      } else {
        state.copy(
          completedProbeCount = completedProbeCount,
          scores = registryBaseUrls.map(scores::getValue),
          preferredOrder = preferredOrder,
          selectedAtEpochMillis = if (preferredOrder.isEmpty()) null else clock(),
          consecutivePrimaryFailures = 0,
        )
      }
      // 先更新进程内协调信号；即使随后持久化因协程取消中断，等待请求也不会永久挂起。
      notifyProbeStateChanged()
      store.write(state)
    }
  }

  /** 请求在完成竞速前被取消或异常终止时归还探测名额，避免后续请求永久等待。 */
  suspend fun releaseProbe(reservationId: Long) {
    mutex.withLock {
      if (activeProbeReservations.remove(reservationId)) notifyProbeStateChanged()
    }
  }

  /**
   * 记录固定源请求结果。
   *
   * 主源成功会清除暂存的失败次数；主源连续失败两次后清空选择，让下一次请求重新并发探测。备用源是否
   * 成功不改变这个阈值，因为当前请求已经完成故障转移。
   */
  suspend fun recordPreferredResult(primarySucceeded: Boolean) {
    mutex.withLock {
      ensureLoaded()
      // 旧固定批次的结果可能晚于重新探测到达，不得污染或清空新一轮统计。
      if (state.preferredOrder.isEmpty()) return
      if (primarySucceeded) {
        if (state.consecutivePrimaryFailures != 0) {
          state = state.copy(consecutivePrimaryFailures = 0)
          store.write(state)
        }
        return
      }

      val failureCount = state.consecutivePrimaryFailures + 1
      state = if (failureCount >= PRIMARY_FAILURES_BEFORE_REPROBE) {
        PersistedNpmRegistrySelection(registryBaseUrls = registryBaseUrls)
      } else {
        state.copy(consecutivePrimaryFailures = failureCount)
      }
      if (state.preferredOrder.isEmpty()) notifyProbeStateChanged()
      store.write(state)
    }
  }

  /** 唤醒等待探测结果的请求；替换信号对象可避免后续等待者误消费旧事件。 */
  private fun notifyProbeStateChanged() {
    val previous = probeStateChanged
    probeStateChanged = CompletableDeferred()
    previous.complete(Unit)
  }

  /**
   * 选择状态只在 schema、registry 配置和有效期均满足时复用，避免旧状态跨版本或网络环境长期生效。
   */
  private suspend fun ensureLoaded() {
    if (loaded) return
    val persisted = store.read()
    state = if (persisted?.schemaVersion == PersistedNpmRegistrySelection.CURRENT_SCHEMA_VERSION &&
      persisted.registryBaseUrls == registryBaseUrls &&
      persisted.preferredOrder.all { it in registryBaseUrls } &&
      persisted.hasFreshPreference()
    ) {
      persisted
    } else {
      PersistedNpmRegistrySelection(registryBaseUrls = registryBaseUrls)
    }
    loaded = true
  }

  /** 长生命周期 Pool 在进程内也可能跨过有效期，因此每次生成请求计划前重新检查。 */
  private fun invalidateExpiredPreference() {
    if (state.preferredOrder.isNotEmpty() && !state.hasFreshPreference()) {
      state = PersistedNpmRegistrySelection(registryBaseUrls = registryBaseUrls)
    }
  }

  /** 系统时间回拨时无法证明样本年龄，按过期处理比无限复用旧选择更安全。 */
  private fun PersistedNpmRegistrySelection.hasFreshPreference(): Boolean {
    if (preferredOrder.isEmpty()) return true
    val selectedAt = selectedAtEpochMillis ?: return false
    val now = clock()
    if (selectedAt !in 0..now) return false
    return now - selectedAt <= PREFERENCE_TTL_MILLIS
  }

  private companion object {
    const val REQUIRED_PROBE_COUNT = 3
    const val PRIMARY_FAILURES_BEFORE_REPROBE = 2
    val PREFERENCE_TTL_MILLIS = 7.days.inWholeMilliseconds
  }
}

/** registry 选择状态持久化边界；失败应由实现降级为空状态，不得中断包加载。 */
internal interface NpmRegistrySelectionStore {
  suspend fun read(): PersistedNpmRegistrySelection?
  suspend fun write(state: PersistedNpmRegistrySelection)
}

/** 测试及未提供磁盘目录的客户端使用的内存状态。 */
internal class InMemoryNpmRegistrySelectionStore : NpmRegistrySelectionStore {
  private var state: PersistedNpmRegistrySelection? = null

  override suspend fun read(): PersistedNpmRegistrySelection? = state

  override suspend fun write(state: PersistedNpmRegistrySelection) {
    this.state = state
  }
}

/**
 * 位于 npm 包池目录内的 registry 选择状态。
 *
 * 文件只是性能提示：缺失、损坏或写入失败时静默退回重新探测；协程取消仍然正常传播。
 */
internal class OkioNpmRegistrySelectionStore(
  rootDirectory: Path,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  },
) : NpmRegistrySelectionStore {
  private val statePath = rootDirectory / "registry-selection.json"

  override suspend fun read(): PersistedNpmRegistrySelection? {
    return withContext(ioDispatcher) {
      try {
        if (!fileSystem.exists(statePath)) return@withContext null
        json.decodeFromString<PersistedNpmRegistrySelection>(
          fileSystem.read(statePath) { readUtf8() },
        )
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: SerializationException) {
        null
      } catch (_: Throwable) {
        null
      }
    }
  }

  override suspend fun write(state: PersistedNpmRegistrySelection) {
    withContext(ioDispatcher) {
      val parent = requireNotNull(statePath.parent)
      val temporary = parent / ".${statePath.name}.${Random.nextLong().toString(16)}.tmp"
      try {
        fileSystem.createDirectories(parent)
        fileSystem.write(temporary, mustCreate = true) {
          writeUtf8(json.encodeToString(state))
        }
        fileSystem.atomicMove(temporary, statePath)
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Throwable) {
        // 选择状态只是性能提示，写入失败时保留内存结果并继续正常包加载。
      } finally {
        runCatching { fileSystem.delete(temporary, mustExist = false) }
      }
    }
  }
}

/**
 * 可跨进程复用的 registry 探测状态。
 *
 * [selectedAtEpochMillis] 只记录完成选优的时间，正常命中不会续期；旧 schema 不具备该字段时会由
 * [NpmRegistrySelector] 废弃并重新探测。
 */
@Serializable
internal data class PersistedNpmRegistrySelection(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val registryBaseUrls: List<String>,
  val completedProbeCount: Int = 0,
  val scores: List<PersistedNpmRegistryScore> = emptyList(),
  val preferredOrder: List<String> = emptyList(),
  val selectedAtEpochMillis: Long? = null,
  val consecutivePrimaryFailures: Int = 0,
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 2
  }
}

/** 单个 registry 在探测阶段的稳定评分字段。 */
@Serializable
internal data class PersistedNpmRegistryScore(
  val registryBaseUrl: String,
  val winCount: Int = 0,
  val failureCount: Int = 0,
  val totalWinnerDurationMicros: Long = 0,
)
