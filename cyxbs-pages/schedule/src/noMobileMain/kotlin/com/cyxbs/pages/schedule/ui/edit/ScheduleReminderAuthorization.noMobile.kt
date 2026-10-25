package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/** 非移动端尚无系统日历提醒能力，用户尝试启用时明确返回失败。 */
@Composable
internal actual fun rememberScheduleReminderAuthorization(
  onResult: (Boolean) -> Unit,
): ScheduleReminderAuthorization {
  val currentOnResult = rememberUpdatedState(onResult)
  return ScheduleReminderAuthorization(
    authorized = false,
    requestAuthorization = {
      currentOnResult.value(false)
    },
  )
}
