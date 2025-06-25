package com.cyxbs.components.init

import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/28
 */

private val AppCoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable -> }

actual val appCoroutineScope: CoroutineScope =
  CoroutineScope(
    SupervisorJob(appLifecycle.coroutineScope.coroutineContext[Job])
        + AppCoroutineExceptionHandler
  )