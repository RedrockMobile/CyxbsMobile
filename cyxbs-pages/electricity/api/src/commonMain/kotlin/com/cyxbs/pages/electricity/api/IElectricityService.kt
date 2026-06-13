package com.cyxbs.pages.electricity.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 电费 feed 内容供给方
 *
 * 由 electricity 模块通过 `@ImplProvider` 提供具体实现，
 * 业务侧通过 `IElectricityService::class.impl()` 获取后将 [ElectricityFeed]
 * 嵌入到自己的页面中，无需直接依赖 electricity 模块。
 */
interface IElectricityService {

  /**
   * 电费查询卡片（用于发现页 feed 区）
   *
   * 点击卡片会弹出宿舍选择对话框，未登录时会先提示登录。
   */
  @Composable
  fun ElectricityFeed(modifier: Modifier = Modifier)
}
