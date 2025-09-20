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
 *
 * 如果需要账号级别的作用域，推荐使用 IAccountService::class.impl().accountCoroutineScope
 * - 自动在登陆和登出时 cancel 掉协程
 */
expect val appCoroutineScope: CoroutineScope


