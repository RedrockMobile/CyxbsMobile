package com.cyxbs.pages.sport.service

/**
 * 体育打卡 feed 在 iOS 端的跳转能力契约
 *
 * commonMain 的 [jumpSportDetail] actual 通过 KtProvider 拿到本接口实现，
 * 由 cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 适配到 iosApp 的
 * KmpInterfaceImpl，再由后者 push 原生 SportAttendanceViewController。
 *
 * 这种「按能力维度的小接口 + 由 IOSKmpInterfaceLink 统一适配」是项目里 iOS 平台能力
 * 反向注入的标准模式，参见 IOSHomeViewPager / IOSToast。
 */
interface SportIosPlatform {

  /** push 体育打卡详情页（iOS 原生 SportAttendanceViewController） */
  fun jumpSportDetail()
}
