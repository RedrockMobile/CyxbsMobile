package com.cyxbs.pages.schedule.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 邮子清单独立页面的导航契约。
 *
 * 清单页与课表页共用 Schedule 仓库，但使用独立导航入口和列表交互；可选 identity 用于从首页 Feed
 * 滚动并高亮某条日程或重复实例，不会直接打开编辑弹窗。实例 identity 存在时必须同时提供所属系列 identity。
 */
@Serializable
data class ScheduleTodoNavArgument(
  val scheduleId: ScheduleId? = null,
  val recurrenceId: RecurrenceId? = null,
) : AppNavArgument {
  init {
    require(recurrenceId == null || scheduleId != null) {
      "recurrenceId requires a scheduleId"
    }
  }
}

/**
 * 邮子清单待办详情的独立导航契约。
 *
 * [scheduleId] 定位父日程；[recurrenceId] 非空时定位该重复系列中的精确实例。详情页自行观察共享
 * Schedule 仓库，不在导航参数中复制易过期的标题、时间或分类数据。
 */
@Serializable
data class ScheduleTodoDetailNavArgument(
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId? = null,
) : AppNavArgument
