package com.cyxbs.components.account.api

/**
 * 本地账户状态不能满足请求的认证条件，例如 session 已失效或必需的 token 缺失。
 *
 * 调用方可按认证失败处理；该异常不表示协程被取消，也不表示服务端判定当前凭据过期，
 * 因此不得据此自动登出或将当前 token 标记为失效。账户 scope 的真实取消仍由协程库传播。
 *
 * @param message 本次认证校验失败的具体原因。
 */
class AccountAuthenticationException(message: String) : IllegalStateException(message)
