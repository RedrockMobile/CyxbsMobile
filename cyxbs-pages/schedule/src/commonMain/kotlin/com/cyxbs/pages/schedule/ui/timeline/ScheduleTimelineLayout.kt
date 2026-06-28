package com.cyxbs.pages.schedule.ui.timeline

/**
 * 时间轴重叠并列布局算法（commonMain 纯函数，可单测）。
 *
 * 把同一天的有时刻事件分配到并列的「列」中：互相重叠的事件会被分到不同列、平分宽度，
 * 与日历类 / 会议类软件的日视图一致。
 */

/**
 * 已定位的事件。
 *
 * @param columnIndex 所在列下标，从 0 开始。
 * @param columnCount 该事件所在「重叠簇」的总列数（同簇事件共享同一值），渲染宽度 = 区宽 / columnCount。
 */
data class PositionedTimedSchedule(
  val event: DayTimedSchedule,
  val columnIndex: Int,
  val columnCount: Int,
)

/**
 * 截止类型（粗线 + 标题）没有真实时长，给一个用于「重叠判定」的等效占用时长（分钟），
 * 约等于一条标题行的高度，避免相邻很近的两条截止线标题在视觉上叠在一起。
 */
private const val DEADLINE_SPAN_MINUTES = 24

/**
 * 对一天的有时刻事件做并列布局。输入需已按开始分钟升序（[timedSchedulesForDate] 已保证）。
 *
 * 算法：扫描线分簇 + 贪心列分配。
 * - 列分配：每个事件放入「上一个结束 ≤ 当前开始」的第一个空闲列，没有则新开一列。
 * - 分簇：当一个事件的开始时间 ≥ 当前簇内所有事件的最大结束时间时，说明与前面完全不重叠，
 *   结算上一簇（簇内所有事件的 columnCount 取该簇用到的最大列数）。
 */
internal fun layoutTimedSchedules(events: List<DayTimedSchedule>): List<PositionedTimedSchedule> {
  if (events.isEmpty()) return emptyList()

  val result = ArrayList<PositionedTimedSchedule>(events.size)
  val columnsLastEnd = ArrayList<Int>()   // 当前簇每一列的最后结束分钟
  val groupIndices = ArrayList<Int>()      // 当前簇在 result 中的下标
  var groupMaxEnd = Int.MIN_VALUE

  fun effEnd(e: DayTimedSchedule): Int =
    if (e.isInterval) e.endMin else e.startMin + DEADLINE_SPAN_MINUTES

  fun flushGroup() {
    val count = columnsLastEnd.size
    for (idx in groupIndices) {
      result[idx] = result[idx].copy(columnCount = count)
    }
    columnsLastEnd.clear()
    groupIndices.clear()
    groupMaxEnd = Int.MIN_VALUE
  }

  for (e in events) {
    if (groupIndices.isNotEmpty() && e.startMin >= groupMaxEnd) {
      flushGroup()
    }
    var col = -1
    for (i in columnsLastEnd.indices) {
      if (columnsLastEnd[i] <= e.startMin) {
        col = i
        break
      }
    }
    if (col == -1) {
      col = columnsLastEnd.size
      columnsLastEnd.add(effEnd(e))
    } else {
      columnsLastEnd[col] = effEnd(e)
    }
    result.add(PositionedTimedSchedule(e, columnIndex = col, columnCount = 1))
    groupIndices.add(result.size - 1)
    groupMaxEnd = maxOf(groupMaxEnd, effEnd(e))
  }
  flushGroup()
  return result
}
