package com.cyxbs.pages.widget.page

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.pages.widget.api.CourseWidgetTimelineSection
import com.cyxbs.pages.widget.widget.glance.DEFAULT_OVERSIZED_TIMELINE_SECTIONS
import com.cyxbs.pages.widget.widget.glance.WidgetItemContainerGap
import com.cyxbs.pages.widget.widget.glance.WidgetItemInnerCornerRadius
import com.cyxbs.pages.widget.widget.glance.WidgetItemOuterCornerRadius
import com.cyxbs.pages.widget.widget.glance.WidgetStripePitchDp
import com.cyxbs.pages.widget.widget.glance.WidgetStripeWidthDp
import com.cyxbs.pages.widget.widget.glance.WidgetWeekItemInnerPadding
import com.cyxbs.pages.widget.widget.glance.WidgetWeekItemVerticalPadding
import com.cyxbs.pages.widget.widget.glance.resolveOversizedVisibleDays
import com.cyxbs.pages.widget.widget.glance.resolveOversizedDayTextLayout
import com.cyxbs.pages.widget.widget.normal.resolveNormalTimelineCardTextLayout
import com.cyxbs.pages.widget.widget.oversize.resolveOversizedMinuteOffset
import com.cyxbs.pages.widget.widget.oversize.resolveOversizedExpandableTimeLabels
import com.cyxbs.pages.widget.widget.oversize.resolveOversizedSectionHeights
import com.cyxbs.pages.widget.widget.oversize.resolveOversizedTimelineWidth
import com.cyxbs.pages.widget.widget.single.SingleWidgetDividerHeight
import com.cyxbs.pages.widget.widget.single.SingleWidgetHorizontalPadding
import com.cyxbs.pages.widget.widget.single.resolveSingleWidgetTextLayout

/**
 * 设置页与图片导出共用的确定性示例条目。
 *
 * day 是周一开始的 0..6；事务使用斜纹，课程使用纯色，所有数据只用于预览，不参与点击和课表缓存。
 */
private data class PreviewCourseItem(
  val day: Int,
  val title: String,
  val content: String,
  val beginMinute: Int,
  val endMinute: Int,
  val background: Color,
  val foreground: Color,
  val isAffair: Boolean = false,
  /** 与真实快照一致：数值越小，绘制时越靠近用户。 */
  val renderLayer: Int = 0,
)

/** 横向预览中条目占用的起始层和层数；只用于固定示例，不参与真实组件排布。 */
private data class PreviewDayBar(
  val item: PreviewCourseItem,
  val lane: Int,
  val laneSpan: Int,
)

// 示例课程沿用课表的时段色：上午橙、下午红、晚上蓝，避免预览与桌面实物错色。
private val PreviewMorningBackground = Color(0xFFF9E7D8)
private val PreviewMorningForeground = Color(0xFFFF8015)
private val PreviewAfternoonBackground = Color(0xFFF9E3E4)
private val PreviewAfternoonForeground = Color(0xFFFF6262)
private val PreviewEveningBackground = Color(0xFFDDE3F8)
private val PreviewEveningForeground = Color(0xFF4066EA)

// 固定示例取自当前课表的一周可见课程和事务；节次时间与课表下发的 1~12 节边界严格一致。
// 保持固定数据是为了让图片导出可复现，不在通用静态预览里持久化或绑定真实账号快照。
private val PreviewItems = listOf(
  PreviewCourseItem(0, "通信原理A", "3105", 14 * 60, 15 * 60 + 40, PreviewAfternoonBackground, PreviewAfternoonForeground),
  PreviewCourseItem(0, "射频通信电路", "3105", 19 * 60, 21 * 60 + 35, PreviewEveningBackground, PreviewEveningForeground),
  PreviewCourseItem(2, "通信原理A", "3105", 8 * 60, 9 * 60 + 40, PreviewMorningBackground, PreviewMorningForeground),
  PreviewCourseItem(2, "物流成本管理", "3210", 10 * 60 + 15, 11 * 60 + 55, PreviewMorningBackground, PreviewMorningForeground),
  PreviewCourseItem(2, "数字信号处理", "3103", 19 * 60, 20 * 60 + 40, PreviewEveningBackground, PreviewEveningForeground),
  PreviewCourseItem(3, "FPGA/Verilog技术基础与工程应用", "ZXJ10/YF313", 14 * 60, 17 * 60 + 55, PreviewAfternoonBackground, PreviewAfternoonForeground),
  PreviewCourseItem(3, "中午", "", 12 * 60, 14 * 60, Color.White, Color(0xFF112C57), true, 4),
  PreviewCourseItem(4, "IT科技英语", "4201", 16 * 60 + 15, 17 * 60 + 55, PreviewAfternoonBackground, PreviewAfternoonForeground),
  PreviewCourseItem(4, "嵌入式与Linux程序设计", "YF312", 19 * 60, 22 * 60 + 30, PreviewEveningBackground, PreviewEveningForeground),
)

