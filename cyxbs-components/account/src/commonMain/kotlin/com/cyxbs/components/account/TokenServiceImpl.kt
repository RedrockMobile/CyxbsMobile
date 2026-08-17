package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.account.api.TokenLifecycleLease
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.pages.login.api.ILoginService
import com.g985892345.provider.api.annotation.ImplProvider
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * 账号生命周期发生变化时使用的可识别取消原因。
 *
 * 仅该类型允许严格 lease 获取在“同一 exact session 的 TokenBean 已前进”时恢复；普通调用方取消、登出和切号产生的
 * 其他取消异常必须原样传播。
 */
internal class TokenLifecycleChangedCancellationException :
  CancellationException("account lifecycle changed")

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
@ImplProvider
object TokenServiceImpl : ITokenService {

  private val requestMutex = Mutex()
  private val requestSynchronized = SynchronizedObject()

  /**
   * 冻结 session 与源 TokenBean 后获取本次请求可用的 lease；任一返回点都会复核同一生命周期。
   *
   * lease 必须对应最终实际附加的 TokenBean。后台 refresh 或锁内等待期间 token 发生换代时，会重新读取当前
   * TokenBean，而不是把最初 snapshot 的 identity 错绑给新 token。
   */
  override suspend fun getOrRequestTokenLease(): TokenLifecycleLease? {
    val snapshot = AccountService.freezeTokenLifecycle() ?: return null
    return getOrRequestTokenLease(snapshot)
  }

  /**
   * 严格入口只接受当前发布的 exact AccountSession identity。
   *
   * expectedSession 不是当前对象、已登出或 token 缺失时均直接取消。尤其不能因为同学号一致而重新冻结当前会话，
   * 否则旧页面在同账号重新登录后会把请求错误归属到新 generation。
   */
  override suspend fun getOrRequestTokenLease(
    expectedSession: AccountSession,
  ): TokenLifecycleLease {
    val snapshot = AccountService.freezeTokenLifecycle() ?: throw lifecycleChanged()
    if (snapshot.session !== expectedSession) throw lifecycleChanged()
    return getOrRequestTokenLease(snapshot)
  }

  /**
   * 在已冻结的 session 与源 TokenBean 上完成获取或刷新，并只签发最终实际附加 TokenBean 的 lease。
   *
   * 该函数返回 lease 后不声称 socket 层仍能 CAS；网络层仅能将返回的同一 lease 随 request/response 传播，
   * 让响应副作用在账号模块按 identity 条件处理。
   */
  private suspend fun getOrRequestTokenLease(
    snapshot: AccountService.TokenLifecycleSnapshot,
  ): TokenLifecycleLease {
    if (checkTokenEnable(snapshot)) return currentTokenLeaseForSession(snapshot.session)
    return requestMutex.withLock {
      val currentToken =
        AccountService.currentTokenFor(snapshot.session) ?: throw lifecycleChanged()
      if (currentToken !== snapshot.token) {
        tokenLeaseIfLifecycleCurrent(snapshot.session, currentToken)
      } else {
        awaitRefreshLease(
          expectedSession = snapshot.session,
          sourceToken = snapshot.token,
          refreshDeferred = requestToken(snapshot),
        )
      }
    }
  }

  /**
   * 等待指定源 TokenBean 的 refresh，并在唯一安全的竞争窗口恢复到同 session 的新 TokenBean。
   *
   * 旧 source 通过锁内检查后，先前启动的后台 refresh 可能提交新 TokenBean 并从 registry 清除；此时旧 source 新建的
   * refresh 会因提交门禁抛出 [TokenLifecycleChangedCancellationException]。只有当前调用协程仍 active、exact
   * [expectedSession] 仍权威且当前 TokenBean identity 已从 [sourceToken] 前进时，才返回新 lease。调用方取消、登出、
   * 切号、同账号新 generation 或 token 缺失均原样取消，不得恢复。
   */
  internal suspend fun awaitRefreshLease(
    expectedSession: AccountSession,
    sourceToken: TokenBean,
    refreshDeferred: Deferred<String>,
  ): TokenLifecycleLease {
    try {
      refreshDeferred.await()
    } catch (error: TokenLifecycleChangedCancellationException) {
      // Deferred 的生命周期取消可能与外层 caller cancellation 同时发生；恢复前必须优先尊重调用方取消。
      currentCoroutineContext().ensureActive()
      val currentToken = AccountService.currentTokenFor(expectedSession) ?: throw error
      if (currentToken === sourceToken) throw error
      return tokenLeaseIfLifecycleCurrent(expectedSession, currentToken)
    }
    // Deferred 完成到当前 continuation 恢复之间仍可能切号，返回前必须再次复核 session 与实际 TokenBean。
    return currentTokenLeaseForSession(expectedSession)
  }

