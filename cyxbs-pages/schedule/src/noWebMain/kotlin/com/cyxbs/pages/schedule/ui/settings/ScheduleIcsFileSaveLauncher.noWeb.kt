package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Desktop 复用 FileKit 的系统保存对话框，业务层只在用户选定位置后写入 UTF-8 文本。 */
@Composable
internal fun rememberFileKitScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
): ScheduleIcsFileSaveLauncher {
  val coroutineScope = rememberCoroutineScope()
  val currentOnResult by rememberUpdatedState(onResult)
  var pending by remember { mutableStateOf<PendingIcsExport?>(null) }
  val platformLauncher = rememberFileSaverLauncher(
    dialogSettings = FileKitDialogSettings.createDefault(),
  ) { file ->
    val export = pending
    pending = null
    if (file == null || export == null) {
      currentOnResult(ScheduleIcsFileSaveResult.Cancelled)
      return@rememberFileSaverLauncher
    }
    coroutineScope.launch {
      try {
        file.write(export.content.encodeToByteArray())
        currentOnResult(ScheduleIcsFileSaveResult.Saved("保存位置：${file.path}"))
      } catch (throwable: CancellationException) {
        throw throwable
      } catch (throwable: Throwable) {
        currentOnResult(ScheduleIcsFileSaveResult.Failed(throwable))
      }
    }
  }
  return remember(platformLauncher) {
    ScheduleIcsFileSaveLauncher(isSupported = true) { fileName, content ->
      pending = PendingIcsExport(fileName, content)
      platformLauncher.launch(
        suggestedName = fileName.removeSuffix(".ics"),
        defaultExtension = "ics",
        allowedExtensions = setOf("ics"),
      )
    }
  }
}

/** 保存对话框打开期间冻结文件名与内容，避免仓库更新改变用户即将保存的文件。 */
private data class PendingIcsExport(
  val fileName: String,
  val content: String,
)

/**
 * iOS 的轻量导出流程：固定写入应用文件目录，再执行系统分享动作。
 *
 * [afterSave] 失败不回滚已经写好的文件；应用卸载时该目录会随沙盒一起删除。
 */
@Composable
internal fun rememberAppFilesScheduleIcsFileSaveLauncher(
  onResult: (ScheduleIcsFileSaveResult) -> Unit,
  afterSave: suspend (PlatformFile) -> Unit,
): ScheduleIcsFileSaveLauncher {
  val coroutineScope = rememberCoroutineScope()
  val currentOnResult by rememberUpdatedState(onResult)
  val currentAfterSave by rememberUpdatedState(afterSave)
  return remember {
    ScheduleIcsFileSaveLauncher(isSupported = true) { fileName, content ->
      coroutineScope.launch {
        try {
          val directory = FileKit.filesDir.resolve(ICS_DIRECTORY_NAME)
          directory.createDirectories()
          val file = directory.resolve(fileName)
          file.write(content.encodeToByteArray())
          currentOnResult(
            ScheduleIcsFileSaveResult.Saved("保存位置：应用文件/$ICS_DIRECTORY_NAME/$fileName"),
          )
          runCatching { currentAfterSave(file) }
        } catch (throwable: CancellationException) {
          throw throwable
        } catch (throwable: Throwable) {
          currentOnResult(ScheduleIcsFileSaveResult.Failed(throwable))
        }
      }
    }
  }
}

private const val ICS_DIRECTORY_NAME = "schedule"
