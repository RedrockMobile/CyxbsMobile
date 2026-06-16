package com.cyxbs.pages.todo.service

/**
 * 邮子清单 feed 在 iOS 端的跳转能力契约
 *
 * 由 cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 通过 KtProvider 注入，
 * 最终落到 iosApp 的 KmpInterfaceImpl，push 原生 ToDoVC。
 *
 * 与 [com.cyxbs.pages.sport.service.SportIosPlatform] 同款模式：按能力维度的小接口 +
 * 由 IOSKmpInterfaceLink 统一适配，参见 IOSHomeViewPager / IOSToast。
 */
interface TodoIosPlatform {

  /** push 邮子清单主页（iOS 原生 ToDoVC） */
  fun jumpTodoMain()
}
