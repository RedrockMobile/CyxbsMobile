package com.cyxbs.pages.schedule.data.remote

import kotlinx.serialization.Serializable

/** 日程接口使用的 Unix 毫秒。日期槽固定为 UTC 零点，客户端不能按本地时区改写。 */
typealias UnixMillis = Long

/** 将业务值与该字段最后一次修改时间绑定，供服务端按字段执行 LWW 合并。 */
@Serializable
data class AtomicField<T>(val data: T, val modifiedAt: UnixMillis)

/** 单次调整字段相对所属日程的继承、清空和替换模式。 */
@Serializable
enum class PatchMode { INHERIT, CLEAR, REPLACE }

/** 单次调整字段的三态值；只有 [PatchMode.REPLACE] 携带 [value]。 */
@Serializable
data class FieldPatch<T>(val mode: PatchMode, val value: T? = null)

@Serializable enum class OccurrenceStatus { ACTIVE, COMPLETED, CANCELLED }
@Serializable enum class TodoState { OPEN, COMPLETED }
@Serializable enum class ScheduleKind { TODO, AFFAIR }
@Serializable enum class TimingKind { TIMED, DEADLINE, ALL_DAY, UNSCHEDULED }
@Serializable enum class OccurrenceTimeKind { TIME_RANGE, TIME_POINT, ALL_DAY }
@Serializable enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
@Serializable enum class Weekday { MO, TU, WE, TH, FR, SA, SU }

/** 单条资源操作的结果。DELETED 表示客户端认为存在的远端资源已不存在。 */
@Serializable
enum class MutationResultCode { SUCCESS, REJECTED, DELETED }

/** 客户端可据此提示或清理本地状态的稳定业务原因。 */
@Serializable
enum class ResultReason {
  INVALID_REQUEST,
  RESOURCE_CHANGED,
  CATEGORY_NOT_FOUND,
  DUPLICATE_CATEGORY_NAME,
  CATEGORY_IN_USE,
  SCHEDULE_NOT_FOUND,
  UNSUPPORTED_RECURRENCE,
}

@Serializable
data class ReminderInput(val minutesBefore: Int)

/** [kind] 决定其余时间字段的唯一合法组合；UNSCHEDULED 仅用于兼容旧清单。 */
@Serializable
data class TimingInput(
  val kind: TimingKind,
  val startAt: UnixMillis? = null,
  val endAt: UnixMillis? = null,
  val dueAt: UnixMillis? = null,
  val date: UnixMillis? = null,
)

/** 单次调整中不带日期的时间形态，日期由独立的 date 字段表达。 */
@Serializable
data class OccurrenceTimeInput(
  val kind: OccurrenceTimeKind,
  val startMinuteOfDay: Int? = null,
  val durationMinutes: Int? = null,
  val minuteOfDay: Int? = null,
)

/** 以 UTC 日期槽表达的日、周、月、年重复规则。 */
@Serializable
data class RecurrenceInput(
  val frequency: RecurrenceFrequency,
  val interval: Int,
  val anchorDate: UnixMillis,
  val count: Int? = null,
  val untilDate: UnixMillis? = null,
  val weekdays: List<Weekday>,
  val monthDays: List<Int>,
  val months: List<Int>,
)

/**
 * 分类完整快照。
 *
 * 创建时只填写 [localId] 且 version=0；更新及服务端下发时只填写 [id] 且 version>0。
 * localId 只在本次请求内帮助新日程引用新分类，服务端不会保存它。
 */
@Serializable
data class CategoryInput(
  val localId: String? = null,
  val id: Long? = null,
  val version: ULong,
  val name: AtomicField<String>,
  val color: AtomicField<String?>,
  val sortOrder: AtomicField<Long>,
)

/**
 * 日程完整快照。
 *
 * 普通日程的 [id] 是客户端生成的 UUID v7，旧数据迁移使用确定性的 UUID v5；服务端原样保存该 ID。
 * [categoryLocalId] 只用于同一次请求引用尚无远端 ID 的新分类。
 */
@Serializable
data class ScheduleInput(
  val id: String,
  val version: ULong,
  val kind: ScheduleKind,
  val title: AtomicField<String>,
  val description: AtomicField<String>,
  val categoryId: AtomicField<Long?>,
  val categoryLocalId: String? = null,
  val timing: AtomicField<TimingInput>,
  val recurrence: AtomicField<RecurrenceInput?>,
  val reminder: AtomicField<ReminderInput?>,
  val todoState: AtomicField<TodoState?>,
  val linkedToCourse: AtomicField<Boolean>,
)

/**
 * 某个 [originalOccurrenceDate] 原始日期槽上的完整单次调整。
 *
 * [scheduleId] 直接引用日程 UUID；创建时可用 [categoryLocalId] 引用同请求新分类。
 * 远端只为单次调整本身分配自增 ID，不保存其客户端 localId。originalOccurrenceDate 是不可变逻辑槽。
 */
