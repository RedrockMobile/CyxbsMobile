package com.cyxbs.pages.schedule.data.remote.v3

import kotlinx.serialization.Serializable

/** Schedule v2 JSON 合同使用的 Unix 毫秒，不允许客户端自行换算为本地时区。 */
typealias UnixMillis = Long

/** 可独立 LWW 合并的业务字段；data 与 modifiedAt 都是 required。 */
@Serializable
data class AtomicField<T>(
  val data: T, // 当前业务值；Category.color、Schedule.recurrence 与 Schedule.todoState 可显式为 null。
  val modifiedAt: UnixMillis, // 客户端最后修改此原子的时刻；零值合法但字段不可缺失。
)

/** OccurrenceOverride 字段的继承、清空与替换模式。 */
@Serializable
enum class PatchMode { INHERIT, CLEAR, REPLACE }

/** 单次 occurrence 的活动、完成或取消状态。 */
@Serializable
enum class OccurrenceStatus { ACTIVE, COMPLETED, CANCELLED }

/** 日程进入清单后的完成状态；原子 data 为 null 时表示不属于清单。 */
@Serializable
enum class TodoState { OPEN, COMPLETED }

/** 日程的不可变创建来源。 */
@Serializable
enum class ScheduleKind { TODO, AFFAIR }

/** Schedule 时间联合类型；kind 决定哪些 nullable 时间字段可以出现。 */
@Serializable
enum class TimingKind { TIMED, DEADLINE, ALL_DAY, UNSCHEDULED }

/** 当前 wire 支持的重复频率。 */
@Serializable
enum class RecurrenceFrequency { DAILY, WEEKLY }

/** WEEKLY recurrence 使用的 ISO 风格星期枚举。 */
@Serializable
enum class Weekday { MO, TU, WE, TH, FR, SA, SU }

/** 普通单资源 mutation 的稳定机器结果码。 */
@Serializable
enum class MutationResultCode {
  CREATED, ALREADY_EXISTS, DELETED, ALREADY_DELETED, APPLIED,
  ALREADY_SATISFIED, SERVER_WON, REJECTED, RESOURCE_DELETED,
}

/** 原子批次及其逐项操作的稳定机器结果码。 */
@Serializable
enum class AtomicBatchResultCode { APPLIED, ALREADY_SATISFIED, REJECTED }

/** 拒绝结果可携带的稳定机器原因。 */
@Serializable
enum class ResultReason {
  INVALID_REQUEST, RESOURCE_NOT_FOUND, RESOURCE_DELETED, CATEGORY_NOT_FOUND,
  RESOURCE_CHANGED, FINAL_GRAPH_INVALID, UNSUPPORTED_RECURRENCE,
}

/** INHERIT/CLEAR 必须省略 value；REPLACE 必须携带完整 value。 */
@Serializable
data class FieldPatch<T>(
  val mode: PatchMode, // required，明确继承、清空或替换语义。
  val value: T? = null, // 仅 REPLACE 使用；其他模式保持 null 并从 JSON 省略。
)

/** 相对 Schedule timing 的提醒配置。 */
@Serializable
data class ReminderInput(
  val minutesBefore: Int, // required 且非负；零表示事件发生时提醒。
  val message: String, // required；空串表示没有自定义文案。
)

/** kind 决定 startAt/endAt/dueAt 的唯一合法组合。 */
@Serializable
data class TimingInput(
  val kind: TimingKind, // required，决定后续字段 presence。
  val startAt: UnixMillis? = null, // TIMED/ALL_DAY required，其他 kind 省略。
  val endAt: UnixMillis? = null, // TIMED/ALL_DAY required 且为排他结束边界。
  val dueAt: UnixMillis? = null, // DEADLINE required，其他 kind 省略。
)

/** 以 UTC 日期槽表达的重复规则；count 与 untilDate 互斥。 */
@Serializable
data class RecurrenceInput(
  val frequency: RecurrenceFrequency, // required，目前仅 DAILY/WEEKLY。
  val interval: Int, // required，正数周期跨度。
  val anchorDate: UnixMillis, // required，UTC 午夜日期槽。
  val count: Int? = null, // 可选成员数上限。
  val untilDate: UnixMillis? = null, // 可选包含边界，不早于 anchorDate。
  val weekdays: List<Weekday>, // required；DAILY 为空，WEEKLY 非空且不重复。
)

