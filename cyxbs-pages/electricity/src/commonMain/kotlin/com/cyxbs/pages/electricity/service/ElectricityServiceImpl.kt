package com.cyxbs.pages.electricity.service

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.pages.electricity.api.IElectricityService
import com.cyxbs.pages.electricity.ui.ElectricityFeed as ElectricityFeedComposable
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * [IElectricityService] 的具体实现，注册到 KtProvider。
 *
 * 业务侧（例如 discover 模块的 feeds 列表）通过 `IElectricityService::class.impl()`
 * 拿到本对象，然后直接在 Compose 树中调用接口上的 `ElectricityFeed`。
 */
@ImplProvider
object ElectricityServiceImpl : IElectricityService {

  @Composable
  override fun ElectricityFeed(modifier: Modifier) {
    ElectricityFeedComposable(modifier = modifier)
  }
}
