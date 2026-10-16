package com.cyxbs.pages.mine.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberDiagnosticLogFileShareLauncher(
  onFailure: (message: String) -> Unit,
): DiagnosticLogFileShareLauncher = remember {
  DiagnosticLogFileShareLauncher(isSupported = false, launch = {})
}