/** Category 的完整 live 快照；version 直接属于资源。 */
@Serializable
data class CategoryInput(
  val id: String, // required，owner 范围内稳定 identity。
  val version: ULong, // required；0 表示 CREATE，正数表示 PATCH。
  val name: AtomicField<String>, // required，名称原子。
  val color: AtomicField<String?>, // required；课表配色 JSON，没有自定义颜色时 data 显式为 null。
  val sortOrder: AtomicField<Long>, // required，排序原子。
)

/** Schedule 的完整 live 快照；kind 不可变，其余八个业务字段按原子合并。 */
@Serializable
data class ScheduleInput(
  val id: String, // required，owner 范围内稳定 identity。
  val version: ULong, // required；0 表示 CREATE，正数表示 PATCH。
  val kind: ScheduleKind, // required，创建来源；PATCH 不允许改变。
  val title: AtomicField<String>, // required，标题原子。
  val description: AtomicField<String>, // required，详情原子。
  val categoryId: AtomicField<String>, // required，Category 引用原子。
  val timing: AtomicField<TimingInput>, // required，完整时间联合值。
  val recurrence: AtomicField<RecurrenceInput?>, // required；data=null 明确表示非重复。
  val reminders: AtomicField<List<ReminderInput>>, // required，空列表合法。
  val todoState: AtomicField<TodoState?>, // required；data=null 表示当前不属于清单。
  val linkedToCourse: AtomicField<Boolean>, // required，是否请求投射到课表。
)

/** identity 固定为 scheduleId + occurrenceDate；移动 timing 不改变原始实例身份。 */
@Serializable
data class OccurrenceOverrideInput(
  val scheduleId: String, // required，parent Schedule identity。
  val occurrenceDate: UnixMillis, // required，UTC 午夜日期槽。
  val version: ULong, // required；0 表示 CREATE，正数表示 PATCH。
  val status: AtomicField<OccurrenceStatus>, // required，实例状态原子。
  val timing: AtomicField<FieldPatch<TimingInput>>, // required，完整 timing 三态原子；CLEAR 非法。
  val title: AtomicField<FieldPatch<String>>, // required，标题三态原子。
  val description: AtomicField<FieldPatch<String>>, // required，详情三态原子。
  val categoryId: AtomicField<FieldPatch<String>>, // required，分类三态原子。
  val reminders: AtomicField<FieldPatch<List<ReminderInput>>>, // required，提醒列表三态原子。
)

/** 客户端已持有的 live Category；tombstone 不进入 confirmed。 */
@Serializable
data class ConfirmedCategory(
  val id: String, // required，live identity。
  val version: ULong, // required 且 >0，客户端已确认的服务端版本。
)

/** 客户端已持有的 live Schedule；可与同 identity pending 同时上传。 */
@Serializable
data class ConfirmedSchedule(
  val id: String, // required，live identity。
  val version: ULong, // required 且 >0，客户端已确认的服务端版本。
)

/** 客户端已持有的 live OccurrenceOverride。 */
@Serializable
data class ConfirmedOccurrenceOverride(
  val scheduleId: String, // required，parent identity。
  val occurrenceDate: UnixMillis, // required，UTC 午夜日期槽。
  val version: ULong, // required 且 >0，客户端已确认的服务端版本。
)

/** delete-wins 的 Category 删除输入；不携带 version。 */
@Serializable
data class CategoryDelete(
  val id: String, // required，待删除 identity。
  val localModifiedAt: UnixMillis, // required，本地删除时刻；零值合法。
)

/** delete-wins 的 Schedule 删除输入；不携带 version。 */
@Serializable
data class ScheduleDelete(
  val id: String, // required，待删除 identity。
  val localModifiedAt: UnixMillis, // required，本地删除时刻；零值合法。
)

