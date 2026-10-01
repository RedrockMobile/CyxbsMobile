package com.cyxbs.pages.mine.setting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SETTING
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.utils.judge.RedrockNetwork
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.login.api.LoginNavArgument
import cyxbsmobile.cyxbs_pages.mine.generated.resources.Res
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_arrow_right
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

/** 设置页路由参数。 */
@Serializable
data object SettingNavArgument : AppNavArgument

/** Android 与 iOS 共用的 CMP 设置页。 */
@AppNav(route = NAV_SETTING)
class SettingNavEntry : AppNavEntry<SettingNavArgument>() {

  override fun isNeedLogin(argument: SettingNavArgument): Boolean = true

  @Composable
  override fun Content(argument: SettingNavArgument) {
    SettingPage(argument)
  }
}

/**
 * 设置主页面。
 *
 * 只渲染 [SettingPlatform.settingItems] 提供的条目；平台决定能力、顺序和对应动作。
 */
@Composable
private fun SettingPage(argument: SettingNavArgument) {
  val platform = remember { SettingPlatform::class.implOrNull() }
  val loginDialogState = rememberLoginDialogState()
  val coroutineScope = rememberCoroutineScope()
  var checkingLogout by remember { mutableStateOf(false) }
  val showMaxWeekDialog = remember { mutableStateOf(false) }
  val showLogoutDialog = remember { mutableStateOf(false) }
  val showLogoutWarningDialog = remember { mutableStateOf(false) }
  var maxWeekText by remember { mutableStateOf((platform?.courseMaxWeek ?: 22).toString()) }
  // 条目定义和平台能力在页面生命周期内不变；回调操作的是已 remember 的页面状态。
  val settingItems = remember(platform) {
    platform?.settingItems(
      SettingItemActions(
        showCourseMaxWeekDialog = {
          maxWeekText = platform.courseMaxWeek.toString()
          showMaxWeekDialog.value = true
        },
      )
    ).orEmpty()
  }

  Column(
    modifier = Modifier.fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .statusBarsPadding(),
  ) {
    SettingTopBar(argument)
    LazyColumn(
      modifier = Modifier.fillMaxSize().navigationBarsPadding(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 32.dp),
    ) {
      items(settingItems, key = SettingItem::key) { settingItem ->
        when (settingItem) {
          is SettingItem.Switch -> {
            var checked by remember(settingItem.key) { mutableStateOf(settingItem.readChecked()) }
            var changing by remember(settingItem.key) { mutableStateOf(false) }
            val itemScope = rememberCoroutineScope()
            val onCheckedChange = settingItem.rememberOnCheckedChange()
            SettingSwitchRow(
              text = settingItem.title,
              checked = checked,
              enabled = !changing,
              onCheckedChange = { value ->
                if (!changing) {
                  changing = true
                  itemScope.launch {
                    try {
                      // 授权及保存完全由条目处理；只在完成后读取实际状态。
                      onCheckedChange(value)
                    } catch (throwable: Throwable) {
                      if (throwable is CancellationException) throw throwable
                      toast("设置失败，请重试")
                    } finally {
                      checked = settingItem.readChecked()
                      changing = false
                    }
                  }
                }
              },
            )
          }
          is SettingItem.Action -> {
            val onClick = settingItem.rememberOnClick()
            SettingArrowRow(text = settingItem.title) {
              if (settingItem.requiresLogin) {
                loginDialogState.doIfLogin(function = settingItem.title) {
                  onClick()
                }
              } else {
                onClick()
              }
            }
          }
        }
      }
      item {
        Spacer(Modifier.height(110.dp))
        Button(
          enabled = !checkingLogout,
          onClick = {
            if (checkingLogout) return@Button
            checkingLogout = true
            toast("退出登录会先检查请求是否正常，请稍后~")
            coroutineScope.launch {
              try {
                if (RedrockNetwork.tryPingNetWork()?.isSuccess == true) {
                  showLogoutDialog.value = true
                } else {
                  showLogoutWarningDialog.value = true
                }
              } finally {
                checkingLogout = false
              }
            }
          },
          modifier = Modifier.padding(horizontal = 72.dp).fillMaxWidth().height(42.dp),
          colors = ButtonDefaults.buttonColors(
            backgroundColor = LocalAppColors.current.positive,
            contentColor = Color.White,
          ),
          shape = RoundedCornerShape(21.dp),
          elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
        ) {
          Text(if (checkingLogout) "检查中…" else "退出登录", fontSize = 17.sp)
        }
      }
    }
  }

  CourseMaxWeekDialog(
    showState = showMaxWeekDialog,
    value = maxWeekText,
    onValueChange = { maxWeekText = it.filter(Char::isDigit).take(2) },
    onConfirm = {
      when (val maxWeek = maxWeekText.toIntOrNull()) {
        null -> toast("请输入完整")
        in 20..30 -> {
          platform?.setCourseMaxWeek(maxWeek)
          showMaxWeekDialog.value = false
          toast("设置成功，请重启应用更新！")
        }
        else -> toast("范围错误")
      }
    },
  )
  LogoutDialog(
    showState = showLogoutDialog,
    content = "是否退出登录？",
    onConfirm = {
      showLogoutDialog.value = false
      performLogout(platform)
    },
  )
  LogoutDialog(
    showState = showLogoutWarningDialog,
    content = "因服务器或当前手机网络原因，检测到掌邮核心服务暂不可用，退出后可能无法正常登录，是否确认退出？",
    positiveText = "仍要退出",
    onConfirm = {
      showLogoutWarningDialog.value = false
      performLogout(platform)
    },
  )
}

