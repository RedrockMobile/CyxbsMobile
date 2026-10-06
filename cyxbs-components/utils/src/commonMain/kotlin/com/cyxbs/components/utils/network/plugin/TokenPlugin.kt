package com.cyxbs.components.utils.network.plugin

import com.cyxbs.components.account.api.AccountAuthenticationException
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountToken
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.network.IApiStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.ClientHook
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey

/**
 * 请求预期所属 AccountSession 的本地属性名，不会作为字段发送给服务端。
 *
 * 普通接口无需设置，默认使用发送请求时当前账户的 token。后台同步、排队、延迟或重试任务
 * 已基于某次登录读取账户数据时，应携带任务开始时保存的 session；若期间退出或重新登录，
 * 插件会抛出 [AccountAuthenticationException] 并阻止发送，避免把 A 账户的数据携带 B 账户的 token 发送。
 *
 * Ktorfit 使用 @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) 参数传入保存的 session；
 * 普通 Ktor 调用使用 [requireAccountSession]，无需直接操作此属性名。
 */
const val EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME = "ExpectedAccountSession"
private val ExpectedAccountSessionKey =
  AttributeKey<AccountSession>(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME)
/** 本次发送所用的认证快照；session 中的凭据会刷新，响应必须携带发送时的值。 */
internal data class AccountRequestAuthentication(
  val session: AccountSession,
  val token: AccountToken,
)

// 保存本次发送实际使用的 session 和 token，供重定向复用生命周期、响应处理匹配旧凭据。
// 这是请求本地属性，不会发给服务端；自定义 Authorization 和匿名请求不保存此快照。
private val AuthenticatedRequestKey = AttributeKey<AccountRequestAuthentication>("AuthenticatedRequest")
// 保存插件自动添加的完整 Authorization 值；重定向会复制 header 和 attributes，
// 需据此识别继承的默认认证，避免把它误认为调用方自定义认证而跳过 token 更新。
private val DefaultAuthorizationKey = AttributeKey<String>("DefaultAuthorization")
/** 默认认证允许的来源及首次账户归属；容灾可更新来源，重定向不能改变账户生命周期。 */
private data class AccountRequestSource(val origin: String, val session: AccountSession)
private val AccountRequestSourceKey = AttributeKey<AccountRequestSource>("AccountRequestSource")

/**
 * 切换至已由 BackupPlugin 确认的备用 host，并同步默认认证来源。
 *
 * 只更新来源，保留首次账户 session；没有安装 TokenPlugin 的请求只修改 host。
 * 普通 HTTP 重定向不能调用此入口，避免将任意跳转域名视为可信备用服务。
 */
internal fun HttpRequestBuilder.switchToBackupHost(backupHost: String) {
  host = backupHost
  val source = attributes.getOrNull(AccountRequestSourceKey) ?: return
  attributes.put(AccountRequestSourceKey, source.copy(origin = accountAuthenticationOrigin()))
}

/** 协议、域名及有效端口共同确定允许携带默认认证的来源。 */
private fun HttpRequestBuilder.accountAuthenticationOrigin(): String {
  val url = url.build()
  return "${url.protocol.name}://${url.host}:${url.port}"
}

/**
 * 将请求绑定到账户生命周期，供日程、资料刷新等后台任务使用。
 *
 * 会话失效时抛出 [AccountAuthenticationException]，不发送请求；普通请求默认使用当前登录 token。
 */
fun HttpRequestBuilder.requireAccountSession(expectedSession: AccountSession) {
  attributes.put(ExpectedAccountSessionKey, expectedSession)
}

/** 读取 Ktorfit 或调用方设置的会话标记。 */
internal fun HttpRequestBuilder.expectedAccountSessionOrNull(): AccountSession? =
  attributes.getOrNull(ExpectedAccountSessionKey)

/** 反序列化完成后处理业务认证状态，不读取或缓存原始响应字节。 */
private object AuthenticatedResponseBodyHook :
  ClientHook<suspend (HttpClientCall, Any) -> Unit> {
  override fun install(client: HttpClient, handler: suspend (HttpClientCall, Any) -> Unit) {
    client.responsePipeline.intercept(HttpResponsePipeline.After) {
      handler(context, subject.response)
    }
  }
}

/**
 * 处理自动携带当前 token 的响应；过期副作用只属于请求实际使用的生命周期和凭据。
 *
 * 自定义 Authorization、匿名和非业务响应不操作当前账号；读取 ApiWrapper 本身也没有登出副作用。
 */
internal fun handleAuthenticatedTypedResponse(
  authentication: AccountRequestAuthentication,
  body: Any,
) {
  val status = body as? IApiStatus ?: return
  when (status.status) {
    20002, 20003 -> authentication.session.tokenState?.tryTokenExpired(authentication.token)
    20004 -> authentication.session.tokenState?.tryRefreshTokenExpired(
      "ApiException, status=20004", authentication.token,
    )
  }
}

