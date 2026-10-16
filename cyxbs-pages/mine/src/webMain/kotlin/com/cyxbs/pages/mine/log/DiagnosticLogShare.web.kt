package com.cyxbs.pages.mine.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberDiagnosticLogFileShareLauncher(
  onFailure: (message: String) -> Unit,
): DiagnosticLogFileShareLauncher = remember {
  DiagnosticLogFileShareLauncher(isSupported = false, launch = {})
}
