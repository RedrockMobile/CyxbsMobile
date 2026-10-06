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

  /** 当前账户生命周期；每次登录、登出和游客切换都会发布新会话。 */
  val session: StateFlow<AccountSession>

  /** 当前 session.state 的只读投影，供 UI 和旧调用方使用；同步读取应与 session 保持一致。 */
  val state: StateFlow<AccountState>

  val stuNum: String?
    get() = (state.value as? AccountState.Login)?.stuNum

  val stuNumFlow: Flow<String?>
    get() = state.map { (it as? AccountState.Login)?.stuNum }.distinctUntilChanged()

  // 用户信息
  val userInfo: UserInfo?
    get() = (state.value as? AccountState.Login)?.userInfo?.value

  /** 当前 session 的作用域；身份切换会取消旧实例，需绑定任务时优先保存 session。 */
  val accountCoroutineScope: CoroutineScope
    get() = session.value.accountCoroutineScope

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

/** 登录状态的业务分类；一次状态可以出现在多次独立的账户生命周期中。 */
sealed interface AccountState {
  /** 每次登录创建新对象，确保 StateFlow 能通知同学号重新登录并清空旧资料。 */
  class Login(
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
