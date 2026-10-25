package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.runtime.Composable

/** Desktop 使用系统文件保存面板，最终目录由用户选择。 */
@Composable
internal actual fun rememberScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
): ScheduleIcsFileSaveLauncher = rememberFileKitScheduleIcsFileSaveLauncher(onResult)
