package com.cyxbs.functions.code.js.runtime

import com.g985892345.provider.manager.KtProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * JavaScript Runtime 工厂。
 *
 * 业务依赖该接口选择具体引擎，公共 `js` 模块不会直接依赖 QuickJS 或其他引擎实现。
 */
fun interface JsRuntimeFactory {

  /**
   * 创建一个相互隔离的 Runtime。
   *
   * @param options 调度器、资源限制与 Module Loader。
   * @return 使用完必须调用 [JsRuntime.close] 的 Runtime。
   * @throws JsRuntimeException Runtime 初始化失败。
   */
  @Throws(JsRuntimeException::class)
  fun create(options: JsRuntimeOptions): JsRuntime

  companion object {

    /** 默认 JavaScript 引擎在 KtProvider 中使用的名称。 */
    const val DEFAULT_PROVIDER_NAME: String = "quickjs"

    /**
     * 从 KtProvider 获取指定 JavaScript 引擎，未安装对应实现时返回 null。
     *
     * 默认选择 QuickJS；调用方只依赖公共 Runtime 接口，具体引擎模块负责注册实现。
     */
    fun implOrNull(name: String = DEFAULT_PROVIDER_NAME): JsRuntimeFactory? {
      return KtProvider.implOrNull(JsRuntimeFactory::class, name)
    }
  }
}

/**
 * 创建 Runtime 的便利重载，避免业务自行组装 [JsRuntimeOptions]。
 *
 * @param allowBytecodeCache 是否允许引擎实现读写字节码缓存；不支持缓存的实现可以安全忽略。
 */
@Throws(JsRuntimeException::class)
fun JsRuntimeFactory.create(
  jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
  config: JsRuntimeConfig = JsRuntimeConfig(),
  moduleLoader: JsModuleLoader? = null,
  allowBytecodeCache: Boolean = true,
  bridges: List<JsRuntimeBridge> = emptyList(),
): JsRuntime = create(
  JsRuntimeOptions(
    jobDispatcher = jobDispatcher,
    config = config,
    moduleLoader = moduleLoader,
    allowBytecodeCache = allowBytecodeCache,
    bridges = bridges,
  ),
)

/**
 * 面向 Android、iOS 与 Desktop 的最小 JavaScript Runtime 契约。
 *
 * Runtime 维护长生命周期的 JavaScript 上下文。实例不得并发调用；使用完必须调用 [close]，
 * 关闭后不可继续执行。宿主能力只能通过 [JsRuntimeOptions.bridges] 在创建时安装，具体引擎、
 * 字节码格式和 native 类型不会出现在该接口中。
 */
interface JsRuntime {

  /**
   * Runtime 是否已经释放。
   *
   * 关闭后的 Runtime 不允许继续执行脚本或注册宿主能力；重复调用 [close] 仍必须安全。
   */
  val isClosed: Boolean

  /**
   * 执行 JavaScript 源码并返回引擎无关的 Kotlin 基础值。
   *
   * @param code 待执行源码。
   * @param filename 用于异常堆栈和相对 import 的逻辑文件名。
   * @param asModule 是否按 ES Module 解析。
   * @return `null`、数字、布尔值、字符串、列表或字符串键 Map 等稳定值。
   * @throws JsRuntimeException 初始化、编译、执行、中断或值映射失败。
   * @throws CancellationException 执行所在协程被取消。
   */
  @Throws(JsRuntimeException::class, CancellationException::class)
  suspend fun evaluateValue(
    code: String,
    filename: String = DEFAULT_FILENAME,
    asModule: Boolean = true,
  ): Any?

  /**
   * 主动中断正在执行的 JavaScript；没有执行任务时调用不产生副作用。
   *
   * @throws JsRuntimeException 引擎无法处理中断请求。
   */
  @Throws(JsRuntimeException::class)
  fun interruptEvaluation()

