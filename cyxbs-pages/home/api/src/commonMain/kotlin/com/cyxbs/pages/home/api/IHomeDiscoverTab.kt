package com.cyxbs.pages.home.api

import androidx.compose.runtime.Composable

/**
 * 主页「发现」tab 内容供给方
 *
 * 由 discover 模块通过 `@ImplProvider` 提供具体实现，home 模块只依赖该接口，
 * 从而避免直接依赖 discover 模块（移动端 / 桌面端可分别替换实现）。
 *
 * 后续可在此接口中追加 BottomNavItem 配置，以支持不同 form factor 自定义底部导航。
 */
interface IHomeDiscoverTab {

  @Composable
  fun Content()
}
