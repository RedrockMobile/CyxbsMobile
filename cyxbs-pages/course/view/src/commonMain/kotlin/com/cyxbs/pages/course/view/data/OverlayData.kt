package com.cyxbs.pages.course.view.data

import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItem

/**
 * 重叠的数据
 *
 * @author 985892345
 * @date 2025/2/15
 */
data class OverlayData(
  val item: CourseItem,
  val showRangeList: List<CoveredRange>, // 未被覆盖的能够显示的区域，是 item 本身的子集
  val coveredRangeList: List<CoveredRange>, // 被覆盖的区域，是 item 本身的子集
)

data class CoveredRange(
  val begin: MinuteTime,
  val final: MinuteTime,
  var bottomCount: Int = 0, // 覆盖的 item 数量
)

object OverlayManager {
  // 计算当天的重叠数据
  fun getSingleDayOverlapData(
    input: List<CourseItem>,
    coveredList: MutableList<CoveredRange>,
  ): List<OverlayData> {
    return input.asReversed() // 这里需要倒序遍历，因为越在后面的 item 越在顶层显示
      .mapNotNull {
        val overlayData = getShowRangeList(it, coveredList)
        if (overlayData.showRangeList.isNotEmpty()) overlayData else null // 过滤掉被完全覆盖的 item
      }.asReversed() // 反转回去
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
        val range = CoveredRange(item.beginTime, item.finalTime)
        showRangeList.add(range)
        coveredList.add(index, range)
      } else if (now.begin > item.beginTime) {
        // 此时 item.begin < now.begin < item.final
        //    ----?????
        // ==========
        val range = CoveredRange(item.beginTime, now.begin)
        showRangeList.add(range)
        coveredList.add(index, range)
        // 剩下的部分为
        // ----??????
        // ========
        opFinal(now.begin, item.finalTime, coveredList, index + 1, showRangeList, coveredRangeList)
      } else {
        // 此时 now.begin ≤ item.begin < now.final
        // -----??????
        //    ======
        opFinal(item.beginTime, item.finalTime, coveredList, index, showRangeList, coveredRangeList)
      }
    }
    return OverlayData(item, showRangeList, coveredRangeList)
  }

  // 第一次进入的条件为： now.begin ≤ begin < now.final
  // 计算 final、now.final、next.begin 三者之间的关系
  private fun opFinal(
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
    now.bottomCount++
    if (final <= now.final) {
      coveredRangeList.add(CoveredRange(begin, final))
      return // 被完全包含
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
        coveredList.add(index + 1, showRange)
        opFinal(next.begin, final, coveredList, index + 2, showRangeList, coveredRangeList)
      } else {
        // 此时 now.final = next.begin
        // -----+++++
        //   ======
        // 这里放入的 begin < next.begin，与 opFinal 调用条件不符
        // 但是对于 begin 这个字段并不会进行判断，所以是允许这样使用的
        opFinal(begin, final, coveredList, index + 1, showRangeList, coveredRangeList)
      }
    }
  }
}


