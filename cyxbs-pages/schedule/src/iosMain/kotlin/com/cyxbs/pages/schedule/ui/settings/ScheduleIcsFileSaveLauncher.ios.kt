package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.shareFile

/** iOS 写入应用 Documents/schedule，再通过系统分享面板把 ICS 文件交给目标应用。 */
@Composable
internal actual fun rememberScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
): ScheduleIcsFileSaveLauncher = rememberAppFilesScheduleIcsFileSaveLauncher(
  onResult = onResult,
  afterSave = { FileKit.shareFile(it) },
)
