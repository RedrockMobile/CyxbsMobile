package com.cyxbs.pages.discover.home.functions

/**
 * 发现页"功能按钮区"在 iOS 端的跳转能力契约
 *
 * cmp 端 [DiscoverFunctions] 里部分 click 默认是 toast("该平台未实现")，iOS 端通过
 * KtProvider 拿到本接口实现，把它们接通到对应的原生 VC（对齐旧 FinderToolsView.push）。
 *
 * 体育打卡和邮子清单均已迁移到 CMP 页面，由 iOS 的 [PlatformDiscoverFunctions] 直接发起
 * 公共导航；这里只保留尚未迁移页面的原生跳转能力。
 */
interface DiscoverFunctionsIosPlatform {

  /** push 没课约（WeDateVC） */
  fun jumpWeDate()

  /** push 我的考试（TestArrangeViewController） */
  fun jumpTestArrange()
}
