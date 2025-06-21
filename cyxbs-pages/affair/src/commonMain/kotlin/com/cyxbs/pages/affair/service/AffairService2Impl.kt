package com.cyxbs.pages.affair.service

import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.affair.model.AffairRepository2
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/19
 */
@ImplProvider
object AffairService2Impl : IAffairService2 by AffairRepository2