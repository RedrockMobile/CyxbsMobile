package com.cyxbs.pages.widget.widget.oversize

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineSection
import com.cyxbs.pages.widget.api.CourseWidgetWeekSnapshot
import com.cyxbs.pages.widget.widget.glance.ACTION_WIDGET_FLUSH_LEGACY
import com.cyxbs.pages.widget.widget.glance.ACTION_WIDGET_REFRESH
import com.cyxbs.pages.widget.widget.glance.OVERSIZED_TWO_COLUMN_MIN_WIDTH_DP
import com.cyxbs.pages.widget.widget.glance.OversizedExpandedTimelineMaskKey
import com.cyxbs.pages.widget.widget.glance.OversizedTimelineSectionIndexKey
import com.cyxbs.pages.widget.widget.glance.ToggleOversizedTimelineSectionAction
import com.cyxbs.pages.widget.widget.glance.WidgetRenderItemCard
import com.cyxbs.pages.widget.widget.glance.dispatchRefreshToGlanceReceiver
import com.cyxbs.pages.widget.widget.glance.findAllDayItem
import com.cyxbs.pages.widget.widget.glance.widgetColorProvider
import com.cyxbs.pages.widget.widget.glance.mondayBasedDay
import com.cyxbs.pages.widget.widget.glance.observeCourseWidgetSnapshot
import com.cyxbs.pages.widget.widget.glance.readCourseWidgetPreviewSnapshot
import com.cyxbs.pages.widget.widget.glance.resolveCurrentWeek
import com.cyxbs.pages.widget.widget.glance.resolveOversizedTimelineSections
import com.cyxbs.pages.widget.widget.glance.resolveOversizedVisibleDays
import com.cyxbs.pages.widget.widget.glance.weekOrEmpty
import java.time.LocalDate
import java.util.Calendar

/**
 * 可折叠宽度的周课表 receiver。保留原 FQN，并移除旧配置页与 RemoteViewsService 依赖。
 */
class OversizedAppWidget : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = OversizedGlanceWidget

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == ACTION_WIDGET_REFRESH || intent.action == ACTION_WIDGET_FLUSH_LEGACY) {
      super.onReceive(context, intent)
      dispatchRefreshToGlanceReceiver(context, javaClass)
      return
    }
    super.onReceive(context, intent)
  }

}

/** 周课表随宿主实际尺寸精确重组，在 1、3、5、7 日视图之间切换。 */
internal object OversizedGlanceWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override val sizeMode: SizeMode = SizeMode.Exact
  override val previewSizeMode = SizeMode.Responsive(
    setOf(
      DpSize(80.dp, 250.dp),
      DpSize(160.dp, 250.dp),
      DpSize(240.dp, 250.dp),
      DpSize(320.dp, 320.dp),
    ),
  )

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      val preferences = currentState<Preferences>()
      // 更新事件可能复用活跃 Glance 会话，因此每次重组都从已原子提交的快照重新计算当前周。
      val snapshot = observeCourseWidgetSnapshot()
      val calendar = Calendar.getInstance()
      val currentWeek = snapshot.resolveCurrentWeek()
      val headerDate = resolveOversizedWeekHeaderDate(snapshot, currentWeek, calendar)
      OversizedWidgetContent(
        snapshot = snapshot,
        monthLabel = headerDate.monthLabel,
        dayOfMonths = headerDate.dayOfMonths,
        today = calendar.mondayBasedDay(),
        expandedTimelineMask = preferences[OversizedExpandedTimelineMaskKey] ?: 0,
      )
    }
  }

  /** Android 15+ widget 选择器生成完整周课表预览，避免 Exact 模式只按最小尺寸截断内容。 */
  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val snapshot = readCourseWidgetPreviewSnapshot()
    val calendar = Calendar.getInstance()
    val currentWeek = snapshot.resolveCurrentWeek()
    val headerDate = resolveOversizedWeekHeaderDate(snapshot, currentWeek, calendar)
    provideContent {
      OversizedWidgetContent(
        snapshot = snapshot,
        monthLabel = headerDate.monthLabel,
        dayOfMonths = headerDate.dayOfMonths,
        today = calendar.mondayBasedDay(),
        expandedTimelineMask = 0,
      )
    }
  }
}

