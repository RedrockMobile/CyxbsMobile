package com.cyxbs.pages.schedule.ui.edit

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncRepository

/**
 * 统一编辑弹窗（[EditScheduleDialog]）的保存/删除路由——把表单产物 + [EditScope] 翻译成仓库调用。
 *
 * 抽成 [ScheduleSyncRepository] 扩展函数，让「邮子清单 ViewModel」与「课表 SchedulePageDecoration」
 * 两处入口共用同一套三态路由逻辑，避免重复。均为 suspend，由调用方在自己的协程作用域里发起。
 */

/** 保存：新建 / 按三态更新。 */
suspend fun ScheduleSyncRepository.applyScheduleEdit(
  state: EditScheduleModelState,
  scope: EditScope,
  occurrenceDate: Date?,
) {
  val origin = state.origin
  if (origin == null) {
    createSchedule(
      title = state.outputTitle,
      detail = state.outputDetail,
      type = state.type,
      startTime = state.outputStartTime,
      endTime = state.outputEndTime,
      recurrence = state.outputRecurrence,
      remindMinutes = state.remindMinutes,
    )
    return
  }
  when (scope) {
    EditScope.ALL -> updateSchedule(state.toEntity(origin))
    EditScope.THIS_ONLY -> if (occurrenceDate != null) editThisOccurrence(
      origin.todoId, occurrenceDate,
      buildOccurrenceOverride(occurrenceDate, state.toEntity(origin), origin),
    )
    EditScope.THIS_AND_FOLLOWING -> if (occurrenceDate != null) editThisAndFollowing(
      origin.todoId, occurrenceDate,
      buildFollowingSeries(state.toEntity(origin), occurrenceDate),
    )
  }
}

/** 删除：按三态删除。 */
suspend fun ScheduleSyncRepository.applyScheduleDelete(
  todoId: Long,
  scope: EditScope,
  occurrenceDate: Date?,
) {
  when (scope) {
    EditScope.ALL -> deleteSchedule(todoId)
    EditScope.THIS_ONLY -> if (occurrenceDate != null) deleteThisOccurrence(todoId, occurrenceDate)
    EditScope.THIS_AND_FOLLOWING -> if (occurrenceDate != null) deleteThisAndFollowing(todoId, occurrenceDate)
  }
}
