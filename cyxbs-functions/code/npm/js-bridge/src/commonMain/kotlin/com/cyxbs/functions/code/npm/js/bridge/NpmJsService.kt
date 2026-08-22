package com.cyxbs.functions.code.npm.js.bridge

/**
 * 声明一个由 npm 包提供实现的 Kotlin/JS Service。
 *
 * KSP 会为非 Web 端生成实现该接口的代理，并为 Kotlin/JS 实现生成调用分发器。业务只依赖声明该
 * 注解的 commonMain 接口，不应直接引用任何以 `_` 开头的生成类型。
 *
 * 本注解只声明 Kotlin 与 JavaScript 之间的接口协议。npm 包名和版本属于每次加载的运行时配置，
 * 由业务调用 `NpmJsServiceLoader.load` 时传入，因此同一个接口可以桥接不同的包或版本。
 *
 * ## 方法失败约定
 *
 * - 所有声明方法都必须是 `suspend fun ...: NpmJsResult<T>`，KSP 会在两端编译期强校验；
 * - 当前 bundle 尚未实现接口请求的方法时，返回的失败为
 *   [NpmJsServiceMethodNotImplementedException]；
 * - bundle 显式返回失败、实现抛出普通异常、参数或返回值 JSON 不兼容、Runtime 调用失败时，
 *   返回的失败为 [NpmJsServiceInvocationException]；
 * - [kotlinx.coroutines.CancellationException] 不进入 [NpmJsResult]，始终继续抛出；
 * - npm bundle 不存在、下载或完整性校验失败发生在 Service 实例创建前，由
 *   `NpmJsServiceLoader.load` 直接抛出加载异常，不能通过尚不存在的 Service 结果返回。
 *
 * [NpmJsResult] 的跨 Runtime 传输只保留稳定成功值和失败消息，不序列化 Throwable 类型或堆栈。调用方
 * 应按上述本地公开异常分类降级，不应依赖远端实现异常的具体类名。
 *
 * 结果信封不兼容旧版“直接返回业务 JSON”的协议，宿主与动态包必须使用相同协议基线。
 * 方法增删仍通过能力列表判断；调用当前动态包未实现的方法时返回
 * [NpmJsServiceMethodNotImplementedException]，而不是尝试按旧协议降级。后续信封只允许新增
 * 可选字段，解码端会忽略未知字段。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NpmJsService

/**
 * 动态 npm JavaScript Service 的共同生命周期接口。
 *
 * Kotlin/JS 实现和端上 KSP 代理都必须显式实现 [close]。端上代理等待当前调用结束后关闭
 * JavaScript Runtime 并释放 npm 入口租约；JavaScript 实现则负责声明自身的资源清理行为。
 * 不再使用实例时必须调用 [close]。
 */
interface NpmJsServiceInstance {

  /**
   * 释放远端 Service、JavaScript Runtime 和 npm 租约；重复调用必须安全。
   *
   * 每个 Service 实现都必须显式声明关闭行为。即使当前没有额外资源，也必须返回
   * `NpmJsResult.success(Unit)`，避免实现者无意忽略生命周期契约。
   *
   * 除 [kotlinx.coroutines.CancellationException] 外，关闭远端实现、等待在途调用或释放 Runtime
   * 失败都通过 [NpmJsResult.failure] 返回。取消继续抛出，调用方不得把它当成普通业务失败吞掉。
   */
  suspend fun close(): NpmJsResult<Unit>
}

/**
 * npm JavaScript Service 声明、生成代码或远端协议不兼容。
 *
 * 加载阶段没有生成工厂、Service 身份不一致、入口模块缺失或初始化协议不完整时直接抛出；实例
 * 创建后的方法编解码错误则被代理包装进 [NpmJsResult.failure]。
 */
class NpmJsServiceProtocolException(
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * 当前 JavaScript Service 尚未实现端上接口请求的方法。
 *
 * 调用 capabilities 中不存在的方法时抛出本异常，调用方可以针对单项能力降级。生成代理把本
 * 异常放进对应方法的 [NpmJsResult.failure]，不会破坏同一实例的其他方法。
 *
 * @param serviceId KSP 根据接口全限定名生成的稳定 Service 标识。
 * @param method 缺少实现的方法名。
 */
class NpmJsServiceMethodNotImplementedException(
  val serviceId: String,
  val method: String,
) : UnsupportedOperationException(
  "npm JavaScript Service '$serviceId' does not implement method '$method'.",
)

/**
 * npm JavaScript Service 方法执行失败。
 *
 * 包括远端显式失败、实现普通异常、JSON 不兼容、Runtime 调用和返回信封损坏。该异常不会暴露
 * QuickJS 等具体引擎类型；本地传输错误通过 [cause] 保留，远端只保留受限失败消息。
 */
class NpmJsServiceInvocationException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