  /** 同步桥接版本同样冻结生命周期；阻塞等待结束后 lease 绑定实际提交的 TokenBean identity。 */
  override fun getOrRequestTokenLease2(
    runBlock: (Deferred<String>) -> String,
  ): TokenLifecycleLease? {
    val snapshot = AccountService.freezeTokenLifecycle() ?: return null
    if (checkTokenEnable(snapshot)) return currentTokenLeaseForSession(snapshot.session)
    return synchronized(requestSynchronized) {
      val currentToken =
        AccountService.currentTokenFor(snapshot.session) ?: throw lifecycleChanged()
      if (currentToken !== snapshot.token) {
        tokenLeaseIfLifecycleCurrent(snapshot.session, currentToken)
      } else {
        val refreshedToken = runBlock.invoke(requestToken(snapshot))
        val committedToken =
          AccountService.currentTokenFor(snapshot.session) ?: throw lifecycleChanged()
        if (committedToken.token != refreshedToken) throw lifecycleChanged()
        TokenLifecycleLeaseImpl(snapshot.session, committedToken)
      }
    }
  }

  /** 快路径返回前复核 session；同一 generation 的后台 refresh 完成时返回其最新 token。 */
  override fun getToken(): String? {
    val snapshot = AccountService.freezeTokenLifecycle() ?: return null
    if (!checkTokenEnable(snapshot)) return null
    return currentTokenForSession(snapshot.session)
  }

  /**
   * 按冻结快照判断 token 有效期；异步 refresh 也必须携带同一 session 与源 TokenBean identity。
   *
   * [requestToken] 自身按 session 与源 TokenBean 合并 Deferred，因此无需跨账号共享额外的全局刷新标记。
   */
  private fun checkTokenEnable(snapshot: AccountService.TokenLifecycleSnapshot): Boolean {
    if (snapshot.tokenRemainTime > 12.hours) return true
    if (snapshot.tokenRemainTime > 10.minutes) {
      if (AccountService.isCurrentTokenLifecycle(snapshot.session, snapshot.token)) {
        requestToken(snapshot)
      }
      return true
    }
    return false
  }

  /** 仅按 session identity 复核，并返回该 generation 已提交的最新 token。 */
  internal fun currentTokenForSession(session: AccountSession): String {
    return currentTokenLeaseForSession(session).token
  }

  /** 返回当前 generation 实际持有 TokenBean 对应的请求 lease。 */
  private fun currentTokenLeaseForSession(session: AccountSession): TokenLifecycleLeaseImpl {
    val token = AccountService.currentTokenFor(session) ?: throw lifecycleChanged()
    return TokenLifecycleLeaseImpl(session, token)
  }

  /** 返回指定 TokenBean 的 lease 前同时复核 session 与 token identity。 */
  private fun tokenLeaseIfLifecycleCurrent(
    session: AccountSession,
    token: TokenBean,
  ): TokenLifecycleLeaseImpl {
    if (AccountService.currentTokenFor(session) !== token) throw lifecycleChanged()
    return TokenLifecycleLeaseImpl(session, token)
  }

  /**
   * 账号模块私有的 lease 实现；类型检查用于拒绝网络层或其他模块自行伪造生命周期上下文。
   */
  private class TokenLifecycleLeaseImpl(
    val session: AccountSession,
    val sourceToken: TokenBean,
  ) : TokenLifecycleLease {
    override val token: String = sourceToken.token
  }

  /** 创建账号模块可识别的生命周期取消原因，供严格 lease 仅恢复安全的 token 前进竞争。 */
  private fun lifecycleChanged() = TokenLifecycleChangedCancellationException()

  override fun isRefreshTokenExpired(): Boolean {
    return TokenProvider.isRefreshTokenExpired()
  }

  @Volatile
  private var lastTryTokenExpiredTime = 0.milliseconds
  private var lastTryTokenExpiredSession: AccountSession? = null
  private var lastTryTokenExpiredToken: TokenBean? = null
  private val tryTokenExpiredLock = SynchronizedObject()

