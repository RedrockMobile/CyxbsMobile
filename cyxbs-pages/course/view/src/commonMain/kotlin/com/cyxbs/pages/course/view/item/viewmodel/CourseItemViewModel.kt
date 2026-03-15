package com.cyxbs.pages.course.view.item.viewmodel

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.course.view.frame.AbstractCourseFrame
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.modifier.BeginFinalTimeShowModifier
import com.cyxbs.pages.course.view.overlay.OverlapCover
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
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
 * @date 2025/10/19
 */
class CourseItemViewModel(
  val frame: AbstractCourseFrame,
  vararg val itemHierarchy: CourseItemHierarchy<*>,
) : BaseViewModel() {

  // 当天所有 item
  val todayListFlow = MutableStateFlow<List<CourseItemState>>(emptyList())
  // 明天所有 item
  val tomorrowListFlow = MutableStateFlow<List<CourseItemState>>(emptyList())
  // 下一节或者当前正在执行的 item
  val nextItemFlow = MutableStateFlow<CourseItemState?>(null)
  // 下一节 item 显示开始和结束时间，使用 lockHideNextItemBeginFinalTime 进行操控
  private val nextItemShowBeginFinalLock = NextItemShowBeginFinalLock()

  // key 为 dateKey = page * 7 + dayOfWeek.ordinal
  // value 为 hierarchyIndex，表示需要刷新的层级
  private val refreshDateSet = HashSet<Int>()
  private val refreshDateMapSynchronized = SynchronizedObject()

  init {
    itemHierarchy.forEach {
      it.bindCourseItemViewModel(this)
    }
  }

  fun tryRefresh(dateKey: Int) {
    synchronized(refreshDateMapSynchronized) {
      if (refreshDateSet.isEmpty()) {
        // 切换到下一次消息队列中执行刷新逻辑
        viewModelScope.launch(Dispatchers.Main) {
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
        itemHierarchy.forEach {
          it.refresh(dateKey, upperOverCover)
        }
      }
      refreshDateSet.clear()
    }
  }

  init {
    // 收集当天和明天所有 item
    frame.beginDate.filterNotNull().flatMapLatest { _ ->
      snapshotFlow { Today }
    }.mapLatest { today ->
      supervisorScope {
        launch {
          val todayPage = frame.getPage(today)
          if (todayPage == null) {
            todayListFlow.tryEmit(emptyList())
          } else {
            combine(
              itemHierarchy.map {
                it.observe(todayPage, Today.dayOfWeek)
              }
            ) { array ->
              array.toList().flatten().sortedBy { it.item.whatTime }
            }.collectLatest {
              logg("today = ${it.size}")
              todayListFlow.tryEmit(it)
            }
          }
        }
        launch {
          val tomorrow = today.plusDays(1)
          val tomorrowPage = frame.getPage(tomorrow)
          if (tomorrowPage == null) {
            tomorrowListFlow.tryEmit(emptyList())
          } else {
            combine(
              itemHierarchy.map {
                it.observe(tomorrowPage, tomorrow.dayOfWeek)
              }
            ) {
              it.toList().flatten().sortedBy { it.item.whatTime }
            }.collectLatest {
              logg("tomorrow = ${it.size}")
              tomorrowListFlow.tryEmit(it)
            }
          }
        }
      }
    }.launchIn(viewModelScope)

    // 收集下一节 item
    combine(todayListFlow, tomorrowListFlow) { today, tomorrow ->
      today to tomorrow
    }.mapLatest {
      logg("today = ${it.first.size}, tomorrow = ${it.second.size}")
      do {
        val localDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val now = localDateTime.toMinuteTimeDate().time
        val itemState = NextItemSearcher.search(it.first, now)
        if (itemState != null) {
          nextItemFlow.emit(itemState)
        } else {
          val tomorrowItemState = NextItemSearcher.search(it.second, MinuteTime(0, 0))
          logg("tomorrowItemState = $tomorrowItemState")
          nextItemFlow.emit(tomorrowItemState)
        }
        delay(1.minutes - localDateTime.second.seconds)
      } while (currentCoroutineContext().isActive)
    }.launchIn(viewModelScope)
  }

  fun lockHideNextItemBeginFinalTime(): Runnable {
    return nextItemShowBeginFinalLock.lock()
  }

  inner class NextItemShowBeginFinalLock {
    private var count = 0

    private var hideRunnable: Runnable? = null

    init {
      nextItemFlow.onEach {
        hideRunnable?.run()
        hideRunnable = it?.let {
          BeginFinalTimeShowModifier.showLock.get(it).lock()
        }
      }.launchIn(viewModelScope)
    }

    fun lock(): Runnable {
      count++
      var isUnlock = false
      hideRunnable?.run()
      return Runnable {
        if (isUnlock) return@Runnable
        isUnlock = true
        count--
        if (!isLocked()) {
          nextItemFlow.value?.let {
            hideRunnable = BeginFinalTimeShowModifier.showLock.get(it).lock()
          }
        }
      }
    }

    fun isLocked(): Boolean {
      return count > 0
    }
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
