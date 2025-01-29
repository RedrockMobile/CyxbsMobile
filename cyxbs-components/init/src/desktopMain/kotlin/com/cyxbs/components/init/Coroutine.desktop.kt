package com.cyxbs.components.init

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/28
 */
actual val appCoroutineScope: CoroutineScope
  get() = appCoroutineScopeInternal

private lateinit var appCoroutineScopeInternal: CoroutineScope

fun runApp(block: suspend CoroutineScope.() -> Unit) {
  runBlocking {
    // appCoroutineScopeInternal 使用 SupervisorJob 避免异常传播
    val supervisor = SupervisorJob(coroutineContext[Job])
    val coroutineScope = CoroutineScope(supervisor)
    appCoroutineScopeInternal = coroutineScope
    block()
  }
  exitProcess(0)
}