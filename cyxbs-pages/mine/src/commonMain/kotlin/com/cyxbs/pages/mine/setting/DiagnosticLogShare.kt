package com.cyxbs.pages.mine.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.cyxbs.components.config.log.AppLogManager
import com.cyxbs.components.log.LogExportResult
import com.cyxbs.components.log.LogStorageResult
import com.cyxbs.components.utils.extensions.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class DiagnosticLogFileShareLauncher(
  val isSupported: Boolean,
  val launch: (filePath: String) -> Unit,
)

@Composable
internal expect fun rememberDiagnosticLogFileShareLauncher(
  onFailure: (message: String) -> Unit,
): DiagnosticLogFileShareLauncher

@Composable
internal fun rememberDiagnosticLogShareAction(): () -> Unit {
  val coroutineScope = rememberCoroutineScope()
  val currentOnFailure by rememberUpdatedState<(String) -> Unit> { message -> toast(message) }
  val shareLauncher = rememberDiagnosticLogFileShareLauncher(currentOnFailure)
  var exporting by remember { mutableStateOf(false) }
  return remember(coroutineScope, shareLauncher) {
    {
      if (!exporting) {
        exporting = true
        toast("正在生成诊断日志文件…")
        coroutineScope.launch {
          try {
            when (val result = AppLogManager.exportDiagnosticLogs()) {
              is LogExportResult.Success -> {
                toast(
                  if (result.truncated) "诊断日志已生成（文件过大，已截取）"
                  else "诊断日志已生成"
                )
                if (shareLauncher.isSupported) {
                  shareLauncher.launch(result.filePath)
                } else {
                  toast("当前平台不支持系统分享，文件位置：${result.filePath}")
                }
              }
              LogExportResult.Unsupported -> toast("当前平台不支持诊断日志导出")
              is LogExportResult.Failure -> toast(result.message)
            }
          } catch (throwable: CancellationException) {
            throw throwable
          } catch (throwable: Throwable) {
            toast(throwable.message ?: "诊断日志导出失败")
          } finally {
            exporting = false
          }
        }
      }
    }
  }
}

@Composable
internal fun rememberDiagnosticLogClearAction(): () -> Unit {
  val coroutineScope = rememberCoroutineScope()
  var clearing by remember { mutableStateOf(false) }
  return remember(coroutineScope) {
    {
      if (!clearing) {
        clearing = true
        coroutineScope.launch {
          try {
            when (val result = AppLogManager.clearDiagnosticLogs()) {
              LogStorageResult.Success -> toast("诊断日志已清除")
              LogStorageResult.Unsupported -> toast("当前平台不支持清理诊断日志")
              is LogStorageResult.Failure -> toast(result.message)
            }
          } catch (throwable: CancellationException) {
            throw throwable
          } catch (throwable: Throwable) {
            toast(throwable.message ?: "诊断日志清理失败")
          } finally {
            clearing = false
          }
        }
      }
    }
  }
}
