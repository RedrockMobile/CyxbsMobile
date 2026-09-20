package com.cyxbs.pages.widget.widget.normal

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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
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
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineMark
import com.cyxbs.pages.widget.widget.glance.ACTION_WIDGET_FLUSH_LEGACY
import com.cyxbs.pages.widget.widget.glance.ACTION_WIDGET_REFRESH
import com.cyxbs.pages.widget.widget.glance.ChangeNormalDayAction
import com.cyxbs.pages.widget.widget.glance.DayDeltaKey
import com.cyxbs.pages.widget.widget.glance.NormalTimelineBar
import com.cyxbs.pages.widget.widget.glance.NormalTimelineGroup
import com.cyxbs.pages.widget.widget.glance.NormalTimelinePlacedBar
import com.cyxbs.pages.widget.widget.glance.NormalTimelineTextMode
import com.cyxbs.pages.widget.widget.glance.WidgetRenderItemCard
import com.cyxbs.pages.widget.widget.glance.WidgetDayOffsetKey
import com.cyxbs.pages.widget.widget.glance.currentMinute
import com.cyxbs.pages.widget.widget.glance.dispatchRefreshToGlanceReceiver
import com.cyxbs.pages.widget.widget.glance.widgetColorProvider
import com.cyxbs.pages.widget.widget.glance.mondayBasedDay
import com.cyxbs.pages.widget.widget.glance.normalDayForOffset
import com.cyxbs.pages.widget.widget.glance.observeCourseWidgetSnapshot
import com.cyxbs.pages.widget.widget.glance.openCourseWidgetItemAction
import com.cyxbs.pages.widget.widget.glance.overlaps
import com.cyxbs.pages.widget.widget.glance.projectNormalTimeline
import com.cyxbs.pages.widget.widget.glance.readCourseWidgetPreviewSnapshot
import com.cyxbs.pages.widget.widget.glance.resolveLaneLayout
import com.cyxbs.pages.widget.widget.glance.resolveNormalTimelineCurrentRatio
import com.cyxbs.pages.widget.widget.glance.resolveNormalTimelinePreferredLaneSpan
import com.cyxbs.pages.widget.widget.glance.resolveNormalTimelineTextAllocation
import com.cyxbs.pages.widget.widget.glance.resolveNormalTimelineTextMode
import com.cyxbs.pages.widget.widget.glance.resolveNormalTimelineTitleStyle
import com.cyxbs.pages.widget.widget.glance.resolveNormalTimelineVisibleLaneCount
import com.cyxbs.pages.widget.widget.glance.resolveCurrentWeek
import com.cyxbs.pages.widget.widget.glance.weekOrEmpty
import java.util.Calendar

/**
 * 横向日课表 receiver。保留原 FQN，使桌面已添加的实例可从 RemoteViews 原位升级。
 */
class NormalWidget : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = NormalGlanceWidget

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == ACTION_WIDGET_REFRESH || intent.action == ACTION_WIDGET_FLUSH_LEGACY) {
      super.onReceive(context, intent)
      dispatchRefreshToGlanceReceiver(context, javaClass)
      return
    }
    super.onReceive(context, intent)
  }
}

/** 日课表 Glance 实现；Exact 用于取得宿主横纵缩放后的实际尺寸并调整甘特布局容量。 */
internal object NormalGlanceWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override val sizeMode: SizeMode = SizeMode.Exact
  override val previewSizeMode = SizeMode.Single

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent {
      val preferences = currentState<Preferences>()
      // Glance 会复用仍存活的组合会话；必须在可重组内容中读取，主动更新才不会继续持有旧快照。
      val snapshot = observeCourseWidgetSnapshot()
      val calendar = Calendar.getInstance()
      NormalWidgetContent(
        snapshot = snapshot,
        today = calendar.mondayBasedDay(),
        nowMinute = currentMinute(calendar),
        dayOffset = preferences[WidgetDayOffsetKey] ?: 0,
      )
    }
  }

  /** Android 15+ widget 选择器使用真实快照或本地示例生成单一预览。 */
  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val calendar = Calendar.getInstance()
    val snapshot = readCourseWidgetPreviewSnapshot(calendar)
    provideContent {
      NormalWidgetContent(
        snapshot = snapshot,
        today = calendar.mondayBasedDay(),
        nowMinute = currentMinute(calendar),
        dayOffset = 0,
      )
    }
  }
}