/** 根据实际宽度绘制 1/3/5/7 日视图，并按实例状态展开课表侧下发的时间分段。 */
@Composable
private fun OversizedWidgetContent(
  snapshot: CourseWidgetSnapshot,
  monthLabel: String,
  dayOfMonths: List<Int>,
  today: Int,
  expandedTimelineMask: Int,
) {
  val localSize = LocalSize.current
  val widgetWidth = localSize.width.value.takeIf { it.isFinite() && it > 0f } ?: 250f
  val widgetHeight = localSize.height.value.takeIf { it.isFinite() && it > 0f } ?: 250f
  val isDesktopSingleCell = widgetWidth < OVERSIZED_TWO_COLUMN_MIN_WIDTH_DP
  val visibleDays = resolveOversizedVisibleDays(widgetWidth, today)
  val sections = snapshot.resolveOversizedTimelineSections()
  val timelineWidth = resolveOversizedTimelineWidth(visibleDayCount = visibleDays.size)
  val cellPadding = if (isDesktopSingleCell) {
    OversizedCompactCellPadding
  } else {
    OversizedCellPadding
  }
  val week = snapshot.weekOrEmpty(snapshot.resolveCurrentWeek())
  val hasAllDayItem = visibleDays.any { findAllDayItem(week, it) != null }
  // 日期列横向不再额外留缝，由每张卡片自身的 2dp 纯白外层负责区分相邻日期。
  val cardWidth = ((widgetWidth - timelineWidth.value) /
    visibleDays.size).coerceAtLeast(1f).dp
  // 表头选中背景取日期列净宽与表头净高中的较小值，确保所有宽度档位都是真正的正方形。
  val headerCellSize = minOf(
    (cardWidth.value - cellPadding.value * 2).coerceAtLeast(1f),
    (OversizedHeaderHeight.value - cellPadding.value * 2).coerceAtLeast(1f),
  ).dp
  val timelineViewportHeight = (widgetHeight - OversizedHeaderHeight.value -
    if (hasAllDayItem) OversizedAllDayHeight.value else 0f).coerceAtLeast(1f)
  val sectionHeights = resolveOversizedSectionHeights(
    sections = sections,
    expandedTimelineMask = expandedTimelineMask,
    availableHeightDp = timelineViewportHeight,
  )
  val timelineContentHeight = sectionHeights.sum().coerceAtLeast(timelineViewportHeight)
  Column(
    modifier = GlanceModifier.fillMaxSize().background(widgetColorProvider(Color.White))
      .appWidgetBackground().cornerRadius(14.dp),
  ) {
    WeekHeader(
      monthLabel = monthLabel,
      dayOfMonths = dayOfMonths,
      today = today,
      visibleDays = visibleDays,
      timelineWidth = timelineWidth,
      headerCellSize = headerCellSize,
      cellPadding = cellPadding,
      isDesktopSingleCell = isDesktopSingleCell,
    )
    AllDayRow(
      week = week,
      visibleDays = visibleDays,
      cardWidth = cardWidth,
      timelineWidth = timelineWidth,
      cellPadding = cellPadding,
      isDesktopSingleCell = isDesktopSingleCell,
    )
    LazyColumn(
      modifier = GlanceModifier.fillMaxWidth().height(timelineViewportHeight.dp),
    ) {
      item(itemId = OversizedTimelineLazyItemId) {
        OversizedScrollableTimeline(
          week = week,
          visibleDays = visibleDays,
          sections = sections,
          sectionHeights = sectionHeights,
          contentHeightDp = timelineContentHeight,
          expandedTimelineMask = expandedTimelineMask,
          timelineWidth = timelineWidth,
          cardWidth = cardWidth,
          cellPadding = cellPadding,
          isDesktopSingleCell = isDesktopSingleCell,
        )
      }
    }
  }
}

/**
 * 可滚动的连续周课表画布。
 *
 * 时间轴行只负责展示课表下发的分段；日期列中的条目根据真实起止分钟独立定位，不再绑定某个课节格。
 */
