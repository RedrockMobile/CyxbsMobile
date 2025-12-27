package com.cyxbs.pages.course.view.overlay

import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWrapper

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


@Stable
class CourseItemOverlap(
  val wrapper: CourseItemWrapper<*>,
  val showRangeList: List<CourseItemRange>, // 能够展示区域
) {
  // ItemCover 类型会在判断 equal 时使父子构成循环引用，所以需要单独分离

  // 被覆盖的区域
  // 其 CourseItemCover#itemOverlap 为上层 item
  val coveredRangeList: MutableList<CourseItemCover> = mutableListOf()

  // 覆盖的 item 集合，只保存了直接覆盖的子 item，如果需要查找所有覆盖的 item，需要进行递归收集
  // 其 CourseItemCover#itemOverlap 为下层 item
  val coveredItemList: MutableList<CourseItemCover> = mutableListOf()

}

@Stable
data class CourseItemRange(
  val begin: MinuteTime,
  val final: MinuteTime,
)

@Stable
data class CourseItemCover(
  val range: CourseItemRange,
  val itemOverlap: CourseItemOverlap,
)


fun CourseItemState.createOverlapResult(
  coveredList: MutableList<OverlapCover>,
): OverlapResult {
  val itemState = this
  val itemBeginTime = itemState.item.whatTime.now.value.beginTime
  val itemFinalTime = itemState.item.whatTime.now.value.finalTime
  val itemOverlap = OverlapResult(itemState)
  val showRangeList = itemOverlap.showRangeList
  var index = 0
  while (index < coveredList.size && coveredList[index].range.second <= itemBeginTime) {
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
      if (now.range.first >= itemFinalTime) {
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
