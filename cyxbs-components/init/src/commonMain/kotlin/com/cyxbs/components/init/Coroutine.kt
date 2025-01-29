package com.cyxbs.components.init

import kotlinx.coroutines.CoroutineScope

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/28
 */

/**
 * 应用级别的协程作用域
 * - 该作用域必须是 SupervisorJob，防止协程异常的传播
 */
expect val appCoroutineScope: CoroutineScope


