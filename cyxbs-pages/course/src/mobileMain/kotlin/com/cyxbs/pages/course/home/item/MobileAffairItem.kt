package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.data.OverlayData
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/25
 */
class MobileAffairItem(
  override val page: Int, // 为 0 则表示整学期，否则表示第几周
) : CourseItem {

  @ImplProvider
  companion object : AffairItemFactory {
    override fun createAffairItem(page: Int): CourseItem {
      return MobileAffairItem(page)
    }
  }

  override val key: String = hashCode().toString()

  override val dayOfWeek: DayOfWeek
    get() = TODO("Not yet implemented")
  override val beginTime: MinuteTime
    get() = TODO("Not yet implemented")
  override val finalTime: MinuteTime
    get() = TODO("Not yet implemented")

  @Composable
  override fun Content(modifier: Modifier, overlap: OverlayData, timeline: CourseTimeline) {
    TODO("Not yet implemented")
  }
}