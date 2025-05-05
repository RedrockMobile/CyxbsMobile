package com.cyxbs.pages.course.view.overlay

import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.CourseItemModel

/**
 * 重叠的数据
 *
 * @author 985892345
 * @date 2025/2/15
 */
@Stable
data class CourseItemOverlap(
  val item: CourseItemModel,
  val showRangeList: List<CourseItemRange>, // 能够展示区域
) {
  // ItemCover 类型会在判断 equal 时使父子构成循环引用，所以需要单独分离
  val coveredRangeList: MutableList<CourseItemCover> = mutableListOf() // 被覆盖的区域

  // 覆盖的 item 集合，只保存了直接覆盖的子 item，如果需要查找所有覆盖的 item，需要进行递归收集
  val coveredItemList: MutableList<CourseItemCover> = mutableListOf()

  companion object {
    fun transformOverlap(items: List<CourseItemModel>): List<CourseItemOverlap> {
      val coveredList = mutableListOf<CourseItemCover>()
      return items
        .asReversed()
        .map {
          transform(it, coveredList)
        }.asReversed()
    }
  }
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


private fun transform(
  item: CourseItemModel,
  coveredList: MutableList<CourseItemCover>,
): CourseItemOverlap {
  val showRangeList = mutableListOf<CourseItemRange>()
  val itemOverlap = CourseItemOverlap(item, showRangeList)
  var index = 0
  while (index < coveredList.size && coveredList[index].range.final <= item.beginTime) {
    index++ // 找到第一个 [index].final > item.begin 的位置
  }
  if (index == coveredList.size) {
    // 当前 item 比所有的都大
    val range = CourseItemRange(item.beginTime, item.finalTime)
    showRangeList.add(range)
    coveredList.add(CourseItemCover(range, itemOverlap))
  } else {
    // 此时 item.begin < now.final
    //       ------ ｜     ----?????  ｜   -----??????  ｜
    // ======       ｜  ==========    ｜      ======    ｜
    var itemBegin = item.beginTime
    while (true) {
      val now = coveredList[index]
      if (now.range.begin >= item.finalTime) {
        // 完全不相交
        //       ------
        // ======
        showRangeList.add(CourseItemRange(itemBegin, item.finalTime))
        coveredList.add(
          index,
          CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap)
        )
        break
      } else if (now.range.begin > itemBegin) {
        // 此时 itemBegin < now.begin < item.final
        //    ----?????
        // ==========
        showRangeList.add(CourseItemRange(itemBegin, now.range.begin))
        if (now.range.final == item.finalTime) {
          //    -------
          // ==========
          val coveredRange = CourseItemRange(now.range.begin, now.range.final)
          itemOverlap.coveredRangeList.add(CourseItemCover(coveredRange, now.itemOverlap))
          now.itemOverlap.coveredItemList.add(CourseItemCover(coveredRange, itemOverlap))
          coveredList[index] =
            CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap)
          break
        } else if (now.range.final > item.finalTime) {
          //    ----------
          // ==========
          val coveredRange = CourseItemRange(now.range.begin, item.finalTime)
          itemOverlap.coveredRangeList.add(CourseItemCover(coveredRange, now.itemOverlap))
          now.itemOverlap.coveredItemList.add(CourseItemCover(coveredRange, itemOverlap))
          coveredList[index] =
            CourseItemCover(CourseItemRange(item.finalTime, now.range.final), now.itemOverlap)
          coveredList.add(
            index,
            CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap)
          )
          break
        } else {
          //    ----
          // ==========
          val coveredRange = CourseItemRange(now.range.begin, now.range.final)
          itemOverlap.coveredRangeList.add(CourseItemCover(coveredRange, now.itemOverlap))
          now.itemOverlap.coveredItemList.add(CourseItemCover(coveredRange, itemOverlap))
          coveredList.removeAt(index)
          if (index == coveredList.size) {
            // 没有下一个 item，则直接添加到末尾
            coveredList.add(CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap))
            break
          } else {
            // 进入下一次 while 循环
            itemBegin = now.range.final
            continue
          }
        }
      } else {
        // 此时 now.begin ≤ itemBegin < now.final
        // -----??????
        //    ======
        if (now.range.final == item.finalTime) {
          // ---------
          //    ======
          val coveredRange = CourseItemRange(itemBegin, item.finalTime)
          itemOverlap.coveredRangeList.add(CourseItemCover(coveredRange, now.itemOverlap))
          now.itemOverlap.coveredItemList.add(CourseItemCover(coveredRange, itemOverlap))
          if (now.range.begin == itemBegin) {
            // ------
            // ======
            coveredList.removeAt(index)
            coveredList.add(
              index,
              CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap)
            )
            break
          } else {
            // ---------
            //    ======
            coveredList[index] =
              CourseItemCover(CourseItemRange(now.range.begin, itemBegin), now.itemOverlap)
            coveredList.add(index + 1, CourseItemCover(coveredRange, itemOverlap))
            break
          }
        } else if (now.range.final > item.finalTime) {
          // ------------
          //    ======
          val coveredRange = CourseItemRange(itemBegin, item.finalTime)
          itemOverlap.coveredRangeList.add(CourseItemCover(coveredRange, now.itemOverlap))
          now.itemOverlap.coveredItemList.add(CourseItemCover(coveredRange, itemOverlap))
          if (now.range.begin == itemBegin) {
            // ---------
            // ======
            coveredList[index] =
              CourseItemCover(CourseItemRange(item.finalTime, now.range.final), now.itemOverlap)
            coveredList.add(index,
              CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap))
            break
          } else {
            // ------------
            //    ======
            coveredList[index] =
              CourseItemCover(CourseItemRange(now.range.begin, itemBegin), now.itemOverlap)
            coveredList.add(index + 1, CourseItemCover(coveredRange, itemOverlap))
            coveredList.add(index + 2,
              CourseItemCover(CourseItemRange(item.finalTime, now.range.final), now.itemOverlap))
            break
          }
        } else {
          // now.final < item.final
          // -------
          //    ======
          val coveredRange = CourseItemRange(itemBegin, now.range.final)
          itemOverlap.coveredRangeList.add(CourseItemCover(coveredRange, now.itemOverlap))
          now.itemOverlap.coveredItemList.add(CourseItemCover(coveredRange, itemOverlap))
          if (now.range.begin == itemBegin) {
            // ----
            // ======
            coveredList.removeAt(index)
            if (index == coveredList.size) {
              // 没有下一个 item，则直接添加到末尾
              coveredList.add(
                CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap))
              break
            } else {
              // 进入下一次 while 循环
              itemBegin = now.range.final
              continue
            }
          } else {
            // -------
            //    ======
            coveredList[index] =
              CourseItemCover(CourseItemRange(now.range.begin, itemBegin), now.itemOverlap)
            index++
            if (index == coveredList.size) {
              // 没有下一个 item，则直接添加到末尾
              coveredList.add(
                CourseItemCover(CourseItemRange(item.beginTime, item.finalTime), itemOverlap))
              break
            } else {
              // 进入下一次 while 循环
              itemBegin = now.range.final
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
internal fun List<CourseItemRange>.mergeOverlapRange(): List<CourseItemRange> {
  val coveredList = mutableListOf<CourseItemRange>()
  fastForEach { item ->
    var index = 0
    while (index < coveredList.size && coveredList[index].final < item.begin) {
      index++ // 找到第一个 [index].final >= item.begin 的位置
    }
    if (index == coveredList.size) {
      // 当前 item 比所有的都大
      coveredList.add(item)
    } else {
      // 此时 item.begin <= now.final
      //       ------ ｜     ----?????  ｜   -----??????  ｜
      // ======       ｜  ==========    ｜      ======    ｜
      var itemBegin = item.begin
      while (true) {
        val now = coveredList[index]
        if (now.begin > item.final) {
          // 完全不相交
          //        ------
          // ======
          coveredList.add(index, item)
          break
        } else if (now.begin == item.final) {
          // 刚好相等
          //       ------
          // ======
          coveredList[index] = CourseItemRange(itemBegin, now.final)
          break
        } else if (now.begin >= itemBegin) {
          // 此时 itemBegin <= now.begin < item.final
          //    ----?????
          // ==========
          if (now.final >= item.final) {
            //    ----------
            // ==========
            coveredList[index] = CourseItemRange(itemBegin, now.final)
            break
          } else {
            //    -----
            // ==========
            coveredList.removeAt(index)
            if (index == coveredList.size) {
              coveredList.add(CourseItemRange(itemBegin, item.final))
              break
            } else {
              continue
            }
          }
        } else {
          // 此时 now.begin < itemBegin <= now.final
          // -----??????
          //    ======
          if (now.final >= item.final) {
            // ------------
            //    ======
            // 已经被包含了，不需要添加
            break
          } else {
            // -------
            //    ======
            coveredList.removeAt(index)
            if (index == coveredList.size) {
              coveredList.add(CourseItemRange(now.begin, item.final))
              break
            } else {
              itemBegin = now.begin
              continue
            }
          }
        }
      }
    }
  }
  return coveredList
}
