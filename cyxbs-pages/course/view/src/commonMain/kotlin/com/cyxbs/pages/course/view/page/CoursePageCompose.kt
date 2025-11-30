package com.cyxbs.pages.course.view.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWrapper
import com.cyxbs.pages.course.view.timeline.Content
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import com.cyxbs.pages.course.view.timeline.LocalCourseScroll
import com.cyxbs.pages.course.view.timeline.LocalCourseScrollContext
import kotlinx.collections.immutable.ImmutableList

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
 */
@Composable
fun CoursePageCompose(
  page: Int,                    // 课表页数
  timeline: CourseTimeline,     // 左侧时间轴
  timelineWidth: Dp,            // 左侧时间轴宽度
  scrollPaddingValues: PaddingValues, // 滚动区域的内边距
  modifier: Modifier = Modifier,
  enableDrawNowTimeLine: Boolean = true,  // 是否绘制当前时间的线
  verticalScrollState: ScrollState = rememberScrollState(),
  decorations: ImmutableList<CoursePageDecoration>,
) {
  // 课表时间轴的绘制
  timeline.Content(
    modifier = modifier,
    timelineWidth = timelineWidth,
    enableDrawNowTimeLine = enableDrawNowTimeLine,
    verticalScrollState = verticalScrollState,
    scrollPaddingValues = scrollPaddingValues,
  ) {
    val courseScroll = LocalCourseScroll.current
    val pageContext = remember {
      LocalCoursePageContext(
        page = page,
        timeline = timeline,
        scrollContext = courseScroll,
      )
    }.apply {
      update(
        timeline = timeline,
        scrollContext = courseScroll,
      )
    }
    CompositionLocalProvider(
      LocalCoursePage provides pageContext,
    ) {
      remember(decorations) {
        // 这里采取嵌套而非并列的方式，可用于解决触摸事件的分发
        // Compose 默认是不会给兄弟节点分发触摸事件的，除非 sharePointerInputWithSiblings 返回 true
        // 嵌套的布局会更利于触摸事件的传递
        decorations.asReversed().fastFold<CoursePageDecoration, @Composable () -> Unit>(
          initial = @Composable {}
        ) { content, decoration ->
          @Composable {
            decoration.CoursePage(content)
          }
        }
      }.invoke()
    }
  }
}

class LocalCoursePageContext(
  val page: Int,
  timeline: CourseTimeline,
  scrollContext: LocalCourseScrollContext, // 滚轴 context
) {

  var timeline: CourseTimeline by mutableStateOf(timeline)
    private set

  var scrollContext: LocalCourseScrollContext by mutableStateOf(scrollContext)
    private set

  private val itemStateByItem = mutableMapOf<Any, CourseItemState>()

  private val findActionsByItem = mutableMapOf<Any, MutableList<(CourseItemState) -> Unit>>()

  fun findItemState(item: CourseItemWrapper<*>): CourseItemState? = itemStateByItem[item]

  // 用于延迟查找的方法
  // 如果超时则需要调用 onDispose 进行移除保存的 action 操作
  fun findItemState(wrapper: CourseItemWrapper<*>, action: (CourseItemState) -> Unit): OnDisposable? {
    val state = itemStateByItem[wrapper.item]
    if (state != null) {
      action(state)
      return null
    } else {
      findActionsByItem.getOrPut(wrapper.item) { mutableListOf() }.add(action)
      return OnDisposable {
        val list = findActionsByItem[wrapper.item]
        if (list != null) {
          list.remove(action)
          if (list.isEmpty()) findActionsByItem.remove(wrapper.item)
        }
      }
    }
  }

  fun putItemState(wrapper: CourseItemWrapper<*>, state: CourseItemState?) {
    if (state == null) {
      itemStateByItem.remove(wrapper.item)
      findActionsByItem.remove(wrapper.item)
    } else {
      itemStateByItem[wrapper.item] = state
      findActionsByItem.remove(wrapper.item)?.fastForEach { it(state) }
    }
  }

  fun update(
    timeline: CourseTimeline,
    scrollContext: LocalCourseScrollContext,
  ) {
    this.timeline = timeline
    this.scrollContext = scrollContext
  }

  fun interface OnDisposable {
    fun onDispose()
  }
}