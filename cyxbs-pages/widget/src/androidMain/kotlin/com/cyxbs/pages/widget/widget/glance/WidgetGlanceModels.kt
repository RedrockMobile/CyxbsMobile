package com.cyxbs.pages.widget.widget.glance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineSection
import com.cyxbs.pages.widget.api.CourseWidgetWeekSnapshot
import com.cyxbs.pages.widget.repo.CourseWidgetSnapshotStore
import java.math.BigInteger
import java.util.Calendar
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Clock

/** 小组件唯一的通用渲染快照入口；UI 不接触课表业务实体或 Room。 */
internal fun readCourseWidgetSnapshot(): CourseWidgetSnapshot = CourseWidgetSnapshotStore.read()

/**
 * 观察进程内快照版本并读取最新持久化内容。
 *
 * Glance 会短暂复用活跃组合会话；版本流负责直接触发重组，系统更新广播则负责启动已结束的会话。
 */
@Composable
internal fun observeCourseWidgetSnapshot(): CourseWidgetSnapshot {
  val revision = CourseWidgetSnapshotStore.snapshotRevision.collectAsState().value
  return remember(revision) { readCourseWidgetSnapshot() }
}

/** 周网格的时间行，包含左侧标题与分钟范围。 */
internal data class WidgetTimeSlot(
  val label: String,
  val beginMinute: Int,
  val endMinute: Int,
)

/** 半开分钟区间 [beginMinute, endMinute)，避免相邻事件在边界重复命中。 */
internal data class WidgetMinuteRange(
  val beginMinute: Int,
  val endMinute: Int,
)

/** 普通横向 Widget 中的一段甘特条目；分钟用于分轨，比例用于最终绘制。 */
internal data class NormalTimelineBar(
  val item: CourseWidgetRenderItem,
  val beginMinute: Int,
  val endMinute: Int,
  val beginRatio: Float,
  val endRatio: Float,
  /** 同层条目沿用快照顺序，避免 Widget 用标题或 id 猜测业务优先级。 */
  val snapshotOrder: Int = 0,
)

/** 半开时间段真正相交时才视为重叠，端点相接不会产生叠卡边框或提示。 */
internal fun NormalTimelineBar.overlaps(other: NormalTimelineBar): Boolean =
  beginMinute < other.endMinute && other.beginMinute < endMinute

/** 一段连续发生重叠的时间区间；各组独立分轨，避免局部重叠压缩整天条目的高度。 */
internal data class NormalTimelineGroup(
  val beginRatio: Float,
  val endRatio: Float,
  val lanes: List<List<NormalTimelineBar>>,
)

/**
 * 相邻时间端点切出的原子区间；区间内同时存在的条目集合保持不变。
 *
 * 课表侧已经用 [CourseWidgetRenderItem.visibleRanges] 给出最终遮挡结果，这里只把原始起止时间
 * 转换为容量约束，避免 Widget 再实现一套课程业务重叠规则。
 */
private data class NormalTimelineOverlapRegion(
  val beginMinute: Int,
  val endMinute: Int,
  val activeBarIndexes: Set<Int>,
)

/** 按当前 Widget 容量重新投影后的可见轨道与下方折叠轨道。 */
internal data class NormalTimelineLaneLayout(
  val visibleLanes: List<List<NormalTimelineBar>>,
  val overflowLanes: List<List<NormalTimelineBar>>,
  /** 可见条目的真实纵向位置；[laneSpan] 大于 1 时可展示标题与内容。 */
  val placedBars: List<NormalTimelinePlacedBar>,
)

/** 普通横条在离散纵向行中的位置与占用行数。 */
internal data class NormalTimelinePlacedBar(
  val bar: NormalTimelineBar,
  val laneIndex: Int,
  val laneSpan: Int,
)

/**
 * 在不区分业务类型的前提下，把条目放入当前高度能够提供的离散纵向行。
 *
 * [preferredLaneSpan] 根据条目宽度、字号和实际文字内容计算首选行数。布局分为三步：先按
 * 所有起止端点切出重叠区间，再按课表优先级为可见条目各保留一行、用剩余容量补足首选高度，
 * 最后才为已分配条目寻找最靠上的连续轨道。没有一行空间的条目进入下沿折叠层。
 *
 * 所有条目的首选高度均为 1 时沿用最大收益流选择，保留旧的等高轨道行为与同层最大展示
 * 宽度；存在多行请求时启用带 rowSpan 的优先级矩形排布。
 */
internal fun NormalTimelineGroup.resolveLaneLayout(
  maxVisibleLaneCount: Int,
  preferredLaneSpan: (NormalTimelineBar) -> Int = { 1 },
): NormalTimelineLaneLayout {
  val capacity = maxVisibleLaneCount.coerceAtLeast(1)
  val indexedBars = lanes.flatten().mapIndexed { index, bar -> IndexedTimelineBar(index, bar) }
  if (indexedBars.isEmpty()) {
    return NormalTimelineLaneLayout(
      visibleLanes = emptyList(),
      overflowLanes = emptyList(),
      placedBars = emptyList(),
    )
  }
  val requestedLaneSpans = indexedBars.associate { indexedBar ->
    indexedBar.index to preferredLaneSpan(indexedBar.bar).coerceIn(1, capacity)
  }
  if (requestedLaneSpans.values.any { it > 1 }) {
    return indexedBars.allocateAndPlaceByOverlapRegions(
      capacity = capacity,
      requestedLaneSpans = requestedLaneSpans,
    )
  }
  if (lanes.size <= capacity) {
    return NormalTimelineLaneLayout(
      visibleLanes = lanes,
      overflowLanes = emptyList(),
      placedBars = lanes.flatMapIndexed { laneIndex, lane ->
        lane.map { bar -> NormalTimelinePlacedBar(bar, laneIndex, laneSpan = 1) }
      },
    )
  }
  val selectedIndexes = indexedBars.selectMaximumVisibleContentIndexes(capacity)
  val visibleBars = indexedBars.filter { it.index in selectedIndexes }.map(IndexedTimelineBar::bar)
  val overflowBars = indexedBars.filter { it.index !in selectedIndexes }.map(IndexedTimelineBar::bar)
  val packedVisibleLanes = visibleBars.packNormalTimelineLanes()
  return NormalTimelineLaneLayout(
    visibleLanes = packedVisibleLanes,
    overflowLanes = overflowBars.packNormalTimelineLanes(),
    placedBars = packedVisibleLanes.flatMapIndexed { laneIndex, lane ->
      lane.map { bar -> NormalTimelinePlacedBar(bar, laneIndex, laneSpan = 1) }
    },
  )
}

