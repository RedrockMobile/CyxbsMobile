package com.cyxbs.pages.schedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_SETTINGS
import kotlinx.serialization.Serializable

/** 邮子清单设置页的无参数导航身份。 */
@Serializable
object ScheduleSettingsNavArgument : AppNavArgument

/**
 * 邮子清单独立设置页。
 *
 * 页面只组织跨平台设置项；平台是否展示系统日历同步由 [ScheduleCalendarExportSetting] 的 actual 实现决定。
 */
@AppNav(route = NAV_SCHEDULE_SETTINGS)
class ScheduleSettingsNavEntry : AppNavEntry<ScheduleSettingsNavArgument>() {
  override fun isNeedLogin(argument: ScheduleSettingsNavArgument): Boolean = true

  override fun getContentKey(argument: ScheduleSettingsNavArgument): String = "schedule_settings_singleton"

  @Composable
  override fun Content(argument: ScheduleSettingsNavArgument) {
    ScheduleSettingsPage(onBack = argument::popBackStack)
  }
}

/** 设置页骨架；系统日历项由平台下发，ICS 导出仅预留禁用入口。 */
@Composable
private fun ScheduleSettingsPage(onBack: () -> Unit) {
  val colors = LocalAppColors.current
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bottomBg)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Box(Modifier.fillMaxWidth().height(56.dp)) {
      IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "返回",
          tint = colors.tvLv1,
        )
      }
      Text(
        text = "邮子清单设置",
        modifier = Modifier.align(Alignment.Center),
        color = colors.tvLv1,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
      )
    }
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ScheduleCalendarExportSetting(Modifier.fillMaxWidth())
      ScheduleSettingSwitchRow(
        title = "导出 ICS 文件",
        summary = "暂未开放",
        checked = false,
        enabled = false,
        onCheckedChange = {},
      )
    }
  }
}

/** Schedule 设置页统一的开关行；业务权限和持久化逻辑由调用方负责。 */
@Composable
internal fun ScheduleSettingSwitchRow(
  title: String,
  summary: String? = null,
  checked: Boolean,
  enabled: Boolean = true,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, color = if (enabled) colors.tvLv1 else colors.tvLv2.copy(alpha = 0.5f), fontSize = 16.sp)
      summary?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, color = colors.tvLv2.copy(alpha = if (enabled) 0.7f else 0.4f), fontSize = 13.sp)
      }
    }
    Switch(
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
    )
  }
}

/** 设置页统一的点击操作行；适合立即执行或需要二次确认的动作。 */
@Composable
internal fun ScheduleSettingActionRow(
  title: String,
  summary: String? = null,
  enabled: Boolean = true,
  danger: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = modifier
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        text = title,
        color = when {
          !enabled -> colors.tvLv2.copy(alpha = 0.5f)
          danger -> Color(0xFFD32F2F)
          else -> colors.tvLv1
        },
        fontSize = 16.sp,
      )
      summary?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, color = colors.tvLv2.copy(alpha = if (enabled) 0.7f else 0.4f), fontSize = 13.sp)
      }
    }
  }
}

/** 平台系统日历设置项；不支持的平台返回 Unit，从而不展示该行。 */
@Composable
internal expect fun ScheduleCalendarExportSetting(modifier: Modifier = Modifier)
