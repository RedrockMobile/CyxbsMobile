package com.cyxbs.pages.login.service

import com.cyxbs.components.config.navigation.HomeArgument
import com.cyxbs.components.config.route.MAIN_ENTRY
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.login.api.ILoginService
import com.cyxbs.pages.login.api.LoginArgument

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/7 21:11
 */
actual object LoginServicePlatform : ILoginService {
  actual override fun jumpToLoginPage() {
    // 登录页是在 MainActivity 上的一个 Compose 页面
    // 所以需要先跳转到 MainActivity，然后流转 MainNavController 到登录页
    startActivity(MAIN_ENTRY)
    LoginArgument.navigate(HomeArgument, clearStack = true)
  }
}