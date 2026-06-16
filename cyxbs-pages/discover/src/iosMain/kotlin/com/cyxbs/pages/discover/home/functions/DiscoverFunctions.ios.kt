package com.cyxbs.pages.discover.home.functions

import com.cyxbs.components.config.service.implOrNull

/**
 * iOS 端发现页功能按钮的跳转实现。
 *
 * 对齐旧版 iOS `FinderToolsView.push(to:)` 的跳转映射：clickFindCourse / clickMap /
 * clickSchoolCar / clickEmptyRoom 已经在 commonMain 通过对应 NavArgument 走 cmp 内部
 * 导航，无需 override；剩下 5 个 click 默认是 toast("该平台未实现")，这里通过
 * [DiscoverFunctionsIosPlatform] 转发到 iosApp 的 KmpInterfaceImpl push 原生 VC。
 *
 * 实现缺失时降级为基类默认（toast 提示），避免崩溃。
 */
actual object PlatformDiscoverFunctions : DiscoverFunctions() {

  override fun clickNoClass() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpWeDate() ?: super.clickNoClass()
  }

  override fun clickSchoolCalendar() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpSchoolCalendar() ?: super.clickSchoolCalendar()
  }

  override fun clickTodo() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpTodoMain() ?: super.clickTodo()
  }

  override fun clickSport() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpSportDetail() ?: super.clickSport()
  }

  override fun clickExam() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpTestArrange() ?: super.clickExam()
  }
}
