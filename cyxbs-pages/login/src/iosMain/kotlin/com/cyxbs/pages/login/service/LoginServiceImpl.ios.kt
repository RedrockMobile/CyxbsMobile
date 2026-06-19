package com.cyxbs.pages.login.service

import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.login.api.ILoginService
import com.cyxbs.pages.login.api.LoginNavArgument

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/7 21:11
 */
actual object LoginServicePlatform : ILoginService {
  actual override fun jumpToLoginPage() {
    // iOS 上只有一个 ComposeUIViewController，不需要 startActivity
    // 直接通过 CMP 导航系统跳转到登录页，登录成功后跳转到主页
    // ⚠️但需要注意的是 iOS 这边如果是在原生的二级页，则需要手动关闭当前 VC 才可以显示，具体页面框架如下：
    // CustomNavigationController（原生导航栈）
    // ├── MainViewController()  ← CMP 的 ComposeUIViewController（root）
    // │   └── AppNavDisplay()   ← CMP 内部导航系统（appNavBackStack）
    // │       ├── 主页（HomeNavEntry）
    // │       ├── 登录页（LoginNavEntry）
    // │       └── ... 其他 CMP 页面
    // ├── MineSettingViewController ← 原生 VC，push 到 CMP 上面
    // └── ... 其他原生 VC
    LoginNavArgument.navigate(HomeNavArgument(), clearStack = true)
  }
}