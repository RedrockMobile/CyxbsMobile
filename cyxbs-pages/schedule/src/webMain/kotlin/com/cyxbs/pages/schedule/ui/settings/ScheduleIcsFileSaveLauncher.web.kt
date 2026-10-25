package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Web 通过浏览器下载能力把 ICS 保存到用户配置的下载目录，不尝试访问本地文件系统路径。 */
@Composable
internal actual fun rememberScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
): ScheduleIcsFileSaveLauncher {
  val coroutineScope = rememberCoroutineScope()
  val currentOnResult by rememberUpdatedState(onResult)
  return remember {
    ScheduleIcsFileSaveLauncher(isSupported = true) { fileName, content ->
      coroutineScope.launch {
        try {
          FileKit.download(content.encodeToByteArray(), fileName)
          currentOnResult(
            ScheduleIcsFileSaveResult.Saved("导出文件为 $fileName"),
          )
        } catch (throwable: CancellationException) {
          throw throwable
        } catch (throwable: Throwable) {
          currentOnResult(ScheduleIcsFileSaveResult.Failed(throwable))
        }
      }
    }
  }
}
