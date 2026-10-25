package com.cyxbs.components.utils.network.plugin

import com.cyxbs.components.account.api.AccountAuthenticationException
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.AccountToken
import com.cyxbs.components.account.api.TokenState
import kotlinx.coroutines.flow.MutableStateFlow
import com.cyxbs.components.utils.network.ApiStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 使用真实 Ktor 插件和内存引擎验证默认认证、自定义 token 以及账户生命周期。 */
class TokenPluginTest {
  /** 默认请求携带当前 token，过期响应传回请求所属生命周期。 */
  @Test
  fun defaultTokenAndExpiryUseRequestSession() = runSuspendTest {
    val service = RecordingTokenService()
    var authorization: List<String>? = null
    val engine = RecordingHttpClientEngine {
      authorization = it.headers.getAll(HttpHeaders.Authorization)
      jsonResponse("""{"status":20002,"info":"expired"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/status").body<ApiStatus>()
      assertEquals(listOf("Bearer current-token"), authorization)
      assertEquals(1, service.tokenCalls)
      assertEquals(1, service.tokenExpiryCount)
    } finally { client.close() }
  }

  /** 同域重定向复制默认 header 后，最终响应仍属于原账户生命周期。 */
  @Test
  fun redirectPreservesDefaultTokenExpiryHandling() = runSuspendTest {
    val service = RecordingTokenService()
    val authorizations = mutableListOf<List<String>?>()
    val engine = RecordingHttpClientEngine { request ->
      authorizations += request.headers.getAll(HttpHeaders.Authorization)
      if (request.url.encodedPath == "/redirect") {
        HttpResponseData(
          statusCode = HttpStatusCode.Found,
          requestTime = GMTDate(),
          headers = headersOf(HttpHeaders.Location, "https://example.test/expired"),
          version = HttpProtocolVersion.HTTP_1_1,
          body = ByteReadChannel(""),
          callContext = coroutineContext + Job(),
        )
      } else jsonResponse("""{"status":20004,"info":"expired"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/redirect").body<ApiStatus>()
      assertEquals(2, engine.executeCount)
      assertEquals<List<List<String>?>>(
        listOf(listOf("Bearer current-token"), listOf("Bearer current-token")), authorizations,
      )
      assertEquals(1, service.logoutCount)
    } finally { client.close() }
  }

  /** 显式 Authorization 优先于默认 token，也不触发当前账户的过期副作用。 */
  @Test
  fun explicitTokenIsPreserved() = runSuspendTest {
    val service = RecordingTokenService()
    var authorization: List<String>? = null
    val engine = RecordingHttpClientEngine {
      authorization = it.headers.getAll(HttpHeaders.Authorization)
      jsonResponse("""{"status":20004,"info":"custom token expired"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/status") { bearerAuth("custom-token") }.body<ApiStatus>()
      assertEquals(listOf("Bearer custom-token"), authorization)
      assertEquals(0, service.tokenCalls)
      assertEquals(0, service.logoutCount)
    } finally { client.close() }
  }

  /** 账户已有 token 但本次请求未携带时，任何认证过期响应都不应修改账户凭据或登出。 */
  @Test
  fun anonymousRequestDoesNotHandleExpiry() = runSuspendTest {
    val service = RecordingTokenService().apply { attachToken = false }
    val statuses = listOf(20002, 20003, 20004)
    val responses = statuses.iterator()
    val authorizations = mutableListOf<String?>()
    val engine = RecordingHttpClientEngine {
      authorizations += it.headers[HttpHeaders.Authorization]
      jsonResponse("""{"status":${responses.next()},"info":"anonymous"}""")
    }
    val client = createTestClient(engine, service)
    try {
      statuses.forEach { client.get("https://example.test/status").body<ApiStatus>() }
      assertEquals(listOf<String?>(null, null, null), authorizations)
      assertEquals("current-token", (service.session.tokenState as RecordingTokenService).tokenFlow.value?.token)
      assertEquals(0, service.tokenExpiryCount)
      assertEquals(0, service.logoutCount)
    } finally { client.close() }
  }

  /** 日程仍可绑定会话，已结束的生命周期不会把请求改发给新账号。 */
  @Test
  fun staleBoundSessionNeverReachesEngine() = runSuspendTest {
    val service = RecordingTokenService()
    val oldSession = service.session
    service.session = AccountSession(
      2,
      AccountState.Login("20260002"),
      CoroutineScope(SupervisorJob()),
      tokenState = null,
    )
    val engine = RecordingHttpClientEngine { error("旧会话不能发请求") }
    val client = createTestClient(engine, service)
    try {
      val failure = assertFailsWith<AccountAuthenticationException> {
        client.get("https://example.test/status") { requireAccountSession(oldSession) }
      }
      assertEquals("请求所属账户生命周期已结束", failure.message)
      assertEquals(0, service.tokenCalls)
      assertEquals(0, engine.executeCount)
    } finally { client.close() }
  }

  /** 绑定请求缺少 token 时返回认证异常，不能退化为匿名写入。 */
  @Test
  fun boundSessionRequiresToken() = runSuspendTest {
    val service = RecordingTokenService(initialToken = null)
    val engine = RecordingHttpClientEngine { error("不能匿名发送") }
    val client = createTestClient(engine, service)
    try {
      val failure = assertFailsWith<AccountAuthenticationException> {
        client.get("https://example.test/status") { requireAccountSession(service.session) }
      }
      assertEquals("当前账户没有 token", failure.message)
      assertEquals(0, service.tokenExpiryCount)
      assertEquals(0, service.logoutCount)
      assertEquals(0, engine.executeCount)
    } finally { client.close() }
  }

  /** 等待 token 期间切号，返回认证异常且不发送请求。 */
  @Test
  fun sessionChangeWhileGettingTokenRejectsRequest() = runSuspendTest {
    val service = RecordingTokenService()
    service.beforeTokenReturn = {
      service.session = AccountSession(
        2,
        AccountState.Login("20260002"),
        CoroutineScope(SupervisorJob()),
        tokenState = null,
      )
    }
    val engine = RecordingHttpClientEngine { error("切号后不能发请求") }
    val client = createTestClient(engine, service)
    try {
      val failure = assertFailsWith<AccountAuthenticationException> {
        client.get("https://example.test/status")
      }
      assertEquals("等待 token 时账户已切换", failure.message)
      assertEquals(0, engine.executeCount)
    } finally { client.close() }
  }

  /** token 服务的协程取消继续向上传递，不转换成认证失败，也不触发账户过期处理。 */
  @Test
  fun tokenLookupCancellationIsPropagated() = runSuspendTest {
    val service = RecordingTokenService()
    service.beforeTokenReturn = { throw CancellationException("token 刷新任务已取消") }
    val engine = RecordingHttpClientEngine { error("已取消的任务不能发请求") }
    val client = createTestClient(engine, service)
    try {
      val failure = assertFailsWith<CancellationException> {
        client.get("https://example.test/status")
      }
      assertEquals("token 刷新任务已取消", failure.message)
      assertEquals(0, engine.executeCount)
      assertEquals(0, service.tokenExpiryCount)
      assertEquals(0, service.logoutCount)
    } finally { client.close() }
  }

  /** 请求发出后重新登录，迟到过期响应不能登出新生命周期。 */
  @Test
  fun expiredResponseAfterReloginIsIgnored() = runSuspendTest {
    val service = RecordingTokenService()
    val engine = RecordingHttpClientEngine {
      service.session = AccountSession(
        2,
        AccountState.Login("20260001"),
        CoroutineScope(SupervisorJob()),
        tokenState = null,
      )
      jsonResponse("""{"status":20004,"info":"old response"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/status").body<ApiStatus>()
      assertEquals(0, service.logoutCount)
    } finally { client.close() }
  }

  /** 同一 session 刷新后，旧请求的三种过期响应仍传回旧凭据，不能操作新 token。 */
  @Test
  fun staleCredentialsInSameSessionDoNotExpireRefreshedToken() = runSuspendTest {
    for (status in listOf(20002, 20003, 20004)) {
      val service = RecordingTokenService()
      val session = service.session
      val sentToken = requireNotNull(session.tokenState?.token)
      val refreshed = sentToken.copy(token = "refreshed-token", refreshToken = "refreshed-refresh")
      val engine = RecordingHttpClientEngine {
        assertEquals("Bearer current-token", it.headers[HttpHeaders.Authorization])
        (session.tokenState as RecordingTokenService).tokenFlow.value = refreshed
        jsonResponse("""{"status":$status,"info":"old credentials"}""")
      }
      val client = createTestClient(engine, service)
      try {
        client.get("https://example.test/status").body<ApiStatus>()
        assertSame(session, service.session)
        assertSame(refreshed, session.tokenState?.token)
        assertSame(sentToken, service.lastExpiryToken)
        assertEquals(0, service.tokenExpiryCount)
        assertEquals(0, service.logoutCount)
      } finally { client.close() }
    }
  }

  /** 获取 token 后恰好发生刷新时，header 和响应上下文必须继续使用同一个返回快照。 */
  @Test
  fun tokenLookupSnapshotIsNotReplacedBySessionCredentials() = runSuspendTest {
    val service = RecordingTokenService()
    val snapshot = requireNotNull(service.session.tokenState?.token)
    service.beforeTokenReturn = {
      (service.session.tokenState as RecordingTokenService).tokenFlow.value = snapshot.copy(token = "new-token", refreshToken = "new-refresh")
    }
    val engine = RecordingHttpClientEngine {
      assertEquals("Bearer current-token", it.headers[HttpHeaders.Authorization])
      jsonResponse("""{"status":20004,"info":"old credentials"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/status").body<ApiStatus>()
      assertSame(snapshot, service.lastExpiryToken)
      assertEquals(0, service.logoutCount)
    } finally { client.close() }
  }

  /** 重定向期间刷新 token，最终响应必须匹配最终请求的凭据，不能被外层首次快照覆盖。 */
  @Test
  fun redirectedResponseUsesFinalRequestCredentials() = runSuspendTest {
    val service = RecordingTokenService()
    val refreshed = requireNotNull(service.session.tokenState?.token).copy(token = "new-token", refreshToken = "new-refresh")
    val authorizations = mutableListOf<String?>()
    val engine = RecordingHttpClientEngine { request ->
      authorizations += request.headers[HttpHeaders.Authorization]
      if (request.url.encodedPath == "/redirect") {
        (service.session.tokenState as RecordingTokenService).tokenFlow.value = refreshed
        HttpResponseData(
          statusCode = HttpStatusCode.Found,
          requestTime = GMTDate(),
          headers = headersOf(HttpHeaders.Location, "https://example.test/expired"),
          version = HttpProtocolVersion.HTTP_1_1,
          body = ByteReadChannel(""),
          callContext = coroutineContext + Job(),
        )
      } else jsonResponse("""{"status":20004,"info":"new credentials expired"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/redirect").body<ApiStatus>()
      assertEquals<List<String?>>(listOf("Bearer current-token", "Bearer new-token"), authorizations)
      assertSame(refreshed, service.lastExpiryToken)
      assertEquals(1, service.logoutCount)
    } finally { client.close() }
  }

  /** 跨域重定向保持 Ktor 的认证剥离，并且目标响应不能影响原账户凭据。 */
  @Test
  fun crossOriginRedirectDoesNotAttachDefaultToken() = runSuspendTest {
    val service = RecordingTokenService()
    val authorizations = mutableListOf<String?>()
    val engine = RecordingHttpClientEngine { request ->
      authorizations += request.headers[HttpHeaders.Authorization]
      if (request.url.host == "example.test") {
        HttpResponseData(
          statusCode = HttpStatusCode.Found,
          requestTime = GMTDate(),
          headers = headersOf(HttpHeaders.Location, "https://other.test/expired"),
          version = HttpProtocolVersion.HTTP_1_1,
          body = ByteReadChannel(""),
          callContext = coroutineContext + Job(),
        )
      } else jsonResponse("""{"status":20004,"info":"foreign response"}""")
    }
    val client = createTestClient(engine, service)
    try {
      client.get("https://example.test/redirect").body<ApiStatus>()
      assertEquals<List<String?>>(listOf("Bearer current-token", null), authorizations)
      assertEquals(1, service.tokenCalls)
      assertEquals(0, service.logoutCount)
      assertNull(service.lastExpiryToken)
    } finally { client.close() }
  }

  /** 首次容灾及已缓存的容灾都允许备用域内跳转刷新 token，之后跳到外域仍剥离认证。 */
  @Test
  fun backupRedirectKeepsAuthenticationOnlyWithinBackupOrigin() = runSuspendTest {
    for (cachedBackup in listOf(false, true)) {
      val service = RecordingTokenService()
      val refreshed = service.tokenFlow.value.copy(token = "new-token", refreshToken = "new-refresh")
      val sends = mutableListOf<Pair<String, String?>>()
      val engine = RecordingHttpClientEngine { request ->
        sends += request.url.host to request.headers[HttpHeaders.Authorization]
        if (request.url.host == "example.test") throw ConnectTimeoutException("触发首次容灾")
        if (request.url.encodedPath == "/start") service.tokenFlow.value = refreshed
        when (request.url.encodedPath) {
          "/start", "/next" -> HttpResponseData(
            statusCode = HttpStatusCode.Found,
            requestTime = GMTDate(),
            headers = headersOf(
              HttpHeaders.Location,
              if (request.url.encodedPath == "/start") "https://backup.test/next" else "https://other.test/final",
            ),
            version = HttpProtocolVersion.HTTP_1_1,
            body = ByteReadChannel(""),
            callContext = coroutineContext + Job(),
          )
          else -> jsonResponse("""{"status":20004,"info":"foreign response"}""")
        }
      }
      val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(createTokenPlugin { service.session })
        // 与生产顺序一致，在 TokenPlugin 下游调用真实的容灾切换入口，避免访问备用地址发现服务。
        install(createClientPlugin("TestBackup") {
          on(Send) { request ->
            if (request.url.host != "example.test") return@on proceed(request)
            if (!cachedBackup) {
              try { return@on proceed(request) } catch (_: ConnectTimeoutException) { }
            }
            request.switchToBackupHost("backup.test")
            proceed(request)
          }
        })
      }
      try {
        client.get("https://example.test/start").body<ApiStatus>()
        val expected = mutableListOf(
          "backup.test" to "Bearer current-token",
          "backup.test" to "Bearer new-token",
          "other.test" to null,
        )
        if (!cachedBackup) expected.add(0, "example.test" to "Bearer current-token")
        assertEquals(expected, sends)
        assertEquals(2, service.tokenCalls)
        assertEquals(0, service.logoutCount)
        assertNull(service.lastExpiryToken)
      } finally { client.close() }
    }
  }

  /** 直接 getter 检查有效期但不等待网络；suspend 入口必须等待刷新并返回同一份新快照。 */
  @Test
  fun sessionGetterDoesNotWaitButSuspendGetterAwaitsRefresh() {
    val service = RecordingTokenService()
    val session = service.session
    val previous = requireNotNull((session.tokenState as RecordingTokenService).tokenFlow.value)
    val refreshed = previous.copy(token = "new-token", refreshToken = "new-refresh")
    val pending = CompletableDeferred<AccountToken?>()
    service.tokenUsable = false
    service.refreshTask = pending

    assertNull(session.tokenState?.token)
    assertEquals(0, service.refreshCalls)
    assertSame(previous, (session.tokenState as RecordingTokenService).tokenFlow.value)
    var result: Result<AccountToken?>? = null
    suspend { session.tokenState?.getOrRefreshToken() }.startCoroutine(object : Continuation<AccountToken?> {
      override val context = EmptyCoroutineContext
      override fun resumeWith(outcome: Result<AccountToken?>) { result = outcome }
    })
    assertNull(result)
    assertEquals(1, service.refreshCalls)
    pending.complete(refreshed)

    assertSame(refreshed, requireNotNull(result).getOrThrow())
    assertSame(refreshed, (session.tokenState as RecordingTokenService).tokenFlow.value)
    assertSame(refreshed, session.tokenState?.token)
  }

  /** 等待刷新期间真实取消会传递给调用者，并保留取消前的 session 凭据。 */
  @Test
  fun cancellationWhileAwaitingRefreshIsPropagated() {
    val service = RecordingTokenService()
    val previous = (service.session.tokenState as RecordingTokenService).tokenFlow.value
    val pending = CompletableDeferred<AccountToken?>()
    service.tokenUsable = false
    service.refreshTask = pending
    var result: Result<AccountToken?>? = null
    suspend { service.session.tokenState?.getOrRefreshToken() }.startCoroutine(object : Continuation<AccountToken?> {
      override val context = EmptyCoroutineContext
      override fun resumeWith(outcome: Result<AccountToken?>) { result = outcome }
    })
    assertFalse(pending.isCompleted)
    pending.cancel(CancellationException("刷新已取消"))

    assertTrue(requireNotNull(result).exceptionOrNull() is CancellationException)
    assertSame(previous, (service.session.tokenState as RecordingTokenService).tokenFlow.value)
  }

  /** 非业务响应不参与账号过期处理。 */
  @Test
  fun rawResponseDoesNotHandleExpiry() {
    val service = RecordingTokenService()
    handleAuthenticatedTypedResponse(
      AccountRequestAuthentication(service.session, requireNotNull(service.session.tokenState?.token)), "raw body",
    )
    assertEquals(0, service.logoutCount)
    assertEquals(0, service.tokenExpiryCount)
  }
}

/** 创建仅使用现有 Ktor core 依赖的真实插件测试客户端。 */
private fun createTestClient(
  engine: RecordingHttpClientEngine,
  tokenService: RecordingTokenService,
): HttpClient = HttpClient(engine) {
  install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true })
  }
  install(createTokenPlugin { tokenService.session })
}

/** 构造可被 ContentNegotiation 转换的 JSON 响应。 */
private suspend fun jsonResponse(body: String): HttpResponseData = HttpResponseData(
  statusCode = HttpStatusCode.OK,
  requestTime = GMTDate(),
  headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
  version = HttpProtocolVersion.HTTP_1_1,
  body = ByteReadChannel(body),
  // Ktor cleanup 会完成 response call job；测试引擎需像官方 engine 一样提供独立 CompletableJob。
  callContext = coroutineContext + Job(),
)

/**
 * 使用 ktor-client-core 实现的最小记录引擎，避免为测试扩大依赖或修改构建文件。
 *
 * [handler] 在真实 HttpClient engine 边界接收最终 [HttpRequestData]，因此可核验 wire header 与引擎调用次数。
 */
private class RecordingHttpClientEngine(
  private val handler: suspend (HttpRequestData) -> HttpResponseData,
) : HttpClientEngineBase("TokenPluginTest") {
  override val config = HttpClientEngineConfig().apply {
    dispatcher = Dispatchers.Unconfined
  }

  var executeCount = 0
    private set

  @OptIn(InternalAPI::class)
  override suspend fun execute(data: HttpRequestData): HttpResponseData {
    executeCount += 1
    return handler(data)
  }
}

/**
 * 在不引入 kotlinx-coroutines-test 的 commonTest 中运行立即完成的 fake suspend 调用。
 *
 * 本文件的 fake 与 Unconfined 测试引擎都会在当前调用栈完成；若未来测试需要调度或延迟，应为模块显式引入
 * coroutine test 依赖，而不是让该轻量桥接承担阻塞等待。
 */
private fun <T> runSuspendTest(block: suspend () -> T): T {
  var outcome: Result<T>? = null
  block.startCoroutine(object : Continuation<T> {
    override val context = EmptyCoroutineContext

    override fun resumeWith(result: Result<T>) {
      outcome = result
    }
  })
  return requireNotNull(outcome).getOrThrow()
}

/** 独立 token 状态的测试实现，记录快照获取及过期响应，不注册全局服务。 */
private class RecordingTokenService(initialToken: String? = "current-token") : TokenState {
  // TokenState 实例始终持有凭据；未登录由 session.tokenState = null 表达。
  override val tokenFlow = MutableStateFlow(
    AccountToken("20260001", initialToken.orEmpty(), "refresh-${initialToken.orEmpty()}", 4_102_444_800_000L, 4_102_444_800_000L),
  )
  var session = AccountSession(
    1, AccountState.Login("20260001"), CoroutineScope(SupervisorJob()), tokenState = if (initialToken == null) null else this,
  )
  var attachToken = initialToken != null
  var tokenCalls = 0
  var tokenUsable = true
  var refreshCalls = 0
  var refreshTask: CompletableDeferred<AccountToken?>? = null
  var tokenExpiryCount = 0
  var logoutCount = 0
  var lastExpiryToken: AccountToken? = null
  var beforeTokenReturn: () -> Unit = {}

  /** 不等待刷新，仍执行获取现场的 hook，验证调用期间账户切换。 */
  override val token: AccountToken?
    get() = tokenFlow.value.takeIf { checkTokenValidity() }

  /** 在凭据读取现场执行 hook，保留返回前的请求配置。 */
  private fun checkTokenValidity(): Boolean {
    tokenCalls += 1
    val shouldAttach = attachToken
    beforeTokenReturn()
    return tokenUsable && shouldAttach
  }

  /** 用可控任务验证真正挂起，不依赖网络或时间延迟。 */
  override suspend fun getOrRefreshToken(): AccountToken? {
    val snapshot = tokenFlow.value
    if (checkTokenValidity()) return snapshot
    refreshCalls += 1
    val pending = refreshTask
    val refreshed = if (pending == null) tokenFlow.value.takeIf { attachToken } else pending.await()
    if (refreshed != null) {
      tokenFlow.value = refreshed
      tokenUsable = true
    }
    return refreshed
  }

  override fun isRefreshTokenExpired(): Boolean = false

  override fun tryTokenExpired(expectedToken: AccountToken) {
    lastExpiryToken = expectedToken
    if (isCurrent(expectedToken)) tokenExpiryCount += 1
  }

  override fun tryRefreshTokenExpired(msg: String, expectedToken: AccountToken) {
    lastExpiryToken = expectedToken
    if (isCurrent(expectedToken)) logoutCount += 1
  }

  /** 模拟旧状态已结束及凭据换代；有效期字段不影响请求归属。 */
  private fun isCurrent(expectedToken: AccountToken): Boolean =
    session.tokenState === this && tokenFlow.value.token == expectedToken.token &&
      tokenFlow.value.refreshToken == expectedToken.refreshToken
}
