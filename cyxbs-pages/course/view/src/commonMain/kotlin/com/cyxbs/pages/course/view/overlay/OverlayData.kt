package com.cyxbs.pages.course.view.overlay

import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.CourseItemState

/**
 * 重叠的数据
 *
 * @author 985892345
 * @date 2025/2/15
 */

class OverlapCover(
  val range: MinuteTimePair,
  val result: OverlapResult,
)

class OverlapResult(
  val itemState: CourseItemState,
) {

  // 能够展示的区域
  val showRangeList: MutableList<MinuteTimePair> = mutableListOf()

  // 被覆盖的区域
  // 其 CourseItemCover#result 为上层 item
  val coveredRangeList: MutableList<OverlapCover> = mutableListOf()

  // 覆盖的 item 集合，只保存了直接覆盖的子 item，如果需要查找所有覆盖的 item，需要进行递归收集
  // 其 CourseItemCover#result 为下层 item
  val coveredItemList: MutableList<OverlapCover> = mutableListOf()
}


fun CourseItemState.createOverlapResult(
  coveredList: MutableList<OverlapCover>,
): OverlapResult {
  val itemState = this
  val itemBeginTime = itemState.item.whatTime.now.value.beginTime
  val itemFinalTime = itemState.item.whatTime.now.value.finalTime
  val itemOverlap = OverlapResult(itemState)
  val showRangeList = itemOverlap.showRangeList
  if (itemBeginTime == itemFinalTime) {
    // 时间点保持零分钟业务语义，但作为有序切割锚点进入 coveredList。后续时间段遇到该锚点时，
    // 会自然拆成锚点前后两个 showRange，让两段内容分别在各自真实高度内完成自适应布局。
    val pointRange = MinuteTimePair(itemBeginTime, itemFinalTime)
    showRangeList.add(pointRange)
    // coveredList 已按时间有序，只定位插入点；相同区间放在已有项之后，保持原有顺序稳定。
    val insertIndex = coveredList.indexOfFirst { cover ->
      cover.range.first > pointRange.first ||
          cover.range.first == pointRange.first && cover.range.second > pointRange.second
    }.takeIf { it >= 0 } ?: coveredList.size
    coveredList.add(insertIndex, OverlapCover(pointRange, itemOverlap))
    return itemOverlap
  }
  var index = 0
  while (
    index < coveredList.size &&
    coveredList[index].range.second <= itemBeginTime &&
    // 普通半开区间在 item.begin 结束时不相交；零长度锚点恰好位于 begin 时仍要留下顶部避让。
    !(coveredList[index].range.isPoint() && coveredList[index].range.first == itemBeginTime)
  ) {
    index++ // 找到第一个 [index].final > item.begin 的位置
  }
  if (index == coveredList.size) {
    // 当前 item 比所有的都大
    val range = MinuteTimePair(itemBeginTime, itemFinalTime)
    showRangeList.add(range)
    coveredList.add(OverlapCover(range, itemOverlap))
  } else {
    // 此时 item.begin < now.range.second
    //       ------ ｜     ----?????  ｜   ???---?????  ｜
    // ======       ｜  ==========    ｜      ======    ｜
    var prevBegin = itemBeginTime
    while (true) {
      val now = coveredList[index]
      if (
        now.range.first > itemFinalTime ||
        now.range.first == itemFinalTime && !now.range.isPoint()
      ) {
        // 完全不相交
        //       ------
        // ======
        showRangeList.add(MinuteTimePair(prevBegin, itemFinalTime))
        coveredList.add(
          index,
          OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
        )
        break
      } else if (now.range.first > prevBegin) {
        // 此时 prevBegin < now.range.first < item.final
        //    ----?????
        // ==========
        showRangeList.add(MinuteTimePair(prevBegin, now.range.first))
        if (now.range.second == itemFinalTime) {
          //    -------
          // ==========
          val coveredRange = MinuteTimePair(now.range.first, now.range.second)
          itemOverlap.coveredRangeList.add(OverlapCover(coveredRange, now.result))
          now.result.coveredItemList.add(OverlapCover(coveredRange, itemOverlap))
          coveredList[index] =
            OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
          break
        } else if (now.range.second > itemFinalTime) {
          //    ----------
          // ==========
          val coveredRange = MinuteTimePair(now.range.first, itemFinalTime)
          itemOverlap.coveredRangeList.add(OverlapCover(coveredRange, now.result))
          now.result.coveredItemList.add(OverlapCover(coveredRange, itemOverlap))
          coveredList[index] =
            OverlapCover(MinuteTimePair(itemFinalTime, now.range.second), now.result)
          coveredList.add(
            index,
            OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
          )
          break
        } else {
          //    ----
          // ==========
          val coveredRange = MinuteTimePair(now.range.first, now.range.second)
          itemOverlap.coveredRangeList.add(OverlapCover(coveredRange, now.result))
          now.result.coveredItemList.add(OverlapCover(coveredRange, itemOverlap))
          coveredList.removeAt(index)
          if (index == coveredList.size) {
            // 没有下一个 item，则直接添加到末尾
            coveredList.add(
              OverlapCover(
                MinuteTimePair(itemBeginTime, itemFinalTime),
                itemOverlap
              )
            )
            showRangeList.add(MinuteTimePair(now.range.second, itemFinalTime))
            break
          } else {
            // 因为前面调用了 coveredList.removeAt(index)
            // 后续 coveredList 中添加时使用 itemBeginTime
            // 进入下一次 while 循环
            prevBegin = now.range.second
            continue
          }
        }
      } else {
        // 此时 now.range.first ≤ itemBegin < now.final
        // ???---?????
        //    ======
        if (now.range.second == itemFinalTime) {
          // ???------
          //    ======
          val coveredRange = MinuteTimePair(prevBegin, itemFinalTime)
          itemOverlap.coveredRangeList.add(OverlapCover(coveredRange, now.result))
          now.result.coveredItemList.add(OverlapCover(coveredRange, itemOverlap))
          if (now.range.first == prevBegin) {
            // ------
            // ======
            coveredList.removeAt(index)
            coveredList.add(
              index,
              OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
            )
            break
          } else {
            // ---------
            //    ======
            coveredList[index] =
              OverlapCover(MinuteTimePair(now.range.first, prevBegin), now.result)
            coveredList.add(index + 1, OverlapCover(coveredRange, itemOverlap))
            break
          }
        } else if (now.range.second > itemFinalTime) {
          // ???---------
          //    ======
          val coveredRange = MinuteTimePair(prevBegin, itemFinalTime)
          itemOverlap.coveredRangeList.add(OverlapCover(coveredRange, now.result))
          now.result.coveredItemList.add(OverlapCover(coveredRange, itemOverlap))
          if (now.range.first == prevBegin) {
            // ---------
            // ======
            coveredList[index] =
              OverlapCover(MinuteTimePair(itemFinalTime, now.range.second), now.result)
            coveredList.add(
              index,
              OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
            )
            break
          } else {
            // ------------
            //    ======
            coveredList[index] =
              OverlapCover(MinuteTimePair(now.range.first, prevBegin), now.result)
            coveredList.add(index + 1, OverlapCover(coveredRange, itemOverlap))
            coveredList.add(
              index + 2,
              OverlapCover(MinuteTimePair(itemFinalTime, now.range.second), now.result)
            )
            break
          }
        } else {
          // now.range.second < item.final
          // ???----
          //    ======
          val coveredRange = MinuteTimePair(prevBegin, now.range.second)
          itemOverlap.coveredRangeList.add(OverlapCover(coveredRange, now.result))
          now.result.coveredItemList.add(OverlapCover(coveredRange, itemOverlap))
          if (now.range.first == prevBegin) {
            // ----
            // ======
            coveredList.removeAt(index)
            if (index == coveredList.size) {
              // 没有下一个 item，则直接添加到末尾
              coveredList.add(
                OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
              )
              showRangeList.add(MinuteTimePair(now.range.second, itemFinalTime))
              break
            } else {
              // 进入下一次 while 循环
              prevBegin = now.range.second
              continue
            }
          } else {
            // -------
            //    ======
            coveredList[index] =
              OverlapCover(MinuteTimePair(now.range.first, prevBegin), now.result)
            index++
            if (index == coveredList.size) {
              // 没有下一个 item，则直接添加到末尾
              coveredList.add(
                OverlapCover(MinuteTimePair(itemBeginTime, itemFinalTime), itemOverlap)
              )
              showRangeList.add(MinuteTimePair(now.range.second, itemFinalTime))
              break
            } else {
              // 进入下一次 while 循环
              prevBegin = now.range.second
              continue
            }
          }
        }
      }
    }
  }
  return itemOverlap
}