  /**
   * 释放 Runtime 和未完成任务。重复调用必须安全。
   *
   * @throws JsRuntimeException Runtime 资源释放失败。
   */
  @Throws(JsRuntimeException::class)
  fun close()

  companion object {
    const val DEFAULT_FILENAME: String = "main.js"
  }
}

/**
 * 执行源码并把稳定基础值转换为调用方要求的 [T]。
 */
@Throws(JsRuntimeException::class, CancellationException::class)
suspend inline fun <reified T> JsRuntime.evaluate(
  code: String,
  filename: String = JsRuntime.DEFAULT_FILENAME,
  asModule: Boolean = false,
): T = convertJsValue(
  value = evaluateValue(code = code, filename = filename, asModule = asModule),
  expectedType = T::class.simpleName ?: "requested Kotlin type",
)

/** 在公开 inline 方法中校验基础值类型，不接触任何底层引擎对象。 */
@PublishedApi
internal inline fun <reified T> convertJsValue(value: Any?, expectedType: String): T {
  if (T::class == Unit::class) {
    @Suppress("UNCHECKED_CAST")
    return Unit as T
  }
  if (value is T) return value
  // JavaScript Number 通常以 Double 穿过引擎无关边界，这里恢复调用方请求的基础数值类型。
  if (value is Number) {
    @Suppress("UNCHECKED_CAST")
    return when (T::class) {
      Byte::class -> value.toByte()
      Short::class -> value.toShort()
      Int::class -> value.toInt()
      Long::class -> value.toLong()
      Float::class -> value.toFloat()
      Double::class -> value.toDouble()
      else -> null
    } as? T ?: throw JsRuntimeException(
      kind = JsRuntimeErrorKind.VALUE_CONVERSION_ERROR,
      message = "Cannot convert JavaScript result to $expectedType.",
    )
  }
  throw JsRuntimeException(
    kind = JsRuntimeErrorKind.VALUE_CONVERSION_ERROR,
    message = "Cannot convert JavaScript result to $expectedType.",
  )
}

/**
 * JavaScript 运行时的资源与执行限制。
 *
 * 不同引擎可以在自身能力范围内实现限制；无法支持的限制必须被安全忽略，不能改变脚本语义。
 *
 * @param memoryLimitBytes 单个运行时允许使用的最大内存，非正数表示不限制。
 * @param maxStackSizeBytes JavaScript 栈空间上限，必须大于 0。
 * @param evaluationTimeoutMillis 单次 JavaScript 执行超时；设为 0 表示不限制。
 */
data class JsRuntimeConfig(
  val memoryLimitBytes: Long = 32L * 1024L * 1024L,
  val maxStackSizeBytes: Long = 256L * 1024L,
  val evaluationTimeoutMillis: Long = 5_000L,
) {

  init {
    require(maxStackSizeBytes > 0) { "maxStackSizeBytes must be greater than 0." }
    require(evaluationTimeoutMillis >= 0) {
      "evaluationTimeoutMillis must not be negative."
    }
  }
}

/**
 * 创建单个 JavaScript Runtime 时交给引擎实现的参数。
 *
 * @param jobDispatcher 异步宿主函数使用的协程调度器。
 * @param config 资源和执行限制。
 * @param moduleLoader 当前 Runtime 的 ES Module 加载器。
 * @param allowBytecodeCache 是否允许引擎实现读写字节码缓存，不保证具体实现一定支持。
 * @param bridges 创建时安装的受控宿主桥；名称必须唯一，创建完成后不可追加。
 */
data class JsRuntimeOptions(
  val jobDispatcher: CoroutineDispatcher = Dispatchers.Default,
  val config: JsRuntimeConfig = JsRuntimeConfig(),
  val moduleLoader: JsModuleLoader? = null,
  val allowBytecodeCache: Boolean = true,
  val bridges: List<JsRuntimeBridge> = emptyList(),
) {
  init {
    require(bridges.map(JsRuntimeBridge::name).distinct().size == bridges.size) {
      "JavaScript Runtime bridge names must be unique."
    }
  }
}
