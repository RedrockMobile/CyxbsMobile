package com.cyxbs.functions.code.npm.js.bridge

/**
 * 声明由端上宿主实现、供 npm JavaScript 包调用的反向桥接口。
 *
 * 接口限定名即稳定 bridgeId。KSP 会在 JS 目标生成强类型代理，在非 Web 目标根据
 * [NpmJsBridgeImpl] 生成宿主分发器。接口不得重载方法；新增或删除方法由运行时 capabilities
 * 协商，默认接口方法只在 JS 侧作为兼容回退，不会下发到宿主。
 *
 * ## 方法失败约定
 *
 * - 接口必须直接继承 [NpmJsBridgeInstance]，所有声明方法必须是
 *   `suspend fun ...: NpmJsResult<T>`，否则 KSP 终止编译；
 * - 当前入口包未安装该桥或不在 [NpmJsBridgeImpl] 配置的可见范围内时，返回的失败为
 *   [NpmJsBridgeNotInstalledException]；
 * - npm 包调用宿主尚未实现的方法时，返回的失败为
 *   [NpmJsBridgeMethodNotImplementedException]；
 * - 宿主显式返回失败、实现抛出普通异常、请求或返回 JSON 不兼容、网关调用失败时，返回的失败
 *   为 [NpmJsBridgeInvocationException]；
 * - [kotlinx.coroutines.CancellationException] 不进入 [NpmJsResult]，始终继续抛出。
 *
 * 桥的 capabilities 只负责判断“桥/方法是否存在”，业务失败仍通过结果信封返回。信封不跨
 * Runtime 序列化 Throwable 类型和堆栈，只传输稳定消息，避免调用双方依赖彼此异常类实现。
 *
 * 结果信封不兼容旧版原始返回 JSON，宿主与动态包必须使用相同协议基线。方法增删仍通过
 * capabilities 判断；调用宿主未实现的方法时返回 [NpmJsBridgeMethodNotImplementedException]，
 * 而不是尝试按旧协议降级。后续信封演进只新增可选字段，解码端忽略未知键。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NpmJsBridge

/**
 * 所有 [NpmJsBridge] 协议必须直接继承的标记接口。
 *
 * KSP 通过该父接口区分普通 Kotlin 接口和可跨 npm Runtime 调用的 Host Bridge，并强制桥方法
 * 使用统一的 `suspend fun ...: NpmJsResult<T>` 失败模型。该接口没有生命周期：Host Bridge 的实例由
 * Runtime 创建和销毁，npm 包不能主动关闭宿主实现。
 */
interface NpmJsBridgeInstance

/**
 * 声明一个反向桥的端上实现及可见 npm 包范围。
 *
 * [packageScope] 没有默认值，避免新增桥时无意暴露给全部动态包。KSP 会校验
 * [NpmJsBridgePackageScope.ALL_PACKAGES] 不携带包名，而
 * [NpmJsBridgePackageScope.SPECIFIED_PACKAGES] 至少携带一个合法且不含版本的 npm 包名。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NpmJsBridgeImpl(
  val packageScope: NpmJsBridgePackageScope,
  val packageNames: Array<String> = [],
)

/** 反向桥实现允许被哪些入口 npm 包发现和调用。 */
enum class NpmJsBridgePackageScope {
  /** 所有入口 npm 包均可使用。 */
  ALL_PACKAGES,

  /** 仅 [NpmJsBridgeImpl.packageNames] 中显式列出的入口 npm 包可使用。 */
  SPECIFIED_PACKAGES,
}

/** npm 反向桥调用的稳定异常基类。 */
open class NpmJsBridgeException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 当前入口 npm 包没有安装或无权发现指定桥。
 *
 * JS 代理将其放入桥方法的 [NpmJsResult.failure]；通常表示桥实现未注册，或入口包不在声明的可见
 * 范围内。
 */
class NpmJsBridgeNotInstalledException(
  val bridgeId: String,
) : NpmJsBridgeException("npm JavaScript bridge '$bridgeId' is not installed.")

/**
 * 已安装桥没有实现指定方法。
 *
 * 这是 capabilities 与调用请求不匹配时的稳定失败，只影响本次方法调用。
 */
class NpmJsBridgeMethodNotImplementedException(
  val bridgeId: String,
  val methodName: String,
) : NpmJsBridgeException(
  "npm JavaScript bridge '$bridgeId' does not implement method '$methodName'.",
)

/**
 * 桥已安装且方法存在，但宿主执行或协议转换失败。
 *
 * 包括宿主显式失败、普通实现异常、参数或返回信封不兼容以及网关传输失败；远端异常只保留受限
 * 消息，不承诺原异常类型和堆栈可用。
 */
class NpmJsBridgeInvocationException(
  message: String,
  cause: Throwable? = null,
) : NpmJsBridgeException(message, cause)

/** npm 反向桥在 JS Runtime 与宿主网关之间共享的稳定 ABI。 */
object NpmJsBridgeHostAbi {
  const val GATEWAY: String = "__cyxbs_npm_js_bridge_gateway"
  const val DESCRIBE: String = "describe"
  const val INVOKE: String = "invoke"

  const val ERROR_BRIDGE_NOT_INSTALLED: String = "bridge_not_installed"
  const val ERROR_METHOD_NOT_IMPLEMENTED: String = "method_not_implemented"
  const val ERROR_INVALID_REQUEST: String = "invalid_request"
  const val ERROR_INVOCATION_FAILED: String = "invocation_failed"
}
