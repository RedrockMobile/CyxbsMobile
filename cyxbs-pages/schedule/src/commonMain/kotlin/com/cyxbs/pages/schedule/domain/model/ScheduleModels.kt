package com.cyxbs.pages.schedule.domain.model

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlin.time.Instant

/**
 * Schedule 的完整领域对象。
 *
 * 它表达已通过领域校验的业务事实；网络 DTO、持久化 Record 与编辑草稿必须保持独立，避免协议缺省值、
 * 存储兼容字段或未完成输入渗入领域规则。[revision] 用于远端快照合并，不等同于本地编辑次数。
 */
data class Schedule(
  val id: ScheduleId,
  val revision: Long,
  val title: String,
  val description: String,
  val categoryId: CategoryId?,
  val timing: ScheduleTiming,
  val recurrence: RecurrenceRule?,
  /** 当前唯一提醒；`null` 表示不提醒。 */
  val reminder: ScheduleReminder?,
  val todoState: ScheduleTodoState?,
  val createdAt: Instant,
  val updatedAt: Instant,
  /** 日程的创建来源；用于区分原生清单与原生事务，创建后不可修改。 */
  val kind: ScheduleKind = ScheduleKind.TODO,
  /** 用户是否要求把该日程投射到课表；最终可见性仍需结合 [kind] 与完成态判断。 */
  val linkedToCourse: Boolean = false,
  /**
   * 当前重复规则的逻辑日期锚点；修改整个系列日期时它随新规则更新。
   * 单次调整的稳定身份由其 `originalOccurrenceDate` 单独保存，因此不依赖这里保留旧日期；新建且尚未经过
   * 仓库投影时可为 null，此时以当前 [timing] 日期作为初始锚点。
   */
  val recurrenceAnchorDate: Date? = null,
)

/**
 * 日程的不可变来源类型。
 *
 * TODO 完成后会暂时从课表隐藏；AFFAIR 即使后来关联到清单并完成，仍保留事务的课表展示语义。
 */
enum class ScheduleKind {
  /** 从清单创建。 */
  TODO,
  /** 从课表事务创建。 */
  AFFAIR,
}

/**
 * Schedule 支持的四种互斥时间语义。
 *
 * 本地墙上时间与 IANA 时区分开保存，而不是过早转换为 [Instant]；这样重复日程跨越 DST 时仍保持用户
 * 设定的当地时刻。全天日程则只使用日期，避免把“一天”错误固定为 24 小时。
 */
sealed interface ScheduleTiming {
  /** 有明确本地开始时间和正时长的日程；时长可以跨越自然日。 */
  data class Timed(
    val start: MinuteTimeDate,
    val durationMinutes: Int,
    val timeZoneId: String,
  ) : ScheduleTiming

  /** 截止于某个本地墙上时间的任务；日历适配器可投影展示，但必须保留“截止”语义。 */
  data class Deadline(
    val due: MinuteTimeDate,
    val timeZoneId: String,
  ) : ScheduleTiming

  /** 只含一个本地日期的全天日程，绝不换算成固定 24 小时。 */
  data class AllDay(
    val date: Date,
  ) : ScheduleTiming

  /** 尚未安排时间的事项；默认不导出到系统日历。 */
  data object Unscheduled : ScheduleTiming
}

/**
 * 日程进入清单后的完成状态；`null` 表示该日程当前不属于清单。
 * 重复系列中某次完成必须记录为 occurrence 例外。
 */
enum class ScheduleTodoState {
  /** 尚未完成。 */
  PENDING,
  /** 已完成整个非重复日程。 */
  COMPLETED,
}

/**
 * 相对发生开始或截止时刻的提醒配置。
 *
 * [offsetMinutes] 表示提前分钟数，零表示准时提醒；可接受范围由领域校验器统一约束。
 */
data class ScheduleReminder(
  val offsetMinutes: Int,
)

/** 用户自定义日程分类；[color] 是可选课表配色 JSON，领域层保持不透明，由 UI 边界负责校验与解析。 */
data class ScheduleCategory(
  val id: CategoryId,
  val revision: Long,
  val name: String,
  val color: String?,
  val sortOrder: Int,
)

/**
 * 系统当前可互操作的 RFC 5545 重复规则子集。
 *
 * 只建模各平台能够无损表达的字段；解析或同步层不得静默接收子集外语义后再降级，否则同一系列会在
 * Android、iOS 与服务端产生不同 occurrence identity。
 */
data class RecurrenceRule(
  val frequency: RecurrenceFrequency,
  val interval: Int = 1,
  val byWeekDays: Set<IsoWeekDay> = emptySet(),
  val byMonthDays: Set<Int> = emptySet(),
  val byMonths: Set<Int> = emptySet(),
  val end: RecurrenceEnd = RecurrenceEnd.Never,
)

/** 可在 Android 与 iOS 日历之间无损转换的重复频率。 */
enum class RecurrenceFrequency {
  /** 按日重复。 */ DAILY,
  /** 按周重复。 */ WEEKLY,
  /** 按月重复。 */ MONTHLY,
  /** 按年重复。 */ YEARLY,
}

