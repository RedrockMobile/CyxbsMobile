package com.cyxbs.pages.widget.widget.glance

import com.cyxbs.pages.widget.api.CourseWidgetAction
import com.cyxbs.pages.widget.api.CourseWidgetBackgroundPattern
import com.cyxbs.pages.widget.api.CourseWidgetItemStyle
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineSection
import com.cyxbs.pages.widget.api.CourseWidgetVisibleRange
import com.cyxbs.pages.widget.api.CourseWidgetWeekSnapshot
import java.time.LocalDate
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 通用快照到三种 widget 布局的纯映射测试。 */
class WidgetGlanceModelsTest {

  /** 最大号课表按桌面一至四列显示 1、3、5、7 天，并让非整周窗口尽量围绕今天。 */
  @Test
  fun oversizedWidthSelectsOneThreeFiveOrSevenDays() {
    assertEquals(listOf(3), resolveOversizedVisibleDays(widgetWidthDp = 99f, today = 3))
    assertEquals(listOf(2, 3, 4), resolveOversizedVisibleDays(widgetWidthDp = 100f, today = 3))
    assertEquals(listOf(0, 1, 2), resolveOversizedVisibleDays(widgetWidthDp = 199f, today = 0))
    assertEquals(listOf(1, 2, 3, 4, 5), resolveOversizedVisibleDays(widgetWidthDp = 200f, today = 3))
    assertEquals(listOf(0, 1, 2, 3, 4), resolveOversizedVisibleDays(widgetWidthDp = 299f, today = 0))
    assertEquals(listOf(2, 3, 4, 5, 6), resolveOversizedVisibleDays(widgetWidthDp = 299f, today = 6))
    assertEquals((0..6).toList(), resolveOversizedVisibleDays(widgetWidthDp = 320f, today = 3))
  }

  /** 固定 9sp 标题最多占三行，内容是否展示由单张卡片的剩余高度决定。 */
  @Test
  fun oversizedDayTextUsesAvailableHeightForContent() {
    val tall = resolveOversizedDayTextLayout(
      title = "大学物理实验课程设计",
      content = "三教 101",
      cardWidthDp = 41f,
      cardHeightDp = 70f,
      contentPaddingHorizontalDp = 1f,
      contentPaddingVerticalDp = 1f,
      fontScale = 1f,
    )
    assertEquals(9, tall.titleSizeSp)
    assertEquals(3, tall.titleLines)
    assertTrue(tall.showContent)
    assertEquals(2, tall.contentLines)

    val short = resolveOversizedDayTextLayout(
      title = "大学物理实验课程设计",
      content = "三教 101",
      cardWidthDp = 41f,
      cardHeightDp = 35f,
      contentPaddingHorizontalDp = 1f,
      contentPaddingVerticalDp = 1f,
      fontScale = 1f,
    )
    assertFalse(short.showContent)
  }

  /** 特别窄的日期列也保持 9sp；截断长标题后仍可用底部空间显示教室。 */
  @Test
  fun oversizedDayTitleKeepsFixedSizeInNarrowColumn() {
    val layout = resolveOversizedDayTextLayout(
      title = "大学物理实验课程设计",
      content = "三教 101",
      cardWidthDp = 41f,
      cardHeightDp = 80f,
      contentPaddingHorizontalDp = 1f,
      contentPaddingVerticalDp = 1f,
      fontScale = 1f,
    )
    assertEquals(9, layout.titleSizeSp)
    assertEquals(3, layout.titleLines)
    assertTrue(layout.contentSizeSp < layout.titleSizeSp)

    // 真机窄列可能连最小字号的三行标题都放不全；底部空间仍应展示教室信息。
    val narrowTall = resolveOversizedDayTextLayout(
      title = "嵌入式与Linux程序设计",
      content = "综合楼 4201",
      cardWidthDp = 27f,
      cardHeightDp = 90f,
      contentPaddingHorizontalDp = 1f,
      contentPaddingVerticalDp = 1f,
      fontScale = 1f,
    )
    assertEquals(9, narrowTall.titleSizeSp)
    assertEquals(3, narrowTall.titleLines)
    assertTrue(narrowTall.showContent)
  }

  /** 1、3、5、7 天的常见列宽和内边距均使用 9sp 标题、8sp 内容。 */
  @Test
  fun oversizedAllDayCountsShareFixedCardFonts() {
    listOf(27f, 41f, 34f, 37f).forEachIndexed { index, columnWidth ->
      val compact = index == 0
      val layout = resolveOversizedDayTextLayout(
        title = "通信原理A",
        content = "3105",
        cardWidthDp = columnWidth,
        cardHeightDp = 70f,
        contentPaddingHorizontalDp = if (compact) 1f else 2f,
        contentPaddingVerticalDp = if (compact) 1f else 3f,
        fontScale = 1f,
      )
      assertEquals(9, layout.titleSizeSp)
      assertEquals(8, layout.contentSizeSp)
      assertTrue(layout.titleLines in 1..3)
    }
  }

