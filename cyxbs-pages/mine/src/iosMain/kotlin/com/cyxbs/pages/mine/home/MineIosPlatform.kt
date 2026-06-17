package com.cyxbs.pages.mine.home

/**
 * 「我的」主页在 iOS 端的跳转能力契约
 *
 * 与 Step 1/2/2.5/5 同款模式：cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink
 * 通过 KtProvider 实现本接口，最终落到 iosApp 的 KmpInterfaceImpl push 原生 VC。
 *
 * 注：[launchNotification] 与 [DiscoverIosPlatform.launchNotification] 同名同签名，
 * 行为也一致（都是 push MineMessageVC），IOSKmpInterfaceLink 中靠 Kotlin 多接口
 * 合并 override 共享单一实现。
 */
interface MineIosPlatform {

  /** push 消息中心（iOS 原生 MineMessageVC） */
  fun launchNotification()

  /** push 邮票中心（iOS 原生 StampCenterVC） */
  fun jumpStore()

  /** push 反馈中心（iOS 原生 FeedBackMainPageViewController） */
  fun jumpFeedbackCenter()

  /**
   * push 签到页（iOS 原生 CheckInViewController）
   *
   * 与 [DiscoverIosPlatform.jumpCheckIn] 是两条独立路径：原版我的页 signViewClicked
   * 是 push，原版发现页 attendanceBtnTouched 是 present 全屏 modal。
   */
  fun jumpSign()

  /** push 设置页（iOS 原生 MineSettingViewController） */
  fun jumpSetting()

  /** push 活动中心（iOS 原生 ActivityCenterVC） */
  fun jumpActivityCenter()
}
