package com.cyxbs.pages.discover.home.functions

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument

/**
 * iOS 端发现页功能按钮的跳转实现。
 *
 * 对齐旧版 iOS `FinderToolsView.push(to:)` 的跳转映射：clickFindCourse / clickMap /
 * clickSchoolCar / clickEmptyRoom 已经在 commonMain 通过对应 NavArgument 走 cmp 内部
 * 导航，无需 override；其余原生功能通过 [DiscoverFunctionsIosPlatform] 转发到 iosApp 的
 * KmpInterfaceImpl。邮子清单已经迁移到 schedule CMP 页面，因此直接使用公共导航参数，
 * 不再回退旧原生 ToDoVC。
 *
 * 实现缺失时降级为基类默认（toast 提示），避免崩溃。
 */
actual object PlatformDiscoverFunctions : DiscoverFunctions() {

  override fun clickNoClass() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpWeDate() ?: super.clickNoClass()
  }

  override fun clickExam() {
    DiscoverFunctionsIosPlatform::class.implOrNull()?.jumpTestArrange() ?: super.clickExam()
  }
}