/** 左侧按实例切换星期，右侧按课表时间轴比例展示当天甘特横条。 */
@Composable
private fun NormalWidgetContent(
  snapshot: CourseWidgetSnapshot,
  today: Int,
  nowMinute: Int,
  dayOffset: Int,
) {
  val currentWeek = snapshot.resolveCurrentWeek()
  val selectedDay = normalDayForOffset(today, dayOffset)
  val groups = projectNormalTimeline(
    week = snapshot.weekOrEmpty(currentWeek),
    day = selectedDay,
    timelineBeginMinute = snapshot.timelineBeginMinute,
    timelineEndMinute = snapshot.timelineEndMinute,
  )
  val localSize = LocalSize.current
  val localWidth = localSize.width.value
  val localHeight = localSize.height.value
  // 8dp 外边距 + 34dp 周导航 + 2dp 间隔；预览环境未提供尺寸时回退到 XML 的最小宽高。
  val widgetWidth = localWidth.takeIf { it.isFinite() && it > 0f } ?: 250f
  val widgetHeight = localHeight.takeIf { it.isFinite() && it > 0f } ?: 40f
  val timelineWidth = (widgetWidth - 44f).coerceAtLeast(1f).dp
  val timelineMarks = snapshot.timelineMarks
    .filter { it.label.isNotBlank() && it.ratio.isFinite() && it.ratio in 0f..1f }
    .sortedBy(CourseWidgetTimelineMark::ratio)
  val fontScale = LocalContext.current.resources.configuration.fontScale
  val maxVisibleLaneCount = resolveNormalTimelineVisibleLaneCount(
    widgetHeightDp = widgetHeight,
    fontScale = fontScale,
    hasTimelineScale = timelineMarks.isNotEmpty(),
  )
  val currentTimeRatio = if (selectedDay == today) {
    resolveNormalTimelineCurrentRatio(
      nowMinute = nowMinute,
      timelineBeginMinute = snapshot.timelineBeginMinute,
      timelineEndMinute = snapshot.timelineEndMinute,
    )
  } else {
    null
  }
  Row(
    modifier = GlanceModifier
      .fillMaxSize()
      .background(NormalSurfaceColor)
      .cornerRadius(12.dp)
      .padding(NormalWidgetOuterPadding),
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    DayNavigator(label = if (selectedDay == today) "今" else NormalDayLabels[selectedDay])
    Spacer(GlanceModifier.width(2.dp))
    NormalTimeline(
      groups = groups,
      timelineMarks = timelineMarks,
      timelineWidth = timelineWidth,
      widgetHeightDp = widgetHeight,
      maxVisibleLaneCount = maxVisibleLaneCount,
      fontScale = fontScale,
      currentTimeRatio = currentTimeRatio,
      modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
    )
  }
}

/** 顶部绘制统一小时刻度，余下区域只在实际重叠的局部时间组内等分高度。 */
@Composable
private fun NormalTimeline(
  groups: List<NormalTimelineGroup>,
  timelineMarks: List<CourseWidgetTimelineMark>,
  timelineWidth: Dp,
  widgetHeightDp: Float,
  maxVisibleLaneCount: Int,
  fontScale: Float,
  currentTimeRatio: Float?,
  modifier: GlanceModifier,
) {
  val timelineOrigin = NormalTimelineMarkWidth / 2
  val trackWidth = (timelineWidth.value - timelineOrigin.value).coerceAtLeast(1f).dp
  Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
    // 课程轨道通过内部 padding 从 8 点中心开始，刻度的左半部分自然占用 padding 区域。
    Column(
      modifier = GlanceModifier.fillMaxSize().padding(start = timelineOrigin)
        .background(NormalTrackColor).cornerRadius(6.dp),
    ) {
      if (timelineMarks.isNotEmpty()) Spacer(GlanceModifier.height(NormalTimelineScaleHeight))
      if (groups.isEmpty()) {
        Spacer(GlanceModifier.defaultWeight().fillMaxWidth())
      } else {
        NormalTimelineGroups(
          groups = groups,
          trackWidth = trackWidth,
          widgetHeightDp = widgetHeightDp,
          hasTimelineScale = timelineMarks.isNotEmpty(),
          maxVisibleLaneCount = maxVisibleLaneCount,
          fontScale = fontScale,
          modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
        )
      }
    }
    if (timelineMarks.isNotEmpty()) {
      NormalTimelineScale(
        marks = timelineMarks,
        layoutWidth = timelineWidth,
        trackWidth = trackWidth,
      )
    }
    if (currentTimeRatio != null) {
      NormalTimelineCurrentLine(
        ratio = currentTimeRatio,
        layoutWidth = timelineWidth,
        trackWidth = trackWidth,
      )
      // RemoteViews 的全尺寸叠加 Row 会先命中触摸；在最上层复刻横条点击区，避免时间线阻断详情入口。
      NormalTimelineClickOverlay(
        groups = groups,
        trackWidth = trackWidth,
        timelineOrigin = timelineOrigin,
        maxVisibleLaneCount = maxVisibleLaneCount,
        widgetHeightDp = widgetHeightDp,
        hasTimelineScale = timelineMarks.isNotEmpty(),
        fontScale = fontScale,
      )
    }
  }
}