/**
 * 默认附加当前用户 token；调用方显式设置 Authorization 时完整保留该值。
 *
 * accountSession 可注入以便测试。重定向沿用请求生命周期，等待 token 前后检查会话是否仍有效。
 */
internal fun createTokenPlugin(
  accountSession: () -> AccountSession = { IAccountService::class.impl().session.value },
) = createClientPlugin("TokenPlugin") {
  // 每次发送（包括重定向后的发送）都在此确定认证方式和所属账户。
  on(Send) { request ->
    val origin = request.accountAuthenticationOrigin()
    // 来源和生命周期贯穿整个重定向链，匿名跨域响应也不能让后续跳转换用新账户。
    val source = request.attributes.getOrNull(AccountRequestSourceKey)
      ?: AccountRequestSource(origin, accountSession()).also { request.attributes.put(AccountRequestSourceKey, it) }
    // 读取之前由插件添加的认证值；首次请求通常没有这个标记。
    val defaultAuthorization = request.attributes.getOrNull(DefaultAuthorizationKey)
    // 仅在原来源或容灾明确切换的备用来源使用默认 token；没有 Authorization 或只有一个值且与插件标记完全一致时，
    // 也视为默认认证。其余情况按调用方自定义认证处理，保留原有 header。
    // 自定义 Authorization 的有效期和刷新由调用方负责；跨来源跳转不获取默认 token。
    val useDefaultToken = origin == source.origin && (
        !request.headers.contains(HttpHeaders.Authorization) ||
        defaultAuthorization != null &&
        request.headers.getAll(HttpHeaders.Authorization) == listOf(defaultAuthorization))
    // 沿用首次发送的 session，防止多次跳转途中换成另一账户。
    val session = source.session
    // 后台任务可显式绑定创建任务时的 session；普通接口无需设置。
    val expected = request.expectedAccountSessionOrNull()
    // 发送前拒绝已经结束的生命周期；显式绑定的请求还要求身份一致且有账户 ID。
    if (accountSession() !== session ||
      expected != null && (expected !== session || expected.accountId == null)
    ) {
      throw AccountAuthenticationException("请求所属账户生命周期已结束")
    }
    // 默认认证等待获取或刷新 token；自定义认证不读取默认 token，也不触发其刷新。
    val token = if (useDefaultToken) session.tokenState?.getOrRefreshToken() else null
    // 获取 token 可能挂起，恢复后再次确认账户未切换，避免发送旧账户请求。
    if (accountSession() !== session) {
      throw AccountAuthenticationException("等待 token 时账户已切换")
    }
    // 显式绑定账户的默认认证请求必须有 token；普通未绑定请求允许匿名发送。
    if (expected != null && token == null && useDefaultToken) {
      throw AccountAuthenticationException("当前账户没有 token")
    }
    // 清除上次发送的认证标记，随后只记录本次实际使用的默认认证。
    // 自定义认证不会留下默认认证快照，因此其响应不能使当前账户的 token 失效。
    request.attributes.remove(AuthenticatedRequestKey)
    request.attributes.remove(DefaultAuthorizationKey)
    // 仅移除插件负责的默认 header，避免重定向沿用旧 token；自定义 header 保持原值。
    // 跨来源不携带插件写入的认证，即使重定向处理器保留了 header，也应主动删除。
    if (useDefaultToken || defaultAuthorization != null &&
      request.headers.getAll(HttpHeaders.Authorization) == listOf(defaultAuthorization)
    ) request.headers.remove(HttpHeaders.Authorization)
    if (token != null) {
      // 写入本次默认 token 的 Bearer header。
      request.bearerAuth(token.token)
      // 标记完整 header 值，供下一次发送识别默认认证。
      request.attributes.put(DefaultAuthorizationKey, "Bearer ${token.token}")
      // 保存发送时的凭据快照；响应回来时 token 可能已刷新，不能再读取最新值代替它。
      request.attributes.put(AuthenticatedRequestKey, AccountRequestAuthentication(session, token))
    }
    // 完成认证准备后交给后续发送流程。
    // call 保留最终 request 的 attributes；重定向返回后不能用首次凭据覆盖最终发送的快照。
    proceed(request)
  }
  // 在响应反序列化完成后处理业务状态，避免为检查状态重复读取原始响应体。
  on(AuthenticatedResponseBodyHook) { call, body ->
    // 从最终请求读取认证快照；自定义认证、匿名请求没有此标记，跳过账户过期处理。
    val authentication = call.attributes.getOrNull(AuthenticatedRequestKey)
    if (authentication != null) {
      // 仅让响应操作它实际使用的 session/token；TokenState 会过滤已刷新凭据的旧响应。
      handleAuthenticatedTypedResponse(authentication, body)
    }
  }
}

internal val TokenPlugin by lazy { createTokenPlugin() }
