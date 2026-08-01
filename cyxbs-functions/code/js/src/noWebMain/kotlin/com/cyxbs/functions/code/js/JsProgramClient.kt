package com.cyxbs.functions.code.js

import com.cyxbs.functions.code.js.internal.JsProgramExecutor
import com.cyxbs.functions.code.js.runtime.QuickJsRuntime
import com.cyxbs.functions.code.js.storage.JsBytecodeCache
import com.cyxbs.functions.code.js.storage.JsBytecodeCacheKey
import com.cyxbs.functions.code.js.storage.JsSourcePackageStore
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本次运行入口文件实际使用的可执行产物来源。
 *
 * 依赖 Module 可以在同一次执行中混合使用缓存和源码，具体集合由
 * [JsExecutionResult.compiledModules] 与 [JsExecutionResult.cachedModules] 给出。
 */
enum class JsExecutableOrigin {
  /** 缓存未命中，在本机由源码编译得到。 */
  COMPILED_SOURCE,

  /** 入口命中与当前引擎、策略和 Bundle 完全匹配的本地字节码。 */
  BYTECODE_CACHE,
}

/**
 * JavaScript 执行结果。
 *
 * @param value 映射到 Kotlin 的返回值。
 * @param origin 本次入口使用源码编译还是字节码缓存。
 * @param engineVersion 实际执行使用的 QuickJS 版本。
 * @param reference 源码包引用。
 * @param compiledModules 本次按实际依赖路径由源码新编译的入口或依赖 Module 名称。
 * @param cachedModules 本次实际交给 QuickJS 使用的持久化缓存 Module 名称。
 */
data class JsExecutionResult<T>(
  val value: T,
  val origin: JsExecutableOrigin,
  val engineVersion: String,
  val reference: JsProgramRef,
  val compiledModules: Set<String>,
  val cachedModules: Set<String>,
)

/**
 * 监听非致命字节码缓存错误。
 *
 * 缓存读写失败不会阻断源码执行；业务可以通过该回调接入日志或监控。
 */
fun interface JsBytecodeCacheErrorHandler {

  /**
   * 上报缓存 [key] 的读取或写入异常。
   */
  fun onError(key: JsBytecodeCacheKey, throwable: Throwable)
}

/**
 * 统一的 JavaScript 安装、加载、编译缓存与执行入口。
 *
 * 远端或教学编辑器只向该类提供 [JsSourcePackage]。Client 会保存已校验源码，并基于源码哈希、
 * QuickJS 版本、策略和 Bundle 生成缓存键。每次执行使用独立 Runtime，避免业务间共享全局状态。
 *
 * @param sourceStore 不可随普通缓存清理的源码主存储。
 * @param bytecodeCache 可随时删除并由源码重建的字节码缓存。
 * @param cacheErrorHandler 非致命缓存错误监听。
 * @param executionDispatcher 编译和执行 JavaScript 的调度器，默认避免阻塞 UI 线程。
 */