/**
 * 先按原子重叠区间分配行数，再把横条放入最靠上的连续空行。
 *
 * 同一条目跨越的所有区间共用一个高度，取各区间剩余容量的最小值。第一轮按优先级尽量
 * 为每个条目保留一行标题，第二轮才把剩余容量补给需要标题和内容的条目；因此新增条目可通过
 * 把已有两行卡片降为一行进入可见层。纵向落位只消费已确定行数，不再决定谁应该显示。
 */
private fun List<IndexedTimelineBar>.allocateAndPlaceByOverlapRegions(
  capacity: Int,
  requestedLaneSpans: Map<Int, Int>,
): NormalTimelineLaneLayout {
  val priorityOrder = sortedWith(normalTimelinePriorityComparator())
  val overlapRegions = buildNormalTimelineOverlapRegions()
  val allocatedSpans = mutableMapOf<Int, Int>()
  val regionUsedCapacity = IntArray(overlapRegions.size)
  val overflow = mutableListOf<NormalTimelineBar>()
  val relatedRegionIndexes = associate { indexedBar ->
    indexedBar.index to overlapRegions.indices.filter { regionIndex ->
      indexedBar.index in overlapRegions[regionIndex].activeBarIndexes
    }
  }

  // 第一轮先保证更多条目至少拥有一行标题，不能让前面的两行详情提前吃掉全部容量。
  priorityOrder.forEach { indexedBar ->
    val regions = relatedRegionIndexes.getValue(indexedBar.index)
    val hasTitleLane = regions.all { regionIndex ->
      regionUsedCapacity[regionIndex] < capacity
    }
    if (!hasTitleLane) {
      overflow += indexedBar.bar
    } else {
      allocatedSpans[indexedBar.index] = 1
      regions.forEach { regionIndex ->
        regionUsedCapacity[regionIndex] += 1
      }
    }
  }

  // 第二轮仍按课表优先级补足首选高度；只消费所有相关重叠区间共同剩余的容量。
  priorityOrder.forEach { indexedBar ->
    val allocatedSpan = allocatedSpans[indexedBar.index] ?: return@forEach
    val preferredSpan = requestedLaneSpans.getValue(indexedBar.index)
    val missingSpan = (preferredSpan - allocatedSpan).coerceAtLeast(0)
    if (missingSpan == 0) return@forEach
    val regions = relatedRegionIndexes.getValue(indexedBar.index)
    val availableSpan = regions.minOfOrNull { regionIndex ->
      capacity - regionUsedCapacity[regionIndex]
    } ?: capacity
    val additionalSpan = minOf(missingSpan, availableSpan).coerceAtLeast(0)
    if (additionalSpan > 0) {
      allocatedSpans[indexedBar.index] = allocatedSpan + additionalSpan
      regions.forEach { regionIndex ->
        regionUsedCapacity[regionIndex] += additionalSpan
      }
    }
  }

  // 第二阶段只为已经通过容量计算的 item 分配稳定纵向位置。
  val placed = mutableListOf<NormalTimelinePlacedBar>()
  priorityOrder.forEach { indexedBar ->
    val allocatedSpan = allocatedSpans[indexedBar.index] ?: return@forEach
    val bar = indexedBar.bar
    val laneIndex = (0..capacity - allocatedSpan).firstOrNull { candidateLaneIndex ->
      placed.none { existing ->
        bar.overlaps(existing.bar) &&
          candidateLaneIndex < existing.laneIndex + existing.laneSpan &&
          existing.laneIndex < candidateLaneIndex + allocatedSpan
      }
    }
    // 区间容量计算对区间图应当总能落位；异常碎片化时安全降级到折叠层，不能遮住其他条目。
    if (laneIndex == null) {
      overflow += bar
    } else {
      placed += NormalTimelinePlacedBar(bar, laneIndex, allocatedSpan)
    }
  }
  val usedLaneCount = placed.maxOfOrNull { it.laneIndex + it.laneSpan } ?: 0
  val visibleLanes = List(usedLaneCount) { laneIndex ->
    placed.asSequence()
      .filter { it.laneIndex == laneIndex }
      .map(NormalTimelinePlacedBar::bar)
      .sortedWith(compareBy<NormalTimelineBar> { it.beginMinute }.thenBy { it.snapshotOrder })
      .toList()
  }
  return NormalTimelineLaneLayout(
    visibleLanes = visibleLanes,
    overflowLanes = overflow.packNormalTimelineLanes(),
    placedBars = placed,
  )
}

/** 按课表绘制层、课表可见比例和稳定快照顺序形成唯一通用优先级。 */
private fun normalTimelinePriorityComparator(): Comparator<IndexedTimelineBar> =
  compareBy<IndexedTimelineBar> { it.bar.item.renderLayer }
    // showRange 是课表重叠算法的最终结果；同层先放仍可见比例更高的条目。
    .thenByDescending { it.bar.visibleCoverageRatio() }
    .thenBy { it.bar.snapshotOrder }
    .thenBy { it.index }

/**
 * 使用所有原始起止端点切分原子区间，使每个区间内的同时活跃集合保持不变。
 *
 * 端点采用半开语义，相邻 item 可以复用容量；没有任何条目的空区间不会进入结果。
 */
private fun List<IndexedTimelineBar>.buildNormalTimelineOverlapRegions(): List<NormalTimelineOverlapRegion> {
  val timePoints = flatMap { indexedBar ->
    listOf(indexedBar.bar.beginMinute, indexedBar.bar.endMinute)
  }.distinct().sorted()
  return timePoints.zipWithNext().mapNotNull { (beginMinute, endMinute) ->
    val activeIndexes = asSequence()
      .filter { indexedBar ->
        indexedBar.bar.beginMinute < endMinute && indexedBar.bar.endMinute > beginMinute
      }
      .map(IndexedTimelineBar::index)
      .toSet()
    activeIndexes.takeIf(Set<Int>::isNotEmpty)?.let {
      NormalTimelineOverlapRegion(beginMinute, endMinute, it)
    }
  }
}

/** 带稳定身份的内部区间，避免内容完全相同的条目在集合淘汰时互相覆盖。 */
private data class IndexedTimelineBar(
  val index: Int,
  val bar: NormalTimelineBar,
)