/** 横向串联互不重叠的时间组，每组使用自己的轨道数和文字排布。 */
@Composable
private fun NormalTimelineGroups(
  groups: List<NormalTimelineGroup>,
  trackWidth: Dp,
  widgetHeightDp: Float,
  hasTimelineScale: Boolean,
  maxVisibleLaneCount: Int,
  fontScale: Float,
  modifier: GlanceModifier,
) {
  val timelineContentHeightDp = (
    widgetHeightDp - NormalWidgetOuterPadding.value * 2 -
      if (hasTimelineScale) NormalTimelineScaleHeight.value else 0f
    ).coerceAtLeast(1f)
  Row(modifier = modifier) {
    var cursor = 0f
    groups.forEach { group ->
      val gapWidth = (group.beginRatio - cursor).coerceAtLeast(0f) * trackWidth.value
      if (gapWidth > 0f) Spacer(GlanceModifier.width(gapWidth.dp).fillMaxHeight())
      val groupWidth = (group.endRatio - group.beginRatio).coerceAtLeast(0f) * trackWidth.value
      val baseLaneHeightDp = timelineContentHeightDp / maxVisibleLaneCount.coerceAtLeast(1)
      val laneLayout = group.resolveLaneLayout(maxVisibleLaneCount) { bar ->
        resolveNormalTimelinePreferredLaneSpan(
          bar = bar,
          trackWidthDp = trackWidth.value,
          laneHeightDp = baseLaneHeightDp,
          fontScale = fontScale,
        )
      }
      val overflowLanes = laneLayout.overflowLanes
      val overflowBars = overflowLanes.flatten()
      val overflowEdgeLanes = overflowLanes.take(NormalTimelineMaxOverflowEdges)
      val overflowEdgeHeight = if (overflowEdgeLanes.isEmpty()) {
        0f
      } else {
        (NormalTimelineMaxOverflowStackHeight.value / overflowEdgeLanes.size).coerceAtMost(1f)
      }
      val overflowStackHeight = overflowEdgeLanes.size * overflowEdgeHeight
      val visibleLaneHeight = (timelineContentHeightDp - overflowStackHeight).coerceAtLeast(1f) /
        maxVisibleLaneCount.coerceAtLeast(1)
      Box(
        modifier = GlanceModifier.width(groupWidth.dp).fillMaxHeight(),
      ) {
        // 溢出轨道先画在底层；层数较多时压缩单条边框，避免挤占标题的最小行高。
        overflowEdgeLanes.asReversed().forEachIndexed { index, lane ->
          NormalTimelineOverflowLane(
            bars = lane,
            trackWidth = trackWidth,
            groupBeginRatio = group.beginRatio,
            groupEndRatio = group.endRatio,
            bottomInset = (index * overflowEdgeHeight).dp,
            availableHeightDp = timelineContentHeightDp,
          )
        }
        Box(
          modifier = GlanceModifier.fillMaxSize().padding(
            bottom = overflowStackHeight.dp,
          ),
        ) {
          laneLayout.placedBars.groupBy(NormalTimelinePlacedBar::laneIndex)
            .toSortedMap().values.forEach { lane ->
            NormalTimelineLane(
              bars = lane,
              overflowBars = overflowBars,
              trackWidth = trackWidth,
              groupBeginRatio = group.beginRatio,
              groupEndRatio = group.endRatio,
              laneCount = maxVisibleLaneCount,
              laneHeightDp = visibleLaneHeight,
              fontScale = fontScale,
              modifier = GlanceModifier.fillMaxSize(),
            )
          }
        }
      }
      cursor = group.endRatio
    }
    val trailingWidth = (1f - cursor).coerceAtLeast(0f) * trackWidth.value
    if (trailingWidth > 0f) Spacer(GlanceModifier.width(trailingWidth.dp).fillMaxHeight())
  }
}

