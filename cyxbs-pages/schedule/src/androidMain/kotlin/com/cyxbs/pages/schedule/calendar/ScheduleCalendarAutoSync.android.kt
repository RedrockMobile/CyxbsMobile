package com.cyxbs.pages.schedule.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * 页面内的自动同步协调器。
 *
 * 监听仓库完整快照：开关开启且已有权限时执行全量幂等 reconcile；开关关闭时严格遵循“停止新增/更新、
 * 保留既有事件”，只清理由删除、整体完成或关闭提醒产生的失活映射。这里绝不主动申请权限，避免数据
 * 刷新在用户无操作时弹系统权限框；权限只能由设置按钮显式触发。
 */
@Composable
internal actual fun ScheduleCalendarAutoSync(schedules: List<ScheduleEntity>) {
  LaunchedEffect(schedules, ScheduleCalendarSettings.autoImport) {
    if (ScheduleCalendarSettings.autoImport && AndroidScheduleCalendarProvider.hasPermission()) {
      AndroidScheduleCalendarReconciler.reconcileAll(schedules)
    } else if (!ScheduleCalendarSettings.autoImport && AndroidScheduleCalendarProvider.hasPermission()) {
      // 关闭自动导入后不更新既有事件；但删除、完成、关闭提醒仍需清理对应映射。
      AndroidScheduleCalendarReconciler.removeInactiveTodos(schedules)
    }
  }
}
