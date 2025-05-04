package com.cyxbs.pages.course.view.data

import androidx.compose.ui.util.fastForEach
import com.cyxbs.pages.course.view.item.CourseItemModel
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.datetime.DayOfWeek

/**
 * 课表数据 ProviderGroup
 * - 管理 CourseDataProvider
 * - 转换 CourseDataProvider 数据，生成 CourseWeekDataPool
 *
 * @author 985892345
 * @date 2025/3/10
 */
class CourseDataProviderGroup(
  vararg val providers: CourseDataProvider, // 添加顺序即为显示的层级顺序，越靠前则越显示在顶层
) {

  private var timeline: CourseTimeline? = null

  private val weekDataPoolByPage = mutableMapOf<Int, CourseWeekDataPool>()

  private val itemListeners = providers.map { provider ->
    object : CourseDataProvider.ItemListener {
      val provider = provider
      override fun onAdd(item: CourseItemModel) {
        weekDataPoolByPage[item.page]?.get(item.dayOfWeek)?.tryRefresh()
      }

      override fun onRemove(item: CourseItemModel) {
        weekDataPoolByPage[item.page]?.get(item.dayOfWeek)?.tryRefresh()
      }

      override fun onClear() {
        weekDataPoolByPage.forEach { entry ->
          DayOfWeek.entries.forEach {
            entry.value.get(it).tryRefresh()
          }
        }
      }
    }
  }

  // 获取 page 对应的周数据，其中 page 为 0 时表示整学期
  fun getWeekDataPool(page: Int): CourseWeekDataPool {
    return weekDataPoolByPage.getOrPut(page) {
      CourseWeekDataPool(providers, timeline!!, page)
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