package com.cyxbs.pages.course.view

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.decoration.CoursePageDecorationManager
import com.cyxbs.pages.course.view.page.CoursePageCompose
import com.cyxbs.pages.course.view.page.CourseWeekCompose
import com.cyxbs.pages.course.view.timeline.CourseTimeline
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/**
 * 课表框架
 *
 * @author 985892345
 * @date 2025/9/22
 */
@Stable
abstract class AbstractCourseFrame : AutoCloseable {

  companion object {

    val Local = staticCompositionLocalOf<AbstractCourseFrame> { error("未设置 AbstractCourseFrame") }

    @get:Composable
    val current: AbstractCourseFrame
      get() = Local.current
  }

  /** Frame 的根协程任务；页面临时离开 Composition 时不会停止，真正销毁 Frame 时统一取消。 */
  internal val courseFrameJob = SupervisorJob()

  /**
   * Frame 内部数据生命周期，不向外部调用方暴露，避免 ViewModel 或 Composable 传递 CoroutineScope。
   */
  private val courseFrameScope = CoroutineScope(Dispatchers.Main.immediate + courseFrameJob)

  /** 启动与整个 Frame 同生命周期的任务，适合账号观察、主页 Header 等跨 Manager 数据。 */
  protected fun launchInCourseFrameScope(block: suspend CoroutineScope.() -> Unit): Job =
    courseFrameScope.launch(block = block)

  private val decorationManagerState = mutableStateOf<CoursePageDecorationManager?>(null)

  /** 当前课表的数据与绘制层级管理器，由 [updateCoursePageDecorations] 统一创建。 */
  val decorationManager: CoursePageDecorationManager
    get() = checkNotNull(decorationManagerState.value) { "尚未配置课表 Decoration" }

  // 课表时间轴
  open val timeline: CourseTimeline = CourseTimeline()

  // 课表起始日期，如果为 null 则不会显示号数
  open val beginDate: StateFlow<Date?> = SchoolCalendar.observeFirstMonDayNullable()

  // 课表 HorizontalPager 状态
  open val pagerState: PagerState by lazy {
    PagerState(initialPage) { maxPage }
  }

  // 课表最大周数
  open val maxWeek: Int = defaultSettings.getInt("课表最大周数", 21)

  // 最大显示页数
  open val maxPage: Int get() = maxWeek + 1 // 因为第一页为整学期页，所以加1

  // 课表初始页，按 beginDate 自动计算(如果有值)，超出 maxPage 时默认显示第一页
  open val initialPage: Int
    get() = getPage(TodayNoEffect, 0) ?: 0

  // 当前日期是课表的第几周
  // 注意存在负数的情况，0表示开学前的一周
  open fun getWeekNum(date: Date): Int? {
    val realBeginDate =
      beginDate.value?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal) ?: return null
    return realBeginDate.daysUntil(date) / 7 + 1
  }

  // 指定日期对应页面数
  fun getPage(date: Date, valueWhenGreaterThanMaxPage: Int? = null): Int? {
    val page = getWeekNum(date) ?: return null
    if (page < 1) return null
    return if (page >= maxPage) valueWhenGreaterThanMaxPage else page
  }

  // 滚动到指定日期的页面
  suspend fun animateScrollToDate(date: Date) {
    val newPage = getPage(date) ?: return
    if (newPage == pagerState.currentPage) return
    pagerState.animateScrollToPage(
      page = newPage,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
  }

  // 获取指定页面是第几周，如果非周数时返回 null，比如整学期
  fun getWeekNumByPage(page: Int): Int? {
    return if (page in 1 ..maxWeek) page else null
  }

  /**
   * 为主页和独立课表统一提供 Frame、Manager CompositionLocal。
   *
   * 读取 [decorationManagerState] 会在 Manager 被替换后自动触发内容重组。
   */
  @Composable
  protected fun CourseFrameComposition(content: @Composable () -> Unit) {
    val manager = decorationManager
    CompositionLocalProvider(
      Local provides this,
      CoursePageDecorationManager.Local provides manager,
      content = content,
    )
  }

  /**
   * 替换当前课表的全部 Decoration。
   *
   * 新 Manager 使用 Frame 根任务下的独立 SupervisorJob；替换时旧数据订阅会立即取消，而 Frame 级任务继续运行。
   */
  protected fun updateCoursePageDecorations(
    vararg decorations: CoursePageDecoration<*>,
  ) {
    decorationManagerState.value?.close()
    decorationManagerState.value = CoursePageDecorationManager(
      courseFrame = this,
      decorations.toList(),
    )
  }

  /** 真正销毁课表 Frame，并取消它持有的全部数据订阅；重复调用是安全的。 */
  override fun close() {
    try {
      decorationManagerState.value?.close()
    } finally {
      // 即使某个 Decoration 的自定义清理抛出异常，也必须取消 Frame 级任务。
      courseFrameScope.cancel()
    }
  }
}

