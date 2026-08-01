package com.cyxbs.functions.code.js

/**
 * 业务层向单个 [QuickJsRuntime] 提供的 ES Module 内容。
 *
 * 字节码缓存必须与当前 QuickJS 版本和 Module 名称兼容；缓存不可用时应返回 [Source]，
 * 由端上重新编译并通过 [JsModuleLoader.onCompiled] 回收新字节码。
 */
sealed interface JsModuleContent {

  /**
   * 尚未编译的完整 ES Module 源码。
   *
   * @param code Module 源码，运行时会使用请求到的标准化名称进行编译。
   */
  class Source(val code: String) : JsModuleContent

  /**
   * 已由兼容 QuickJS 版本编译的 ES Module 字节码。
   *
   * @param bytes 字节码中记录的 Module 名称必须与本次加载请求一致。
   */
  class Bytecode(val bytes: ByteArray) : JsModuleContent
}

/**
 * 为单个 [QuickJsRuntime] 按需加载 ES Module，并接收源码编译后的字节码。
 *
 * [load] 与 [onCompiled] 都在 QuickJS 解析 Module 时同步调用，必须快速返回且不得重入同一个
 * [QuickJsRuntime]。需要写磁盘或数据库时，调用方应在 [onCompiled] 中仅复制或投递数据，
 * 再由自身的异步队列完成持久化；回调异常不会被封装层捕获，会直接终止当前操作。
 */
fun interface JsModuleLoader {

  /**
   * 加载 QuickJS 请求的标准化 Module 名称。
   *
   * @param name 标准化后的 Module 名称。
   * @return 有效缓存字节码、回退源码，或在模块不存在时返回 `null`。
   */
  fun load(name: String): JsModuleContent?

  /**
   * 接收刚由源码编译出的单个 Module 字节码。
   *
   * 已成功通知的字节码即使后续依赖解析失败仍然有效；QuickJS 不会在回调结束后继续持有该数组。
   *
   * @param name 标准化后的 Module 名称。
   * @param bytecode 新编译的 Module 字节码。
   */
  fun onCompiled(name: String, bytecode: ByteArray) = Unit
}
