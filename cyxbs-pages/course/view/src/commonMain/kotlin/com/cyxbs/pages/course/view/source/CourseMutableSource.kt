package com.cyxbs.pages.course.view.source

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/28
 */
@Stable
abstract class CourseMutableSource : CourseSource {

  private val itemSet = HashSet<CourseItem>()
  private val sourceSetByTimeline = HashMap<CourseTimeline, CourseSourceSet>()

  protected fun add(item: CourseItem) {
    if (itemSet.add(item)) {
      sourceSetByTimeline.forEach {
        it.value.add(item)
      }
    }
  }

  protected fun addAll(items: Collection<CourseItem>) {
    itemSet.addAll(items)
    sourceSetByTimeline.forEach {
      it.value.addAll(items)
    }
  }

  protected fun remove(item: CourseItem) {
    if (itemSet.remove(item)) {
      sourceSetByTimeline.forEach {
        it.value.remove(item)
      }
    }
  }

  protected fun removeAll(items: Collection<CourseItem>) {
    itemSet.removeAll(items)
    sourceSetByTimeline.forEach {
      it.value.removeAll(items)
    }
  }

  protected fun contains(item: CourseItem): Boolean {
    return itemSet.contains(item)
  }

  private val comparator = Comparator<CourseItem> { a, b ->
    a.beginTime.minutesUntil(b.beginTime)
  }

  /**
   * 返回值可被观察重组
   */
  override fun getDayItems(
    beginDate: Date?,
    week: Int,
    dayOfWeek: DayOfWeek,
    timeline: CourseTimeline
  ): SnapshotStateList<CourseItem>? {
    return if (beginDate == null) null else sourceSetByTimeline.getOrPut(timeline) {
      CourseSourceSet(timeline).apply { addAll(itemSet) }
    }.getDayItems(beginDate.plusWeeks(week).plusDays((dayOfWeek.ordinal + 7 - beginDate.dayOfWeek.ordinal) % 7))
  }

  private inner class CourseSourceSet(
    val timeline: CourseTimeline
  ) {
    private val itemMap = HashMap<Date, SnapshotStateList<CourseItem>>()
    fun add(item: CourseItem) {
      val beginDate = timeline.getItemWhichDate(item.beginTime)
      itemMap.getOrPut(beginDate) { SnapshotStateList() }.add(item)
      val finalDate = timeline.getItemWhichDate(item.finalTime)
      if (beginDate != finalDate) {
        itemMap.getOrPut(finalDate) { SnapshotStateList() }.add(item)
      }
    }

    fun addAll(items: Collection<CourseItem>) {
      val map = HashMap<Date, MutableList<CourseItem>>()
      items.forEach { item ->
        val beginDate = timeline.getItemWhichDate(item.beginTime)
        map.getOrPut(beginDate) { ArrayList() }.add(item)
        val finalDate = timeline.getItemWhichDate(item.finalTime)
        if (beginDate != finalDate) {
          map.getOrPut(finalDate) { ArrayList() }.add(item)
        }
      }
      map.forEach {
        itemMap.getOrPut(it.key) { SnapshotStateList() }.addAll(it.value)
      }
    }

    fun remove(item: CourseItem) {
      val beginDate = timeline.getItemWhichDate(item.beginTime)
      itemMap[beginDate]?.remove(item)
      val finalDate = timeline.getItemWhichDate(item.finalTime)
      if (beginDate != finalDate) {
        itemMap[finalDate]?.remove(item)
      }
    }

    fun removeAll(items: Collection<CourseItem>) {
      val map = HashMap<Date, MutableList<CourseItem>>()
      items.forEach { item ->
        val beginDate = timeline.getItemWhichDate(item.beginTime)
        map.getOrPut(beginDate) { ArrayList() }.add(item)
        val finalDate = timeline.getItemWhichDate(item.finalTime)
        if (beginDate != finalDate) {
          map.getOrPut(finalDate) { ArrayList() }.add(item)
        }
      }
      map.forEach {
        itemMap[it.key]?.removeAll(it.value)
      }
    }

    fun getDayItems(day: Date): SnapshotStateList<CourseItem> {
      return itemMap.getOrPut(day) { SnapshotStateList() }
    }
  }
}

