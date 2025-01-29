package com.cyxbs.components.init

import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineScope

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/28
 */
actual val appCoroutineScope: CoroutineScope
  get() = appLifecycle.coroutineScope