/** 参照课表 drawNowTimeLine：时间轴交点绘制小圆点，并向课程区延伸 1dp 细线。 */
@Composable
private fun NormalTimelineCurrentLine(
  ratio: Float,
  layoutWidth: Dp,
  trackWidth: Dp,
) {
  val centerX = NormalTimelineMarkWidth.value / 2 + ratio * trackWidth.value
  val lineStart = (centerX - NormalTimelineCurrentLineWidth.value / 2).coerceIn(
    minimumValue = 0f,
    maximumValue = (layoutWidth.value - NormalTimelineCurrentLineWidth.value).coerceAtLeast(0f),
  )
  val pointStart = (centerX - NormalTimelineCurrentPointDiameter.value / 2).coerceIn(
    minimumValue = 0f,
    maximumValue = (layoutWidth.value - NormalTimelineCurrentPointDiameter.value).coerceAtLeast(0f),
  )
  Box(modifier = GlanceModifier.width(layoutWidth).fillMaxHeight()) {
    Row(
      modifier = GlanceModifier.width(layoutWidth).fillMaxHeight()
        .padding(top = NormalTimelineScaleHeight),
    ) {
      if (lineStart > 0f) Spacer(GlanceModifier.width(lineStart.dp).fillMaxHeight())
      Box(
        modifier = GlanceModifier.width(NormalTimelineCurrentLineWidth).fillMaxHeight()
          .background(NormalTimelineCurrentColor),
      ) {}
    }
    Row(
      modifier = GlanceModifier.width(layoutWidth)
        .height(NormalTimelineScaleHeight + NormalTimelineCurrentPointDiameter / 2),
      verticalAlignment = Alignment.Vertical.Bottom,
    ) {
      if (pointStart > 0f) Spacer(GlanceModifier.width(pointStart.dp).fillMaxHeight())
      Box(
        modifier = GlanceModifier.width(NormalTimelineCurrentPointDiameter)
          .height(NormalTimelineCurrentPointDiameter)
          .background(NormalTimelineCurrentColor)
          .cornerRadius(NormalTimelineCurrentPointDiameter / 2),
      ) {}
    }
  }
}

/**
 * 按课程横条的同一时间比例生成透明点击区。
 *
 * 仅在当前时间线叠加时启用，用来补偿 RemoteViews 顶层容器对下层点击分发的拦截。
 */
@Composable
private fun NormalTimelineClickOverlay(
  groups: List<NormalTimelineGroup>,
  trackWidth: Dp,
  timelineOrigin: Dp,
  maxVisibleLaneCount: Int,
  widgetHeightDp: Float,
  hasTimelineScale: Boolean,
  fontScale: Float,
) {
  if (groups.isEmpty()) return
  Column(
    modifier = GlanceModifier.fillMaxSize().padding(start = timelineOrigin),
  ) {
    Spacer(GlanceModifier.height(NormalTimelineScaleHeight))
    Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
      var cursor = 0f
      groups.forEach { group ->
        val gapWidth = (group.beginRatio - cursor).coerceAtLeast(0f) * trackWidth.value
        if (gapWidth > 0f) Spacer(GlanceModifier.width(gapWidth.dp).fillMaxHeight())
        val groupWidth = (group.endRatio - group.beginRatio).coerceAtLeast(0f) * trackWidth.value
        val timelineContentHeightDp = (
          widgetHeightDp - NormalWidgetOuterPadding.value * 2 -
            if (hasTimelineScale) NormalTimelineScaleHeight.value else 0f
          ).coerceAtLeast(1f)
        val baseLaneHeightDp = timelineContentHeightDp / maxVisibleLaneCount.coerceAtLeast(1)
        val laneLayout = group.resolveLaneLayout(maxVisibleLaneCount) { bar ->
          resolveNormalTimelinePreferredLaneSpan(
            bar = bar,
            trackWidthDp = trackWidth.value,
            laneHeightDp = baseLaneHeightDp,
            fontScale = fontScale,
          )
        }
        val overflowEdgeLanes = laneLayout.overflowLanes.take(NormalTimelineMaxOverflowEdges)
        val overflowStackHeight = if (overflowEdgeLanes.isEmpty()) 0f else {
          overflowEdgeLanes.size *
            (NormalTimelineMaxOverflowStackHeight.value / overflowEdgeLanes.size).coerceAtMost(1f)
        }
        val laneHeightDp = (timelineContentHeightDp - overflowStackHeight).coerceAtLeast(1f) /
          maxVisibleLaneCount.coerceAtLeast(1)
        Box(
          modifier = GlanceModifier.width(groupWidth.dp).fillMaxHeight()
            .padding(bottom = overflowStackHeight.dp),
        ) {
          laneLayout.placedBars.groupBy(NormalTimelinePlacedBar::laneIndex)
            .toSortedMap().values.forEach { lane ->
            Row(modifier = GlanceModifier.fillMaxSize()) {
              var laneCursor = group.beginRatio
              lane.sortedBy { it.bar.beginMinute }.forEach { placedBar ->
                val bar = placedBar.bar
                val laneGapWidth = (bar.beginRatio - laneCursor).coerceAtLeast(0f) * trackWidth.value
                if (laneGapWidth > 0f) Spacer(GlanceModifier.width(laneGapWidth.dp).fillMaxHeight())
                val barWidth = (bar.endRatio - bar.beginRatio).coerceAtLeast(0f) * trackWidth.value
                val topPadding = placedBar.laneIndex * laneHeightDp
                val bottomPadding = (
                  maxVisibleLaneCount - placedBar.laneIndex - placedBar.laneSpan
                  ).coerceAtLeast(0) * laneHeightDp
                Box(
                  modifier = GlanceModifier.width(barWidth.dp).fillMaxHeight()
                    .padding(top = topPadding.dp, bottom = bottomPadding.dp)
                    .clickable(openCourseWidgetItemAction(bar.item.action)),
                ) {}
                laneCursor = bar.endRatio
              }
              val laneTrailingWidth = (group.endRatio - laneCursor).coerceAtLeast(0f) * trackWidth.value
              if (laneTrailingWidth > 0f) {
                Spacer(GlanceModifier.width(laneTrailingWidth.dp).fillMaxHeight())
              }
            }
          }
        }
        cursor = group.endRatio
      }
      val trailingWidth = (1f - cursor).coerceAtLeast(0f) * trackWidth.value
      if (trailingWidth > 0f) Spacer(GlanceModifier.width(trailingWidth.dp).fillMaxHeight())
    }
  }
}

