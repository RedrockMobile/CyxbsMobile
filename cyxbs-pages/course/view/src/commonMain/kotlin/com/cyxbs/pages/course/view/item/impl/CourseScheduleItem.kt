package com.cyxbs.pages.course.view.item.impl

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.color
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemDarkContentColor
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.createCourseDefaultModifierList
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import com.cyxbs.pages.schedule.api.ScheduleDefaultOccurrenceColor
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

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
    val defaultBackground = defaultScheduleTodoBackgroundColor()
    val defaultContent = defaultScheduleTodoContentColor()
    val isAffair = occurrence.kind == ScheduleOccurrenceKind.AFFAIR
    val isLinkedTodoAffair = isAffair && occurrence.isInTodoList
    // 旧版本可能给纯事务留下 categoryId；未关联清单时必须忽略该颜色，保持原生事务视觉。
    val categoryColor = occurrence.categoryColor.takeIf { !isAffair || isLinkedTodoAffair }
    val configuredBackground = categoryColor?.let {
      Color(
        if (MaterialTheme.colors.isLight) it.lightBackgroundArgb.toInt()
        else it.darkBackgroundArgb.toInt(),
      )
    }
    val configuredContent = categoryColor?.let {
      if (MaterialTheme.colors.isLight) it.lightContentArgb.toInt().color()
      else CourseItemDarkContentColor
    }
    val itemTextColor = when {
      isLinkedTodoAffair -> configuredContent ?: defaultContent
      isAffair -> LocalAppColors.current.tvLv2.dark(CourseItemDarkContentColor)
      else -> configuredContent ?: defaultContent
    }
    val resolvedBackgroundColor = configuredBackground ?: defaultBackground
    val itemBackgroundColor = if (isAffair) {
      // 通用背景绘制在 modifierList 之后；事务必须透明，否则会覆盖 drawBehind 绘制的斜纹。
      Color.Transparent
    } else {
      resolvedBackgroundColor
    }
    val affairStripeColor = if (isLinkedTodoAffair) {
      resolvedBackgroundColor
    } else {
      0xFFE4E7EC.dark(0xFF4D4B4C)
    }
    val itemModifierList = if (isAffair) {
      remember(affairStripeColor) {
        createCourseDefaultModifierList()
          .add(
            ScheduleAffairBackgroundItemModifier(
              stripeColor = affairStripeColor,
            ),
          )
      }
    } else {
      remember { createCourseDefaultModifierList() }
    }
    LayoutItemModifier.minimumVisualHeight.set(
      itemState,
      if (isDeadline) DEADLINE_VISUAL_HEIGHT else 0.dp,
    )
    CourseDefaultItemContent(
      itemState = itemState,
      topText = data.title,
      bottomText = data.description,
      // 分组配色作用于清单及关联清单后的事务；事务仍由 modifierList 叠加斜纹，保留来源辨识度。
      textColor = itemTextColor,
      // 事务保持透明底，避免通用背景覆盖 modifierList 中先绘制的斜纹。
      backgroundColor = itemBackgroundColor,
      modifierList = itemModifierList,
      onClick = onClick,
    )
  }
}

/** 默认清单使用不突出的中性灰，避免与课程的橙、红、蓝主色混淆。 */
@Composable
internal fun defaultScheduleTodoBackgroundColor(): Color =
  Color(
    if (MaterialTheme.colors.isLight) ScheduleDefaultOccurrenceColor.lightBackgroundArgb.toInt()
    else ScheduleDefaultOccurrenceColor.darkBackgroundArgb.toInt(),
  )

/** 默认清单在浅色模式使用中灰文字，深色模式统一使用白色文字。 */
@Composable
internal fun defaultScheduleTodoContentColor(): Color =
  if (MaterialTheme.colors.isLight) ScheduleDefaultOccurrenceColor.lightContentArgb.toInt().color()
  else CourseItemDarkContentColor

/**
 * 日程事务专用斜纹；关联清单后使用分组背景色绘制斜线，间隙保持透明以透出课表颜色。
 *
 * [CourseDefaultItemContent] 的通用背景位于自定义 modifier 之后，因此事务必须传入透明背景，避免
 * 覆盖本 modifier 的 `drawBehind` 结果。
 */
private data class ScheduleAffairBackgroundItemModifier(
  val stripeColor: Color,
) : CourseItemModifier {
  @Composable
  override fun createModifier(): Modifier {
    return Modifier.drawBehind {
      val lineWidth = 8.dp.toPx()
      val lineSpace = lineWidth * 1.414F
      var start = Offset(-3.dp.toPx(), lineSpace)
      var end = Offset(lineSpace, -3.dp.toPx())
      repeat(((size.width + size.height) / lineSpace / 2).roundToInt()) {
        drawLine(stripeColor, start, end, lineWidth)
        start = start.copy(y = start.y + lineSpace * 2)
        end = end.copy(x = end.x + lineSpace * 2)
      }
    }
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
    other is ScheduleItemWhatTime &&
      other.data.stableId == data.stableId &&
      // identity 相同时仍需比较展示数据，否则 reset 会沿用旧 Item，标题或关联配色无法即时刷新。
      other.data.occurrence == data.occurrence

  override fun hashCode(): Int = 31 * data.stableId.hashCode() + data.occurrence.hashCode()
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
