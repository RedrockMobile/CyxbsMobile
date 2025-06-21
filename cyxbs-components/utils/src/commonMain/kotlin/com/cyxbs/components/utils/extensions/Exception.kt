package com.cyxbs.components.utils.extensions

import kotlin.reflect.KClass

/**
 * 配合 Rxjava 和 Flow 使用 DSL 处理异常的工具类
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/28 14:31
 */

fun showExceptionDialog(throwable: Throwable) {
  // todo 待实现
  toast(throwable.message)
}


open class ExceptionResult<Emitter>(
  val throwable: Throwable,
  val emitter: Emitter
) {

  /**
   * 处理 [T] 类型的异常
   * ```
   * NullPointerException::class.catch {
   *     val exception = it // 闭包里面的 it 就是 NullPointerException
   *     val emitter = this // 闭包里面的 this 是发射器，用于重新发送值给下游
   * }
   * ```
   */
  inline infix fun <reified T : Throwable> KClass<T>.catch(
    action: Emitter.(T) -> Unit
  ): KClass<T> {
    if (throwable is T) {
      action.invoke(emitter, throwable)
    }
    return this
  }

  /**
   * 抓取除了 [T] 以外的其他异常
   */
  inline infix fun <reified T : Throwable> KClass<T>.catchOther(
    action: Emitter.(Throwable) -> Unit
  ): KClass<T> {
    if (throwable !is T) {
      action.invoke(emitter, throwable)
    }
    return this
  }
}