package com.cyxbs.pages.schedule.api

import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable

/**
 * 日程（邮子清单）主页的导航参数。
 *
 * 对外取代旧的 `TodoMainNavArgument`，发现页 feed 与其他入口跳转日程主页时使用。
 */
@Serializable
object ScheduleMainNavArgument : AppNavArgument
