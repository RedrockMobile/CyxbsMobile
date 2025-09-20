package com.cyxbs.components.utils.extensions

import kotlin.coroutines.cancellation.CancellationException

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */

inline fun <T, R> T.runCatchingCoroutine(block: T.() -> R): Result<R> {
  return try {
    Result.success(block())
  } catch (e: CancellationException) {
    throw e // 协程的取消需要抛出
  } catch (e: Throwable) {
    Result.failure(e)
  }
}

inline fun <R, T> Result<T>.mapCatchingCoroutine(transform: (value: T) -> R): Result<R> {
  return when {
    isSuccess -> runCatchingCoroutine { transform(getOrNull()!!) }
    else -> Result.failure(exceptionOrNull()!!)
  }
}