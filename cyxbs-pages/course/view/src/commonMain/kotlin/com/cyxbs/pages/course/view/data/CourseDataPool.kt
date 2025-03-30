package com.cyxbs.pages.course.view.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemContent
import com.cyxbs.pages.course.view.overlay.IOverlayController
import com.cyxbs.pages.course.view.overlay.OverlayManager
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/5
 */
// 课表一周的数据
@Stable
class CourseWeekDataPool(
  val providers: Array<out CourseDataProvider>,
  val timeline: CourseTimeline,
  val page: Int,
) {
  val mon: CourseDayDataPool = CourseDayDataPool(DayOfWeek.MONDAY, this)
  val tue: CourseDayDataPool = CourseDayDataPool(DayOfWeek.TUESDAY, this)
  val wed: CourseDayDataPool = CourseDayDataPool(DayOfWeek.WEDNESDAY, this)
  val thu: CourseDayDataPool = CourseDayDataPool(DayOfWeek.THURSDAY, this)
  val fri: CourseDayDataPool = CourseDayDataPool(DayOfWeek.FRIDAY, this)
  val sat: CourseDayDataPool = CourseDayDataPool(DayOfWeek.SATURDAY, this)
  val sun: CourseDayDataPool = CourseDayDataPool(DayOfWeek.SUNDAY, this)

  fun get(dayOfWeek: DayOfWeek): CourseDayDataPool {
    return when (dayOfWeek) {
      DayOfWeek.MONDAY -> mon
      DayOfWeek.TUESDAY -> tue
      DayOfWeek.WEDNESDAY -> wed
      DayOfWeek.THURSDAY -> thu
      DayOfWeek.FRIDAY -> fri
      DayOfWeek.SATURDAY -> sat
      DayOfWeek.SUNDAY -> sun
      else -> error("???")
    }
  }

  init {
    DayOfWeek.entries.forEach {
      get(it).tryRefresh()
    }
  }
}

// 课表一天的数据
@Stable
class CourseDayDataPool(
  val dayOfWeek: DayOfWeek,
  val weekDataPool: CourseWeekDataPool,
) : IOverlayController {

  val state: MutableState<List<CourseItemContent>> = mutableStateOf(emptyList())

  // 是否已经发送了 setStateRunnable
  private var isPostRunnable = false

  // 刷新数据的 Runnable，为避免频繁刷新，设计成在下一个消息队列中才会触发
  private val setStateRunnable: Runnable = Runnable { refreshByItemSet() }

  // 将不参与覆盖其他 item 的计算
  private val ignoreCoverOther = mutableSetOf<CourseItem>()

  // itemSet 改变时触发的刷新
  private fun refreshByItemSet() {
    val itemList = weekDataPool.providers.map {
      it.getDayData(weekDataPool.page, dayOfWeek).sortedWith(it::compare)
    }.asReversed().flatten()
    state.value = OverlayManager.getSingleDayOverlapData(
      input = itemList,
      coveredList = mutableListOf(),
      ignoreCoverOther = ignoreCoverOther,
    ).map {
      CourseItemContent(it)
    }
  }

  // 尝试触发刷新，将在下一个消息进行执行
  fun tryRefresh() {
    tryOverlapRunnable()
  }

  private fun tryOverlapRunnable() {
    if (!isPostRunnable) {
      isPostRunnable = true
      appCoroutineScope.launch(Dispatchers.Main) {
        // 在下一个消息中才触发执行重叠处理
        isPostRunnable = false
        setStateRunnable.run()
      }
    }
  }

  override fun ignoreCoverOther(item: CourseItem, enable: Boolean) {
    if (enable && ignoreCoverOther.add(item)) {
      refreshByItemSet()
    } else if (!enable && ignoreCoverOther.remove(item)) {
      refreshByItemSet()
    }
  }
}
