package com.cyxbs.functions.code.js.quickjs.internal

import com.cyxbs.functions.code.js.runtime.JsModuleLoader

/**
 * 单次 QuickJS Runtime 使用的同步 Module 状态。
 *
 * QuickJS 的 Loader 回调发生在引擎锁内，因此这里只访问预读到内存的字节码和调用方已经准备好的
 * 源码 Loader。磁盘读取与写入必须由外层缓存 Runtime 在进入或离开引擎调用前完成。
 */
internal class JsModuleCacheState(
  private val sourceLoader: JsModuleLoader?,
) : QuickJsModuleLoader {

  private val bytecodes = mutableMapOf<String, ByteArray>()
  private val seededCacheNames = mutableSetOf<String>()
  private val usedCacheNames = linkedSetOf<String>()
  private val loadedNames = linkedSetOf<String>()
  private val compiled = linkedMapOf<String, ByteArray>()

  /** 在执行前放入已经通过缓存键和完整性校验的 Module 字节码。 */
  fun seedCachedBytecode(name: String, bytecode: ByteArray) {
    bytecodes[name] = bytecode.copyOf()
    seededCacheNames += name
  }

  override fun load(name: String): QuickJsModuleContent? {
    val source = sourceLoader?.load(name) ?: return null
    loadedNames += name
    val bytecode = bytecodes[name]
    if (bytecode != null) {
      if (name in seededCacheNames) usedCacheNames += name
      return QuickJsModuleContent.Bytecode(bytecode)
    }
    return QuickJsModuleContent.Source(source)
  }

  override fun onCompiled(name: String, bytecode: ByteArray) {
    val copied = bytecode.copyOf()
    loadedNames += name
    bytecodes[name] = copied
    seededCacheNames -= name
    compiled[name] = copied
  }

  /** 返回本次确实交给 QuickJS 使用过的持久化缓存 Module。 */
  fun usedCachedModuleNames(): Set<String> = usedCacheNames.toSet()

  /** 返回静态或动态解析期间实际到达的 Module 名称。 */
  fun loadedModuleNames(): Set<String> = loadedNames.toSet()

  /** 返回本次由源码新编译的依赖 Module 字节码快照。 */
  fun compiledBytecodes(): Map<String, ByteArray> {
    return compiled.mapValues { (_, bytecode) -> bytecode.copyOf() }
  }
}