/**
 * 按课表侧提供的线性比例放置小时刻度；文字中心与课程横条共用同一时间坐标。
 * 首个标签的左半部分绘制在轨道外，避免为了完整显示 8 点而整体压缩时间轴。
 * 相邻刻度过近时顺延，避免 RemoteViews 中的文字互相覆盖。
 */
@Composable
private fun NormalTimelineScale(
  marks: List<CourseWidgetTimelineMark>,
  layoutWidth: Dp,
  trackWidth: Dp,
) {
  Row(
    modifier = GlanceModifier.width(layoutWidth).height(NormalTimelineScaleHeight),
    verticalAlignment = Alignment.Vertical.Top,
  ) {
    // Glance 1.2.0 的 RemoteViewsTranslator.setChildren 只转换容器前 10 个直接子节点；
    // 每个刻度最多产生 Spacer + Box 两个节点，因此按四个刻度分组，避免后半段被框架截断。
    val groups = marks.chunked(NormalTimelineMarksPerGroup)
    groups.forEachIndexed { index, group ->
      val groupStart = if (index == 0) {
        0f
      } else {
        (groups[index - 1].last().ratio + group.first().ratio) / 2 * trackWidth.value
      }
      val groupEnd = if (index == groups.lastIndex) {
        layoutWidth.value
      } else {
        (group.last().ratio + groups[index + 1].first().ratio) / 2 * trackWidth.value
      }
      NormalTimelineScaleGroup(
        marks = group,
        trackWidth = trackWidth,
        groupStart = groupStart,
        groupEnd = groupEnd,
      )
    }
  }
}

/** 在单个安全规模的 Row 内按全局比例放置刻度，避免标签位置因分组发生变化。 */
@Composable
private fun NormalTimelineScaleGroup(
  marks: List<CourseWidgetTimelineMark>,
  trackWidth: Dp,
  groupStart: Float,
  groupEnd: Float,
) {
  val groupWidth = (groupEnd - groupStart).coerceAtLeast(0f)
  Row(
    modifier = GlanceModifier.width(groupWidth.dp).fillMaxHeight(),
    verticalAlignment = Alignment.Vertical.Bottom,
  ) {
    var cursor = 0f
    marks.forEach { mark ->
      // 标签盒子的中心比左边缘多半个宽度，正好与轨道起点的同等偏移抵消。
      val preferredStart = mark.ratio * trackWidth.value - groupStart
      val markStart = preferredStart.coerceIn(
        minimumValue = cursor,
        maximumValue = (groupWidth - NormalTimelineMarkWidth.value).coerceAtLeast(cursor),
      )
      if (markStart > cursor) Spacer(GlanceModifier.width((markStart - cursor).dp).fillMaxHeight())
      Box(
        modifier = GlanceModifier.width(NormalTimelineMarkWidth).fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
      ) {
        Text(
          text = mark.label,
          style = TextStyle(
            color = NormalTimelineScaleColor,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
          ),
          maxLines = 1,
        )
      }
      cursor = markStart + NormalTimelineMarkWidth.value
    }
    val trailingWidth = (groupWidth - cursor).coerceAtLeast(0f)
    if (trailingWidth > 0f) Spacer(GlanceModifier.width(trailingWidth.dp).fillMaxHeight())
  }
}

