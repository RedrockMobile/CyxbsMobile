package com.cyxbs.pages.schedule.ui.timeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence

/** 每小时对应的像素高度，外部计算默认滚动位置时复用。 */
internal val HourHeight: Dp = 64.dp

/** 时间轴内容总高：00..24 共 24 小时 + 底部留白。 */
private val TimelineHeight: Dp = HourHeight * 24 + 24.dp

/** 左侧时间刻度列宽度。 */
private val AxisWidth: Dp = 44.dp

/** 统一间距：事件块与卡片左右边、相邻并列列之间、以及参考线两端都用它。 */
private val EventGap: Dp = 4.dp

/** 整日块（当日/未排期，跨 0-24 点）单块的最大宽度。 */
private val FullDayBlockMaxWidth: Dp = 96.dp

/** N 个整日块占用的右侧条总宽：单块封顶 [FullDayBlockMaxWidth]，整条封顶 [FullDayStripMax]，块多则缩。 */
private fun fullDayStripWidth(n: Int, totalWidth: Dp): Dp {
  return if (n <= 0) 0.dp else minOf(FullDayBlockMaxWidth * n, totalWidth * 0.5F)
}

/**
 * 第三层时间轴的「当天」视图：左侧 00..24 灰色小时刻度，右侧按分钟摆放事件。
 *
 * - 时间段类型：圆角区间块，块内显示标题与时间段。
 * - 截止类型：一条粗线 + 标题。
 * - 重叠事件并列分列展示（[layoutTimedSchedules]）。
 *
 * 左侧时间刻度与右侧事件区共用一个 [scrollState] 竖向**同步滚动**；右侧事件区的 middleBg 圆角卡片
 * **固定为视口大小、不随内容滚动**，内容在卡片内部滚动并被圆角裁剪，因此无论滚到哪，卡片圆角始终
 * 留在可视区四角（卡片若随内容滚动，上下圆角会被滚出视口）。
 *
 * 整日块（当日/未排期，跨 0-24 点）的彩色条在事件区里随内容滚动，标题放在不滚动的
 * [FullDayTitleOverlay] 层，垂直居中于可视区、右对齐到整日条所在列，天然 sticky。
 */
@Composable
fun ScheduleTimelinePane(
  timed: List<DayTimedSchedule>,
  scrollState: ScrollState,
  onScheduleClick: (ScheduleUiOccurrence) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val fullDay = remember(timed) { timed.filter { it.isFullDay() } }
  Box(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
    Row(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
      // 左侧时间刻度：独立滚动容器，与右侧共享 scrollState 保持同步。
      Box(
        modifier = Modifier
          .width(AxisWidth)
          .fillMaxHeight()
          .verticalScroll(scrollState),
      ) {
        TimeAxis(modifier = Modifier.fillMaxWidth().height(TimelineHeight))
      }
      // 右侧事件区：固定视口大小的圆角卡片（不滚动），内容在其内部滚动并被圆角裁剪。
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          // 同心圆角：外圆角 = 内块圆角(6) + 内块到卡片边的间距 EventGap(4)，使内外曲率一致。
          .clip(RoundedCornerShape(6.dp + EventGap))
          .background(colors.middleBg),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        ) {
          EventArea(
            timed = timed,
            onScheduleClick = onScheduleClick,
            modifier = Modifier.fillMaxWidth().height(TimelineHeight),
          )
        }
      }
    }
    if (fullDay.isNotEmpty()) {
      FullDayTitleOverlay(
        fullDay = fullDay,
        onScheduleClick = onScheduleClick,
        modifier = Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp),
      )
    }
  }
}

/** 左侧 00..24 灰色小时数字刻度列。纯静态标签，与事件区分离独立摆放。 */
@Composable
private fun TimeAxis(modifier: Modifier) {
  val colors = LocalAppColors.current
  Box(modifier = modifier) {
    for (h in 0..24) {
      Text(
        text = h.toString().padStart(2, '0'),
        fontSize = 11.sp,
        color = colors.tvLv2.copy(alpha = 0.4f),
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .offset(y = (HourHeight * h - 7.dp).coerceAtLeast(0.dp)),
      )
    }
  }
}

