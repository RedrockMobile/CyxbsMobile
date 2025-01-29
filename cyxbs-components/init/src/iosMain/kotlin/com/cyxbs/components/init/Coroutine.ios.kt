package com.cyxbs.components.init

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * 应用级别的协程作用域
 * - 该作用域必须是 SupervisorJob，防止协程异常的传播
 */
actual val appCoroutineScope: CoroutineScope = CoroutineScope(SupervisorJob())