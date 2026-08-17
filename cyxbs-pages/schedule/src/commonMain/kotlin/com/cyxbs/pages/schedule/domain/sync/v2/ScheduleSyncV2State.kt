package com.cyxbs.pages.schedule.domain.sync.v2

private const val UTC_DAY_MILLIS = 86_400_000L

/**
 * 服务端协议中的独立原子字段。
 *
 * [modifiedAt] 是客户端对该字段最后一次修改的 Unix 毫秒，用于服务端的字段级 LWW 合并；
 * 它不参与本地 pending 的比较与清除。
 */
data class AtomicField<T>(
  val data: T,
  val modifiedAt: Long,
) {
  init {
    require(modifiedAt >= 0) { "modifiedAt must not be negative" }
  }
}

/** 服务端支持的三态单次覆盖模式。 */
sealed interface FieldPatch<out T> {
  /** 继续使用 parent Schedule 当前字段。 */
  data object Inherit : FieldPatch<Nothing>

  /** 显式覆盖为空。 */
  data object Clear : FieldPatch<Nothing>

  /** 使用完整替换值覆盖。 */
  data class Replace<T>(val value: T) : FieldPatch<T>
}

enum class TimingKind {
  TIMED,
  DEADLINE,
  ALL_DAY,
  UNSCHEDULED,
}

enum class CompletionStatus {
  OPEN,
  COMPLETED,
}

enum class OccurrenceStatus {
  ACTIVE,
  COMPLETED,
  CANCELLED,
}

enum class RecurrenceFrequency {
  DAILY,
  WEEKLY,
}

enum class Weekday {
  MO,
  TU,
  WE,
  TH,
  FR,
  SA,
  SU,
}

/** 服务端 TimingInput 的完整本地表示，字段组合由 [kind] 决定。 */
data class TimingInput(
  val kind: TimingKind,
  val startAt: Long? = null,
  val endAt: Long? = null,
  val dueAt: Long? = null,
)

/**
 * 服务端当前支持的保守重复规则子集。
 *
 * 日期字段均为 UTC 午夜日期槽；[count] 与 [untilDate] 不能同时存在。
 */
data class RecurrenceInput(
  val frequency: RecurrenceFrequency,
  val interval: Int,
  val anchorDate: Long,
  val count: Int? = null,
  val untilDate: Long? = null,
  val weekdays: Set<Weekday> = emptySet(),
) {
  init {
    require(interval > 0) { "recurrence interval must be positive" }
    require(count == null || count > 0) { "recurrence count must be positive" }
    require(count == null || untilDate == null) { "recurrence count and untilDate are mutually exclusive" }
    require(anchorDate % UTC_DAY_MILLIS == 0L) { "recurrence anchorDate must be a UTC day slot" }
    require(untilDate == null || untilDate % UTC_DAY_MILLIS == 0L) {
      "recurrence untilDate must be a UTC day slot"
    }
  }
}

/** 相对日程 timing 的提醒配置。 */
data class ReminderInput(
  val minutesBefore: Int,
  val message: String,
)

/** 服务端确认 live 资源时附带的只读时间元数据。 */
data class ServerResourceMeta(
  val createdAt: Long,
  val remoteModifiedAt: Long,
)

/** 三类资源的强类型 identity 共同约束。 */
sealed interface ResourceIdentity

data class CategoryIdentity(val id: String) : ResourceIdentity {
  init {
    require(id.isNotBlank()) { "category id must not be blank" }
  }
}

data class ScheduleIdentity(val id: String) : ResourceIdentity {
  init {
    require(id.isNotBlank()) { "schedule id must not be blank" }
  }
}

/**
 * 单次覆盖的稳定复合 identity。
 *
 * occurrenceDate 只表示 UTC 自然日槽，绝不能因展示时区或实际 timing 改变。
 */
data class OccurrenceOverrideIdentity(
  val scheduleId: String,
  val occurrenceDate: Long,
) : ResourceIdentity {
  init {
    require(scheduleId.isNotBlank()) { "occurrence override scheduleId must not be blank" }
    require(occurrenceDate % UTC_DAY_MILLIS == 0L) {
      "occurrenceDate must be a UTC day slot"
    }
  }
}