private val PreviewInk = Color(0xFF15315B)
private val PreviewBlue = Color(0xFF2A4E84)
private val PreviewWhite = Color.White
private val PreviewTrack = Color(0xFFF7F8FA)
private val PreviewOutline = Color(0xFFD5DDE8)
/**
 * 小组件目录页的 Compose 示例入口。
 *
 * 三类预览始终使用固定测试数据，避免导出图片时因设备日期和真实账号不同而产生不可复现的内容。
 */
@Composable
internal fun CourseWidgetSamplePreview(kind: CourseWidgetKind, modifier: Modifier = Modifier) {
  // Material 正文默认有 0.5sp 字间距；Glance TextView 无此间距，窄列预览需清除继承值。
  CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(letterSpacing = 0.sp)) {
    when (kind) {
      CourseWidgetKind.CURRENT_COURSE -> Box(modifier, contentAlignment = Alignment.Center) {
        // 预览只绘制真实组件的透明白字；目录卡片提供可读的浅蓝展示环境。
        CompactWidgetSamplePreview(Modifier.width(151.dp).height(80.dp))
      }
      CourseWidgetKind.DAY_TIMELINE -> DayTimelineSamplePreview(modifier)
      CourseWidgetKind.WEEK_TIMETABLE -> WeekTimetableSampleGallery(modifier)
    }
  }
}

/** 复用实际 2×1 组件的字号降级顺序与标题行数计算；微弱字影仅帮助浅色卡片上的白字辨认。 */
@Composable
private fun CompactWidgetSamplePreview(modifier: Modifier = Modifier) {
  val title = "通信原理A"
  val metrics = LocalContext.current.resources.displayMetrics
  val textStyle = TextStyle(
    shadow = Shadow(Color(0x99516A91), Offset(0f, 2f), blurRadius = 3f),
  )
  BoxWithConstraints(modifier) {
    val contentWidth = (maxWidth - SingleWidgetHorizontalPadding * 2).coerceAtLeast(1.dp)
    val layout = remember(
      title, contentWidth, maxHeight, metrics.densityDpi, metrics.scaledDensity,
    ) {
      resolveSingleWidgetTextLayout(title, contentWidth, maxHeight, metrics)
    }
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = SingleWidgetHorizontalPadding),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        "8:00–09:40", color = PreviewWhite, fontSize = layout.timeSizeSp.sp,
        maxLines = 1, style = textStyle,
      )
      Text(
        title, color = PreviewWhite, fontSize = layout.titleSizeSp.sp,
        maxLines = layout.titleMaxLines, overflow = TextOverflow.Ellipsis, style = textStyle,
      )
      Box(Modifier.fillMaxWidth().height(SingleWidgetDividerHeight).background(PreviewWhite))
      Text(
        "3105", color = PreviewWhite, fontSize = layout.contentSizeSp.sp,
        maxLines = 1, style = textStyle,
      )
    }
  }
}

