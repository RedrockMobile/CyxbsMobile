package com.cyxbs.functions.code.editor.workbench.internal

import androidx.compose.runtime.Composable

/**
 * 在编辑器工作台显示期间应用平台系统栏样式。
 *
 * Android 会让系统导航栏透明地覆盖在工作台背景上，并在离开工作台时恢复原状态；其他平台
 * 不需要处理系统导航栏，因此使用空实现。
 */
@Composable
internal expect fun CodeEditorSystemBarsEffect()
