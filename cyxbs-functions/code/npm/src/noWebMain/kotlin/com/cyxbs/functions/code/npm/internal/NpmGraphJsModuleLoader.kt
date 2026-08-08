package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsModuleNormalizer
import com.cyxbs.functions.code.npm.module.NpmModuleGraph

/**
 * 优先从已经完整准备的 npm Module 图解析源码，无法处理时再委托业务 Loader。
 *
 * 两层 Loader 都必须只访问同步内存，避免 JavaScript 引擎持锁回调期间发生阻塞或重入。
 */
internal class NpmGraphJsModuleLoader(
  private val graph: NpmModuleGraph,
  private val fallback: JsModuleLoader?,
) : JsModuleLoader {

  override val normalizer = JsModuleNormalizer { baseName, requestedName ->
    graph.normalizeOrNull(baseName, requestedName)
      ?: fallback?.normalizer?.normalize(baseName, requestedName)
      ?: requestedName
  }

  /** 返回规范化名称对应的 npm 源码，未命中时交给调用方原有 Loader。 */
  override fun load(name: String): String? {
    return graph.load(name) ?: fallback?.load(name)
  }
}