/** 按实际分钟比例放置示例横条；局部三重叠使用三层，独立课程占满轨道。 */
@Composable
private fun DayTimelineSamplePreview(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.clip(RoundedCornerShape(12.dp)).background(PreviewWhite)
      .border(1.dp, PreviewOutline, RoundedCornerShape(12.dp)).padding(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.width(34.dp).fillMaxHeight()
        .clip(RoundedCornerShape(8.dp)).background(PreviewBlue),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      Text("⌃", color = PreviewWhite, fontSize = 15.sp, lineHeight = 15.sp)
      Text("今", color = PreviewWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      Text("⌄", color = PreviewWhite, fontSize = 15.sp, lineHeight = 15.sp)
    }
    BoxWithConstraints(
      Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp)
        .clip(RoundedCornerShape(6.dp)).background(PreviewTrack),
    ) {
      val timelineWidth = maxWidth
      val markWidth = 14.dp
      val timelineOrigin = markWidth / 2
      val trackWidth = (timelineWidth - timelineOrigin).coerceAtLeast(1.dp)
      val scaleHeight = 12.dp
      val laneHeight = (maxHeight - scaleHeight) / 3
      val fontScale = LocalConfiguration.current.fontScale
      listOf(8, 10, 12, 14, 16, 18, 20, 22).forEach { hour ->
        // 与真实组件一样：刻度盒子左侧从比例坐标开始，中心和课程起点同在半盒宽后的时间点。
        val x = trackWidth * ((hour * 60 - 8 * 60) / (14.5f * 60))
        Text(
          hour.toString(), color = Color(0xFF7A879B), fontSize = 9.sp,
          modifier = Modifier.offset(x = x).width(markWidth),
          textAlign = TextAlign.Center, maxLines = 1,
        )
      }
      // 中组件为展示三层重叠形态，把本周不同日期的真实课程合成一条示意时间轴；
      // 每门课程与事务仍保持它在课表中的原始起止时刻，不再人为挪到错误的节次。
      val bars = listOf(
        PreviewDayBar(PreviewItems[6], lane = 0, laneSpan = 3),
        PreviewDayBar(PreviewItems[2], lane = 0, laneSpan = 3),
        PreviewDayBar(PreviewItems[5], lane = 0, laneSpan = 2),
        PreviewDayBar(PreviewItems[3], lane = 0, laneSpan = 3),
        PreviewDayBar(PreviewItems[7], lane = 2, laneSpan = 1),
        PreviewDayBar(PreviewItems[8], lane = 0, laneSpan = 3),
      )
      bars.forEach { (item, lane, laneSpan) ->
        val begin = ((item.beginMinute - 8 * 60) / (14.5f * 60)).coerceIn(0f, 1f)
        val end = ((item.endMinute - 8 * 60) / (14.5f * 60)).coerceIn(0f, 1f)
        if (end > begin) {
          val barWidth = (trackWidth * (end - begin)).coerceAtLeast(1.dp)
          val itemHeight = laneHeight * laneSpan
          // 与桌面横向日课表共用文字分配：宽条可以缩小字号并展示多行标题。
          val textLayout = resolveNormalTimelineCardTextLayout(
            title = item.title,
            content = item.content,
            barWidthDp = barWidth.value,
            itemHeightDp = itemHeight.value,
            laneSpan = laneSpan,
            fontScale = fontScale,
          )
          PreviewItemCard(
            item = item,
            titleSizeSp = textLayout.titleSizeSp,
            contentSizeSp = textLayout.contentSizeSp,
            showContent = textLayout.showContent,
            topBottomText = textLayout.showContent,
            maxTitleLines = textLayout.titleLines,
            maxContentLines = textLayout.contentLines,
            innerHorizontalPadding = textLayout.contentPaddingHorizontal,
            innerVerticalPadding = textLayout.contentPaddingVertical,
            containerColor = PreviewTrack,
            modifier = Modifier.offset(
              x = timelineOrigin + trackWidth * begin,
              y = scaleHeight + laneHeight * lane + 1.dp,
            )
              .width(barWidth)
              .height((itemHeight - 2.dp).coerceAtLeast(1.dp)),
          )
        }
      }
    }
  }
}

/**
 * 展示周课表从 1 天到 7 天的宽度折叠过程；箭头位于两个完整示例之间。
 *
 * 横向滚动只属于设置页预览，不暗示桌面小组件本身可横向滚动。
 */
