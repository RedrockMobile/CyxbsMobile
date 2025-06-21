package com.cyxbs.components.account.api

/**
 * account 模块允许外界特殊模块进行的编辑操作
 *
 * @author 985892345
 * @date 2025/1/11
 */
interface IAccountEditService {

  // 登录成功，由 login 模块调用
  // 允许抛出异常，外界应主动 catch
  fun onLoginSuccess(stuNum: String, token: String, refreshToken: String)

  // 登出，由 mine 模块调用
  fun onLogout()

  // 游客模式，由 login 模块调用
  fun onTouristMode()

  // 刷新用户信息，之后会刷新 IAccountService.userInfo ，由 mine 模块调用
  fun refreshInfo()
}