/** 残量网络中的一条有向边，记录反向边位置、剩余容量和展示收益。 */
private class TimelineFlowEdge(
  val to: Int,
  val reverseIndex: Int,
  var capacity: Int,
  val value: BigInteger,
)

/**
 * 用逐次最长增广路求容量受限、优先级严格有序的最大可见内容集合。
 *
 * 连续时间边允许每条流向后移动；选择横条边会获得“课表优先级 + 渲染宽度”的字典序收益。
 * 反向边允许后续增广重新安排之前的选择，因此长条、交错短条和桥接区间都能得到全局最优结果。
 */
private fun List<IndexedTimelineBar>.selectMaximumVisibleContentIndexes(
  capacity: Int,
): Set<Int> {
  if (isEmpty()) return emptySet()
  val timePoints = flatMap { listOf(it.bar.beginMinute, it.bar.endMinute) }.distinct().sorted()
  val timeIndex = timePoints.withIndex().associate { it.value to it.index }
  val graph = List(timePoints.size) { mutableListOf<TimelineFlowEdge>() }
  val visibleWidthValues = associate { indexedBar ->
    indexedBar.index to (
      (indexedBar.bar.endRatio - indexedBar.bar.beginRatio).coerceAtLeast(0f) *
        NORMAL_TIMELINE_WIDTH_VALUE_SCALE
      ).roundToLong().coerceAtLeast(1L)
  }
  val priorityMultipliers = mutableMapOf<Int, BigInteger>()
  var lowerPriorityMaximum = BigInteger.ZERO
  groupBy { it.bar.item.renderLayer }.entries.sortedByDescending { it.key }.forEach { entry ->
    // 当前层一个最小宽度单位也必须大于所有低优先级条目的理论收益总和。
    val multiplier = lowerPriorityMaximum + BigInteger.ONE
    priorityMultipliers[entry.key] = multiplier
    val layerMaximumWidth = entry.value.sumOf { visibleWidthValues.getValue(it.index) }
    lowerPriorityMaximum += BigInteger.valueOf(layerMaximumWidth) * multiplier
  }

  fun addEdge(from: Int, to: Int, edgeCapacity: Int, value: BigInteger): TimelineFlowEdge {
    val forward = TimelineFlowEdge(to, graph[to].size, edgeCapacity, value)
    val reverse = TimelineFlowEdge(from, graph[from].size, 0, value.negate())
    graph[from] += forward
    graph[to] += reverse
    return forward
  }

  for (index in 0 until timePoints.lastIndex) {
    addEdge(index, index + 1, capacity, value = BigInteger.ZERO)
  }
  val intervalEdges = associate { indexedBar ->
    val beginIndex = timeIndex.getValue(indexedBar.bar.beginMinute)
    val endIndex = timeIndex.getValue(indexedBar.bar.endMinute)
    val visibleWidthValue = BigInteger.valueOf(visibleWidthValues.getValue(indexedBar.index)) *
      priorityMultipliers.getValue(indexedBar.bar.item.renderLayer)
    indexedBar.index to addEdge(
      from = beginIndex,
      to = endIndex,
      edgeCapacity = 1,
      value = visibleWidthValue,
    )
  }

  repeat(capacity) {
    val values = arrayOfNulls<BigInteger>(graph.size)
    val previousNode = IntArray(graph.size) { -1 }
    val previousEdge = IntArray(graph.size) { -1 }
    values[0] = BigInteger.ZERO
    // 残量图包含反向边，使用 Bellman-Ford 形式的松弛以支持重新安排早先流量。
    var remainingRelaxations = graph.size - 1
    while (remainingRelaxations-- > 0) {
      var changed = false
      graph.indices.forEach { from ->
        val fromValue = values[from] ?: return@forEach
        graph[from].forEachIndexed { edgeIndex, edge ->
          if (edge.capacity <= 0) return@forEachIndexed
          val candidateValue = fromValue + edge.value
          val currentValue = values[edge.to]
          if (currentValue == null || candidateValue > currentValue) {
            values[edge.to] = candidateValue
            previousNode[edge.to] = from
            previousEdge[edge.to] = edgeIndex
            changed = true
          }
        }
      }
      if (!changed) break
    }
    var node = graph.lastIndex
    while (node != 0) {
      val from = previousNode[node]
      val edgeIndex = previousEdge[node]
      check(from >= 0 && edgeIndex >= 0) { "时间轴残量网络缺少完整增广路" }
      val edge = graph[from][edgeIndex]
      edge.capacity -= 1
      graph[node][edge.reverseIndex].capacity += 1
      node = from
    }
  }
  return intervalEdges.filterValues { edge -> edge.capacity == 0 }.keys
}

/**
 * 按课表绘制层优先占用靠上轨道，再复用不相交的已有轨道。
 *
 * 同一绘制层沿用快照顺序；分配结束后才按时间排序，保证布局从左到右稳定，同时不会让
 * 后开始的低优先级长条抢占高优先级条目的上方位置。
 */
private fun List<NormalTimelineBar>.packNormalTimelineLanes(): List<List<NormalTimelineBar>> {
  val packed = mutableListOf<MutableList<NormalTimelineBar>>()
  sortedWith(
    compareBy<NormalTimelineBar> { it.item.renderLayer }
      .thenByDescending(NormalTimelineBar::visibleCoverageRatio)
      .thenBy { it.snapshotOrder },
  ).forEach { bar ->
    val reusableLane = packed.indexOfFirst { lane -> lane.none(bar::overlaps) }
    if (reusableLane >= 0) {
      packed[reusableLane] += bar
    } else {
      packed += mutableListOf(bar)
    }
  }
  packed.forEach { lane ->
    lane.sortWith(compareBy<NormalTimelineBar> { it.beginMinute }.thenBy { it.snapshotOrder })
  }
  return packed
}

/** 普通横条内文字的排布方式：高度充足时上下分行，否则只保留一行标题。 */
internal enum class NormalTimelineTextMode {
  STACKED,
  TITLE_ONLY,
}

/** 普通横条标题最终使用的字号与最大行数。 */
internal data class NormalTimelineTitleStyle(
  val fontSizeSp: Int,
  val maxLines: Int,
)

/** 普通横条在当前高度内实际分配给标题和内容的行数。 */
internal data class NormalTimelineTextAllocation(
  val titleLines: Int,
  val contentLines: Int,
)

