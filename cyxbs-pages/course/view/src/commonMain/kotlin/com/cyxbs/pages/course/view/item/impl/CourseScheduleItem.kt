package com.cyxbs.pages.course.view.item.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek

/**
 * course:view 自己拥有的日程课表 Item。
 *
 * Decoration 负责生成时间和层级数据；本类只负责课表 Item 的视觉内容，并把点击与详情行为委托给
 * [PlatformScheduleItemFactory]，从而允许具体平台自行选择交互容器。
 */
class CourseScheduleItem internal constructor(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  private val data: ScheduleCourseDecorationItem,
  private val isDeadline: Boolean,
  platformItemFactory: PlatformScheduleItemFactory,
) : CourseItem(whatTime, coroutineScope) {

  init {
    // 仅复用课表的长按拖动预览；扩展保留默认落点，松手后回到原位置且不修改日程数据。
    extensions.add(SchedulePreviewMovableItemExtension)
  }

  /** Schedule API 暴露的只读 occurrence，供具体平台决定点击和详情行为。 */
  val occurrence
    get() = data.occurrence

  private companion object {
    /** 截止时间保持零分钟业务区间，仅额外提供可读、可点的视觉高度。 */
    val DEADLINE_VISUAL_HEIGHT = 20.dp
  }

  private val platform = platformItemFactory.create(this)

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper { onClick ->
      Content(onClick)
    }
  }

  /** 绘制平台无关的日程 Item；点击回调完全由 [platform] 提供。 */
  @Composable
  private fun Content(onClick: ((MinuteTimePair) -> Unit)?) {
    val accentColor = 0xFF7654C7.dark(0xFFD3C4FF)
    LayoutItemModifier.minimumVisualHeight.set(
      itemState,
      if (isDeadline) DEADLINE_VISUAL_HEIGHT else 0.dp,
    )
    CourseDefaultItemContent(
      itemState = itemState,
      topText = data.title,
      bottomText = data.description,
      textColor = accentColor,
      backgroundColor = accentColor.copy(alpha = 0.12f),
      onClick = onClick,
    )
  }
}

/**
 * 日程的只读拖动预览能力。
 *
 * 不重写 `changeWhatTime` 和目的地计算，因此拖动结束始终回弹，不会向 Schedule 写入新的日期或时间。
 */
private data object SchedulePreviewMovableItemExtension : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean = true
}

/** 时间段或截止时间点生成 [CourseScheduleItem] 所需的纯展示数据。 */
internal class ScheduleCourseDecorationItem(
  val stableId: String,
  val occurrence: ScheduleOccurrenceView,
  val page: Int,
  val dayOfWeek: DayOfWeek,
  val beginTime: MinuteTime,
  val finalTime: MinuteTime,
  val title: String,
  val description: String,
)

/** 全天日程在某一天列上的纯展示数据，不进入 CourseItemHierarchy。 */
internal class ScheduleAllDayDecorationItem(
  val stableId: String,
  val occurrence: ScheduleOccurrenceView,
  val page: Int,
  val dayIndex: Int,
  val title: String,
)

/** 日程在 ItemHierarchy 中的稳定时间描述。 */
internal class ScheduleItemWhatTime(
  private val data: ScheduleCourseDecorationItem,
  private val isDeadline: Boolean,
  private val platformItemFactory: PlatformScheduleItemFactory,
) : ItemHierarchyWhatTime<CourseScheduleItem>() {

  override val now = MutableStateFlow<CourseItemWhatTime.Fixed>(
    CourseItemWhatTime.Fixed(
      page = data.page,
      dayOfWeek = data.dayOfWeek,
      beginTime = data.beginTime,
      finalTime = data.finalTime,
    ),
  )

  override fun createItem(coroutineScope: CoroutineScope): CourseScheduleItem =
    CourseScheduleItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      data = data,
      isDeadline = isDeadline,
      platformItemFactory = platformItemFactory,
    )

  override fun equals(other: Any?): Boolean =
    other is ScheduleItemWhatTime && other.data.stableId == data.stableId

  override fun hashCode(): Int = data.stableId.hashCode()
}

/** 全天背景中的单条日程；平台包装决定点击后的详情容器。 */
class ScheduleAllDayItem internal constructor(
  internal val data: ScheduleAllDayDecorationItem,
  platformItemFactory: PlatformScheduleItemFactory,
) {
  val occurrence
    get() = data.occurrence

  internal val platform = platformItemFactory.create(this)
}

/** 具体课表平台为 Schedule Item 提供点击与详情行为。 */
interface PlatformScheduleItemFactory {
  /** 为时间段或截止时间 Item 创建平台行为。 */
  fun create(item: CourseScheduleItem): PlatformScheduleCourseItem

  /** 为全天背景 Item 创建平台行为。 */
  fun create(item: ScheduleAllDayItem): PlatformScheduleAllDayItem
}

/** 时间段/截止时间 Item 的平台包装，通常在此注入点击详情与 BottomSheet 扩展。 */
interface PlatformScheduleCourseItem {
  @Composable
  fun CourseItemContentWrapper(
    content: @Composable (onClick: ((MinuteTimePair) -> Unit)?) -> Unit,
  )
}

/** 全天 Item 的平台包装；全天没有 CourseItemState，因此由平台自行选择详情容器。 */
interface PlatformScheduleAllDayItem {
  @Composable
  fun AllDayItemContentWrapper(
    content: @Composable (onClick: (() -> Unit)?) -> Unit,
  )
}
