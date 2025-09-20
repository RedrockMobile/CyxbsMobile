package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * 处理返回键和 ESC 键
 *
 * @author 985892345
 * @date 2025/3/29
 */

@Stable
expect fun Modifier.backHandler(enabled: Boolean = true, onBack: () -> Unit): Modifier