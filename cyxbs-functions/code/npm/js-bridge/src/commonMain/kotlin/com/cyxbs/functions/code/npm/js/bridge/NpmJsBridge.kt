package com.cyxbs.functions.code.npm.js.bridge

/**
 * 声明由端上宿主实现、供 npm JavaScript 包调用的反向桥接口。
 *
 * 接口限定名即稳定 bridgeId。KSP 会在 JS 目标生成强类型代理，在非 Web 目标根据
 * [NpmJsBridgeImpl] 生成宿主分发器。接口不得重载方法；新增或删除方法由运行时 capabilities
 * 协商，默认接口方法只在 JS 侧作为兼容回退，不会下发到宿主。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NpmJsBridge

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

/** 当前入口 npm 包没有安装或无权发现指定桥。 */
class NpmJsBridgeNotInstalledException(
  val bridgeId: String,
) : NpmJsBridgeException("npm JavaScript bridge '$bridgeId' is not installed.")

/** 已安装桥的当前宿主版本没有实现指定方法。 */
class NpmJsBridgeMethodNotImplementedException(
  val bridgeId: String,
  val methodName: String,
) : NpmJsBridgeException(
  "npm JavaScript bridge '$bridgeId' does not implement method '$methodName'.",
)

/** 桥已安装且方法存在，但宿主执行或协议转换失败。 */
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
