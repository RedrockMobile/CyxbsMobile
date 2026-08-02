package com.cyxbs.functions.code.js.quickjs.internal

/** QuickJS 内部使用的源码或兼容字节码 Module 内容。 */
internal sealed interface QuickJsModuleContent {

  /** 尚未编译的 Module 源码。 */
  data class Source(val code: String) : QuickJsModuleContent

  /** 当前 QuickJS 版本生成的 Module 字节码。 */
  data class Bytecode(val bytes: ByteArray) : QuickJsModuleContent
}

/**
 * QuickJS 内部 Module Loader，承载字节码复用与编译结果回收。
 *
 * 该接口不会跨出实现模块；回调期间只能操作内存，不执行阻塞持久化。
 */
internal interface QuickJsModuleLoader {

  /** 返回源码、兼容字节码或缺失结果。 */
  fun load(name: String): QuickJsModuleContent?

  /** 接收 QuickJS 刚从源码编译出的单个 Module 字节码。 */
  fun onCompiled(name: String, bytecode: ByteArray) = Unit
}
