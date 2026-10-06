package com.cyxbs.components.account.api

import kotlinx.coroutines.flow.StateFlow

/**
 * 单次账户生命周期内的凭据状态，封装缓存、有效期检查、刷新和请求过期响应。
 *
 * 每次登录使用独立实例；调用方只能观察凭据，不能直接写入。
 * 实现不读取 AccountSession，生命周期由创建时传入的协程作用域管理。
 *
 * @author 985892345
 * @date 2026/10/5
 */
interface TokenState {
  /** 原始凭据的只读状态；观察此属性不会触发刷新。 */
  val tokenFlow: StateFlow<AccountToken>

  /** 不等待网络的可用凭据；检查有效期，临近过期可触发后台刷新，不可用返回 null。 */
  val token: AccountToken?

  /** 双检锁获取可用凭据，必要时等待共享刷新；失败和调用方取消原样传播。 */
  suspend fun getOrRefreshToken(): AccountToken?

  /** 根据凭据有效期判断 refresh token 是否过期。 */
  fun isRefreshTokenExpired(): Boolean

  /** 标记请求快照对应的 access token 过期；凭据已更新时忽略旧响应。 */
  fun tryTokenExpired(expectedToken: AccountToken)

  /** 确认请求快照对应的 refresh token 失效后通知账户层；msg 用于提示原因。 */
  fun tryRefreshTokenExpired(msg: String, expectedToken: AccountToken)
}

/**
 * 一次签发的完整登录凭据，由所属 TokenState 持有。
 *
 * @param stuNum token 中的学号，用于校验凭据与账户身份一致。
 * @param token 请求使用的 access token。
 * @param refreshToken 刷新接口使用的凭据。
 * @param tokenExpiredAtMillis access token 的过期时间，Unix 毫秒；0 表示强制过期。
 * @param refreshTokenExpiredAtMillis refresh token 的过期时间，Unix 毫秒。
 */
data class AccountToken(
  val stuNum: String,
  val token: String,
  val refreshToken: String,
  val tokenExpiredAtMillis: Long,
  val refreshTokenExpiredAtMillis: Long,
)