/** delete-wins 的 Override 删除输入；parent/date 构成 identity，不携带 version。 */
@Serializable
data class OccurrenceOverrideDelete(
  val scheduleId: String, // required，parent identity。
  val occurrenceDate: UnixMillis, // required，UTC 午夜日期槽。
  val localModifiedAt: UnixMillis, // required，本地删除时刻；零值合法。
)

/** Category live inventory 与普通 pending；三个列表都 required。 */
@Serializable
data class CategorySyncRequest(
  val confirmed: List<ConfirmedCategory>, // live-only inventory，空列表也显式发送。
  val upserts: List<CategoryInput>, // 结果与 upsertResults 按下标对齐。
  val deletes: List<CategoryDelete>, // 结果与 deleteResults 按下标对齐。
)

/** Schedule live inventory 与普通 pending；三个列表都 required。 */
@Serializable
data class ScheduleSyncRequest(
  val confirmed: List<ConfirmedSchedule>, // live-only，可与同 identity pending 并存。
  val upserts: List<ScheduleInput>, // 结果与 upsertResults 按下标对齐。
  val deletes: List<ScheduleDelete>, // 结果与 deleteResults 按下标对齐。
)

/** Override live inventory 与普通 pending；三个列表都 required。 */
@Serializable
data class OccurrenceOverrideSyncRequest(
  val confirmed: List<ConfirmedOccurrenceOverride>, // live-only parent/date inventory。
  val upserts: List<OccurrenceOverrideInput>, // 结果与 upsertResults 按下标对齐。
  val deletes: List<OccurrenceOverrideDelete>, // 结果与 deleteResults 按下标对齐。
)

/** 原子批次中的 Category 操作；两个列表均 required。 */
@Serializable
data class CategoryAtomicBlock(
  val upserts: List<CategoryInput>, // 与 atomic upsertResults 按下标对齐。
  val deletes: List<CategoryDelete>, // 与 atomic deleteResults 按下标对齐。
)

/** 原子批次中的 Schedule 操作；两个列表均 required。 */
@Serializable
data class ScheduleAtomicBlock(
  val upserts: List<ScheduleInput>, // 与 atomic upsertResults 按下标对齐。
  val deletes: List<ScheduleDelete>, // 与 atomic deleteResults 按下标对齐。
)

/** 原子批次中的 OccurrenceOverride 操作；两个列表均 required。 */
@Serializable
data class OccurrenceOverrideAtomicBlock(
  val upserts: List<OccurrenceOverrideInput>, // 与 atomic upsertResults 按下标对齐。
  val deletes: List<OccurrenceOverrideDelete>, // 与 atomic deleteResults 按下标对齐。
)

/** 需要最终资源图一致性的 typed 事务；批次至少包含一项操作。 */
@Serializable
data class AtomicBatch(
  val batchId: String, // required，请求内唯一，用于关联 AtomicBatchResult。
  val categories: CategoryAtomicBlock, // required，Category 原子操作块。
  val schedules: ScheduleAtomicBlock, // required，Schedule 原子操作块。
  val occurrenceOverrides: OccurrenceOverrideAtomicBlock, // required，Override 原子操作块。
)

/** 一次完整同步请求；所有 typed block/list 都必须显式出现。 */
@Serializable
data class SyncRequest(
  val syncRequestId: String, // required，请求级关联 ID，响应原样回显。
  val categories: CategorySyncRequest, // required，Category inventory 与普通 mutation。
  val schedules: ScheduleSyncRequest, // required，Schedule inventory 与普通 mutation。
  val occurrenceOverrides: OccurrenceOverrideSyncRequest, // required，Override inventory 与普通 mutation。
  val atomicBatches: List<AtomicBatch>, // required，空列表也必须显式发送。
)

/** canonical live 资源的服务端只读时间元数据。 */
@Serializable
data class ServerResourceMeta(
  val createdAt: UnixMillis, // required，服务端首次创建时刻。
  val remoteModifiedAt: UnixMillis, // required，服务端最后接受变更时刻。
)

/** 服务端 canonical live Category。 */
@Serializable
data class CategoryCurrent(
  val resource: CategoryInput, // required，包含当前 version 的完整资源。
  val meta: ServerResourceMeta, // required，服务端时间元数据。
)

