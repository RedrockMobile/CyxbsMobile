package com.cyxbs.pages.course.view.data

import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.timeline.CourseTimeline

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/10
 */
class CourseDataProviderGroup(
  vararg val providers: CourseDataProvider
) {

  private var timeline: CourseTimeline? = null

  private val weekDataPoolByPage = mutableMapOf<Int, List<CourseWeekDataPool>>()

  private val itemListeners = providers.map { provider ->
    object : CourseDataProvider.ItemListener {
      val provider = provider
      override fun onAdd(item: CourseItem) {
        val timeline = timeline ?: return
        weekDataPoolByPage[item.page]?.find { it.provider === provider }?.add(item)
        if (item.beginTime < timeline.startMinuteTime && item.dayOfWeek == timeline.beginDayOfWeek) {
          weekDataPoolByPage[item.page - 1]?.find { it.provider === provider }?.add(item)
        }
      }

      override fun onRemove(item: CourseItem) {
        val timeline = timeline ?: return
        weekDataPoolByPage[item.page]?.find { it.provider === provider }?.remove(item)
        if (item.beginTime < timeline.startMinuteTime && item.dayOfWeek == timeline.beginDayOfWeek) {
          weekDataPoolByPage[item.page - 1]?.find { it.provider === provider }?.remove(item)
        }
      }

      override fun onClear() {
        weekDataPoolByPage.forEach { entry ->
          entry.value.forEach {
            if (it.provider === provider) {
              it.clear()
            }
          }
        }
      }
    }
  }

  // 获取 page 对应的周数据，其中 page 为 0 时表示整学期
  fun getWeekDataPool(page: Int): List<CourseWeekDataPool> {
    return weekDataPoolByPage.getOrPut(page) {
      providers.map {
        CourseWeekDataPool(it, timeline!!, page)
      }.also { list ->
        list.fastForEachIndexed { i, pool ->
          pool.topWeekDataPool = list.getOrNull(i - 1)
          pool.bottomWeekDataPool = list.getOrNull(i + 1)
        }
      }
    }
  }

  // 绑定课程页面
  fun onBindCourseCompose(timeline: CourseTimeline) {
    this.timeline = timeline
    itemListeners.fastForEach {
      it.provider.addItemListener(it)
    }
  }

  // 解绑课程页面
  fun onUnbindCourseCompose() {
    itemListeners.fastForEach {
      it.provider.removeItemListener(it)
    }
    weekDataPoolByPage.clear()
    timeline = null
  }
}