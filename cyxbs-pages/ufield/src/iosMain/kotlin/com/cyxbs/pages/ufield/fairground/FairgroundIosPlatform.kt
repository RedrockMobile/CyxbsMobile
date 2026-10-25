package com.cyxbs.pages.ufield.fairground

/**
 * 邮乐园在 iOS 端的跳转能力契约
 *
 * 与 Step 1/2/2.5/5 同款模式：cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink
 * 通过 KtProvider 实现本接口，最终落到 iosApp 的 KmpInterfaceImpl push 原生 VC。
 */
interface FairgroundIosPlatform {

  /** push 答疑广场（iOS 原生 QAMainVC，对齐原 RYCarnieViewController.tapStatementEntry） */
  fun jumpQaEntry()

  /** push 活动布告栏（iOS 原生 ActivityMainViewController，对齐原 tapEventEntry） */
  fun jumpUfieldMainEntry()
}
