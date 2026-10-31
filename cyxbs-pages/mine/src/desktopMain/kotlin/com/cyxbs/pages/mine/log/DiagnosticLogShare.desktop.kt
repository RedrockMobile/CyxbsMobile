package com.cyxbs.pages.mine.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.File

@Composable
internal actual fun rememberDiagnosticLogFileShareLauncher(
  onFailure: (message: String) -> Unit,
): DiagnosticLogFileShareLauncher = remember(onFailure) {
  val supported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
  DiagnosticLogFileShareLauncher(isSupported = supported) { filePath ->
    runCatching {
      Desktop.getDesktop().open(File(filePath).parentFile)
    }.onFailure { throwable ->
      onFailure(throwable.message ?: "无法打开诊断日志目录")
    }
  }
}
