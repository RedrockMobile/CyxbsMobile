package com.cyxbs.pages.schedule.calendar

import androidx.compose.runtime.Composable
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * 观察当前账号的完整 Schedule 快照并按平台策略同步系统日历。
 * Android 执行实际 reconcile；尚未实现导出的平台提供无副作用空实现。
 */
@Composable
internal expect fun ScheduleCalendarAutoSync(schedules: List<ScheduleEntity>)
