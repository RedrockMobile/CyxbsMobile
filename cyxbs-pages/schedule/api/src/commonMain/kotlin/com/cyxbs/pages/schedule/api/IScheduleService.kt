package com.cyxbs.pages.schedule.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 日程（邮子清单）feed 内容供给方
 *
 * 由 schedule 模块通过 `@ImplProvider` 提供具体实现，
 * 业务侧通过 `IScheduleService::class.impl()` 获取后将 [ScheduleFeed]
 * 嵌入到自己的页面中，无需直接依赖 schedule 模块。
 *
 * 该模块是 todo + 课表事务融合后的统一日程实现，对外取代旧的 `ITodoService`。
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
   * 日程卡片（用于发现页 feed 区）
   *
   * 展示前若干条未完成日程，点击卡片跳转日程主页、点击单项跳转编辑（跳转由平台实现）。
   */
  @Composable
  fun ScheduleFeed(modifier: Modifier)
}
