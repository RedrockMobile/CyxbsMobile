package com.cyxbs.pages.todo.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 邮子清单 feed 内容供给方
 *
 * 由 todo 模块通过 `@ImplProvider` 提供具体实现，
 * 业务侧通过 `ITodoService::class.impl()` 获取后将 [TodoFeed]
 * 嵌入到自己的页面中，无需直接依赖 todo 模块。
 */
interface ITodoService {

  /**
   * 邮子清单卡片（用于发现页 feed 区）
   *
   * 展示前若干条未完成待办，点击卡片跳转邮子清单主页、点击单项跳转详情页（跳转由平台实现）。
   */
  @Composable
  fun TodoFeed(modifier: Modifier)
}
