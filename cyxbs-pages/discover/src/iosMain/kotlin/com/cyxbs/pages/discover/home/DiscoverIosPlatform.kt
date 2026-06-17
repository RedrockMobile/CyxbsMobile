package com.cyxbs.pages.discover.home

/**
 * 发现首页 Header / Banner / 教务在线在 iOS 端的跳转能力契约
 *
 * 由 cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 通过 KtProvider 注入，
 * 最终落到 iosApp 的 KmpInterfaceImpl 调用原生 push / present / open。
 *
 * 与 Step 1/2/2.5 同款模式（参见 SportIosPlatform / TodoIosPlatform /
 * DiscoverFunctionsIosPlatform）。
 */
interface DiscoverIosPlatform {

  /** push 消息中心（iOS 原生 MineMessageVC） */
  fun launchNotification()

  /** present 签到页（iOS 原生 CheckInViewController，全屏 modal） */
  fun jumpCheckIn()

  /** 跳转教务在线新闻列表（iOS 原版功能已停服，留 toast 兜底） */
  fun jumpJwNewsList()

  /** 跳转教务在线某条新闻详情（iOS 原版功能已停服，留 toast 兜底） */
  fun jumpJwNewsItem(newId: String)

  /** Banner 点击：把外链交给系统 Safari 打开（UIApplication.shared.open） */
  fun onBannerClick(pictureGotoUrl: String, keyword: String)
}
