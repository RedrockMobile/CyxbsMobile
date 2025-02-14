package com.cyxbs.components.view.calendar.state

import androidx.compose.runtime.*
import com.cyxbs.components.view.calendar.scroll.HorizontalScrollState
import com.cyxbs.components.view.calendar.scroll.VerticalScrollState
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.Today
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.utils.compose.derivedStateOfStructure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/18 18:43
 */
@Stable
class CalendarState(
  internal val coroutineScope: CoroutineScope,
  val initialClickDate: Date,
  val startDateState: State<Date>,
  val endDateState: State<Date>,
  val today: () -> Date,
  internal var onClick: ((Date) -> Unit)? = null,
) {

  val showBeginDate: Date
    get() = if (currentIsCollapsed) {
      startDateState.value.run { minusDays(dayOfWeekOrdinal) }
    } else {
      startDateState.value.copy(dayOfMonth = 1).run { minusDays(dayOfWeekOrdinal) }
    }

  val showFinalDate: Date
    get() = if (currentIsCollapsed) {
      endDateState.value.run { plusDays(6 - dayOfWeekOrdinal) }
    } else {
      endDateState.value.run { copy(dayOfMonth = lengthOfMonth).plusDays(6 - dayOfWeekOrdinal) }
    }

  // 用于在拖动期间修改 clickDate 时临时保存，拖动后才设置
  internal var tempClickDate: Date? = null

  internal val clickDateState = mutableStateOf(
    initialClickDate.coerceIn(startDateState.value, endDateState.value)
  )

  val clickDate by derivedStateOfStructure {
    if (layoutWidth == 0) {
      clickDateState.value
    } else {
      val diffPage = (horizontalScrollState.value.offset / layoutWidth).roundToInt()
      if (currentIsCollapsed) {
        clickDateState.value.minusWeeks(diffPage)
      } else {
        clickDateState.value.minusMonths(diffPage)
      }.coerceIn(startDateState.value, endDateState.value)
    }
  }

  // 点击事件，自带容量为 1 的缓存，下游消耗不过来时会自动丢弃旧值
  internal val clickEventFlowInternal = MutableSharedFlow<ClickEventData>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  /**
   * 点击事件，下游消耗不过来时会自动丢弃旧值
   */
  val clickEventFlow: Flow<ClickEventData>
    get() = clickEventFlowInternal

  data class ClickEventData(
    val old: Date,
    val new: Date
  )

  init {
    clickEventFlow.onEach {
      onClick?.invoke(it.new) ?: updateClickDate(it.new)
    }.launchIn(coroutineScope)
  }

  /**
   * 更新选中的日期，如果不在当前页则会自动跳转
   */
  fun updateClickDate(date: Date) {
    if (date in startDateState.value..endDateState.value) {
      if (verticalIsScrolling || horizontalIsScrolling) {
        tempClickDate = date
      } else {
        clickDateState.value = date
        tempClickDate = null
      }
    }
  }

  val pageCount: Int
    get() = getPage(endDateState.value) + 1

  /**
   * 得到当前 date 对应的页数，注意在折叠和展开时页数不一致，如果超出范围则返回 -1
   */
  fun getPage(date: Date): Int {
    val start = startDateState.value
    val end = endDateState.value
    if (date !in start..end) return -1
    return if (currentIsCollapsed) {
      showBeginDate.daysUntil(date) / 7
    } else {
      (date.year - start.year) * 12 + date.monthNumber - start.monthNumber
    }
  }

  val currentIsExpanded: Boolean by derivedStateOfStructure {
    verticalScrollState.value.offset == maxVerticalScrollOffset
  }

  val verticalIsExpanded: Boolean by derivedStateOfStructure {
    verticalScrollState.value is VerticalScrollState.Expanded
  }

  val currentIsCollapsed: Boolean by derivedStateOfStructure {
    verticalScrollState.value.offset == 0F
  }

  val verticalIsCollapsed: Boolean by derivedStateOfStructure {
    verticalScrollState.value is VerticalScrollState.Collapsed
  }

  val verticalIsScrolling: Boolean by derivedStateOfStructure {
    verticalScrollState.value is VerticalScrollState.Scrolling
  }

  val horizontalIsScrolling: Boolean by derivedStateOfStructure {
    horizontalScrollState.value is HorizontalScrollState.Scrolling
  }

  // 手势滚动中的上下滑状态，在滚动未结束时，即使当前显示状态是折叠/展开的，仍处于 Scrolling 状态
  val verticalScrollState: MutableState<VerticalScrollState> =
    mutableStateOf(VerticalScrollState.Collapsed)

  val horizontalScrollState: MutableState<HorizontalScrollState> =
    mutableStateOf(HorizontalScrollState.Idle)

  // 是否允许垂直嵌套滑动
  var enableVerticalScroll: Boolean = true

  // 日历日期视图的宽度，不包含其他东西
  internal var layoutWidth by mutableIntStateOf(0)

  // 日历日期行的高度
  val lineHeightState = mutableFloatStateOf(0F)

  val maxVerticalScrollOffset: Float
    get() = lineHeightState.value * 5 // 有的月份能显示 6 行，所以乘 5

  /**
   * 当前滑动的值
   */
  val verticalScrollOffset: Float by derivedStateOfStructure {
    verticalScrollState.value.offset
  }

  /**
   * 当前滑动的比例，[0.0, 1.0]
   */
  val fraction: Float by derivedStateOfStructure {
    verticalScrollState.value.offset / maxVerticalScrollOffset
  }
}

@Composable
fun rememberCalendarState(
  initialClickDate: Date = TodayNoEffect,
  startDate: Date = Date(1901, 1, 1),
  endDate: Date = Date(2099, 12, 31),
  today: () -> Date = { Today },
  onClick: ((Date) -> Unit)? = null,
): CalendarState {
  val coroutineScope = rememberCoroutineScope()
  val startDateState = rememberUpdatedState(startDate)
  val endDateState = rememberUpdatedState(endDate)
  return remember {
    CalendarState(
      coroutineScope = coroutineScope,
      initialClickDate = initialClickDate,
      startDateState = startDateState,
      endDateState = endDateState,
      onClick = onClick,
      today = today,
    )
  }.apply { this.onClick = onClick }
}
