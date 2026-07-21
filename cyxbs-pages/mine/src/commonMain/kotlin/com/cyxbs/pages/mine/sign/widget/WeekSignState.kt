package com.cyxbs.pages.mine.sign.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import com.cyxbs.pages.mine.sign.util.SignUtil

/**  
 * description: 周组件的State
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/20 16:36
 */
@Stable
class WeekSignState(
  initialProgress: Float = 1f
) {

  private val _progress = Animatable(initialProgress)
  val progress: Float get() = _progress.value

  /**
   * 不带动画的设置进度（默认target = 1f）
   */
  suspend fun setProgress(progress: Float = 1f) {
    _progress.snapTo(progress)
  }

  /**
   * 带动画的设置进度（默认target = 1f）
   */
  suspend fun animateProgress(progress: Float = 1f) {
    _progress.animateTo(
      targetValue = progress,
      animationSpec = tween(1000, easing = LinearOutSlowInEasing)
    )
  }

}

/**
 * 每天的圆点状态
 */
enum class WeekDayState {
  SIGNED, // 蓝色圆点
  TODAY_PENDING, // 钻石
  PENDING // 灰色圆点
}

/**
 * 线的状态
 */
enum class WeekLineState {
  BLUE,
  LIGHT_BLUE,
  GREY
}

/**
 * 把数据类的weekInfo转WeekDayState
 */
fun String.toWeekDayStates(
  todayIndex: Int = SignUtil.getTodayOfWeek()
): List<WeekDayState> {
  return List(7) { index ->
    when {
      getOrNull(index) == '1' -> WeekDayState.SIGNED
      index == todayIndex -> WeekDayState.TODAY_PENDING
      else -> WeekDayState.PENDING
    }
  }
}

/**
 * 把数据类转成WeekLineState
 */
fun String.toWeekLineStates(
  todayIndex: Int = SignUtil.getTodayOfWeek(),
): List<WeekLineState> {
  return List(6) { index ->
    val leftSigned = getOrNull(index) == '1'
    val rightSigned = getOrNull(index + 1) == '1'

    when {
      // 1. 两端都签到；
      // 2. 左端签到，右端是今天；
      // 3. 左端是今天，且今天已签到。
      (leftSigned && rightSigned) ||
          (leftSigned && todayIndex == index + 1) ||
          (todayIndex == index && leftSigned) -> {
        WeekLineState.BLUE
      }

      // 左端签到、右端未签到，且右端不是今天。
      leftSigned && !rightSigned && todayIndex != index + 1 -> {
        WeekLineState.LIGHT_BLUE
      }

      else -> {
        WeekLineState.GREY
      }
    }
  }
}

/**
 * 根据本周今天之前的连续签到天数，计算今天可获得的积分。
 */
fun String.getTodayScore(
  todayIndex: Int = SignUtil.getTodayOfWeek(),
): Int {
  var continuousSignedDays = 0
  for (index in (todayIndex - 1) downTo 0) {
    if (getOrNull(index) != '1') break
    continuousSignedDays++
  }
  return intArrayOf(10, 15, 20, 25, 30, 30, 30)[continuousSignedDays]
}
