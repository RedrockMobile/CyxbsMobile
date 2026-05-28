package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * 处理返回键和 ESC 键
 *
 * 优先使用 [NavigationBackHandler]
 *
 * @author 985892345
 * @date 2025/3/29
 */

@Deprecated("优先使用 androidx.navigationevent.compose.NavigationBackHandler")
@Stable
expect fun Modifier.backHandler(enabled: Boolean = true, onBack: () -> Unit): Modifier