package com.cyxbs.pages.course.view.item.impl

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.color
import com.cyxbs.pages.widget.api.CourseWidgetAction
import com.cyxbs.pages.widget.api.CourseWidgetBackgroundPattern
import com.cyxbs.pages.widget.api.CourseWidgetItemStyle
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetVisibleRange
import com.cyxbs.pages.course.view.item.CourseDefaultItemContent
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemDarkContentColor
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.CourseWidgetRenderProvider
import com.cyxbs.pages.course.view.item.CourseWidgetDarkContentArgb
import com.cyxbs.pages.course.view.item.CourseWidgetSecondaryContentArgb
import com.cyxbs.pages.course.view.item.courseWidgetDialogItemId
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.createCourseDefaultModifierList
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import com.cyxbs.pages.course.view.item.modifier.CourseItemModifier
import com.cyxbs.pages.schedule.api.ScheduleDefaultOccurrenceColor
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceView
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
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
  platformItemFactory: PlatformScheduleItemFactory,
) : CourseItem(whatTime, coroutineScope), CourseWidgetRenderProvider {

  override val widgetItemId: String
    get() = "schedule:${data.stableId}"

  override val widgetDialogItemId: String
    get() = occurrence.courseWidgetDialogItemId()

  init {
    // 仅复用课表的长按拖动预览；扩展保留默认落点，松手后回到原位置且不修改日程数据。
    extensions.add(SchedulePreviewMovableItemExtension)
  }

  /** Schedule API 暴露的只读 occurrence，供具体平台决定点击和详情行为。 */
  val occurrence
    get() = data.occurrence

  private val platform = platformItemFactory.create(this)

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper { onClick ->
      Content(onClick)
    }
  }

  /** 导出日程的稳定身份、来源配色与事务斜纹，让 Widget 复用 Compose 的视觉语义。 */
  override fun createWidgetRenderItem(
    itemState: CourseItemState,
    visibleRanges: List<CourseWidgetVisibleRange>,
  ): CourseWidgetRenderItem {
    val fixed = whatTime.now.value
    val isAffair = occurrence.kind == ScheduleOccurrenceKind.AFFAIR
    val styles = occurrence.widgetStyles(isAffair)
    return CourseWidgetRenderItem(
      id = widgetItemId,
      dayOfWeek = fixed.dayOfWeek.isoDayNumber,
      title = data.title,
      content = data.description,
      beginMinute = fixed.beginTime.minuteOfDay,
      endMinute = fixed.finalTime.minuteOfDay,
      visibleRanges = visibleRanges,
      lightStyle = styles.first,
      darkStyle = styles.second,
      backgroundPattern = if (isAffair) CourseWidgetBackgroundPattern.DIAGONAL_STRIPE else CourseWidgetBackgroundPattern.SOLID,
      action = CourseWidgetAction(week = fixed.page, itemId = widgetDialogItemId),
    )
  }

  /** 绘制平台无关的日程 Item；点击回调完全由 [platform] 提供。 */
  @Composable
  private fun Content(onClick: ((MinuteTimePair) -> Unit)?) {
    val isAffair = occurrence.kind == ScheduleOccurrenceKind.AFFAIR
    // Compose 与 Widget 都从同一个纯样式解析结果取值，避免主题和事务斜纹颜色随实现分支漂移。
    val styles = occurrence.widgetStyles(isAffair)
    val style = if (MaterialTheme.colors.isLight) styles.first else styles.second
    // 小组件样式保存的是 32 位 ARGB Long，不能当成 Compose 内部 ULong 打包值解析。
    val itemTextColor = Color(style.contentArgb)
    val itemBackgroundColor = Color(style.backgroundArgb)
    val affairStripeColor = style.stripeArgb?.let(::Color)
    val itemModifierList = if (isAffair) {
      remember(affairStripeColor) {
        createCourseDefaultModifierList()
          .add(
            ScheduleAffairBackgroundItemModifier(
              stripeColor = checkNotNull(affairStripeColor),
            ),
          )
      }
    } else {
      remember { createCourseDefaultModifierList() }
    }
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

/** 日程样式选择与 Compose 中的 occurrence/category/事务判断保持同一业务分支。 */
internal fun ScheduleOccurrenceView.widgetStyles(isAffair: Boolean): Pair<CourseWidgetItemStyle, CourseWidgetItemStyle> {
  val linkedTodoAffair = isAffair && isInTodoList
  val category = categoryColor.takeIf { !isAffair || linkedTodoAffair }
  val lightBackground = category?.lightBackgroundArgb ?: ScheduleDefaultOccurrenceColor.lightBackgroundArgb
  val darkBackground = category?.darkBackgroundArgb ?: ScheduleDefaultOccurrenceColor.darkBackgroundArgb
  val lightContent = when {
    // 关联到清单的事务仍继承该清单分组的文字色；只有纯事务才使用主题二级文字色。
    linkedTodoAffair && category != null -> category.lightContentArgb
    isAffair -> CourseWidgetSecondaryContentArgb
    category != null -> category.lightContentArgb
    else -> ScheduleDefaultOccurrenceColor.lightContentArgb
  }
  val stripeLight = if (isAffair) if (linkedTodoAffair) lightBackground else 0xFFE4E7EC else null
  val stripeDark = if (isAffair) if (linkedTodoAffair) darkBackground else 0xFF4D4B4C else null
  // AFFAIR 只绘制斜线，背景必须透明，才能保留线条之间透出的课表底色。
  val lightStyle = CourseWidgetItemStyle(
    contentArgb = lightContent,
    backgroundArgb = if (isAffair) 0L else lightBackground,
    stripeArgb = stripeLight,
  )
  val darkStyle = CourseWidgetItemStyle(
    contentArgb = CourseWidgetDarkContentArgb,
    backgroundArgb = if (isAffair) 0L else darkBackground,
    stripeArgb = stripeDark,
  )
  return lightStyle to darkStyle
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