@Composable
private fun OversizedScrollableTimeline(
  week: CourseWidgetWeekSnapshot,
  visibleDays: List<Int>,
  sections: List<CourseWidgetTimelineSection>,
  sectionHeights: List<Float>,
  contentHeightDp: Float,
  expandedTimelineMask: Int,
  timelineWidth: Dp,
  cardWidth: Dp,
  cellPadding: Dp,
  isDesktopSingleCell: Boolean,
) {
  Box(modifier = GlanceModifier.fillMaxWidth().height(contentHeightDp.dp)) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
      // MIUI RemoteViews 宿主会截掉同一 Column 中排在第 11 个之后的子节点，因此每 5 行嵌套一组。
      sections.indices.chunked(OversizedMaxRowsPerGroup).forEach { indexes ->
        val groupHeight = indexes.sumOf { sectionHeights[it].toDouble() }.toFloat().dp
        Column(modifier = GlanceModifier.fillMaxWidth().height(groupHeight)) {
          indexes.forEach { index ->
            val section = sections[index]
            val rowHeight = sectionHeights[index].dp
            Row(modifier = GlanceModifier.fillMaxWidth().height(rowHeight)) {
              TimeAxisCell(section, rowHeight, timelineWidth, isDesktopSingleCell)
            }
          }
        }
      }
    }
    Row(modifier = GlanceModifier.fillMaxSize()) {
      Spacer(GlanceModifier.width(timelineWidth).fillMaxHeight())
      visibleDays.forEach { day ->
        OversizedDayTimeline(
          week = week,
          day = day,
          sections = sections,
          sectionHeights = sectionHeights,
          cardWidth = cardWidth,
          visibleDayCount = visibleDays.size,
          cellPadding = cellPadding,
          isDesktopSingleCell = isDesktopSingleCell,
        )
      }
    }
    ExpandableTimeLabelsOverlay(
      sections = sections,
      sectionHeights = sectionHeights,
      expandedTimelineMask = expandedTimelineMask,
      timelineWidth = timelineWidth,
      isDesktopSingleCell = isDesktopSingleCell,
      timelineHeightDp = contentHeightDp,
    )
  }
}

/** 顶部表头；左侧时间轴固定宽度，其余空间由当前 1/3/7 个日期等分。 */
@Composable
private fun WeekHeader(
  monthLabel: String,
  dayOfMonths: List<Int>,
  today: Int,
  visibleDays: List<Int>,
  timelineWidth: Dp,
  headerCellSize: Dp,
  cellPadding: Dp,
  isDesktopSingleCell: Boolean,
) {
  Row(modifier = GlanceModifier.fillMaxWidth().height(OversizedHeaderHeight)) {
    TimelineHeaderText(
      text = if (isDesktopSingleCell) {
        "${monthLabel.removeSuffix("月")}\n月"
      } else {
        monthLabel
      },
      timelineWidth = timelineWidth,
      isDesktopSingleCell = isDesktopSingleCell,
    )
    visibleDays.forEach { day ->
      Box(
        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(cellPadding),
        contentAlignment = Alignment.Center,
      ) {
        HeaderText(
          dayLabel = OversizedDayLabels[day],
          dayOfMonth = dayOfMonths.getOrElse(day) { 0 },
          selected = day == today,
          cellSize = headerCellSize,
          isDesktopSingleCell = isDesktopSingleCell,
        )
      }
    }
  }
}

/** 全日项显示在星期标题下；[cardWidth] 用于保持斜纹线宽不受单元格缩放影响。 */
@Composable
private fun AllDayRow(
  week: CourseWidgetWeekSnapshot,
  visibleDays: List<Int>,
  cardWidth: Dp,
  timelineWidth: Dp,
  cellPadding: Dp,
  isDesktopSingleCell: Boolean,
) {
  val allDayItems = visibleDays.map { findAllDayItem(week, it) }
  if (allDayItems.all { it == null }) return
  Row(modifier = GlanceModifier.fillMaxWidth().height(OversizedAllDayHeight)) {
    StaticTimeLabel("全天", timelineWidth, isDesktopSingleCell)
    allDayItems.forEach { item ->
      AllDayItemCell(
        item = item,
        columnWidth = cardWidth,
        rowHeight = OversizedAllDayHeight,
        visibleDayCount = visibleDays.size,
        cellPadding = cellPadding,
        isDesktopSingleCell = isDesktopSingleCell,
      )
    }
  }
}

