package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.language.api.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.api.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.api.bridge.DynamicLanguageMetadata
import com.cyxbs.functions.code.language.internal.JsRuntimeDynamicLanguageAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 动态语言 Runtime 的资源限制。
 *
 * @param maxSourceBytes 动态语言完整 Module 图允许的最大 UTF-8 字节数。
 * @param memoryLimitBytes 单个语言 Runtime 的内存上限。
 * @param maxStackSizeBytes JavaScript 栈空间上限。
 * @param evaluationTimeoutMillis 初始化或单次分析调用的执行超时。
 */
data class DynamicLanguageRuntimeConfig(
  val maxSourceBytes: Long = 2L * 1024L * 1024L,
  val memoryLimitBytes: Long = 32L * 1024L * 1024L,
  val maxStackSizeBytes: Long = 512L * 1024L,
  val evaluationTimeoutMillis: Long = 2_000L,
) {
  init {
    require(maxSourceBytes > 0) { "maxSourceBytes must be greater than 0." }
    require(memoryLimitBytes > 0) { "memoryLimitBytes must be greater than 0." }
    require(maxStackSizeBytes > 0) { "maxStackSizeBytes must be greater than 0." }
    require(evaluationTimeoutMillis > 0) {
      "evaluationTimeoutMillis must be greater than 0."
    }
  }
}

/**
 * 使用动态下发 JavaScript 包提供高亮与补全的语言适配器。
 *
 * 每个实例持有一个独立且长生命周期的 JavaScript Runtime，以避免每次输入都重新加载解析器。
 * 所有分析调用会自动切到 [create] 指定的调度器并串行执行。动态语言包属于受信任的内部产物，
 * 不能把用户输入直接当作语言包加载；用户源码只会作为 JSON 转义后的参数传给既有语言包。
 *
 * 不再使用实例时必须调用 [close]。调用 [close] 时不得仍有新的分析请求进入；关闭完成后继续
 * 调用任何分析方法都会抛出 [IllegalStateException]。
 */
interface DynamicLanguageAdapter {

  /** 当前已经通过协议校验的动态语言元数据。 */
  val metadata: DynamicLanguageMetadata

  /**
   * 分析完整源码并返回有序高亮区间。
   *
   * @throws DynamicLanguageProtocolException 动态语言包返回越界或格式错误的数据。
   * @throws DynamicLanguageExecutionException 动态语言引擎执行失败或超时。
   * @throws IllegalStateException 适配器已经关闭。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    DynamicLanguageProtocolException::class,
    DynamicLanguageExecutionException::class,
    IllegalStateException::class,
    CancellationException::class,
  )
  suspend fun highlight(source: String): List<DynamicHighlightSpan>

  /**
   * 查询指定光标位置的补全候选。
   *
   * @param source 当前完整源码。
   * @param position 光标的 UTF-16 偏移，必须位于 `0..source.length`。
   * @param explicit 是否由用户主动触发补全。
   * @return 补全区间和候选；没有候选时返回 `null`。
   * @throws IllegalArgumentException [position] 超出源码范围。
   * @throws DynamicLanguageProtocolException 动态语言包返回越界或格式错误的数据。
   * @throws DynamicLanguageExecutionException 动态语言引擎执行失败或超时。
   * @throws IllegalStateException 适配器已经关闭。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    IllegalArgumentException::class,
    DynamicLanguageProtocolException::class,
    DynamicLanguageExecutionException::class,
    IllegalStateException::class,
    CancellationException::class,
  )
  suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean = false,
  ): DynamicCompletionResult?

  /**
   * 串行等待正在执行的分析结束并释放 JavaScript Runtime。
   *
   * @throws CancellationException 关闭过程所在协程被取消，此时调用方仍应再次尝试关闭。
   */
  @Throws(CancellationException::class)
  suspend fun close()

  companion object {
    /** 当前 Kotlin 与动态语言 JavaScript 包之间的桥协议版本。 */
    const val PROTOCOL_VERSION: Int = 1

    /**
     * 加载并校验一个已经完成依赖解析的动态语言 JavaScript 包。
     *
     * 包必须在 `globalThis.CyxbsDynamicLanguage` 暴露协议对象。初始化失败时已创建的 Runtime 会
     * 自动释放；成功后调用方负责最终调用 [DynamicLanguageAdapter.close]。
     *
     * @param runtimeFactory 具体 JavaScript 引擎工厂；本模块不依赖 QuickJS 等实现。
     * @param moduleGraph 已在 Runtime 外完成下载、校验和依赖解析的 ESM 源码图。
     * @param config Runtime 与完整源码图的体积限制。
     * @param dispatcher 加载、分析和关闭 Runtime 使用的调度器。
     * @throws DynamicLanguageProtocolException 包缺少协议入口、版本不兼容或元数据非法。
     * @throws DynamicLanguageExecutionException 动态语言引擎初始化或执行语言包失败。
     * @throws CancellationException 初始化协程被取消。
     */
    @Throws(
      DynamicLanguageProtocolException::class,
      DynamicLanguageExecutionException::class,
      CancellationException::class,
    )
    suspend fun create(
      runtimeFactory: JsRuntimeFactory,
      moduleGraph: DynamicLanguageModuleGraph,
      config: DynamicLanguageRuntimeConfig = DynamicLanguageRuntimeConfig(),
      dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): DynamicLanguageAdapter {
      return JsRuntimeDynamicLanguageAdapter.create(
        runtimeFactory = runtimeFactory,
        moduleGraph = moduleGraph,
        config = config,
        dispatcher = dispatcher,
      )
    }
  }
}

/** 动态语言包不满足桥协议或返回了不可信边界数据。 */
class DynamicLanguageProtocolException(
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * 动态语言引擎初始化或执行失败。
 *
 * 该异常是面向业务的稳定边界，不暴露当前使用的 JavaScript 引擎。底层异常仅保存在 [cause] 中，
 * 调用方可以记录完整异常链用于排查，但不应依赖其具体类型。
 */
class DynamicLanguageExecutionException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
