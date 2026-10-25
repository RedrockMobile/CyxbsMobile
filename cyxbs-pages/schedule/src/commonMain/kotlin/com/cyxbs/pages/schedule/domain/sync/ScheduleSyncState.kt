package com.cyxbs.pages.schedule.domain.sync

private const val UTC_DAY_MILLIS = 86_400_000L

/** 服务端按字段执行 LWW 合并所需的业务值与客户端修改时间。 */
data class AtomicField<T>(val data: T, val modifiedAt: Long) {
  init { require(modifiedAt >= 0) { "modifiedAt must not be negative" } }
}

/** 单次调整字段相对所属日程的继承、清空或替换值。 */
sealed interface FieldPatch<out T> {
  data object Inherit : FieldPatch<Nothing>
  data object Clear : FieldPatch<Nothing>
  data class Replace<T>(val value: T) : FieldPatch<T>
}

enum class TimingKind { TIMED, DEADLINE, ALL_DAY, UNSCHEDULED }
enum class OccurrenceTimeKind { TIME_RANGE, TIME_POINT, ALL_DAY }
enum class TodoState { OPEN, COMPLETED }
enum class ScheduleKind { TODO, AFFAIR }
enum class OccurrenceStatus { ACTIVE, COMPLETED, CANCELLED }
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class Weekday { MO, TU, WE, TH, FR, SA, SU }

/** 日程时间联合值；[kind] 决定其余字段的唯一合法组合。 */
data class TimingInput(
  val kind: TimingKind,
  val startAt: Long? = null,
  val endAt: Long? = null,
  val dueAt: Long? = null,
  val date: Long? = null,
)

/** 单次调整中不带日期的时间形态。 */
data class OccurrenceTimeInput(
  val kind: OccurrenceTimeKind,
  val startMinuteOfDay: Int? = null,
  val durationMinutes: Int? = null,
  val minuteOfDay: Int? = null,
)

/** 服务端支持的日、周、月、年重复规则。 */
data class RecurrenceInput(
  val frequency: RecurrenceFrequency,
  val interval: Int,
  val anchorDate: Long,
  val count: Int? = null,
  val untilDate: Long? = null,
  val weekdays: Set<Weekday> = emptySet(),
  val monthDays: Set<Int> = emptySet(),
  val months: Set<Int> = emptySet(),
) {
  init {
    require(interval > 0) { "recurrence interval must be positive" }
    require(count == null || count > 0) { "recurrence count must be positive" }
    require(count == null || untilDate == null) { "recurrence count and untilDate are mutually exclusive" }
    require(anchorDate % UTC_DAY_MILLIS == 0L) { "recurrence anchorDate must be a UTC day slot" }
    require(untilDate == null || untilDate % UTC_DAY_MILLIS == 0L) { "untilDate must be a UTC day slot" }
  }
}

/** 相对日程时间的唯一提醒；0 表示准时提醒。 */
data class ReminderInput(val minutesBefore: Int)

/** 客户端持久化使用的稳定资源标识；不同资源是否同时作为远端 ID 由具体 identity 说明。 */
sealed interface ResourceIdentity

/** 分类的本地 UUID v7；服务端另行分配自增 ID。 */
data class CategoryIdentity(val id: String) : ResourceIdentity {
  init { require(id.isNotBlank()) { "category local id must not be blank" } }
}

/** 普通日程使用 UUID v7，迁移日程使用确定性的 UUID v5；该值同时作为服务端日程 ID。 */
data class ScheduleIdentity(val id: String) : ResourceIdentity {
  init { require(id.isNotBlank()) { "schedule local id must not be blank" } }
}

/**
 * 单次调整的纯本地标识。
 *
 * [localId] 是 UUID v7；[originalOccurrenceDate] 是 RRULE 原始生成的不可变 UTC 日期槽。
 */
data class OccurrenceAdjustmentIdentity(
  val localId: String,
  val scheduleId: String,
  val originalOccurrenceDate: Long,
) : ResourceIdentity {
  init {
    require(localId.isNotBlank()) { "occurrence adjustment localId must not be blank" }
    require(scheduleId.isNotBlank()) { "occurrence adjustment scheduleId must not be blank" }
    require(originalOccurrenceDate % UTC_DAY_MILLIS == 0L) { "originalOccurrenceDate must be a UTC day slot" }
  }
}

/** 可被创建或更新的完整本地资源。 */
sealed interface SyncResource<I : ResourceIdentity> {
  val identity: I
  val version: Long
}

data class CategoryResource(
  override val identity: CategoryIdentity,
  val remoteId: Long?,
  override val version: Long,
  val name: AtomicField<String>,
  val color: AtomicField<String?>,
  val sortOrder: AtomicField<Long>,
) : SyncResource<CategoryIdentity> {
  init { requireRemoteShape("category", remoteId, version) }
}

