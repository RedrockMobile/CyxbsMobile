package com.cyxbs.functions.code.js

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ByteString.Companion.toByteString

/**
 * 本次运行实际使用的可执行产物来源。
 */
enum class JsExecutableOrigin {
  /** 缓存未命中，在本机由源码编译得到。 */
  COMPILED_SOURCE,

  /** 命中与当前引擎、策略和 Bundle 完全匹配的本地字节码。 */
  BYTECODE_CACHE,

  /**
   * 当前程序形态不适合安全复用字节码，直接执行源码。
   *
   * QuickJS-kt 1.0.5 的 Module 字节码加载存在原生崩溃风险，因此多模块程序暂时走该路径。
   */
  SOURCE_DIRECT,
}

/**
 * JavaScript 执行结果。
 *
 * @param value 映射到 Kotlin 的返回值。
 * @param origin 本次使用源码编译还是字节码缓存。
 * @param engineVersion 实际执行使用的 QuickJS 版本。
 * @param reference 源码包引用。
 */
data class JsExecutionResult<T>(
  val value: T,
  val origin: JsExecutableOrigin,
  val engineVersion: String,
  val reference: JsProgramRef,
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
   */
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
   */
  suspend fun uninstall(reference: JsProgramRef) {
    sourceStore.removeSource(reference)
  }

  /**
   * 清空全部本地字节码缓存。
   *
   * 源码包不会被删除；下一次执行会按当前 QuickJS 和 Bundle 重新编译。
   */
  suspend fun clearBytecodeCache() {
    bytecodeCache.clearBytecode()
  }

  /**
   * 加载、校验并执行已安装源码包。
   *
   * 缓存命中时直接执行字节码；未命中时在端上编译入口源码、原子写入缓存后执行。普通 JS
   * 运行异常不会自动回退源码，以免已产生宿主副作用的脚本被重复执行。
   *
   * @param reference 已安装源码包引用。
   * @param environment 当前业务场景和能力 Bundle。
   * @return 执行值及缓存命中信息。
   */
  suspend inline fun <reified T> execute(
    reference: JsProgramRef,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<T> {
    return executeInternal(reference = reference, environment = environment) { runtime, executable ->
      when (executable) {
        is PreparedJsExecutable.Bytecode -> runtime.evaluate<T>(executable.value)
        is PreparedJsExecutable.Source -> runtime.evaluate<T>(
          code = executable.code,
          filename = executable.filename,
          asModule = executable.asModule,
        )
      }
    }
  }

  /**
   * 安装源码包后立即执行。
   */
  suspend inline fun <reified T> installAndExecute(
    sourcePackage: JsSourcePackage,
    environment: JsExecutionEnvironment,
  ): JsExecutionResult<T> {
    val reference = install(sourcePackage = sourcePackage, environment = environment)
    return execute(reference = reference, environment = environment)
  }

  /**
   * 执行公共流程，并把最终字节码交给保留 reified 类型信息的调用点。
   */
  @PublishedApi
  internal suspend fun <T> executeInternal(
    reference: JsProgramRef,
    environment: JsExecutionEnvironment,
    evaluator: suspend (runtime: QuickJsRuntime, executable: PreparedJsExecutable) -> T,
  ): JsExecutionResult<T> {
    return withContext(executionDispatcher) {
      val sourcePackage = sourceStore.readSource(reference)
        ?: throw JsProgramNotFoundException(reference)
      environment.policy.validate(sourcePackage = sourcePackage, bundle = environment.bundle)
      environment.sourceVerifier.verify(sourcePackage)

      val runtime = QuickJsRuntime(config = environment.policy.runtimeConfig)
      try {
        environment.bundle.install(runtime)
        sourcePackage.files.forEach { (name, code) ->
          if (name != sourcePackage.entry) {
            runtime.addModule(name = name, code = code)
          }
        }

        val origin: JsExecutableOrigin
        val executable: PreparedJsExecutable
        if (!supportsBytecodeCache(sourcePackage)) {
          origin = JsExecutableOrigin.SOURCE_DIRECT
          executable = PreparedJsExecutable.Source(
            code = sourcePackage.entrySource(),
            filename = sourcePackage.entry,
            asModule = sourcePackage.mode == JsProgramMode.MODULE,
          )
        } else {
          val cacheKey = createCacheKey(
            sourcePackage = sourcePackage,
            environment = environment,
            engineVersion = runtime.engineVersion,
          )
          val cachedBytecode = readBytecodeSafely(cacheKey)
          if (cachedBytecode != null) {
            origin = JsExecutableOrigin.BYTECODE_CACHE
            executable = PreparedJsExecutable.Bytecode(cachedBytecode)
          } else {
            origin = JsExecutableOrigin.COMPILED_SOURCE
            val bytecode = runtime.compile(
              code = sourcePackage.entrySource(),
              filename = sourcePackage.entry,
              asModule = false,
            )
            writeBytecodeSafely(key = cacheKey, bytecode = bytecode)
            executable = PreparedJsExecutable.Bytecode(bytecode)
          }
        }

        JsExecutionResult(
          value = evaluator(runtime, executable),
          origin = origin,
          engineVersion = runtime.engineVersion,
          reference = reference,
        )
      } finally {
        runtime.close()
      }
    }
  }

  /**
   * 缓存读取失败时退化为未命中，但协程取消必须继续向上传播。
   */
  internal suspend fun readBytecodeSafely(key: JsBytecodeCacheKey): ByteArray? {
    return try {
      bytecodeCache.readBytecode(key)
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      cacheErrorHandler.onError(key, throwable)
      null
    }
  }

  /**
   * 缓存写入失败不影响本次执行，但协程取消必须继续向上传播。
   */
  internal suspend fun writeBytecodeSafely(
    key: JsBytecodeCacheKey,
    bytecode: ByteArray,
  ) {
    try {
      bytecodeCache.writeBytecode(key = key, bytecode = bytecode)
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      cacheErrorHandler.onError(key, throwable)
    }
  }

  companion object {
    private const val BYTECODE_CACHE_FORMAT_VERSION = 1
    private const val RUNTIME_ADAPTER_VERSION = 1

    /**
     * 根据源码、引擎和宿主环境生成字节码缓存键。
     *
     * QuickJS 或 Bundle 升级后键会自然变化，旧字节码不会进入新 Runtime。
     */
    internal fun createCacheKey(
      sourcePackage: JsSourcePackage,
      environment: JsExecutionEnvironment,
      engineVersion: String,
    ): JsBytecodeCacheKey {
      val buffer = Buffer()
      buffer.writeInt(BYTECODE_CACHE_FORMAT_VERSION)
      buffer.writeInt(RUNTIME_ADAPTER_VERSION)
      buffer.writeStableString(sourcePackage.contentHash)
      buffer.writeStableString(sourcePackage.entry)
      buffer.writeStableString(sourcePackage.mode.name)
      buffer.writeStableString(engineVersion)
      buffer.writeStableString(environment.policy.id)
      buffer.writeStableString(environment.bundle.id)
      buffer.writeInt(environment.bundle.version)
      buffer.writeInt(environment.bundle.hostApiVersion)
      val digest = buffer.readByteArray().toByteString().sha256().hex()
      return JsBytecodeCacheKey(digest)
    }

    /**
     * 判断当前程序能否安全使用字节码缓存。
     *
     * QuickJS-kt 1.0.5 在加载 ES Module 字节码时可能发生 Native 崩溃，所以当前只缓存已经
     * 打成单文件的普通脚本。升级 QuickJS-kt 后必须先完成三端回归，才能扩展该条件。
     */
    private fun supportsBytecodeCache(sourcePackage: JsSourcePackage): Boolean {
      return sourcePackage.mode == JsProgramMode.SCRIPT && sourcePackage.files.size == 1
    }

    /**
     * 向缓存键缓冲区写入带长度的 UTF-8 字符串。
     */
    private fun Buffer.writeStableString(value: String) {
      val bytes = value.encodeToByteArray()
      writeInt(bytes.size)
      write(bytes)
    }
  }
}

/**
 * 已完成场景判断、等待交给 QuickJS 执行的程序形态。
 */
@PublishedApi
internal sealed interface PreparedJsExecutable {

  /**
   * 已通过完整性与兼容键校验的 QuickJS 字节码。
   */
  class Bytecode(val value: ByteArray) : PreparedJsExecutable

  /**
   * 不启用字节码缓存时直接执行的源码入口。
   */
  data class Source(
    val code: String,
    val filename: String,
    val asModule: Boolean,
  ) : PreparedJsExecutable
}

/**
 * 请求执行的源码包尚未安装或已经被删除。
 */
class JsProgramNotFoundException(
  val reference: JsProgramRef,
) : IllegalStateException(
  "JavaScript source package '${reference.packageId}:${reference.version}' is not installed.",
)
