package com.cyxbs.pages.schedule.api

/**
 * 外部业务创建日程时使用的稳定来源类型。
 *
 * 同一个账号内，[ScheduleExternalCreateRequest.sourceId] 只需在对应来源下唯一；Schedule 会据此生成稳定 ID，
 * 避免用户重复点击或请求重试时创建重复日程。
 */
enum class ScheduleExternalSource {
  /** 通知中心收到的没课约行程。 */
  ITINERARY,

  /** 邮子活动中的活动详情。 */
  UFIELD_ACTIVITY,
}

/** 外部业务可选择的固定分类；传入 null 仍表示不分组。 */
enum class ScheduleExternalCategory(
  val displayName: String,
) {
  STUDY("学习"),
  LIFE("生活"),
  OTHER("其他"),
}

/**
 * 外部业务当前需要的重复配置。
 *
 * 没课约只会表达“指定周单次发生”或“整学期同一星期重复”，因此这里暂不暴露日程编辑器的完整重复能力。
 */
sealed interface ScheduleExternalRecurrence {
  /** 从首个时间段开始按周重复 [occurrenceCount] 次。 */
  data class Weekly(
    val occurrenceCount: Int,
  ) : ScheduleExternalRecurrence
}

/**
 * 外部模块创建原生日程的请求。
 *
 * [isInTodoList] 与 [linkedToCourse] 是相互独立的业务状态：事务可以只展示在课表，也可以同时进入清单；
 * 清单也可以选择是否投射到课表。[category] 只接受固定分类，传入 null 表示不分组。
 */
data class ScheduleExternalCreateRequest(
  val source: ScheduleExternalSource,
  val sourceId: String,
  val title: String,
  val description: String = "",
  val timing: ScheduleOccurrenceTiming,
  val recurrence: ScheduleExternalRecurrence? = null,
  /** null 表示不提醒，0 表示准时提醒，正数表示提前对应分钟。 */
  val reminderOffsetMinutes: Int? = null,
  val kind: ScheduleOccurrenceKind,
  val isInTodoList: Boolean,
  val linkedToCourse: Boolean,
  val category: ScheduleExternalCategory? = null,
)

/** 外部创建结果；远端失败但本地已经保存时仍返回 [Success]，由 Schedule 自己继续同步。 */
sealed interface ScheduleExternalCreateResult {
  /** [alreadyExists] 为 true 表示相同来源日程此前已经创建，本次没有覆盖用户后续编辑。 */
  data class Success(
    val scheduleId: ScheduleId,
    val alreadyExists: Boolean,
  ) : ScheduleExternalCreateResult

  /** 请求无法形成合法本地日程，调用方可提示添加失败。 */
  data class Failure(
    /** 可直接展示的中文原因；底层除预设文案外也可以返回更具体的业务原因。 */
    val reason: String,
  ) : ScheduleExternalCreateResult
}

/**
 * 外部创建常用的稳定失败文案。
 *
 * 这些常量用于统一常见提示，但 [ScheduleExternalCreateResult.Failure.reason] 不受此列表限制，底层可在
 * 不泄漏凭证、请求正文和内部异常细节的前提下返回其他中文业务原因，但推荐都放在一起。
 */
object ScheduleExternalCreateFailureReason {
  /** 标题、时间、重复、提醒或状态组合不合法。 */
  const val INVALID_INPUT = "日程参数不合法"

  /** 当前没有可写的登录账号。 */
  const val ACCOUNT_REQUIRED = "请先登录账号"

  /** 本地命令未能保存；远端暂时不可用但本地已保存不属于此情况。 */
  const val LOCAL_SAVE_FAILED = "日程保存失败"
}
