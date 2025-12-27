package com.cyxbs.pages.course.view.data

import androidx.compose.ui.util.fastForEachReversed
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemWrapper
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.datetime.DayOfWeek

/**
 * 课表数据 Provider
 * - 与课表组件完全解耦
 *
 * @author 985892345
 * @date 2025/3/10
 */
abstract class CourseDataProvider<T : CourseItem> : Comparator<CourseItemWrapper<T>> {

  // 课程天数据，key 为 week * 7 + dayOfWeek.ordinal
  private val dayItemMap = mutableMapOf<Int, MutableSet<CourseItemWrapper<T>>>()

  // 课程数据监听
  private val itemListenerList = mutableListOf<ItemListener>()

  private val dayItemMapLock = SynchronizedObject()
  private val itemListenerListLock = SynchronizedObject()

  // 当天课程中比较两个课程的位置，越大则越显示在顶层
  override fun compare(a: CourseItemWrapper<T>, b: CourseItemWrapper<T>): Int {
    // page 越小越在上
    val pageDiff = a.page - b.page
    return if (pageDiff != 0) -pageDiff else {
      // dayOfWeek 越小越在上
      val dayOfWeekDiff = a.dayOfWeek.ordinal - b.dayOfWeek.ordinal
      if (dayOfWeekDiff != 0) -dayOfWeekDiff else {
        // beginTime 越大越在上
        val beginTimeDiff = a.beginTime.compareTo(b.beginTime)
        if (beginTimeDiff != 0) beginTimeDiff else {
          // finalTime 越大越在上
          a.finalTime.compareTo(b.finalTime)
        }
      }
    }
  }

  // 获取某一天的课程（week = 0 时则获取整学期课程）
  fun getDayData(week: Int, dayOfWeek: DayOfWeek, compare: Boolean = false): List<CourseItemWrapper<T>> {
    require(week >= 0) { "week 不能小于 0, week = $week" }
    val day = week * 7 + dayOfWeek.ordinal
    return getDayData(dayDiff = day, compare = compare)
  }

  // 获取某一天的课程
  fun getDayData(dayDiff: Int, compare: Boolean = false): List<CourseItemWrapper<T>> {
    require(dayDiff >= 0) { "dayDiff 不能小于 0, dayDiff = $dayDiff" }
    val list = dayItemMap[dayDiff]?.toMutableList() ?: mutableListOf()
    if (compare) list.sortWith(this)
    return list
  }

  // 添加课程
  fun add(item: CourseItemWrapper<T>) {
    val day = item.page * 7 + item.dayOfWeek.ordinal
    val addSuccess = synchronized(dayItemMapLock) {
      dayItemMap.getOrPut(day) { mutableSetOf() }.add(item)
    }
    if (addSuccess) {
      synchronized(itemListenerListLock) {
        itemListenerList.fastForEachReversed { it.onAdd(item) }
      }
    }
  }

  // 添加课程
  fun addAll(items: Collection<CourseItemWrapper<T>>) {
    items.forEach { add(it) }
  }

  // 移除课程
  fun remove(item: CourseItemWrapper<T>) {
    val day = item.page * 7 + item.dayOfWeek.ordinal
    val removeSuccess = synchronized(dayItemMapLock) {
      dayItemMap[day]?.remove(item)
    }
    if (removeSuccess == true) {
      synchronized(itemListenerListLock) {
        itemListenerList.fastForEachReversed { it.onRemove(item) }
      }
    }
  }

  // 清空 item
  // ⚠️注意：全量更新会导致前后等价的 item 并不属于同一个 item，因为 item 自身作为了 Compose Key
  fun clear() {
    synchronized(dayItemMapLock) {
      dayItemMap.clear()
    }
    synchronized(itemListenerListLock) {
      itemListenerList.fastForEachReversed { it.onClear() }
    }
  }

  // 添加课程监听
  fun addItemListener(listener: ItemListener) {
    synchronized(itemListenerListLock) {
      itemListenerList.add(listener)
    }
  }

  // 移除课程监听
  fun removeItemListener(listener: ItemListener) {
    synchronized(itemListenerListLock) {
      itemListenerList.remove(listener)
    }
  }

  interface ItemListener {
    fun onAdd(item: CourseItemWrapper<*>)
    fun onRemove(item: CourseItemWrapper<*>)
    fun onClear()
  }
}