  /**
   * 按认证请求冻结的 exact lifecycle 处理业务状态码。
   *
   * 只有本对象签发的 lease 才包含不可伪造的 AccountSession 与 TokenBean identity；未知实现、非认证请求或已经
   * 陈旧的请求全部 fail-closed。业务异常仍由 ApiWrapper 在调用方处抛出，本方法只负责认证副作用。
   */
  override fun handleAuthenticatedApiStatus(
    lease: TokenLifecycleLease,
    status: Int,
    msg: String,
  ) {
    val lifecycleLease = lease as? TokenLifecycleLeaseImpl ?: return
    when (status) {
      20002, 20003 -> expireTokenIfCurrent(lifecycleLease)
      20004 -> expireRefreshTokenIfCurrent(
        msg,
        lifecycleLease.session,
        lifecycleLease.sourceToken,
      )
    }
  }

  /**
   * 仅对 lease 对应的当前源 TokenBean 执行过期，并按 exact lifecycle 节流。
   *
   * 条件操作失败时不更新时间；新 session 或同 session 新 TokenBean 也不会被旧 lifecycle 的 30 分钟窗口抑制。
   */
  private fun expireTokenIfCurrent(lease: TokenLifecycleLeaseImpl) {
    synchronized(tryTokenExpiredLock) {
      val now = Clock.System.now().toEpochMilliseconds().milliseconds
      if (
        lastTryTokenExpiredSession === lease.session &&
        lastTryTokenExpiredToken === lease.sourceToken &&
        now - lastTryTokenExpiredTime <= 30.minutes
      ) return
      if (
        AccountService.expireTokenIfCurrentTokenLifecycle(
          lease.session,
          lease.sourceToken,
        )
      ) {
        lastTryTokenExpiredSession = lease.session
        lastTryTokenExpiredToken = lease.sourceToken
        lastTryTokenExpiredTime = now
      }
    }
  }

  override fun tryTokenExpired() {
    synchronized(tryTokenExpiredLock) {
      val now = Clock.System.now().toEpochMilliseconds().milliseconds
      if (now - lastTryTokenExpiredTime > 30.minutes) {
        // 无上下文兼容入口不应让其时间窗伪装成某个请求 lifecycle。
        lastTryTokenExpiredSession = null
        lastTryTokenExpiredToken = null
        lastTryTokenExpiredTime = now
        TokenProvider.forceTokenExpired()
      }
    }
  }

  @Volatile
  private var lastTryRefreshTokenExpiredTime = 0.milliseconds
  private val tryRefreshTokenExpiredLock = SynchronizedObject()

  /**
   * 普通业务请求的兼容入口；该路径尚无请求上下文，本切片不改变其既有行为。
   *
   * refresh 请求必须改用 [expireRefreshTokenIfCurrent]，避免旧 refresh 响应登出新账号。
   */
  override fun tryRefreshTokenExpired(msg: String) {
    synchronized(tryRefreshTokenExpiredLock) {
      val now = Clock.System.now().toEpochMilliseconds().milliseconds
      if (now - lastTryRefreshTokenExpiredTime > 30.minutes) {
        lastTryRefreshTokenExpiredTime = now
        toastLong("登录已过期，请重新登录\n原因：$msg")
        IAccountEditService::class.impl().onLogout()
        ILoginService::class.impl().jumpToLoginPage() // 跳转登录页
      }
    }
  }

  private val refreshDeferredRegistry = TokenRefreshDeferredRegistry()

  /**
   * 为冻结 session 与源 TokenBean 创建或复用 refresh 请求。
   *
   * 任务绑定 [AccountService.accountCoroutineScopeFor]，切号会主动取消；成功提交和失败副作用仍通过 publication guard
   * 条件执行，以覆盖网络完成与取消同时发生的边界。
   */
  private fun requestToken(snapshot: AccountService.TokenLifecycleSnapshot): Deferred<String> {
    return refreshDeferredRegistry.getOrCreate(snapshot.session, snapshot.token) {
      createRefreshDeferred(snapshot)
    }
  }

