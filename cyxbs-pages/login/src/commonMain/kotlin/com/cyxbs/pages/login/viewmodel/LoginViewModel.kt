package com.cyxbs.pages.login.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.login.bean.LoginBean
import com.cyxbs.pages.login.bean.LoginFailureBean
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds


/**
 * .
 *
 * @author 985892345
 * @date 2024/12/31
 */
expect class LoginViewModel() : CommonLoginViewModel

@Stable
abstract class CommonLoginViewModel : BaseViewModel() {

  val stuNum = mutableStateOf("")

  val password = mutableStateOf("")

  val isCheckUserArgument = mutableStateOf(false)

  val isLoginAnim = mutableStateOf(false)

  // 点击登录
  fun clickLogin() {
    if (isLoginAnim.value) return
    val stuNum = stuNum.value
    val password = password.value
    if (!isCheckUserArgument.value) {
      toast("请先同意用户协议吧")
    } else if (stuNum.isEmpty()) {
      toast("请输入学号")
    } else if (password.length < 6) {
      toast("请检查一下密码吧，似乎有点问题")
    } else {
      isLoginAnim.value = true
      val startTime = Clock.System.now()
      launch {
        try {
          if (requestLogin(stuNum, password)) {
            delay(startTime + 2.seconds - Clock.System.now()) // 网络太快会闪一下，像bug，就让它最少待两秒吧
          }
        } finally {
          isLoginAnim.value = false
        }
      }
    }
  }

  // 触发网络请求
  private suspend fun requestLogin(stuNum: String, password: String): Boolean {
    return runCatchingCoroutine {
      HttpClientNoToken.post("/magipoke/token") {
        setBody(buildJsonObject {
          put("stuNum", stuNum)
          put("idNum", password)
        }.toString())
      }.bodyAsText()
    }.mapCatching {
      val wrapper = try {
        defaultJson.decodeFromString<ApiWrapper<LoginBean>>(it)
      } catch (e: Exception) {
        throw IllegalStateException("error=${e.message}\nbody=$it", e)
      }
      wrapper.throwApiExceptionIfFail() // 如果网络请求返回了异常，则直接抛出
      wrapper.data
    }.onFailure {
      runCatching { onLoginFailure(it) }.onFailure {
        // TODO 打开 CrashDialog
      }
    }.onSuccess {
      runCatching { onLoginSuccess(stuNum, it) }.onFailure {
        // TODO 打开 CrashDialog
      }
    }.isSuccess
  }

  // 登录成功的处理
  open suspend fun onLoginSuccess(username: String, bean: LoginBean) {
    IAccountEditService::class.impl().onLoginSuccess(
      stuNum = username,
      token = bean.token,
      refreshToken = bean.refreshToken,
    )
  }

  // 登录失败的处理
  open suspend fun onLoginFailure(throwable: Throwable) {
    when (throwable) {
      is ConnectTimeoutException, is HttpRequestTimeoutException -> toast("连接超时")
      is ServerResponseException -> toast("服务器错误\nhttp status=${throwable.response.status}\nbody=${throwable.response.bodyAsText()}")
      is ClientRequestException -> {
        if (throwable.response.status == HttpStatusCode.BadRequest) {
          // 在请求失败时后端会返回 http 状态码 400，这里需要单独进行解析
          val failureBean = throwable.response.body<LoginFailureBean>()
          when {
            failureBean.status == 20004 -> toast("学号或者密码错误")
            failureBean.status == 40004 -> toast("登录过于频繁，请15分钟后再试")
            failureBean.errcode == 10010 -> toast("该学号信息未注册")
            else -> toastLong("未知错误\nhttp status=${throwable.response.status}\nbody=${throwable.response.bodyAsText()}")
          }
        } else {
          toastLong("未知错误\nhttp status=${throwable.response.status}\nbody=${throwable.response.bodyAsText()}")
        }
      }

      else -> toastLong(throwable.message)
    }
  }

  // 点击忘记密码
  abstract fun clickForgetPassword()

  // 点击用户协议
  abstract fun clickUserAgreement()

  // 点击隐私政策
  abstract fun clickPrivacyPolicy()

  // 点击游客模式
  fun clickTouristMode() {
    if (!isCheckUserArgument.value) {
      toast("请先同意用户协议吧")
    } else {
      enterTouristMode()
    }
  }

  // 进入游客模式
  abstract fun enterTouristMode()

  // 不同意用户协议
  abstract fun clickDisagreeUserAgreement()
}
