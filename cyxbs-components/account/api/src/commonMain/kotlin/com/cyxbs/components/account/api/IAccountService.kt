package com.cyxbs.components.account.api

import com.cyxbs.components.init.appCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/11
 */
interface IAccountService {

  /**
   * 当前账户状态
   */
  val state: StateFlow<AccountState>

  val stuNum: String?
    get() = (state.value as? AccountState.Login)?.stuNum

  val stuNumFlow: Flow<String?>
    get() = state.map { (it as? AccountState.Login)?.stuNum }.distinctUntilChanged()

  // 用户信息
  val userInfo: UserInfo?
    get() = (state.value as? AccountState.Login)?.userInfo?.value

  // 跟账号状态进行绑定的协程作用域
  // 登陆、登出都会触发协程作用域的 cancel
  val accountCoroutineScope: CoroutineScope

  /**
   * 是否处于登录状态
   */
  fun isLogin(): Boolean = state.value is AccountState.Login

  /**
   * 是否处于游客模式
   */
  fun isTouristMode(): Boolean = state.value is AccountState.Tourist

  // 在登陆时执行一次
  fun doOnLogin(action: (AccountState.Login) -> Unit): Job {
    return appCoroutineScope.launch {
      val login = state.filterIsInstance<AccountState.Login>().first()
      action.invoke(login)
    }
  }

  // 在登出时执行一次
  fun doOnLogout(action: (AccountState.Logout) -> Unit): Job {
    return appCoroutineScope.launch {
      val logout = state.filterIsInstance<AccountState.Logout>().first()
      action.invoke(logout)
    }
  }
}

sealed interface AccountState {
  data class Login(
    val stuNum: String,
  ) : AccountState {
    // 用户信息
    val userInfo: MutableStateFlow<UserInfo?> = MutableStateFlow(null)
  }
  data class Logout(
    val login: Login?
  ) : AccountState
  data object Tourist : AccountState
}

@Serializable
data class UserInfo(
  @SerialName("gender")
  val gender: String, // 性别
  @SerialName("photo_src")
  val photoSrc: String, // 个人头像
  @SerialName("stunum")
  val stuNum: String, // 学号
  @SerialName("username")
  val username: String, // 用户名字
  @SerialName("nickname")
  val nickname: String, // 昵称
  @SerialName("college")
  val college: String, // 学院信息
  @SerialName("introduction")
  val introduction: String? = null, // 签名
  @SerialName("phone")
  val phone: String? = null, // 电话
  @SerialName("qq")
  val qq: String? = null, // QQ 号
)