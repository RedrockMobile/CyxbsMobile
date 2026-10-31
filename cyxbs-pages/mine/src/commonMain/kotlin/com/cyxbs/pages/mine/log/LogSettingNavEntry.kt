package com.cyxbs.pages.mine.log

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.log.AppLogManager
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.log.LogStorageResult
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_LOG_SETTING
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

/** mine 模块内部的诊断日志设置路由参数。 */
@Serializable
data object LogSettingNavArgument : AppNavArgument

/**
 * 诊断日志独立页面。
 *
 * 页面只负责用户授权、导出和清理；文件阈值、轮转与目录策略继续由日志组件统一管理。
 */
@AppNav(route = NAV_LOG_SETTING)
class LogSettingNavEntry : AppNavEntry<LogSettingNavArgument>() {

  override fun isNeedLogin(argument: LogSettingNavArgument): Boolean = true

  @Composable
  override fun Content(argument: LogSettingNavArgument) {
    LogSettingPage(argument)
  }
}

/** 展示诊断日志授权及管理操作，不把日志实现细节泄漏回通用设置页。 */
@Composable
private fun LogSettingPage(argument: LogSettingNavArgument) {
  val supported = remember { AppLogManager.isDiagnosticLoggingSupported() }
  var checked by remember { mutableStateOf(AppLogManager.isDiagnosticLoggingEnabled()) }
  var changing by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val shareAction = rememberDiagnosticLogShareAction()
  val clearAction = rememberDiagnosticLogClearAction()

  Column(
    modifier = Modifier.fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    LogSettingTopBar(argument)
    Column(
      modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      LogSwitchRow(
        checked = checked,
        enabled = supported && !changing,
        onCheckedChange = { enabled ->
          if (!changing) {
            changing = true
            coroutineScope.launch {
              try {
                when (val result = AppLogManager.setDiagnosticLoggingEnabled(enabled)) {
                  LogStorageResult.Success -> Unit
                  LogStorageResult.Unsupported -> toast("当前平台不支持诊断日志")
                  is LogStorageResult.Failure -> toast(result.message)
                }
              } catch (throwable: CancellationException) {
                throw throwable
              } catch (throwable: Throwable) {
                toast(throwable.message ?: "诊断日志设置失败")
              } finally {
                checked = AppLogManager.isDiagnosticLoggingEnabled()
                changing = false
              }
            }
          }
        },
      )
      Text(
        text = if (supported) {
          "默认开启，可随时关闭。仅在本机有界保存 Info 及以上的诊断上下文，用于定位问题；不会自动上传。"
        } else {
          "当前平台不支持本地诊断日志。"
        },
        color = LocalAppColors.current.tvLv3,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      )
      LogActionRow(text = "导出并分享诊断日志", enabled = supported, onClick = shareAction)
      LogActionRow(text = "清除诊断日志", enabled = supported, onClick = clearAction)
      Text(
        text = "清除会同时删除原始日志和已经生成的导出文件。",
        color = LocalAppColors.current.tvLv3,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      )
    }
  }
}

/** 绘制诊断日志页面标题栏。 */
@Composable
private fun LogSettingTopBar(argument: LogSettingNavArgument) {
  TopAppBar(backgroundColor = Color.Transparent, elevation = 0.dp) {
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(ConfigRes.configIcBack()),
        contentDescription = "返回",
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(16.dp)
          .clickableSingle { argument.popBackStack() },
      )
      Text(
        text = "诊断日志",
        color = LocalAppColors.current.tvLv1,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

/** 诊断日志授权开关，整行和开关本身行为一致。 */
@Composable
private fun LogSwitchRow(
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
      .clickableNoIndicator { if (enabled) onCheckedChange(!checked) }
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "记录诊断日志",
      color = LocalAppColors.current.tvLv2,
      fontSize = 15.sp,
      modifier = Modifier.weight(1F),
    )
    Switch(
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(checkedThumbColor = LocalAppColors.current.positive),
    )
  }
}

/** 无箭头的日志管理动作，禁用态用于不支持文件日志的平台。 */
@Composable
private fun LogActionRow(text: String, enabled: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
      .clickableNoIndicator { if (enabled) onClick() }
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text,
      color = if (enabled) LocalAppColors.current.tvLv2 else LocalAppColors.current.tvLv3,
      fontSize = 15.sp,
    )
  }
}
