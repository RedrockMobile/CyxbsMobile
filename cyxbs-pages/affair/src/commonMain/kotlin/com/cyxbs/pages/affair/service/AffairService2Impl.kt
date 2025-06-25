package com.cyxbs.pages.affair.service

import com.cyxbs.pages.affair.api.AffairModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.affair.model.AffairRepository2
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/19
 */
@ImplProvider
object AffairService2Impl : IAffairService2 {
  override fun observeAffairModelStateFlow(): StateFlow<AffairModel?> {
    return AffairRepository2.getAffairModelStateFlow()
  }
}