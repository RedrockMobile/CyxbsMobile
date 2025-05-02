package com.cyxbs.pages.course.view.overlay

import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItem

/**
 * 重叠的数据
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Stable
data class OverlayData(
  val item: CourseItem,
  val showRangeList: List<CoveredRange>, // 未被覆盖的能够显示的区域，是 item 本身的子集
  val coveredRangeList: List<CoveredRange>, // 被覆盖的区域，是 item 本身的子集
)

@Stable
data class CoveredRange(
  val begin: MinuteTime,
  val final: MinuteTime,
  val coveredItems: MutableList<CourseItem> = mutableListOf(), // 被覆盖的 item，只有 showRangeList 才存在值
)

object OverlayManager {
  // 计算当天的重叠数据
  fun getSingleDayOverlapData(
    input: List<CourseItem>,
    coveredList: MutableList<CoveredRange>,
    ignoreCoverBottom: Set<CourseItem>, // 不会覆盖下层 item 的 item
    allowNoShowRange: Set<CourseItem>, // 允许被完全覆盖时仍然展示
  ): List<OverlayData> {
    val ignoreShowRangeList = mutableListOf<CoveredRange>()
    return input.asReversed() // 这里需要倒序遍历，因为越在后面的 item 越在顶层显示
      .mapNotNull { item ->
        val enableIgnoreCoverBottom = ignoreCoverBottom.contains(item)
        // 如果 item 在 ignore 中，则复制出一份全新的 coveredList，使 opFinal 中的 coveredList.add 失效
        val fakeCoveredList = if (enableIgnoreCoverBottom) coveredList.toMutableList() else coveredList
        val overlayData = getShowRangeList(item, fakeCoveredList)
        updateIgnoreShowRangeList(item, ignoreShowRangeList) // 因为被忽略的 range 不会参与计算，所以要单独计算其 coveredItems
        if (enableIgnoreCoverBottom) {
          ignoreShowRangeList.addAll(overlayData.showRangeList) // 保存起来用于单独计算其 coveredItems
        }
        // 过滤掉被完全覆盖的 item
        if (overlayData.showRangeList.isNotEmpty() || allowNoShowRange.contains(item)) overlayData else null
      }.asReversed() // 反转回去
  }

  // 不覆盖下层 item 的 item 需要单独计算其 coveredItems
  private fun updateIgnoreShowRangeList(item: CourseItem, rangeList: List<CoveredRange>) {
    rangeList.fastForEach {
      if (!(item.finalTime <= it.begin || item.beginTime >= it.final)) {
        it.coveredItems.add(item)
      }
    }
  }

  /**
   * 计算 item 在 coveredList 覆盖后剩下的未被覆盖的区域
   */
  private fun getShowRangeList(
    item: CourseItem,
    coveredList: MutableList<CoveredRange>,
  ): OverlayData {
    val showRangeList = mutableListOf<CoveredRange>()
    val coveredRangeList = mutableListOf<CoveredRange>()
    var index = 0
    while (index < coveredList.size && coveredList[index].final <= item.beginTime) {
      index++ // 找到第一个 [index].final > item.begin 的位置
    }
    if (index == coveredList.size) {
      val range = CoveredRange(item.beginTime, item.finalTime)
      showRangeList.add(range)
      coveredList.add(range)
    } else {
      // 此时 item.begin < now.final
      //        ----- ｜     ----?????  ｜   -----??????  ｜
      // ======       ｜  ==========    ｜      ======    ｜
      val now = coveredList[index]
      if (now.begin >= item.finalTime) {
        // 完全不相交
        //        -----
        // ======
        val showRange = CoveredRange(item.beginTime, item.finalTime)
        showRangeList.add(showRange)
        coveredList.add(index, showRange)
      } else if (now.begin > item.beginTime) {
        // 此时 item.begin < now.begin < item.final
        //    ----?????
        // ==========
        val showRange = CoveredRange(item.beginTime, now.begin)
        showRangeList.add(showRange)
        coveredList.add(index, showRange) // 注意这里在 index 位置进行了插入，opFinal 中要传递 index + 1
        // 剩下的部分为
        // ----??????
        // ========
        opFinal(item, now.begin, item.finalTime, coveredList, index + 1, showRangeList, coveredRangeList)
      } else {
        // 此时 now.begin ≤ item.begin < now.final
        // -----??????
        //    ======
        opFinal(item, item.beginTime, item.finalTime, coveredList, index, showRangeList, coveredRangeList)
      }
    }
    return OverlayData(item, showRangeList, coveredRangeList)
  }

  // 第一次进入的条件为： now.begin ≤ begin < now.final
  // 计算 final、now.final、next.begin 三者之间的关系
  private fun opFinal(
    item: CourseItem,
    begin: MinuteTime,
    final: MinuteTime,
    coveredList: MutableList<CoveredRange>,
    index: Int,
    showRangeList: MutableList<CoveredRange>,
    coveredRangeList: MutableList<CoveredRange>,
  ) {
    // 此时与 now 在起始部分肯定存在交集
    // -----??????
    //    ======
    val now = coveredList[index]
    now.coveredItems.add(item)
    if (final <= now.final) {
      // 被完全包含
      coveredRangeList.add(CoveredRange(begin, final))
      return
    }
    // 接下来是 now.begin ≤ begin < now.final < final
    // -----
    //    ======
    val next = coveredList.getOrNull(index + 1)
    if (next == null || final <= next.begin) {
      // 此时 now.final < final ≤ next.begin
      // -----      +++++
      //    ======
      val coveredRange = CoveredRange(begin, now.final)
      coveredRangeList.add(coveredRange)
      val showRange = CoveredRange(now.final, final)
      showRangeList.add(showRange)
      coveredList.add(index + 1, showRange)
    } else {
      // 此时 final > next.begin
      // -----???+++++
      //    =======
      if (now.final < next.begin) {
        // 此时如下，now.final 到 next.begin 构成一个单独的 range
        // -----   +++++
        //    =======
        val coveredRange = CoveredRange(begin, now.final)
        coveredRangeList.add(coveredRange)
        val showRange = CoveredRange(now.final, next.begin)
        showRangeList.add(showRange)
        coveredList.add(index + 1, showRange) // 对 index + 1 进行了插入，opFinal 中要传递 index + 2
        opFinal(item, next.begin, final, coveredList, index + 2, showRangeList, coveredRangeList)
      } else {
        // 此时 now.final = next.begin
        // -----+++++
        //   ======
        val coveredRange = CoveredRange(begin, now.final)
        coveredRangeList.add(coveredRange)
        opFinal(item, next.begin, final, coveredList, index + 1, showRangeList, coveredRangeList)
      }
    }
  }
}


