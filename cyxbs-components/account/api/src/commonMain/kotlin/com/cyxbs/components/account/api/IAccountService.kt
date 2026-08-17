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
   * 当前账户会话，也是账号生命周期门禁的权威入口。
   *
   * [AccountSession.generation] 在每次登录、登出或进入游客模式时递增；即使新旧登录学号相同，也必须发布
   * 新会话。需要冻结身份、scope 或异步回写代次的调用方应同步读取 `value`，并使用
   * [accountCoroutineScopeFor] 复核 identity。
   */
  val session: StateFlow<AccountSession>

  /**
   * 当前账户状态的兼容投影。
   *
   * 它与 [session] 由同一写方按顺序发布，但两个独立 StateFlow 不提供跨 Flow 的双向原子观察；生命周期敏感逻辑
   * 必须以 [session] 为权威入口，本属性适合 UI 状态与旧调用方。
   */
  val state: StateFlow<AccountState>

  val stuNum: String?
    get() = (state.value as? AccountState.Login)?.stuNum

  val stuNumFlow: Flow<String?>
    get() = state.map { (it as? AccountState.Login)?.stuNum }.distinctUntilChanged()

  // 用户信息
  val userInfo: UserInfo?
    get() = (state.value as? AccountState.Login)?.userInfo?.value

  /**
   * 跟当前账号生命周期绑定的兼容协程作用域；登录、登出和游客切换都会取消旧实例。
   *
   * 需要先核验账号或 generation 再启动任务的调用方不得把本属性与 [session] 分两次读取，应改用
   * [accountCoroutineScopeFor]，否则两次读取之间发生切号时可能把旧账号任务挂到新 scope。
   */
  val accountCoroutineScope: CoroutineScope

  /**
   * 仅当 [expectedSession] 仍是权威代次时返回与其绑定的协程作用域。
   *
   * 实现必须把 session 校验与 scope 读取放在同一同步边界内；账号不匹配、同学号重新登录或生命周期已结束时
   * 返回 `null`，供需要身份门禁的调用方 fail-closed。
   */
  fun accountCoroutineScopeFor(expectedSession: AccountSession): CoroutineScope?

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

/**
 * 单次账户生命周期的权威快照。
 *
 * [generation] 是进程内单调递增的会话代次；[state] 描述该代次的登录、登出或游客身份。账号相关组件应同时
 * 比较两者，避免把“同学号重新登录”误判为同一生命周期。
 */
data class AccountSession(
  val generation: Long,
  val state: AccountState,
) {
  /** 当前登录分区键；登出和游客返回 `null`。 */
  val accountId: String?
    get() = (state as? AccountState.Login)?.stuNum?.takeIf(String::isNotBlank)
}

sealed interface AccountState {
  /**
   * 单次登录生命周期对象。
   *
   * 即使 [stuNum] 相同，重新登录也必须创建并发布新 identity；因此这里刻意不使用 data class，避免 StateFlow
   * 依据学号结构相等而吞掉新对象，导致 [AccountSession.state] 与兼容 `state.value` 分叉并残留旧 userInfo。
   */
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