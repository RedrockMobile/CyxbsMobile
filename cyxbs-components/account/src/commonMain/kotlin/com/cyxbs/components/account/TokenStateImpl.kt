package com.cyxbs.components.account

import com.cyxbs.components.account.api.AccountToken
import com.cyxbs.components.account.api.TokenState
import com.cyxbs.components.account.bean.TokenBean
import com.cyxbs.components.account.provider.SecretTransformer
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClientNoToken
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 单次登录的 token 状态，统一管理缓存、有效期和刷新；不读取账户服务或 AccountSession。
 *
 * @param accountScope 所属生命周期的作用域，结束后旧刷新不能提交缓存或产生过期副作用。
 * @param initialToken 初始凭据，只有存在登录凭据时才创建本实例。
 * @param onExpired 凭据确认失效后的主线程回调，账户层负责登出与导航。
 */
internal class TokenStateImpl(
  private val accountScope: CoroutineScope,
  initialToken: AccountToken,
  private val onExpired: (TokenState, String) -> Unit,
) : TokenState {

  override val tokenFlow = MutableStateFlow(initialToken)

  override val token: AccountToken?
    get() = tokenFlow.value.takeIf { checkTokenValidity(it) }

  private val requestMutex = Mutex()
  private val refreshMutex = Mutex()

  @Volatile
  private var refreshTask: RequestTask? = null

  /**
   * 双检获取可用凭据：等待锁期间可能已完成刷新，锁内重新读取，避免刷新新 token。
   * Mutex 只串行需要等待刷新的调用，后台刷新继续复用本实例的任务。
   */
  override suspend fun getOrRefreshToken(): AccountToken {
    val snapshot = tokenFlow.value
    if (checkTokenValidity(snapshot)) return snapshot
    return requestMutex.withLock {
      val current = tokenFlow.value
      if (checkTokenValidity(current)) return@withLock current
      requestToken(current)
    }
  }

  /** 沿用提前十二小时后台刷新、最后十分钟等待刷新的有效期策略。 */
  private fun checkTokenValidity(token: AccountToken): Boolean {
    if (!isCurrentToken(token)) return false
    val now = Clock.System.now().toEpochMilliseconds().milliseconds
    val remaining = token.tokenExpiredAtMillis.milliseconds - now
    if (remaining > 12.hours) return true
    if (remaining > 10.minutes) {
      accountScope.launch { requestToken(token) }
      return true
    }
    return false
  }

  /** 仅读取凭据有效期，沿用 refresh token 提前一天过期的策略。 */
  override fun isRefreshTokenExpired(): Boolean {
    val token = tokenFlow.value
    val now = Clock.System.now().toEpochMilliseconds().milliseconds
    val remaining = token.refreshTokenExpiredAtMillis.milliseconds - now
    return remaining < 1.days // 提前 1 天过期
  }

  /** 原子标记匹配快照的凭据过期，已更新的 token 不受旧响应影响。 */
  override fun tryTokenExpired(expectedToken: AccountToken) {
    if (!isCurrentToken(expectedToken)) return
    tokenFlow.update { current ->
      if (current.hasSameCredentials(expectedToken)) {
        current.copy(tokenExpiredAtMillis = 0)
      } else current
    }
  }

  /** 过期时间可以被本地修改；只有服务端签发的凭据内容决定旧响应是否仍属于当前 token。 */
  private fun AccountToken.hasSameCredentials(expected: AccountToken): Boolean =
    stuNum == expected.stuNum && token == expected.token && refreshToken == expected.refreshToken

  /** 校验本实例的凭据快照，刷新成功后的旧响应不得操作新 token。 */
  private fun isCurrentToken(token: AccountToken): Boolean =
    tokenFlow.value.hasSameCredentials(token)

  /** 主线程确认仍为请求所用凭据后通知账户层，不处理身份切换与页面导航。 */
  override fun tryRefreshTokenExpired(msg: String, expectedToken: AccountToken) {
    if (!isCurrentToken(expectedToken)) return
    accountScope.launch(Dispatchers.Main.immediate) {
      if (!isCurrentToken(expectedToken)) return@launch
      onExpired(this@TokenStateImpl, msg)
    }
  }

  /**
   * 创建或复用完整刷新任务，在本方法内完成请求、持久化及 token 状态发布。
   *
   * 只合并并发网络刷新，不对登录加锁；完成回调只清理自己的任务，不会清除后来的刷新。
   */
  private suspend fun requestToken(token: AccountToken): AccountToken {
    val oldRefreshTask = refreshTask
    if (oldRefreshTask != null && oldRefreshTask.prevToken.hasSameCredentials(token)) {
      // 已经触发了刷新，且之前的刷新就是根据当前 token 来触发的
      return oldRefreshTask.awaitToken()
    }
    refreshMutex.withLock {
      val task = refreshTask
      if (task != null && task.prevToken.hasSameCredentials(token)) {
        // 双检锁
        return task.awaitToken()
      }
      val newTask = RequestTask(
        scope = accountScope,
        prevToken = token,
      ) { result ->
        if (tokenFlow.value.hasSameCredentials(prevToken) && accountScope.isActive) {
          // accountScope 为当前用户登录状态的协程作用域，requestToken 是在网络请求协程中触发，
          // 所以需要判断当前用户登录状态是否还有效，防止 save 覆盖掉全局数据
          result.onSuccess {
            save(it)
            tokenFlow.value = it
            // 在刷新成功后 refreshTask 不能置为 null，因为要防止 lock 外等待的协程重复进入导致创建多次 RequestTask
            // 同时一次 token 正常来说也只有一次刷新的机会，因为刷新后就是新的 token 了
          }.onFailure {
            refreshTask = null
            onRefreshFailure(token, it)
          }
        }
      }.also { refreshTask = it }
      return newTask.awaitToken()
    }
  }

  /**
   * 1. refreshToken 失败，如果没带 STU-NUM，则直接返回 status=20004
   * 2. refreshToken 失败，如果带了 STU-NUM，则会兜底签一个 token
   *  2.1. 兜底签的 token 5 天只能使用一次，重复使用则返回 http 400，errcode=10010, errmessage=emergence refused:重复的学号
   *  2.2. 如果系统内部调用失败，则返回 http 400，errcode=10010, errmessage=find redid error
   *  2.3. 如果签发失败，则返回 http 400，errcode=10010, errmessage=sign in emerge error
   *  2.4. 签发不合法，则返回 http 400，status=20004
   */
  private suspend fun onRefreshFailure(token: AccountToken, failure: Throwable) {
    if (!isCurrentToken(token)) return
    when (failure) {
      is ConnectTimeoutException, is HttpRequestTimeoutException -> {
        toastRefreshTokenFailed("refresh token 连接超时")
      }
      is ServerResponseException -> {
        toastRefreshTokenFailed(
          "refresh token 服务器错误\nhttp status=${failure.response.status}\nbody=${failure.response.bodyAsText()}",
        )
      }
      is ClientRequestException -> {
        if (failure.response.status == HttpStatusCode.BadRequest) {
          // 后端旧 refresh 接口使用 HTTP 400 返回业务错误，需读取其专用错误字段。
          val failureBean = failure.response.body<RequestTokenFailureBean>()
          when {
            failureBean.status == 20004 -> {
              toastRefreshTokenFailed("refresh token 已失效，请重新登录")
              tryRefreshTokenExpired("refresh, status=20004", token)
            }
            failureBean.errcode == 10010 && failureBean.errmessage.contains("重复的学号") -> {
              toastRefreshTokenFailed("refresh token 重签失败，请重新登录")
              tryRefreshTokenExpired("refresh, emergence refused:重复的学号", token)
            }
            failureBean.errcode == 10010 && failureBean.errmessage.contains("find redid error") -> {
              toastRefreshTokenFailed("refresh token 系统内部调用失败")
            }
            failureBean.errcode == 10010 && failureBean.errmessage.contains("sign in emerge error") -> {
              toastRefreshTokenFailed("refresh token 签发失败")
            }
            else -> toastRefreshTokenFailed(
              "refresh token 未知错误\nhttp status=${failure.response.status}\nbody=${failure.response.bodyAsText()}",
            )
          }
        } else {
          toastRefreshTokenFailed(
            "未知错误\nhttp status=${failure.response.status}\nbody=${failure.response.bodyAsText()}",
          )
        }
      }
      is ApiException -> {
        if (failure.status == 20004) {
          toastRefreshTokenFailed("refresh token 已失效，请重新登录")
          tryRefreshTokenExpired("refresh, status=20004", token)
        } else {
          toastRefreshTokenFailed(failure.message)
        }
      }
      else -> toastRefreshTokenFailed(failure.message)
    }
  }

  private var lastToastRequestFailureTime = 0.milliseconds

  /**
   * 恢复原版 Debug 错误提示的一分钟节流；msg 保留具体错误原因及服务端诊断信息。
   *
   * 主线程检查时间并更新提示记录，避免并发失败重复提示；旧生命周期或旧凭据的提示不参与节流。
   */
  private fun toastRefreshTokenFailed(msg: String?) {
    if (!isDebug()) return
    accountScope.launch(Dispatchers.Main.immediate) {
      val nowTime = Clock.System.now().toEpochMilliseconds().milliseconds
      if (nowTime - lastToastRequestFailureTime > 1.minutes) {
        lastToastRequestFailureTime = nowTime
        toastLong(msg)
      }
    }
  }

  /**
   * 单次共享刷新任务，首次 await 才启动，结果回调结束后才向等待者返回。
   *
   * @param onResult 在所属作用域中处理成功或失败，由所有者负责保存、发布及失败清理。
   */
  class RequestTask(
    val scope: CoroutineScope,
    val prevToken: AccountToken,
    val onResult: suspend RequestTask.(newTokenResult: Result<AccountToken>) -> Unit,
  ) {
    private val async = scope.async(start = CoroutineStart.LAZY) { // 先登记 RequestTask，再允许执行结果回调。
      runCatching { // 包含 CancellationException，结果回调仍由账户作用域的活性决定是否处理。
        val wrapper = HttpClientNoToken.post("/magipoke/token/refresh") {
          setBody(buildJsonObject { put("refreshToken", prevToken.refreshToken) }.toString())
          header("STU-NUM", prevToken.stuNum)
        }.body<ApiWrapper<TokenBean>>()
        wrapper.throwApiExceptionIfFail()
        wrapper.data
      }.mapCatching {
        // 转换失败也必须进入 onFailure，清理共享任务，允许后续请求重试。
        toAccountToken(bean = it)
      }.onSuccess {
        onResult.invoke(this@RequestTask, Result.success(it))
      }.onFailure {
        onResult.invoke(this@RequestTask, Result.failure(it))
      }.getOrThrow()
    }

    suspend fun awaitToken(): AccountToken {
      return async.await()
    }
  }

  /** 旧 refresh 接口在 HTTP 400 时使用的错误字段。 */
  @Serializable
  private class RequestTokenFailureBean(
    @SerialName("status") val status: Int = 0,
    @SerialName("errcode") val errcode: Int = 0,
    @SerialName("errmessage") val errmessage: String = "",
  )


  /** 沿用旧版缓存键和加密格式，已登录用户无需重新登录。 */
  companion object {
    private const val KEY = "cyxbsmobile_user_v2"
    private const val KEY_REFRESH_TOKEN_EXPIRED = "user_refresh_token_expired_time"

    // refreshToken 过期时间
    private val REFRESH_TOKEN_EXPIRED_DAYS = 45.days

    /** 启动时恢复完整凭据；损坏的缓存会清除并返回 null，不在此处发布账户状态。 */
    internal fun load(): AccountToken? = defaultSettings.getStringOrNull(KEY)?.let { encrypted ->
      runCatching {
        val bean = defaultJson.decodeFromString<TokenBean>(SecretTransformer.impl.secretDecrypt(encrypted))
        toAccountToken(bean, defaultSettings.getLong(KEY_REFRESH_TOKEN_EXPIRED, 0))
      }.onFailure { clear() }.getOrNull()
    }

    /** 持久化新签发的凭据；调用方自行发布对应 token 状态，本方法不修改运行态。 */
    internal fun save(token: AccountToken) {
      val json = defaultJson.encodeToString(TokenBean(token = token.token, refreshToken = token.refreshToken))
      defaultSettings.putString(KEY, SecretTransformer.impl.secretEncrypt(json))
      defaultSettings.putLong(KEY_REFRESH_TOKEN_EXPIRED, token.refreshTokenExpiredAtMillis)
    }

    /** 退出登录或进入游客模式时清除缓存；账户生命周期由 AccountService 切换。 */
    internal fun clear() {
      defaultSettings.remove(KEY)
      defaultSettings.putLong(KEY_REFRESH_TOKEN_EXPIRED, 0)
    }

    /** 将后端 DTO 转为完整凭据；JWT 的秒级有效期统一转换为毫秒。 */
    internal fun toAccountToken(
      bean: TokenBean,
      refreshExpiredAt: Long = (Clock.System.now().toEpochMilliseconds().milliseconds + REFRESH_TOKEN_EXPIRED_DAYS).inWholeMilliseconds
    ) = AccountToken(
      stuNum = bean.info.data.stuNum,
      token = bean.token,
      refreshToken = bean.refreshToken,
      tokenExpiredAtMillis = bean.info.exp.toLong().seconds.inWholeMilliseconds,
      refreshTokenExpiredAtMillis = refreshExpiredAt,
    )
  }
}