data class ScheduleResource(
  override val identity: ScheduleIdentity,
  override val version: Long,
  val kind: ScheduleKind,
  val title: AtomicField<String>,
  val description: AtomicField<String>,
  val categoryId: AtomicField<String?>,
  val timing: AtomicField<TimingInput>,
  val recurrence: AtomicField<RecurrenceInput?>,
  val reminder: AtomicField<ReminderInput?>,
  val todoState: AtomicField<TodoState?>,
  val linkedToCourse: AtomicField<Boolean>,
) : SyncResource<ScheduleIdentity> {
  init { require(version >= 0) { "schedule version must not be negative" } }
}

data class OccurrenceAdjustmentResource(
  override val identity: OccurrenceAdjustmentIdentity,
  val remoteId: Long?,
  override val version: Long,
  val status: AtomicField<OccurrenceStatus>,
  val date: AtomicField<FieldPatch<Long>>,
  val time: AtomicField<FieldPatch<OccurrenceTimeInput>>,
  val title: AtomicField<FieldPatch<String>>,
  val description: AtomicField<FieldPatch<String>>,
  val categoryId: AtomicField<FieldPatch<String>>,
  val reminder: AtomicField<FieldPatch<ReminderInput>>,
) : SyncResource<OccurrenceAdjustmentIdentity> {
  init { requireRemoteShape("occurrence adjustment", remoteId, version) }
}

/** 服务端 canonical live 快照；服务端物理删除后直接移除该快照。 */
sealed interface RemoteSnapshot<I : ResourceIdentity, out R : SyncResource<I>> {
  val resource: R
  val identity: I get() = resource.identity
  val version: Long get() = resource.version
}

data class CategoryRemoteSnapshot(override val resource: CategoryResource) :
  RemoteSnapshot<CategoryIdentity, CategoryResource>

data class ScheduleRemoteSnapshot(override val resource: ScheduleResource) :
  RemoteSnapshot<ScheduleIdentity, ScheduleResource>

data class OccurrenceAdjustmentRemoteSnapshot(override val resource: OccurrenceAdjustmentResource) :
  RemoteSnapshot<OccurrenceAdjustmentIdentity, OccurrenceAdjustmentResource>

/** 本地尚未被服务端确认的单一变更；localRevision 只用于端内 CAS。 */
sealed interface PendingChange<I : ResourceIdentity, R : SyncResource<I>> {
  val identity: I
  val localRevision: Long
}

/** 待上传的完整创建或更新快照。 */
data class PendingUpsert<I : ResourceIdentity, R : SyncResource<I>>(
  val resource: R,
  override val localRevision: Long,
) : PendingChange<I, R> {
  override val identity: I get() = resource.identity
  init { require(localRevision > 0) { "localRevision must be positive" } }
}

/**
 * 待上传的物理删除。
 *
 * [localModifiedAt] 只用于失败记录和本地诊断，不进入当前服务端删除合同。
 */
data class PendingDelete<I : ResourceIdentity, R : SyncResource<I>>(
  override val identity: I,
  val localModifiedAt: Long,
  override val localRevision: Long,
) : PendingChange<I, R> {
  init {
    require(localModifiedAt >= 0) { "localModifiedAt must not be negative" }
    require(localRevision > 0) { "localRevision must be positive" }
  }
}

/**
 * 一个本地标识的远端快照和至多一个 pending。
 *
 * 请求期间形成的更高 localRevision 不会被旧响应清除；读取时 pending 始终优先于远端快照。
 */
data class LocalSyncState<I : ResourceIdentity, R : SyncResource<I>, S : RemoteSnapshot<I, R>>(
  val identity: I,
  val remoteSnapshot: S?,
  val pending: PendingChange<I, R>? = null,
) {
  init {
    require(remoteSnapshot == null || remoteSnapshot.identity == identity)
    require(pending == null || pending.identity == identity)
  }

  fun effectiveResource(): R? = when (val change = pending) {
    is PendingUpsert -> change.resource
    is PendingDelete -> null
    null -> remoteSnapshot?.resource
  }

  /** 用更晚的本地变更覆盖旧 pending。 */
  fun replacePending(newPending: PendingChange<I, R>?): LocalSyncState<I, R, S> {
    if (newPending != null && pending != null) {
      require(newPending.localRevision > pending.localRevision)
    }
    return copy(pending = newPending)
  }
}

typealias CategorySyncState = LocalSyncState<CategoryIdentity, CategoryResource, CategoryRemoteSnapshot>
typealias ScheduleSyncState = LocalSyncState<ScheduleIdentity, ScheduleResource, ScheduleRemoteSnapshot>
typealias OccurrenceAdjustmentSyncState = LocalSyncState<
    OccurrenceAdjustmentIdentity,
    OccurrenceAdjustmentResource,
    OccurrenceAdjustmentRemoteSnapshot,
    >

private fun requireRemoteShape(name: String, remoteId: Long?, version: Long) {
  require((remoteId == null && version == 0L) || (remoteId != null && remoteId > 0L && version > 0L)) {
    "$name remoteId/version shape is invalid"
  }
}
