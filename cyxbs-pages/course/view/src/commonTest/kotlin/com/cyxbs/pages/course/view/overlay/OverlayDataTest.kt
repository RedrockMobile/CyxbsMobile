package com.cyxbs.pages.course.view.overlay

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * 验证课表重叠算法的时间点切割与直接覆盖关系。
 */
class OverlayDataTest {

  /**
   * 时间点保持零分钟语义，同时把后加入的时间段切成前后两个可展示区间。
   */
  @Test
  fun pointSplitsTimedRangeWithoutExpandingBusinessTime() {
    val covered = mutableListOf<OverlapCover>()
    val point = itemState(11, 15, 11, 15).createOverlapResult(covered)
    val timed = itemState(11, 0, 11, 30).createOverlapResult(covered)

    assertEquals(
      listOf(range(11, 0, 11, 15), range(11, 15, 11, 30)),
      timed.showRangeList,
    )
    assertEquals(listOf(range(11, 15, 11, 15)), timed.coveredRangeList.map { it.range })
    assertSame(point, timed.coveredRangeList.single().result)
    assertEquals(range(11, 15, 11, 15), point.showRangeList.single())
  }

  /**
   * 三层重叠时按时间片保存直接上层，避免把间接祖先错误传播给下层。
   */
  @Test
  fun threeLayersKeepOnlyDirectUpperCover() {
    val covered = mutableListOf<OverlapCover>()
    val upper = itemState(14, 0, 16, 0).createOverlapResult(covered)
    val middle = itemState(14, 30, 15, 30).createOverlapResult(covered)
    val lower = itemState(15, 0, 16, 30).createOverlapResult(covered)

    assertEquals(emptyList(), middle.showRangeList)
    assertEquals(listOf(range(14, 30, 15, 30)), middle.coveredRangeList.map { it.range })
    assertSame(upper, middle.coveredRangeList.single().result)

    assertEquals(listOf(range(16, 0, 16, 30)), lower.showRangeList)
    assertEquals(
      listOf(range(15, 0, 15, 30), range(15, 30, 16, 0)),
      lower.coveredRangeList.map { it.range },
    )
    assertSame(middle, lower.coveredRangeList[0].result)
    assertSame(upper, lower.coveredRangeList[1].result)
  }

  /**
   * 构造只携带固定时间的最小课表项状态，避免测试依赖页面与平台 UI。
   */
  private fun itemState(
    beginHour: Int,
    beginMinute: Int,
    finalHour: Int,
    finalMinute: Int,
  ): CourseItemState {
    return CourseItemState(
      TestCourseItem(
        range(beginHour, beginMinute, finalHour, finalMinute),
      )
    )
  }

  /**
   * 构造测试使用的分钟区间。
   */
  private fun range(
    beginHour: Int,
    beginMinute: Int,
    finalHour: Int,
    finalMinute: Int,
  ): MinuteTimePair {
    return MinuteTimePair(
      MinuteTime(beginHour, beginMinute),
      MinuteTime(finalHour, finalMinute),
    )
  }

  /**
   * 为重叠纯算法提供固定时间，不参与真实页面更新。
   */
  private class TestCourseItem(
    range: MinuteTimePair,
  ) : CourseItem(
    whatTime = TestWhatTime(range),
    coroutineScope = CoroutineScope(EmptyCoroutineContext),
  ) {
    @Composable
    override fun CourseItemContent() = Unit
  }

  /**
   * 固定到同一页周一，仅按时间区间提供稳定排序。
   */
  private class TestWhatTime(
    range: MinuteTimePair,
  ) : CourseItemWhatTime {
    override val now = MutableStateFlow(
      CourseItemWhatTime.Fixed(
        page = 0,
        dayOfWeek = DayOfWeek.MONDAY,
        beginTime = range.first,
        finalTime = range.second,
      )
    )

    override fun compareTo(other: CourseItemWhatTime): Int {
      return compareValuesBy(
        this,
        other,
        { it.now.value.beginTime },
        { it.now.value.finalTime },
      )
    }
  }
}