/** 零长度区间只作为视觉切割锚点，不改变任何业务时间范围。 */
private fun MinuteTimePair.isPoint(): Boolean = first == second

// 合并区间
internal fun List<MinuteTimePair>.mergeOverlapRange(): List<MinuteTimePair> {
  val coveredList = mutableListOf<MinuteTimePair>()
  fastForEach { item ->
    var index = 0
    while (index < coveredList.size && coveredList[index].second < item.first) {
      index++ // 找到第一个 [index].final >= item.first 的位置
    }
    if (index == coveredList.size) {
      // 当前 item 比所有的都大
      coveredList.add(item)
    } else {
      // 此时 item.first <= now.final
      //       ------ ｜     ----?????  ｜   -----??????  ｜
      // ======       ｜  ==========    ｜      ======    ｜
      var itemBegin = item.first
      while (true) {
        val now = coveredList[index]
        if (now.first > item.second) {
          // 完全不相交
          //        ------
          // ======
          coveredList.add(index, item)
          break
        } else if (now.first == item.second) {
          // 刚好相等
          //       ------
          // ======
          coveredList[index] = MinuteTimePair(itemBegin, now.second)
          break
        } else if (now.first >= itemBegin) {
          // 此时 itemBegin <= now.first < item.second
          //    ----?????
          // ==========
          if (now.second >= item.second) {
            //    ----------
            // ==========
            coveredList[index] = MinuteTimePair(itemBegin, now.second)
            break
          } else {
            //    -----
            // ==========
            coveredList.removeAt(index)
            if (index == coveredList.size) {
              coveredList.add(MinuteTimePair(itemBegin, item.second))
              break
            } else {
              continue
            }
          }
        } else {
          // 此时 now.first < itemBegin <= now.second
          // -----??????
          //    ======
          if (now.second >= item.second) {
            // ------------
            //    ======
            // 已经被包含了，不需要添加
            break
          } else {
            // -------
            //    ======
            coveredList.removeAt(index)
            if (index == coveredList.size) {
              coveredList.add(MinuteTimePair(now.first, item.second))
              break
            } else {
              itemBegin = now.first
              continue
            }
          }
        }
      }
    }
  }
  return coveredList
}
