package com.cyxbs.pages.schedule.ui.service

import com.cyxbs.pages.schedule.api.ScheduleExternalCreateFailureReason
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateRequest
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateResult
import com.cyxbs.pages.schedule.api.ScheduleExternalCategory
import com.cyxbs.pages.schedule.api.ScheduleExternalRecurrence
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import com.cyxbs.pages.schedule.domain.uuid.stableUuidV5
import com.cyxbs.pages.schedule.ui.category.ScheduleDefaultCategories
import kotlin.time.Clock

/**
 * 将外部业务资源转换并保存为原生日程。
 *
 * 该类集中处理幂等 identity、固定分类和本地优先结果，避免通知中心、活动中心分别复制 Schedule 领域规则。
 */
internal class ScheduleExternalCreateService(
  private val repository: ScheduleRepository,
  private val clock: Clock,
) {

  /**
   * 创建一条外部来源日程。
   *
   * 相同账号、来源与 [ScheduleExternalCreateRequest.sourceId] 始终映射到同一个 Schedule ID。若已经存在，
   * 不会覆盖用户随后在日程页做出的修改。远端失败但本地已保存时仍视为成功。
   */
  suspend fun create(request: ScheduleExternalCreateRequest): ScheduleExternalCreateResult {
    repository.initialize()
    val snapshot = repository.snapshot.value
    val accountId = snapshot.accountId
      ?: return failure(ScheduleExternalCreateFailureReason.ACCOUNT_REQUIRED)
    val input = request.toValidatedInput()
      ?: return failure(ScheduleExternalCreateFailureReason.INVALID_INPUT)
    val scheduleId = ScheduleId(
      stableUuidV5(
        namespace = "com.cyxbs.schedule.external",
        identity = "${accountId}|${request.source.name}|${input.sourceId}",
      ),
    )
    if (snapshot.schedules.any { it.id == scheduleId }) {
      return ScheduleExternalCreateResult.Success(scheduleId, alreadyExists = true)
    }

    val category = resolveCategory(input.category, snapshot.categories)
    val now = clock.now()
    val schedule = Schedule(
      id = scheduleId,
      revision = 0,
      title = input.title,
      description = input.description,
      categoryId = category?.selected?.id,
      timing = input.timing,
      recurrence = input.recurrence,
      reminder = input.reminderOffsetMinutes?.let(::ScheduleReminder),
      todoState = if (input.isInTodoList) ScheduleTodoState.PENDING else null,
      createdAt = now,
      updatedAt = now,
      kind = input.kind,
      linkedToCourse = input.linkedToCourse,
      recurrenceAnchorDate = input.recurrence?.let { input.timing.date() },
    )
    val command = category?.missingDefault?.let { missing ->
      ScheduleCommand.SaveScheduleWithNewCategory(missing, schedule)
    } ?: ScheduleCommand.Create(schedule)
    val result = repository.execute(command)
    return when (result) {
      is ScheduleSyncResult.Success ->
        ScheduleExternalCreateResult.Success(scheduleId, alreadyExists = false)
      is ScheduleSyncResult.Failure -> {
        // Room 先落本地、再请求远端；真正发起过远端请求就说明本地事实已保存，后续由 pending 继续同步。
        if (result.attempted) ScheduleExternalCreateResult.Success(scheduleId, alreadyExists = false)
        else failure(ScheduleExternalCreateFailureReason.LOCAL_SAVE_FAILED)
      }
      null -> failure(ScheduleExternalCreateFailureReason.LOCAL_SAVE_FAILED)
    }
  }
}

