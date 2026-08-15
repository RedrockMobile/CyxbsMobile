package com.cyxbs.functions.code.editor.theme

import androidx.compose.ui.unit.dp

/**
 * 编辑器连续面板轮廓共用的圆角半径。
 *
 * 侧边栏连接圆角、文件标签顶部圆角与底部反圆角、代码区左上路径圆角必须使用同一值，
 * 避免相邻区域的曲率不一致。
 */
internal val CodeEditorConnectedCornerRadius = 7.dp