@Composable
fun AbstractCourseFrame.HomeCoursePageContent(
  page: Int,
) {
  val pageBeginDate = beginDate.collectAsState().value?.plusWeeks(page - 1)
    ?.weekBeginDate?.plusDays(timeline.beginDayOfWeek.ordinal)
  val timelineWidth = 40.dp
  val scrollPaddingValues = PaddingValues(top = 4.dp, bottom = 16.dp)
  val showShadowDayOfWeek by rememberDerivedStateOfStructure(pageBeginDate) {
    if (page == 0) Today.dayOfWeek else {
      pageBeginDate?.daysUntil(Today)?.takeIf { it in 0..6 }?.let { Today.dayOfWeek }
    }
  }
  Column(
    modifier = Modifier.drawTodayShadow(
      showShadowDayOfWeek = showShadowDayOfWeek,
      beginDayOfWeek = timeline.beginDayOfWeek,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues
    )
  ) {
    CourseWeekCompose(
      weekBeginDate = if (page == 0) null else pageBeginDate, // 整学期页不显示日期
      beginDayOfWeek = timeline.beginDayOfWeek,
      showShadowDayOfWeek = showShadowDayOfWeek,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues,
    )
    CoursePageCompose(
      page = page,
      timeline = timeline,
      timelineWidth = timelineWidth,
      scrollPaddingValues = scrollPaddingValues,
      decorations = CoursePageDecorationManager.current.decorations.toImmutableList(),
    )
  }
}

// 绘制课表今日的背景阴影
@Composable
private fun Modifier.drawTodayShadow(
  showShadowDayOfWeek: DayOfWeek?, // 星期几展示今日阴影
  beginDayOfWeek: DayOfWeek,
  timelineWidth: Dp,
  scrollPaddingValues: PaddingValues,
): Modifier {
  showShadowDayOfWeek ?: return this
  val todayIndex = (showShadowDayOfWeek.ordinal - beginDayOfWeek.ordinal + 7) % 7
  val color = 0x93E8F0FC.dark(0x26000101)
  return drawBehind {
    val paddingStart = scrollPaddingValues.calculateStartPadding(layoutDirection).toPx()
    val paddingEnd = scrollPaddingValues.calculateEndPadding(layoutDirection).toPx()
    val width = (size.width - timelineWidth.toPx() - paddingStart - paddingEnd) / 7
    val offsetX = timelineWidth.toPx() + paddingStart + todayIndex * width
    drawRoundRect(
      color = color,
      topLeft = Offset(x = offsetX, y = 0F),
      size = Size(width = width, height = 10.dp.toPx()),
      cornerRadius = CornerRadius(x = 8.dp.toPx(), y = 8.dp.toPx())
    )
    drawRect(
      color = color,
      topLeft = Offset(x = offsetX, y = 10F),
      size = Size(width = width, height = size.height - 10.dp.toPx()),
    )
  }
}