/**
 * 根据横条的实际可用宽度为标题选择字号和行数。
 *
 * 优先使用课表 Item 的 11sp 首选字号和指定最大行数；只有预计超过最大行数时才逐级缩小，
 * 默认最小保持 9sp，极窄横条可由调用方传入更低下限。标题预计会占满第三行时额外降低
 * 1sp，让窄横条尽量展示更多标题字符。
 * 该估算只依赖字符宽度，可在 Glance 无法取得 Text 测量结果的 RemoteViews 环境中稳定运行。
 */
internal fun resolveNormalTimelineTitleStyle(
  text: String,
  availableWidthDp: Float,
  preferredFontSizeSp: Int,
  minFontSizeSp: Int = 9,
  maxLines: Int,
): NormalTimelineTitleStyle {
  val preferredSize = preferredFontSizeSp.coerceAtLeast(1)
  val minimumSize = minFontSizeSp.coerceIn(1, preferredSize)
  val lineLimit = maxLines.coerceAtLeast(1)
  if (text.isBlank() || !availableWidthDp.isFinite() || availableWidthDp <= 0f) {
    return NormalTimelineTitleStyle(preferredSize, lineLimit)
  }
  val widthUnits = text.sumOf { it.normalTimelineWidthUnit().toDouble() }.toFloat()
    .coerceAtLeast(1f)
  val fittingSize = (preferredSize downTo minimumSize).firstOrNull { fontSize ->
    widthUnits * fontSize <= availableWidthDp * lineLimit
  } ?: minimumSize
  val balancedSize = if (
    lineLimit >= 3 &&
    widthUnits * fittingSize > availableWidthDp * (lineLimit - 1)
  ) {
    (fittingSize - 1).coerceAtLeast(minimumSize)
  } else {
    fittingSize
  }
  return NormalTimelineTitleStyle(balancedSize, lineLimit)
}

/**
 * 根据横条的真实宽高为标题和内容分配行数。
 *
 * 标题具有绝对优先级：先按实际宽度计算完整标题需要的行数，并尽量把可用高度交给标题；
 * 只有标题所需行数已经全部容纳且仍有剩余高度时，才允许内容占用最多 [maxContentLines] 行。
 * 由于 Glance 的 RemoteViews 无法返回 Text 实测尺寸，这里沿用标题字号选择时的字符宽度估算。
 */
internal fun resolveNormalTimelineTextAllocation(
  title: String,
  content: String,
  availableWidthDp: Float,
  availableHeightDp: Float,
  titleFontSizeSp: Int,
  contentFontSizeSp: Int,
  maxTitleLines: Int,
  maxContentLines: Int,
  fontScale: Float,
): NormalTimelineTextAllocation {
  val titleLineLimit = maxTitleLines.coerceAtLeast(1)
  val contentLineLimit = maxContentLines.coerceAtLeast(0)
  val safeTitleSize = titleFontSizeSp.coerceAtLeast(1)
  val safeContentSize = contentFontSizeSp.coerceAtLeast(1)
  val safeFontScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
  if (!availableWidthDp.isFinite() || availableWidthDp <= 0f ||
    !availableHeightDp.isFinite() || availableHeightDp <= 0f
  ) {
    return NormalTimelineTextAllocation(titleLines = 1, contentLines = 0)
  }

  val widthUnits = title.sumOf { it.normalTimelineWidthUnit().toDouble() }.toFloat()
    .coerceAtLeast(1f)
  val requiredTitleLines = ceil(widthUnits * safeTitleSize / availableWidthDp).toInt()
    .coerceAtLeast(1)
  val titleLineHeightDp = safeTitleSize * safeFontScale * NORMAL_TIMELINE_TEXT_LINE_HEIGHT_FACTOR
  val titleHeightCapacity = floor(availableHeightDp / titleLineHeightDp).toInt().coerceAtLeast(1)
  val allocatedTitleLines = minOf(requiredTitleLines, titleHeightCapacity, titleLineLimit)

  // 标题尚未完整容纳时不能再用内容挤占空间，否则窄课程只会留下“数...”一类无效信息。
  if (allocatedTitleLines < requiredTitleLines || content.isBlank() || contentLineLimit == 0) {
    return NormalTimelineTextAllocation(allocatedTitleLines, contentLines = 0)
  }
  val remainingHeightDp = availableHeightDp - allocatedTitleLines * titleLineHeightDp
  val contentLineHeightDp = safeContentSize * safeFontScale * NORMAL_TIMELINE_TEXT_LINE_HEIGHT_FACTOR
  // Glance 最终把标题和内容翻译成两个独立 TextView；内容节点自身还带字体上下留白。
  // 如果只比较理论行高，临界高度会创建内容 TextView，但宿主只能裁出底部的一小截。
  val allocatedContentLines = floor(
    (remainingHeightDp - NORMAL_TIMELINE_CONTENT_TEXT_BLOCK_PADDING_DP) / contentLineHeightDp,
  ).toInt()
    .coerceIn(0, contentLineLimit)
  return NormalTimelineTextAllocation(allocatedTitleLines, allocatedContentLines)
}

/**
 * 根据宿主给出的实际高度和轨道数选择文字排布。
 *
 * 计算会扣除 Widget 上下各 4dp 的外边距和统一时间刻度行；每条轨道至少 28dp
 * 才放置标题和内容两行文字。该高度对应 11sp 标题、9sp 内容及卡片内外留白。
 */
internal fun resolveNormalTimelineTextMode(
  widgetHeightDp: Float,
  laneCount: Int,
  hasTimelineScale: Boolean = true,
): NormalTimelineTextMode {
  if (!widgetHeightDp.isFinite() || widgetHeightDp <= 0f || laneCount <= 0) {
    return NormalTimelineTextMode.TITLE_ONLY
  }
  val scaleHeightDp = if (hasTimelineScale) NORMAL_TIMELINE_SCALE_HEIGHT_DP else 0f
  val laneHeightDp = (widgetHeightDp - NORMAL_WIDGET_VERTICAL_PADDING_DP - scaleHeightDp)
    .coerceAtLeast(0f) / laneCount
  return if (laneHeightDp >= NORMAL_TIMELINE_STACKED_TEXT_MIN_HEIGHT_DP) {
    NormalTimelineTextMode.STACKED
  } else {
    NormalTimelineTextMode.TITLE_ONLY
  }
}

/**
 * 根据普通小组件的实际高度计算单个重叠组最多可显示的轨道数。
 *
 * 每条轨道至少容纳一行 9sp 文本和固定卡片上下留白；字体缩放会同步增加
 * 单行高度。返回值至少为 1，并限制在 Glance 1.2.0 单容器可转换的 10 个直接子节点内。
 */