class JsProgramClient(
  internal val sourceStore: JsSourcePackageStore,
  internal val bytecodeCache: JsBytecodeCache,
  internal val cacheErrorHandler: JsBytecodeCacheErrorHandler =
    JsBytecodeCacheErrorHandler { _, _ -> },
  internal val executionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

  /**
   * 校验并安装源码包。
   *
   * 内部场景会调用业务提供的签名校验器；教学场景默认信任本机编辑器生成的源码。策略校验在
   * 写入前完成，避免超限包污染本地存储。
   *
   * [JsSourcePackageVerifier] 与 [JsSourcePackageStore] 实现抛出的其他异常不会被捕获或包装，
   * 调用方应按自身实现处理。
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
   * 删除指定源码包。
   *
   * 字节码键包含多项运行环境信息，无法只凭引用枚举；对应缓存会在业务清理缓存目录时删除。
   * [JsSourcePackageStore] 实现抛出的存储异常会原样透传。
   *
   * @throws CancellationException 删除过程所在协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun uninstall(reference: JsProgramRef) {
    sourceStore.removeSource(reference)
  }

  /**
   * 清空全部本地字节码缓存。
   *
   * 源码包不会被删除；下一次执行会按当前 QuickJS 和 Bundle 重新编译。
   * [JsBytecodeCache] 实现抛出的存储异常会原样透传。
   *
   * @throws CancellationException 清理过程所在协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun clearBytecodeCache() {
    bytecodeCache.clearBytecode()
  }

  /**
   * 加载、校验并执行已安装源码包。
   *
   * 入口和每个依赖 Module 都使用独立缓存键；源码未变化的 Module 可以跨源码包业务版本复用，
   * 变化的 Module 会在实际依赖路径到达时重新编译。静态依赖准备阶段若缓存不可解析，会在尚未
   * 执行顶层代码时使用新 Runtime 回退源码。动态 import 或普通 JS 运行异常不会自动重试，以免
   * 已产生宿主副作用的脚本被重复执行；失败期间首次使用的动态 Module 缓存会被删除，供下次
   * 执行回退源码修复。
   *
   * @param reference 已安装源码包引用。
   * @param environment 当前业务场景和能力 Bundle。
   * @return 执行值、入口来源及本次实际编译和命中的 Module 集合。
   * @throws JsProgramNotFoundException [reference] 对应的源码包尚未安装或已经被删除。
   * @throws JsPolicyViolationException 源码包或 Bundle 不满足当前执行策略。
   * @throws JsSourceVerificationException 源码来源或签名校验失败。
   * @throws QuickJsInterruptedException 执行超时或被 [QuickJsRuntime.interruptEvaluation] 主动中断。
   * @throws QuickJsException QuickJS 初始化、编译、Module 加载或执行失败。
   * @throws CancellationException 执行过程所在协程被取消。
   *
   * [JsSourcePackageStore]、[JsSourcePackageVerifier] 和 [JsBytecodeCacheErrorHandler] 实现抛出的
   * 其他异常不会被捕获或包装。字节码缓存自身的普通读写异常只会通知
   * [JsBytecodeCacheErrorHandler]，不会阻断源码执行。
   */
  @Throws(
    JsProgramNotFoundException::class,
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    QuickJsException::class,
    CancellationException::class,
  )
  suspend inline fun <reified T> execute(
    reference: JsProgramRef,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<T> {
    return executeInternal(
      reference = reference,
      environment = environment,
    ) { runtime, entryBytecode ->
      runtime.evaluate<T>(entryBytecode)
    }
  }

  /**
   * 安装源码包后立即执行。
   *
   * 异常边界与依次调用 [install]、[execute] 相同；源码存储成功后若执行失败，已安装源码不会
   * 自动回滚。
   *
   * @throws JsProgramNotFoundException 对应的源码包尚未安装或已经被删除。
   * @throws JsPolicyViolationException 源码包或 Bundle 不满足当前执行策略。
   * @throws JsSourceVerificationException 源码来源或签名校验失败。
   * @throws QuickJsInterruptedException 执行超时或被 [QuickJsRuntime.interruptEvaluation] 主动中断。
   * @throws QuickJsException QuickJS 初始化、编译、Module 加载或执行失败。
   * @throws CancellationException 安装或执行过程所在协程被取消。
   */
  @Throws(
    JsProgramNotFoundException::class,
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    QuickJsException::class,
    CancellationException::class,
  )
  suspend inline fun <reified T> installAndExecute(
    sourcePackage: JsSourcePackage,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<T> {
    val reference = install(sourcePackage = sourcePackage, environment = environment)
    return execute(reference = reference, environment = environment)
  }

  /**
   * 执行公共流程，并把最终可执行产物交给保留 reified 类型信息的调用点。
   */
  @PublishedApi
  internal suspend fun <T> executeInternal(
    reference: JsProgramRef,
    environment: JsExecutionEnvironment,
    evaluator: suspend (runtime: QuickJsRuntime, entryBytecode: ByteArray) -> T,
  ): JsExecutionResult<T> {
    return withContext(executionDispatcher) {
      val sourcePackage = sourceStore.readSource(reference)
        ?: throw JsProgramNotFoundException(reference)
      environment.policy.validate(sourcePackage = sourcePackage, bundle = environment.bundle)
      environment.sourceVerifier.verify(sourcePackage)

      JsProgramExecutor(
        bytecodeCache = bytecodeCache,
        cacheErrorHandler = cacheErrorHandler,
      ).execute(
        sourcePackage = sourcePackage,
        environment = environment,
        evaluator = evaluator,
      )
    }
  }

}

/**
 * 请求执行的源码包尚未安装或已经被删除。
 */
class JsProgramNotFoundException(
  val reference: JsProgramRef,
) : IllegalStateException(
  "JavaScript source package '${reference.packageId}:${reference.version}' is not installed.",
)