/**
 * 在一条起始轨道中横向串联条目，并按每个条目的 laneIndex/laneSpan 放置真实高度。
 *
 * 标题最大行数会扣除内容字体实际行高后再计算，避免较矮卡片把底部内容挤出边界。
 */
@Composable
private fun NormalTimelineLane(
  bars: List<NormalTimelinePlacedBar>,
  overflowBars: List<NormalTimelineBar>,
  trackWidth: Dp,
  groupBeginRatio: Float,
  groupEndRatio: Float,
  laneCount: Int,
  laneHeightDp: Float,
  fontScale: Float,
  modifier: GlanceModifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    var cursor = groupBeginRatio
    bars.sortedBy { it.bar.beginMinute }.forEach { placedBar ->
      val bar = placedBar.bar
      val gapWidth = (bar.beginRatio - cursor).coerceAtLeast(0f) * trackWidth.value
      if (gapWidth > 0f) Spacer(GlanceModifier.width(gapWidth.dp).fillMaxHeight())
      val barWidth = (bar.endRatio - bar.beginRatio).coerceAtLeast(0f) * trackWidth.value
      val topPaddingDp = placedBar.laneIndex * laneHeightDp
      val bottomPaddingDp = (
        laneCount - placedBar.laneIndex - placedBar.laneSpan
        ).coerceAtLeast(0) * laneHeightDp
      val itemHeightDp = (placedBar.laneSpan * laneHeightDp).coerceAtLeast(1f)
      val cardHeightDp = (itemHeightDp - NormalTimelineItemVerticalPadding.value * 2)
        .coerceAtLeast(1f)
      val isStackedText = resolveNormalTimelineTextMode(
        widgetHeightDp = itemHeightDp,
        laneCount = 1,
        hasTimelineScale = false,
      ) == NormalTimelineTextMode.STACKED
      val preferredTitleSize = when {
        isStackedText -> 11
        placedBar.laneSpan >= 2 -> 10
        else -> 9
      }
      // 高度不足时只保留标题单行；内容不再与标题拼接，避免最小形态过密且难以辨认。
      val titleText = bar.item.title
      val safeFontScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
      val availableTextHeightDp = (
        cardHeightDp - NormalTimelineItemContentPadding.value * 2
        ).coerceAtLeast(1f)
      val titleLineLimit = if (isStackedText) {
        // 窄卡片先按 9sp 下限反推实际可容纳行数，再据此选字号，避免估算为三行但高度只有两行。
        (availableTextHeightDp /
          (NormalTimelineMinimumStackedTitleSizeSp * safeFontScale *
            NormalTimelineTextLineHeightFactor))
          .toInt().coerceIn(1, 3)
      } else {
        1
      }
      val contentPaddingHorizontal = if (barWidth < NormalTimelineNarrowItemWidthThreshold.value) {
        NormalTimelineNarrowItemContentPadding
      } else {
        NormalTimelineItemContentPadding
      }
      val actualTitleWidthDp = (
        barWidth - contentPaddingHorizontal.value * 2
        ).coerceAtLeast(1f)
      // 字号选择保留安全余量，但实际行数必须按真实内容宽度计算，避免保守估算反而制造省略号。
      val titleStyleWidthDp = (actualTitleWidthDp * NormalTimelineTitleWidthSafetyRatio)
        .coerceAtLeast(1f)
      val initialTitleStyle = resolveNormalTimelineTitleStyle(
        text = titleText,
        availableWidthDp = titleStyleWidthDp,
        preferredFontSizeSp = preferredTitleSize,
        minFontSizeSp = if (isStackedText) NormalTimelineMinimumStackedTitleSizeSp else 9,
        maxLines = titleLineLimit,
      )
      val initialContentSizeSp = minOf(9, initialTitleStyle.fontSizeSp - 1).coerceAtLeast(6)
      val initialTextAllocation = resolveNormalTimelineTextAllocation(
        title = titleText,
        content = if (isStackedText) bar.item.content else "",
        availableWidthDp = actualTitleWidthDp,
        availableHeightDp = availableTextHeightDp,
        titleFontSizeSp = initialTitleStyle.fontSizeSp,
        contentFontSizeSp = initialContentSizeSp,
        maxTitleLines = initialTitleStyle.maxLines,
        maxContentLines = if (isStackedText) 2 else 0,
        fontScale = fontScale,
      )
      // 首轮字号导致高度只能容纳更少行时，必须用最终行数再次选字号，不能继续沿用三行字号。
      val titleStyle = if (initialTextAllocation.titleLines < initialTitleStyle.maxLines) {
        resolveNormalTimelineTitleStyle(
          text = titleText,
          availableWidthDp = titleStyleWidthDp,
          preferredFontSizeSp = preferredTitleSize,
          minFontSizeSp = if (isStackedText) NormalTimelineMinimumStackedTitleSizeSp else 9,
          maxLines = initialTextAllocation.titleLines,
        )
      } else {
        initialTitleStyle
      }
      // 内容始终比标题至少小 1sp；标题为完整展示降到下限时，内容不会反过来比标题更大。
      val contentSizeSp = minOf(9, titleStyle.fontSizeSp - 1).coerceAtLeast(6)
      val textAllocation = resolveNormalTimelineTextAllocation(
        title = titleText,
        content = if (isStackedText) bar.item.content else "",
        availableWidthDp = actualTitleWidthDp,
        availableHeightDp = availableTextHeightDp,
        titleFontSizeSp = titleStyle.fontSizeSp,
        contentFontSizeSp = contentSizeSp,
        maxTitleLines = titleStyle.maxLines,
        maxContentLines = if (isStackedText) 2 else 0,
        fontScale = fontScale,
      )
      val showContent = textAllocation.contentLines > 0
      WidgetRenderItemCard(
        item = bar.item,
        modifier = GlanceModifier.width(barWidth.dp).fillMaxHeight()
          .padding(top = topPaddingDp.dp, bottom = bottomPaddingDp.dp)
          // 横向不能加外边距，否则可见色块会偏离真实时间坐标；纵向仍留出轨道间隔。
          .padding(vertical = NormalTimelineItemVerticalPadding),
        // 旧版小组件没有夜间色资源，固定使用课表的浅色样式才能保持原有橙/红/蓝配色。
        isDark = false,
        titleSizeSp = titleStyle.fontSizeSp,
        contentSizeSp = contentSizeSp,
        maxTitleLines = textAllocation.titleLines,
        maxContentLines = textAllocation.contentLines.coerceAtLeast(1),
        showContent = showContent,
        inlineContent = false,
        topBottomText = showContent,
        // 上下布局已经用弹性 Spacer 分隔，额外固定间隔会挤压三行标题和底部内容。
        textGap = 0.dp,
        // 所有高度分支共用固定四边内容边距，避免三行标题与普通条目的上下留白不一致。
        contentPaddingHorizontal = contentPaddingHorizontal,
        contentPaddingVertical = NormalTimelineItemContentPadding,
        containerColorOverride = NormalTrackColor,
        coverTipColor = if (overflowBars.any(bar::overlaps)) {
          widgetColorProvider(Color(bar.item.lightStyle.contentArgb))
        } else {
          null
        },
        renderSize = DpSize(
          width = barWidth.dp,
          height = cardHeightDp.dp,
        ),
      )
      cursor = bar.endRatio
    }
    val trailingWidth = (groupEndRatio - cursor).coerceAtLeast(0f) * trackWidth.value
    if (trailingWidth > 0f) Spacer(GlanceModifier.width(trailingWidth.dp).fillMaxHeight())
  }
}