@Serializable
data class OccurrenceAdjustmentInput(
  val localId: String? = null,
  val id: Long? = null,
  val scheduleId: String,
  val originalOccurrenceDate: UnixMillis,
  val version: ULong,
  val status: AtomicField<OccurrenceStatus>,
  val date: AtomicField<FieldPatch<UnixMillis>>,
  val time: AtomicField<FieldPatch<OccurrenceTimeInput>>,
  val title: AtomicField<FieldPatch<String>>,
  val description: AtomicField<FieldPatch<String>>,
  val categoryId: AtomicField<FieldPatch<Long>>,
  val categoryLocalId: String? = null,
  val reminder: AtomicField<FieldPatch<ReminderInput>>,
)

/** 客户端已经持有的远端资源及版本；ID 类型由具体资源决定。 */
@Serializable
data class ConfirmedResource<ID>(val id: ID, val version: ULong)

/** 物理删除输入；服务端把重复删除同样视为成功。 */
@Serializable
data class DeleteResource<ID>(val id: ID)

@Serializable data class CategorySyncRequest(
  val confirmed: List<ConfirmedResource<Long>>,
  val upserts: List<CategoryInput>,
  val deletes: List<DeleteResource<Long>>,
)
@Serializable data class ScheduleSyncRequest(
  val confirmed: List<ConfirmedResource<String>>,
  val upserts: List<ScheduleInput>,
  val deletes: List<DeleteResource<String>>,
)
@Serializable data class OccurrenceAdjustmentSyncRequest(
  val confirmed: List<ConfirmedResource<Long>>,
  val upserts: List<OccurrenceAdjustmentInput>,
  val deletes: List<DeleteResource<Long>>,
)
@Serializable data class CategoryMutationRequest(
  val upserts: List<CategoryInput>, val deletes: List<DeleteResource<Long>>,
)
@Serializable data class ScheduleMutationRequest(
  val upserts: List<ScheduleInput>, val deletes: List<DeleteResource<String>>,
)
@Serializable data class OccurrenceAdjustmentMutationRequest(
  val upserts: List<OccurrenceAdjustmentInput>, val deletes: List<DeleteResource<Long>>,
)

/** 新增、修改、删除接口共用的逐资源操作请求。 */
@Serializable
data class MutationRequest(
  val categories: CategoryMutationRequest,
  val schedules: ScheduleMutationRequest,
  val occurrenceAdjustments: OccurrenceAdjustmentMutationRequest,
)

/** 首次进入或网络恢复时使用的完整 inventory 与 pending 请求。 */
@Serializable
data class SyncRequest(
  val categories: CategorySyncRequest,
  val schedules: ScheduleSyncRequest,
  val occurrenceAdjustments: OccurrenceAdjustmentSyncRequest,
)

@Serializable enum class ConfirmedResultCode { CONFIRMED, CHANGED, DELETED }

/** confirmed 的位置对齐结果；只有 CHANGED 携带完整 [resource]。 */
@Serializable data class ConfirmedResult<ID, T>(
  val id: ID, val result: ConfirmedResultCode, val resource: T? = null,
)

/** upserts 的位置对齐结果；SUCCESS 必带 canonical 快照，REJECTED 仅在需要客户端收敛时携带当前快照。 */
@Serializable data class UpsertResult<T>(
  val result: MutationResultCode,
  val reason: ResultReason? = null,
  val info: String? = null,
  val resource: T? = null,
)

/** deletes 的位置对齐结果；仅拒绝时可能携带仍然存在的 [resource]。 */
@Serializable data class DeleteResult<ID, T>(
  val id: ID,
  val result: MutationResultCode,
  val reason: ResultReason? = null,
  val info: String? = null,
  val resource: T? = null,
)

@Serializable data class CategorySyncResponse(
  val confirmedResults: List<ConfirmedResult<Long, CategoryInput>>,
  val discoveredResults: List<CategoryInput>,
  val upsertResults: List<UpsertResult<CategoryInput>>,
  val deleteResults: List<DeleteResult<Long, CategoryInput>>,
)
@Serializable data class ScheduleSyncResponse(
  val confirmedResults: List<ConfirmedResult<String, ScheduleInput>>,
  val discoveredResults: List<ScheduleInput>,
  val upsertResults: List<UpsertResult<ScheduleInput>>,
  val deleteResults: List<DeleteResult<String, ScheduleInput>>,
)
@Serializable data class OccurrenceAdjustmentSyncResponse(
  val confirmedResults: List<ConfirmedResult<Long, OccurrenceAdjustmentInput>>,
  val discoveredResults: List<OccurrenceAdjustmentInput>,
  val upsertResults: List<UpsertResult<OccurrenceAdjustmentInput>>,
  val deleteResults: List<DeleteResult<Long, OccurrenceAdjustmentInput>>,
)

@Serializable data class MutationResourceResponse<ID, T>(
  val upsertResults: List<UpsertResult<T>>, val deleteResults: List<DeleteResult<ID, T>>,
)

/** 日常新增、修改、删除接口的逐资源响应。 */
@Serializable data class MutationResponse(
  val categories: MutationResourceResponse<Long, CategoryInput>,
  val schedules: MutationResourceResponse<String, ScheduleInput>,
  val occurrenceAdjustments: MutationResourceResponse<Long, OccurrenceAdjustmentInput>,
)

/** 同步接口的 inventory 核对、远端发现与本地操作响应。 */
@Serializable data class SyncResponse(
  val categories: CategorySyncResponse,
  val schedules: ScheduleSyncResponse,
  val occurrenceAdjustments: OccurrenceAdjustmentSyncResponse,
)
