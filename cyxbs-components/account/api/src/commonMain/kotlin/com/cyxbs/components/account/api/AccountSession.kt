package com.cyxbs.components.account.api

import kotlinx.coroutines.CoroutineScope

/**
 * 单次账户生命周期，统一持有身份状态、登录凭据及专属协程作用域。
 *
 * AccountState 描述登录、登出或游客模式；同学号重新登录、重复进入游客模式也会创建新的 session。
 * 生命周期使用对象身份比较，避免把状态相同的两个实例合并，或复制实例后意外共享旧作用域。
 *
 * @param generation 生命周期序号，用于区分和观察账户切换。
 * @param state 此次生命周期的业务状态，账户切换时创建新对象而非修改此属性。
 * @param accountCoroutineScope 专属于此次生命周期的作用域，不应与其他 session 共用。
 * 调用方必须显式提供并管理其生命周期；应用账户服务在切换时取消旧实例。
 * @param tokenState 本次生命周期的凭据状态；无登录凭据时为 null，封装存储与刷新，刷新凭据不改变账户生命周期。
 */
class AccountSession(
  val generation: Long,
  val state: AccountState,
  val accountCoroutineScope: CoroutineScope,
  val tokenState: TokenState?,
) {
  /** 当前登录分区键；登出和游客返回 null。 */
  val accountId: String?
    get() = (state as? AccountState.Login)?.stuNum?.takeIf(String::isNotBlank)
}

