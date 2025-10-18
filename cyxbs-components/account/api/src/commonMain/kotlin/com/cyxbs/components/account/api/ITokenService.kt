package com.cyxbs.components.account.api

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/6
 */
interface ITokenService {

  /**
   * 得到或者请求 token
   * - 如果未登录，则直接返回 null
   * - 如果 token 已请求且未过期，则返回 token
   * - 如果 token 未请求，则挂起协程进行请求，内部短时间内只会触发一次请求
   * - 如果 token 已过期，则挂起协程进行请求，内部短时间内只会触发一次请求
   * - 如果 token 请求抛错，则直接向外抛出
   */
  suspend fun getOrRequestToken(): String?

  // 提供给 ApiGenerator 使用
  fun getToken(): String?

  /**
   * refreshToken 是否过期，过期了只能重新登录
   */
  fun isRefreshTokenExpired(): Boolean

  /**
   * 主动触发 token 过期，1 分钟内只能触发一次
   */
  fun tryTokenExpired()

  /**
   * 主动触发 refreshToken 过期，跳转到登录页，30 分钟内只能触发一次
   * @param msg 触发源，将以 toast 弹出进行排查问题
   */
  fun tryRefreshTokenExpired(msg: String)
}