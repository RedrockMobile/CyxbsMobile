package com.cyxbs.pages.login.viewmodel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.init.InitialManager
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.mapCatchingCoroutine
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.HttpClientNoToken
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.login.api.LoginNavArgument
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds


/**
 * .
 *
 * @author 985892345
 * @date 2024/12/31
 */
expect class LoginViewModel(argument: LoginNavArgument) : CommonLoginViewModel

/**
 * 管理登录表单、请求和成功后的导航；单例页面复用时保留输入并更新路由参数。
 *
 * @param argument 首次创建时的登录参数，后续由页面通过 [updateArgument] 同步。
 */
@Stable
abstract class CommonLoginViewModel(argument: LoginNavArgument) : BaseViewModel() {

  /** 当前登录页的参数，返回和登录完成时均以此匹配导航栈中的页面。 */
  var argument by mutableStateOf(argument)
    private set

  /**
   * 在页面重组成功后同步当前参数，不清空表单或中断进行中的登录。
   *
   * @param argument 当前渲染的登录参数；调用方应在主线程同步。
   */
  fun updateArgument(argument: LoginNavArgument) {
    this.argument = argument
  }

  val stuNum = mutableStateOf("")

  val password = mutableStateOf(TextFieldValue())

  val isCheckUserArgument = mutableStateOf(false)

  val isLoginAnim = mutableStateOf(false)

  init {
    InitialManager.cancelPrivacyAgree() // 重新登录时取消之前已保存的隐私政策同意状态
  }

  /** 校验表单并发起登录；成功后等待最短动画时长，再按最新路由参数离开登录页。 */
  fun clickLogin() {
    if (isLoginAnim.value) return
    val stuNum = stuNum.value
    val password = password.value.text
    if (!isCheckUserArgument.value) {
      toast("请先同意用户协议吧")
    } else if (stuNum.isEmpty()) {
      toast("请输入学号")
    } else if (password.length < 6) {
      toast("请检查一下密码吧，似乎有点问题")
    } else {
      isLoginAnim.value = true
      val startTime = Clock.System.now()
      launchByViewModelScope {
        try {
          if (requestLogin(stuNum, password)) {
            delay(startTime + 2.seconds - Clock.System.now()) // 网络太快会闪一下，像bug，就让它最少待两秒吧
            completeLogin()
          }
        } catch (e: Exception) {
          logg("login error: ${e.stackTraceToString()}")
        } finally {
          isLoginAnim.value = false
        }
      }
    }
  }

  /**
   * 登录及动画等待结束后移除当前登录页；有目标则跳转，无目标且栈为空则进入首页。
   *
   * 参数必须在异步等待之后读取，避免登录期间发生过期跳转时仍用旧参数出栈。
   * 出栈与目标跳转使用同一次参数快照，调用方应在主线程执行。
   */
  internal fun completeLogin() {
    val argument = this.argument
    val targetUrl = argument.targetUrl
    argument.popBackStack()
    if (targetUrl != null) {
      AppScheme.jump(targetUrl)
    } else if (appNavBackStack.isEmpty()) {
      HomeNavArgument().navigate()
    }
    logg("clickLogin: $appNavBackStack, targetUrl = $targetUrl")
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
    }.mapCatchingCoroutine {
      val wrapper = try {
        defaultJson.decodeFromString<ApiWrapper<LoginBean>>(it)
      } catch (e: Exception) {
        throw IllegalStateException("error=${e.message}\nbody=$it", e)
      }
      wrapper.throwApiExceptionIfFail() // 如果网络请求返回了异常，则直接抛出
      wrapper.data
    }.onFailure { throwable ->
      logg("requestLogin, onLoginFailure: $throwable")
      runCatchingCoroutine { onLoginFailure(throwable) }.onFailure {
        // TODO 打开 CrashDialog
      }.getOrThrow()
    }.onSuccess {
      logg("requestLogin, onLoginSuccess")
      runCatchingCoroutine { onLoginSuccess(stuNum, it) }.onFailure {
        // TODO 打开 CrashDialog
      }.getOrThrow()
    }.isSuccess
  }

  // 登录成功的处理
  open suspend fun onLoginSuccess(username: String, bean: LoginBean) {
    IAccountEditService::class.impl().onLoginSuccess(
      stuNum = username,
      token = bean.token,
      refreshToken = bean.refreshToken,
    )
    InitialManager.tryPrivacyAgree()
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
            failureBean.status == 20003 -> toast("用户不存在")
            failureBean.status == 20004 -> toast("学号或者密码错误")
            failureBean.status == 20005 -> toast("新生数据还未录入，稍等一下吧～")
            failureBean.status == 40004 -> toast("登录过于频繁，请15分钟后再试")
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
  open fun clickForgetPassword() {}

  // 点击用户协议
  open fun clickUserAgreement() {
    ILegalNoticeService::class.impl().openUserAgreementScreen()
  }

  // 点击隐私政策
  open fun clickPrivacyPolicy() {
    ILegalNoticeService::class.impl().openPrivacyPolicyScreen()
  }

  // 点击游客模式
  fun clickTouristMode() {
    if (!isCheckUserArgument.value) {
      toast("请先同意用户协议吧")
    } else {
      enterTouristMode()
    }
  }

  // 进入游客模式
  open fun enterTouristMode() {
    // 弹出所有页面，重新回到主页
    IAccountEditService::class.impl().onTouristMode()
    appNavBackStack.clear()
    HomeNavArgument().navigate()
  }

  /** 不同意协议时移除当前登录页，使用复用页面后最新同步的导航参数。 */
  open fun clickDisagreeUserAgreement() {
    argument.popBackStack()
    // todo 这里应该直接退出 app，但需要分不同平台来处理，后续再配置
  }
}