@Composable
private fun WeekTimetableSampleGallery(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    listOf(1, 3, 5, 7).forEachIndexed { index, days ->
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$days 天", color = PreviewInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        WeekTimetableSamplePreview(
          dayCount = days,
          // 周课表支持纵向扩高；示例保留足够的课程块高度，才能展示真实组件允许的多行标题。
          modifier = Modifier.width(weekPreviewWidth(days)).height(450.dp)
            .padding(top = 5.dp),
        )
      }
      if (index < 3) {
        Text("→", color = PreviewBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

/** 真机 3 天组件主体约 151dp，扣除 28dp 时间轴后，每天约 41dp；四档沿用该列宽。 */
private fun weekPreviewWidth(dayCount: Int): Dp =
  resolveOversizedTimelineWidth(dayCount) + 41.dp * dayCount

/**
 * 单一宽度档位的周课表预览。
 *
 * 日期列沿用实际小组件的居中选日规则；纵向坐标复用小组件的折叠分段算法，示例课程与事务
 * 因而能落在和桌面组件相同的时间区域。
 */
@Composable
private fun WeekTimetableSamplePreview(dayCount: Int, modifier: Modifier = Modifier) {
  val visibleDays = resolveOversizedVisibleDays(weekPreviewWidth(dayCount).value, today = 2)
  val fontScale = LocalConfiguration.current.fontScale
  Column(
    modifier = modifier.clip(RoundedCornerShape(14.dp)).background(PreviewWhite)
      .border(1.dp, PreviewOutline, RoundedCornerShape(14.dp)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().height(44.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        if (dayCount == 1) "9\n月" else "9月",
        color = PreviewInk,
        fontSize = if (dayCount <= 3) 7.sp else 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(resolveOversizedTimelineWidth(dayCount)),
        textAlign = TextAlign.Center,
      )
      visibleDays.forEach { day ->
        val selected = day == 2
        BoxWithConstraints(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          contentAlignment = Alignment.Center,
        ) {
          val cellPadding = if (dayCount == 1) 1.dp else 2.dp
          val side = minOf(
            (maxWidth - cellPadding * 2).coerceAtLeast(1.dp),
            maxHeight - cellPadding * 2,
          )
          Column(
            modifier = Modifier.size(side).clip(RoundedCornerShape(8.dp))
              .background(if (selected) PreviewBlue else PreviewWhite),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              "周${"一二三四五六日"[day]}",
              color = if (selected) PreviewWhite else PreviewInk,
              fontSize = if (dayCount == 1) 10.sp else 11.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
            )
            Text(
              "${21 + day}日", color = if (selected) PreviewWhite else PreviewInk,
              fontSize = if (dayCount == 1) 9.sp else 10.sp, maxLines = 1,
            )
          }
        }
      }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val axisWidth = resolveOversizedTimelineWidth(dayCount)
      val contentWidth = (maxWidth - axisWidth).coerceAtLeast(1.dp)
      val dayWidth = contentWidth / dayCount
      val sections = DEFAULT_OVERSIZED_TIMELINE_SECTIONS
      val sectionHeights = resolveOversizedSectionHeights(sections, 0, maxHeight.value)
      PreviewWeekTimeAxis(sections, sectionHeights, axisWidth, maxHeight, dayCount == 1)
      visibleDays.forEachIndexed { column, day ->
        // 与 Glance 一致：每天只占一整列，低优先级先画，高优先级叠在其上。
        val dayItems = PreviewItems.filter { it.day == day }
          .sortedWith(compareByDescending<PreviewCourseItem> { it.renderLayer }
            .thenBy(PreviewItems::indexOf))
        dayItems.forEachIndexed { drawIndex, item ->
          val top = resolveOversizedMinuteOffset(sections, sectionHeights, item.beginMinute)
          val bottom = resolveOversizedMinuteOffset(sections, sectionHeights, item.endMinute)
          if (bottom > top) {
            val cardHeight = ((bottom - top).dp - WidgetWeekItemVerticalPadding * 2)
              .coerceAtLeast(1.dp)
            // 预览与桌面组件共用相同的 4dp 文字内边距和行数分配规则。
            val textLayout = resolveOversizedDayTextLayout(
              title = item.title,
              content = item.content,
              cardWidthDp = dayWidth.value,
              cardHeightDp = cardHeight.value,
              contentPaddingHorizontalDp = WidgetWeekItemInnerPadding.value,
              contentPaddingVerticalDp = WidgetWeekItemInnerPadding.value,
              fontScale = fontScale,
            )
            val hasUnderlyingOverlap = dayItems.take(drawIndex).any { other ->
              item.beginMinute < other.endMinute && other.beginMinute < item.endMinute
            }
            PreviewItemCard(
              item = item,
              titleSizeSp = textLayout.titleSizeSp,
              contentSizeSp = textLayout.contentSizeSp,
              showContent = textLayout.showContent,
              topBottomText = true,
              maxTitleLines = textLayout.titleLines,
              maxContentLines = textLayout.contentLines,
              showTip = hasUnderlyingOverlap,
              innerHorizontalPadding = WidgetWeekItemInnerPadding,
              innerVerticalPadding = WidgetWeekItemInnerPadding,
              modifier = Modifier.offset(
                x = axisWidth + dayWidth * column,
                y = top.dp + WidgetWeekItemVerticalPadding,
              ).width(dayWidth).height(cardHeight),
            )
          }
        }
      }
    }
  }
}

/** 直接使用 Glance 的折叠分段高度和标签定位，避免预览刻度与真实组件错位。 */
@Composable
private fun PreviewWeekTimeAxis(
  sections: List<CourseWidgetTimelineSection>,
  sectionHeights: List<Float>,
  axisWidth: Dp,
  timelineHeight: Dp,
  isSingleCell: Boolean,
) {
  val labelHeight = if (isSingleCell) 14.dp else 12.dp
  val labelWidth = axisWidth - if (isSingleCell) 0.dp else 4.dp
  val labelSize = if (isSingleCell) 6.sp else if (axisWidth <= 28.dp) 7.sp else 8.sp
  var sectionTop = 0f
  sections.forEachIndexed { index, section ->
    val sectionHeight = sectionHeights[index]
    if (!section.expandable && section.collapsedLabel.isNotBlank() &&
      sectionHeight >= if (isSingleCell) 10f else 12f
    ) {
      val labels = section.collapsedLabel.split('\n')
      labels.forEachIndexed { labelIndex, label ->
        val center = sectionTop + sectionHeight * (labelIndex + 0.5f) / labels.size
        Text(
          label, color = PreviewInk, fontSize = labelSize,
          modifier = Modifier.offset(y = (center - labelHeight.value / 2).dp)
            .width(labelWidth).height(labelHeight),
          textAlign = TextAlign.Center, maxLines = 1,
        )
      }
    }
    sectionTop += sectionHeight
  }
  resolveOversizedExpandableTimeLabels(
    sections = sections,
    sectionHeights = sectionHeights,
    expandedTimelineMask = 0,
    labelHeightDp = labelHeight.value,
    timelineHeightDp = timelineHeight.value,
    bottomLabelInsetDp = if (isSingleCell) 2f else 0f,
  ).forEach { mark ->
    Text(
      mark.text, color = PreviewInk, fontSize = labelSize,
      modifier = Modifier.offset(y = mark.topDp.dp)
        .width(labelWidth).height(labelHeight),
      textAlign = TextAlign.Center, maxLines = 1,
    )
  }
}

/** 按真实 Glance 卡片的外底卡、内容层、文字层和独立 tips 层绘制预览。 */
@Composable
private fun PreviewItemCard(
  item: PreviewCourseItem,
  titleSizeSp: Int,
  modifier: Modifier = Modifier,
  containerColor: Color = PreviewWhite,
  contentSizeSp: Int = 8,
  showContent: Boolean? = null,
  topBottomText: Boolean = false,
  showTip: Boolean = false,
  maxTitleLines: Int? = null,
  maxContentLines: Int = 2,
  innerHorizontalPadding: Dp = 3.dp,
  innerVerticalPadding: Dp = 2.dp,
  titleTopPadding: Dp = 0.dp,
) {
  Box(
    modifier = modifier.clip(RoundedCornerShape(WidgetItemOuterCornerRadius))
      .background(containerColor),
  ) {
    Box(
      Modifier.fillMaxSize().padding(WidgetItemContainerGap)
        .clip(RoundedCornerShape(WidgetItemInnerCornerRadius))
        // 纯事务的斜纹间隔透出外底卡色，而非测试数据里占位的透明背景。
        .background(if (item.isAffair) containerColor else item.background),
    ) {
      if (item.isAffair) {
        Canvas(Modifier.matchParentSize()) {
          val pitch = WidgetStripePitchDp.dp.toPx()
          var x = -size.height
          while (x < size.width + size.height) {
            drawLine(
              color = Color(0xFFE4E7EC),
              start = Offset(x, size.height),
              end = Offset(x + size.height, 0f),
              strokeWidth = WidgetStripeWidthDp.dp.toPx(),
            )
            x += pitch
          }
        }
      }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(
      horizontal = innerHorizontalPadding + WidgetItemContainerGap,
      vertical = innerVerticalPadding + WidgetItemContainerGap,
    )) {
      // 实际可用高度不足时，预览只保留标题；不能把两段文字压进同一小块。
      val minimumTwoTextHeight = if (topBottomText) 45.dp else 27.dp
      val canShowContent = showContent ?: (maxHeight >= minimumTwoTextHeight)
      val titleLines = maxTitleLines ?: when {
        !topBottomText -> 1
        maxHeight < 28.dp -> 1
        maxHeight < 45.dp -> 2
        else -> 3
      }
      Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (topBottomText) Arrangement.Top else Arrangement.Center,
      ) {
        if (topBottomText && titleTopPadding > 0.dp) {
          Box(Modifier.fillMaxWidth().height(titleTopPadding))
        }
        Text(
          item.title, color = item.foreground, fontSize = titleSizeSp.sp,
          lineHeight = (titleSizeSp + 1).sp,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          maxLines = titleLines,
          overflow = TextOverflow.Ellipsis,
        )
        if (canShowContent && item.content.isNotBlank()) {
          Box(Modifier.weight(1f))
          Text(
            item.content, color = item.foreground, fontSize = contentSizeSp.sp,
            lineHeight = (contentSizeSp + 1).sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            maxLines = maxContentLines.coerceAtLeast(1), overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    if (showTip) {
      Box(Modifier.fillMaxSize().padding(top = 3.dp, end = 4.dp),
        contentAlignment = Alignment.TopEnd) {
        Box(Modifier.width(6.dp).height(2.dp)
          .clip(RoundedCornerShape(1.dp)).background(item.foreground))
      }
    }
  }
}

/** 供设计检查及后续导出系统静态预览图使用；图片导出后再替换 previewImage 资源。 */
@Preview(widthDp = 151, heightDp = 80, showBackground = false)
@Composable
private fun CompactWidgetImagePreview() {
  CourseWidgetSamplePreview(CourseWidgetKind.CURRENT_COURSE, Modifier.fillMaxSize())
}

/** 横向甘特组件的单张静态预览导出入口。 */
@Preview(widthDp = 360, heightDp = 100, showBackground = false)
@Composable
private fun DayTimelineWidgetImagePreview() {
  CourseWidgetSamplePreview(CourseWidgetKind.DAY_TIMELINE, Modifier.fillMaxSize())
}

/** 单一周课表尺寸直接导出组件本体，边界由自身的细描边表示。 */
@Composable
private fun WeekTimetableImagePreview(dayCount: Int) {
  WeekTimetableSamplePreview(dayCount, Modifier.fillMaxSize())
}

/** 1 天周课表的独立导出入口。 */
@Preview(widthDp = 65, heightDp = 310, showBackground = false)
@Composable
private fun WeekTimetableOneDayImagePreview() {
  WeekTimetableImagePreview(1)
}

/** 3 天周课表的独立导出入口。 */
@Preview(widthDp = 151, heightDp = 310, showBackground = false)
@Composable
private fun WeekTimetableThreeDayImagePreview() {
  WeekTimetableImagePreview(3)
}

/** 5 天周课表的独立导出入口。 */
@Preview(widthDp = 237, heightDp = 310, showBackground = false)
@Composable
private fun WeekTimetableFiveDayImagePreview() {
  WeekTimetableImagePreview(5)
}

/** 7 天周课表的独立导出入口；系统静态图建议使用这一档。 */
@Preview(widthDp = 327, heightDp = 310, showBackground = false)
@Composable
private fun WeekTimetableSevenDayImagePreview() {
  WeekTimetableImagePreview(7)
}

/** Android Studio 独立预览将完整的 7 天板块放在第二行；设置页 Gallery 不受影响。 */
@Preview(widthDp = 560, heightDp = 310, showBackground = false)
@Composable
private fun WeekTimetableGalleryPreview() {
  // 只调整设计预览的板块位置，不复用设置页入口，避免改变应用内的横向滚动展示。
  CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(letterSpacing = 0.sp)) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(1, 3, 5).forEachIndexed { index, days ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$days 天", color = PreviewInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            WeekTimetableSamplePreview(
              dayCount = days,
              modifier = Modifier.width(weekPreviewWidth(days)).height(450.dp).padding(top = 5.dp),
            )
          }
          if (index < 2) {
            Text("→", color = PreviewBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