/** 所有可被 CREATE/PATCH 上传的完整资源 payload。 */
sealed interface SyncResource<I : ResourceIdentity> {
  val identity: I
  val version: Long
}

/** CategoryInput：version 与全部三个原子字段直接属于分类；没有自定义颜色时 [color] 的 data 为 null。 */
data class CategoryResource(
  override val identity: CategoryIdentity,
  override val version: Long,
  val name: AtomicField<String>,
  val color: AtomicField<String?>,
  val sortOrder: AtomicField<Long>,
) : SyncResource<CategoryIdentity> {
  init {
    require(version >= 0) { "category version must not be negative" }
  }
}

/** ScheduleInput：version 与七个业务原子字段直接属于日程。 */
data class ScheduleResource(
  override val identity: ScheduleIdentity,
  override val version: Long,
  val title: AtomicField<String>,
  val description: AtomicField<String>,
  val categoryId: AtomicField<String>,
  val timing: AtomicField<TimingInput>,
  val recurrence: AtomicField<RecurrenceInput?>,
  val reminders: AtomicField<List<ReminderInput>>,
  val completion: AtomicField<CompletionStatus>,
) : SyncResource<ScheduleIdentity> {
  init {
    require(version >= 0) { "schedule version must not be negative" }
  }
}

/**
 * OccurrenceOverrideInput：只允许四个可独立合并的原子字段。
 *
 * 时间与分类仍来自 parent Schedule，不能在单次覆盖中新增 timing 或 category 字段。
 */
data class OccurrenceOverrideResource(
  override val identity: OccurrenceOverrideIdentity,
  override val version: Long,
  val status: AtomicField<OccurrenceStatus>,
  val title: AtomicField<FieldPatch<String>>,
  val description: AtomicField<FieldPatch<String>>,
  val reminders: AtomicField<FieldPatch<List<ReminderInput>>>,
) : SyncResource<OccurrenceOverrideIdentity> {
  init {
    require(version >= 0) { "occurrence override version must not be negative" }
  }
}

/**
 * 客户端保存的服务端 canonical live 快照。
 *
 * tombstone 不进入 remoteSnapshot；服务端删除结果会令该快照被移除。
 */
sealed interface RemoteSnapshot<I : ResourceIdentity, out R : SyncResource<I>> {
  val resource: R
  val meta: ServerResourceMeta

  val identity: I
    get() = resource.identity

  val version: Long
    get() = resource.version
}

data class CategoryRemoteSnapshot(
  override val resource: CategoryResource,
  override val meta: ServerResourceMeta,
) : RemoteSnapshot<CategoryIdentity, CategoryResource> {
  init {
    require(version > 0) { "remote category version must be positive" }
  }
}

data class ScheduleRemoteSnapshot(
  override val resource: ScheduleResource,
  override val meta: ServerResourceMeta,
  val firstRecurrenceAnchorDate: Long? = null,
) : RemoteSnapshot<ScheduleIdentity, ScheduleResource> {
  init {
    require(version > 0) { "remote schedule version must be positive" }
    require(firstRecurrenceAnchorDate == null || firstRecurrenceAnchorDate % UTC_DAY_MILLIS == 0L) {
      "firstRecurrenceAnchorDate must be a UTC day slot"
    }
  }
}

data class OccurrenceOverrideRemoteSnapshot(
  override val resource: OccurrenceOverrideResource,
  override val meta: ServerResourceMeta,
) : RemoteSnapshot<OccurrenceOverrideIdentity, OccurrenceOverrideResource> {
  init {
    require(version > 0) { "remote occurrence override version must be positive" }
  }
}

/**
 * 本地尚未被服务端确认的单一变更。
 *
 * localRevision 只服务于本地 CAS，与服务端 resource version 完全独立。上传 pending R 时，调用方
 * 记录 uploadedRevision；若请求期间再次编辑，则当前 pending 已成为更高 revision 的 U。R 失败时
 * 原样保留 U；R 成功并推进或合并 remote 时，也只有当前 localRevision 仍等于 uploadedRevision 才能
 * 清除 pending。否则 U 必须继续保留为有效值，等待下一轮上传后收敛。
 *
 * localBatchId 仅把同一原子批次的资源归组，不保存 batch 状态、回执或历史。
 */
