package com.cyxbs.pages.course.view.decoration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.snapshotFlow
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.overlay.OverlapCover
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 管理课表中的所有 item，配合 [com.cyxbs.pages.course.view.item.CourseItemHierarchy] 统一刷新重叠区间
 *
 * @author 985892345
 * @date 2026/4/19
 */
class CoursePageDecorationManager(
  val courseFrame: AbstractCourseFrame,
  val courseCoroutineScope: CoroutineScope, // 当前课表组件的协程作用域
  vararg val decorations: CoursePageDecoration<*>
) {

  companion object {
    val Local = compositionLocalOf<CoursePageDecorationManager> { error("未提供") }

    val current: CoursePageDecorationManager
      @Composable
      get() = Local.current
  }

  // 当天所有 item
  val todayListFlow = MutableStateFlow<List<CourseItemState>>(emptyList())
  // 明天所有 item
  val tomorrowListFlow = MutableStateFlow<List<CourseItemState>>(emptyList())
  // 下一节或者当前正在执行的 item
  val nextItemFlow = MutableStateFlow<CourseItemState?>(null)

  // key 为 dateKey = page * 7 + dayOfWeek.ordinal
  // value 为 hierarchyIndex，表示需要刷新的层级
  private val refreshDateSet = HashSet<Int>()
  private val refreshDateMapSynchronized = SynchronizedObject()

  init {
    decorations.forEach {
      it.itemHierarchy.bindCourseItemViewModel(this)
    }
  }

  fun tryRefresh(dateKey: Int) {
    synchronized(refreshDateMapSynchronized) {
      if (refreshDateSet.isEmpty()) {
        // 切换到下一次消息队列中执行刷新逻辑
        courseCoroutineScope.launch(Dispatchers.Main) {
          refreshInternal()
        }
      }
      refreshDateSet.add(dateKey)
    }
  }

  private fun refreshInternal() {
    synchronized(refreshDateMapSynchronized) {
      refreshDateSet.forEach { dateKey ->
        val upperOverCover = mutableListOf<OverlapCover>()
        decorations.forEach {
          it.itemHierarchy.refresh(dateKey, upperOverCover)
        }
      }
      refreshDateSet.clear()
    }
  }

  ////////////////////////////
  //   寻找当天和明天的 item
  ////////////////////////////
  init {
    // 收集当天和明天所有 item
    courseFrame.beginDate.filterNotNull().flatMapLatest { _ ->
      snapshotFlow { Today }
    }.mapLatest { today ->
      supervisorScope {
        launch {
          val todayPage = courseFrame.getPage(today)
          if (todayPage == null) {
            todayListFlow.tryEmit(emptyList())
          } else {
            combine(
              decorations.map {
                it.itemHierarchy.observe(todayPage, Today.dayOfWeek)
              }
            ) { array ->
              array.toList().flatten().sortedBy { it.item.whatTime }
            }.collectLatest {
              todayListFlow.tryEmit(it)
            }
          }
        }
        launch {
          val tomorrow = today.plusDays(1)
          val tomorrowPage = courseFrame.getPage(tomorrow)
          if (tomorrowPage == null) {
            tomorrowListFlow.tryEmit(emptyList())
          } else {
            combine(
              decorations.map {
                it.itemHierarchy.observe(tomorrowPage, tomorrow.dayOfWeek)
              }
            ) {
              it.toList().flatten().sortedBy { it.item.whatTime }
            }.collectLatest {
              tomorrowListFlow.tryEmit(it)
            }
          }
        }
      }
    }.launchIn(courseCoroutineScope)

    // 收集下一节 item
    combine(todayListFlow, tomorrowListFlow) { today, tomorrow ->
      today to tomorrow
    }.mapLatest {
      do {
        val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val now = localDateTime.toMinuteTimeDate().time
        val itemState = NextItemSearcher.search(it.first, now)
        if (itemState != null) {
          nextItemFlow.emit(itemState)
        } else {
          val tomorrowItemState = NextItemSearcher.search(it.second, MinuteTime(0, 0))
          nextItemFlow.emit(tomorrowItemState)
        }
        delay(1.minutes - localDateTime.second.seconds)
      } while (currentCoroutineContext().isActive)
    }.launchIn(courseCoroutineScope)
  }

  interface NextItemSearcher {
    fun search(sortedList: List<CourseItemState>, now: MinuteTime): CourseItemState?

    companion object : NextItemSearcher {
      override fun search(sortedList: List<CourseItemState>, now: MinuteTime): CourseItemState? {
        val impl = NextItemSearcher::class.implOrNull()
        return if (impl != null) impl.search(sortedList, now) else sortedList.firstOrNull {
          it.item.whatTime.finalTime > now
        }
      }
    }
  }
}