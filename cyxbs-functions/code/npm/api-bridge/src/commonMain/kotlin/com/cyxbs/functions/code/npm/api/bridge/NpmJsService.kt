package com.cyxbs.functions.code.npm.api.bridge

/**
 * 声明一个由 npm 包提供实现的 Kotlin/JS Service。
 *
 * KSP 会为非 Web 端生成实现该接口的代理，并为 Kotlin/JS 实现生成调用分发器。业务只依赖声明该
 * 注解的 commonMain 接口，不应直接引用任何以 `_` 开头的生成类型。
 *
 * 本注解只声明 Kotlin 与 JavaScript 之间的接口协议。npm 包名和版本属于每次加载的运行时配置，
 * 由业务调用 `NpmJsServiceLoader.load` 时传入，因此同一个接口可以桥接不同的包或版本。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NpmJsService

/**
 * 动态 npm JavaScript Service 的共同生命周期接口。
 *
 * Kotlin/JS 实现通常沿用默认空实现；端上 KSP 代理会覆盖 [close]，等待当前调用结束后关闭
 * JavaScript Runtime 并释放 npm 入口租约。不再使用实例时必须调用 [close]。
 */
interface NpmJsServiceInstance {

  /**
   * 释放远端 Service、JavaScript Runtime 和 npm 租约；重复调用必须安全。
   */
  suspend fun close() = Unit
}

/** npm JavaScript Service 声明、生成代码或远端协议不兼容。 */
class NpmJsServiceProtocolException(
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * npm JavaScript Service 方法执行失败。
 *
 * 该异常不会暴露 QuickJS 等具体引擎类型，底层错误仅通过 [cause] 保留用于诊断。
 */
class NpmJsServiceInvocationException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
