package com.cyxbs.pages.course.view.data

import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * 重叠的数据
 *
 * @author 985892345
 * @date 2025/2/15
 */
data class OverlayData(
  val item: CourseItem,
  val showRangeList: List<CoveredRange>, // 未被覆盖的能够显示的区域，是 item 本身的子集
)

data class CoveredRange(
  val begin: Duration,
  val final: Duration,
) {
  val beginTime: MinuteTime = (begin.inWholeMinutes % 1.days.inWholeMinutes).let {
    MinuteTime((it / 60).toInt(), (it % 60).toInt())
  }
  val duration: Duration = final - begin
}

object OverlayManager {
  fun getOverlapData(
    input: Collection<CourseItem>,
    comparator: Comparator<CourseItem>,
    coveredList: MutableList<CoveredRange>,
  ): List<OverlayData> {
    return input.sortedWith(comparator)
      .asReversed() // 这里需要倒序遍历，因为越在后面的 item 越在顶层显示
      .mapNotNull {
        val begin = it.page.days * 7 + it.dayOfWeek.ordinal.days + it.beginTime.hour.hours + it.beginTime.minute.minutes
        val final = it.page.days * 7 + it.dayOfWeek.ordinal.days + it.finalTime.hour.hours + it.finalTime.minute.minutes +
            if (it.finalTime < it.beginTime) 1.days else 0.days
        val rangeList = getShowRangeList(CoveredRange(begin, final), coveredList)
        if (rangeList.isNotEmpty()) OverlayData(it, rangeList) else null // 过滤掉被完全覆盖的 item
      }.asReversed() // 反转回去
  }

  /**
   * 计算 item 在 coveredList 覆盖后剩下的未被覆盖的区域
   */
  private fun getShowRangeList(
    item: CoveredRange,
    coveredList: MutableList<CoveredRange>,
  ): List<CoveredRange> {
    val showRangeList = mutableListOf<CoveredRange>()
    var index = 0
    while (index < coveredList.size && coveredList[index].final < item.begin) {
      index++ // 找到第一个 [index].final >= item.begin 的位置
    }
    if (index == coveredList.size) {
      showRangeList.add(item)
      coveredList.add(item)
    } else {
      // item.begin ≤ now.final
      val now = coveredList[index]
      if (now.begin <= item.begin) {
        // now.begin ≤ begin ≤ now.final
        opFinal(item.final, coveredList, index, showRangeList)
      } else if (now.begin > item.final) {
        // 不相交
        showRangeList.add(item)
        coveredList.add(index, item)
      } else {
        // begin < now.begin ≤ final
        // 把 begin 到 now.final 可以组成一组新的覆盖区间 begin -> now.final
        // 则仍然满足了 now.begin ≤ begin ≤ now.final 的条件，所以可以继续递归调用 opFinal
        showRangeList.add(CoveredRange(item.begin, now.begin))
        coveredList[index] = CoveredRange(item.begin, now.final)
        opFinal(item.final, coveredList, index, showRangeList)
      }
    }
    return showRangeList
  }

  // 用于在 now.begin ≤ begin ≤ now.final 时调用
  // 计算 final、now.final、next.begin 三者之间的关系
  private fun opFinal(
    final: Duration,
    coveredList: MutableList<CoveredRange>,
    index: Int,
    showRangeList: MutableList<CoveredRange>
  ) {
    val now = coveredList[index]
    // 与 now 在起始部分肯定存在交集
    if (final <= now.final) return // 被完全包含
    val next = coveredList.getOrNull(index + 1)
    if (next == null || final < next.begin) {
      // now.final < final < next.begin
      showRangeList.add(CoveredRange(now.final, final))
      coveredList[index] = CoveredRange(now.begin, final)
    } else {
      // final ≥ next.begin
      showRangeList.add(CoveredRange(now.final, next.begin))
      coveredList[index] = CoveredRange(now.begin, next.final) // 合并两个区间
      coveredList.removeAt(index + 1)
      opFinal(final, coveredList, index, showRangeList) // 对于 final 与 next.final 又可以递归调用
    }
  }
}


