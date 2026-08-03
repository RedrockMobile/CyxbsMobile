package com.cyxbs.functions.code.js.runtime

/**
 * 为单个 [JsRuntime] 按需提供 ES Module 源码。
 *
 * [load] 可能在引擎持锁期间同步调用，必须快速返回且不得重入同一个 Runtime。业务只提供源码，
 * 编译产物和缓存协议完全由具体引擎实现管理。
 */
fun interface JsModuleLoader {

  /**
   * 可选的 Module 名称解析器；未设置时由具体 JavaScript 引擎使用默认解析规则。
   */
  val normalizer: JsModuleNormalizer?
    get() = null

  /**
   * 加载引擎请求的标准化 Module 名称。
   *
   * @param name 标准化后的 Module 名称。
   * @return 完整 Module 源码，或在模块不存在时返回 `null`。
   */
  fun load(name: String): String?
}

/**
 * 将 import 中的请求名称解析为当前 Runtime 内唯一的标准 Module 名称。
 *
 * 回调可能在引擎持锁期间同步执行，必须快速返回且不得重入同一个 Runtime。
 */
fun interface JsModuleNormalizer {

  /**
   * @param baseName 发起 import 的 Module 标准名称。
   * @param requestedName import 语句中声明的请求名称。
   * @return 交给 [JsModuleLoader.load] 的标准 Module 名称。
   */
  fun normalize(baseName: String, requestedName: String): String
}
