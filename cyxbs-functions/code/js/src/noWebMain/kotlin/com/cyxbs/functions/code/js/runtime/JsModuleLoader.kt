package com.cyxbs.functions.code.js.runtime

/**
 * 为单个 [JsRuntime] 按需提供 ES Module 源码。
 *
 * [load] 可能在引擎持锁期间同步调用，必须快速返回且不得重入同一个 Runtime。业务只提供源码，
 * 编译产物和缓存协议完全由具体引擎实现管理。
 */
fun interface JsModuleLoader {

  /**
   * 加载引擎请求的标准化 Module 名称。
   *
   * @param name 标准化后的 Module 名称。
   * @return 完整 Module 源码，或在模块不存在时返回 `null`。
   */
  fun load(name: String): String?
}