  /** 创建账号 scope 内的 refresh Deferred；session 已失效时返回已取消结果。 */
  private fun createRefreshDeferred(
    snapshot: AccountService.TokenLifecycleSnapshot,
  ): Deferred<String> {
    val scope = AccountService.accountCoroutineScopeFor(snapshot.session)
      ?: return CompletableDeferred<String>().also { it.cancel(lifecycleChanged()) }
    return scope.async(start = CoroutineStart.LAZY) {
      try {
        val wrapper = HttpClientNoToken.post("/magipoke/token/refresh") {
          setBody(buildJsonObject {
            put("refreshToken", snapshot.token.refreshToken)
          }.toString())
          header("STU-NUM", snapshot.token.info.data.stuNum)
        }.body<ApiWrapper<TokenBean>>()
        // refresh 响应不能调用无上下文 throwApiExceptionIfFail，否则 20004 会旁路生命周期门禁。
        if (!wrapper.isSuccess()) throw ApiException(wrapper.status, wrapper.info)
        val refreshedToken = wrapper.data
        if (!AccountService.commitRefreshedToken(
            snapshot.session,
            snapshot.token,
            refreshedToken
          )
        ) {
          throw lifecycleChanged()
        }
        refreshedToken.token
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        onRequestTokenFailure(error, snapshot.session, snapshot.token)
        throw error
      }
    }
  }

  /**
   * 1. refreshToken 失败，如果没带 STU-NUM，则直接返回 status=20004
   * 2. refreshToken 失败，如果带了 STU-NUM，则会兜底签一个 token
   *  2.1. 兜底签的 token 5 天只能使用一次，重复使用则返回 http 400，errcode=10010, errmessage=emergence refused:重复的学号
   *  2.2. 如果系统内部调用失败，则返回 http 400，errcode=10010, errmessage=find redid error
   *  2.3. 如果签发失败，则返回 http 400，errcode=10010, errmessage=sign in emerge error
   *  2.4. 签发不合法，则返回 http 400，status=20004
   *
   * 每次挂起解析前后都复核 session 与源 TokenBean；旧响应只向原调用方抛错，不 toast、登出或跳转页面。
   */
  private suspend fun onRequestTokenFailure(
    throwable: Throwable,
    expectedSession: AccountSession,
    expectedToken: TokenBean,
  ) {
    if (!AccountService.isCurrentTokenLifecycle(expectedSession, expectedToken)) return
    when (throwable) {
      is ConnectTimeoutException, is HttpRequestTimeoutException -> {
        toastRefreshTokenFailedIfCurrent(
          "refresh token 连接超时", expectedSession, expectedToken,
        )
      }

      is ServerResponseException -> {
        val body = throwable.response.bodyAsText()
        toastRefreshTokenFailedIfCurrent(
          "refresh token 服务器错误\nhttp status=${throwable.response.status}\nbody=$body",
          expectedSession,
          expectedToken,
        )
      }

      is ClientRequestException -> {
        if (throwable.response.status == HttpStatusCode.BadRequest) {
          val failureBean = throwable.response.body<RequestTokenFailureBean>()
          if (!AccountService.isCurrentTokenLifecycle(expectedSession, expectedToken)) return
          when {
            failureBean.status == 20004 -> {
              toastRefreshTokenFailedIfCurrent(
                "refresh token 已失效，请重新登录", expectedSession, expectedToken,
              )
              expireRefreshTokenIfCurrent(
                "refresh, status=20004", expectedSession, expectedToken,
              )
            }

            failureBean.errcode == 10010 && failureBean.errmessage.contains("重复的学号") -> {
              toastRefreshTokenFailedIfCurrent(
                "refresh token 重签失败，请重新登录", expectedSession, expectedToken,
              )
              expireRefreshTokenIfCurrent(
                "refresh, emergence refused:重复的学号", expectedSession, expectedToken,
              )
            }

            failureBean.errcode == 10010 && failureBean.errmessage.contains("find redid error") -> {
              toastRefreshTokenFailedIfCurrent(
                "refresh token 系统内部调用失败", expectedSession, expectedToken,
              )
            }

            failureBean.errcode == 10010 && failureBean.errmessage.contains("sign in emerge error") -> {
              toastRefreshTokenFailedIfCurrent(
                "refresh token 签发失败", expectedSession, expectedToken,
              )
            }

            else -> {
              val body = throwable.response.bodyAsText()
              toastRefreshTokenFailedIfCurrent(
                "refresh token 未知错误\nhttp status=${throwable.response.status}\nbody=$body",
                expectedSession,
                expectedToken,
              )
            }
          }
        } else {
          val body = throwable.response.bodyAsText()
          toastRefreshTokenFailedIfCurrent(
            "未知错误\nhttp status=${throwable.response.status}\nbody=$body",
            expectedSession,
            expectedToken,
          )
        }
      }

      is ApiException -> {
        if (throwable.status == 20004) {
          expireRefreshTokenIfCurrent(
            "refresh, status=20004", expectedSession, expectedToken,
          )
        } else {
          toastRefreshTokenFailedIfCurrent(throwable.message, expectedSession, expectedToken)
        }
      }

      else -> toastRefreshTokenFailedIfCurrent(throwable.message, expectedSession, expectedToken)
    }
  }

