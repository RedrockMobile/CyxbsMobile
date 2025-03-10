package com.cyxbs.pages.course.view.data

import androidx.compose.ui.util.fastForEachReversed
import com.cyxbs.pages.course.view.item.CourseItem
import kotlinx.datetime.DayOfWeek

/**
 * 课表数据 Provider
 * - 与课表组件完全解耦
 *
 * @author 985892345
 * @date 2025/3/10
 */
abstract class CourseDataProvider {

  // 课程天数据，key 为 week * 7 + dayOfWeek.ordinal
  private val dayItemMap = mutableMapOf<Int, MutableSet<CourseItem>>()

  // 课程数据监听
  private val itemListenerList = mutableListOf<ItemListener>()

  // 比较两个课程的位置，越大则越显示在顶层
  abstract fun compare(a: CourseItem, b: CourseItem): Int

  // 获取整学期的课程（整学期课程为性能考虑，需单独实现）
  open fun getSemesterDayDate(dayOfWeek: DayOfWeek): Set<CourseItem> = emptySet()

  // 获取某一天的课程（week = 0 时则获取整学期课程）
  fun getDayData(week: Int, dayOfWeek: DayOfWeek): Set<CourseItem> {
    require(week >= 0) { "week 不能小于 0, week = $week" }
    val day = week * 7 + dayOfWeek.ordinal
    return getDayData(day)
  }

  // 获取某一天的课程
  fun getDayData(dayDiff: Int): Set<CourseItem> {
    require(dayDiff >= 0) { "dayDiff 不能小于 0, dayDiff = $dayDiff" }
    if (dayDiff < 7) {
      // dayDiff 取 [0, 7) 时返回整学期的课程，整学期的课程需单独处理
      return getSemesterDayDate(DayOfWeek(dayDiff + 1))
    }
    return dayItemMap[dayDiff] ?: emptySet()
  }

  // 添加课程
  fun add(item: CourseItem) {
    val day = item.page * 7 + item.dayOfWeek.ordinal
    if (dayItemMap.getOrPut(day) { mutableSetOf() }.add(item)) {
      itemListenerList.fastForEachReversed { it.onAdd(item) }
    }
  }

  // 添加课程
  fun addAll(items: Collection<CourseItem>) {
    items.forEach { add(it) }
  }

  // 移除课程
  fun remove(item: CourseItem) {
    val day = item.page * 7 + item.dayOfWeek.ordinal
    if (dayItemMap[day]?.remove(item) == true) {
      itemListenerList.fastForEachReversed { it.onRemove(item) }
    }
  }

  // 添加课程监听
  fun addItemListener(listener: ItemListener) {
    itemListenerList.add(listener)
  }

  // 移除课程监听
  fun removeItemListener(listener: ItemListener) {
    itemListenerList.remove(listener)
  }

  interface ItemListener {
    fun onAdd(item: CourseItem)
    fun onRemove(item: CourseItem)
  }
}