/** 月份占用固定时间轴宽度，不随可见日期数量变化。 */
@Composable
private fun TimelineHeaderText(text: String, timelineWidth: Dp, isDesktopSingleCell: Boolean) {
  Box(
    modifier = GlanceModifier.width(timelineWidth).fillMaxHeight().padding(
      end = if (isDesktopSingleCell) 0.dp else OversizedTimelineLabelRightInset,
    ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = TextStyle(
        color = widgetColorProvider(Color(0xFF15315B)),
        fontSize = when {
          isDesktopSingleCell -> 7.sp
          timelineWidth <= OversizedNarrowTimelineWidth -> 7.sp
          else -> 9.sp
        },
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      ),
      maxLines = if (isDesktopSingleCell) 2 else 1,
    )
  }
}

/** 表头按课表本体的“周几 + 几日”两行结构绘制；课程详情点击由实际 item 卡片承担。 */
@Composable
private fun HeaderText(
  dayLabel: String,
  dayOfMonth: Int,
  selected: Boolean,
  cellSize: Dp,
  isDesktopSingleCell: Boolean,
) {
  val background = if (selected) Color(0xFF2A4E84) else Color.Transparent
  val foreground = if (selected) Color.White else Color(0xFF15315B)
  // RemoteViews 的 Text 不会随 fillMaxHeight 自动垂直居中，表头需要显式以容器中心为锚点。
  Box(
    modifier = GlanceModifier.width(cellSize).height(cellSize)
      .background(widgetColorProvider(background)).cornerRadius(8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = GlanceModifier.fillMaxWidth(),
      horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
      Text(
        text = dayLabel,
        modifier = GlanceModifier.fillMaxWidth(),
        style = TextStyle(
          color = widgetColorProvider(foreground),
          fontSize = if (isDesktopSingleCell) 10.sp else 11.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        ),
        maxLines = 1,
      )
      Text(
        text = if (dayOfMonth > 0) "${dayOfMonth}日" else "",
        modifier = GlanceModifier.fillMaxWidth(),
        style = TextStyle(
          color = widgetColorProvider(foreground),
          fontSize = if (isDesktopSingleCell) 9.sp else 10.sp,
          textAlign = TextAlign.Center,
        ),
        maxLines = 1,
      )
    }
  }
}

/**
 * 固定课节直接参与行测量；多行标签在区间内等分高度，保证相邻课节编号的中心间距一致。
 * 可折叠段由覆盖层绘制，防止文字最小高度改变权重比例。
 */
