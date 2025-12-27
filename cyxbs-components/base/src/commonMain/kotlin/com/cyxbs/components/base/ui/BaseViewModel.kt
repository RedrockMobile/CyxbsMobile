package com.cyxbs.components.base.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.utils.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * commonMain 下的 BaseViewModel，用于写一些多平台的通用逻辑
 *
 * @author 985892345
 * @date 2024/12/31
 */
expect abstract class BaseViewModel() : CommonBaseViewModel

abstract class CommonBaseViewModel : ViewModel(), ToastUtils {

  /**
   * 开启协程并收集 Flow
   */
  protected fun <T> Flow<T>.collectLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    action: suspend (value: T) -> Unit
  ): Job = launchByViewModelScope(
    context = context,
    start = start,
  ) {
    collect{ action.invoke(it) }
  }

  /**
   * ViewModel 下开启协程
   */
  protected fun launchByViewModelScope(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
  ): Job = viewModelScope.launch(context, start, block)
}