sealed interface PendingChange<I : ResourceIdentity, R : SyncResource<I>> {
  val identity: I
  val localRevision: Long
  val localBatchId: String?
}

/** 待上传的完整 CREATE/PATCH 资源 payload。 */
data class PendingUpsert<I : ResourceIdentity, R : SyncResource<I>>(
  val resource: R,
  override val localRevision: Long,
  override val localBatchId: String? = null,
) : PendingChange<I, R> {
  override val identity: I
    get() = resource.identity

  init {
    require(localRevision > 0) { "localRevision must be positive" }
    require(localBatchId == null || localBatchId.isNotBlank()) { "localBatchId must not be blank" }
  }
}

/**
 * 待上传的删除操作。
 *
 * 协议刻意不携带资源版本：删除优先级最高，由服务端按 identity 与 localModifiedAt 处理。
 */
data class PendingDelete<I : ResourceIdentity, R : SyncResource<I>>(
  override val identity: I,
  val localModifiedAt: Long,
  override val localRevision: Long,
  override val localBatchId: String? = null,
) : PendingChange<I, R> {
  init {
    require(localModifiedAt >= 0) { "localModifiedAt must not be negative" }
    require(localRevision > 0) { "localRevision must be positive" }
    require(localBatchId == null || localBatchId.isNotBlank()) { "localBatchId must not be blank" }
  }
}

/**
 * 一个 identity 的本地双快照状态。
 *
 * 只保留服务端 live [remoteSnapshot] 和至多一个 [pending]。每次本地写入以 [replacePending] 直接
 * 覆盖旧 pending。请求 R 发出后产生的更高 revision pending U 始终优先于 remote：无论 R 失败，
 * 还是 R 成功后 remote 已推进，均不得覆盖或重写 U；响应处理只能按 uploadedRevision 做
 * compare-and-clear，未被清除的 U 继续作为 effective 值并在下一轮上传后收敛。
 */
data class LocalSyncState<
  I : ResourceIdentity,
  R : SyncResource<I>,
  S : RemoteSnapshot<I, R>,
>(
  val identity: I,
  val remoteSnapshot: S?,
  val pending: PendingChange<I, R>? = null,
) {
  init {
    require(remoteSnapshot == null || remoteSnapshot.identity == identity) {
      "remoteSnapshot identity must match state identity"
    }
    pending?.let { change ->
      require(change.identity == identity) { "pending identity must match state identity" }
      if (change is PendingUpsert) {
        if (remoteSnapshot == null) {
          require(change.resource.version == 0L) { "CREATE upsert version must be 0" }
        } else {
          // CREATE R 发出期间形成的 U 仍是 version=0；R 成功推进 remote 后也必须原样保留 U。
          // 这里只允许该短暂组合，下一次请求由 planner 投影当前 remote version，不在状态内 rebase U。
          require(change.resource.version >= 0) { "pending upsert version must not be negative" }
        }
      }
    }
  }

  /**
   * 计算 UI 和本地业务读取的有效资源。
   *
   * pending UPSERT 覆盖 remote，pending DELETE 隐藏资源，其他情况读取 remote；
   * 这不修改 remote，也不尝试在客户端做重放或 rebase。
   */
  fun effectiveResource(): R? = when (val change = pending) {
    is PendingUpsert -> change.resource
    is PendingDelete -> null
    null -> remoteSnapshot?.resource
  }

  /** 用更晚的同 identity pending 覆盖旧 pending，并再次执行版本与 identity 不变量校验。 */
  fun replacePending(newPending: PendingChange<I, R>?): LocalSyncState<I, R, S> {
    if (newPending != null && pending != null) {
      require(newPending.localRevision > pending.localRevision) {
        "new pending localRevision must be greater than the current pending"
      }
    }
    return copy(pending = newPending)
  }
}

typealias CategorySyncState = LocalSyncState<CategoryIdentity, CategoryResource, CategoryRemoteSnapshot>
typealias ScheduleSyncState = LocalSyncState<ScheduleIdentity, ScheduleResource, ScheduleRemoteSnapshot>
typealias OccurrenceOverrideSyncState =
  LocalSyncState<OccurrenceOverrideIdentity, OccurrenceOverrideResource, OccurrenceOverrideRemoteSnapshot>