/** 服务端 canonical live Schedule。 */
@Serializable
data class ScheduleCurrent(
  val resource: ScheduleInput, // required，字段合并后的完整资源，客户端直接接受。
  val meta: ServerResourceMeta, // required，服务端时间元数据。
  val firstRecurrenceAnchorDate: UnixMillis? = null, // 可选，首次启用重复的稳定 UTC 日期槽。
)

/** 服务端 canonical live OccurrenceOverride。 */
@Serializable
data class OccurrenceOverrideCurrent(
  val resource: OccurrenceOverrideInput, // required，parent/date 与四原子完整资源。
  val meta: ServerResourceMeta, // required，服务端时间元数据。
)

/** Category 删除状态；不携带 version/deleteVersion。 */
@Serializable
data class CategoryTombstone(
  val id: String, // required，被删除 identity。
  val deletedAt: UnixMillis, // required，服务端确认删除时刻。
  val reason: ResultReason? = null, // 可选稳定原因；无值时省略。
)

/** Schedule 删除状态；不携带 version/deleteVersion。 */
@Serializable
data class ScheduleTombstone(
  val id: String, // required，被删除 identity。
  val deletedAt: UnixMillis, // required，服务端确认删除时刻。
  val reason: ResultReason? = null, // 可选稳定原因；无值时省略。
)

/** OccurrenceOverride 删除状态；仍使用 parent/date identity。 */
@Serializable
data class OccurrenceOverrideTombstone(
  val scheduleId: String, // required，parent identity。
  val occurrenceDate: UnixMillis, // required，UTC 午夜日期槽。
  val deletedAt: UnixMillis, // required，服务端确认删除时刻。
  val reason: ResultReason? = null, // 可选稳定原因；无值时省略。
)

/** 与 categories.upserts 按下标对齐的结果。 */
@Serializable
data class CategoryUpsertResult(
  val id: String, // required，对应输入 identity。
  val code: MutationResultCode, // required，稳定处理结论。
  val reason: ResultReason? = null, // 可选，通常只在拒绝时存在。
  val current: CategoryCurrent? = null, // live 最终状态；与 tombstone 按 code 语义互斥。
  val tombstone: CategoryTombstone? = null, // 删除最终状态；与 current 按 code 语义互斥。
)

/** 与 schedules.upserts 按下标对齐的结果。 */
@Serializable
data class ScheduleUpsertResult(
  val id: String, // required，对应输入 identity。
  val code: MutationResultCode, // required，稳定处理结论。
  val reason: ResultReason? = null, // 可选，通常只在拒绝时存在。
  val current: ScheduleCurrent? = null, // live 合并结果；与 tombstone 按 code 语义互斥。
  val tombstone: ScheduleTombstone? = null, // 不可复活删除状态；与 current 互斥。
)

/** 与 occurrenceOverrides.upserts 按下标对齐的结果。 */
@Serializable
data class OccurrenceOverrideUpsertResult(
  val scheduleId: String, // required，对应输入 parent identity。
  val occurrenceDate: UnixMillis, // required，对应输入 UTC 日期槽。
  val code: MutationResultCode, // required，稳定处理结论。
  val reason: ResultReason? = null, // 可选，通常只在拒绝时存在。
  val current: OccurrenceOverrideCurrent? = null, // live 最终状态；与 tombstone 互斥。
  val tombstone: OccurrenceOverrideTombstone? = null, // 删除最终状态；与 current 互斥。
)

/** 与 categories.deletes 按下标对齐的结果。 */
@Serializable
data class CategoryDeleteResult(
  val id: String, // required，对应输入 identity。
  val code: MutationResultCode, // required，删除、幂等或拒绝结论。
  val reason: ResultReason? = null, // 可选机器原因。
  val current: CategoryCurrent? = null, // 删除拒绝时的 live 状态；与 tombstone 互斥。
  val tombstone: CategoryTombstone? = null, // 删除成功/已删除状态；与 current 互斥。
)

