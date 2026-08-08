package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsModuleNormalizer
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import kotlinx.coroutines.CancellationException

/**
 * 将 npm 依赖准备、Module Loader 安装和 JavaScript Runtime 生命周期整合为统一执行入口。
 *
 * 执行顺序固定为：
 *
 * ```
 * 刷新/复用依赖图 -> 下载并校验完整闭包 -> 构建内存 Module 图
 * -> 创建带 Loader 的 Runtime -> 交给业务绑定宿主能力并执行 -> 关闭 Runtime -> 释放入口租约
 * ```
 *
 * Module Loader 必须在 [JsRuntimeFactory.create] 时安装，因此本类接收 Runtime 工厂，而不能接收已经
 * 创建完成的 [JsRuntime]。[withRuntime] 会把配置完整的 Runtime 交给业务，适合宿主桥和
 * DynamicLanguage 等需要自定义执行协议的场景。
 */
class NpmJsExecutor(
  private val packagePool: NpmPackagePool,
  private val moduleGraphFactory: NpmModuleGraphFactory = NpmModuleGraphFactory(),
) {

  /**
   * 在依赖图对应的 Runtime 中执行调用方逻辑，并自动关闭 Runtime、释放入口租约。
   *
   * @param request npm 入口包、版本策略和可选入口 Module。
   * @param runtimeFactory JavaScript 引擎工厂。
   * @param runtimeOptions Runtime 配置；已有业务 Module Loader 会作为 npm Loader 的后备实现。
   * @param block 接收已安装 npm Loader 的 Runtime，以及规范化后的入口 Module 名称。
   * @return [block] 返回的业务结果。
   * @throws NpmException 依赖准备或 Module 图构建失败。
   * @throws JsRuntimeException Runtime 创建、业务执行或关闭失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmException::class, JsRuntimeException::class, CancellationException::class)
  suspend fun <T> withRuntime(
    request: NpmEntryRequest,
    runtimeFactory: JsRuntimeFactory,
    runtimeOptions: JsRuntimeOptions = JsRuntimeOptions(),
    block: suspend (runtime: JsRuntime, entryModuleName: String) -> T,
  ): T {
    return withRuntimeAndGraph(request, runtimeFactory, runtimeOptions) { runtime, graph ->
      block(runtime, graph.entryModuleName)
    }
  }

  /**
   * 执行 npm 包声明的入口 Module，并返回引擎无关的 Kotlin 基础值。
   *
   * @param configureRuntime 在入口执行前同步注册宿主函数或对象；不得重入当前 Runtime。
   * @throws NpmException 依赖准备或 Module 图构建失败。
   * @throws JsRuntimeException Runtime 配置、入口编译、执行或关闭失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmException::class, JsRuntimeException::class, CancellationException::class)
  suspend fun executeEntryValue(
    request: NpmEntryRequest,
    runtimeFactory: JsRuntimeFactory,
    runtimeOptions: JsRuntimeOptions = JsRuntimeOptions(),
    configureRuntime: (JsRuntime) -> Unit = {},
  ): Any? {
    return withRuntimeAndGraph(request, runtimeFactory, runtimeOptions) { runtime, graph ->
      configureRuntime(runtime)
      val source = checkNotNull(graph.load(graph.entryModuleName)) {
        "Prepared npm entry Module is missing from its in-memory graph."
      }
      runtime.evaluateValue(
        code = source,
        filename = graph.entryModuleName,
        asModule = true,
      )
    }
  }

  /**
   * 以已准备的 npm 依赖图为 Module Loader，执行调用方提供的 ES Module 源码。
   *
   * 该方法适合测试、胶水入口以及动态业务协议：源码可以按裸包名 import 当前入口依赖图中的包。
   *
   * @param code 调用方提供的 ES Module 源码。
   * @param filename 入口逻辑文件名，用于异常堆栈和相对 import。
   * @param configureRuntime 在源码执行前同步注册宿主能力；不得重入当前 Runtime。
   * @throws NpmException 依赖准备或 Module 图构建失败。
   * @throws JsRuntimeException Runtime 配置、源码编译、执行或关闭失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmException::class, JsRuntimeException::class, CancellationException::class)
  suspend fun executeValue(
    request: NpmEntryRequest,
    runtimeFactory: JsRuntimeFactory,
    code: String,
    filename: String = JsRuntime.DEFAULT_FILENAME,
    runtimeOptions: JsRuntimeOptions = JsRuntimeOptions(),
    configureRuntime: (JsRuntime) -> Unit = {},
  ): Any? {
    return withRuntimeAndGraph(request, runtimeFactory, runtimeOptions) { runtime, _ ->
      configureRuntime(runtime)
      runtime.evaluateValue(code = code, filename = filename, asModule = true)
    }
  }

  /** 在入口租约内构建图、创建 Runtime，并以严格的 finally 顺序释放两层资源。 */
  private suspend fun <T> withRuntimeAndGraph(
    request: NpmEntryRequest,
    runtimeFactory: JsRuntimeFactory,
    runtimeOptions: JsRuntimeOptions,
    block: suspend (JsRuntime, NpmModuleGraph) -> T,
  ): T {
    return packagePool.withEntry(request) { preparedEntry ->
      val graph = moduleGraphFactory.create(preparedEntry)
      val runtime = runtimeFactory.create(
        runtimeOptions.copy(
          moduleLoader = NpmGraphJsModuleLoader(
            graph = graph,
            fallback = runtimeOptions.moduleLoader,
          ),
        ),
      )
      try {
        block(runtime, graph)
      } finally {
        runtime.close()
      }
    }
  }
}

/**
 * 优先解析 npm 图，无法处理时再委托业务 Loader。
 *
 * 两层 Loader 都只访问同步内存，满足 JavaScript 引擎持锁回调不得阻塞或重入 Runtime 的约束。
 */
private class NpmGraphJsModuleLoader(
  private val graph: NpmModuleGraph,
  private val fallback: JsModuleLoader?,
) : JsModuleLoader {

  override val normalizer = JsModuleNormalizer { baseName, requestedName ->
    graph.normalizeOrNull(baseName, requestedName)
      ?: fallback?.normalizer?.normalize(baseName, requestedName)
      ?: requestedName
  }

  override fun load(name: String): String? {
    return graph.load(name) ?: fallback?.load(name)
  }
}
