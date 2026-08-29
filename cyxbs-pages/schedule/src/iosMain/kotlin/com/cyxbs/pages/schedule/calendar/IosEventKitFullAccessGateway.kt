package com.cyxbs.pages.schedule.calendar

import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * EventKit full-access 授权状态。
 *
 * `WRITE_ONLY` 对本 gateway 等同于权限不足：本切片依赖 canonical 扫描、写后回读与精确删除，不能用仅写权限
 * 伪装成完整管理能力。
 */
enum class IosEventKitFullAccessStatus {
  NOT_DETERMINED,
  FULL_ACCESS,
  WRITE_ONLY,
  DENIED,
  RESTRICTED,
  UNKNOWN,
}

/** 显式 full-access 请求的结果；构造 gateway 不会触发此请求。 */
sealed interface IosEventKitPermissionResult {
  data object Granted : IosEventKitPermissionResult

  data class Rejected(val reason: IosEventKitGatewayFailure) : IosEventKitPermissionResult
}

/** 设置页可消费的只读 EventKit source；identifier 稳定、展示名只用于 picker，二者都不是所有权。 */
data class IosEventKitSettingsSource(
  val identifier: String,
  val displayName: String,
)

/** 设置层只读枚举的结果；普通加载绝不能借失败猜测默认 source。 */
sealed interface IosEventKitSettingsReadResult<out T> {
  data class Available<T>(val value: T) : IosEventKitSettingsReadResult<T>
  data object Unavailable : IosEventKitSettingsReadResult<Nothing>
}

/**
 * 缓存 source/calendar 的精确检查结果。
 *
 * `NoCalendarHint` 是 #280 的合法状态：设置阶段不创建 calendar，#281 首次原子导出成功才回填 identifier。
 * `Ambiguous` 覆盖无法证明唯一归属的有限历史/canonical 情形，调用方必须要求用户重新选择。
 */
enum class IosEventKitCachedSelection {
  Available,
  NoCalendarHint,
  SourceMissing,
  CalendarMissing,
  CalendarMovedToOtherSource,
  Ambiguous,
  Unavailable,
}

/**
 * 供 #280 controller 使用的最窄 EventKit 设置 seam。
 *
 * 只暴露权限状态、显式 full-access 请求、source picker 数据和缓存精确检查；不声明 upsert/delete，设置层没有
 * 创建日历、事件 reconcile 或任何 CRUD 能力。
 */
interface IosEventKitSettingsGateway {
  fun fullAccessStatus(): IosEventKitFullAccessStatus

  suspend fun requestFullAccess(): IosEventKitPermissionResult

  fun sources(): IosEventKitSettingsReadResult<List<IosEventKitSettingsSource>>

  fun checkCachedSelection(
    sourceIdentifier: String,
    calendarIdentifier: String?,
  ): IosEventKitCachedSelection
}

/**
 * EventKit identifier 缓存。
 *
 * 三个 identifier 都只用于减少扫描或定位用户已选择的 source，不构成所有权。事件/日历所有权最终只由
 * canonical v2 URL 与当前 [CalendarExportScope] 判定；title 从不参与认领。
 */
data class IosEventKitIdentifierHints(
  val sourceIdentifier: String,
  val calendarIdentifier: String? = null,
  val eventIdentifier: String? = null,
)

/**
 * 一次成功或明确 commit-entered-but-unknown 的 atomic calendar + first-event 尝试对应的 process-resident 恢复 key。
 *
 * key 同时冻结 store universe、账号 scope、用户明确选择的 source、projection identity/URI 与 target fingerprint；普通
 * non-atomic fixture 即使 identifier 字符串完全相同，也无法命中 gateway 私有 ledger 中的 eligibility。
 */
private data class IosEventKitAtomicRecoveryAttemptKey(
  /** 同一进程内的 EventKit store 身份；fake store 默认以对象 identity 隔离，生产 bridge 使用进程级 identity。 */
  val processIdentity: Any,
  val scope: CalendarExportScope,
  val sourceIdentifier: String,
  val projectionId: CalendarProjectionId,
  val canonicalUri: String,
  val targetFingerprint: String,
)

/**
 * gateway 签发、runtime 只能原样回传的窄 process capability。
 *
 * 构造出的普通对象不会自动获得资格：有效性只存在于本进程私有 ledger 中，并以对象 identity、当前 gateway issuer、
 * projection target 与完整 source/calendar/event binding 联合校验。该对象不序列化，也不承诺 process death 后可恢复。
 */
class IosEventKitLocatorRecoveryProof internal constructor()

/** locator 持久化 acknowledgement 的严格结果；拒绝不会消费任何其它资格。 */
enum class IosEventKitLocatorAcknowledgement {
  ACKNOWLEDGED,
  REJECTED,
}

/**
 * 旧 locator recovery eligibility 的一次性 retirement 结果。
 *
 * `RETIRED` 仅表示 gateway 已在当前进程内消费 exact capability；它不访问 EventKit、不证明 target 未变化，也不写
 * durable cache。`REJECTED` 不会消费任何其它 eligibility，调用方必须 fail-closed，禁止继续 Update/Delete CRUD。
 */
enum class IosEventKitLocatorEligibilityRetirement {
  RETIRED,
  REJECTED,
}

/** 生产 gateway 跨 AccountSession 实例共享、但不会离开当前进程的 EventKit recovery universe。 */
private object ProcessEventKitRecoveryIdentity

/**
 * 保存本进程尚未完成 durable locator acknowledgement 的 atomic pair 资格。
 *
 * 成功 atomic commit 与 store port 明确分型的 commit-entered unknown attempt 才建立资格。fresh proof 只刷新当前 proof identity，不消费资格；
 * runtime 必须在 calendar hint 与 event-ref ledger 都 durable、且 lifecycle/generation boundary 复核完成后显式 ack。只有
 * exact ack 才移除 entry。任一 cache/lifecycle 失败都会保留资格，供后续显式 intent 或新 AccountSession generation 恢复。
 */
private object IosEventKitAtomicRecoveryAttemptLedger {
  private class Eligibility(
    var binding: IosEventKitGatewayBinding?,
    var currentProof: IosEventKitLocatorRecoveryProof? = null,
    var currentIssuerIdentity: Any? = null,
  )

  private val lock = SynchronizedObject()
  private val attempts = mutableMapOf<IosEventKitAtomicRecoveryAttemptKey, Eligibility>()

  /**
   * 仅当 atomic port 已进入 commit、但提交终态未知时建立未绑定资格。
   *
   * configure 或 `commit=false` 排队阶段的确定失败即使统一映射为普通 `AMBIGUOUS`，也绝不能进入此入口；否则随后
   * 出现的 ordinary exact event 会借用错误 provenance 签发 proof。原失败 generation 仍必须 terminal-uncertain。
   */
  fun markCommitOutcomeUnknown(key: IosEventKitAtomicRecoveryAttemptKey) = synchronized(lock) {
    attempts[key] = Eligibility(binding = null)
  }

  /** atomic port 成功返回即建立 exact binding 资格；后续 readback 失败也不能遗漏该资格。 */
  fun markCommitted(
    key: IosEventKitAtomicRecoveryAttemptKey,
    binding: IosEventKitGatewayBinding,
  ) = synchronized(lock) {
    attempts[key] = Eligibility(binding = binding)
  }

  /** fresh 扫描前只读资格；该检查不签发 proof、更不消费 entry。 */
  fun isEligible(key: IosEventKitAtomicRecoveryAttemptKey): Boolean = synchronized(lock) {
    key in attempts
  }

  /**
   * 在完整 fresh authority/foundation/fingerprint 事实成立后签发新的 proof。
   *
   * ambiguous entry 首次在这里冻结 exact binding；已绑定 entry 必须完全相等。每次签发都会使更早 proof 变 stale，但资格
   * 仍保留，直到 runtime 完成 durable acknowledgement。
   */
  fun issueProof(
    key: IosEventKitAtomicRecoveryAttemptKey,
    binding: IosEventKitGatewayBinding,
    issuerIdentity: Any,
  ): IosEventKitLocatorRecoveryProof? = synchronized(lock) {
    val eligibility = attempts[key] ?: return@synchronized null
    val exactBinding = eligibility.binding
    if (exactBinding != null && exactBinding != binding) return@synchronized null
    eligibility.binding = binding
    val proof = IosEventKitLocatorRecoveryProof()
    eligibility.currentProof = proof
    eligibility.currentIssuerIdentity = issuerIdentity
    proof
  }

  /**
   * 只消费当前 issuer 签发的 latest proof，且 target/binding 必须与 ledger exact 相等。
   *
   * wrong/stale/duplicate/越权 ack 均返回 REJECTED，不会删除其它 entry；成功 ack 后资格一次性终结。
   */
  fun acknowledge(
    key: IosEventKitAtomicRecoveryAttemptKey,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
    issuerIdentity: Any,
  ): IosEventKitLocatorAcknowledgement = synchronized(lock) {
    if (!consumeExact(key, binding, proof, issuerIdentity)) {
      IosEventKitLocatorAcknowledgement.REJECTED
    } else {
      IosEventKitLocatorAcknowledgement.ACKNOWLEDGED
    }
  }