/** 与 schedules.deletes 按下标对齐的结果。 */
@Serializable
data class ScheduleDeleteResult(
  val id: String, // required，对应输入 identity。
  val code: MutationResultCode, // required，删除、幂等或拒绝结论。
  val reason: ResultReason? = null, // 可选机器原因。
  val current: ScheduleCurrent? = null, // 删除拒绝时的 live 状态；与 tombstone 互斥。
  val tombstone: ScheduleTombstone? = null, // 删除成功/已删除状态；与 current 互斥。
)

/** 与 occurrenceOverrides.deletes 按下标对齐的结果。 */
@Serializable
data class OccurrenceOverrideDeleteResult(
  val scheduleId: String, // required，对应输入 parent identity。
  val occurrenceDate: UnixMillis, // required，对应输入 UTC 日期槽。
  val code: MutationResultCode, // required，删除、幂等或拒绝结论。
  val reason: ResultReason? = null, // 可选机器原因。
  val current: OccurrenceOverrideCurrent? = null, // 删除拒绝时的 live 状态；与 tombstone 互斥。
  val tombstone: OccurrenceOverrideTombstone? = null, // 删除成功/已删除状态；与 current 互斥。
)

/** Category inventory delta 与普通操作结果。 */
@Serializable
data class CategorySyncResponse(
  val upserts: List<CategoryCurrent>, // required，需覆盖的 canonical live remote。
  val deletes: List<CategoryTombstone>, // required，需移除的 remote identity。
  val upsertResults: List<CategoryUpsertResult>, // required，与请求 upserts 按下标对齐。
  val deleteResults: List<CategoryDeleteResult>, // required，与请求 deletes 按下标对齐。
)

/** Schedule inventory delta 与普通操作结果。 */
@Serializable
data class ScheduleSyncResponse(
  val upserts: List<ScheduleCurrent>, // required，需覆盖的 canonical live remote。
  val deletes: List<ScheduleTombstone>, // required，需移除的 remote identity。
  val upsertResults: List<ScheduleUpsertResult>, // required，与请求 upserts 按下标对齐。
  val deleteResults: List<ScheduleDeleteResult>, // required，与请求 deletes 按下标对齐。
)

/** OccurrenceOverride inventory delta 与普通操作结果。 */
@Serializable
data class OccurrenceOverrideSyncResponse(
  val upserts: List<OccurrenceOverrideCurrent>, // required，需覆盖的 canonical live remote。
  val deletes: List<OccurrenceOverrideTombstone>, // required，需移除的 parent/date identity。
  val upsertResults: List<OccurrenceOverrideUpsertResult>, // required，与请求 upserts 按下标对齐。
  val deleteResults: List<OccurrenceOverrideDeleteResult>, // required，与请求 deletes 按下标对齐。
)

/** 原子批次 Category upsert 的逐项结论，与请求列表按下标对齐。 */
@Serializable
data class CategoryAtomicUpsertResult(
  val id: String, // required，对应输入 identity。
  val code: AtomicBatchResultCode, // required，整批语义下的结论。
  val reason: ResultReason? = null, // 可选拒绝原因。
)

/** 原子批次 Category delete 的逐项结论，与请求列表按下标对齐。 */
@Serializable
data class CategoryAtomicDeleteResult(
  val id: String, // required，对应输入 identity。
  val code: AtomicBatchResultCode, // required，整批语义下的结论。
  val reason: ResultReason? = null, // 可选拒绝原因。
)

/** 原子批次 Schedule upsert 的逐项结论，与请求列表按下标对齐。 */
@Serializable
data class ScheduleAtomicUpsertResult(
  val id: String, // required，对应输入 identity。
  val code: AtomicBatchResultCode, // required，整批语义下的结论。
  val reason: ResultReason? = null, // 可选拒绝原因。
)

/** 原子批次 Schedule delete 的逐项结论，与请求列表按下标对齐。 */
@Serializable
data class ScheduleAtomicDeleteResult(
  val id: String, // required，对应输入 identity。
  val code: AtomicBatchResultCode, // required，整批语义下的结论。
  val reason: ResultReason? = null, // 可选拒绝原因。
)

