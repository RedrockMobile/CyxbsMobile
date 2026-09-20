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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class CoursePageDecorationManager internal constructor(
  val courseFrame: AbstractCourseFrame,
  val decorations: List<CoursePageDecoration<*>>
) : AutoCloseable {

  /**
   * Manager 自己拥有 Frame 根任务下的数据作用域，调用方无需创建或传递 CoroutineScope。
   *
   * 该作用域不包含 Compose 的 `MonotonicFrameClock`，只能用于数据订阅、刷新和资源生命周期管理，
   * 禁止执行 `Animatable.animateTo`、`animate`、滚动动画等需要 UI 帧时钟的操作。
   */
  internal val courseCoroutineScope = CoroutineScope(
    Dispatchers.Main.immediate + SupervisorJob(courseFrame.courseFrameJob)
  )

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
  private val isClosed = atomic(false)

  init {
    try {
      decorations.forEach {
        it.itemHierarchy.bindCourseItemViewModel(this)
        it.attach(this)
      }
    } catch (throwable: Throwable) {
      try {
        close()
      } catch (closeThrowable: Throwable) {
        throwable.addSuppressed(closeThrowable)
      }
      throw throwable
    }
  }

  /**
   * 取消当前 Manager、Decoration 和 CourseItem 的全部任务，并逆序通知 Decoration 解除挂载。
   *
   * 该方法幂等；单个 [CoursePageDecoration.onDetached] 失败不会阻断其他 Decoration 的清理。
   */
  override fun close() {
    if (!isClosed.compareAndSet(expect = false, update = true)) return
    courseCoroutineScope.cancel()
    var failure: Throwable? = null
    for (index in decorations.lastIndex downTo 0) {
      val decoration = decorations[index]
      try {
        decoration.detach(this)
      } catch (throwable: Throwable) {
        if (failure == null) failure = throwable else failure.addSuppressed(throwable)
      }
    }
    failure?.let { throw it }
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