  /**
   * 在 Update/Delete 前一次性废止旧 target 的 recovery eligibility。
   *
   * retirement 与 acknowledgement 互斥地消费同一 entry；它只比较 gateway 私有 ledger 中的 exact key、binding、proof
   * object identity 与 issuer，不调用 store，也不把“已消费 capability”解释成 EventKit target 仍未变化。
   */
  fun retire(
    key: IosEventKitAtomicRecoveryAttemptKey,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
    issuerIdentity: Any,
  ): IosEventKitLocatorEligibilityRetirement = synchronized(lock) {
    if (!consumeExact(key, binding, proof, issuerIdentity)) {
      IosEventKitLocatorEligibilityRetirement.REJECTED
    } else {
      IosEventKitLocatorEligibilityRetirement.RETIRED
    }
  }

  /** 锁内 exact 一次性消费；所有 mismatch 都必须原样保留当前及其它 eligibility。 */
  private fun consumeExact(
    key: IosEventKitAtomicRecoveryAttemptKey,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
    issuerIdentity: Any,
  ): Boolean {
    val eligibility = attempts[key] ?: return false
    if (eligibility.binding != binding || eligibility.currentProof !== proof ||
      eligibility.currentIssuerIdentity !== issuerIdentity
    ) {
      return false
    }
    attempts.remove(key)
    return true
  }

  /** fresh authority 已确认没有既有 target、即将开始新 atomic attempt 时，旧资格不再为后续对象背书。 */
  fun discard(key: IosEventKitAtomicRecoveryAttemptKey) {
    synchronized(lock) { attempts.remove(key) }
  }
}

/** 成功写入后返回给未来 link 持久化层的最新 opaque cache。 */
data class IosEventKitGatewayBinding(
  val sourceIdentifier: String,
  val calendarIdentifier: String,
  val eventIdentifier: String,
)

/** Gateway 操作结果；失败只描述平台投影，调用方不得据此删除或改写 Schedule。 */
sealed interface IosEventKitGatewayResult {
  data class Upserted(
    val binding: IosEventKitGatewayBinding,
    val changed: Boolean,
    /** 仅同一 pending queue 的 calendar+首 event 原子 commit 可授权回填原本为空的 calendar hint。 */
    val atomicCalendarAndFirstEvent: Boolean = false,
    /**
     * 成功 atomic commit 或 fresh canonical recovery 签发的 process-resident capability。
     *
     * proof 本身不消费 eligibility；runtime 只有在 calendar + event ledger 均 durable 后才能向同一 gateway 显式 ack。
     */
    val locatorRecoveryProof: IosEventKitLocatorRecoveryProof? = null,
  ) : IosEventKitGatewayResult

  data class Deleted(
    val calendarIdentifier: String?,
    val changed: Boolean,
  ) : IosEventKitGatewayResult

  data class Failed(
    val reason: IosEventKitGatewayFailure,
    val mappingError: IosEventKitMappingError? = null,
  ) : IosEventKitGatewayResult
}

/**
 * runtime 使用的严格 direct lookup 结果。
 *
 * 该 seam 只按持久化的 opaque event ref 调 `eventByIdentifier`，然后同时核验 selected source、calendar、canonical
 * v2 URI、scope 与 foundation 映射；没有 ref 时绝不按 title、默认 calendar 或全日历扫描认领。
 */
sealed interface IosEventKitVerifiedEventLookup {
  data class Managed(
    val event: ManagedCalendarEvent,
    /** locator 已 durable 但先前 ack 未完成时，由本次 fresh authority lookup 重签的 capability。 */
    val locatorRecoveryProof: IosEventKitLocatorRecoveryProof? = null,
    /** proof 非空时与其 exact 绑定；普通 direct lookup 保持 null。 */
    val recoveryBinding: IosEventKitGatewayBinding? = null,
  ) : IosEventKitVerifiedEventLookup

  /**
   * 该 ref 的 source/calendar/canonical URI/scope 全部正确，但 foundation 无法安全表示其已受管平台形状。
   *
   * 这不是 foreign identity：runtime 必须保留 calendar 与所有 ledger，而不是删除/降级 master 或清空全局 locator。
   */
  data class UnsupportedManaged(
    val projectionId: CalendarProjectionId,
    val mappingError: IosEventKitMappingError,
  ) : IosEventKitVerifiedEventLookup

  data object KnownAbsent : IosEventKitVerifiedEventLookup
  data class Blocked(val failure: IosEventKitGatewayFailure) : IosEventKitVerifiedEventLookup
}

/**
 * runtime 的窄 EventKit seam。
 *
 * fake 只需实现这些 outbound/direct-lookup/capability 操作，测试不构造 `EKEventStore`；生产实现仍是
 * [IosEventKitFullAccessGateway]，其余 settings/bridge API 不暴露给 runtime。
 */
internal interface IosScheduleCalendarRuntimeGateway {
  fun fullAccessStatus(): IosEventKitFullAccessStatus

