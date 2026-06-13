package com.cyxbs.pages.sport.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 体育打卡 feed 内容供给方
 *
 * 由 sport 模块通过 `@ImplProvider` 提供具体实现，
 * 业务侧通过 `ISportService::class.impl()` 获取后将 [SportFeed]
 * 嵌入到自己的页面中，无需直接依赖 sport 模块。
 */
interface ISportService {

  /**
   * 体育打卡卡片（用于发现页 feed 区）
   *
   * 点击卡片：登录态下跳转体育打卡详情页（跳转由平台实现），未登录时先提示登录。
   */
  @Composable
  fun SportFeed(modifier: Modifier)
}
