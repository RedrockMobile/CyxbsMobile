package com.cyxbs.pages.map.util

import androidx.compose.runtime.Composable

/**
 * @Desc : 类的描述
 * @Author : zzx
 * @Date : 2025/12/2 17:08
 */

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)