/**
 * 按原始时间边缘绘制一条低优先级溢出轨道。
 *
 * 低层仍使用完整卡片：被高层覆盖的区间只露出 2dp 底卡边缘，未被覆盖且高度足够的区间
 * 继续显示标题、内容并响应点击，不会因为进入折叠层就无条件丢失信息。
 */
@Composable
private fun NormalTimelineOverflowLane(
  bars: List<NormalTimelineBar>,
  trackWidth: Dp,
  groupBeginRatio: Float,
  groupEndRatio: Float,
  bottomInset: Dp,
  availableHeightDp: Float,
) {
  val availableItemHeightDp = (availableHeightDp - bottomInset.value).coerceAtLeast(1f)
  val textMode = resolveNormalTimelineTextMode(
    widgetHeightDp = availableItemHeightDp,
    laneCount = 1,
    hasTimelineScale = false,
  )
  val isStackedText = textMode == NormalTimelineTextMode.STACKED
  Row(modifier = GlanceModifier.fillMaxSize().padding(bottom = bottomInset)) {
    var cursor = groupBeginRatio
    bars.forEach { bar ->
      val gapWidth = (bar.beginRatio - cursor).coerceAtLeast(0f) * trackWidth.value
      if (gapWidth > 0f) Spacer(GlanceModifier.width(gapWidth.dp).fillMaxHeight())
      val barWidth = (bar.endRatio - bar.beginRatio).coerceAtLeast(0f) * trackWidth.value
      Box(
        modifier = GlanceModifier.width(barWidth.dp).fillMaxHeight(),
      ) {
        val titleStyle = resolveNormalTimelineTitleStyle(
          text = bar.item.title,
          availableWidthDp = (barWidth - NormalTimelineItemContentPadding.value * 2)
            .coerceAtLeast(1f),
          preferredFontSizeSp = if (isStackedText) 11 else 9,
          maxLines = if (isStackedText) 3 else 1,
        )
        WidgetRenderItemCard(
          item = bar.item,
          // 普通小组件与正文卡片一致，固定复用课表浅色样式。
          isDark = false,
          modifier = GlanceModifier.fillMaxSize(),
          titleSizeSp = titleStyle.fontSizeSp,
          contentSizeSp = minOf(9, titleStyle.fontSizeSp - 1).coerceAtLeast(8),
          maxTitleLines = titleStyle.maxLines,
          maxContentLines = if (isStackedText) 2 else 1,
          showContent = isStackedText,
          topBottomText = isStackedText,
          contentPaddingHorizontal = NormalTimelineItemContentPadding,
          contentPaddingVertical = NormalTimelineItemContentPadding,
          containerColorOverride = NormalTrackColor,
          renderSize = DpSize(
            width = barWidth.dp,
            height = availableItemHeightDp.dp,
          ),
        )
      }
      cursor = bar.endRatio
    }
    val trailingWidth = (groupEndRatio - cursor).coerceAtLeast(0f) * trackWidth.value
    if (trailingWidth > 0f) Spacer(GlanceModifier.width(trailingWidth.dp).fillMaxHeight())
  }
}