/** 原子批次 OccurrenceOverride upsert 的逐项结论。 */
@Serializable
data class OccurrenceOverrideAtomicUpsertResult(
  val scheduleId: String, // required，对应输入 parent identity。
  val occurrenceDate: UnixMillis, // required，对应输入 UTC 日期槽。
  val code: AtomicBatchResultCode, // required，整批语义下的结论。
  val reason: ResultReason? = null, // 可选拒绝原因。
)

/** 原子批次 OccurrenceOverride delete 的逐项结论。 */
@Serializable
data class OccurrenceOverrideAtomicDeleteResult(
  val scheduleId: String, // required，对应输入 parent identity。
  val occurrenceDate: UnixMillis, // required，对应输入 UTC 日期槽。
  val code: AtomicBatchResultCode, // required，整批语义下的结论。
  val reason: ResultReason? = null, // 可选拒绝原因。
)

/** Category 原子逐项结果与批次结束后的相关 canonical 状态。 */
@Serializable
data class CategoryAtomicResultBlock(
  val upsertResults: List<CategoryAtomicUpsertResult>, // 与 batch categories.upserts 按下标对齐。
  val deleteResults: List<CategoryAtomicDeleteResult>, // 与 batch categories.deletes 按下标对齐。
  val relatedUpserts: List<CategoryCurrent>, // 本批涉及 identity 的最终 live canonical 状态。
  val relatedDeletes: List<CategoryTombstone>, // 本批涉及 identity 的最终 tombstone 状态。
)

/** Schedule 原子逐项结果与批次结束后的相关 canonical 状态。 */
@Serializable
data class ScheduleAtomicResultBlock(
  val upsertResults: List<ScheduleAtomicUpsertResult>, // 与 batch schedules.upserts 按下标对齐。
  val deleteResults: List<ScheduleAtomicDeleteResult>, // 与 batch schedules.deletes 按下标对齐。
  val relatedUpserts: List<ScheduleCurrent>, // 本批涉及 identity 的最终 live canonical 状态。
  val relatedDeletes: List<ScheduleTombstone>, // 本批涉及 identity 的最终 tombstone 状态。
)

/** OccurrenceOverride 原子逐项结果与批次结束后的相关 canonical 状态。 */
@Serializable
data class OccurrenceOverrideAtomicResultBlock(
  val upsertResults: List<OccurrenceOverrideAtomicUpsertResult>, // 与 batch override upserts 按下标对齐。
  val deleteResults: List<OccurrenceOverrideAtomicDeleteResult>, // 与 batch override deletes 按下标对齐。
  val relatedUpserts: List<OccurrenceOverrideCurrent>, // 本批 parent/date 的最终 live canonical 状态。
  val relatedDeletes: List<OccurrenceOverrideTombstone>, // 本批 parent/date 的最终 tombstone 状态。
)

/** 一个 typed 原子批次的总体结论和最终资源图相关状态。 */
@Serializable
data class AtomicBatchResult(
  val batchId: String, // required，回显请求 batchId；用于按批关联，不是持久化 receipt。
  val code: AtomicBatchResultCode, // required，整批处理结论。
  val reason: ResultReason? = null, // 可选，整批拒绝时的机器原因。
  val categories: CategoryAtomicResultBlock, // required，Category 逐项与最终状态。
  val schedules: ScheduleAtomicResultBlock, // required，Schedule 逐项与最终状态。
  val occurrenceOverrides: OccurrenceOverrideAtomicResultBlock, // required，Override 逐项与最终状态。
)

/** 一次完整 Schedule v2 同步响应。 */
@Serializable
data class SyncResponse(
  val syncRequestId: String, // required，原样回显请求 ID，客户端须与本次请求对应。
  val categories: CategorySyncResponse, // required，Category delta 与普通结果。
  val schedules: ScheduleSyncResponse, // required，Schedule delta 与普通结果。
  val occurrenceOverrides: OccurrenceOverrideSyncResponse, // required，Override delta 与普通结果。
  val atomicBatchResults: List<AtomicBatchResult>, // required，按 batchId 关联请求 atomicBatches。
)
