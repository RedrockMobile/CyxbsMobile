package com.cyxbs.components.account.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/6
 */
interface ITokenService {

  /**
   * 得到或者请求 token，并冻结本次请求实际附加的账号生命周期凭证。
   *
   * lease 除 [TokenLifecycleLease.token] 外不暴露内部身份；网络层必须把它原样带到 typed 响应处理阶段，不能用
   * 学号、token 字符串或响应到达时的当前账号重新构造。
   */
  suspend fun getOrRequestTokenLease(): TokenLifecycleLease?

  /**
   * 为指定的权威账号会话获取本次请求的严格 token lease。
   *
   * @param expectedSession 调用方冻结并原样传递的 [AccountSession]；实现必须使用引用相等校验，不能按学号、
   * generation 或结构相等回退到当前会话。
   * @return 与实际写入请求的源 TokenBean identity 绑定的 lease。
   * @throws CancellationException 当 expectedSession 已陈旧、被复制、已登出、当前缺少 token，或旧实现尚未支持严格
   * 生命周期校验时；默认实现会安全拒绝，绝不回退调用可空的无参入口。调用方收到取消后不得发出匿名请求。
   */
  suspend fun getOrRequestTokenLease(expectedSession: AccountSession): TokenLifecycleLease {
    throw CancellationException("strict account session token lease is unsupported")
  }

  /**
   * [getOrRequestTokenLease] 的同步桥接版本，仅供 Android ApiGenerator 使用。
   *
   * @param runBlock 将 refresh Deferred 阻塞等待为 token 字符串；返回前实现仍会复核实际提交的 TokenBean。
   */
  fun getOrRequestTokenLease2(
    runBlock: (Deferred<String>) -> String,
  ): TokenLifecycleLease?

  /**
   * 兼容旧调用方的 token 字符串入口。
   *
   * 新的认证网络请求必须使用 [getOrRequestTokenLease]，否则响应阶段无法证明副作用属于哪个生命周期。
   */
  suspend fun getOrRequestToken(): String? = getOrRequestTokenLease()?.token

  /** 兼容旧 Android 调用方的同步 token 字符串入口。 */
  fun getOrRequestToken2(runBlock: (Deferred<String>) -> String): String? =
    getOrRequestTokenLease2(runBlock)?.token

  /**
   * 处理带认证请求的业务状态码。
   *
   * 实现只能在 [lease] 仍对应当前 AccountSession identity 与实际附加的源 TokenBean identity 时处理
   * 20002/20003/20004；lease 已陈旧或不是本实现签发时必须 fail-closed。[msg] 仅用于失效提示和排查。
   */
  fun handleAuthenticatedApiStatus(
    lease: TokenLifecycleLease,
    status: Int,
    msg: String,
  )

  /**
   * 获取当前 token
   * - 如果已过期则返回 null
   * - 如果未登录则返回 null
   */
  fun getToken(): String?

  /**
   * refreshToken 是否过期，过期了只能重新登录
   */
  fun isRefreshTokenExpired(): Boolean

  /**
   * 主动触发 token 过期，30 分钟内只能触发一次。
   *
   * 这是无请求上下文的兼容入口；普通认证请求必须改用 [handleAuthenticatedApiStatus]。
   */
  fun tryTokenExpired()

  /**
   * 主动触发 refreshToken 过期，跳转到登录页，30 分钟内只能触发一次。
   *
   * 这是无请求上下文的兼容入口；普通认证请求必须改用 [handleAuthenticatedApiStatus]。
   *
   * @param msg 触发源，将以 toast 弹出进行排查问题
   */
  fun tryRefreshTokenExpired(msg: String)
}

/**
 * 单个认证请求实际附加 token 的不透明生命周期凭证。
 *
 * 调用方只能读取 [token] 写入 Authorization；AccountSession 与源 TokenBean identity 由账号模块私有实现保存，
 * 不参与序列化，也不得由网络层复制或伪造。
 */
interface TokenLifecycleLease {
  val token: String
}
