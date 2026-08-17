package com.cyxbs.components.utils.network.plugin

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.account.api.TokenLifecycleLease
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.network.IApiStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.ClientHook
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException

/** 请求与响应之间传递的认证 lease key；值只保存在本地 Ktor attribute，不进入 wire DTO。 */
internal val TokenLifecycleLeaseKey =
  AttributeKey<TokenLifecycleLease>("TokenLifecycleLease")

/** 仅由 [requireAccountSession] 写入的每请求严格账号会话标记。 */
private val ExpectedAccountSessionKey =
  AttributeKey<AccountSession>("ExpectedAccountSession")

/**
 * 标记该请求只能以传入的 exact [expectedSession] 发出认证信息。
 *
 * 标记会让 [TokenPlugin] 调用严格 lease 入口；会话引用已经陈旧、被 `copy()`、已登出或 token 缺失时请求会以
 * [CancellationException] 结束，绝不退化为当前账号或匿名请求。若客户端已绑定会话，标记必须与该会话为同一引用；
 * 即使结构相等的 `copy()` 也会在读取 token 前被拒绝。它只是本次请求的 fail-closed 标记，不能也不试图在 lease
 * 返回后对 socket 发包建立 CAS。未调用本函数的请求保持原有“有 token 则附加、无 token 仍可发送”的兼容行为。
 */
fun HttpRequestBuilder.requireAccountSession(expectedSession: AccountSession) {
  attributes.put(ExpectedAccountSessionKey, expectedSession)
}

/** 读取每请求严格会话标记，供 Send hook 在实际发出前选择对应的 lease 获取策略。 */
internal fun HttpRequestBuilder.expectedAccountSessionOrNull(): AccountSession? =
  attributes.getOrNull(ExpectedAccountSessionKey)

/**
 * 用新 lease 原子替换 builder 上一次认证信息。
 *
 * redirect 或 Send 重入可能复用、复制已经含 Authorization 的 builder；必须先删除旧值再写 Bearer，保证 wire 上
 * 始终只有一个 Authorization，且它与 request attribute 中最终 lease 一致。
 */
internal fun HttpRequestBuilder.applyTokenLifecycleLease(lease: TokenLifecycleLease) {
  headers.remove(HttpHeaders.Authorization)
  attributes.put(TokenLifecycleLeaseKey, lease)
  bearerAuth(lease.token)
}

/**
 * 根据请求是否带有严格会话标记获取 lease。
 *
 * 已标记路径直接传播严格入口的 CancellationException，不得调用无参入口兜底；未标记路径保留原来的可空 lease
 * 行为，以兼容不要求登录的 API。
 */
internal suspend fun acquireTokenLifecycleLease(
  tokenService: ITokenService,
  expectedSession: AccountSession?,
): TokenLifecycleLease? =
  if (expectedSession == null) {
    tokenService.getOrRequestTokenLease()
  } else {
    tokenService.getOrRequestTokenLease(expectedSession)
  }

/**
 * 在 ContentNegotiation 完成 typed body 转换后观察结果。
 *
 * `TransformResponseBody` 只能消费原始 ByteReadChannel，不适合做反序列化后处理；因此使用公开的 After phase，既不
 * 读取或重放 raw body，也不会改变调用方收到的对象。
 */
private object AuthenticatedResponseBodyHook :
  ClientHook<suspend (HttpClientCall, Any) -> Unit> {

  override fun install(
    client: HttpClient,
    handler: suspend (HttpClientCall, Any) -> Unit,
  ) {
    client.responsePipeline.intercept(HttpResponsePipeline.After) {
      handler(context, subject.response)
    }
  }
}

/**
 * 对 typed 认证响应执行 lease-aware 状态处理。
 *
 * no-token 请求没有 [lease]，raw/stream/download 与其他返回类型不是 [IApiStatus]，两者都会 fail-closed。该函数
 * 独立于 Ktor pipeline，便于用轻量 fake service 直接验证 post-convert 分派。
 */
internal fun handleAuthenticatedTypedResponse(
  tokenService: ITokenService,
  lease: TokenLifecycleLease?,
  body: Any,
) {
  val status = body as? IApiStatus ?: return
  if (lease == null) return
  tokenService.handleAuthenticatedApiStatus(
    lease = lease,
    status = status.status,
    msg = "ApiException, status=${status.status}",
  )
}

/**
 * 为带认证的 Ktor 请求附加 token lease，并在 typed 响应阶段条件处理认证状态码。
 *
 * 对 [requireAccountSession] 标记或客户端绑定会话的请求，Send 只调用严格入口并传播其取消结果，不得回退为当前账号或匿名请求。
 * 已绑定客户端的请求标记只能是相同引用；不同引用（包括结构相等的副本）在任何 token service 调用和 engine 前立即取消，
 * 不能覆盖构造时冻结的会话。成功发出后，将同一个 opaque lease 保存到 response 的 [HttpClientCall.attributes]；Ktor 通过
 * `response.call.attributes` 暴露该本地 attribute。这只证明 header 与响应副作用共享同一生命周期上下文，不声称
 * lease 返回后仍能在 socket 层执行 CAS。
 */
internal fun createTokenPlugin(
  tokenService: ITokenService,
  boundSession: AccountSession? = null,
) = createClientPlugin(
  "TokenPlugin",
) {
  on(Send) { request ->
    val requestSession = request.expectedAccountSessionOrNull()
    if (boundSession != null && requestSession != null && boundSession !== requestSession) {
      // 绑定客户端的冻结会话是唯一认证来源，结构相等的副本也不能覆盖它。
      throw CancellationException("绑定账号会话与请求会话不是同一引用")
    }
    val lease = acquireTokenLifecycleLease(
      tokenService = tokenService,
      expectedSession = boundSession ?: requestSession,
    )
    if (lease != null) {
      request.applyTokenLifecycleLease(lease)
      // 未登录时也允许未标记请求继续发送，端上不好判断该请求是否强依赖登录状态。
    }
    val call = proceed(request)
    if (lease != null) {
      // HttpResponse 没有独立 attribute 容器；其 call attribute 可由 response.call 读取。
      call.attributes.put(TokenLifecycleLeaseKey, lease)
    }
    call
  }
  on(AuthenticatedResponseBodyHook) { call, body ->
    handleAuthenticatedTypedResponse(
      tokenService = tokenService,
      lease = call.attributes.getOrNull(TokenLifecycleLeaseKey),
      body = body,
    )
  }
}

/**
 * 在独立客户端中安装绑定 exact [expectedSession] 的 token 插件。
 *
 * 此客户端的每个请求都只调用 [ITokenService.getOrRequestTokenLease] 的严格重载；请求未显式标记时同样不会退化到
 * 无参 lease。显式标记只能复用 [expectedSession] 的同一引用，不同引用（包括结构相等的副本）会在调用 token service
 * 前取消，不能覆盖工厂绑定会话。它只为该客户端绑定冻结会话，不修改全局 [TokenPlugin] 的兼容语义。
 */
public fun io.ktor.client.HttpClientConfig<*>.installBoundAccountSessionTokenPlugin(
  tokenService: ITokenService,
  expectedSession: AccountSession,
) {
  install(createTokenPlugin(tokenService, expectedSession))
}

internal val TokenPlugin by lazy {
  createTokenPlugin(ITokenService::class.impl())
}