  @Serializable
  class RequestTokenFailureBean(
    @SerialName("status")
    val status: Int = 0,
    @SerialName("errcode")
    val errcode: Int = 0,
    @SerialName("errmessage")
    val errmessage: String = "",
  )

  /** 仅在冻结登录 scope 仍有效时展示 refresh 失败提示。 */
  private fun toastRefreshTokenFailedIfCurrent(
    msg: String?,
    expectedSession: AccountSession,
    expectedToken: TokenBean,
  ) {
    val accountScope = AccountService.accountCoroutineScopeFor(expectedSession) ?: return
    accountScope.launch(Dispatchers.Main.immediate) {
      if (AccountService.accountCoroutineScopeFor(expectedSession) !== accountScope) return@launch
      if (!AccountService.isCurrentTokenLifecycle(expectedSession, expectedToken)) return@launch
      toastRefreshTokenFailed(msg)
    }
  }

  /**
   * refreshToken 失效的最终副作用通过 publication guard 条件登出。
   *
   * 提示与导航绑定新发布的 Logout scope；如果用户在主线程处理前完成新登录，该 scope 会被取消，旧事件不会再
   * 操作新 generation。条件登出本身也是幂等门禁，只有第一个仍匹配的响应能够发布 Logout session。
   */
  private fun expireRefreshTokenIfCurrent(
    msg: String,
    expectedSession: AccountSession,
    expectedToken: TokenBean,
  ) {
    val logoutSession =
      AccountService.logoutIfCurrentTokenLifecycle(expectedSession, expectedToken) ?: return
    val logoutScope = AccountService.accountCoroutineScopeFor(logoutSession) ?: return
    logoutScope.launch(Dispatchers.Main.immediate) {
      if (AccountService.accountCoroutineScopeFor(logoutSession) !== logoutScope) return@launch
      toastLong("登录已过期，请重新登录\n原因：$msg")
      // toast 与导航之间再次复核，避免提示期间发生的新登录被旧事件带回登录页。
      if (AccountService.accountCoroutineScopeFor(logoutSession) !== logoutScope) return@launch
      ILoginService::class.impl().jumpToLoginPage()
    }
  }

  private var lastToastRequestFailureTime = 0.milliseconds

  private fun toastRefreshTokenFailed(msg: String?) {
    if (isDebug()) {
      val nowTime = Clock.System.now().toEpochMilliseconds().milliseconds
      if (nowTime - lastToastRequestFailureTime > 1.minutes) {
        lastToastRequestFailureTime = nowTime
        toastLong(msg)
      }
    }
  }
}

/**
 * 按 AccountSession 与源 TokenBean identity 合并 refresh Deferred。
 *
 * 新 generation 可以立即创建自己的请求；旧 Deferred 完成时只清除自身，不能覆盖或清理新账号的共享槽。
 */
internal class TokenRefreshDeferredRegistry {
  private val guard = SynchronizedObject()
  private var deferred: Deferred<String>? = null
  private var session: AccountSession? = null
  private var sourceToken: TokenBean? = null

  fun getOrCreate(
    expectedSession: AccountSession,
    expectedToken: TokenBean,
    create: () -> Deferred<String>,
  ): Deferred<String> {
    synchronized(guard) {
      deferred?.takeIf { existing ->
        session === expectedSession && sourceToken === expectedToken && !existing.isCompleted
      }?.let { return it }
    }

    // create 可能读取 AccountService publication guard，必须放在 registry guard 外避免锁序反转。
    val candidate = create()
    var installed = false
    val result = synchronized(guard) {
      deferred?.takeIf { existing ->
        session === expectedSession && sourceToken === expectedToken && !existing.isCompleted
      } ?: candidate.also {
        deferred = it
        session = expectedSession
        sourceToken = expectedToken
        installed = true
      }
    }
    if (!installed) {
      candidate.cancel()
      return result
    }
    candidate.invokeOnCompletion {
      synchronized(guard) {
        if (deferred === candidate) {
          deferred = null
          session = null
          sourceToken = null
        }
      }
    }
    candidate.start()
    return candidate
  }
}
