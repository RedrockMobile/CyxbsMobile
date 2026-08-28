package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDateTime
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.time.LocalDateTimeResolution
import com.cyxbs.pages.schedule.domain.time.ScheduleDstResolver
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidationIssue
import com.cyxbs.pages.schedule.domain.validation.ScheduleValidator
import com.cyxbs.pages.schedule.ui.model.ScheduleDraft
import com.cyxbs.pages.schedule.ui.timeline.formatScheduleDateTime
import com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Compose 持有的日程编辑状态，负责在文本输入控件与领域值之间转换。
 *
 * 初始化时实例 [initialOccurrence] 的标题、描述、分类、时间和提醒优先于系列 [origin]，
 * 这样编辑已移动或覆盖的重复实例时不会回退到系列原值；重复规则仍来自 [origin]，因为实例例外不拥有 RRULE。
 * 该对象只保存本次弹窗会话状态，不直接写仓库；只有 [toDraft] 的结果会进入异步命令。
 */
@Stable
class EditScheduleModelState(
  val origin: Schedule?,
  val initialOccurrence: ScheduleOccurrence? = null,
  /** 新建入口指定的不可变来源；编辑既有日程时始终以 [origin] 为准。 */
  private val creationKind: ScheduleKind = ScheduleKind.TODO,
  /** 课表长按创建时传入的初始时间段，保存前不会写入仓库。 */
  creationTiming: ScheduleTiming? = null,
) {
  private val initialTiming = initialOccurrence?.timing ?: origin?.timing ?: creationTiming
  private val initialStartText = initialTiming?.toStartEditText().orEmpty()
  private val initialEndText = initialTiming?.toEndEditText().orEmpty()
  private val initialIsInterval = initialTiming is ScheduleTiming.Timed || origin == null
  private val initialIsAllDay = initialTiming is ScheduleTiming.AllDay
  private val initialRecurrenceDraft = origin?.recurrence.toDraft()
  private val initialReminders = if (initialOccurrence != null) initialOccurrence.reminders else origin?.reminders.orEmpty()
  val title = TextFieldState(if (initialOccurrence != null) initialOccurrence.title else origin?.title.orEmpty())
  val detail = TextFieldState(if (initialOccurrence != null) initialOccurrence.description else origin?.description.orEmpty())
  var categoryId by mutableStateOf(if (initialOccurrence != null) initialOccurrence.categoryId else origin?.categoryId)
  var startTime by mutableStateOf(initialStartText)
  var endTime by mutableStateOf(initialEndText)
  var isInterval by mutableStateOf(initialIsInterval)
  var isAllDay by mutableStateOf(initialIsAllDay)
  var recurrence by mutableStateOf(initialRecurrenceDraft)
  private val initialReminderMinutes = initialReminders.firstOrNull()?.offsetMinutes ?: -1
  var remindMinutes by mutableStateOf(initialReminderMinutes)
  /** 创建来源不可修改；关联设置只改变清单归属和课表投射状态。 */
  val kind: ScheduleKind = origin?.kind ?: creationKind
  private val initialTodoState = origin?.todoState ?: if (origin == null && kind == ScheduleKind.TODO) {
    ScheduleTodoState.PENDING
  } else {
    null
  }
  private val initialLinkedToCourse = origin?.linkedToCourse ?: (kind == ScheduleKind.AFFAIR)
  var todoState by mutableStateOf(initialTodoState)
  var linkedToCourse by mutableStateOf(initialLinkedToCourse)

  /** 当前日程是否属于清单；原生清单与关联清单后的事务都返回 true。 */
  val isInTodoList: Boolean get() = todoState != null

  /** 当前弹窗所展示 occurrence 的完成态；重复项优先读取实例状态。 */
  val isOccurrenceCompleted: Boolean get() = initialOccurrence?.status == OccurrenceStatus.COMPLETED ||
    (initialOccurrence == null && todoState == ScheduleTodoState.COMPLETED)

  /**
   * 切换课表页中的关联关系。
   *
   * 原生事务固定展示在课表，只切换清单归属；原生清单固定属于清单，只切换课表投射。
   * 事务重新关联清单时从未完成开始，避免恢复已经解除关联的旧完成态。
   */
  fun toggleCourseRelation() {
    when (kind) {
      ScheduleKind.TODO -> linkedToCourse = !linkedToCourse
      ScheduleKind.AFFAIR -> todoState = if (todoState == null) ScheduleTodoState.PENDING else null
    }
  }

  /** RRULE 编辑预览必须以父系列起点为 anchor，不能使用 moved occurrence 的展示日期。 */
  val recurrenceAnchorDate: Date get() = origin?.timing?.let(::timingAnchorDate) ?: anchorDate

  val anchorDate: Date get() = parseScheduleDateTime(outputStartTime)?.date
    ?: parseScheduleDateTime(outputEndTime)?.date
    ?: Date.now()
  val startMinuteOfDay: Int? get() = parseScheduleDateTime(outputStartTime)?.minuteOfDay
  val endMinuteOfDay: Int? get() = parseScheduleDateTime(outputEndTime)?.minuteOfDay
  val outputStartTime: String? get() = if (isInterval) startTime.takeIf(String::isNotBlank) else null
  val outputEndTime: String? get() = endTime.takeIf(String::isNotBlank)
  /** 摘要与 recurrence UI 均使用有效规则，避免 moved occurrence 的 anchor 改写隐式 BY* 展示。 */
  val outputRecurrence: RecurrenceRule? get() = effectiveRecurrence

  /**
   * 时间字段是否发生语义输入变化；只比较初始显示文本和模式，Compose 启动时回写相同文本不会误置 dirty。
   */
  internal val isTimingInputChanged: Boolean get() =
    startTime != initialStartText || endTime != initialEndText ||
      isInterval != initialIsInterval || isAllDay != initialIsAllDay

  /**
   * 重复编辑器是否被用户改变。未改变时直接保留原始 RRULE，避免 YEARLY 多月份等当前 UI 不可表达字段丢失。
   */
  internal val isRecurrenceInputChanged: Boolean get() = recurrence != initialRecurrenceDraft

  /** 未触碰 timing 时返回初始领域值，避免 DST overlap 的 local end 文本无法携带 fold 而重算错时长。 */
  internal val effectiveTiming: ScheduleTiming get() =
    if (!isTimingInputChanged && initialTiming != null) initialTiming else parsedTiming

  /** 未触碰 RRULE 时精确保留 origin；实际编辑后明确用当前 UI 支持子集整体替换。 */
  internal val effectiveRecurrence: RecurrenceRule? get() =
    if (!isRecurrenceInputChanged) origin?.recurrence else recurrence.toRecurrenceRule(recurrenceAnchorDate)

  /**
   * 根据“全天/是否为时间段/起止输入”组合生成四态时间模型。
   *
   * 实际编辑 Timed 时，起止均通过 [ScheduleDstResolver] 使用冻结的 gap/overlap 策略解析为 instant，再计算时长；
   * 非整分钟 gap 或无法消歧会降为非法时长并由 validator 阻止保存。未编辑路径不进入这里，而是保留 [initialTiming]。
   */
  private val parsedTiming: ScheduleTiming get() {
    val zone = when (val timing = initialTiming) {
      is ScheduleTiming.Timed -> timing.timeZoneId
      is ScheduleTiming.Deadline -> timing.timeZoneId
      else -> TimeZone.currentSystemDefault().id
    }
    if (isAllDay) return ScheduleTiming.AllDay(anchorDate)
    val start = parseMinuteTimeDate(outputStartTime)
    val end = parseMinuteTimeDate(outputEndTime)
    return when {
      isInterval && start != null && end != null -> {
        val startResolved = ScheduleDstResolver.resolve(start, zone) as? LocalDateTimeResolution.Resolved
        val endResolved = ScheduleDstResolver.resolve(end, zone) as? LocalDateTimeResolution.Resolved
        val duration = if (startResolved != null && endResolved != null) {
          (endResolved.instant - startResolved.instant).inWholeMinutes.toInt()
        } else {
          0
        }
        ScheduleTiming.Timed(start, duration, zone)
      }
      !isInterval && end != null -> ScheduleTiming.Deadline(end, zone)
      else -> ScheduleTiming.Unscheduled
    }
  }

  val validationIssues: List<ScheduleValidationIssue> get() = ScheduleValidator.validate(
    toDraft().let { draft ->
      if (draft.timing == ScheduleTiming.Unscheduled) draft.copy(reminders = emptyList()) else draft
    }.toNewDomainForValidation()
  )
  val canConfirm: Boolean get() = validationIssues.isEmpty()
  /** occurrence 投影中的标题是否被用户实际改动；用于保留其他未触碰的 existing patch。 */
  internal val isOccurrenceTitleChanged: Boolean get() = initialOccurrence?.let { outputTitle != it.title.trim() } ?: true
  /** occurrence 投影中的描述是否被用户实际改动。 */
  internal val isOccurrenceDescriptionChanged: Boolean get() = initialOccurrence?.let { outputDetail != it.description.trim() } ?: true
  /** occurrence 投影中的分类是否被用户实际改动。 */
  internal val isOccurrenceCategoryChanged: Boolean get() = initialOccurrence?.let { categoryId != it.categoryId } ?: true
  /** occurrence 投影中的完整 timing 是否被用户实际改动。 */
  internal val isOccurrenceTimingChanged: Boolean get() = isTimingInputChanged
  /** occurrence 的提醒控件是否被用户实际修改；timing 派生为空不得伪装成 reminder edit。 */
  internal val isOccurrenceRemindersChanged: Boolean get() = remindMinutes != initialReminderMinutes

  /**
   * 仅判断 occurrence 可覆盖字段是否变化；RRULE 是系列属性，不应让 THIS_ONLY 生成无意义 exception。
   */
  internal val isOccurrenceFieldsChanged: Boolean get() =
    isOccurrenceTitleChanged || isOccurrenceDescriptionChanged || isOccurrenceCategoryChanged ||
      isOccurrenceTimingChanged || isOccurrenceRemindersChanged

  /** RRULE 是否相对父系列发生变化，供 ALL 与 THIS_AND_FOLLOWING 独立判断系列编辑。 */
  internal val isSeriesRecurrenceChanged: Boolean get() = isRecurrenceInputChanged

  /** 清单归属和课表投射都是系列级属性，不允许写入单次 occurrence patch。 */
  internal val isSeriesRelationChanged: Boolean get() =
    todoState != initialTodoState || linkedToCourse != initialLinkedToCourse

  /**
   * 判断弹窗是否有任意未保存输入，用于关闭确认；scope 路由会进一步区分 occurrence 字段与系列 RRULE。
   *
   * occurrence 字段与初始化投影比较，避免 parent 演进后误判；RRULE 仍与 parent 比较，使从 occurrence 打开后
   * 只改重复规则也会提示未保存，但 THIS_ONLY 保存时不会把 RRULE 写进单次 patch。
   */
  val isChanged: Boolean get() {
    if (initialOccurrence != null) {
      return isOccurrenceFieldsChanged || isSeriesRecurrenceChanged || isSeriesRelationChanged
    }
    if (origin == null) {
      // 新建态只把用户相对初始草稿产生的有效输入视为修改；课表预填时间本身不触发二次确认。
      return outputTitle.isNotEmpty() || outputDetail.isNotEmpty() || categoryId != null ||
        isTimingInputChanged || isRecurrenceInputChanged ||
        remindMinutes != initialReminderMinutes || isSeriesRelationChanged
    }
    return toDraft().let { draft ->
      draft.title.trim() != origin.title || draft.description.trim() != origin.description ||
        draft.categoryId != origin.categoryId || draft.timing != origin.timing ||
        isRecurrenceInputChanged || draft.reminders != origin.reminders || isSeriesRelationChanged
    }
  }

  val outputTitle: String get() = title.text.toString().trim()
  val outputDetail: String get() = detail.text.toString().trim()

  /**
   * 提醒 payload 只由提醒控件输入决定；timing 改为 Unscheduled 时由路由层执行显式原子清理，不能伪造 dirty。
   */
  internal val effectiveReminders: List<ScheduleReminder> get() = when {
    remindMinutes < 0 -> emptyList()
    initialReminderMinutes == remindMinutes -> initialReminders
    else -> listOf(
      ScheduleReminder(
        ReminderId(initialReminders.firstOrNull()?.id?.value ?: "draft-reminder"),
        remindMinutes,
        ReminderChannel.DEVICE,
      ),
    )
  }

  /**
   * 生成不含持久化 DTO 的编辑草稿，供校验和仓库命令复用。
   *
   * 新建态使用固定占位 [ScheduleId]，仅用于让草稿满足完整领域校验；命令边界必须在写入前替换成真实 UUIDv7。
   * 时间戳、revision 与正式 ID 均由调用方/仓库拥有；未排期或“不提醒”不会生成提醒。
   */
  fun toDraft(): ScheduleDraft = ScheduleDraft(
    id = origin?.id ?: ScheduleId("00000000-0000-7000-8000-000000000000"),
    title = outputTitle,
    description = outputDetail,
    categoryId = categoryId,
    timing = effectiveTiming,
    recurrence = effectiveRecurrence,
    reminders = effectiveReminders,
    todoState = todoState,
    kind = kind,
    linkedToCourse = linkedToCourse,
  )

  /** 仅为运行完整领域校验补齐非编辑字段；占位时间与 revision 绝不会进入仓库。 */
  private fun ScheduleDraft.toNewDomainForValidation() = Schedule(
    id, 0, title, description, categoryId, timing, recurrence, reminders, todoState,
    Instant.DISTANT_PAST, Instant.DISTANT_PAST,
    kind = kind,
    linkedToCourse = linkedToCourse,
  )
}

