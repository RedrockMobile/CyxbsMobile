package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.add
import com.cyxbs.pages.course.view.data.CourseWeekDataPool
import com.cyxbs.pages.course.view.item.CourseItemModel
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.page.LocalCoursePageContext.OnDisposable
import com.cyxbs.pages.course.view.timeline.Content
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/10
 */

// CoursePageCompose 下的一些 context
val LocalCoursePage = compositionLocalOf<LocalCoursePageContext> { error("未初始化") }

/**
 * @param timeline 时间轴
 * @param enableDrawNowTimeLine 是否绘制当前时间线
 * @param verticalScrollState 垂直滚动状态
 * @param decorations 绘制在课表上的装饰物，同时也能拦截触摸事件
 * @param weekDataPool 当周的数据，越靠前越展示在顶层
 */
@Composable
fun CoursePageCompose(
  timeline: CourseTimeline,
  weekDataPool: CourseWeekDataPool,
  modifier: Modifier = Modifier,
  enableDrawNowTimeLine: Boolean = true,
  verticalScrollState: ScrollState = rememberScrollState(),
  decorations: ImmutableList<CoursePageDecoration> = persistentListOf(),
) {
  val timelineWidth = 40.dp
  val scrollPaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp)
  val scrollContext = remember { mutableStateOf<LocalCourseScrollContext?>(null) }
  val pageContext = remember {
    LocalCoursePageContext(
      timeline = timeline,
      weekDataPool = weekDataPool,
      scrollContext = scrollContext,
    )
  }.apply {
    update(
      timeline = timeline,
      weekDataPool = weekDataPool,
    )
  }
  CompositionLocalProvider(
    LocalCoursePage provides pageContext,
  ) {
    Box {
      // 使用 fastFold 将 decorations 一层一层包裹起来
      // 其实添加了 asReversed()，所以先添加的 decoration 会在最外层
      decorations.asReversed().fastFold<CoursePageDecoration, @Composable () -> Unit>(
        initial = @Composable {
          // 课表时间轴的绘制
          timeline.Content(
            modifier = modifier,
            enableDrawNowTimeLine = enableDrawNowTimeLine,
            verticalScrollState = verticalScrollState,
          ) {
            scrollContext.value = LocalCourseScroll.current
            decorations.asReversed().fastFold<CoursePageDecoration, @Composable () -> Unit>(
              initial = @Composable {
                // 课程的绘制
                CourseWeekDataContent(
                  weekDataPool = weekDataPool,
                  timeline = timeline,
                )
              }
            ) { content, decoration ->
              @Composable {
                decoration.InnerCoursePage(content)
              }
            }.invoke()
          }
        }
      ) { content, decoration ->
        @Composable {
          decoration.OuterCoursePage(
            scrollPaddingValues = scrollPaddingValues,
            timelineWidth = timelineWidth,
            content = content
          )
        }
      }.invoke()
    }
  }
}

@Composable
private fun CourseWeekDataContent(weekDataPool: CourseWeekDataPool, timeline: CourseTimeline) {
  val pageContext = LocalCoursePage.current
  repeat(7) { index ->
    val dayOfWeek = timeline.beginDayOfWeek.add(index)
    val dayDataPool = weekDataPool.get(dayOfWeek)
    dayDataPool.state.collectAsState().value.fastForEach { overlay ->
      key(overlay.item.weekItemKey) {
        val itemState = remember {
          CourseItemState(
            timeline = timeline,
            overlap = overlay,
          )
        }
        SideEffect {
          itemState.update(
            timeline = timeline,
            overlap = overlay,
          )
        }
        DisposableEffect(Unit) {
          pageContext.putItemState(overlay.item, itemState)
          onDispose { pageContext.putItemState(overlay.item, null) }
        }
        overlay.item.CourseItemContent(
          itemState = itemState,
          modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(
              Constraints(
                maxWidth = constraints.maxWidth / 7,
                maxHeight = constraints.maxHeight
              )
            )
            layout(placeable.width, placeable.height) {
              placeable.placeRelative(index * placeable.width, 0)
            }
          },
        )
      }
    }
  }
}

class LocalCoursePageContext(
  timeline: CourseTimeline,
  weekDataPool: CourseWeekDataPool,
  val scrollContext: State<LocalCourseScrollContext?>, // 滚轴 context
) {

  var timeline: CourseTimeline by mutableStateOf(timeline)
    private set

  var weekDataPool: CourseWeekDataPool by mutableStateOf(weekDataPool)
    private set

  private val itemStateMap = mutableMapOf<CourseItemModel, CourseItemState>()

  private val findActions = mutableMapOf<CourseItemModel, MutableList<(CourseItemState) -> Unit>>()

  fun findItemState(item: CourseItemModel): CourseItemState? = itemStateMap[item]

  // 用于延迟查找的方法
  // 如果超时则需要调用 onDispose 进行移除保存的 action 操作
  fun findItemState(item: CourseItemModel, action: (CourseItemState) -> Unit): OnDisposable? {
    val state = itemStateMap[item]
    if (state != null) {
      action(state)
      return null
    } else {
      findActions.getOrPut(item) { mutableListOf() }.add(action)
      return OnDisposable {
        val list = findActions[item]
        if (list != null) {
          list.remove(action)
          if (list.isEmpty()) findActions.remove(item)
        }
      }
    }
  }

  fun putItemState(item: CourseItemModel, state: CourseItemState?) {
    if (state == null) {
      itemStateMap.remove(item)
      findActions.remove(item)
    } else {
      itemStateMap[item] = state
      findActions.remove(item)?.fastForEach { it(state) }
    }
  }

  fun update(
    timeline: CourseTimeline,
    weekDataPool: CourseWeekDataPool,
  ) {
    this.timeline = timeline
    this.weekDataPool = weekDataPool
  }

  fun interface OnDisposable {
    fun onDispose()
  }
}