/** 校验并转换 API 请求；失败返回 null，不把半成品输入带入领域层。 */
private fun ScheduleExternalCreateRequest.toValidatedInput(): ValidatedExternalInput? {
  val cleanSourceId = sourceId.trim().takeIf(String::isNotEmpty) ?: return null
  val cleanTitle = title.trim().takeIf(String::isNotEmpty) ?: return null
  val reminderOffset = reminderOffsetMinutes
  if (reminderOffset != null && reminderOffset < 0) return null
  if (category != null && !isInTodoList) return null
  if (kind == ScheduleOccurrenceKind.TODO && !isInTodoList) return null
  val domainTiming = timing.toDomainTiming() ?: return null
  val domainRecurrence = when (val value = recurrence) {
    null -> null
    is ScheduleExternalRecurrence.Weekly -> {
      if (value.occurrenceCount <= 0 || domainTiming == ScheduleTiming.Unscheduled) return null
      val weekDay = IsoWeekDay.fromIsoNumber(domainTiming.date().dayOfWeekNumber) ?: return null
      RecurrenceRule(
        frequency = RecurrenceFrequency.WEEKLY,
        byWeekDays = setOf(weekDay),
        end = RecurrenceEnd.Count(value.occurrenceCount),
      )
    }
  }
  return ValidatedExternalInput(
    sourceId = cleanSourceId,
    title = cleanTitle,
    description = description.trim(),
    category = category,
    timing = domainTiming,
    recurrence = domainRecurrence,
    reminderOffsetMinutes = reminderOffset,
    kind = when (kind) {
      ScheduleOccurrenceKind.TODO -> ScheduleKind.TODO
      ScheduleOccurrenceKind.AFFAIR -> ScheduleKind.AFFAIR
    },
    isInTodoList = isInTodoList,
    linkedToCourse = linkedToCourse,
  )
}

/** 将不含课表实现类型的 API 时间转换成 Schedule 领域时间，并拒绝非法时长。 */
private fun ScheduleOccurrenceTiming.toDomainTiming(): ScheduleTiming? = when (this) {
  is ScheduleOccurrenceTiming.Timed -> if (durationMinutes > 0 && timeZoneId.isNotBlank()) {
    ScheduleTiming.Timed(start, durationMinutes, timeZoneId)
  } else null
  is ScheduleOccurrenceTiming.Deadline -> if (timeZoneId.isNotBlank()) {
    ScheduleTiming.Deadline(due, timeZoneId)
  } else null
  is ScheduleOccurrenceTiming.AllDay -> ScheduleTiming.AllDay(date)
  ScheduleOccurrenceTiming.Unscheduled -> ScheduleTiming.Unscheduled
}

/** 返回有日期时间模型的首日；重复规则不会调用未排期分支。 */
private fun ScheduleTiming.date() = when (this) {
  is ScheduleTiming.Timed -> start.date
  is ScheduleTiming.Deadline -> due.date
  is ScheduleTiming.AllDay -> date
  ScheduleTiming.Unscheduled -> error("Unscheduled timing has no recurrence date")
}

/**
 * 解析固定分类。
 *
 * 仓库已有同名或同 identity 的分类时复用远端事实；否则只允许返回固定候选，并交由组合命令惰性创建。
 */
private fun resolveCategory(
  requested: ScheduleExternalCategory?,
  actualCategories: List<ScheduleCategory>,
): ResolvedExternalCategory? {
  requested ?: return null
  val default = ScheduleDefaultCategories.first {
    it.name == requested.displayName
  }
  val actual = actualCategories.firstOrNull {
    it.id == default.id || it.name.trim().equals(default.name, ignoreCase = true)
  }
  return ResolvedExternalCategory(
    selected = actual ?: default,
    missingDefault = default.takeIf { actual == null },
  )
}

/** 已校验的创建参数，避免主流程重复空值与范围判断。 */
private data class ValidatedExternalInput(
  val sourceId: String,
  val title: String,
  val description: String,
  val category: ScheduleExternalCategory?,
  val timing: ScheduleTiming,
  val recurrence: RecurrenceRule?,
  val reminderOffsetMinutes: Int?,
  val kind: ScheduleKind,
  val isInTodoList: Boolean,
  val linkedToCourse: Boolean,
)

/** 固定分类的最终选择及是否需要随日程一起创建。 */
private data class ResolvedExternalCategory(
  val selected: ScheduleCategory,
  val missingDefault: ScheduleCategory?,
)

/** 构造可展示的失败结果；调用方也可接收常量列表外的具体中文业务原因。 */
private fun failure(reason: String) =
  ScheduleExternalCreateResult.Failure(reason)