internal fun resolveNormalTimelineVisibleLaneCount(
  widgetHeightDp: Float,
  fontScale: Float,
  hasTimelineScale: Boolean = true,
): Int {
  if (!widgetHeightDp.isFinite() || widgetHeightDp <= 0f) return 1
  val safeFontScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
  val scaleHeightDp = if (hasTimelineScale) NORMAL_TIMELINE_SCALE_HEIGHT_DP else 0f
  val availableHeightDp = (widgetHeightDp - NORMAL_WIDGET_VERTICAL_PADDING_DP - scaleHeightDp)
    .coerceAtLeast(0f)
  // RemoteViews 无法回传 Text 测量值，按 Android 默认字体行高的保守比例换算 sp 到 dp。
  val oneLineTextHeightDp = ceil(
    NORMAL_TIMELINE_TITLE_ONLY_TEXT_SIZE_SP * safeFontScale * NORMAL_TIMELINE_TEXT_LINE_HEIGHT_FACTOR,
  )
  // 卡片固定上下留白本身已经形成轨道间隔，不能再次叠加 spacing，否则 1 格高度会被误判为仅 2 层。
  val minimumLaneHeightDp = oneLineTextHeightDp + NORMAL_TIMELINE_ITEM_VERTICAL_PADDING_DP
  return floor(availableHeightDp / minimumLaneHeightDp).toInt()
    .coerceIn(1, NORMAL_TIMELINE_MAX_VISIBLE_LANE_COUNT)
}

/**
 * 根据局部重叠区间内所有条目的首选文字高度，计算该组实际需要的纵向行数。
 *
 * [maxVisibleLaneCount] 是宿主高度允许的上限，[preferredLaneSpan] 是每个条目为了展示有效文字
 * 希望占用的行数。互不重叠的条目可以复用高度；真正重叠的条目才累加首选行数。这样单个条目
 * 仍能铺满，而两个需要详情的条目在三行容量中会按 `2 + 1` 分配，不会被强制等分成两个单行标题。
 */
internal fun NormalTimelineGroup.resolveNormalTimelineGroupLaneCount(
  maxVisibleLaneCount: Int,
  preferredLaneSpan: (NormalTimelineBar) -> Int,
): Int {
  val capacity = maxVisibleLaneCount.coerceAtLeast(1)
  val bars = lanes.flatten()
  if (bars.isEmpty()) return 1
  val timePoints = bars.flatMap { bar -> listOf(bar.beginMinute, bar.endMinute) }
    .distinct()
    .sorted()
  val requiredLaneCount = timePoints.zipWithNext().maxOfOrNull { (beginMinute, endMinute) ->
    bars.asSequence()
      .filter { bar -> bar.beginMinute < endMinute && bar.endMinute > beginMinute }
      .sumOf { bar -> preferredLaneSpan(bar).coerceIn(1, capacity) }
  } ?: 1
  return requiredLaneCount.coerceIn(1, capacity)
}

/**
 * 根据横条真实宽度、字号和内容计算它的首选占位行数。
 *
 * 这里只申请至少容纳一行标题和一行内容所需的高度，不会因为优先级较高就占满全部可用行；
 * 最终可取得的行数由所属重叠区间的剩余容量统一裁剪，最低仍保留一行标题。
 */
internal fun resolveNormalTimelinePreferredLaneSpan(
  bar: NormalTimelineBar,
  trackWidthDp: Float,
  laneHeightDp: Float,
  fontScale: Float,
): Int {
  if (!trackWidthDp.isFinite() || trackWidthDp <= 0f ||
    !laneHeightDp.isFinite() || laneHeightDp <= 0f
  ) {
    return 1
  }
  val safeFontScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
  val barWidthDp = (bar.endRatio - bar.beginRatio).coerceAtLeast(0f) * trackWidthDp
  val availableTextWidthDp = (barWidthDp - NORMAL_TIMELINE_ITEM_HORIZONTAL_PADDING_DP)
    .coerceAtLeast(1f)
  val hasContent = bar.item.content.isNotBlank()
  val titleStyle = resolveNormalTimelineTitleStyle(
    text = bar.item.title,
    availableWidthDp = availableTextWidthDp,
    preferredFontSizeSp = if (hasContent) 11 else 9,
    maxLines = if (hasContent) 3 else 1,
  )
  val titleHeightDp = titleStyle.fontSizeSp * safeFontScale * NORMAL_TIMELINE_TEXT_LINE_HEIGHT_FACTOR
  val contentHeightDp = if (hasContent) {
    minOf(9, titleStyle.fontSizeSp - 1).coerceAtLeast(8) * safeFontScale *
      NORMAL_TIMELINE_TEXT_LINE_HEIGHT_FACTOR
  } else {
    0f
  }
  // 首选高度只保证标题和内容各至少一行；更长标题由实际剩余高度决定可展示行数。
  val minimumUsefulHeightDp = titleHeightDp + contentHeightDp +
    NORMAL_TIMELINE_ITEM_VERTICAL_PADDING_DP
  return ceil(minimumUsefulHeightDp / laneHeightDp).toInt().coerceAtLeast(1)
}

/** 周网格按旧视觉划分的 8 行：6 个双节块加中午、傍晚两个过渡段。 */
internal val WEEK_TIME_SLOTS = listOf(
  WidgetTimeSlot("1\n2", 8 * 60, 9 * 60 + 40),
  WidgetTimeSlot("3\n4", 10 * 60 + 15, 11 * 60 + 55),
  WidgetTimeSlot("中午", 11 * 60 + 55, 14 * 60),
  WidgetTimeSlot("5\n6", 14 * 60, 15 * 60 + 40),
  WidgetTimeSlot("7\n8", 16 * 60 + 15, 17 * 60 + 55),
  WidgetTimeSlot("傍晚", 17 * 60 + 55, 19 * 60),
  WidgetTimeSlot("9\n10", 19 * 60, 20 * 60 + 40),
  WidgetTimeSlot("11\n12", 20 * 60 + 50, 22 * 60 + 30),
)

