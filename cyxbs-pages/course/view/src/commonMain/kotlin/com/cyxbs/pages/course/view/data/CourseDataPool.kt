package com.cyxbs.pages.course.view.data

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.config.time.prev
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemContent
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
  val provider: CourseDataProvider,
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

  var topWeekDataPool: CourseWeekDataPool? = null

  var bottomWeekDataPool: CourseWeekDataPool? = null

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

  fun add(item: CourseItem) {
    if (page == item.page) {
      get(item.dayOfWeek).add(item)
      if (item.beginTime < timeline.startMinuteTime) {
        // 小于时间轴的开始时间点，则会显示在前一天中
        get(item.dayOfWeek.prev()).add(item)
      }
    } else if (
      item.beginTime < timeline.startMinuteTime
      && page == item.page - 1
      && item.dayOfWeek == timeline.beginDayOfWeek
    ) {
      get(timeline.beginDayOfWeek.prev()).add(item)
    }
  }

  fun remove(item: CourseItem) {
    if (page == item.page) {
      get(item.dayOfWeek).remove(item)
      if (item.beginTime < timeline.startMinuteTime) {
        get(item.dayOfWeek.prev()).remove(item)
      }
    } else if (
      item.beginTime < timeline.startMinuteTime
      && page == item.page - 1
      && item.dayOfWeek == timeline.beginDayOfWeek
    ) {
      get(timeline.beginDayOfWeek.prev()).remove(item)
    }
  }

  fun clear() {
    DayOfWeek.entries.forEach {
      get(it).clear()
    }
  }

  init {
    if (page == 0) {
      // 整学期单独处理
      DayOfWeek.entries.forEach { dayOfWeek ->
        val nowDay = get(dayOfWeek)
        val preDay  = get(dayOfWeek.prev())
        provider.getDayData(0, dayOfWeek).forEach {
          nowDay.add(it)
          if (it.beginTime < timeline.startMinuteTime) {
            preDay.add(it)
          }
        }
      }
    } else {
      DayOfWeek.entries.forEach { dayOfWeek ->
        val dayPool = get(dayOfWeek)
        dayPool.addAll(provider.getDayData(page * 7 + dayOfWeek.ordinal))
        // 因为 timelineStart 的存在，当天可能会显示第二天的课程
        dayPool.addAll(provider.getDayData(page * 7 + dayOfWeek.ordinal + 1))
      }
    }
  }
}

// 课表一天的数据
@Stable
class CourseDayDataPool(
  val dayOfWeek: DayOfWeek,
  val weekDataPool: CourseWeekDataPool,
) {

  val state: MutableState<List<CourseItemContent>> = mutableStateOf(emptyList())

  private val itemSet = mutableSetOf<CourseItem>()
  private var oldTopCoveredList = emptyList<CoveredRange>()
  private var allowRefreshByItemSet = false

  // 是否已经发送了 setStateRunnable
  private var isPostRunnable = false

  // 刷新数据的 Runnable，为避免频繁刷新，设计成在下一个消息队列中才会触发
  private val setStateRunnable: Runnable = Runnable { refreshByItemSet() }
  private var runnableJob: Job? = null

  private val comparator = Comparator<CourseItem> { a, b ->
    weekDataPool.provider.compare(a, b)
  }

  // itemSet 改变时触发的刷新
  private fun refreshByItemSet() {
    if (!allowRefreshByItemSet) return
    val coveredList = oldTopCoveredList.toMutableList()
    state.value = OverlayManager.getOverlapData(
      input = itemSet,
      comparator = comparator,
      coveredList = coveredList,
    ).map {
      CourseItemContent(it)
    }
    // 重叠区域触发了刷新，需要通知下层的 Pool 进行刷新
    weekDataPool.bottomWeekDataPool
      ?.get(dayOfWeek)
      ?.refreshByCoveredList(coveredList)
  }

  // topCoveredList 改变时触发的刷新
  private fun refreshByCoveredList(topCoveredList: MutableList<CoveredRange>) {
    if (topCoveredList != oldTopCoveredList) {
      oldTopCoveredList = topCoveredList.toList() // 记录新的输入 coveredList
      state.value = OverlayManager.getOverlapData(
        input = itemSet,
        comparator = comparator,
        coveredList = topCoveredList,
      ).map {
        CourseItemContent(it)
      }
      allowRefreshByItemSet = false
      // 递归通知下一层 Pool 进行刷新
      weekDataPool.bottomWeekDataPool
        ?.get(dayOfWeek)
        ?.refreshByCoveredList(topCoveredList)
    }
  }

  internal fun add(item: CourseItem) {
    // 当天并且结束时间 > timelineStart 或者 开始时间 < timelineStart 且在明天的 item
    val allow =
      (item.dayOfWeek == dayOfWeek && item.finalTime > weekDataPool.timeline.startMinuteTime)
          || (item.beginTime < weekDataPool.timeline.startMinuteTime && item.dayOfWeek == dayOfWeek.prev())
    if (allow) {
      if (itemSet.add(item)) {
        allowRefreshByItemSet = true
        tryOverlapRunnable()
      }
    }
  }

  internal fun addAll(items: Collection<CourseItem>) {
    items.forEach { add(it) }
  }

  internal fun remove(item: CourseItem) {
    if (itemSet.remove(item)) {
      allowRefreshByItemSet = true
      tryOverlapRunnable()
    }
  }

  internal fun clear() {
    itemSet.clear()
    oldTopCoveredList = emptyList()
    allowRefreshByItemSet = false
    runnableJob?.cancel()
    state.value = emptyList()
  }

  private fun tryOverlapRunnable() {
    if (!isPostRunnable) {
      isPostRunnable = true
      runnableJob?.cancel()
      runnableJob = appCoroutineScope.launch(Dispatchers.Main) {
        // 在下一个消息中才触发执行重叠处理
        isPostRunnable = false
        setStateRunnable.run()
        runnableJob = null
      }
    }
  }
}
