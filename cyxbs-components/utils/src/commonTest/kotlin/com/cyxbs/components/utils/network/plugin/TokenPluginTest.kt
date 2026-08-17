package com.cyxbs.components.utils.network.plugin

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.account.api.TokenLifecycleLease
import com.cyxbs.components.utils.network.ApiStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** typed 响应 post-convert 分派的轻量测试，不依赖真实网络或账号实现。 */
class TokenPluginTest {

  @Test
  fun typedApiStatusPropagatesOriginalLease() {
    val service = RecordingTokenService()
    val lease = TestTokenLifecycleLease("token")

    handleAuthenticatedTypedResponse(
      tokenService = service,
      lease = lease,
      body = ApiStatus(status = 20002, info = "expired"),
    )

    val call = requireNotNull(service.lastCall)
    assertSame(lease, call.lease)
    assertEquals(20002, call.status)
    assertEquals("ApiException, status=20002", call.msg)
  }

  @Test
  fun noLeaseOrNonApiStatusFailsClosed() {
    val service = RecordingTokenService()
    val lease = TestTokenLifecycleLease("token")

    handleAuthenticatedTypedResponse(service, null, ApiStatus(20004, "expired"))
    handleAuthenticatedTypedResponse(service, lease, "raw body")

    assertEquals(null, service.lastCall)
  }

  @Test
  fun requestMarkerPreservesExactExpectedSessionIdentity() {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val request = HttpRequestBuilder().apply {
      requireAccountSession(expectedSession)
    }

    assertSame(expectedSession, request.expectedAccountSessionOrNull())
  }

  @Test
  fun takeFromCopiesExactExpectedSessionIdentity() {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val source = HttpRequestBuilder().apply {
      requireAccountSession(expectedSession)
    }
    val copied = HttpRequestBuilder().apply {
      takeFrom(source)
    }

    assertSame(expectedSession, copied.expectedAccountSessionOrNull())
  }

  @Test
  fun unsupportedLegacyServiceRejectsStrictMarkerWithoutNullableFallback() = runSuspendTest {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val service = LegacyTokenService()

    assertFailsWith<CancellationException> {
      acquireTokenLifecycleLease(service, expectedSession)
    }

    assertEquals(0, service.nullableLeaseCalls)
  }

  @Test
  fun strictMarkerUsesExactLeaseWithoutNullableFallback() = runSuspendTest {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val strictLease = TestTokenLifecycleLease("strict-token")
    val service = RecordingTokenService(strictLease = strictLease)
    val request = HttpRequestBuilder().apply {
      requireAccountSession(expectedSession)
    }

    val lease = acquireTokenLifecycleLease(service, request.expectedAccountSessionOrNull())
    requireNotNull(lease).let(request::applyTokenLifecycleLease)

    assertSame(expectedSession, service.strictExpectedSession)
    assertEquals(0, service.nullableLeaseCalls)
    assertLatestAuthentication(request, strictLease)
  }

  @Test
  fun strictMarkerCancellationNeverFallsBackOrWritesHeader() = runSuspendTest {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val service = RecordingTokenService(strictFailure = CancellationException("stale session"))
    val request = HttpRequestBuilder().apply {
      requireAccountSession(expectedSession)
    }

    assertFailsWith<CancellationException> {
      acquireTokenLifecycleLease(service, request.expectedAccountSessionOrNull())
    }

    assertSame(expectedSession, service.strictExpectedSession)
    assertEquals(0, service.nullableLeaseCalls)
    assertNull(request.headers.getAll(HttpHeaders.Authorization))
  }

  @Test
  fun unmarkedRequestPreservesNullableLeaseBehavior() = runSuspendTest {
    val nullableLease = TestTokenLifecycleLease("compat-token")
    val service = RecordingTokenService(nullableLease = nullableLease)

    val lease = acquireTokenLifecycleLease(service, null)

    assertSame(nullableLease, lease)
    assertEquals(1, service.nullableLeaseCalls)
    assertNull(service.strictExpectedSession)
  }

  @Test
  fun realPluginSendsOneHeaderAndPropagatesCallLeaseToTypedHandler() = runSuspendTest {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val strictLease = TestTokenLifecycleLease("strict-token")
    val service = RecordingTokenService(strictLease = strictLease)
    var engineAuthorization: List<String>? = null
    val engine = RecordingHttpClientEngine { request ->
      engineAuthorization = request.headers.getAll(HttpHeaders.Authorization)
      jsonResponse("""{"status":20002,"info":"expired"}""")
    }
    val client = createTestClient(engine, service)

    try {
      val response = client.get("https://example.test/status") {
        headers.append(HttpHeaders.Authorization, "Bearer stale-token")
        requireAccountSession(expectedSession)
      }
      assertSame(strictLease, response.call.attributes[TokenLifecycleLeaseKey])
      val body = response.body<ApiStatus>()

      assertEquals(20002, body.status)
      assertEquals(listOf("Bearer strict-token"), engineAuthorization)
      assertEquals(1, engine.executeCount)
      assertSame(strictLease, requireNotNull(service.lastCall).lease)
    } finally {
      client.close()
    }
  }

