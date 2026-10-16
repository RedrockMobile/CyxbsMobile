package com.cyxbs.pages.mine.log

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
internal actual fun rememberDiagnosticLogFileShareLauncher(
  onFailure: (message: String) -> Unit,
): DiagnosticLogFileShareLauncher {
  val context = LocalContext.current
  return remember(context, onFailure) {
    DiagnosticLogFileShareLauncher(isSupported = true) { filePath ->
      runCatching {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileProvider",
          file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
          type = DIAGNOSTIC_LOG_MIME_TYPE
          putExtra(Intent.EXTRA_STREAM, uri)
          clipData = ClipData.newUri(context.contentResolver, "掌上重邮诊断日志", uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
          Intent.createChooser(sendIntent, "分享诊断日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
        )
      }.onFailure { throwable ->
        onFailure(throwable.message ?: "无法打开系统分享面板")
      }
    }
  }
}

private const val DIAGNOSTIC_LOG_MIME_TYPE = "application/x-ndjson"