@Composable
private fun TimeAxisCell(
  section: CourseWidgetTimelineSection,
  rowHeight: Dp,
  timelineWidth: Dp,
  isDesktopSingleCell: Boolean,
) {
  val minimumLabelHeight = if (isDesktopSingleCell) 10.dp else 12.dp
  if (section.expandable || section.collapsedLabel.isBlank() || rowHeight < minimumLabelHeight) {
    Spacer(GlanceModifier.width(timelineWidth).fillMaxHeight())
    return
  }
  val labels = section.collapsedLabel.split('\n')
  Column(modifier = GlanceModifier.width(timelineWidth).fillMaxHeight()) {
    labels.forEach { label ->
      // RemoteViews 的 Text 不会随父级 weight 自动垂直居中，必须用 Box 锚定每段的几何中心。
      Box(
        modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(
          end = if (isDesktopSingleCell) 0.dp else OversizedTimelineLabelRightInset,
        ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label,
          modifier = GlanceModifier.padding(1.dp),
          style = TextStyle(
            color = widgetColorProvider(Color(0xFF15315B)),
            fontSize = oversizedTimelineLabelFontSize(timelineWidth, isDesktopSingleCell),
            textAlign = TextAlign.Center,
          ),
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * 在不参与课程行测量的覆盖层中绘制全部可折叠时间段标签。
 *
 * 标签固定以对应分段中心为锚点，其可点击高度不会反向撑大折叠分段。标签按固定数量拆分到
 * 多个 RemoteViews Column，避免单列直接子节点过多时宿主丢弃最后的夜间标签。
 */
@Composable
private fun ExpandableTimeLabelsOverlay(
  sections: List<CourseWidgetTimelineSection>,
  sectionHeights: List<Float>,
  expandedTimelineMask: Int,
  timelineWidth: Dp,
  isDesktopSingleCell: Boolean,
  timelineHeightDp: Float,
) {
  val labelHeight = if (isDesktopSingleCell) {
    OversizedCompactTimelineLabelHeight
  } else {
    OversizedTimelineLabelHeight
  }
  val bottomLabelInset = if (isDesktopSingleCell) OversizedTimelineBottomLabelInset else 0.dp
  val labelPositions = resolveOversizedExpandableTimeLabels(
    sections = sections,
    sectionHeights = sectionHeights,
    expandedTimelineMask = expandedTimelineMask,
    labelHeightDp = labelHeight.value,
    timelineHeightDp = timelineHeightDp,
    bottomLabelInsetDp = bottomLabelInset.value,
  )
  // 每列最多两个标签，规避 MIUI 的直接子节点限制；两列仍覆盖在同一条时间轴宽度上。
  labelPositions.chunked(OversizedExpandableLabelsPerOverlay).forEach { labels ->
    Column(modifier = GlanceModifier.width(timelineWidth).height(timelineHeightDp.dp)) {
      var previousLabelBottomDp = 0f
      labels.forEach { labelPosition ->
        val gapDp = (labelPosition.topDp - previousLabelBottomDp).coerceAtLeast(0f)
        if (gapDp > 0f) Spacer(GlanceModifier.height(gapDp.dp))
        Box(
          modifier = GlanceModifier.width(timelineWidth).height(labelHeight)
            .padding(end = if (isDesktopSingleCell) 0.dp else OversizedTimelineLabelRightInset)
            .clickable(
              actionRunCallback<ToggleOversizedTimelineSectionAction>(
                actionParametersOf(
                  OversizedTimelineSectionIndexKey to labelPosition.sectionIndex,
                ),
              ),
            ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = labelPosition.text,
            modifier = GlanceModifier.padding(1.dp),
            style = TextStyle(
              color = widgetColorProvider(Color(0xFF15315B)),
              fontSize = oversizedTimelineLabelFontSize(timelineWidth, isDesktopSingleCell),
              textAlign = TextAlign.Center,
            ),
            // 折叠文案和展开后的 HH:mm 刻度都应保持单行，避免改变时间锚点。
            maxLines = 1,
          )
        }
        previousLabelBottomDp = labelPosition.topDp + labelHeight.value
      }
    }
  }
}

/** 不可交互的固定时间轴标签。 */
@Composable
private fun StaticTimeLabel(label: String, timelineWidth: Dp, isDesktopSingleCell: Boolean) {
  Box(
    modifier = GlanceModifier.width(timelineWidth).fillMaxHeight().padding(
      end = if (isDesktopSingleCell) 0.dp else OversizedTimelineLabelRightInset,
    ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      modifier = GlanceModifier.padding(1.dp),
      style = TextStyle(
        color = widgetColorProvider(Color(0xFF15315B)),
        fontSize = oversizedTimelineLabelFontSize(timelineWidth, isDesktopSingleCell),
        textAlign = TextAlign.Center,
      ),
      maxLines = 2,
    )
  }
}

/** 三天视图扣除左移补偿后仅剩 16dp，使用 7sp 才能让两个汉字连同 padding 保持单行。 */
private fun oversizedTimelineLabelFontSize(
  timelineWidth: Dp,
  isDesktopSingleCell: Boolean,
) = when {
  isDesktopSingleCell -> 6.sp
  timelineWidth <= OversizedNarrowTimelineWidth -> 7.sp
  else -> 8.sp
}

/** 全日项不参与连续时间轴，固定放在星期标题下方。 */
@Composable
private fun RowScope.AllDayItemCell(
  item: CourseWidgetRenderItem?,
  columnWidth: Dp,
  rowHeight: Dp,
  visibleDayCount: Int = 7,
  cellPadding: Dp,
  isDesktopSingleCell: Boolean,
) {
  if (item == null) {
    Spacer(GlanceModifier.defaultWeight().fillMaxHeight().padding(2.dp))
    return
  }
  WidgetRenderItemCard(
    item = item,
    // 横向贴合相邻日期列；纵向仍保留原定位间距，避免全天项挤占表头或时间轴。
    modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(vertical = cellPadding),
    isDark = false,
    titleSizeSp = if (isDesktopSingleCell) 7 else if (visibleDayCount == 1) 10 else 8,
    contentSizeSp = if (isDesktopSingleCell) 6 else if (visibleDayCount == 1) 9 else 7,
    showContent = false,
    contentPaddingHorizontal = if (isDesktopSingleCell) 1.dp else 2.dp,
    contentPaddingVertical = 1.dp,
    maxTitleLines = 1,
    renderSize = DpSize(
      width = columnWidth,
      height = (rowHeight - cellPadding * 2).coerceAtLeast(1.dp),
    ),
  )
}

/**
 * 一天的所有条目按真实时间覆盖在同一条连续时间轴上。
 *
 * 先绘制低优先级层，再绘制高优先级层；完全重叠项由上层卡片的 action 携带关联 ID，点击后仍可
 * 进入课表详情。条目不再被拆进课节格，也不会因跨越课间而产生断裂。
 */
@Composable
private fun RowScope.OversizedDayTimeline(
  week: CourseWidgetWeekSnapshot,
  day: Int,
  sections: List<CourseWidgetTimelineSection>,
  sectionHeights: List<Float>,
  cardWidth: Dp,
  visibleDayCount: Int,
  cellPadding: Dp,
  isDesktopSingleCell: Boolean,
) {
  val items = week.items.withIndex()
    .filter { (_, item) ->
      val beginMinute = item.beginMinute
      val endMinute = item.endMinute
      !item.isAllDay && item.dayOfWeek == day + 1 &&
        beginMinute != null && endMinute != null && endMinute > beginMinute
    }
    .sortedWith(
      compareByDescending<IndexedValue<CourseWidgetRenderItem>> { it.value.renderLayer }
        .thenBy { it.index },
    )
  Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
    Spacer(GlanceModifier.fillMaxSize())
    items.forEachIndexed { drawIndex, (_, item) ->
      val beginMinute = item.beginMinute ?: return@forEachIndexed
      val endMinute = item.endMinute ?: return@forEachIndexed
      // 当前 Item 后绘制在上层；只要与此前已绘制的下层 Item 相交，就显示课表同款右上角 tips。
      val hasUnderlyingOverlap = item.action.overlapItemIds.isNotEmpty() ||
        items.subList(0, drawIndex).any { (_, underlyingItem) ->
          item.overlapsInOversizedTimeline(underlyingItem)
        }
      val topDp = resolveOversizedMinuteOffset(sections, sectionHeights, beginMinute)
      val bottomDp = resolveOversizedMinuteOffset(sections, sectionHeights, endMinute)
      val itemHeight = (bottomDp - topDp).coerceAtLeast(0f).dp
      if (itemHeight > 0.dp) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
          if (topDp > 0f) Spacer(GlanceModifier.height(topDp.dp))
          WidgetRenderItemCard(
            item = item,
            // 日期列之间不再加外边距，课程自身的纯白背景层承担横向分隔。
            modifier = GlanceModifier.fillMaxWidth().height(itemHeight)
              .padding(vertical = cellPadding),
            isDark = false,
            titleSizeSp = when {
              isDesktopSingleCell -> 7
              visibleDayCount == 1 -> 10
              visibleDayCount == 3 -> 9
              else -> 8
            },
            contentSizeSp = when {
              isDesktopSingleCell -> 6
              visibleDayCount == 1 -> 9
              visibleDayCount == 3 -> 8
              else -> 7
            },
            showContent = !isDesktopSingleCell,
            contentPaddingHorizontal = if (isDesktopSingleCell) 1.dp else 2.dp,
            contentPaddingVertical = if (isDesktopSingleCell) 1.dp else 3.dp,
            maxTitleLines = 3,
            topBottomText = true,
            coverTipColor = if (hasUnderlyingOverlap) {
              widgetColorProvider(Color(item.lightStyle.contentArgb))
            } else {
              null
            },
            renderSize = DpSize(
              width = cardWidth,
              height = (itemHeight - cellPadding * 2).coerceAtLeast(1.dp),
            ),
          )
        }
      }
    }
  }
}

/** 半开分钟区间真实相交时才算覆盖，端点相接不会误显示右上角 tips。 */
private fun CourseWidgetRenderItem.overlapsInOversizedTimeline(
  other: CourseWidgetRenderItem,
): Boolean {
  val begin = beginMinute ?: return false
  val end = endMinute ?: return false
  val otherBegin = other.beginMinute ?: return false
  val otherEnd = other.endMinute ?: return false
  return begin < otherEnd && otherBegin < end
}

/** 把任意分钟映射到当前展开状态下的累计纵向坐标。 */
internal fun resolveOversizedMinuteOffset(
  sections: List<CourseWidgetTimelineSection>,
  sectionHeights: List<Float>,
  minute: Int,
): Float {
  if (sections.isEmpty() || sections.size != sectionHeights.size) return 0f
  var offset = 0f
  sections.forEachIndexed { index, section ->
    val height = sectionHeights[index].coerceAtLeast(0f)
    if (minute <= section.beginMinute) return offset
    if (minute < section.endMinute) {
      val duration = (section.endMinute - section.beginMinute).coerceAtLeast(1)
      val ratio = (minute - section.beginMinute).toFloat() / duration
      return offset + height * ratio.coerceIn(0f, 1f)
    }
    offset += height
  }
  return offset
}

/**
 * 根据可见天数计算固定的时间轴宽度。
 *
 * 1 天、3 天档始终保留完整显示 `HH:mm` 所需的宽度，避免展开时横向扩容造成日期列和课程卡片
 * 跳动。5 天、7 天档分别沿用原有宽度。
 */
internal fun resolveOversizedTimelineWidth(
  visibleDayCount: Int,
): Dp = when (visibleDayCount) {
    1, 3 -> OversizedNarrowTimelineWidth
    5 -> OversizedFiveDayTimelineWidth
    else -> OversizedTimelineWidth
  }

/** 覆盖层中的可点击时间轴标签及其绝对纵向位置。 */
internal data class OversizedExpandableTimeLabel(
  val sectionIndex: Int,
  val text: String,
  val topDp: Float,
)

/**
 * 计算可折叠时间段的覆盖层标签。
 *
 * 折叠态沿用课表下发的分段文案；展开态与课表本体一致，从区间内第一个整点开始按小时
 * 显示 `HH:mm`，末段额外显示 `24:00`。标签中心按真实分钟比例定位，因而不会随分段高度变化
 * 而偏离对应时刻。
 */
internal fun resolveOversizedExpandableTimeLabels(
  sections: List<CourseWidgetTimelineSection>,
  sectionHeights: List<Float>,
  expandedTimelineMask: Int,
  labelHeightDp: Float,
  timelineHeightDp: Float,
  bottomLabelInsetDp: Float,
): List<OversizedExpandableTimeLabel> {
  if (sections.size != sectionHeights.size) return emptyList()
  val safeLabelHeightDp = labelHeightDp.coerceAtLeast(0f)
  val maximumLabelTopDp = (
    timelineHeightDp - safeLabelHeightDp - bottomLabelInsetDp.coerceAtLeast(0f)
    ).coerceAtLeast(0f)
  return buildList {
    var sectionTopDp = 0f
    sections.forEachIndexed { index, section ->
      val sectionHeightDp = sectionHeights[index].coerceAtLeast(0f)
      if (section.expandable) {
        val isExpanded = expandedTimelineMask and (1 shl index) != 0
        if (isExpanded) {
          val durationMinutes = (section.endMinute - section.beginMinute).coerceAtLeast(1)
          val firstWholeHour = if (section.beginMinute % 60 == 0) {
            section.beginMinute
          } else {
            (section.beginMinute / 60 + 1) * 60
          }
          val hourMarks = buildList {
            var minute = firstWholeHour
            while (minute <= section.endMinute) {
              add(minute)
              minute += 60
            }
            if (section.endMinute == 23 * 60 + 59) add(24 * 60)
          }
          hourMarks.forEach { minute ->
            val minuteRatio = (minute - section.beginMinute).toFloat() / durationMinutes
            val centerDp = sectionTopDp + sectionHeightDp * minuteRatio
            add(
              OversizedExpandableTimeLabel(
                sectionIndex = index,
                text = "${(minute / 60).toString().padStart(2, '0')}:" +
                  (minute % 60).toString().padStart(2, '0'),
                topDp = (centerDp - safeLabelHeightDp / 2f)
                  .coerceIn(0f, maximumLabelTopDp),
              ),
            )
          }
        } else {
          val centerDp = sectionTopDp + sectionHeightDp / 2f
          add(
            OversizedExpandableTimeLabel(
              sectionIndex = index,
              text = section.collapsedLabel,
              topDp = (centerDp - safeLabelHeightDp / 2f)
                .coerceIn(0f, maximumLabelTopDp),
            ),
          )
        }
      }
      sectionTopDp += sectionHeightDp
    }
  }
}

/**
 * 以完全折叠时的总权重计算固定单位高度，再使用当前权重计算各分段高度。
 *
 * 因此完全折叠时总高度恰好等于容器高度；展开后单位高度不变，内容总高度按增加的权重自然变长，
 * 由外层滚动容器承载。这与课表以 initialWeight 确定基准高度、再改变 nowWeight 的行为一致。
 */
internal fun resolveOversizedSectionHeights(
  sections: List<CourseWidgetTimelineSection>,
  expandedTimelineMask: Int,
  availableHeightDp: Float,
): List<Float> {
  if (sections.isEmpty()) return emptyList()
  val safeHeight = availableHeightDp.coerceAtLeast(1f)
  val collapsedTotalWeight = sections.sumOf {
    it.collapsedWeight.coerceAtLeast(0f).toDouble()
  }.toFloat().coerceAtLeast(0.001f)
  val unitHeight = safeHeight / collapsedTotalWeight
  val weights = sections.mapIndexed { index, section ->
    if (section.expandable && expandedTimelineMask and (1 shl index) != 0) {
      section.expandedWeight
    } else {
      section.collapsedWeight
    }.coerceAtLeast(0f)
  }
  return weights.map { weight ->
    unitHeight * weight
  }
}

/** 最大号小组件表头使用的当前周日期，月份取该周周一，与课表本体保持一致。 */
internal data class OversizedWeekHeaderDate(
  val monthLabel: String,
  val dayOfMonths: List<Int>,
)

/**
 * 根据课表学期起始日计算指定周的表头日期。
 *
 * 快照缺少学期日期时退回设备当前周；该回退只影响表头文字，不改变课程数据所使用的周数。
 */
internal fun resolveOversizedWeekHeaderDate(
  snapshot: CourseWidgetSnapshot,
  week: Int,
  calendar: Calendar,
): OversizedWeekHeaderDate {
  val today = calendar.mondayBasedDay()
  val currentDate = LocalDate.of(
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH),
  )
  val fallbackMonday = currentDate.minusDays(today.toLong())
  val monday = snapshot.firstWeekBeginEpochDays?.let { firstWeekBeginEpochDays ->
    LocalDate.ofEpochDay(firstWeekBeginEpochDays.toLong())
      .plusWeeks((week - 1).coerceAtLeast(0).toLong())
  } ?: fallbackMonday
  return OversizedWeekHeaderDate(
    monthLabel = "${monday.monthValue}月",
    dayOfMonths = List(7) { offset -> monday.plusDays(offset.toLong()).dayOfMonth },
  )
}

private val OversizedCellPadding = 2.dp
private val OversizedTimelineWidth = 40.dp
private val OversizedCompactCellPadding = 1.dp
private val OversizedNarrowTimelineWidth = 28.dp
private val OversizedFiveDayTimelineWidth = 32.dp
// 非紧凑视图使用 12dp 标签盒，使首尾标签距边缘同为 6dp，并扩大其与第 1/12 节的间距。
private val OversizedTimelineLabelHeight = 12.dp
private val OversizedCompactTimelineLabelHeight = 14.dp
private val OversizedTimelineBottomLabelInset = 2.dp
// 左侧可见区域包含根容器外边距；右侧收窄 4dp，使标签视觉中心向左补偿 2dp。
private val OversizedTimelineLabelRightInset = 4.dp
private const val OversizedExpandableLabelsPerOverlay = 2
private val OversizedHeaderHeight = 44.dp
private val OversizedAllDayHeight = 22.dp
private const val OversizedMaxRowsPerGroup = 5
private const val OversizedTimelineLazyItemId = 1L
private val OversizedDayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
