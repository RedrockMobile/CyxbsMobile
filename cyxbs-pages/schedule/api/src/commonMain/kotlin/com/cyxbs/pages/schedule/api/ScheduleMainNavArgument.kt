package com.cyxbs.pages.schedule.api

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 日程（邮子清单）主页的导航参数。
 *
 * [todoId] 为空时进入主页；非空时在数据加载后打开对应日程。
 * [recurrenceId] 是重复 occurrence 的原始锚点日期，用于仅此次/此次及后续编辑。
 *
 * URL 由 Navigation 统一编码为 `cyxbs://schedule?todoId=...&recurrenceId=yyyy-MM-dd`，同时可写入
 * Android CalendarContract 的 CUSTOM_APP_URI，作为系统事件映射标识与返回应用的 deep link。
 */
@Serializable
data class ScheduleMainNavArgument(
  val todoId: Long? = null,
  val recurrenceId: Date? = null,
) : AppNavArgument