/** 旧快照尚未携带分段时使用的兼容布局；保持课表原始逐段权重以支持连续时间坐标。 */
internal val DEFAULT_OVERSIZED_TIMELINE_SECTIONS = listOf(
  CourseWidgetTimelineSection("before-8", "···", "早晨", 0, 8 * 60, 0.1f, 8f, true),
  CourseWidgetTimelineSection("lesson-1", "1", "第1节", 8 * 60, 8 * 60 + 45, 1f, 1f, false),
  CourseWidgetTimelineSection("break-1", "", "课间", 8 * 60 + 45, 8 * 60 + 55, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-2", "2", "第2节", 8 * 60 + 55, 9 * 60 + 40, 1f, 1f, false),
  CourseWidgetTimelineSection("break-2", "大课间", "大课间", 9 * 60 + 40, 10 * 60 + 15, 0.05f, 0.05f, false),
  CourseWidgetTimelineSection("lesson-3", "3", "第3节", 10 * 60 + 15, 11 * 60, 1f, 1f, false),
  CourseWidgetTimelineSection("break-3", "", "课间", 11 * 60, 11 * 60 + 10, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-4", "4", "第4节", 11 * 60 + 10, 11 * 60 + 55, 1f, 1f, false),
  CourseWidgetTimelineSection("noon", "中午", "中午", 11 * 60 + 55, 14 * 60, 0.1f, 2f, true),
  CourseWidgetTimelineSection("lesson-5", "5", "第5节", 14 * 60, 14 * 60 + 45, 1f, 1f, false),
  CourseWidgetTimelineSection("break-5", "", "课间", 14 * 60 + 45, 14 * 60 + 55, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-6", "6", "第6节", 14 * 60 + 55, 15 * 60 + 40, 1f, 1f, false),
  CourseWidgetTimelineSection("break-6", "大课间", "大课间", 15 * 60 + 40, 16 * 60 + 15, 0.05f, 0.05f, false),
  CourseWidgetTimelineSection("lesson-7", "7", "第7节", 16 * 60 + 15, 17 * 60, 1f, 1f, false),
  CourseWidgetTimelineSection("break-7", "", "课间", 17 * 60, 17 * 60 + 10, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-8", "8", "第8节", 17 * 60 + 10, 17 * 60 + 55, 1f, 1f, false),
  CourseWidgetTimelineSection("evening", "傍晚", "傍晚", 17 * 60 + 55, 19 * 60, 0.1f, 1f, true),
  CourseWidgetTimelineSection("lesson-9", "9", "第9节", 19 * 60, 19 * 60 + 45, 1f, 1f, false),
  CourseWidgetTimelineSection("break-9", "", "课间", 19 * 60 + 45, 19 * 60 + 55, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-10", "10", "第10节", 19 * 60 + 55, 20 * 60 + 40, 1f, 1f, false),
  CourseWidgetTimelineSection("break-10", "", "课间", 20 * 60 + 40, 20 * 60 + 50, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-11", "11", "第11节", 20 * 60 + 50, 21 * 60 + 35, 1f, 1f, false),
  CourseWidgetTimelineSection("break-11", "", "课间", 21 * 60 + 35, 21 * 60 + 45, 0.01f, 0.01f, false),
  CourseWidgetTimelineSection("lesson-12", "12", "第12节", 21 * 60 + 45, 22 * 60 + 30, 1f, 1f, false),
  CourseWidgetTimelineSection("after-2230", "···", "夜晚", 22 * 60 + 30, 23 * 60 + 59, 0.1f, 1.5f, true),
)

internal const val OVERSIZED_TWO_COLUMN_MIN_WIDTH_DP = 100f
private const val OVERSIZED_THREE_COLUMN_MIN_WIDTH_DP = 200f
private const val OVERSIZED_FOUR_COLUMN_MIN_WIDTH_DP = 300f

/**
 * 按宿主实际宽度选择 1、3、5、7 日视图。
 *
 * 宽度档位对应桌面的一至四列；不足整周时尽量让今天居中，并在周一、周日边缘收口。
 */
internal fun resolveOversizedVisibleDays(widgetWidthDp: Float, today: Int): List<Int> {
  val safeToday = today.coerceIn(0, 6)
  val count = when {
    widgetWidthDp >= OVERSIZED_FOUR_COLUMN_MIN_WIDTH_DP -> 7
    widgetWidthDp >= OVERSIZED_THREE_COLUMN_MIN_WIDTH_DP -> 5
    widgetWidthDp >= OVERSIZED_TWO_COLUMN_MIN_WIDTH_DP -> 3
    else -> 1
  }
  val start = when (count) {
    1 -> safeToday
    3 -> (safeToday - 1).coerceIn(0, 4)
    5 -> (safeToday - 2).coerceIn(0, 2)
    else -> 0
  }
  return (start until start + count).toList()
}

/** 过滤损坏分段；空结果回退到升级前的默认课表，避免旧持久化快照显示空白。 */
internal fun CourseWidgetSnapshot.resolveOversizedTimelineSections(): List<CourseWidgetTimelineSection> =
  oversizedTimelineSections.filter { section ->
    section.id.isNotBlank() &&
      section.beginMinute in 0 until MINUTES_PER_DAY &&
      section.endMinute in 1..MINUTES_PER_DAY &&
      section.endMinute > section.beginMinute &&
      section.collapsedWeight.isFinite() && section.collapsedWeight > 0f &&
      section.expandedWeight.isFinite() && section.expandedWeight > 0f
  }.take(Int.SIZE_BITS - 1).takeIf { it.isNotEmpty() } ?: DEFAULT_OVERSIZED_TIMELINE_SECTIONS

/** 当前校历周；快照缺少起始日期时回退到最小可用周。 */
internal fun CourseWidgetSnapshot.resolveCurrentWeek(): Int {
  val firstDay = firstWeekBeginEpochDays
  if (firstDay != null) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()
    val candidate = floor((today - firstDay) / 7.0).toInt() + 1
    if (candidate in 1..maxWeek) return candidate
  }
  return weeks.asSequence().map { it.week }.filter { it in 1..maxWeek }.minOrNull() ?: 1
}

/** 将星期偏移规范到当前周的周一至周日；delta 为 0 时回到今天。 */
internal fun normalizedDayOffset(
  today: Int,
  storedOffset: Int,
  delta: Int,
): Int {
  if (today !in 0..6 || delta == 0) return 0
  val selectedDay = normalDayForOffset(today, storedOffset)
  val nextDay = floorModSeven(selectedDay + delta)
  return nextDay - today
}

/** 根据今天和持久化偏移取得周一为 0 的目标星期，异常大偏移也会安全循环。 */
internal fun normalDayForOffset(today: Int, offset: Int): Int =
  floorModSeven(today.coerceIn(0, 6) + offset)

/** 获取指定周的快照；不存在时返回空周，避免坏数据使 widget 崩溃。 */
internal fun CourseWidgetSnapshot.weekOrEmpty(week: Int): CourseWidgetWeekSnapshot =
  weeks.firstOrNull { it.week == week } ?: CourseWidgetWeekSnapshot(week = week, items = emptyList())

/** 返回当前分钟，显式使用 24 小时制。 */
internal fun currentMinute(calendar: Calendar = Calendar.getInstance()): Int =
  calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

/**
 * 计算当前分钟在普通 Widget 固定时间轴中的线性比例。
 *
 * 返回 null 表示快照范围无效或当前时间不在半开区间内，调用方此时不绘制当前时间线。
 */
internal fun resolveNormalTimelineCurrentRatio(
  nowMinute: Int,
  timelineBeginMinute: Int?,
  timelineEndMinute: Int?,
): Float? {
  val beginMinute = timelineBeginMinute ?: return null
  val endMinute = timelineEndMinute ?: return null
  if (beginMinute !in 0 until MINUTES_PER_DAY ||
    endMinute !in 1..MINUTES_PER_DAY ||
    endMinute <= beginMinute ||
    nowMinute !in beginMinute until endMinute
  ) {
    return null
  }
  return (nowMinute - beginMinute).toFloat() / (endMinute - beginMinute)
}

/** 把 Calendar 的周日开头序号转换为 ISO 周一为 0。 */
internal fun Calendar.mondayBasedDay(): Int = (get(Calendar.DAY_OF_WEEK) + 5) % 7

/**
 * compact 优先展示正在进行的 item，再展示今天下一项；只消费通用时间字段。
 * 全日项没有明确时间，不参与“下一节”排序。
 */
internal fun findCompactItem(
  week: CourseWidgetWeekSnapshot,
  day: Int,
  nowMinute: Int,
): CourseWidgetRenderItem? {
  val candidates = week.items.filter { !it.isAllDay && it.isOnDay(day) }
  return candidates
    .filter { item -> item.timeRanges().any { nowMinute in it.beginMinute until it.endMinute } }
    .minByOrNull { it.timeRanges().minOf { range -> range.beginMinute } }
    ?: candidates
      .filter { it.timeRanges().any { range -> range.beginMinute > nowMinute } }
      .minByOrNull { it.timeRanges().minOf { range -> range.beginMinute } }
}

/**
 * 将一天的任意时间段先拆成重叠连通组，再在各组内分配最少轨道。
 *
 * 相邻或互不重叠的组各自恢复为完整高度，只有当前组实际发生重叠时才缩短条目高度。
 * 组内严格沿用课表下发的绘制层，避免低优先级长条仅凭持续时间占据上方轨道。
 */
internal fun projectNormalTimeline(
  week: CourseWidgetWeekSnapshot,
  day: Int,
  timelineBeginMinute: Int? = null,
  timelineEndMinute: Int? = null,
): List<NormalTimelineGroup> {
  val linearRange = if (
    timelineBeginMinute != null && timelineEndMinute != null &&
    timelineBeginMinute in 0 until MINUTES_PER_DAY &&
    timelineEndMinute in 1..MINUTES_PER_DAY && timelineEndMinute > timelineBeginMinute
  ) {
    WidgetMinuteRange(timelineBeginMinute, timelineEndMinute)
  } else {
    null
  }
  val bars = week.items.mapIndexedNotNull { snapshotOrder, item ->
    if (item.isAllDay || !item.isOnDay(day)) return@mapIndexedNotNull null
    val beginMinute = item.beginMinute ?: return@mapIndexedNotNull null
    val endMinute = item.endMinute ?: return@mapIndexedNotNull null
    if (beginMinute !in 0 until MINUTES_PER_DAY || endMinute !in 1..MINUTES_PER_DAY || endMinute <= beginMinute) {
      return@mapIndexedNotNull null
    }
    // 固定时间轴不绘制越界事项，避免边缘出现经过比例裁剪但语义不完整的半条内容。
    if (linearRange != null &&
      (beginMinute < linearRange.beginMinute || endMinute > linearRange.endMinute)
    ) {
      return@mapIndexedNotNull null
    }
    val beginRatio = linearRange?.ratioOf(beginMinute)
      ?: item.beginRatio.validTimelineRatioOr(beginMinute)
    val endRatio = linearRange?.ratioOf(endMinute)
      ?: item.endRatio.validTimelineRatioOr(endMinute)
    if (endRatio <= beginRatio) return@mapIndexedNotNull null
    NormalTimelineBar(
      item = item,
      beginMinute = beginMinute,
      endMinute = endMinute,
      beginRatio = beginRatio,
      endRatio = endRatio,
      snapshotOrder = snapshotOrder,
    )
  }.sortedWith(
    compareBy<NormalTimelineBar> { it.beginMinute }
      .thenByDescending { it.endMinute - it.beginMinute }
      .thenBy { it.item.id },
  )
  if (bars.isEmpty()) return emptyList()
  val connectedGroups = mutableListOf<MutableList<NormalTimelineBar>>()
  var connectedEndMinute = Int.MIN_VALUE
  bars.forEach { bar ->
    // 半开区间在端点相等时不算重叠，因此可从这里开始新的完整高度区域。
    if (connectedGroups.isEmpty() || bar.beginMinute >= connectedEndMinute) {
      connectedGroups += mutableListOf(bar)
      connectedEndMinute = bar.endMinute
    } else {
      connectedGroups.last() += bar
      connectedEndMinute = maxOf(connectedEndMinute, bar.endMinute)
    }
  }
  return connectedGroups.map(List<NormalTimelineBar>::toNormalTimelineGroup)
}

/**
 * 对单个重叠连通组按课表传来的绘制层执行稳定分轨。
 *
 * 高优先级层先占用靠上的轨道；同层沿用快照顺序。每条轨道最终再按时间排序，以便横向布局。
 */
private fun List<NormalTimelineBar>.toNormalTimelineGroup(): NormalTimelineGroup {
  val lanes = mutableListOf<MutableList<NormalTimelineBar>>()
  sortedWith(
    compareBy<NormalTimelineBar> { it.item.renderLayer }
      .thenBy { it.snapshotOrder },
  ).forEach { bar ->
    val reusableLane = lanes.indexOfFirst { lane -> lane.none(bar::overlaps) }
    val laneIndex = if (reusableLane >= 0) reusableLane else lanes.size.also {
      lanes += mutableListOf<NormalTimelineBar>()
    }
    lanes[laneIndex] += bar
  }
  lanes.forEach { lane ->
    lane.sortWith(compareBy<NormalTimelineBar> { it.beginMinute }.thenBy { it.snapshotOrder })
  }
  return NormalTimelineGroup(
    beginRatio = minOf { it.beginRatio },
    endRatio = maxOf { it.endRatio },
    lanes = lanes,
  )
}

/**
 * 课表侧 showRangeList 对应的可见时间比例；比例越低，说明越多区间已被同层或上层条目覆盖。
 *
 * 旧快照缺少 visibleRanges 时返回 0，并由稳定快照顺序兜底，不在 Widget 内猜测业务类型。
 */
private fun NormalTimelineBar.visibleCoverageRatio(): Float {
  val duration = (endMinute - beginMinute).coerceAtLeast(1)
  val visibleDuration = item.visibleRanges.sumOf { range ->
    (minOf(range.endMinute, endMinute) - maxOf(range.beginMinute, beginMinute)).coerceAtLeast(0)
  }
  return (visibleDuration.toFloat() / duration).coerceIn(0f, 1f)
}

/** 为周视图投影某天某时间行；视觉类型由快照 style/pattern 完整决定。 */
internal fun findWeekGridItem(
  week: CourseWidgetWeekSnapshot,
  day: Int,
  slot: WidgetTimeSlot,
): CourseWidgetRenderItem? = week.items
  .filter { !it.isAllDay && it.isOnDay(day) && it.intersects(slot) }
  .maxWithOrNull(compareBy<CourseWidgetRenderItem> { it.visibleScore(slot) }
    .thenBy { -it.timeRanges().minOf { range -> range.beginMinute } })

/** 返回某天的第一个全日项，作为周列表头下方提示。 */
internal fun findAllDayItem(week: CourseWidgetWeekSnapshot, day: Int): CourseWidgetRenderItem? =
  week.items.firstOrNull { it.isAllDay && it.isOnDay(day) }

/** 生成 compact 的“下课/今日”时间提示。 */
internal fun compactTimeLabel(item: CourseWidgetRenderItem, nowMinute: Int): String {
  val ranges = item.timeRanges()
  val active = ranges.firstOrNull { nowMinute in it.beginMinute until it.endMinute }
  return if (active != null) "结束：${formatMinute(active.endMinute)}"
  else "今日：${formatMinute(ranges.minOf { it.beginMinute })}"
}

/** visibleRanges 是复杂事件的权威可见区间，普通事件回退到 begin/end。 */
internal fun CourseWidgetRenderItem.timeRanges(): List<WidgetMinuteRange> {
  val visible = visibleRanges.map { WidgetMinuteRange(it.beginMinute, it.endMinute) }
  if (visible.isNotEmpty()) return visible
  val begin = beginMinute
  val end = endMinute
  return if (begin != null && end != null && end > begin) listOf(WidgetMinuteRange(begin, end)) else emptyList()
}

/** 时间区间采用半开区间相交，避免相邻课程在边界被重复绘制。 */
private fun CourseWidgetRenderItem.intersects(slot: WidgetTimeSlot): Boolean =
  timeRanges().any { range ->
    range.beginMinute < slot.endMinute && range.endMinute > slot.beginMinute
  }

/** DTO 使用 ISO 1..7，非法星期值不进入任何 UI 列。 */
private fun CourseWidgetRenderItem.isOnDay(day: Int): Boolean =
  day in 0..6 && dayOfWeek in 1..7 && dayOfWeek - 1 == day

/**
 * 发生同槽重叠时优先选择发布者标记的最大可见比例 root；其余 id 由 action.overlapItemIds 保留。
 */
private fun CourseWidgetRenderItem.visibleScore(slot: WidgetTimeSlot): Float {
  if (visibleRanges.isNotEmpty()) {
    return visibleRanges
      .filter { it.beginMinute < slot.endMinute && it.endMinute > slot.beginMinute }
      .sumOf { (it.endRatio - it.beginRatio).coerceAtLeast(0f).toDouble() }
      .toFloat()
  }
  val duration = (slot.endMinute - slot.beginMinute).coerceAtLeast(1)
  return timeRanges().sumOf { range ->
    (minOf(range.endMinute, slot.endMinute) - maxOf(range.beginMinute, slot.beginMinute))
      .coerceAtLeast(0)
      .toDouble()
  }.toFloat() / duration
}

private fun formatMinute(value: Int): String = "%d:%02d".format(value / 60, value % 60)

/** 损坏或旧快照没有课表比例时，退化为全天自然分钟比例。 */
private fun Float?.validTimelineRatioOr(minute: Int): Float =
  this?.takeIf { it.isFinite() && it in 0f..1f } ?: minute.toFloat() / MINUTES_PER_DAY

/** 把分钟映射到统一线性时间轴并限制在可见范围内。 */
private fun WidgetMinuteRange.ratioOf(minute: Int): Float =
  ((minute - beginMinute).toFloat() / (endMinute - beginMinute)).coerceIn(0f, 1f)

/** Kotlin 公共代码中显式处理负余数，确保向上切到周日时仍得到 0..6。 */
private fun floorModSeven(value: Int): Int = ((value % 7) + 7) % 7

/** 中文按全角估算，拉丁字符与标点按常见比例估算，避免仅按字符数误判混合标题。 */
private fun Char.normalTimelineWidthUnit(): Float = when {
  isWhitespace() -> 0.35f
  code <= 0x7F && isLetterOrDigit() -> 0.58f
  code <= 0x7F -> 0.5f
  else -> 1f
}

private const val NORMAL_WIDGET_VERTICAL_PADDING_DP = 8f
private const val NORMAL_TIMELINE_SCALE_HEIGHT_DP = 12f
private const val NORMAL_TIMELINE_STACKED_TEXT_MIN_HEIGHT_DP = 28f
private const val NORMAL_TIMELINE_TITLE_ONLY_TEXT_SIZE_SP = 9f
private const val NORMAL_TIMELINE_TEXT_LINE_HEIGHT_FACTOR = 1.2f
private const val NORMAL_TIMELINE_CONTENT_TEXT_BLOCK_PADDING_DP = 2f
private const val NORMAL_TIMELINE_ITEM_HORIZONTAL_PADDING_DP = 4f
private const val NORMAL_TIMELINE_ITEM_VERTICAL_PADDING_DP = 4f
private const val NORMAL_TIMELINE_MAX_VISIBLE_LANE_COUNT = 10
private const val NORMAL_TIMELINE_WIDTH_VALUE_SCALE = 1_000_000L
private const val MINUTES_PER_DAY = 24 * 60