/** 绘制设置页标题栏。 */
@Composable
private fun SettingTopBar(argument: SettingNavArgument) {
  TopAppBar(backgroundColor = Color.Transparent, elevation = 0.dp) {
    Box(modifier = Modifier.fillMaxSize()) {
      Image(
        painter = painterResource(ConfigRes.configIcBack()),
        contentDescription = "返回",
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(16.dp)
          .clickableSingle { argument.popBackStack() },
      )
      Text(
        text = "设置",
        color = LocalAppColors.current.tvLv1,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

/** 带开关的设置项，整行点击与开关点击保持相同行为。 */
@Composable
private fun SettingSwitchRow(
  text: String,
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
      text = text,
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

/** 普通设置项；箭头右边缘与上方开关的可见轨道右边缘对齐。 */
@Composable
private fun SettingArrowRow(text: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
      .clickableNoIndicator(onClick = onClick)
      // Material Switch 的可见轨道比其触控区域右缘内缩约 6dp，箭头需要同样内缩。
      .padding(start = 16.dp, end = 22.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text,
      color = LocalAppColors.current.tvLv2,
      fontSize = 15.sp,
      modifier = Modifier.weight(1F),
    )
    Image(
      painter = painterResource(Res.drawable.mine_ic_arrow_right),
      contentDescription = null,
      modifier = Modifier.size(width = 8.dp, height = 14.dp),
    )
  }
}

/** CMP 版课表最大周数输入弹窗，继续沿用 20..30 的业务约束。 */
@Composable
private fun CourseMaxWeekDialog(
  showState: androidx.compose.runtime.MutableState<Boolean>,
  value: String,
  onValueChange: (String) -> Unit,
  onConfirm: () -> Unit,
) {
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = "确定",
    negativeBtnText = "取消",
    onClickPositiveBtn = onConfirm,
    onClickNegativeBtn = { showState.value = false },
  ) {
    Text(
      text = "课表最大周数",
      color = LocalAppColors.current.tvLv1,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
      textAlign = TextAlign.Center,
    )
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      textStyle = TextStyle(
        color = LocalAppColors.current.tvLv1,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
      ),
      cursorBrush = SolidColor(LocalAppColors.current.positive),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 24.dp)
        .border(1.dp, LocalAppColors.current.tvLv3, RoundedCornerShape(10.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp),
      decorationBox = { innerTextField ->
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          if (value.isEmpty()) {
            Text("范围：20–30", color = LocalAppColors.current.tvLv3, fontSize = 15.sp)
          }
          innerTextField()
        }
      },
    )
  }
}

/** 退出登录确认弹窗；网络正常与异常仅复用结构，提示内容由调用方决定。 */
@Composable
private fun LogoutDialog(
  showState: androidx.compose.runtime.MutableState<Boolean>,
  content: String,
  positiveText: String = "确定",
  onConfirm: () -> Unit,
) {
  ChooseDialogCompose(
    showState = showState,
    positiveBtnText = positiveText,
    negativeBtnText = "取消",
    onClickPositiveBtn = onConfirm,
    onClickNegativeBtn = { showState.value = false },
  ) {
    DialogMessage(title = "温馨提示", content = content)
  }
}

/** 通用设置弹窗文案区。 */
@Composable
internal fun DialogMessage(title: String, content: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = title,
      color = LocalAppColors.current.tvLv1,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = content,
      color = LocalAppColors.current.tvLv2,
      fontSize = 14.sp,
      lineHeight = 21.sp,
      textAlign = TextAlign.Center,
    )
  }
}

/** 清理平台缓存、注销账号并清空主导航栈回到登录页。 */
private fun performLogout(platform: SettingPlatform?) {
  platform?.clearPlatformDataBeforeLogout()
  IAccountEditService::class.impl().onLogout()
  LoginNavArgument.navigate(HomeNavArgument(), clearStack = true)
}
