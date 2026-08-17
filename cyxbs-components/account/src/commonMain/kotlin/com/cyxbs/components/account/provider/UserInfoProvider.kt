package com.cyxbs.components.account.provider

import com.cyxbs.components.account.AccountService
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 用户信息提供
 *
 * @author 985892345
 * @date 2025/1/18
 */
internal object UserInfoProvider {

  private const val KEY = "cyxbsmobile_user_info"

  var value = defaultSettings.getStringOrNull(KEY)?.let {
    runCatching {
      defaultJson.decodeFromString<UserInfo>(SecretTransformer.impl.secretDecrypt(it))
    }.onFailure {
      defaultSettings.remove(KEY)
    }.getOrNull()
  }
    private set

  private val refreshJobGuard = SynchronizedObject()
  private var refreshRequestId = 0L
  private var refreshJob: Job? = null

  /** 清除持久化资料、取消当前刷新，并使尚未安装的旧 refresh 调用失效。 */
  fun clear() {
    val job = synchronized(refreshJobGuard) {
      refreshRequestId += 1
      refreshJob.also { refreshJob = null }
    }
    job?.cancel()
    defaultSettings.remove(KEY)
    value = null
  }

  /**
   * 在 AccountService publication guard 已完成生命周期校验后写入资料。
   *
   * 调用方必须保证资料仍属于当前 session；本方法只负责同步持久化与内存赋值，不自行读取全局账号状态。
   */
  fun set(userInfo: UserInfo) {
    defaultSettings.putString(
      KEY,
      SecretTransformer.impl.secretEncrypt(defaultJson.encodeToString(userInfo))
    )
    value = userInfo
  }

  /**
   * 为冻结的 [expectedSession] 刷新用户资料。
   *
   * 请求绑定到对应账号 scope，切号时会主动取消；成功结果仍交由 AccountService 比较 session identity 后条件提交，
   * 以覆盖网络完成与取消并发发生的边界。同学号新 generation 同样会拒绝旧请求结果。
   */
  fun refresh(expectedSession: AccountSession) {
    // 请求序号与 session 校验在 AccountService publication guard 内登记，旧 generation 不能毒化新账号序号。
    val requestId = AccountService.beginUserInfoRefresh(expectedSession) {
      synchronized(refreshJobGuard) {
        refreshRequestId += 1
        refreshRequestId
      }
    } ?: return
    val scope = AccountService.accountCoroutineScopeFor(expectedSession) ?: return
    lateinit var job: Job
    job = scope.launch(start = CoroutineStart.LAZY) {
      runCatchingCoroutine {
        HttpClient.get("/magipoke/person/info").body<ApiWrapper<UserInfo>>()
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data
      }.onFailure {
        if (isDebug()) {
          toast("用户信息请求失败")
          logg("用户信息请求失败: " + it.stackTraceToString())
        }
      }.onSuccess {
        AccountService.commitUserInfo(expectedSession, it) {
          isCurrentRefresh(requestId, job)
        }
      }
    }
    var previousJob: Job? = null
    val installed = synchronized(refreshJobGuard) {
      if (requestId != refreshRequestId) {
        false
      } else {
        previousJob = refreshJob
        refreshJob = job
        true
      }
    }
    if (!installed) {
      job.cancel()
      return
    }
    // 先公开新句柄再注册 completion；即使 scope 已取消、回调立即执行，也能准确清除自身。
    job.invokeOnCompletion { clearRefreshJob(job) }
    previousJob?.cancel()
    job.start()
  }

  /** 在 AccountService publication guard 内调用，只读取 provider 自身状态，不得反向访问账号服务。 */
  private fun isCurrentRefresh(requestId: Long, job: Job): Boolean =
    synchronized(refreshJobGuard) {
      refreshRequestId == requestId && refreshJob === job
    }

  /** 仅允许完成的任务清除自己的句柄，避免旧任务 completion 覆盖新刷新。 */
  private fun clearRefreshJob(completedJob: Job?) {
    synchronized(refreshJobGuard) {
      if (refreshJob === completedJob) refreshJob = null
    }
  }
}
