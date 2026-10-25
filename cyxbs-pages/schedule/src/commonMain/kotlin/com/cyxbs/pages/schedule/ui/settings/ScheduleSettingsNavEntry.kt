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
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_SETTINGS
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.schedule.data.remote.ScheduleApiService
import com.cyxbs.pages.schedule.data.repository.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.calendar.ScheduleIcsDocument
import com.cyxbs.pages.schedule.domain.calendar.ScheduleIcsExporter
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.ui.todo.main.saveScheduleTodoPinnedIds
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

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

/** 设置页骨架；系统日历项由平台下发，ICS 导出使用当前账号已发布的本地快照。 */
@Composable
private fun ScheduleSettingsPage(onBack: () -> Unit) {
  val colors = LocalAppColors.current
  val accountService = remember { IAccountService::class.impl() }
  val api = remember { ScheduleApiService::class.impl() }
  val repository = remember { ScheduleRepositoryProvider.repository }
  val coroutineScope = rememberCoroutineScope()
  val showClearConfirmation = remember { mutableStateOf(false) }
  var clearConfirmationStep by remember { mutableStateOf(1) }
  var isClearing by remember { mutableStateOf(false) }
  var clearResultMessage by remember { mutableStateOf<String?>(null) }
  var isExportingIcs by remember { mutableStateOf(false) }
  var pendingIcsDocument by remember { mutableStateOf<ScheduleIcsDocument?>(null) }
  val icsFileSaveLauncher = rememberScheduleIcsFileSaveLauncher { result ->
    val document = pendingIcsDocument
    pendingIcsDocument = null
    isExportingIcs = false
    when (result) {
      is ScheduleIcsFileSaveResult.Saved -> if (document != null) {
        toastLong(buildString {
          append("已导出 ${document.scheduleCount} 个日程")
          if (document.adjustmentCount > 0) append("，含 ${document.adjustmentCount} 条单次调整")
          if (document.skippedScheduleCount > 0) {
            append("；另有 ${document.skippedScheduleCount} 个当前不可导出项已跳过")
          }
          append("\n${result.displayMessage}")
        })
      }
      ScheduleIcsFileSaveResult.Cancelled -> Unit
      is ScheduleIcsFileSaveResult.Failed -> toast("ICS 文件保存失败，请重试")
      ScheduleIcsFileSaveResult.Unsupported -> toast("当前平台暂不支持导出 ICS 文件")
    }
  }

  /** 冻结当前账号与仓库快照后生成文件；远端暂不可用不影响导出已经确认的本地数据。 */
  fun exportCurrentSnapshotToIcs() {
    if (!icsFileSaveLauncher.isSupported) {
      toast("当前平台暂不支持导出 ICS 文件")
      return
    }
    val expectedAccountId = accountService.session.value.accountId
    val snapshot = repository.snapshot.value
    if (expectedAccountId == null || snapshot.accountId != expectedAccountId) {
      toast("日程数据尚未准备完成，请稍后重试")
      return
    }
    when (snapshot.status) {
      ScheduleRepositoryStatus.Loading -> {
        toast("日程数据正在加载，请稍后重试")
        return
      }
      is ScheduleRepositoryStatus.Corrupted -> {
        toast("当前日程数据不可用，暂时无法导出")
        return
      }
      is ScheduleRepositoryStatus.Ready,
      is ScheduleRepositoryStatus.Recovered,
      is ScheduleRepositoryStatus.Unavailable -> Unit
    }
    isExportingIcs = true
    val now = Clock.System.now()
    val document = try {
      ScheduleIcsExporter.export(snapshot, now)
    } catch (_: Throwable) {
      isExportingIcs = false
      toast("ICS 文件生成失败，请重试")
      return
    }
    if (document.scheduleCount == 0) {
      isExportingIcs = false
      toast("当前没有可导出的已排期日程")
      return
    }
    pendingIcsDocument = document
    icsFileSaveLauncher.launch(
      fileName = ScheduleIcsExporter.suggestedFileName(now, TimeZone.currentSystemDefault()),
      content = document.content,
    )
  }

  /** 使用发起操作时冻结的账号会话清空服务端与本地数据；切号后的响应不会误清新账号。 */
  fun clearCurrentAccountSchedules() {
    val expectedSession = accountService.session.value
    val expectedAccountId = expectedSession.accountId
    if (expectedAccountId == null) {
      toast("当前没有可清空的登录账号")
      return
    }
    isClearing = true
    clearResultMessage = null
    coroutineScope.launch {
      var remoteCleared = false
      try {
        api.clearAllSchedules(expectedSession).throwApiExceptionIfFail()
        remoteCleared = true
        if (accountService.session.value == expectedSession) {
          repository.clearLocalAccountData(expectedAccountId)
          saveScheduleTodoPinnedIds(AccountSettings.get(expectedAccountId), emptyList())
          clearResultMessage = "当前账号的服务端与本地日程已清空"
          toast("当前账号全部日程已清空")
        }
      } catch (throwable: CancellationException) {
        throw throwable
      } catch (throwable: Throwable) {
        if (accountService.session.value == expectedSession) {
          clearResultMessage = if (remoteCleared) {
            "服务端已清空，但本地清理失败：${throwable.message ?: "未知错误"}"
          } else {
            "清空失败：${throwable.message ?: "未知错误"}"
          }
        }
      } finally {
        if (accountService.session.value == expectedSession) isClearing = false
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bottomBg)
      .statusBarsPadding()
      .navigationBarsPadding(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .padding(top = 13.dp, start = 16.dp, end = 16.dp),
    ) {
      Box(
        modifier = Modifier
          .width(22.dp)
          .height(37.dp)
          .align(Alignment.CenterStart)
          .clickableNoIndicator(onClick = onBack),
        contentAlignment = Alignment.CenterStart,
      ) {
        Icon(
          painter = painterResource(ConfigRes.configIcBack()),
          contentDescription = "返回",
          tint = colors.tvLv1,
          modifier = Modifier.width(9.dp).height(19.dp),
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
      ScheduleSettingActionRow(
        title = "导出 ICS 文件",
        summary = when {
          !icsFileSaveLauncher.isSupported -> "当前平台暂不支持"
          isExportingIcs -> "正在生成并等待保存…"
          else -> "导出有日期的日程，包含重复规则、提醒和单次调整"
        },
        enabled = icsFileSaveLauncher.isSupported && !isExportingIcs,
        onClick = ::exportCurrentSnapshotToIcs,
      )
      if (isDebug()) {
        ScheduleSettingActionRow(
          title = "清空当前账号全部日程",
          summary = if (isClearing) "正在清空服务端与本地数据…" else "清空后将不可恢复!!!",
          enabled = !isClearing,
          danger = true,
          onClick = {
            clearConfirmationStep = 1
            showClearConfirmation.value = true
          },
        )
        clearResultMessage?.let { message ->
          Text(
            text = message,
            color = if (message.startsWith("清空失败")) MaterialTheme.colors.error else colors.tvLv2,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
          )
        }
      }
    }
  }

  if (isDebug()) {
    ChooseDialogCompose(
      showState = showClearConfirmation,
      positiveBtnText = if (clearConfirmationStep < 3) "继续" else "确认清空",
      negativeBtnText = "取消",
      onDismissRequest = {
        showClearConfirmation.value = false
        clearConfirmationStep = 1
      },
      onClickPositiveBtn = {
        if (clearConfirmationStep < 3) {
          clearConfirmationStep += 1
        } else {
          showClearConfirmation.value = false
          clearConfirmationStep = 1
          clearCurrentAccountSchedules()
        }
      },
      onClickNegativeBtn = {
        showClearConfirmation.value = false
        clearConfirmationStep = 1
      },
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "第 $clearConfirmationStep 次确认",
          color = colors.tvLv1,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
          text = when (clearConfirmationStep) {
            1 -> "这会删除当前登录账号在服务端保存的全部日程、分组和单次修改。"
            2 -> "当前设备保存的日程、临时提交和失败记录也会一起清除，操作后无法恢复。"
            else -> "最后确认：你确定要清空当前账号的全部日程吗？"
          },
          color = colors.tvLv2,
          fontSize = 15.sp,
          textAlign = TextAlign.Center,
        )
      }
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
          danger -> MaterialTheme.colors.error
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
