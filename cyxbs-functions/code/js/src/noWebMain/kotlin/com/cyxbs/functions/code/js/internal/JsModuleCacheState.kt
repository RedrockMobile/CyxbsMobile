package com.cyxbs.functions.code.js.internal

import com.cyxbs.functions.code.js.runtime.JsModuleContent
import com.cyxbs.functions.code.js.runtime.JsModuleLoader

/**
 * 单个 ES Module 的稳定名称与源码。
 *
 * @param name QuickJS Module Loader 使用的标准化名称。
 * @param source 当前安装版本对应的源码。
 * @param ownerId 用于隔离业务源码包与共享 Bundle 的稳定所有者标识。
 */
internal data class JsModuleDefinition(
  val name: String,
  val source: String,
  val ownerId: String,
)

/**
 * 单次 QuickJS Runtime 使用的同步 Module 缓存状态。
 *
 * QuickJS 的 [JsModuleLoader.load] 和 [JsModuleLoader.onCompiled] 均在引擎持锁期间同步调用，
 * 因此这里只访问内存。持久化缓存由 [compiledBytecodes] 返回给 Runtime 外层，在本批执行结束并
 * 释放引擎锁后完成。
 *
 * @param definitions 当前 Runtime 可以按需加载的依赖 Module；入口 Module 由调用方单独管理。
 */
internal class JsModuleCacheState(
  definitions: Map<String, JsModuleDefinition>,
) : JsModuleLoader {

  private val definitions = definitions.toMap()
  private val bytecodes = mutableMapOf<String, ByteArray>()
  private val seededCacheNames = mutableSetOf<String>()
  private val usedCacheNames = linkedSetOf<String>()
  private val compiled = linkedMapOf<String, ByteArray>()

  /**
   * 在执行前放入已通过业务缓存键校验的字节码。
   *
   * Loader 回调本身不能调用 suspend 存储，因此调用方需要在 Runtime 开始工作前完成预读取。
   */
  fun seedCachedBytecode(name: String, bytecode: ByteArray) {
    if (name in definitions) {
      bytecodes[name] = bytecode.copyOf()
      seededCacheNames += name
    }
  }

  override fun load(name: String): JsModuleContent? {
    val definition = definitions[name] ?: return null
    val bytecode = bytecodes[name]
    if (bytecode != null) {
      if (name in seededCacheNames) {
        usedCacheNames += name
      }
      return JsModuleContent.Bytecode(bytecode)
    }
    return JsModuleContent.Source(definition.source)
  }

  override fun onCompiled(name: String, bytecode: ByteArray) {
    val copied = bytecode.copyOf()
    bytecodes[name] = copied
    seededCacheNames -= name
    compiled[name] = copied
  }

  /**
   * 返回本次确实交给 QuickJS 使用过的持久化缓存 Module。
   *
   * 仅预读取但执行路径没有到达的动态 Module 不会出现在结果中。
   */
  fun usedCachedModuleNames(): Set<String> = usedCacheNames.toSet()

  /**
   * 返回本次由源码新编译的依赖 Module 字节码快照。
   */
  fun compiledBytecodes(): Map<String, ByteArray> {
    return compiled.mapValues { (_, bytecode) -> bytecode.copyOf() }
  }

}
