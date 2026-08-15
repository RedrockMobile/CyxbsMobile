package com.cyxbs.functions.code.editor.workbench.internal

import androidx.compose.runtime.Composable

/** iOS 的系统手势区域由 Compose 安全区处理，无需修改窗口样式。 */
@Composable
internal actual fun CodeEditorSystemBarsEffect() = Unit
