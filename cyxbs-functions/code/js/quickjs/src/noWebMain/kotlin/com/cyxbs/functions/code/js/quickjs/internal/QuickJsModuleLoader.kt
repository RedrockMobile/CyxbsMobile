package com.cyxbs.functions.code.js.quickjs.internal

import com.cyxbs.functions.code.js.runtime.JsModuleNormalizer

/** QuickJS 内部使用的源码或兼容字节码 Module 内容。 */
internal sealed interface QuickJsModuleContent {

  /** 尚未编译的 Module 源码。 */
  data class Source(val code: String) : QuickJsModuleContent

  /** 当前 QuickJS 版本生成的 Module 字节码。 */
  data class Bytecode(val bytes: ByteArray) : QuickJsModuleContent
}

/**
 * QuickJS 内部 Module Loader，承载字节码复用、编译结果回收与失败通知。
 *
 * 该接口不会跨出实现模块；回调期间只能操作内存，不执行阻塞持久化。
 */
internal interface QuickJsModuleLoader {

  /** 可选的 Module 名称解析器；为空时保留 QuickJS 默认解析行为。 */
  val normalizer: JsModuleNormalizer?
    get() = null

  /** 返回源码、兼容字节码或缺失结果。 */
  fun load(name: String): QuickJsModuleContent?

  /** 接收 QuickJS 刚从源码编译出的单个 Module 字节码。 */
  fun onCompiled(name: String, bytecode: ByteArray) = Unit

  /** 记录加载失败的 Module；回调期间不得执行阻塞操作。 */
  fun onLoadFailed(name: String) = Unit
}