/**
 * 右侧事件区：一整块 middleBg 圆角底 + 按分钟/列号绝对摆放的事件块。
 *
 * 用自定义 [Layout]：measure 阶段直接拿宽度按列等分、measure 各事件块，place 阶段按
 * y=分钟、x=列号 绝对定位，省掉 `BoxWithConstraints` 的 subcomposition。
 */
@Composable
private fun EventArea(
  timed: List<DayTimedSchedule>,
  onScheduleClick: (ScheduleUiOccurrence) -> Unit,
  modifier: Modifier,
) {
  val colors = LocalAppColors.current
  // 整日块（跨 0-24 点）固定贴最右侧成条；有时刻事件在左侧区域按列摆放。
  val fullDay = remember(timed) { timed.filter { it.isFullDay() } }
  val timedOnly = remember(timed) { timed.filterNot { it.isFullDay() } }
  val positions = remember(timedOnly) { layoutTimedSchedules(timedOnly) }

  // middleBg + 8dp 圆角由父级固定卡片承载（不随滚动）；这里只画每小时一条淡参考线（随内容滚动），
  // 参考线只画在左侧有时刻区域（不穿过右侧整日条）。
  val gridColor = colors.tvLv2.copy(alpha = 0.08f)
  Layout(
    modifier = modifier
      .drawBehind {
        val strokeWidth = 1.dp.toPx()
        val hourPx = HourHeight.toPx()
        val inset = EventGap.toPx()
        val stripWidth = fullDayStripWidth(fullDay.size, size.width.toDp())
        val leftWidth = size.width - stripWidth.toPx()
        for (h in 1..23) {
          val y = hourPx * h
          drawLine(
            color = gridColor,
            start = Offset(inset, y),
            end = Offset((leftWidth - inset).coerceAtLeast(inset), y),
            strokeWidth = strokeWidth,
          )
        }
      },
    content = {
      // [0 until positions.size] 有时刻事件，按 positions 顺序。
      positions.forEach { pos ->
        val e = pos.event
        if (e.isInterval) {
          IntervalBlock(
            occurrence = e.occurrence,
            startMin = e.startMin,
            endMin = e.endMin,
            onClick = { onScheduleClick(e.occurrence) },
          )
        } else {
          DeadlineLine(
            occurrence = e.occurrence,
            onClick = { onScheduleClick(e.occurrence) },
          )
        }
      }
      // [positions.size ..] 整日块彩色条（无标题，标题在 overlay 层）。
      fullDay.forEach { e ->
        FullDayBar(occurrence = e.occurrence, onClick = { onScheduleClick(e.occurrence) })
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val totalHeightPx = TimelineHeight.roundToPx()
    val gapPx = EventGap.roundToPx()
    val stripWidth = fullDayStripWidth(fullDay.size, width.toDp())
    val stripPx = stripWidth.roundToPx()
    val leftWidth = (width - stripPx).coerceAtLeast(0)
    val n = fullDay.size

    // 左侧有时刻区域：左右各留 gap、相邻列之间留 gap，剩余等分给每列。
    fun colWidthOf(pos: PositionedTimedSchedule) =
      ((leftWidth - (pos.columnCount + 1) * gapPx) / pos.columnCount).coerceAtLeast(0)

    val timedPlaceables = positions.mapIndexed { i, pos ->
      val e = pos.event
      val hPx = if (e.isInterval) {
        // 整段时长占位减去上下各一个 gap，使四周留白与左右一致。
        val slotPx = (HourHeight * ((e.endMin - e.startMin) / 60f)).roundToPx()
        (slotPx - 2 * gapPx).coerceAtLeast(26.dp.roundToPx())
      } else {
        22.dp.roundToPx()
      }
      measurables[i].measure(Constraints.fixed(colWidthOf(pos), hPx))
    }

    // 右侧整日条：条内左右各留 gap、相邻块留 gap，剩余等分；高度同样上下各留一个 gap。
    val fullDayColPx = if (n > 0) ((stripPx - (n + 1) * gapPx) / n).coerceAtLeast(0) else 0
    val fullDayHeightPx = (totalHeightPx - 2 * gapPx).coerceAtLeast(0)
    val fullDayPlaceables = (0 until n).map { i ->
      measurables[positions.size + i].measure(Constraints.fixed(fullDayColPx, fullDayHeightPx))
    }

    layout(width, totalHeightPx) {
      timedPlaceables.forEachIndexed { i, p ->
        val pos = positions[i]
        val e = pos.event
        val x = gapPx + (colWidthOf(pos) + gapPx) * pos.columnIndex
        val y = if (e.isInterval) {
          // 顶部下移一个 gap，配合高度减少的 2*gap，使四周留白与左右一致。
          (HourHeight * (e.startMin / 60f)).roundToPx() + gapPx
        } else {
          ((HourHeight * (e.startMin / 60f)) - 18.dp).coerceAtLeast(0.dp).roundToPx()
        }
        p.place(x = x, y = y)
      }
      fullDayPlaceables.forEachIndexed { i, p ->
        val x = leftWidth + gapPx + (fullDayColPx + gapPx) * i
        p.place(x = x, y = gapPx)
      }
    }
  }
}

/** 整日块的彩色条（无标题）：跨 0-24 点全高，圆角纯色底。标题由 overlay 层渲染。 */
@Composable
private fun FullDayBar(
  occurrence: ScheduleUiOccurrence,
  onClick: () -> Unit,
) {
  val accent = occurrenceAccent(occurrence)
  Box(
    modifier = Modifier
      .fillMaxHeight()
      .clip(RoundedCornerShape(6.dp))
      .background(accent.copy(alpha = 0.14f))
      .clickableNoIndicator(onClick = onClick),
  )
}

/**
 * 不滚动的整日块标题层：垂直居中于可视区、右对齐到整日条所在列。
 *
 * 与 [EventArea] 用同一套 [fullDayStripWidth] / [EventGap] 计算，使标题正好压在对应彩色条上。
 */
@Composable
private fun FullDayTitleOverlay(
  fullDay: List<DayTimedSchedule>,
  onScheduleClick: (ScheduleUiOccurrence) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .layout { measurables, constraints ->
        // 仅占有一半的宽度
        val width = constraints.maxWidth
        val stripWidth = fullDayStripWidth(fullDay.size, width.toDp()).roundToPx()
        val placeable = measurables.measure(constraints.copy(maxWidth = stripWidth))
        layout(placeable.width, placeable.height) {
          placeable.place(x = 0, y = 0)
        }
      }
      .padding(horizontal = EventGap),
    horizontalArrangement = Arrangement.spacedBy(EventGap),
  ) {
    fullDay.forEach { e ->
      val accent = occurrenceAccent(e.occurrence)
      Box(
        modifier = Modifier
          .weight(1f)
          .clickableNoIndicator(onClick = { onScheduleClick(e.occurrence) }),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = e.occurrence.title,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = accent,
          textAlign = TextAlign.Center,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun IntervalBlock(
  occurrence: ScheduleUiOccurrence,
  startMin: Int,
  endMin: Int,
  onClick: () -> Unit,
) {
  val accent = occurrenceAccent(occurrence)
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(accent.copy(alpha = 0.14f))
      .clickableNoIndicator(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
      Text(
        text = occurrence.title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = accent,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "${minuteToHHmm(startMin)}-${minuteToHHmm(endMin)}",
        fontSize = 10.sp,
        color = accent.copy(alpha = 0.7f),
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun DeadlineLine(
  occurrence: ScheduleUiOccurrence,
  onClick: () -> Unit,
) {
  val accent = occurrenceAccent(occurrence)
  Box(
    modifier = Modifier.clickableNoIndicator(onClick = onClick),
  ) {
    Text(
      text = occurrence.title,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = accent,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(bottom = 5.dp),
    )
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .height(3.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(accent),
    )
  }
}

/** 按稳定分类 identity 选择强调色；是否过期由上层状态样式决定，避免颜色映射混入时间判断。 */
@Composable
private fun occurrenceAccent(occurrence: ScheduleUiOccurrence): Color {
  val colors = LocalAppColors.current
  return when (occurrence.categoryId?.value) {
    "study" -> colors.positive
    "life" -> MaterialTheme.colors.secondary
    else -> colors.tvLv4
  }
}

private fun minuteToHHmm(min: Int): String {
  val h = (min / 60).coerceIn(0, 24)
  val m = (min % 60).coerceIn(0, 59)
  return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
