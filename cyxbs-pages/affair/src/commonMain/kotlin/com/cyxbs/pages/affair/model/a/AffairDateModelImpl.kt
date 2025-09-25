package com.cyxbs.pages.affair.model.a

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairDateModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairDateModelImpl(
  override val idModel: AffairIdModel,
  whatTimeModelImpl: AffairWhatTimeModelImpl,
  date: Date,
) : AffairDateModel {
  override val enable = MutableStateFlow(true)
  override val whatTime = MutableStateFlow(whatTimeModelImpl)
  override val date = MutableStateFlow(date)
}