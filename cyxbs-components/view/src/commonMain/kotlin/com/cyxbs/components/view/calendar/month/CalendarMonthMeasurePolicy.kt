package com.cyxbs.components.view.calendar.month

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import com.cyxbs.components.view.calendar.scroll.HorizontalScrollState
import com.cyxbs.components.view.calendar.scroll.VerticalScrollState
import com.cyxbs.components.config.time.Date
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/25 22:11
 */
@OptIn(ExperimentalFoundationApi::class)
internal class CalendarMonthMeasurePolicy(
  private val verticalScrollState: State<VerticalScrollState>,
  private val horizontalScrollState: State<HorizontalScrollState>,
  private val startDateState: State<Date>,
  private val endDateState: State<Date>,
  private val clickDateState: State<Date>,
  private val lineHeightState: MutableFloatState,
  private val itemProvider: CalendarMonthItemProvider,
) : (LazyLayoutMeasureScope, Constraints) -> MeasureResult {

  override fun invoke(scope: LazyLayoutMeasureScope, constraints: Constraints): MeasureResult {
    itemConstraints = Constraints.fixed(0, 0)
    return when (val vertical = verticalScrollState.value) {
      VerticalScrollState.Collapsed -> when (horizontalScrollState.value) {
        HorizontalScrollState.Idle -> scope.collapsedHorizontalIdleMeasure(constraints)
        is HorizontalScrollState.Scrolling -> scope.collapsedHorizontalScrollingMeasure(constraints)
      }

      is VerticalScrollState.Expanded -> when (horizontalScrollState.value) {
        HorizontalScrollState.Idle -> scope.expandedHorizontalIdleMeasure(constraints)
        is HorizontalScrollState.Scrolling -> scope.expandedHorizontalScrollingMeasure(constraints)
      }

      is VerticalScrollState.Scrolling -> scope.verticalScrollingMeasure(constraints, vertical)
    }
  }

  private var itemConstraints = Constraints.fixed(0, 0)

  // 测量当前点击日期所在月的布局
  private fun LazyLayoutMeasureScope.measureMonth(
    parentConstraints: Constraints
  ): Array<Array<Placeable>> {
    val lineCount = clickDateState.value.monthLineCount()
    val firstDate = clickDateState.value.firstDate
    return Array(lineCount) {
      // 月份视图不会超出范围，所以打 !!
      if (it != lineCount - 1) {
        // 不是最后一行时
        measureWeekLine(firstDate.plusWeeks(it), parentConstraints)!!
      } else {
        measureWeekLine(firstDate.lastDate, parentConstraints)!!
      }
    }
  }

  /**
   * ```
   * 对于: 30  31  1   2   3   4   5
   * 如果传递的是 30-31 号，则返回上一个月的最后一行，如果是 1-5 号，则返回下一个月的第一行
   * ```
   */
  private fun LazyLayoutMeasureScope.measureWeekLine(
    date: Date,
    parentConstraints: Constraints,
  ): Array<Placeable>? {
    if (date < startDateState.value.firstDate.weekBeginDate) return null
    if (date > endDateState.value.lastDate.weekFinalDate) return null
    val beginDate = date.weekBeginDate
    return Array(7) {
      val nowDate = beginDate.plusDays(it)
      measure(nowDate, nowDate.monthNumber == date.monthNumber, parentConstraints)
    }
  }

  private fun LazyLayoutMeasureScope.measure(
    date: Date,
    isNowMonth: Boolean,
    parentConstraints: Constraints,
  ): Placeable {
    return measureInternal(itemProvider.getIndex(date, isNowMonth), parentConstraints)
  }

  private fun LazyLayoutMeasureScope.measureInternal(
    itemIndex: Int,
    parentConstraints: Constraints,
  ): Placeable {
    if (itemConstraints.isZero) {
      val width = parentConstraints.maxWidth / 7
      val placeable = measure(
        itemIndex,
        parentConstraints.copy(minWidth = width, maxWidth = width)
      ).first()
      itemConstraints = Constraints.fixed(width, placeable.height)
      lineHeightState.value = placeable.height.toFloat()
      return placeable
    }
    return measure(itemIndex, itemConstraints).first()
  }

  /**
   * 折叠并且左右滑动静止
   * ```
   * 此时 UI 显示的状态为:
   *            左边页                       中间页                        右边页
   *                            | 30  31  1   2   3   4   5  |
   *                            |            空行             |
   * ---------------------------|----------------------------|---------------------------
   * 6   7   8   9   10  11  12 | 13  14  15  16  17  18  19 | 20  21  22  23  24  25  26
   * ---------------------------|----------------------------|---------------------------
   *                            |            空行             |
   *                            | 27  28  29  30  1   2   3  |
   *
   * 因为是静止状态，中间页会提前绘制上下行以减轻上下滑的卡顿
   * 空行其实就是左右页显示的行，在展开时会直接布局过去
   * ```
   */
  private fun LazyLayoutMeasureScope.collapsedHorizontalIdleMeasure(constraints: Constraints): MeasureResult {
    val monthArray = measureMonth(constraints)
    val showLine = clickDateState.value.run { firstDate.dayOfWeekOrdinal + dayOfMonth - 1 } / 7
    var lineLeft: Array<Placeable>? = emptyArray()
    var lineRight: Array<Placeable>? = emptyArray()
    when (showLine) {
      0 -> lineLeft = measureWeekLine(clickDateState.value.minusWeeks(1), constraints)
      1 -> clickDateState.value.minusWeeks(1).let {
        if (it.monthNumber != clickDateState.value.monthNumber) {
          lineLeft = measureWeekLine(it, constraints)
        }
      }

      monthArray.lastIndex - 1 -> clickDateState.value.plusWeeks(1).let {
        if (it.monthNumber != clickDateState.value.monthNumber) {
          lineRight = measureWeekLine(it, constraints)
        }
      }

      monthArray.lastIndex -> lineRight =
        measureWeekLine(clickDateState.value.plusWeeks(1), constraints)
    }
    // null 表示有但超过范围了，emptyArray 表示没有
    if (lineLeft?.isEmpty() == true) {
      lineLeft = monthArray[showLine - 1]
      monthArray[showLine - 1] = emptyArray()
    }
    if (lineRight?.isEmpty() == true) {
      lineRight = monthArray[showLine + 1]
      monthArray[showLine + 1] = emptyArray()
    }
    val width = constraints.maxWidth / 7
    val parentWidth = width * 7 // 确保是 7 的倍数
    val lineHeight = itemConstraints.maxHeight
    return layout(parentWidth, lineHeight) {
      placeRelativeWithLayerHorizontal(lineLeft, x = -parentWidth)
      placeRelativeWithLayerHorizontal(lineRight, x = parentWidth)
      val lineDistance = if (monthArray.size == 6) 0F else lineHeight / 4F
      // topOffset 为顶部隐藏的距离
      val topOffset = -showLine * (lineHeight + lineDistance)
      repeat(monthArray.size) {
        placeRelativeWithLayerHorizontal(
          array = monthArray[it],
          y = (topOffset + (lineHeight + lineDistance) * it).roundToInt()
        )
      }
    }
  }

  private fun Placeable.PlacementScope.placeRelativeWithLayerHorizontal(
    array: Array<Placeable>?,
    x: Int = 0,
    y: Int = 0
  ) {
    array ?: return
    var left = x
    repeat(array.size) {
      val placeable = array[it]
      placeable.placeRelativeWithLayer(x = left, y = y)
      left += placeable.width
    }
  }

  /**
   * 折叠时进行左右滑动
   * ```
   * 此时 UI 显示的状态为:
   *            左边页                       中间页                        右边页
   * ---------------------------|----------------------------|---------------------------
   * 6   7   8   9   10  11  12 | 13  14  15  16  17  18  19 | 20  21  22  23  24  25  26
   * ---------------------------|----------------------------|---------------------------
   *
   * 左右滑动的情况下将不再提前绘制上下行
   * ```
   */
  private fun LazyLayoutMeasureScope.collapsedHorizontalScrollingMeasure(
    constraints: Constraints,
  ): MeasureResult {
    val width = constraints.maxWidth / 7
    val parentWidth = width * 7 // 确保是 7 的倍数
    // 以 13 号为准滑动的列数
    val columnDiff = (horizontalScrollState.value.offset / width).roundToInt()
    val startDate = clickDateState.value
      .minusDays(7)
      .minusDays(clickDateState.value.dayOfWeekOrdinal + columnDiff)
    val beginDate = startDateState.value.weekBeginDate
    val finalDate = endDateState.value.weekFinalDate
    val array = Array(21) {
      val date = startDate.plusDays(it)
      if (date !in beginDate..finalDate) return@Array null
      val weekClickDate =
        date.plusDays(clickDateState.value.dayOfWeekOrdinal - date.dayOfWeekOrdinal)
          .coerceIn(startDateState.value, endDateState.value)
      measure(date, date.monthNumber == weekClickDate.monthNumber, constraints)
    }
    val lineHeight = itemConstraints.maxHeight
    return layout(parentWidth, lineHeight) {
      val offset = (horizontalScrollState.value.offset - (columnDiff + 7) * width).roundToInt()
      repeat(array.size) {
        array[it]?.placeRelativeWithLayer(x = offset + it * width, y = 0)
      }
    }
  }

  /**
   * 上下滑动
   * ```
   * 此时 UI 显示的状态为:
   *            左边页                       中间页                        右边页
   *                            | 30  31  1   2   3   4   5  |
   *                            | 6   7   8   9   10  11  12 |
   *             空             | 13  14  15  16  17  18  19 |             空
   *                            | 20  21  22  23  24  25  26 |
   *                            | 27  28  29  30  1   2   3  |
   * ```
   */
  private fun LazyLayoutMeasureScope.verticalScrollingMeasure(
    constraints: Constraints,
    vertical: VerticalScrollState.Scrolling,
  ): MeasureResult {
    val monthArray = measureMonth(constraints)
    val width = constraints.maxWidth / 7
    val parentWidth = width * 7 // 确保是 7 的倍数
    val lineHeight = itemConstraints.maxHeight
    return layout(parentWidth, lineHeight + vertical.offset.roundToInt()) {
      val showLine = clickDateState.value.run { firstDate.dayOfWeekOrdinal + dayOfMonth - 1 } / 7
      val lineDistance = if (monthArray.size == 6) 0F else lineHeight / 4F
      // topOffset 为顶部隐藏的距离
      val topOffset = -(lineHeight * 5 - vertical.offset) / (monthArray.size - 1) * showLine
      repeat(monthArray.size) {
        placeRelativeWithLayerHorizontal(
          array = monthArray[it],
          y = (topOffset + (lineHeight + lineDistance) * it).roundToInt()
        )
      }
    }
  }

  /**
   * 展开时并且左右滑动静止
   * ```
   * 此时 UI 显示的状态为:
   *            左边页                       中间页                        右边页
   *                         1  | 30  31  1   2   3   4   5  |  27
   *                         8  | 6   7   8   9   10  11  12 |  4
   *                         15 | 13  14  15  16  17  18  19 |  11
   *                         22 | 20  21  22  23  24  25  26 |  18
   *                         29 | 27  28  29  30  1   2   3  |  25
   *                         5
   * ```
   */
  private fun LazyLayoutMeasureScope.expandedHorizontalIdleMeasure(
    constraints: Constraints
  ): MeasureResult {
    val width = constraints.maxWidth / 7
    val leftTopDate = getLeftTopDateExpended(width) // 得到注释中的 30 号
    val spareColumn = 1 // 多余的单页列数
    val topDate = getTopDate(leftTopDate, -spareColumn)
    return expandedMeasureInternal(constraints, topDate, 2 * spareColumn + 7, spareColumn)
  }

  /**
   * 展开时进行左右滑动
   * ```
   * 此时 UI 显示的状态为:
   *            左边页                       中间页                        右边页
   *                         1  | 30  31  1   2   3   4   5  |
   *                         8  | 6   7   8   9   10  11  12 |
   *                         15 | 13  14  15  16  17  18  19 |
   *                         22 | 20  21  22  23  24  25  26 |
   *                         29 | 27  28  29  30  1   2   3  |
   *                         5
   *
   * 展开状态时左右页只布局一列，如果向右偏离时则左边布局一列，如果向右偏离时则右边布局一列
   * ```
   */
  private fun LazyLayoutMeasureScope.expandedHorizontalScrollingMeasure(
    constraints: Constraints,
  ): MeasureResult {
    val width = constraints.maxWidth / 7
    val leftTopDate = getLeftTopDateExpended(width) // 得到注释中的 30 号
    if (horizontalScrollState.value.offset > 0) {
      val topDate = getTopDate(leftTopDate, -1)
      return expandedMeasureInternal(constraints, topDate, 8, 1)
    } else {
      return expandedMeasureInternal(constraints, leftTopDate, 8, 0)
    }
  }

  private fun LazyLayoutMeasureScope.expandedMeasureInternal(
    constraints: Constraints,
    topDate: Date,
    columnCount: Int,
    leftColumnDiff: Int, // 左页需要布局的列数
  ): MeasureResult {
    var date = topDate
    val columnArray = Array(columnCount) {
      val array = measureColumn(date, constraints)
      date = getTopDate(date, 1)
      array
    }
    val width = constraints.maxWidth / 7
    val parentWidth = width * 7 // 确保是 7 的倍数
    val lineHeight = itemConstraints.maxHeight
    return layout(parentWidth, lineHeight * 6) {
      // 以 30 号为准滑动的列数
      val columnDiff = (horizontalScrollState.value.offset / width).toInt()
      repeat(columnArray.size) { column ->
        val placeableArray = columnArray[column] ?: return@repeat
        val x =
          (horizontalScrollState.value.offset - (columnDiff + leftColumnDiff - column) * width).roundToInt()
        val lineDistance = if (placeableArray.size == 6) 0F else lineHeight / 4F
        repeat(placeableArray.size) { row ->
          placeableArray[row].placeRelativeWithLayer(
            x = x,
            y = ((lineHeight + lineDistance) * row).roundToInt()
          )
        }
      }
    }
  }

  private fun getLeftTopDateExpended(
    width: Int,
    offset: Float = horizontalScrollState.value.offset
  ): Date {
    val topDate = clickDateState.value.firstDate.weekBeginDate
    val columnDiff = (offset / width).toInt()
    val columnsToAdd = -columnDiff
    return getTopDate(topDate, columnsToAdd)
  }

  private fun getTopDate(topDate: Date, columnsToAdd: Int): Date {
    if (columnsToAdd == 0) return topDate
    // 以月视图的第一个号数开始计算，注意不一定是一号
    val columns = topDate.dayOfWeekOrdinal + columnsToAdd
    val firstDate =
      if (topDate.dayOfMonth < 15) topDate.firstDate else topDate.plusMonths(1).firstDate
    return firstDate.plusMonths(columns / 7) // 先加上翻的页数
      .run {
        val dateDiff = columns % 7
        if (dateDiff >= 0) {
          plusDays(dateDiff - dayOfWeekOrdinal)
        } else {
          minusMonths(1).firstDate.weekFinalDate.minusDays(-dateDiff - 1)
        }
      }
  }

  /**
   * 从顶部 [topDate] 遍历当前列
   */
  private inline fun <reified T> forEachColumn(
    topDate: Date,
    block: (itemIndex: Int) -> T
  ): Array<T>? {
    // 只有展开时才会调用该方法，所以这里直接判断是否在月份显示视图的范围内
    if (topDate <= startDateState.value.firstDate.minusMonths(1).weekFinalDate) return null
    if (topDate >= endDateState.value.lastDate.firstDate.plusMonths(1).weekBeginDate) return null
    val secondLineDate = topDate.plusWeeks(1)
    val monthNumber = secondLineDate.monthNumber
    val lineCount = secondLineDate.monthLineCount()
    return Array(lineCount) {
      val date = topDate.plusWeeks(it)
      block.invoke(itemProvider.getIndex(date, date.monthNumber == monthNumber))
    }
  }

  private fun LazyLayoutMeasureScope.measureColumn(
    topDate: Date,
    parentConstraints: Constraints,
  ): Array<Placeable>? {
    return forEachColumn(topDate) {
      measureInternal(it, parentConstraints)
    }
  }
}

