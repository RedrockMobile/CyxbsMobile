package com.cyxbs.pages.schedule.ui.course

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence
import kotlinx.coroutines.CoroutineScope

/**
 * 课表上的一条日程 item（对标事务的 [com.cyxbs.pages.course.view.item.impl.CourseAffairItem]）。
 *
 * 与 affair 并存：用 positive 淡底 + 标题/备注，区别于事务的斜线透明底。点击回调 [onClick] 交给
 * [SchedulePageDecoration] 打开统一编辑弹窗。装饰层仅投影「时间段型」ACTIVE occurrence，其他四态仍留在主时间轴展示。
 */
class ScheduleCourseItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val occurrence: ScheduleUiOccurrence,
  private val topText: String,
  private val bottomText: String,
  private val onClick: () -> Unit,
) : CourseItem(whatTime, coroutineScope) {

  @Composable
  override fun CourseItemContent() {
    val colors = LocalAppColors.current
    CourseDefaultItemContent(
      itemState = itemState,
      topText = topText,
      bottomText = bottomText,
      textColor = colors.tvLv2,
      backgroundColor = colors.positive.copy(alpha = 0.12f),
      onClick = { onClick() },
    )
  }
}