  @Test
  fun realPluginStrictCancellationNeverCallsEngine() = runSuspendTest {
    val expectedSession = AccountSession(1, AccountState.Login("20260001"))
    val service = RecordingTokenService(
      strictFailure = CancellationException("stale session"),
    )
    val engine = RecordingHttpClientEngine {
      jsonResponse("""{"status":0,"info":"unexpected"}""")
    }
    val client = createTestClient(engine, service)

    try {
      assertFailsWith<CancellationException> {
        client.get("https://example.test/status") {
          requireAccountSession(expectedSession)
        }
      }
      assertEquals(0, engine.executeCount)
      assertEquals(0, service.nullableLeaseCalls)
    } finally {
      client.close()
    }
  }

  @Test
  fun repeatedOrCopiedBuilderReplacesAuthorizationWithLatestLease() {
    val leaseA = TestTokenLifecycleLease("token-a")
    val leaseB = TestTokenLifecycleLease("token-b")
    val sameBuilder = HttpRequestBuilder()

    sameBuilder.applyTokenLifecycleLease(leaseA)
    sameBuilder.applyTokenLifecycleLease(leaseB)
    assertLatestAuthentication(sameBuilder, leaseB)

    val sourceBuilder = HttpRequestBuilder().apply {
      applyTokenLifecycleLease(leaseA)
    }
    val copiedBuilder = HttpRequestBuilder().apply {
      takeFrom(sourceBuilder)
      applyTokenLifecycleLease(leaseB)
    }
    assertLatestAuthentication(copiedBuilder, leaseB)
  }

  /** 同时核验 wire header 唯一性与 request attribute 的 lease 一致性。 */
  private fun assertLatestAuthentication(
    request: HttpRequestBuilder,
    expectedLease: TokenLifecycleLease,
  ) {
    assertEquals(
      listOf("Bearer ${expectedLease.token}"),
      request.headers.getAll(HttpHeaders.Authorization),
    )
    assertSame(expectedLease, request.attributes[TokenLifecycleLeaseKey])
  }
}

/** 创建仅使用现有 Ktor core 依赖的真实插件测试客户端。 */
private fun createTestClient(
  engine: RecordingHttpClientEngine,
  tokenService: ITokenService,
): HttpClient = HttpClient(engine) {
  install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true })
  }
  install(createTokenPlugin(tokenService))
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
 * 模拟尚未实现 strict overload 的旧服务；接口默认实现必须直接拒绝，不能调用该可空入口。
 */
private class LegacyTokenService : ITokenService {
  var nullableLeaseCalls = 0

  override suspend fun getOrRequestTokenLease(): TokenLifecycleLease? {
    nullableLeaseCalls += 1
    return TestTokenLifecycleLease("legacy-token")
  }

  override fun getOrRequestTokenLease2(
    runBlock: (Deferred<String>) -> String,
  ): TokenLifecycleLease? = null

  override fun handleAuthenticatedApiStatus(
    lease: TokenLifecycleLease,
    status: Int,
    msg: String,
  ) = Unit

  override fun getToken(): String? = null

  override fun isRefreshTokenExpired(): Boolean = true

  override fun tryTokenExpired() = Unit

  override fun tryRefreshTokenExpired(msg: String) = Unit
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

/** 测试专用不透明 lease；只验证网络层原样传播，不模拟账号内部 identity。 */
private class TestTokenLifecycleLease(
  override val token: String,
) : TokenLifecycleLease

/**
 * 记录认证入口与 typed-response handler 入参的轻量 fake。
 *
 * 严格入口保留 expected session，测试可因此验证标记请求没有误走无参兼容路径。
 */
private class RecordingTokenService(
  private val nullableLease: TokenLifecycleLease? = null,
  private val strictLease: TokenLifecycleLease? = null,
  private val strictFailure: CancellationException? = null,
) : ITokenService {
  data class Call(
    val lease: TokenLifecycleLease,
    val status: Int,
    val msg: String,
  )

  var lastCall: Call? = null
  var nullableLeaseCalls = 0
  var strictExpectedSession: AccountSession? = null

  override suspend fun getOrRequestTokenLease(): TokenLifecycleLease? {
    nullableLeaseCalls += 1
    return nullableLease
  }

  override suspend fun getOrRequestTokenLease(
    expectedSession: AccountSession,
  ): TokenLifecycleLease {
    strictExpectedSession = expectedSession
    strictFailure?.let { throw it }
    return requireNotNull(strictLease)
  }

  override fun getOrRequestTokenLease2(
    runBlock: (Deferred<String>) -> String,
  ): TokenLifecycleLease? = null

  override fun handleAuthenticatedApiStatus(
    lease: TokenLifecycleLease,
    status: Int,
    msg: String,
  ) {
    lastCall = Call(lease, status, msg)
  }

  override fun getToken(): String? = null

  override fun isRefreshTokenExpired(): Boolean = true

  override fun tryTokenExpired() = Unit

  override fun tryRefreshTokenExpired(msg: String) = Unit
}
