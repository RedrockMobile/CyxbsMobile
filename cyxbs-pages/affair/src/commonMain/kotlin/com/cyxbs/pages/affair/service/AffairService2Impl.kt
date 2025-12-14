package com.cyxbs.pages.affair.service

import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.affair.repos.AffairRepository2
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
  override fun observeAffairGroupModel(): StateFlow<AffairGroupModel?> {
    return AffairRepository2.getAffairModelStateFlow()
  }
}