  /** 时间轴宽度只由可见天数决定，展开和折叠不能让日期列横向跳动。 */
  @Test
  fun oversizedTimelineWidthIsIndependentFromExpansion() {
    val resolveWidth = { dayCount: Int ->
      com.cyxbs.pages.widget.widget.oversize.resolveOversizedTimelineWidth(
        visibleDayCount = dayCount,
      ).value
    }

    assertEquals(24f, resolveWidth(1))
    assertEquals(28f, resolveWidth(3))
    assertEquals(32f, resolveWidth(5))
    assertEquals(40f, resolveWidth(7))
  }

  /** 最大号表头按目标课表周展示月份和号数，跨月时不能继续使用设备当天月份。 */
  @Test
  fun oversizedHeaderUsesSnapshotWeekDates() {
    val firstWeekMonday = LocalDate.of(2026, 1, 26)
    val snapshot = CourseWidgetSnapshot(
      generatedAtEpochMillis = 0L,
      firstWeekBeginEpochDays = firstWeekMonday.toEpochDay().toInt(),
      maxWeek = 2,
      weeks = emptyList(),
    )

    val header = com.cyxbs.pages.widget.widget.oversize.resolveOversizedWeekHeaderDate(
      snapshot = snapshot,
      week = 2,
      calendar = Calendar.getInstance(),
    )

    assertEquals("2月", header.monthLabel)
    assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), header.dayOfMonths)
  }

  /** 折叠高度填满容器；展开保持单位权重高度不变并增加可滚动内容总高度。 */
  @Test
  fun oversizedTimelineExpansionUsesUpstreamWeights() {
    val sections = listOf(
      CourseWidgetTimelineSection("morning", "···", "早晨", 0, 480, 0.1f, 8f, true),
      CourseWidgetTimelineSection("lesson", "1\n2", "第1-2节", 480, 580, 2f, 2f, false),
    )

    val collapsed = com.cyxbs.pages.widget.widget.oversize.resolveOversizedSectionHeights(
      sections = sections,
      expandedTimelineMask = 0,
      availableHeightDp = 100f,
    )
    val expanded = com.cyxbs.pages.widget.widget.oversize.resolveOversizedSectionHeights(
      sections = sections,
      expandedTimelineMask = 1,
      availableHeightDp = 100f,
    )

    assertTrue(kotlin.math.abs(collapsed.first() / collapsed.last() - 0.05f) < 0.001f)
    assertTrue(kotlin.math.abs(collapsed.sum() - 100f) < 0.001f)
    assertTrue(kotlin.math.abs(expanded.first() / expanded.last() - 4f) < 0.001f)
    assertTrue(expanded.first() > expanded.last())
    assertTrue(kotlin.math.abs(expanded.sum() - 100f * 10f / 2.1f) < 0.001f)
  }

  /** 任意起止时间按分段内部比例定位，不依赖课节格边界。 */
  @Test
  fun oversizedTimelineMapsArbitraryMinutesContinuously() {
    val sections = listOf(
      CourseWidgetTimelineSection("first", "1", "1", 8 * 60, 9 * 60, 1f, 2f, true),
      CourseWidgetTimelineSection("second", "2", "2", 9 * 60, 10 * 60, 1f, 1f, false),
    )
    val heights = com.cyxbs.pages.widget.widget.oversize.resolveOversizedSectionHeights(
      sections = sections,
      expandedTimelineMask = 1,
      availableHeightDp = 100f,
    )

    val begin = com.cyxbs.pages.widget.widget.oversize.resolveOversizedMinuteOffset(
      sections,
      heights,
      8 * 60 + 30,
    )
    val end = com.cyxbs.pages.widget.widget.oversize.resolveOversizedMinuteOffset(
      sections,
      heights,
      9 * 60 + 30,
    )

    assertEquals(50f, begin)
    assertEquals(125f, end)
    assertEquals(75f, end - begin)
  }

  /** 当前时间线复用展开后的分钟坐标，超出时间轴范围时不吸附到首尾。 */
  @Test
  fun oversizedCurrentTimeLineUsesExpandedMinuteOffset() {
    val sections = listOf(
      CourseWidgetTimelineSection("first", "1", "1", 8 * 60, 9 * 60, 1f, 2f, true),
      CourseWidgetTimelineSection("second", "2", "2", 9 * 60, 10 * 60, 1f, 1f, false),
    )
    val heights = listOf(100f, 50f)

    val current = com.cyxbs.pages.widget.widget.oversize.resolveOversizedCurrentTimeOffset(
      sections = sections,
      sectionHeights = heights,
      nowMinute = 8 * 60 + 30,
    )

    assertEquals(50f, current)
    assertNull(
      com.cyxbs.pages.widget.widget.oversize.resolveOversizedCurrentTimeOffset(
        sections = sections,
        sectionHeights = heights,
        nowMinute = 8 * 60 - 1,
      ),
    )
    assertNull(
      com.cyxbs.pages.widget.widget.oversize.resolveOversizedCurrentTimeOffset(
        sections = sections,
        sectionHeights = heights,
        nowMinute = 10 * 60,
      ),
    )
  }

  /** 可折叠分段展开后按真实分钟比例显示整点，而不是继续显示“早晨”等概括文案。 */
  @Test
  fun oversizedExpandedTimelineShowsHourlyTimeLabels() {
    val sections = listOf(
      CourseWidgetTimelineSection("morning", "···", "早晨", 0, 8 * 60, 0.1f, 8f, true),
      CourseWidgetTimelineSection("lesson", "1", "第1节", 8 * 60, 9 * 60, 1f, 1f, false),
      CourseWidgetTimelineSection(
        "night",
        "···",
        "夜晚",
        22 * 60 + 30,
        23 * 60 + 59,
        0.1f,
        1.5f,
        true,
      ),
    )

    val labels = com.cyxbs.pages.widget.widget.oversize.resolveOversizedExpandableTimeLabels(
      sections = sections,
      sectionHeights = listOf(80f, 10f, 15f),
      expandedTimelineMask = (1 shl 0) or (1 shl 2),
      labelHeightDp = 10f,
      timelineHeightDp = 105f,
      bottomLabelInsetDp = 0f,
    )

    assertEquals(
      listOf(
        "00:00", "01:00", "02:00", "03:00", "04:00", "05:00",
        "06:00", "07:00", "08:00", "23:00", "24:00",
      ),
      labels.map { it.text },
    )
    assertFalse(labels.any { it.text == "早晨" || it.text == "夜晚" })
    assertEquals(0f, labels.first().topDp)
    assertEquals(95f, labels.last().topDp)
  }

  /** 正在进行的条目优先于今天稍后的条目。 */
  @Test
  fun compactPrefersOngoingItem() {
    val ongoing = item("ongoing", begin = 8 * 60, end = 9 * 60)
    val next = item("next", begin = 10 * 60, end = 11 * 60)

    val actual = findCompactItem(week(ongoing, next), day = 0, nowMinute = 8 * 60 + 30)

    assertEquals("ongoing", actual?.id)
    assertEquals("结束：9:00", actual?.let { compactTimeLabel(it, 8 * 60 + 30) })
  }

  /** 普通 Widget 使用条目的原始时段绘制完整横条，不再被重叠裁剪区间拆成固定课节槽。 */
  @Test
  fun normalUsesOriginalRangeInsteadOfVisibleFragments() {
    val split = item("split", begin = 8 * 60, end = 22 * 60).copy(
      beginRatio = 0.1f,
      endRatio = 0.9f,
      visibleRanges = listOf(
        CourseWidgetVisibleRange(8 * 60, 9 * 60, 0f, 1f),
        CourseWidgetVisibleRange(19 * 60, 20 * 60, 0f, 1f),
      ),
    )

    val groups = projectNormalTimeline(week(split), day = 0)

    assertEquals(1, groups.size)
    val bar = groups.single().lanes.single().single()
    assertEquals("split", bar.item.id)
    assertEquals(8 * 60, bar.beginMinute)
    assertEquals(22 * 60, bar.endMinute)
    assertEquals(0.1f, bar.beginRatio)
    assertEquals(0.9f, bar.endRatio)
  }

  /** 新快照提供线性范围后，课程位置不再继承课表内部对午间和课间的压缩比例。 */
  @Test
  fun normalTimelineUsesLinearMinuteRatio() {
    val lesson = item("linear", begin = 8 * 60, end = 10 * 60).copy(
      beginRatio = 0.3f,
      endRatio = 0.9f,
    )

    val bar = projectNormalTimeline(
      week = week(lesson),
      day = 0,
      timelineBeginMinute = 8 * 60,
      timelineEndMinute = 22 * 60 + 30,
    ).single().lanes.single().single()

    assertEquals(0f, bar.beginRatio)
    assertEquals(120f / 870f, bar.endRatio)
  }

  /** 固定时间轴裁剪相交条目并记录被裁边，完全位于 8:00–22:30 外的条目仍不绘制。 */
  @Test
  fun normalTimelineClipsItemsAtFixedRangeEdges() {
    val fullyBefore = item("fully-before", begin = 7 * 60, end = 7 * 60 + 30)
    val before = item("before", begin = 7 * 60 + 30, end = 8 * 60 + 30)
    val startsAtBoundary = item("starts-at-boundary", begin = 8 * 60, end = 9 * 60)
    val endsAtBoundary = item("ends-at-boundary", begin = 21 * 60, end = 22 * 60 + 30)
    val after = item("after", begin = 22 * 60, end = 23 * 60)
    val fullyAfter = item("fully-after", begin = 22 * 60 + 30, end = 23 * 60 + 30)

    val bars = projectNormalTimeline(
      week = week(fullyBefore, before, startsAtBoundary, endsAtBoundary, after, fullyAfter),
      day = 0,
      timelineBeginMinute = 8 * 60,
      timelineEndMinute = 22 * 60 + 30,
    ).flatMap { it.lanes }.flatten().associateBy { it.item.id }

    assertEquals(setOf("before", "starts-at-boundary", "ends-at-boundary", "after"), bars.keys)
    assertEquals(8 * 60, bars.getValue("before").beginMinute)
    assertEquals(0f, bars.getValue("before").beginRatio)
    assertTrue(bars.getValue("before").isStartClipped)
    assertFalse(bars.getValue("before").isEndClipped)
    assertFalse(bars.getValue("starts-at-boundary").isStartClipped)
    assertFalse(bars.getValue("ends-at-boundary").isEndClipped)
    assertEquals(22 * 60 + 30, bars.getValue("after").endMinute)
    assertEquals(1f, bars.getValue("after").endRatio)
    assertFalse(bars.getValue("after").isStartClipped)
    assertTrue(bars.getValue("after").isEndClipped)
  }

  /** 当前时间线按固定范围线性定位，范围外与右侧半开边界不绘制。 */
  @Test
  fun normalTimelineCurrentRatioUsesFixedRange() {
    val begin = 8 * 60
    val end = 22 * 60 + 30

    assertEquals(0f, resolveNormalTimelineCurrentRatio(begin, begin, end))
    assertEquals(0.5f, resolveNormalTimelineCurrentRatio(15 * 60 + 15, begin, end))
    assertNull(resolveNormalTimelineCurrentRatio(begin - 1, begin, end))
    assertNull(resolveNormalTimelineCurrentRatio(end, begin, end))
  }

  /** 长区间与左右两个互不相交的短区间只需两条轨道，同层条目沿用快照优先级。 */
  @Test
  fun normalTimelineReusesLaneAroundLongOverlap() {
    val left = item("left", begin = 8 * 60, end = 10 * 60)
    val long = item("long", begin = 9 * 60, end = 13 * 60)
    val right = item("right", begin = 12 * 60, end = 14 * 60)

    val group = projectNormalTimeline(week(left, long, right), day = 0).single()

    assertEquals(2, group.lanes.size)
    assertEquals(listOf("left", "right"), group.lanes.first().map { it.item.id })
    assertEquals(listOf("long"), group.lanes.last().map { it.item.id })
  }

  /** Widget 不推断业务类型，较小的课表绘制层必须始终先进入可见轨道。 */
  @Test
  fun normalTimelineKeepsUpstreamRenderLayerPriority() {
    val affair = item("affair", begin = 8 * 60, end = 12 * 60, renderLayer = 4)
    val lesson = item("lesson", begin = 9 * 60, end = 10 * 60, renderLayer = 2)

    val group = projectNormalTimeline(week(affair, lesson), day = 0).single()

    assertEquals(listOf("lesson"), group.lanes.first().map { it.item.id })
    assertEquals(listOf("affair"), group.lanes.last().map { it.item.id })
  }

  /** 低优先级长条不能凭借宽度遮住课表下发的高优先级短条。 */
  @Test
  fun normalTimelineCapacityKeepsUpstreamPriorityBeforeVisibleWidth() {
    val lowPriorityLong = item("low-long", begin = 8 * 60, end = 18 * 60, renderLayer = 4)
    val highPriorityShort = item("high-short", begin = 9 * 60, end = 10 * 60, renderLayer = 2)

    val layout = projectNormalTimeline(
      week(lowPriorityLong, highPriorityShort),
      day = 0,
    ).single().resolveLaneLayout(maxVisibleLaneCount = 1)

    assertEquals(listOf("high-short"), layout.visibleLanes.flatten().map { it.item.id })
    assertEquals(listOf("low-long"), layout.overflowLanes.flatten().map { it.item.id })
  }

  /** 可见层和折叠层都必须保持课表绘制层从上到下排列。 */
  @Test
  fun normalTimelineCapacityOrdersVisibleAndOverflowLanesByUpstreamPriority() {
    val low = item("low", begin = 8 * 60, end = 12 * 60, renderLayer = 4)
    val middle = item("middle", begin = 8 * 60, end = 12 * 60, renderLayer = 2)
    val high = item("high", begin = 8 * 60, end = 12 * 60, renderLayer = 1)

    val layout = projectNormalTimeline(week(low, middle, high), day = 0)
      .single().resolveLaneLayout(maxVisibleLaneCount = 1)

    assertEquals(listOf("high"), layout.visibleLanes.first().map { it.item.id })
    assertEquals(listOf("middle"), layout.overflowLanes.first().map { it.item.id })
    assertEquals(listOf("low"), layout.overflowLanes.last().map { it.item.id })
  }

  /** 先为重叠条目各保留标题行，再用剩余空间展开没有三重重叠的课程。 */
  @Test
  fun normalTimelineShrinksOnlyCourseOverlappingThirdVisibleItem() {
    val digital = item("digital", begin = 10 * 60 + 15, end = 11 * 60 + 55, renderLayer = 2)
    val english = item("english", begin = 16 * 60 + 15, end = 17 * 60 + 55, renderLayer = 2)
    val embedded = item("embedded", begin = 19 * 60, end = 22 * 60 + 30, renderLayer = 2)
    val longAffair = item("000", begin = 9 * 60, end = 19 * 60 + 38, renderLayer = 4)
      .copy(content = "")
    val coveredAffair = item("456", begin = 15 * 60 + 30, end = 17 * 60 + 33, renderLayer = 4)
      .copy(content = "")

    val layout = projectNormalTimeline(
      week(digital, english, embedded, longAffair, coveredAffair),
      day = 0,
      timelineBeginMinute = 8 * 60,
      timelineEndMinute = 22 * 60 + 30,
    ).single().resolveLaneLayout(
      maxVisibleLaneCount = 3,
      preferredLaneSpan = { bar -> if (bar.item.renderLayer == 2) 2 else 1 },
    )

    assertEquals(
      listOf("digital", "english", "embedded"),
      layout.visibleLanes.first().map { it.item.id },
    )
    assertEquals(2, layout.placedBars.single { it.bar.item.id == "digital" }.laneSpan)
    assertEquals(1, layout.placedBars.single { it.bar.item.id == "english" }.laneSpan)
    assertEquals(2, layout.placedBars.single { it.bar.item.id == "embedded" }.laneSpan)
    assertEquals(2, layout.placedBars.single { it.bar.item.id == "000" }.laneIndex)
    assertEquals(1, layout.placedBars.single { it.bar.item.id == "456" }.laneIndex)
    assertTrue(layout.overflowLanes.isEmpty())
  }

  /** 三个条目同时重叠且恰有三层时，应全部保留标题，而不是展开首项后折叠低优先级项。 */
  @Test
  fun normalTimelineHighPriorityOverlapMayUseTitleOnlyLane() {
    val firstLesson = item("first-lesson", begin = 10 * 60, end = 12 * 60, renderLayer = 2)
    val secondLesson = item("second-lesson", begin = 11 * 60, end = 13 * 60, renderLayer = 2)
    val lowAffair = item("low-affair", begin = 9 * 60, end = 14 * 60, renderLayer = 4)

    val layout = projectNormalTimeline(
      week(firstLesson, secondLesson, lowAffair),
      day = 0,
    ).single().resolveLaneLayout(
      maxVisibleLaneCount = 3,
      preferredLaneSpan = { bar -> if (bar.item.renderLayer == 2) 2 else 1 },
    )

    assertEquals(
      setOf("first-lesson", "second-lesson", "low-affair"),
      layout.visibleLanes.flatten().map { it.item.id }.toSet(),
    )
    assertEquals(1, layout.placedBars.single { it.bar.item.id == "first-lesson" }.laneSpan)
    assertEquals(1, layout.placedBars.single { it.bar.item.id == "second-lesson" }.laneSpan)
    assertEquals(1, layout.placedBars.single { it.bar.item.id == "low-affair" }.laneSpan)
    assertTrue(layout.overflowLanes.isEmpty())
  }

  /**
   * 容量只有两层时按总展示宽度选择，而不是为了条目数量把一个长条换成多个短条。
   * 长条与上午、下午各一个短条组成的可见宽度最大，剩余两个短条进入下方折叠层。
   */
  @Test
  fun normalTimelineCapacityMaximizesVisibleContentWidth() {
    val long = item("long", begin = 8 * 60, end = 18 * 60)
    val morningTop = item("morning-top", begin = 9 * 60, end = 10 * 60)
    val morningBottom = item("morning-bottom", begin = 9 * 60, end = 10 * 60)
    val afternoonTop = item("afternoon-top", begin = 14 * 60, end = 15 * 60)
    val afternoonBottom = item("afternoon-bottom", begin = 14 * 60, end = 15 * 60)

    val layout = projectNormalTimeline(
      week(long, morningTop, morningBottom, afternoonTop, afternoonBottom),
      day = 0,
    ).single().resolveLaneLayout(maxVisibleLaneCount = 2)

    val visibleIds = layout.visibleLanes.flatten().map { it.item.id }.toSet()
    val overflowIds = layout.overflowLanes.flatten().map { it.item.id }.toSet()
    assertEquals(3, visibleIds.size)
    assertEquals(2, overflowIds.size)
    assertTrue("long" in visibleIds)
    assertEquals(1, visibleIds.count { it.startsWith("morning-") })
    assertEquals(1, visibleIds.count { it.startsWith("afternoon-") })
    assertEquals(2, layout.visibleLanes.size)
  }

  /** 完全重叠的条目必须分行，点击身份仍由各自 action 与 overlap ids 保留。 */
  @Test
  fun normalTimelineSeparatesFullyOverlappingItems() {
    val first = item("first", begin = 8 * 60, end = 10 * 60)
    val second = item("second", begin = 8 * 60, end = 10 * 60)

    val group = projectNormalTimeline(week(first, second), day = 0).single()

    assertEquals(2, group.lanes.size)
    assertEquals(setOf("first", "second"), group.lanes.flatten().map { it.item.id }.toSet())
  }

  /** 叠卡提示沿用半开时间段语义：真实交集显示，端点相接不误报。 */
  @Test
  fun normalTimelineOverflowTipIgnoresTouchingEdges() {
    val first = NormalTimelineBar(item("first", 8 * 60, 9 * 60), 8 * 60, 9 * 60, 0f, 0.1f)
    val overlapping = NormalTimelineBar(item("overlap", 8 * 60 + 30, 10 * 60), 8 * 60 + 30, 10 * 60, 0.05f, 0.2f)
    val touching = NormalTimelineBar(item("touching", 9 * 60, 10 * 60), 9 * 60, 10 * 60, 0.1f, 0.2f)

    assertTrue(first.overlaps(overlapping))
    assertFalse(first.overlaps(touching))
  }

  /** 局部事务重叠不得压缩前后互不相交的课程；各连通组必须独立决定轨道数。 */
  @Test
  fun normalTimelineOnlyCompressesOverlappingRegion() {
    val morning = item("morning", begin = 8 * 60, end = 10 * 60)
    val affair = item("affair", begin = 15 * 60 + 45, end = 17 * 60)
    val afternoon = item("afternoon", begin = 16 * 60 + 15, end = 17 * 60 + 55)
    val evening = item("evening", begin = 19 * 60, end = 20 * 60 + 40)

    val groups = projectNormalTimeline(week(morning, affair, afternoon, evening), day = 0)

    assertEquals(3, groups.size)
    assertEquals(listOf(1, 2, 1), groups.map { it.lanes.size })
    assertEquals(listOf("morning"), groups.first().lanes.single().map { it.item.id })
    assertEquals(
      setOf("affair", "afternoon"),
      groups[1].lanes.flatten().map { it.item.id }.toSet(),
    )
    assertEquals(listOf("evening"), groups.last().lanes.single().map { it.item.id })
  }

  /** 扣除顶部刻度后两条轨道各自仍有至少 28dp 时，应保留标题与内容上下两行。 */
  @Test
  fun normalTimelineStacksTextWhenEachLaneIsHighEnough() {
    assertEquals(
      NormalTimelineTextMode.STACKED,
      resolveNormalTimelineTextMode(widgetHeightDp = 91f, laneCount = 2),
    )
  }

  /** 单条轨道高度不足时只保留标题一行，内容不再挤进最小形态。 */
  @Test
  fun normalTimelineUsesTitleOnlyWhenLaneIsTooShort() {
    assertEquals(
      NormalTimelineTextMode.TITLE_ONLY,
      resolveNormalTimelineTextMode(widgetHeightDp = 40f, laneCount = 2),
    )
  }

  /** 升级前快照没有刻度时不应凭空扣除刻度高度。 */
  @Test
  fun normalTimelineKeepsLegacyHeightWithoutScale() {
    assertEquals(
      NormalTimelineTextMode.STACKED,
      resolveNormalTimelineTextMode(
        widgetHeightDp = 76f,
        laneCount = 2,
        hasTimelineScale = false,
      ),
    )
  }

  /** 高度尚未达到下一条标题轨道的阈值时，不应继续压缩已有标题。 */
  @Test
  fun normalTimelineHeightBelowNextTitleLaneKeepsCurrentCapacity() {
    assertEquals(
      2,
      resolveNormalTimelineVisibleLaneCount(widgetHeightDp = 64f, fontScale = 1f),
    )
  }

  /** 高度增加后按单行文字和固定卡片留白逐层扩充容量。 */
  @Test
  fun normalTimelineExpandedHeightFitsMoreLanes() {
    assertEquals(
      3,
      resolveNormalTimelineVisibleLaneCount(widgetHeightDp = 65f, fontScale = 1f),
    )
  }

  /** 局部容量按同时重叠条目的首选高度累加，不能只按条目数量强制等分。 */
  @Test
  fun normalTimelineLocalOverlapUsesPreferredTextHeight() {
    val first = item("first", begin = 8 * 60, end = 10 * 60)
    val second = item("second", begin = 9 * 60, end = 11 * 60)
    val group = projectNormalTimeline(
      week = week(first, second),
      day = 0,
      timelineBeginMinute = 8 * 60,
      timelineEndMinute = 22 * 60 + 30,
    ).single()

    assertEquals(
      3,
      group.resolveNormalTimelineGroupLaneCount(maxVisibleLaneCount = 3) { 2 },
    )
    assertEquals(
      2,
      group.resolveNormalTimelineGroupLaneCount(maxVisibleLaneCount = 3) { 1 },
    )
  }

  /** 同组但时间不重叠的条目复用纵向空间，单个详情条目仍可铺满局部高度。 */
  @Test
  fun normalTimelineLocalNonOverlapReusesPreferredHeight() {
    val group = NormalTimelineGroup(
      beginRatio = 0f,
      endRatio = 1f,
      lanes = listOf(
        listOf(
          NormalTimelineBar(item("first", 8 * 60, 9 * 60), 8 * 60, 9 * 60, 0f, 0.5f),
          NormalTimelineBar(item("second", 9 * 60, 10 * 60), 9 * 60, 10 * 60, 0.5f, 1f),
        ),
      ),
    )

    assertEquals(
      2,
      group.resolveNormalTimelineGroupLaneCount(maxVisibleLaneCount = 3) { 2 },
    )
  }

  /** 系统字体放大会提高每条轨道的最低高度，避免文字被相邻轨道裁切。 */
  @Test
  fun normalTimelineLaneCapacityHonorsFontScale() {
    assertEquals(
      1,
      resolveNormalTimelineVisibleLaneCount(widgetHeightDp = 56f, fontScale = 1.5f),
    )
  }

  /** 即使宿主给出极大高度，也不能超过 Glance 单容器十个直接子节点的转换限制。 */
  @Test
  fun normalTimelineLaneCapacityStopsAtGlanceLimit() {
    assertEquals(
      10,
      resolveNormalTimelineVisibleLaneCount(widgetHeightDp = 1_000f, fontScale = 1f),
    )
  }

  /** 首选占位根据实际文字与字号计算：标题加内容需要两行空间，纯短标题只需一行。 */
  @Test
  fun normalTimelinePreferredLaneSpanUsesActualTextHeight() {
    val detailedBar = projectNormalTimeline(
      week(item("detail", begin = 10 * 60, end = 11 * 60 + 40)),
      day = 0,
      timelineBeginMinute = 8 * 60,
      timelineEndMinute = 22 * 60 + 30,
    ).single().lanes.single().single()
    val titleOnlyBar = detailedBar.copy(item = detailedBar.item.copy(title = "000", content = ""))

    assertEquals(
      2,
      resolveNormalTimelinePreferredLaneSpan(
        bar = detailedBar,
        trackWidthDp = 218f,
        laneHeightDp = 18f,
        fontScale = 1f,
      ),
    )
    assertEquals(
      1,
      resolveNormalTimelinePreferredLaneSpan(
        bar = titleOnlyBar,
        trackWidthDp = 218f,
        laneHeightDp = 18f,
        fontScale = 1f,
      ),
    )
  }

  /** 短标题在宽横条中保留首选字号和单行布局。 */
  @Test
  fun normalTimelineShortTitleKeepsPreferredSingleLineStyle() {
    assertEquals(
      NormalTimelineTitleStyle(fontSizeSp = 11, maxLines = 3),
      resolveNormalTimelineTitleStyle(
        text = "高数",
        availableWidthDp = 80f,
        preferredFontSizeSp = 11,
        maxLines = 3,
      ),
    )
  }

  /** 标题需要占满第三行时降低一档字号，让窄横条尽量展示更多标题字符。 */
  @Test
  fun normalTimelineThreeLineTitleShrinksForReadability() {
    assertEquals(
      NormalTimelineTitleStyle(fontSizeSp = 10, maxLines = 3),
      resolveNormalTimelineTitleStyle(
        text = "FPGA与Verilog硬件描述语言",
        availableWidthDp = 54f,
        preferredFontSizeSp = 11,
        maxLines = 3,
      ),
    )
  }

  /** 三行仍放不下的标题才逐级缩小，且不低于课表 Item 的 9sp 下限。 */
  @Test
  fun normalTimelineExtraLongTitleShrinksWithinThreeLines() {
    assertEquals(
      NormalTimelineTitleStyle(fontSizeSp = 9, maxLines = 3),
      resolveNormalTimelineTitleStyle(
        text = "超长课程标题用于验证三行显示后仍然需要缩小字号",
        availableWidthDp = 60f,
        preferredFontSizeSp = 11,
        maxLines = 3,
      ),
    )
  }

  /** 轨道高度不足时即使标题较长也只能缩小为单行，避免向下挤出横条。 */
  @Test
  fun normalTimelineInlineTitleNeverWraps() {
    assertEquals(
      NormalTimelineTitleStyle(fontSizeSp = 9, maxLines = 1),
      resolveNormalTimelineTitleStyle(
        text = "数据结构·二教203",
        availableWidthDp = 32f,
        preferredFontSizeSp = 10,
        maxLines = 1,
      ),
    )
  }

  /** 两行高度不足以同时展示完整标题和内容时，应把两行都优先分配给标题。 */
  @Test
  fun normalTimelineNarrowCardPrioritizesTitleOverContent() {
    assertEquals(
      NormalTimelineTextAllocation(titleLines = 2, contentLines = 0),
      resolveNormalTimelineTextAllocation(
        title = "数字信号处理",
        content = "3103",
        availableWidthDp = 30f,
        availableHeightDp = 24f,
        titleFontSizeSp = 9,
        contentFontSizeSp = 8,
        maxTitleLines = 3,
        maxContentLines = 2,
        fontScale = 1f,
      ),
    )
  }

  /** 两行宽度只能在 9sp 下容纳完整标题时，应缩小到可读下限而不是输出省略号。 */
  @Test
  fun normalTimelineNarrowTitleShrinksToFitTwoLines() {
    assertEquals(
      NormalTimelineTitleStyle(fontSizeSp = 9, maxLines = 2),
      resolveNormalTimelineTitleStyle(
        text = "数字信号处理",
        availableWidthDp = 27f,
        preferredFontSizeSp = 11,
        minFontSizeSp = 9,
        maxLines = 2,
      ),
    )
  }

  /** 两行窄卡片应降到与相邻重叠卡片一致的 9sp，避免大字号只显示“通信 / 原…”。 */
  @Test
  fun normalTimelineTwoLineTitleUsesReadableSmallSize() {
    assertEquals(
      NormalTimelineTitleStyle(fontSizeSp = 9, maxLines = 2),
      resolveNormalTimelineTitleStyle(
        text = "通信原理A",
        availableWidthDp = 22f,
        preferredFontSizeSp = 11,
        minFontSizeSp = 9,
        maxLines = 2,
      ),
    )
  }

  /** 理论行高刚好卡边时仍需扣除独立内容 TextView 的字体留白，不能泄露一截内容。 */
  @Test
  fun normalTimelineHidesContentAtTextViewPaddingBoundary() {
    assertEquals(
      NormalTimelineTextAllocation(titleLines = 1, contentLines = 0),
      resolveNormalTimelineTextAllocation(
        title = "IT",
        content = "科教楼",
        availableWidthDp = 30f,
        availableHeightDp = 25f,
        titleFontSizeSp = 11,
        contentFontSizeSp = 9,
        maxTitleLines = 3,
        maxContentLines = 2,
        fontScale = 1f,
      ),
    )
  }

  /** 标题、内容行高和独立文字节点留白都能容纳时，才展示完整内容。 */
  @Test
  fun normalTimelineShowsContentAfterTextViewPaddingFits() {
    assertEquals(
      NormalTimelineTextAllocation(titleLines = 1, contentLines = 1),
      resolveNormalTimelineTextAllocation(
        title = "IT",
        content = "科教楼",
        availableWidthDp = 30f,
        availableHeightDp = 29f,
        titleFontSizeSp = 11,
        contentFontSizeSp = 9,
        maxTitleLines = 3,
        maxContentLines = 2,
        fontScale = 1f,
      ),
    )
  }

  /** 全日条目只进入全日提示，不占用时间网格。 */
  @Test
  fun allDayItemDoesNotOccupyTimedSlot() {
    val allDay = item("all-day", begin = null, end = null).copy(isAllDay = true)
    val week = week(allDay)

    assertEquals("all-day", findAllDayItem(week, 0)?.id)
    assertNull(findWeekGridItem(week, 0, WEEK_TIME_SLOTS.first()))
  }

  /** 非法 ISO 星期不应错误落入周一或周日列。 */
  @Test
  fun invalidDayOfWeekIsIgnored() {
    val invalid = item("invalid", begin = 8 * 60, end = 9 * 60).copy(dayOfWeek = 0)

    assertNull(findCompactItem(week(invalid), day = 0, nowMinute = 8 * 60 + 30))
    assertTrue(projectNormalTimeline(week(invalid), day = 0).isEmpty())
  }

  /** 上下切换在当前周循环，周一向上切换应落到周日。 */
  @Test
  fun dayOffsetWrapsInsideCurrentWeek() {
    assertEquals(6, normalizedDayOffset(today = 0, storedOffset = 0, delta = -1))
    assertEquals(0, normalizedDayOffset(today = 0, storedOffset = 6, delta = 1))
    assertEquals(0, normalizedDayOffset(today = 2, storedOffset = 999, delta = 0))
  }

  /** 无真实数据时的高级预览仍覆盖当前课程、全天事项与斜纹事务三种关键视觉语义。 */
  @Test
  fun generatedPreviewFallbackContainsRepresentativeItems() {
    val calendar = Calendar.getInstance().apply {
      set(2026, Calendar.SEPTEMBER, 14, 12, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val snapshot = createCourseWidgetPreviewSnapshot(calendar)
    val week = snapshot.weekOrEmpty(1)
    val compact = findCompactItem(
      week = week,
      day = calendar.mondayBasedDay(),
      nowMinute = currentMinute(calendar),
    )

    assertEquals("数据结构", compact?.title)
    assertEquals(
      listOf("8", "10", "12", "14", "16", "18", "20", "22"),
      snapshot.timelineMarks.map { it.label },
    )
    val markGaps = snapshot.timelineMarks.zipWithNext { left, right -> right.ratio - left.ratio }
    assertTrue(markGaps.all { gap -> kotlin.math.abs(gap - markGaps.first()) < 0.0001f })
    assertTrue(week.items.any { it.isAllDay })
    assertTrue(week.items.any { it.backgroundPattern == CourseWidgetBackgroundPattern.DIAGONAL_STRIPE })
  }

  /** 创建最小通用渲染条目，测试不依赖任何课程/事务业务类型。 */
  private fun item(
    id: String,
    begin: Int?,
    end: Int?,
    renderLayer: Int = 0,
  ) = CourseWidgetRenderItem(
    id = id,
    renderLayer = renderLayer,
    dayOfWeek = 1,
    title = id,
    content = "content",
    beginMinute = begin,
    endMinute = end,
    lightStyle = STYLE,
    darkStyle = STYLE,
    action = CourseWidgetAction(week = 1, itemId = id),
  )

  /** 创建第一周快照以聚焦投影算法。 */
  private fun week(vararg items: CourseWidgetRenderItem) =
    CourseWidgetWeekSnapshot(week = 1, items = items.toList())

  private companion object {
    val STYLE = CourseWidgetItemStyle(
      contentArgb = 0xFF15315B,
      backgroundArgb = 0xFFFFFFFF,
    )
  }
}
