package com.cyxbs.functions.code.js.runtime

/**
 * 创建 Runtime 时一次性安装的具名宿主桥。
 *
 * 桥只描述一个全局函数或宿主对象，不持有 [JsRuntime]，因此实现无法在安装期间继续注册额外
 * 能力。需要回调 JavaScript 时，应由上层在构造桥时显式传入受控回调，而不是暴露 Runtime。
 *
 * @param name JavaScript 侧可见的稳定全局名称。
 */
abstract class JsRuntimeBridge protected constructor(
  val name: String,
) {
  init {
    require(name.isNotBlank()) { "JavaScript Runtime bridge name must not be blank." }
  }

  /** 当前桥交给引擎安装的受控 binding。 */
  abstract val binding: JsRuntimeBridgeBinding

  /**
   * Runtime 完成全部 binding 安装后提供受控回调。
   *
   * 默认桥无需回调。实现可以保存 [callback] 供之后的异步调用使用，但不得在本方法中同步重入
   * Runtime；回调接口刻意不提供任何注册能力。
   */
  open fun onRuntimeReady(callback: JsRuntimeCallback) = Unit
}

/** 桥实现可使用的最小 Runtime 回调，不包含宿主函数注册能力。 */
fun interface JsRuntimeCallback {
  /** 执行一段 JavaScript；调用方必须避免在当前宿主回调尚未返回时同步重入。 */
  suspend fun evaluateValue(
    code: String,
    filename: String,
    asModule: Boolean,
  ): Any?
}

/**
 * Runtime 引擎允许安装的三类宿主 binding。
 *
 * 该类型只承载回调，不暴露引擎或 Runtime 注册器；新增宿主能力必须先构造 [JsRuntimeBridge]，
 * 再随 [JsRuntimeOptions] 一次性交给 Runtime。
 */
sealed interface JsRuntimeBridgeBinding {
  /** 同步顶层函数；回调必须快速返回且不得同步重入当前 Runtime。 */
  class SyncFunction(
    val block: (args: Array<Any?>) -> Any?,
  ) : JsRuntimeBridgeBinding

  /** 返回 Promise 的异步顶层函数。 */
  class AsyncFunction(
    val block: suspend (args: Array<Any?>) -> Any?,
  ) : JsRuntimeBridgeBinding

  /** 由一组同步函数组成的宿主对象。 */
  class ObjectFunctions(
    functions: Map<String, (args: Array<Any?>) -> Any?>,
  ) : JsRuntimeBridgeBinding {
    val functions: Map<String, (args: Array<Any?>) -> Any?> = functions.toMap()

    init {
      require(this.functions.isNotEmpty()) { "Host object functions must not be empty." }
      require(this.functions.keys.all(String::isNotBlank)) {
        "Host object function names must not be blank."
      }
    }
  }
}

/** 把一个同步回调包装成可复用的具名 Runtime 桥。 */
class JsSyncFunctionBridge(
  name: String,
  block: (args: Array<Any?>) -> Any?,
) : JsRuntimeBridge(name) {
  override val binding: JsRuntimeBridgeBinding = JsRuntimeBridgeBinding.SyncFunction(block)
}

/** 把一个 suspend 回调包装成可复用的具名 Runtime 桥。 */
class JsAsyncFunctionBridge(
  name: String,
  block: suspend (args: Array<Any?>) -> Any?,
) : JsRuntimeBridge(name) {
  override val binding: JsRuntimeBridgeBinding = JsRuntimeBridgeBinding.AsyncFunction(block)
}

/** 把一组同步函数包装成可复用的具名宿主对象桥。 */
class JsObjectFunctionsBridge(
  name: String,
  functions: Map<String, (args: Array<Any?>) -> Any?>,
) : JsRuntimeBridge(name) {
  override val binding: JsRuntimeBridgeBinding = JsRuntimeBridgeBinding.ObjectFunctions(functions)
}
