package com.cyxbs.pages.schedule.calendar

import androidx.compose.runtime.Composable
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * 系统日历设置入口。
 * Android 展示权限、自动导入、清理和重导入 UI；尚未实现日历导出的平台暂不展示入口。
 */
@Composable
internal expect fun ScheduleCalendarSettingsButton(schedules: List<ScheduleEntity>)
