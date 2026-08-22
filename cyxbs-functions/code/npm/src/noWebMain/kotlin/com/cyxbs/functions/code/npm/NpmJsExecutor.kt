package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.npm.internal.NpmGraphJsModuleLoader
import com.cyxbs.functions.code.npm.model.NpmEntryRequest
import com.cyxbs.functions.code.npm.model.NpmException
import com.cyxbs.functions.code.npm.model.NpmRefreshPolicy
import com.cyxbs.functions.code.npm.module.NpmModuleGraph
import com.cyxbs.functions.code.npm.module.NpmModuleGraphFactory
import com.cyxbs.functions.code.npm.pool.NpmPackagePool
import kotlinx.coroutines.CancellationException

/**
 * 将 npm 依赖准备、Module Loader 安装和 JavaScript Runtime 生命周期整合为统一执行入口。
 *
 * 执行顺序固定为：
 *
 * ```
 * 刷新/复用依赖图 -> 下载并校验完整闭包 -> 构建内存 Module 图
 * -> 创建带 Loader 与预声明桥的 Runtime -> 执行业务代码 -> 关闭 Runtime -> 释放入口租约
 * ```
 *
 * Module Loader 必须在 [JsRuntimeFactory.create] 时安装，因此本类接收 Runtime 工厂，而不能接收已经
 * 创建完成的 [JsRuntime]。[withRuntime] 会把配置完整的 Runtime 交给业务，适合宿主桥和
 * DynamicLanguage 等需要自定义执行协议的场景。
 *
 * @param packagePool App 级长生命周期 npm 全局包池；默认使用 [NpmPackagePool.Default]。
 * @param moduleGraphFactory 将已准备归档转换为只读 Module 图的工厂。
 */
class NpmJsExecutor(
  private val packagePool: NpmPackagePool = NpmPackagePool.Default,
  private val moduleGraphFactory: NpmModuleGraphFactory = NpmModuleGraphFactory(),
) {

  /**
   * 在依赖图对应的 Runtime 中执行调用方逻辑，并自动关闭 Runtime、释放入口租约。
   *
   * @param request npm 入口包、版本策略和可选入口 Module。
   * @param runtimeFactory JavaScript 引擎工厂。
   * @param runtimeOptions Runtime 配置；已有业务 Module Loader 会作为 npm Loader 的后备实现。
   * @param refreshPolicy `AUTO` 每个包池实例首次检查 Latest 且失败可回退；`FORCE` 本次必须刷新
   * 成功才创建 Runtime。完整语义见 [NpmRefreshPolicy]。
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
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
    block: suspend (runtime: JsRuntime, entryModuleName: String) -> T,
  ): T {
    return withRuntimeAndGraph(
      request,
      runtimeFactory,
      runtimeOptions,
      refreshPolicy,
    ) { runtime, graph ->
      block(runtime, graph.entryModuleName)
    }
  }

  /**
   * 执行 npm 包声明的入口 Module，并返回引擎无关的 Kotlin 基础值。
   *
   * @param refreshPolicy 本次执行的远端刷新策略，具体差异见 [NpmRefreshPolicy]。
   * @throws NpmException 依赖准备或 Module 图构建失败。
   * @throws JsRuntimeException Runtime 配置、入口编译、执行或关闭失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmException::class, JsRuntimeException::class, CancellationException::class)
  suspend fun executeEntryValue(
    request: NpmEntryRequest,
    runtimeFactory: JsRuntimeFactory,
    runtimeOptions: JsRuntimeOptions = JsRuntimeOptions(),
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
  ): Any? {
    return withRuntimeAndGraph(
      request,
      runtimeFactory,
      runtimeOptions,
      refreshPolicy,
    ) { runtime, graph ->
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
   * @param refreshPolicy 本次执行的远端刷新策略，具体差异见 [NpmRefreshPolicy]。
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
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
  ): Any? {
    return withRuntimeAndGraph(
      request,
      runtimeFactory,
      runtimeOptions,
      refreshPolicy,
    ) { runtime, _ ->
      runtime.evaluateValue(code = code, filename = filename, asModule = true)
    }
  }

  /** 在入口租约内构建图、创建 Runtime，并以严格的 finally 顺序释放两层资源。 */
  private suspend fun <T> withRuntimeAndGraph(
    request: NpmEntryRequest,
    runtimeFactory: JsRuntimeFactory,
    runtimeOptions: JsRuntimeOptions,
    refreshPolicy: NpmRefreshPolicy,
    block: suspend (JsRuntime, NpmModuleGraph) -> T,
  ): T {
    return packagePool.withEntry(request, refreshPolicy) { preparedEntry ->
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
