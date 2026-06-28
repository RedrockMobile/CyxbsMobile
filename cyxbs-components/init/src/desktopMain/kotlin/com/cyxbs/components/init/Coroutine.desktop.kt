package com.cyxbs.components.init

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

private val AppCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable -> }

actual val appCoroutineScope: CoroutineScope
  // runApp 设置真实作用域；未调用 runApp 时（如单测）回退到 [defaultAppCoroutineScope]，
  // 避免直接访问未初始化的 lateinit 抛 UninitializedPropertyAccessException。
  get() = appCoroutineScopeInternal ?: defaultAppCoroutineScope

private var appCoroutineScopeInternal: CoroutineScope? = null

/**
 * runApp 未调用时的兜底应用作用域，遵循 Main.immediate 契约。
 * 单测可用 `Dispatchers.setMain(...)` 控制其调度（desktop 单测据此即可测试依赖 [appCoroutineScope] 的代码）。
 */
private val defaultAppCoroutineScope: CoroutineScope by lazy {
  CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + AppCoroutineExceptionHandler)
}

fun runApp(block: suspend CoroutineScope.() -> Unit) {
  runBlocking {
    // appCoroutineScopeInternal 使用 SupervisorJob 避免异常传播
    val supervisor = SupervisorJob(coroutineContext[Job])
    val coroutineScope = CoroutineScope(supervisor + Dispatchers.Main.immediate + AppCoroutineExceptionHandler)
    appCoroutineScopeInternal = coroutineScope
    block()
  }
  exitProcess(0)
}