/** 重复规则互斥的终止条件。 */
sealed interface RecurrenceEnd {
  /** 不主动终止；展开仍必须由调用方提供有界窗口。 */
  data object Never : RecurrenceEnd
  /** 包含指定本地日期在内的截止条件；产品只提供日期粒度，不携带偶然的时分秒。 */
  data class Until(val date: Date) : RecurrenceEnd
  /** 最多生成指定数量的规则实例。 */
  data class Count(val value: Int) : RecurrenceEnd
}

/** 同时携带稳定 ISO-8601 数字与 RFC 5545 文本的星期值。 */
enum class IsoWeekDay(val isoNumber: Int, val rfc5545: String) {
  MONDAY(1, "MO"), TUESDAY(2, "TU"), WEDNESDAY(3, "WE"), THURSDAY(4, "TH"),
  FRIDAY(5, "FR"), SATURDAY(6, "SA"), SUNDAY(7, "SU");

  companion object {
    /** 将 1..7 的 ISO 星期数字转换为枚举，范围外返回 `null`。 */
    fun fromIsoNumber(number: Int): IsoWeekDay? = entries.firstOrNull { it.isoNumber == number }
  }
}

typealias RecurrenceId = com.cyxbs.pages.schedule.api.RecurrenceId

/**
 * 重复系列中某次发生的独立状态与可选覆盖。
 *
 * [recurrenceId] 始终指向规则原始生成的实例；即使 [patch] 移动了显示时间，也不能改变身份。
 */
data class ScheduleOccurrenceAdjustment(
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId,
  val revision: Long,
  val status: OccurrenceStatus,
  val patch: OccurrencePatch?,
  val createdAt: Instant,
  val updatedAt: Instant,
)

/** 单次发生状态；取消与完成必须区分，以便同步、统计和后续恢复保持不同语义。 */
enum class OccurrenceStatus {
  /** 正常存在且未完成。 */ ACTIVE,
  /** 用户已完成该次发生。 */ COMPLETED,
  /** 该次发生已从系列中取消，不应再物化展示。 */ CANCELLED,
}

/**
 * 稀疏编辑字段的统一三态。
 *
 * [Inherit] 沿用系列值，[Clear] 显式清空支持清空的字段，[Replace] 使用非空的新业务值。类型参数协变且
 * [Replace] 不接受可空字段约定；若未来业务值本身允许 `null`，必须另建有标签的值类型，不能让
 * `Replace(null)` 与 [Clear] 混淆。
 */
sealed interface FieldPatch<out T> {
  /** 不产生覆盖，继续继承系列值。 */
  data object Inherit : FieldPatch<Nothing>

  /** 显式清空字段；调用方只能用于该字段业务语义允许清空时。 */
  data object Clear : FieldPatch<Nothing>

  /** 用非空业务值覆盖系列值。 */
  data class Replace<T : Any>(val value: T) : FieldPatch<T>
}

/**
 * 单次 occurrence 的时间形态覆盖，不携带日期。
 *
 * 日期由 [OccurrencePatch.date] 独立控制；未覆盖时间时继续继承父系列。这样整系列改日期、改时间与单次
 * 自定义日期/时间可以分别合并，不会因完整 [ScheduleTiming] 替换而互相覆盖。显式替换的 Timed/Deadline
 * 保存 IANA 时区，使父系列改成全天后，既有单次时间覆盖仍能独立物化。
 */
sealed interface OccurrenceTime {
  /** 某个日内分钟开始的时间段；[durationMinutes] 可跨越自然日。 */
  data class TimeRange(
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val timeZoneId: String,
  ) : OccurrenceTime {
    init {
      require(startMinuteOfDay in 0 until 24 * 60) { "startMinuteOfDay is out of range" }
      require(durationMinutes > 0) { "durationMinutes must be positive" }
    }
  }

  /** 某个日内分钟的时间点。 */
  data class TimePoint(
    val minuteOfDay: Int,
    val timeZoneId: String,
  ) : OccurrenceTime {
    init {
      require(minuteOfDay in 0 until 24 * 60) { "minuteOfDay is out of range" }
    }
  }

  /** 全天 occurrence；日期仍由继承结果或 [OccurrencePatch.date] 决定。 */
  data object AllDay : OccurrenceTime
}

/**
 * 某次发生的稀疏覆盖，每个字段均显式保留继承、清空和替换三态。
 *
 * [date] 与 [time] 分别是字段级原子：整系列改日期时不会覆盖单次时间，整系列改时间时也
 * 不会挪动显式指定日期的单次。日期、时间和标题不允许 [FieldPatch.Clear]；描述与分类允许显式清空，
 * 提醒用 Clear 表示取消，Replace 表示设置单个提醒。
 */
data class OccurrencePatch(
  val date: FieldPatch<Date> = FieldPatch.Inherit,
  val time: FieldPatch<OccurrenceTime> = FieldPatch.Inherit,
  val title: FieldPatch<String> = FieldPatch.Inherit,
  val description: FieldPatch<String> = FieldPatch.Inherit,
  val categoryId: FieldPatch<CategoryId> = FieldPatch.Inherit,
  val reminder: FieldPatch<ScheduleReminder> = FieldPatch.Inherit,
)
