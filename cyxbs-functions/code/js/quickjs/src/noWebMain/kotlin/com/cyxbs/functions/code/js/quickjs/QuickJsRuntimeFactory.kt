package com.cyxbs.functions.code.js.quickjs

import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.js.quickjs.internal.QuickJsCachingRuntime
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 基于 quickjs-kt 的 JavaScript Runtime 工厂。
 *
 * 当 [JsRuntimeOptions.allowBytecodeCache] 为 `true` 时，会在 `quickjs` 模块固定的缓存目录中
 * 透明复用 Module 字节码；具体 Runtime、缓存格式和 quickjs-kt 类型保持内部可见。
 */
@ImplProvider(
  clazz = JsRuntimeFactory::class,
  name = JsRuntimeFactory.DEFAULT_PROVIDER_NAME,
)
object QuickJsRuntimeFactory : JsRuntimeFactory {

  /**
   * 创建启用内部 Module 字节码缓存和静态依赖预检的隔离 Runtime。
   *
   * @throws JsRuntimeException QuickJS 初始化失败。
   */
  @Throws(JsRuntimeException::class)
  override fun create(options: JsRuntimeOptions): JsRuntime {
    return if (options.allowBytecodeCache) {
      QuickJsCachingRuntime(options)
    } else {
      QuickJsRuntime(options)
    }
  }
}