@Composable
internal fun rememberEditScheduleModelState(
  editSchedule: Schedule?,
  occurrence: ScheduleOccurrence? = null,
  creationKind: ScheduleKind = ScheduleKind.TODO,
  creationTiming: ScheduleTiming? = null,
): EditScheduleModelState = remember(editSchedule, occurrence, creationKind, creationTiming) {
  EditScheduleModelState(editSchedule, occurrence, creationKind, creationTiming)
}

private fun timingAnchorDate(timing: ScheduleTiming): Date = when (timing) {
  is ScheduleTiming.Timed -> timing.start.date
  is ScheduleTiming.Deadline -> timing.due.date
  is ScheduleTiming.AllDay -> timing.startDate
  ScheduleTiming.Unscheduled -> Date.now()
}

private fun ScheduleTiming.toStartEditText(): String = when (this) {
  is ScheduleTiming.Timed -> start.toEditText()
  is ScheduleTiming.AllDay -> MinuteTimeDate(startDate, 0, 0).toEditText()
  else -> ""
}

private fun ScheduleTiming.toEndEditText(): String = when (this) {
  is ScheduleTiming.Timed -> start.toLocalDateTime().toInstant(TimeZone.of(timeZoneId))
    .plus(durationMinutes, kotlinx.datetime.DateTimeUnit.MINUTE, TimeZone.of(timeZoneId))
    .toLocalDateTime(TimeZone.of(timeZoneId)).toMinuteTimeDate().toEditText()
  is ScheduleTiming.Deadline -> due.toEditText()
  is ScheduleTiming.AllDay -> MinuteTimeDate(startDate, 0, 0).toEditText()
  ScheduleTiming.Unscheduled -> ""
}

private fun Schedule?.timingStartText(): String = this?.timing?.toStartEditText() ?: ""
private fun Schedule?.timingEndText(): String = this?.timing?.toEndEditText() ?: ""
private fun MinuteTimeDate.toEditText() = formatScheduleDateTime(
  date.year, date.monthNumber, date.dayOfMonth, time.hour, time.minute,
)

/** 将已解析的编辑输入收窄为分钟级领域时间，不引入秒或隐式系统时区。 */
private fun parseMinuteTimeDate(value: String?): MinuteTimeDate? =
  parseScheduleDateTime(value)?.let { parsed ->
    val minuteOfDay = parsed.minuteOfDay ?: 0
    MinuteTimeDate(parsed.date, minuteOfDay / 60, minuteOfDay % 60)
  }
