package com.cyxbs.pages.schedule.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 日程 Feed 内容供给方。
 *
 * 由 schedule 模块通过 `@ImplProvider` 提供实现。业务侧通过 `IScheduleService::class.impl()` 获取后，
 * 可把提醒横幅和日程卡片嵌入自己的页面，无需依赖 schedule 实现模块。
 */
interface IScheduleService {

  /**
   * 发现页整个 Feed 区域上方的临期/超期提醒横幅。
   *
   * 没有符合条件的未完成事项时不占据布局空间；具体统计口径由 schedule 模块统一维护。
   */
  @Composable
  fun ScheduleUrgentBanner(modifier: Modifier)

  /**
   * 发现页使用的日程卡片。
   *
   * 展示前若干条未完成日程，点击卡片进入日程主页，点击单项进入对应详情。
   */
  @Composable
  fun ScheduleFeed(modifier: Modifier)
}
