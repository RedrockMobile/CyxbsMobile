package com.cyxbs.functions.code.js.program

import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.convertJsValue
import com.cyxbs.functions.code.js.runtime.create
import com.cyxbs.functions.code.js.storage.JsSourcePackageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JavaScript 程序的一次执行结果。
 *
 * Program 层只返回稳定业务结果，不暴露具体引擎、编译产物或缓存命中情况。
 *
 * @param value 映射到 Kotlin 的返回值。
 * @param reference 本次执行的源码包引用。
 */
data class JsExecutionResult<T>(
  val value: T,
  val reference: JsProgramRef,
)

/**
 * 统一的 JavaScript 源码包安装与执行入口。
 *
 * Program 只负责编排源码、策略、Bundle 和公共 [com.cyxbs.functions.code.js.runtime.JsRuntime]；
 * 编译、依赖预解析与缓存均由 [runtimeFactory] 选择的具体引擎实现管理。
 *
 * @param sourceStore 不可随普通缓存清理的源码主存储。
 * @param runtimeFactory JavaScript Runtime 的具体实现工厂。
 * @param executionDispatcher 加载和执行 JavaScript 的调度器，默认避免阻塞 UI 线程。
 * @param allowBytecodeCache 是否允许 Runtime 实现透明读写字节码缓存。
 */
class JsProgramClient(
  private val sourceStore: JsSourcePackageStore,
  private val runtimeFactory: JsRuntimeFactory,
  private val executionDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val allowBytecodeCache: Boolean = true,
) {

  /**
   * 校验并安装源码包。
   *
   * [JsSourcePackageVerifier] 与 [JsSourcePackageStore] 实现抛出的其他异常不会被捕获或包装。
   *
   * @throws JsPolicyViolationException 源码包或 Bundle 不满足当前执行策略。
   * @throws JsSourceVerificationException 源码来源或签名校验失败。
   * @throws CancellationException 安装过程所在协程被取消。
   */
  @Throws(
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    CancellationException::class,
  )
  suspend fun install(
    sourcePackage: JsSourcePackage,
    environment: JsExecutionEnvironment,
  ): JsProgramRef {
    environment.policy.validate(sourcePackage = sourcePackage, bundle = environment.bundle)
    environment.sourceVerifier.verify(sourcePackage)
    sourceStore.writeSource(sourcePackage)
    return sourcePackage.reference
  }

  /**
   * 删除指定源码包；Runtime 实现自己的缓存不属于 Program 生命周期。
   *
   * @throws CancellationException 删除过程所在协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun uninstall(reference: JsProgramRef) {
    sourceStore.removeSource(reference)
  }

  /**
   * 加载、校验并执行已安装源码包。
   *
   * 每次执行创建独立 Runtime。依赖 Module 由内存中的源码映射同步提供，避免引擎持锁期间访问
   * 磁盘；具体引擎可以在 Runtime 内部透明完成缓存和静态依赖预检。
   *
   * @throws JsProgramNotFoundException [reference] 对应的源码包尚未安装或已经被删除。
   * @throws JsPolicyViolationException 源码包或 Bundle 不满足当前执行策略。
   * @throws JsSourceVerificationException 源码来源或签名校验失败。
   * @throws JsRuntimeException Runtime 初始化、Module 加载或执行失败。
   * @throws CancellationException 执行过程所在协程被取消。
   */
  @Throws(
    JsProgramNotFoundException::class,
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    JsRuntimeException::class,
    CancellationException::class,
  )
  suspend inline fun <reified T> execute(
    reference: JsProgramRef,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<T> {
    val result = executeInternal(reference = reference, environment = environment)
    return JsExecutionResult(
      value = convertJsValue(
        value = result.value,
        expectedType = T::class.simpleName ?: "requested Kotlin type",
      ),
      reference = result.reference,
    )
  }

  /**
   * 安装源码包后立即执行；执行失败不会回滚已经完成的源码安装。
   */
  @Throws(
    JsProgramNotFoundException::class,
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    JsRuntimeException::class,
    CancellationException::class,
  )
  suspend inline fun <reified T> installAndExecute(
    sourcePackage: JsSourcePackage,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<T> {
    val reference = install(sourcePackage = sourcePackage, environment = environment)
    return execute(reference = reference, environment = environment)
  }

  /** 执行公共流程并返回引擎无关基础值，公开 inline 方法只负责最终类型转换。 */
  @PublishedApi
  internal suspend fun executeInternal(
    reference: JsProgramRef,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<Any?> = withContext(executionDispatcher) {
    val sourcePackage = sourceStore.readSource(reference)
      ?: throw JsProgramNotFoundException(reference)
    environment.policy.validate(sourcePackage = sourcePackage, bundle = environment.bundle)
    environment.sourceVerifier.verify(sourcePackage)

    val moduleSources = buildMap {
      putAll(environment.bundle.modules)
      sourcePackage.files.forEach { (name, source) ->
        if (name != sourcePackage.entry) put(name, source)
      }
    }
    val runtime = runtimeFactory.create(
      jobDispatcher = executionDispatcher,
      config = environment.policy.runtimeConfig,
      moduleLoader = JsModuleLoader(moduleSources::get),
      allowBytecodeCache = allowBytecodeCache,
      bridges = environment.bundle.capabilities.flatMap { it.runtimeBridges },
    )
    try {
      JsExecutionResult(
        value = runtime.evaluateValue(
          code = sourcePackage.entrySource(),
          filename = sourcePackage.entry,
          asModule = sourcePackage.mode == JsProgramMode.MODULE,
        ),
        reference = sourcePackage.reference,
      )
    } finally {
      runtime.close()
    }
  }
}

/** 请求执行的源码包尚未安装或已经被删除。 */
class JsProgramNotFoundException(
  val reference: JsProgramRef,
) : IllegalStateException(
  "JavaScript source package '${reference.packageId}:${reference.version}' is not installed.",
)