  fun lookupVerified(
    projectionId: CalendarProjectionId,
    eventRef: PlatformCalendarEventRef,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitVerifiedEventLookup

  fun upsert(
    projection: CalendarEventProjection,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitGatewayResult

  /**
   * runtime 在 Update/Delete CRUD 前废止 preflight lookup 携带的旧 target eligibility。
   *
   * 实现只可消费同一 issuer 签发的 exact latest proof、旧 managed target 与完整 binding；不得访问 EventKit、写 cache，
   * 或把 retirement 成功解释为 target 未变化。拒绝或异常后调用方必须零 CRUD 并终结当前 generation。
   */
  fun retireLocatorRecoveryEligibility(
    event: ManagedCalendarEvent,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
  ): IosEventKitLocatorEligibilityRetirement

  /**
   * runtime 在 calendar hint 与 event-ref ledger 均 durable 且 lifecycle/generation 复核完成后显式确认。
   *
   * 实现只能消费当前 gateway issuer 的 exact latest proof；拒绝、stale 或重复 ack 不得影响其它 eligibility。
   */
  fun acknowledgeLocatorPersistence(
    projection: CalendarEventProjection,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
  ): IosEventKitLocatorAcknowledgement

  fun deleteKnown(
    projectionId: CalendarProjectionId,
    eventRef: PlatformCalendarEventRef,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitGatewayResult
}

/**
 * Gateway 的 fail-closed 分类。
 *
 * `STORE_AMBIGUOUS` 包括 NSError 缺失、保存结果不一致及其他无法安全判断是否已提交的 EventKit 失败；这类
 * 失败只能交给后续显式重查/恢复，绝不能补偿性删除 Schedule 或盲目重试创建。
 */
enum class IosEventKitGatewayFailure {
  PERMISSION_REQUIRED,
  PERMISSION_DENIED,
  PERMISSION_REVOKED,
  SOURCE_DISAPPEARED,
  CALENDAR_DISAPPEARED,
  AMBIGUOUS_CALENDAR,
  AMBIGUOUS_EVENT,
  FOREIGN_IDENTITY,
  UNSUPPORTED_PROJECTION,
  READ_AFTER_WRITE_MISMATCH,
  STORE_AMBIGUOUS,
}

/** EventKit source 的最小只读快照；identifier 是用户选择 source 的可失效缓存。 */
internal data class IosEventKitSourceSnapshot(
  val identifier: String,
  val supportsEvents: Boolean,
  /** 用户 picker 的展示名；绝不用于 source/calendar 所有权认领。 */
  val displayName: String = identifier,
)

/** EventKit calendar 的最小只读快照；title 故意不进入 gateway 所有权判断。 */
internal data class IosEventKitCalendarSnapshot(
  val identifier: String,
  val sourceIdentifier: String,
  val allowsContentModifications: Boolean,
)

/** Gateway 与真实 EKEventStore 之间的事件快照。 */
internal data class IosEventKitStoreEventSnapshot(
  val calendarIdentifier: String,
  val raw: IosEventKitRawEvent,
)

/**
 * EventKit canonical 扫描窗口。
 *
 * `predicateForEvents` 单次最多查询四年；gateway 按目标投影生成不超过该上限的窗口，bridge 不得再用
 * `distantPast..distantFuture` 让系统静默截断到错误年代。
 */
internal data class IosEventKitScanWindow(
  val startEpochSeconds: Long,
  val endEpochSeconds: Long,
) {
  init {
    require(startEpochSeconds < endEpochSeconds)
    require(endEpochSeconds - startEpochSeconds <= MAX_EVENTKIT_SCAN_SECONDS)
  }

  internal companion object {
    const val MAX_EVENTKIT_SCAN_SECONDS = 1_460L * 24L * 60L * 60L
  }
}

/** 首次原子创建 calendar + event 后的 committed cache；两者必须来自同一次 store commit。 */
internal data class IosEventKitCreatedEventSnapshot(
  val calendar: IosEventKitCalendarSnapshot,
  val eventIdentifier: String,
)


/**
 * EventKit store port 的失败阶段，不向上泄露可识别 scope 或平台错误文本。
 *
 * [AMBIGUOUS] 是普通 store 不确定失败，也覆盖 atomic create 在 configure/排队阶段已 reset、因而没有 durable pair 的
 * 失败；它只能终结当前 generation，不能建立 recovery eligibility。[ATOMIC_COMMIT_OUTCOME_UNKNOWN] 仅允许
 * `createCalendarWithEvent` 在 calendar/event 都已排队且真正进入 commit 后返回，用于证明“可能已有 durable pair”的
 * process-resident provenance。两者对 runtime 都投影为 `STORE_AMBIGUOUS`，但只有后者能在 fresh authority 后签发 proof。
 */
internal enum class IosEventKitStoreFailure {
  NOT_FOUND,
  ACCESS_LOST,
  AMBIGUOUS,
  ATOMIC_COMMIT_OUTCOME_UNKNOWN,
}

/** 真实 bridge 与 fake 共享的严格返回值。 */
internal sealed interface IosEventKitStoreResult<out T> {
  data class Success<T>(val value: T) : IosEventKitStoreResult<T>

  data class Failure(val reason: IosEventKitStoreFailure) : IosEventKitStoreResult<Nothing>
}

internal interface IosEventKitStorePort {
  fun authorizationStatus(): IosEventKitFullAccessStatus

  suspend fun requestFullAccess(): IosEventKitStoreResult<IosEventKitFullAccessStatus>

  fun sources(): IosEventKitStoreResult<List<IosEventKitSourceSnapshot>>

  fun calendars(): IosEventKitStoreResult<List<IosEventKitCalendarSnapshot>>

  fun eventByIdentifier(identifier: String): IosEventKitStoreResult<IosEventKitStoreEventSnapshot?>

  fun events(
    calendarIdentifier: String,
    window: IosEventKitScanWindow,
  ): IosEventKitStoreResult<List<IosEventKitStoreEventSnapshot>>

  /**
   * 在同一待提交队列中创建隔离 calendar 与首个 canonical event，再一次性 commit。
   *
   * 实现不得先单独提交空 calendar；configure 或任一 `commit=false` 排队步骤失败都必须 reset 未提交队列并返回普通
   * [IosEventKitStoreFailure.AMBIGUOUS]，该结果没有 recovery provenance。只有 calendar/event 均已排队且真正进入 commit 后
   * 终态不明，才能返回 [IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN]，由后续 canonical 扫描恢复。
   */
  fun createCalendarWithEvent(
    sourceIdentifier: String,
    displayTitle: String,
    payload: IosEventKitWritePayload,
  ): IosEventKitStoreResult<IosEventKitCreatedEventSnapshot>

  fun saveEvent(
    calendarIdentifier: String,
    existingEventIdentifier: String?,
    payload: IosEventKitWritePayload,
  ): IosEventKitStoreResult<String>

  fun removeEvent(eventIdentifier: String): IosEventKitStoreResult<Unit>
}

/**
 * iOS EventKit full-access gateway。
 *
 * 生产构造器只创建并持有一个 [IosEventKitStoreBridge]；不会在构造、repository 初始化或后台回调中请求权限。
 * 只有调用 [requestFullAccess] 才可能展示系统授权。所有 CRUD 在进入 store 前重新检查 full access，并在平台
 * 返回失败后再次检查撤权状态；gateway 从不持有 Schedule repository，因此失败没有删除或修改 Schedule 的路径。
 */
class IosEventKitFullAccessGateway private constructor(
  private val scope: CalendarExportScope,
  private val store: IosEventKitStorePort,
  /**
   * 作为有界 scope 恢复窗口的当前时刻锚点。
   *
   * 测试显式固定该值，以验证目标窗口之外的历史 canonical identity；生产入口取当前时刻，绝不以
   * `distantPast..distantFuture` 规避 EventKit 的单 predicate 四年上限。
   */
  private val scopeRecoveryAnchorEpochSeconds: Long,
  /** eligibility 的 process/store universe；不使用 source/calendar/event 字符串充当跨实例 capability。 */
  private val recoveryProcessIdentity: Any,
) : IosEventKitSettingsGateway, IosScheduleCalendarRuntimeGateway {
  /** 每个 gateway 实例独有的 proof issuer；ack 必须回到签发 proof 的同一实例。 */
  private val recoveryProofIssuerIdentity = Any()

  /** iosTest 入口：同一个内存 store 对象共享 recovery universe，不同 fake store 绝不串资格。 */
  internal constructor(
    scope: CalendarExportScope,
    store: IosEventKitStorePort,
    scopeRecoveryAnchorEpochSeconds: Long = Clock.System.now().epochSeconds,
  ) : this(scope, store, scopeRecoveryAnchorEpochSeconds, store)

  /** 生产入口；不同 AccountSession/runtime gateway 共享本进程 EventKit recovery universe。 */
  constructor(scope: CalendarExportScope) : this(
    scope = scope,
    store = IosEventKitStoreBridge(),
    scopeRecoveryAnchorEpochSeconds = Clock.System.now().epochSeconds,
    recoveryProcessIdentity = ProcessEventKitRecoveryIdentity,
  )

  /**
   * 由未来设置页的明确用户操作调用 full-access 请求。
   *
   * iOS 17+ 使用 full-access API；部署目标覆盖的旧系统由 bridge 使用 legacy event access，它在旧系统上等价于
   * 完整事件访问。回调成功后仍重新读取授权状态，避免把 write-only 或模糊回调误报为成功。
   */
  override suspend fun requestFullAccess(): IosEventKitPermissionResult {
    val result = store.requestFullAccess()
    val status = (result as? IosEventKitStoreResult.Success)?.value
      ?: return IosEventKitPermissionResult.Rejected(failureAfterStoreError())
    return if (status == IosEventKitFullAccessStatus.FULL_ACCESS) {
      IosEventKitPermissionResult.Granted
    } else {
      IosEventKitPermissionResult.Rejected(status.toPermissionFailure(explicitRequest = true))
    }
  }

  /** 当前系统授权状态的只读投影；不请求权限，也不读取或变更 source/calendar。 */
  override fun fullAccessStatus(): IosEventKitFullAccessStatus = store.authorizationStatus()

  /**
   * 枚举可供 picker 明确选择的 EventKit source。
   *
   * 只有 full access 才读取 source；失败保持 `Unavailable`，调用方不得把它降级为默认 source 或空列表。
   */
  override fun sources(): IosEventKitSettingsReadResult<List<IosEventKitSettingsSource>> {
    if (fullAccessStatus() != IosEventKitFullAccessStatus.FULL_ACCESS) {
      return IosEventKitSettingsReadResult.Unavailable
    }
    return when (val result = store.sources()) {
      is IosEventKitStoreResult.Success -> {
        val sources = result.value.filter { it.supportsEvents }
          .groupBy { it.identifier }
          .mapNotNull { (identifier, values) ->
            values.singleOrNull()?.let { source ->
              IosEventKitSettingsSource(identifier, source.displayName)
            }
          }
        if (sources.size != result.value.count { it.supportsEvents }) {
          IosEventKitSettingsReadResult.Unavailable
        } else {
          IosEventKitSettingsReadResult.Available(sources.sortedBy { it.displayName })
        }
      }

      is IosEventKitStoreResult.Failure -> IosEventKitSettingsReadResult.Unavailable
    }
  }

  /**
   * 精确检查缓存 source 与 calendar 的当前关系。
   *
   * 不按 title/default source/default calendar 回退；calendar 被移动到其它 source、重复 identifier、只读或底层
   * 查询失败均 fail-closed。这里不扫描 canonical identity，因此无法由有限历史证明的 ownership 必须由后续导出
   * gateway 在 #281 再次检查，设置页只保留用户重新选择的显式入口。
   */
  override fun checkCachedSelection(
    sourceIdentifier: String,
    calendarIdentifier: String?,
  ): IosEventKitCachedSelection {
    if (fullAccessStatus() != IosEventKitFullAccessStatus.FULL_ACCESS) {
      return IosEventKitCachedSelection.Unavailable
    }
    val source = when (val result = store.sources()) {
      is IosEventKitStoreResult.Success -> result.value.filter {
        it.identifier == sourceIdentifier && it.supportsEvents
      }

      is IosEventKitStoreResult.Failure -> return IosEventKitCachedSelection.Unavailable
    }
    if (source.isEmpty()) return IosEventKitCachedSelection.SourceMissing
    if (source.size != 1) return IosEventKitCachedSelection.Ambiguous
    if (calendarIdentifier == null) return IosEventKitCachedSelection.NoCalendarHint
    val calendars = when (val result = store.calendars()) {
      is IosEventKitStoreResult.Success -> result.value.filter { it.identifier == calendarIdentifier }
      is IosEventKitStoreResult.Failure -> return IosEventKitCachedSelection.Unavailable
    }
    if (calendars.isEmpty()) return IosEventKitCachedSelection.CalendarMissing
    if (calendars.size != 1) return IosEventKitCachedSelection.Ambiguous
    val calendar = calendars.single()
    if (calendar.sourceIdentifier != sourceIdentifier) {
      return IosEventKitCachedSelection.CalendarMovedToOtherSource
    }
    // 不能因为 opaque calendar id 尚能命中就默认接管。仅使用最多四年的当前恢复窗口检查 canonical identity；
    // 缓存日历在该有限窗口内没有可证明的当前 scope、混入 foreign scope 或读取失败时均不返回 Available，要求
    // 用户重新选择而不是把有限历史的空白猜成所有权。
    val authority = IosEventKitScanWindow(
      startEpochSeconds = scopeRecoveryAnchorEpochSeconds - EVENTKIT_SCAN_HALF_SECONDS,
      endEpochSeconds = scopeRecoveryAnchorEpochSeconds + EVENTKIT_SCAN_HALF_SECONDS,
    )
    return when (inspectCalendar(calendar, listOf(authority))) {
      CalendarOwnership.Owned -> if (calendar.allowsContentModifications) {
        IosEventKitCachedSelection.Available
      } else {
        IosEventKitCachedSelection.Ambiguous
      }

      CalendarOwnership.Unowned,
      CalendarOwnership.Foreign -> IosEventKitCachedSelection.Ambiguous
      is CalendarOwnership.Failed -> IosEventKitCachedSelection.Unavailable
    }
  }

  /**
   * 用持久化 event ref 直接确认一个受管投影。
   *
   * 此方法刻意不调用 `events()`：ref 缺失只能视为缺失，不能通过 title/default source 或全日历扫描扩大所有权。
   * foundation 重新计算 canonical identity 与 fingerprint，故 opaque ref 复用、跨 calendar/source 或 platform 改写均
   * fail-closed。
   */
  override fun lookupVerified(
    projectionId: CalendarProjectionId,
    eventRef: PlatformCalendarEventRef,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitVerifiedEventLookup {
    requireFullAccess()?.let { return IosEventKitVerifiedEventLookup.Blocked(it.reason) }
    val calendarIdentifier = hints.calendarIdentifier
      ?: return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.CALENDAR_DISAPPEARED)
    val sources = when (val result = store.sources()) {
      is IosEventKitStoreResult.Success -> result.value.filter {
        it.identifier == hints.sourceIdentifier && it.supportsEvents
      }
      is IosEventKitStoreResult.Failure -> return IosEventKitVerifiedEventLookup.Blocked(
        storeFailure(result.reason, null, hints).reason,
      )
    }
    if (sources.isEmpty()) return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.SOURCE_DISAPPEARED)
    if (sources.size != 1) return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR)
    val calendars = when (val result = store.calendars()) {
      is IosEventKitStoreResult.Success -> result.value.filter { it.identifier == calendarIdentifier }
      is IosEventKitStoreResult.Failure -> return IosEventKitVerifiedEventLookup.Blocked(
        storeFailure(result.reason, null, hints).reason,
      )
    }
    if (calendars.isEmpty()) return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.CALENDAR_DISAPPEARED)
    if (calendars.size != 1 || calendars.single().sourceIdentifier != hints.sourceIdentifier ||
      !calendars.single().allowsContentModifications
    ) {
      return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR)
    }
    val snapshot = when (val result = store.eventByIdentifier(eventRef.value)) {
      is IosEventKitStoreResult.Success -> result.value
      is IosEventKitStoreResult.Failure -> return IosEventKitVerifiedEventLookup.Blocked(
        storeFailure(result.reason, calendars.single(), hints).reason,
      )
    } ?: return IosEventKitVerifiedEventLookup.KnownAbsent
    if (snapshot.calendarIdentifier != calendarIdentifier || snapshot.raw.eventIdentifier != eventRef.value) {
      return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.FOREIGN_IDENTITY)
    }
    // `toManagedEvent()` 会优先报告 occurrence exception。direct lookup 的保守分支只能用于确属当前 ledger
    // projection 的 master，因此先独立验证 canonical URI、scope 与 projection identity，不能让早退掩盖 foreign ref。
    if (!hasExpectedCanonicalIdentity(snapshot.raw, projectionId)) {
      return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.FOREIGN_IDENTITY)
    }
    // 不论 direct ref 本身能否被 foundation 表示，都必须先检查其真实时间窗内的 canonical sibling。否则 occurrence
    // master 的 UnsupportedManaged 早退会掩盖重复身份，runtime 将错误保留 ledger 并继续同代读取。
    val canonicalUri = CalendarProjectionUriCodec.encode(projectionId)
    val siblingIdentifiers = when (val result = store.events(calendarIdentifier, directLookupWindow(snapshot.raw))) {
      is IosEventKitStoreResult.Success -> result.value
        .filter { it.raw.externalUri == canonicalUri }
        .map { it.raw.eventIdentifier ?: return IosEventKitVerifiedEventLookup.Blocked(
          IosEventKitGatewayFailure.AMBIGUOUS_EVENT,
        ) }
      is IosEventKitStoreResult.Failure -> return IosEventKitVerifiedEventLookup.Blocked(
        storeFailure(result.reason, calendars.single(), hints).reason,
      )
    }
    if (siblingIdentifiers.distinct() != listOf(eventRef.value)) {
      return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.AMBIGUOUS_EVENT)
    }
    val mapped = when (val result = IosEventKitCalendarAdapterFoundation.toManagedEvent(snapshot.raw, scope)) {
      is IosEventKitMappingResult.Mapped -> result.value.managedEvent
      is IosEventKitMappingResult.Unsupported -> {
        // occurrence exception 本身不参与 fingerprint；移除这一唯一不可表示形状后仍须能严格映射，才证明 URI、scope、
        // projection shape 和所有用于 fingerprint 的字段完整。其它 unsupported/drift 一律不能保留为受管 master。
        val withoutOccurrence = snapshot.raw.takeIf { result.error ==
          IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION && it.hasOccurrenceException
        }?.copy(hasOccurrenceException = false)
        val canonicalMaster = withoutOccurrence?.let {
          (IosEventKitCalendarAdapterFoundation.toManagedEvent(it, scope) as? IosEventKitMappingResult.Mapped)
            ?.value
            ?.managedEvent
        }
        if (canonicalMaster?.id != projectionId) {
          return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.FOREIGN_IDENTITY)
        }
        return IosEventKitVerifiedEventLookup.UnsupportedManaged(
          projectionId = projectionId,
          mappingError = result.error,
        )
      }
    }
    if (mapped.id != projectionId) return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.FOREIGN_IDENTITY)
    val recoveryAttempt = recoveryAttemptKey(
      projectionId = mapped.id,
      canonicalUri = canonicalUri,
      targetFingerprint = mapped.fingerprint,
      sourceIdentifier = hints.sourceIdentifier,
    )
    if (IosEventKitAtomicRecoveryAttemptLedger.isEligible(recoveryAttempt)) {
      // calendar + ledger 可能已 durable，但 lifecycle 在 ack 前失效。direct ref 命中仍不能直接消费资格；必须重新执行
      // source/calendar authority 与 target + recovery 全窗口唯一性，再签发 latest proof 交给 runtime durable reread 后 ack。
      val targetWindow = directLookupWindow(snapshot.raw)
      val refreshedCalendar = when (val refreshed = selectExistingCalendar(
        hints = hints.copy(eventIdentifier = eventRef.value),
        targetWindow = targetWindow,
      )) {
        is CalendarSelection.Selected -> refreshed.calendar
        CalendarSelection.Absent -> return IosEventKitVerifiedEventLookup.Blocked(
          IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
        )
        is CalendarSelection.Failed -> return IosEventKitVerifiedEventLookup.Blocked(refreshed.result.reason)
      }
      if (refreshedCalendar.identifier != calendarIdentifier) {
        return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
      }
      val confirmed = when (val located = locateTargetAcrossWindows(
        calendar = refreshedCalendar,
        id = projectionId,
        eventIdentifierHint = eventRef.value,
        windows = authorityWindows(targetWindow),
      )) {
        is EventSelection.Selected -> located.event
        EventSelection.Absent -> return IosEventKitVerifiedEventLookup.Blocked(
          IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
        )
        is EventSelection.Failed -> return IosEventKitVerifiedEventLookup.Blocked(located.result.reason)
      }
      val freshMapped = when (val result = IosEventKitCalendarAdapterFoundation.toManagedEvent(confirmed.raw, scope)) {
        is IosEventKitMappingResult.Mapped -> result.value.managedEvent
        is IosEventKitMappingResult.Unsupported -> return IosEventKitVerifiedEventLookup.Blocked(
          IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
        )
      }
      if (freshMapped.id != mapped.id || freshMapped.fingerprint != mapped.fingerprint) {
        return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
      }
      val recoveryBinding = binding(refreshedCalendar, confirmed.requireEventIdentifier())
      val proof = IosEventKitAtomicRecoveryAttemptLedger.issueProof(
        key = recoveryAttempt,
        binding = recoveryBinding,
        issuerIdentity = recoveryProofIssuerIdentity,
      ) ?: return IosEventKitVerifiedEventLookup.Blocked(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
      return IosEventKitVerifiedEventLookup.Managed(
        event = ManagedCalendarEvent(mapped.id, mapped.fingerprint, eventRef),
        locatorRecoveryProof = proof,
        recoveryBinding = recoveryBinding,
      )
    }
    return IosEventKitVerifiedEventLookup.Managed(
      event = ManagedCalendarEvent(mapped.id, mapped.fingerprint, eventRef),
    )
  }

  /**
   * 验证 direct ref 的 raw URI 是本次 ledger projection 的 canonical v2 identity。
   *
   * foundation 对 occurrence exception 会早退，故不能依赖其映射结果来判断 URI/scope。这里要求完整 decode 后的
   * [CalendarProjectionId] 完全相等，避免同一个 event ref 被跨账号、跨投影或伪造 URI 的 occurrence 占用时仍被
   * 当成 `UnsupportedManaged` 保留。
   */
  private fun hasExpectedCanonicalIdentity(
    raw: IosEventKitRawEvent,
    projectionId: CalendarProjectionId,
  ): Boolean = raw.externalUri
    ?.let(CalendarProjectionUriCodec::decodeOrNull)
    ?.let { it == projectionId }
    ?: false

  /**
   * 删除已被 direct lookup 严格验证的一个 event。
   *
   * 没有 ref 不存在删除路径；删除后再次精确读取相同 identifier，只有确认 absent 才向 runtime 返回 `Deleted`。
   */
  override fun deleteKnown(
    projectionId: CalendarProjectionId,
    eventRef: PlatformCalendarEventRef,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitGatewayResult {
    when (val verified = lookupVerified(projectionId, eventRef, hints)) {
      is IosEventKitVerifiedEventLookup.Blocked -> return IosEventKitGatewayResult.Failed(verified.failure)
      is IosEventKitVerifiedEventLookup.UnsupportedManaged -> return IosEventKitGatewayResult.Failed(
        IosEventKitGatewayFailure.UNSUPPORTED_PROJECTION,
        verified.mappingError,
      )
      IosEventKitVerifiedEventLookup.KnownAbsent -> return IosEventKitGatewayResult.Deleted(
        calendarIdentifier = hints.calendarIdentifier,
        changed = false,
      )
      is IosEventKitVerifiedEventLookup.Managed -> Unit
    }
    val calendarIdentifier = hints.calendarIdentifier
      ?: return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.CALENDAR_DISAPPEARED)
    // lookupVerified 已在删除前确认 ref 的身份；这里取真实 start 只用于锁定删除后的有限 canonical absence 窗口。
    // 若 identifier 在两次调用之间被 EventKit 复用、迁移或改写，也宁可拒绝删除而不扩大认领范围。
    val beforeDelete = when (val result = store.eventByIdentifier(eventRef.value)) {
      is IosEventKitStoreResult.Success -> result.value
      is IosEventKitStoreResult.Failure -> return storeFailure(result.reason, null, hints)
    } ?: return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
    if (beforeDelete.calendarIdentifier != calendarIdentifier ||
      beforeDelete.raw.eventIdentifier != eventRef.value ||
      !hasExpectedCanonicalIdentity(beforeDelete.raw, projectionId)
    ) {
      return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.FOREIGN_IDENTITY)
    }
    val deletionWindow = directLookupWindow(beforeDelete.raw)
    val canonicalUri = CalendarProjectionUriCodec.encode(projectionId)
    when (val removed = store.removeEvent(eventRef.value)) {
      is IosEventKitStoreResult.Success -> Unit
      is IosEventKitStoreResult.Failure -> return storeFailure(removed.reason, null, hints)
    }
    when (val readBack = store.eventByIdentifier(eventRef.value)) {
      is IosEventKitStoreResult.Success -> if (readBack.value != null) {
        return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
      }
      is IosEventKitStoreResult.Failure -> return storeFailure(readBack.reason, null, hints)
    }
    // identifier 消失不足以证明删除完整：EventKit 系列拆分、identifier 变化或并发 actor 都可能留下同 canonical URI 的
    // sibling。只有原有界窗口内不存在该 URI，runtime 才能安全移除 ledger locator。
    return when (val remaining = store.events(calendarIdentifier, deletionWindow)) {
      is IosEventKitStoreResult.Success -> if (remaining.value.none { it.raw.externalUri == canonicalUri }) {
        IosEventKitGatewayResult.Deleted(calendarIdentifier, changed = true)
      } else {
        IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
      }
      is IosEventKitStoreResult.Failure -> storeFailure(remaining.reason, null, hints)
    }
  }

  /**
   * 在 app 隔离日历内幂等创建或更新投影。
   *
   * identifier 命中后必须再次验证 canonical URL；miss 时在目标投影对应的 EventKit 安全窗口内扫描 canonical
   * URL 恢复。首次创建会把 calendar 与 event 放进同一次 store commit，避免首个事件失败时泄漏空日历。保存后
   * 以 canonical URL 重新读取、重新经过 foundation 映射及 fingerprint 比对。
   */
  override fun upsert(
    projection: CalendarEventProjection,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitGatewayResult {
    requireFullAccess()?.let { return it }
    val payload =
      when (val mapped = IosEventKitCalendarAdapterFoundation.toWritePayload(projection)) {
        is IosEventKitMappingResult.Mapped -> mapped.value
        is IosEventKitMappingResult.Unsupported -> return IosEventKitGatewayResult.Failed(
          IosEventKitGatewayFailure.UNSUPPORTED_PROJECTION,
          mapped.error,
        )
      }
    val window = payload.scanWindow()
    val recoveryAttempt = recoveryAttemptKey(projection, hints.sourceIdentifier)
    val calendar = when (val selection = selectExistingCalendar(hints, window)) {
      is CalendarSelection.Selected -> selection.calendar
      CalendarSelection.Absent -> {
        // fresh authority 已确认当前没有目标 event；若随后需要重新创建，旧 unknown attempt 不能继续为未来普通 event 背书。
        IosEventKitAtomicRecoveryAttemptLedger.discard(recoveryAttempt)
        val created = when (val result = store.createCalendarWithEvent(
          sourceIdentifier = hints.sourceIdentifier,
          displayTitle = MANAGED_CALENDAR_DISPLAY_TITLE,
          payload = payload,
        )) {
          is IosEventKitStoreResult.Success -> result.value
          is IosEventKitStoreResult.Failure -> {
            if (result.reason == IosEventKitStoreFailure.ATOMIC_COMMIT_OUTCOME_UNKNOWN) {
              // 只有已进入 commit 的 unknown outcome 才可能留下 durable pair；pre-commit failure 即使分类为 AMBIGUOUS，
              // 也不能建立 eligibility，避免未来 ordinary exact event 借用错误 provenance 签发 proof。
              IosEventKitAtomicRecoveryAttemptLedger.markCommitOutcomeUnknown(recoveryAttempt)
            }
            return storeFailure(result.reason, null, hints)
          }
        }
        val committedBinding = IosEventKitGatewayBinding(
          sourceIdentifier = created.calendar.sourceIdentifier,
          calendarIdentifier = created.calendar.identifier,
          eventIdentifier = created.eventIdentifier,
        )
        // store 已明确返回 atomic success 就必须立即建立资格。后续 authority/readback 或 runtime cache 失败都不能遗漏恢复权。
        IosEventKitAtomicRecoveryAttemptLedger.markCommitted(recoveryAttempt, committedBinding)
        // 两个 gateway 可能同时从同一旧快照得出 Absent。首次原子提交后必须重新枚举 source/scope：
        // 任何并发创建出的第二个 canonical calendar 都不能被“自己的 read-after-write 成功”掩盖。
        val confirmedCalendar = when (
          val refreshed = selectExistingCalendar(
            hints = hints.copy(
              calendarIdentifier = created.calendar.identifier,
              eventIdentifier = created.eventIdentifier,
            ),
            targetWindow = window,
          )
        ) {
          is CalendarSelection.Selected -> refreshed.calendar
          CalendarSelection.Absent -> return IosEventKitGatewayResult.Failed(
            IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
          )

          is CalendarSelection.Failed -> return refreshed.result
        }
        if (confirmedCalendar.identifier != created.calendar.identifier) {
          return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
        }
        val confirmed = confirmUpsert(
          projection = projection,
          calendar = confirmedCalendar,
          eventIdentifier = created.eventIdentifier,
          window = window,
          changed = true,
          atomicCalendarAndFirstEvent = true,
        )
        if (confirmed !is IosEventKitGatewayResult.Upserted) return confirmed
        val proof = IosEventKitAtomicRecoveryAttemptLedger.issueProof(
          key = recoveryAttempt,
          binding = confirmed.binding,
          issuerIdentity = recoveryProofIssuerIdentity,
        ) ?: return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
        return confirmed.copy(locatorRecoveryProof = proof)
      }

      is CalendarSelection.Failed -> return selection.result
    }
    val existing =
      when (val located = locateTarget(calendar, projection.id, hints.eventIdentifier, window)) {
        is EventSelection.Selected -> located.event
        EventSelection.Absent -> null
        is EventSelection.Failed -> return located.result
      }
    if (existing != null) {
      val managed = when (val mapped =
        IosEventKitCalendarAdapterFoundation.toManagedEvent(existing.raw, scope)) {
        is IosEventKitMappingResult.Mapped -> mapped.value
        is IosEventKitMappingResult.Unsupported -> return IosEventKitGatewayResult.Failed(
          IosEventKitGatewayFailure.UNSUPPORTED_PROJECTION,
          mapped.error,
        )
      }
      if (managed.managedEvent.id != projection.id) return foreignIdentity()
      val existingIdentifier = existing.requireEventIdentifier()
      val requiresRecoveryAuthority = hints.eventIdentifier == null
      val hasCurrentEligibility = IosEventKitAtomicRecoveryAttemptLedger.isEligible(recoveryAttempt)
      val recoveryEligible = requiresRecoveryAuthority &&
        (hints.calendarIdentifier == null || hints.calendarIdentifier == calendar.identifier) &&
        hasCurrentEligibility
      val durableLocatorAwaitingAcknowledgement = hints.calendarIdentifier == calendar.identifier &&
        hints.eventIdentifier == existingIdentifier && hasCurrentEligibility
      if (requiresRecoveryAuthority && !recoveryEligible) {
        // 没有 event ledger 的 ordinary existing/partial locator 不能只凭 canonical equality 被接管。
        return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
      }
      if (managed.managedEvent.fingerprint == projection.fingerprint) {
        if (recoveryEligible || durableLocatorAwaitingAcknowledgement) {
          // proof 不能复用前半段 observation：空/partial locator 的恢复，以及 locator 已 durable 但 ack 前 lifecycle 丢失的
          // 收尾，都必须再次 fresh 枚举 source/calendar authority。签发 proof 不消费 eligibility，失败后仍可重签。
          val refreshedCalendar = when (val refreshed = selectExistingCalendar(
            hints = hints.copy(
              calendarIdentifier = calendar.identifier,
              eventIdentifier = existingIdentifier,
            ),
            targetWindow = window,
          )) {
            is CalendarSelection.Selected -> refreshed.calendar
            CalendarSelection.Absent -> return IosEventKitGatewayResult.Failed(
              IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
            )

            is CalendarSelection.Failed -> return refreshed.result
          }
          if (refreshedCalendar.identifier != calendar.identifier) {
            return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
          }
          val confirmed = confirmUpsert(
            projection = projection,
            calendar = refreshedCalendar,
            eventIdentifier = existingIdentifier,
            window = window,
            changed = false,
            verificationWindows = authorityWindows(window),
          )
          if (confirmed !is IosEventKitGatewayResult.Upserted) return confirmed
          val proof = IosEventKitAtomicRecoveryAttemptLedger.issueProof(
            key = recoveryAttempt,
            binding = confirmed.binding,
            issuerIdentity = recoveryProofIssuerIdentity,
          ) ?: return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
          return confirmed.copy(locatorRecoveryProof = proof)
        }
        return IosEventKitGatewayResult.Upserted(
          binding = binding(calendar, existingIdentifier),
          changed = false,
        )
      }
      if (recoveryEligible) {
        // eligibility 只授权恢复原 atomic target，不能把 changed target/fingerprint 升格为扫描式 update。
        return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
      }
    }

    if (hints.eventIdentifier == null) {
      // 即将执行普通 save/create 时，旧 unknown/success eligibility 不能为非原子 outcome 背书。
      IosEventKitAtomicRecoveryAttemptLedger.discard(recoveryAttempt)
    }
    val savedIdentifier = when (val saved = store.saveEvent(
      calendarIdentifier = calendar.identifier,
      existingEventIdentifier = existing?.requireEventIdentifier(),
      payload = payload,
    )) {
      is IosEventKitStoreResult.Success -> saved.value
      is IosEventKitStoreResult.Failure -> return storeFailure(saved.reason, calendar, hints)
    }
    return confirmUpsert(
      projection = projection,
      calendar = calendar,
      eventIdentifier = savedIdentifier,
      window = window,
      changed = true,
    )
  }

  /**
   * 在 runtime 即将 Update/Delete 前废止 preflight lookup 观察到的旧 target eligibility。
   *
   * key 只使用 gateway 重新验证后返回的 managed id/fingerprint 与 exact binding source 构造；ledger 仍要求 proof object identity、
   * binding 和 issuer 全部相等。此方法不读取 store，因此成功只代表 capability 已消费，不代表 EventKit target 未变化。
   */
  override fun retireLocatorRecoveryEligibility(
    event: ManagedCalendarEvent,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
  ): IosEventKitLocatorEligibilityRetirement = IosEventKitAtomicRecoveryAttemptLedger.retire(
    key = recoveryAttemptKey(
      projectionId = event.id,
      canonicalUri = CalendarProjectionUriCodec.encode(event.id),
      targetFingerprint = event.fingerprint,
      sourceIdentifier = binding.sourceIdentifier,
    ),
    binding = binding,
    proof = proof,
    issuerIdentity = recoveryProofIssuerIdentity,
  )

  /**
   * 在 runtime 已 durable 写入 calendar hint 与对应 event-ref ledger 后消费 exact eligibility。
   *
   * key 重新由当前 scope/source/projection/fingerprint 构造，binding 与 latest proof 也必须完全匹配；wrong gateway、stale proof、
   * changed target/source/calendar/event 或重复 ack 都只返回 REJECTED，不会影响其它 process-resident entry。
   */
  override fun acknowledgeLocatorPersistence(
    projection: CalendarEventProjection,
    binding: IosEventKitGatewayBinding,
    proof: IosEventKitLocatorRecoveryProof,
  ): IosEventKitLocatorAcknowledgement = IosEventKitAtomicRecoveryAttemptLedger.acknowledge(
    key = recoveryAttemptKey(projection, binding.sourceIdentifier),
    binding = binding,
    proof = proof,
    issuerIdentity = recoveryProofIssuerIdentity,
  )

  /** 构造不可跨 store universe 或 changed target 复用的 exact recovery key。 */
  private fun recoveryAttemptKey(
    projection: CalendarEventProjection,
    sourceIdentifier: String,
  ): IosEventKitAtomicRecoveryAttemptKey = recoveryAttemptKey(
    projectionId = projection.id,
    canonicalUri = projection.externalUri,
    targetFingerprint = projection.fingerprint,
    sourceIdentifier = sourceIdentifier,
  )

  /** direct verified lookup 以重新映射出的 canonical target 构造同一 exact key，不信任普通字符串 locator。 */
  private fun recoveryAttemptKey(
    projectionId: CalendarProjectionId,
    canonicalUri: String,
    targetFingerprint: String,
    sourceIdentifier: String,
  ): IosEventKitAtomicRecoveryAttemptKey = IosEventKitAtomicRecoveryAttemptKey(
    processIdentity = recoveryProcessIdentity,
    scope = scope,
    sourceIdentifier = sourceIdentifier,
    projectionId = projectionId,
    canonicalUri = canonicalUri,
    targetFingerprint = targetFingerprint,
  )

  /**
   * 对已提交 create/update 执行 canonical 写后回读。
   *
   * EventKit identifier 只用于优先定位；仍在同一受限窗口扫描 URL 以发现重复身份，并重新走 foundation/fingerprint
   * 校验。任何缺失、重复或平台改写都返回失败，不能把已提交状态猜成成功。
   */
  private fun confirmUpsert(
    projection: CalendarEventProjection,
    calendar: IosEventKitCalendarSnapshot,
    eventIdentifier: String,
    window: IosEventKitScanWindow,
    changed: Boolean,
    /** 只由 createCalendarWithEvent 的同队列确认路径传入 true。 */
    atomicCalendarAndFirstEvent: Boolean = false,
    /** 普通写后回读只检查目标窗口；fresh recovery 必须传入全部 authority 窗口并聚合唯一 identifier。 */
    verificationWindows: List<IosEventKitScanWindow> = listOf(window),
  ): IosEventKitGatewayResult {
    val confirmed = when (val located = locateTargetAcrossWindows(
      calendar = calendar,
      id = projection.id,
      eventIdentifierHint = eventIdentifier,
      windows = verificationWindows,
    )) {
      is EventSelection.Selected -> located.event
      EventSelection.Absent -> return IosEventKitGatewayResult.Failed(
        IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
      )

      is EventSelection.Failed -> return located.result
    }
    val readBack = when (val mapped =
      IosEventKitCalendarAdapterFoundation.toManagedEvent(confirmed.raw, scope)) {
      is IosEventKitMappingResult.Mapped -> mapped.value
      is IosEventKitMappingResult.Unsupported -> return IosEventKitGatewayResult.Failed(
        IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
        mapped.error,
      )
    }
    if (readBack.managedEvent.id != projection.id ||
      readBack.managedEvent.fingerprint != projection.fingerprint
    ) {
      return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH)
    }
    return IosEventKitGatewayResult.Upserted(
      binding = binding(calendar, confirmed.requireEventIdentifier()),
      changed = changed,
      atomicCalendarAndFirstEvent = atomicCalendarAndFirstEvent,
    )
  }


  /**
   * 在 app 隔离日历内幂等删除一个 canonical 投影。
   *
   * 删除仍接收完整 projection，以便先通过 foundation 拒绝 occurrence exception/不可投影形状，并用目标时间
   * 生成不超过 EventKit 四年上限的 canonical 扫描窗口。目标缺失视为成功；commit 后 canonical URL 仍存在则
   * 返回模糊失败，不做任何 Schedule 补偿。
   */
  fun delete(
    projection: CalendarEventProjection,
    hints: IosEventKitIdentifierHints,
  ): IosEventKitGatewayResult {
    requireFullAccess()?.let { return it }
    val payload = when (val mapped =
      IosEventKitCalendarAdapterFoundation.toWritePayload(projection)) {
      is IosEventKitMappingResult.Mapped -> mapped.value
      is IosEventKitMappingResult.Unsupported -> return IosEventKitGatewayResult.Failed(
        IosEventKitGatewayFailure.UNSUPPORTED_PROJECTION,
        mapped.error,
      )
    }
    val window = payload.scanWindow()
    val calendar = when (val selection = selectExistingCalendar(
      hints = hints,
      targetWindow = window,
      // 删除没有 create 路径：有限历史无法证明 identity 时按 no-op 返回，不把旧空 calendar 误报为写入歧义。
      blockCreationWhenHistoricallyUnprovable = false,
    )) {
      is CalendarSelection.Selected -> selection.calendar
      CalendarSelection.Absent -> return IosEventKitGatewayResult.Deleted(
        calendarIdentifier = null,
        changed = false,
      )

      is CalendarSelection.Failed -> return selection.result
    }
    val existing =
      when (val located = locateTarget(calendar, projection.id, hints.eventIdentifier, window)) {
        is EventSelection.Selected -> located.event
        EventSelection.Absent -> return IosEventKitGatewayResult.Deleted(
          calendarIdentifier = calendar.identifier,
          changed = false,
        )

        is EventSelection.Failed -> return located.result
      }
    when (val removed = store.removeEvent(existing.requireEventIdentifier())) {
      is IosEventKitStoreResult.Success -> Unit
      is IosEventKitStoreResult.Failure -> return storeFailure(removed.reason, calendar, hints)
    }
    return when (val readBack = locateTarget(calendar, projection.id, null, window)) {
      EventSelection.Absent -> IosEventKitGatewayResult.Deleted(
        calendarIdentifier = calendar.identifier,
        changed = true,
      )

      is EventSelection.Selected -> IosEventKitGatewayResult.Failed(
        IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
      )

      is EventSelection.Failed -> readBack.result
    }
  }

  /** CRUD 前的授权门禁；未询问与已拒绝分开，便于设置页给出准确状态。 */
  private fun requireFullAccess(): IosEventKitGatewayResult.Failed? {
    val status = store.authorizationStatus()
    return if (status == IosEventKitFullAccessStatus.FULL_ACCESS) null else {
      IosEventKitGatewayResult.Failed(status.toPermissionFailure(explicitRequest = false))
    }
  }

  /**
   * identifier 只作为扫描提示：先确认调用方明确选择的 source 仍存在，再检查 target 与当前恢复窗口中的 canonical
   * scope。
   *
   * 每个 predicate 都严格不超过四年，但不能只靠目标投影的窗口认定 calendar 不存在；否则历史 canonical
   * identity 会被误判为 Unowned 而另建 calendar。有限窗口仍不能证明被显式缓存的 calendar 完全没有更早身份，
   * 所以在 upsert 的创建分支中该类候选不会降级为 Absent，而是 fail-closed；delete 没有创建副作用，可将它
   * 视为 no-op。其他 source/account 下的同 scope calendar 也不能认领，一旦发现只能返回歧义。只读 calendar
   * 必须参与所有权检查。title 从不参与判断。
   */
  private fun selectExistingCalendar(
    hints: IosEventKitIdentifierHints,
    targetWindow: IosEventKitScanWindow,
    blockCreationWhenHistoricallyUnprovable: Boolean = true,
  ): CalendarSelection {
    val authorityWindows = authorityWindows(targetWindow)
    val selectedSource = when (val result = store.sources()) {
      is IosEventKitStoreResult.Success -> result.value.singleOrNull {
        it.identifier == hints.sourceIdentifier && it.supportsEvents
      }

      is IosEventKitStoreResult.Failure -> return CalendarSelection.Failed(
        storeFailure(result.reason, null, hints),
      )
    } ?: return CalendarSelection.Failed(
      IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.SOURCE_DISAPPEARED),
    )
    val calendars = when (val result = store.calendars()) {
      is IosEventKitStoreResult.Success -> result.value
      is IosEventKitStoreResult.Failure -> return CalendarSelection.Failed(
        storeFailure(result.reason, null, hints),
      )
    }
    val hinted = hints.calendarIdentifier?.let { id ->
      calendars.singleOrNull {
        it.identifier == id && it.sourceIdentifier == selectedSource.identifier
      }
    }
    val orderedCalendars = buildList {
      if (hinted != null) add(hinted)
      calendars.forEach { calendar ->
        if (calendar.identifier != hinted?.identifier) add(calendar)
      }
    }
    val selectedCandidates = mutableListOf<IosEventKitCalendarSnapshot>()
    var foundOwnedCalendarInDifferentSource = false
    var hasHistoricallyUnprovableSelectedCalendar = false
    for (calendar in orderedCalendars) {
      when (val ownership = inspectCalendar(calendar, authorityWindows)) {
        CalendarOwnership.Owned -> if (calendar.sourceIdentifier == selectedSource.identifier) {
          selectedCandidates += calendar
        } else {
          foundOwnedCalendarInDifferentSource = true
        }

        // EventKit 不能以单个无界 predicate 扫完历史。若选中 source 中已有日历在两个有限窗口都没有
        // canonical URL，就不能证明它从未承载当前 scope；在需要创建时必须停在歧义，而不能猜测 absent。
        CalendarOwnership.Unowned -> if (calendar.sourceIdentifier == selectedSource.identifier) {
          hasHistoricallyUnprovableSelectedCalendar = true
        }

        CalendarOwnership.Foreign -> return CalendarSelection.Failed(foreignIdentity())
        is CalendarOwnership.Failed -> return CalendarSelection.Failed(ownership.result)
      }
    }
    if (foundOwnedCalendarInDifferentSource || selectedCandidates.size > 1 ||
      (blockCreationWhenHistoricallyUnprovable && selectedCandidates.isEmpty() &&
          hasHistoricallyUnprovableSelectedCalendar)
    ) {
      return CalendarSelection.Failed(
        IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR),
      )
    }
    val selected = selectedCandidates.singleOrNull() ?: return CalendarSelection.Absent
    if (!selected.allowsContentModifications) {
      return CalendarSelection.Failed(
        IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.STORE_AMBIGUOUS),
      )
    }
    return CalendarSelection.Selected(selected)
  }

  /**
   * 仅在任一有界 authority 窗口发现当前 scope canonical URL 时认领 calendar。
   *
   * target 与当前恢复窗口分别服务投影定位和 identifier/source 失效恢复，每个窗口都不超过 EventKit 四年
   * predicate 限制。两个窗口仍没有身份的 selected-source calendar 由调用方视为历史不可证明，创建前返回
   * `AMBIGUOUS_CALENDAR`；普通日历、空日历和 title 都不能授权写入。
   */
  private fun inspectCalendar(
    calendar: IosEventKitCalendarSnapshot,
    windows: List<IosEventKitScanWindow>,
  ): CalendarOwnership {
    var hasCurrentScope = false
    var hasForeignIdentity = false
    for (window in windows) {
      val events = when (val result = store.events(calendar.identifier, window)) {
        is IosEventKitStoreResult.Success -> result.value
        is IosEventKitStoreResult.Failure -> return CalendarOwnership.Failed(
          storeFailure(result.reason, calendar, null),
        )
      }
      for (event in events) {
        val id = event.raw.externalUri?.let(CalendarProjectionUriCodec::decodeOrNull)
        if (id?.scope == scope) {
          hasCurrentScope = true
        } else {
          // 只有当同一 calendar 还含当前 scope 事件时，该普通/其他 scope 事件才构成受管 calendar 污染。
          hasForeignIdentity = true
        }
      }
    }
    return when {
      hasCurrentScope && hasForeignIdentity -> CalendarOwnership.Foreign
      hasCurrentScope -> CalendarOwnership.Owned
      else -> CalendarOwnership.Unowned
    }
  }

  /** eventIdentifier 命中也必须与 calendar + canonical URI 同时一致，并在目标安全窗口扫描以检测重复身份。 */
  private fun locateTarget(
    calendar: IosEventKitCalendarSnapshot,
    id: CalendarProjectionId,
    eventIdentifierHint: String?,
    window: IosEventKitScanWindow,
  ): EventSelection = locateTargetAcrossWindows(
    calendar = calendar,
    id = id,
    eventIdentifierHint = eventIdentifierHint,
    windows = listOf(window),
  )

  /**
   * 在给定 authority 窗口全集中聚合同一 canonical URI，并按 committed event identifier 去重。
   *
   * fresh recovery 不能只看 projection target window：同 URI sibling 可能位于独立 recovery window。相同 identifier 在重叠
   * predicate 中重复出现是同一对象；但 identifier 缺失，或同一 identifier 在两次 fresh observation 中返回不同快照，都无法
   * 形成唯一权威，必须按 [IosEventKitGatewayFailure.AMBIGUOUS_EVENT] 停机。
   */
  private fun locateTargetAcrossWindows(
    calendar: IosEventKitCalendarSnapshot,
    id: CalendarProjectionId,
    eventIdentifierHint: String?,
    windows: List<IosEventKitScanWindow>,
  ): EventSelection {
    val canonicalUri = CalendarProjectionUriCodec.encode(id)
    val hinted = eventIdentifierHint?.let { identifier ->
      when (val result = store.eventByIdentifier(identifier)) {
        is IosEventKitStoreResult.Success -> result.value
        is IosEventKitStoreResult.Failure -> return EventSelection.Failed(
          storeFailure(result.reason, calendar, null),
        )
      }
    }
    if (hinted != null &&
      (hinted.calendarIdentifier != calendar.identifier || hinted.raw.externalUri != canonicalUri)
    ) {
      return EventSelection.Failed(foreignIdentity())
    }
    val scanned = mutableListOf<IosEventKitStoreEventSnapshot>()
    for (window in windows.distinct()) {
      when (val result = store.events(calendar.identifier, window)) {
        is IosEventKitStoreResult.Success -> scanned += result.value.filter {
          it.raw.externalUri == canonicalUri
        }
        is IosEventKitStoreResult.Failure -> return EventSelection.Failed(
          storeFailure(result.reason, calendar, null),
        )
      }
    }
    val matchesByIdentifier = linkedMapOf<String, IosEventKitStoreEventSnapshot>()
    for (candidate in scanned + listOfNotNull(hinted)) {
      val identifier = candidate.raw.eventIdentifier ?: return EventSelection.Failed(
        IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.AMBIGUOUS_EVENT),
      )
      val previous = matchesByIdentifier[identifier]
      if (previous != null && previous != candidate) {
        return EventSelection.Failed(
          IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.AMBIGUOUS_EVENT),
        )
      }
      matchesByIdentifier[identifier] = candidate
    }
    return when (matchesByIdentifier.size) {
      0 -> EventSelection.Absent
      1 -> EventSelection.Selected(matchesByIdentifier.values.single())
      else -> EventSelection.Failed(
        IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.AMBIGUOUS_EVENT),
      )
    }
  }


  /**
   * direct ref 确认时围绕其真实平台起点扫描 canonical sibling，仍严格遵守 EventKit 四年 predicate 上限。
   *
   * 该窗口不尝试无界恢复历史；它只防止同一投影在本次可验证的 calendar/time 范围内被第二个有效 event 隐藏。
   */
  private fun directLookupWindow(raw: IosEventKitRawEvent): IosEventKitScanWindow = IosEventKitScanWindow(
    startEpochSeconds = raw.start.epochSeconds - EVENTKIT_SCAN_HALF_SECONDS,
    endEpochSeconds = raw.start.epochSeconds + EVENTKIT_SCAN_HALF_SECONDS,
  )

  /**
   * 以 projection 起点为中心生成 1460 天扫描窗口，严格不超过 EventKit 单 predicate 四年限制。
   *
   * Timed 使用 foundation 已解析的 instant；AllDay 使用 UTC 日期 epoch，仅用于查询候选，不改变领域日期语义。
   */
  private fun IosEventKitWritePayload.scanWindow(): IosEventKitScanWindow {
    val anchorEpochSeconds = when (val value = timing) {
      is IosEventKitWriteTiming.Timed -> value.start.epochSeconds
      is IosEventKitWriteTiming.AllDay -> value.startDate.toEpochDays().toLong() * SECONDS_PER_DAY
    }
    return IosEventKitScanWindow(
      startEpochSeconds = anchorEpochSeconds - EVENTKIT_SCAN_HALF_SECONDS,
      endEpochSeconds = anchorEpochSeconds + EVENTKIT_SCAN_HALF_SECONDS,
    )
  }

  /**
   * 为 calendar/source ownership 生成去重后的有限扫描窗口。
   *
   * 第一个窗口精确覆盖目标投影；第二个围绕当前恢复锚点，负责发现目标日期之外仍在近期历史/未来的 canonical identity。
   * 每个窗口均由 [IosEventKitScanWindow] 校验为最多四年，不能用无界时间范围绕过 EventKit 限制。若 selected source
   * 既有 calendar 在这些窗口内仍无法证明身份，调用方会在创建前 fail-closed。
   */
  private fun authorityWindows(
    targetWindow: IosEventKitScanWindow,
  ): List<IosEventKitScanWindow> {
    val recoveryWindow = IosEventKitScanWindow(
      startEpochSeconds = scopeRecoveryAnchorEpochSeconds - EVENTKIT_SCAN_HALF_SECONDS,
      endEpochSeconds = scopeRecoveryAnchorEpochSeconds + EVENTKIT_SCAN_HALF_SECONDS,
    )
    return listOf(targetWindow, recoveryWindow).distinct()
  }


  /**
   * 平台失败后再次读取授权/source/calendar，把撤权与确定资源消失从模糊 NSError 中安全分离。
   *
   * 诊断 reread 自身失败时仍然是未知终态，必须保留 STORE_AMBIGUOUS；绝不能把失败结果当空列表并误报
   * SOURCE_DISAPPEARED/CALENDAR_DISAPPEARED，避免未来调用方清空仍有效的选择。
   */
  private fun storeFailure(
    failure: IosEventKitStoreFailure,
    calendar: IosEventKitCalendarSnapshot?,
    hints: IosEventKitIdentifierHints?,
  ): IosEventKitGatewayResult.Failed {
    if (store.authorizationStatus() != IosEventKitFullAccessStatus.FULL_ACCESS) {
      return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.PERMISSION_REVOKED)
    }
    if (failure == IosEventKitStoreFailure.NOT_FOUND) {
      val sourceId = calendar?.sourceIdentifier ?: hints?.sourceIdentifier
      if (sourceId != null) {
        when (val sources = store.sources()) {
          is IosEventKitStoreResult.Success -> if (sources.value.none { it.identifier == sourceId }) {
            return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.SOURCE_DISAPPEARED)
          }

          is IosEventKitStoreResult.Failure -> return diagnosticReadFailure()
        }
      }
      val calendarId = calendar?.identifier ?: hints?.calendarIdentifier
      if (calendarId != null) {
        when (val calendars = store.calendars()) {
          is IosEventKitStoreResult.Success -> if (calendars.value.none { it.identifier == calendarId }) {
            return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.CALENDAR_DISAPPEARED)
          }

          is IosEventKitStoreResult.Failure -> return diagnosticReadFailure()
        }
      }
    }
    return IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
  }

  /** 诊断查询失败后再看一次权限；仅明确撤权可降级分类，其余保持 store 模糊失败。 */
  private fun diagnosticReadFailure(): IosEventKitGatewayResult.Failed =
    if (store.authorizationStatus() == IosEventKitFullAccessStatus.FULL_ACCESS) {
      IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.STORE_AMBIGUOUS)
    } else {
      IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.PERMISSION_REVOKED)
    }


  private fun failureAfterStoreError(): IosEventKitGatewayFailure =
    if (store.authorizationStatus() == IosEventKitFullAccessStatus.FULL_ACCESS) {
      IosEventKitGatewayFailure.STORE_AMBIGUOUS
    } else {
      IosEventKitGatewayFailure.PERMISSION_DENIED
    }

  private fun IosEventKitFullAccessStatus.toPermissionFailure(
    explicitRequest: Boolean,
  ): IosEventKitGatewayFailure = when (this) {
    IosEventKitFullAccessStatus.NOT_DETERMINED -> if (explicitRequest) {
      IosEventKitGatewayFailure.PERMISSION_DENIED
    } else {
      IosEventKitGatewayFailure.PERMISSION_REQUIRED
    }

    IosEventKitFullAccessStatus.FULL_ACCESS -> IosEventKitGatewayFailure.STORE_AMBIGUOUS
    IosEventKitFullAccessStatus.WRITE_ONLY,
    IosEventKitFullAccessStatus.DENIED,
    IosEventKitFullAccessStatus.RESTRICTED,
    IosEventKitFullAccessStatus.UNKNOWN -> IosEventKitGatewayFailure.PERMISSION_DENIED
  }

  private fun binding(
    calendar: IosEventKitCalendarSnapshot,
    eventIdentifier: String,
  ): IosEventKitGatewayBinding = IosEventKitGatewayBinding(
    sourceIdentifier = calendar.sourceIdentifier,
    calendarIdentifier = calendar.identifier,
    eventIdentifier = eventIdentifier,
  )

  private fun IosEventKitStoreEventSnapshot.requireEventIdentifier(): String =
    raw.eventIdentifier?.takeIf { it.isNotBlank() }
      ?: error("Store bridge returned an event without a committed identifier")

  private fun foreignIdentity(): IosEventKitGatewayResult.Failed =
    IosEventKitGatewayResult.Failed(IosEventKitGatewayFailure.FOREIGN_IDENTITY)

  private sealed interface CalendarSelection {
    data class Selected(val calendar: IosEventKitCalendarSnapshot) : CalendarSelection
    data object Absent : CalendarSelection
    data class Failed(val result: IosEventKitGatewayResult.Failed) : CalendarSelection
  }

  private sealed interface CalendarOwnership {
    data object Owned : CalendarOwnership
    data object Unowned : CalendarOwnership
    data object Foreign : CalendarOwnership
    data class Failed(val result: IosEventKitGatewayResult.Failed) : CalendarOwnership
  }

  private sealed interface EventSelection {
    data class Selected(val event: IosEventKitStoreEventSnapshot) : EventSelection
    data object Absent : EventSelection
    data class Failed(val result: IosEventKitGatewayResult.Failed) : EventSelection
  }

  private companion object {
    /** 仅作展示；gateway 的所有权判断绝不比较该标题。 */
    const val MANAGED_CALENDAR_DISPLAY_TITLE = "掌邮日程"
    const val SECONDS_PER_DAY = 24L * 60L * 60L
    const val EVENTKIT_SCAN_HALF_SECONDS = 730L * SECONDS_PER_DAY
  }
}
