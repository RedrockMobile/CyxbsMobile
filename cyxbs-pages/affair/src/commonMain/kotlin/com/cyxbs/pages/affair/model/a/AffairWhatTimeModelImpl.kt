package com.cyxbs.pages.affair.model.a

import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairWhatTimeModelImpl(
  override val idModel: AffairIdModel,
  timePair: MinuteTimePair,
) : AffairWhatTimeModel {
  override val enable = MutableStateFlow(true)
  override val timePair = MutableStateFlow(timePair)
  override val dateList = MutableStateFlow(persistentListOf<AffairDateModelImpl>())
}