/** 深蓝竖栏用上/下箭头切换星期，点击中间标签回到今天。 */
@Composable
private fun DayNavigator(label: String) {
  Column(
    modifier = GlanceModifier.width(34.dp).fillMaxHeight()
      .background(widgetColorProvider(Color(0xFF2A4E84))).cornerRadius(8.dp)
      .padding(vertical = 3.dp),
    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    verticalAlignment = Alignment.Vertical.CenterVertically,
  ) {
    NavigatorCell(
      text = "⌃",
      delta = -1,
      modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
    )
    NavigatorCell(
      text = label,
      delta = 0,
      modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
    )
    NavigatorCell(
      text = "⌄",
      delta = 1,
      modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
    )
  }
}

/** 将切周导航等分成三个区域，使中间周标签在组件内保持几何居中。 */
@Composable
private fun NavigatorCell(
  text: String,
  delta: Int,
  modifier: GlanceModifier,
) {
  Box(
    modifier = modifier.clickable(
      actionRunCallback<ChangeNormalDayAction>(actionParametersOf(DayDeltaKey to delta)),
    ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = navigatorStyle(),
      maxLines = 1,
    )
  }
}

/** 统一导航栏白色粗体样式。 */
private fun navigatorStyle() = TextStyle(
  color = widgetColorProvider(Color.White),
  fontSize = 12.sp,
  fontWeight = FontWeight.Bold,
  textAlign = TextAlign.Center,
)

/** 普通 Widget 复刻旧版始终为浅色的白色底板。 */
private val NormalSurfaceColor = widgetColorProvider(Color.White)

/** 无条目区域使用轻微灰度区分轨道，同时保持旧版浅色视觉。 */
private val NormalTrackColor = widgetColorProvider(Color(0xFFF7F8FA))

/** 顶部小时刻度使用弱化蓝灰色，避免与课程标题争夺视觉层级。 */
private val NormalTimelineScaleColor = widgetColorProvider(Color(0xFF7A879B))

/** 与课表 drawNowTimeLine 保持一致的灰色。 */
private val NormalTimelineCurrentColor = widgetColorProvider(Color.Gray)

private val NormalTimelineScaleHeight = 12.dp
private val NormalTimelineMarkWidth = 14.dp
private val NormalTimelineCurrentLineWidth = 1.dp
private val NormalTimelineCurrentPointDiameter = 4.dp
private val NormalTimelineItemContentPadding = 2.dp
private val NormalTimelineNarrowItemContentPadding = 1.dp
private val NormalTimelineNarrowItemWidthThreshold = 40.dp
private val NormalTimelineItemVerticalPadding = 1.dp
private val NormalTimelineMaxOverflowStackHeight = 3.dp
private val NormalWidgetOuterPadding = 4.dp
private const val NormalTimelineMinimumStackedTitleSizeSp = 9
private const val NormalTimelineTextLineHeightFactor = 1.2f
private const val NormalTimelineTitleWidthSafetyRatio = 0.85f
private const val NormalTimelineMarksPerGroup = 4
// Box 连同最上层可见 Column 最多保留 10 个直接子节点，最多可展示 9 条溢出轨道边缘。
private const val NormalTimelineMaxOverflowEdges = 9

/** 与旧版普通小组件一致，非今天时显示目标星期。 */
private val NormalDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
