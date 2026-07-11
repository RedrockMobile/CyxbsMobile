package com.cyxbs.pages.schedule.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.ui.dialog.ScheduleBottomSheet

/**
 * Android 系统日历设置入口。
 *
 * 权限申请必须由用户点击开启/重新导入触发。关闭自动导入只停止后续新增和更新，不隐式删除既有事件；
 * 用户可通过“清理已导入”显式删除 Schedule 管理的事件，或通过“重新导入”执行一次完整 reconcile。
 */
@Composable
internal actual fun ScheduleCalendarSettingsButton(schedules: List<ScheduleEntity>) {
  var show by remember { mutableStateOf(false) }
  var autoImport by remember { mutableStateOf(ScheduleCalendarSettings.autoImport) }
  // 授权结果同时决定开关是否落盘：拒绝任一权限时保持关闭，不产生“已开启但永远无法同步”的状态。
  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val granted = grants[Manifest.permission.READ_CALENDAR] == true &&
      grants[Manifest.permission.WRITE_CALENDAR] == true
    if (granted) {
      autoImport = true
      ScheduleCalendarSettings.autoImport = true
      AndroidScheduleCalendarReconciler.reconcileAll(schedules)
    }
  }

  IconButton(onClick = { show = true }) {
    Icon(
      imageVector = Icons.Rounded.Settings,
      contentDescription = "日历导入设置",
      tint = LocalAppColors.current.tvLv1,
    )
  }

  ScheduleBottomSheet(show = show, onDismiss = { show = false }) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
      Text("系统日历", color = colors.tvLv1)
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("自动导入设置了提醒的日程", color = colors.tvLv2, modifier = Modifier.weight(1f))
        Switch(
          checked = autoImport,
          onCheckedChange = { checked ->
            if (!checked) {
              autoImport = false
              ScheduleCalendarSettings.autoImport = false
            } else if (AndroidScheduleCalendarProvider.hasPermission()) {
              autoImport = true
              ScheduleCalendarSettings.autoImport = true
              AndroidScheduleCalendarReconciler.reconcileAll(schedules)
            } else {
              permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
              )
            }
          },
        )
      }
      Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
          "清理已导入",
          color = colors.negative,
          modifier = Modifier.clickableNoIndicator {
            AndroidScheduleCalendarReconciler.clearAll()
          }.padding(vertical = 10.dp),
        )
        Spacer(Modifier.width(24.dp))
        Text(
          "重新导入",
          color = colors.positive,
          modifier = Modifier.clickableNoIndicator {
            if (AndroidScheduleCalendarProvider.hasPermission()) {
              AndroidScheduleCalendarReconciler.reconcileAll(schedules)
            } else {
              permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
              )
            }
          }.padding(vertical = 10.dp),
        )
      }
    }
  }
}
