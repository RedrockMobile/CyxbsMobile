package com.cyxbs.pages.widget.api

/**
 * 桌面小组件设置入口服务。
 *
 * 设置模块只通过该接口判断入口是否可见并发起跳转，不感知具体的小组件类型、Receiver 或
 * Android 系统版本。未提供实现的平台应隐藏入口。
 */
interface IWidgetEntryService {

  /** 当前平台是否需要在设置页展示“小组件”入口。 */
  val isSettingEntryVisible: Boolean

  /** 跳转到由 Widget 模块维护的小组件目录页。 */
  fun navigateToWidgetPage()
}
