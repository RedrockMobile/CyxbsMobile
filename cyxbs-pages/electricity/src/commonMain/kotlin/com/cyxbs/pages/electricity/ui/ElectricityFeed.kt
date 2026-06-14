package com.cyxbs.pages.electricity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.pages.electricity.bean.ElecInf
import com.cyxbs.pages.electricity.viewmodel.ElectricityFeedUiState
import com.cyxbs.pages.electricity.viewmodel.ElectricityFeedViewModel
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.Res
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_feed_subtitle_record
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_inquire_string
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_label_fee
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_label_kilowatt
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_no_data
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_searching
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_unbind
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_unit_kilowatt
import cyxbsmobile.cyxbs_pages.electricity.generated.resources.electricity_unit_yuan
import org.jetbrains.compose.resources.stringResource

/**
 * 电费查询 feed 卡片
 *
 * 整体布局与旧版 ElectricityFeedFragment + electricity_fragment_base_feed.xml +
 * electricity_discover_feed.xml 一致：
 * - 顶部一行：标题（左）+ 抄表时间（右）
 * - 下方两列数据，无数据时显示「查询中...」「先选择寝室吧~」「暂无该宿舍电费数据」之一
 *
 * 点击整张卡片：登录态下弹出 [ElectricityFeedSettingDialog] 选择寝室。
 */
@Composable
fun ElectricityFeed(modifier: Modifier = Modifier) {
  val viewModel = viewModel { ElectricityFeedViewModel() }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val accountState by IAccountService::class.impl().state.collectAsStateWithLifecycle()

  val titleText = stringResource(Res.string.electricity_inquire_string)
  var showDialog by remember { mutableStateOf(false) }

  // 登录态变化时按旧 onResume 的逻辑刷新：登录后按本地缓存拉取，账号失效则清空回初始态
  LaunchedEffect(accountState) {
    when (accountState) {
      is AccountState.Login -> viewModel.refresh()
      else -> viewModel.clear()
    }
  }

  val loginDialogState = rememberLoginDialogState()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickableSingle {
        loginDialogState.doIfLogin(function = titleText) {
          showDialog = true
        }
      },
  ) {
    val recordTime = (uiState as? ElectricityFeedUiState.Data)?.elecInf?.recordTime
    val subtitle = if (!recordTime.isNullOrEmpty()) {
      stringResource(Res.string.electricity_feed_subtitle_record, recordTime)
    } else {
      ""
    }
    ElectricityFeedHeader(title = titleText, subtitle = subtitle)
    Spacer(modifier = Modifier.height(16.dp))
    when (val state = uiState) {
      is ElectricityFeedUiState.Data -> ElectricityDataRow(state.elecInf)
      ElectricityFeedUiState.Loading -> ElectricityEmpty(stringResource(Res.string.electricity_searching))
      ElectricityFeedUiState.Unbind -> ElectricityEmpty(stringResource(Res.string.electricity_unbind))
      ElectricityFeedUiState.NoData -> ElectricityEmpty(stringResource(Res.string.electricity_no_data))
    }
  }

  if (showDialog) {
    ElectricityFeedSettingDialog(
      onDismiss = { showDialog = false },
      onConfirm = { id, room ->
        viewModel.getCharge(id, room)
        showDialog = false
      },
    )
  }
}

@Composable
private fun ElectricityFeedHeader(title: String, subtitle: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      color = LocalAppColors.current.tvLv2,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 14.dp),
    )
    Spacer(modifier = Modifier.weight(1f))
    if (subtitle.isNotEmpty()) {
      Text(
        text = subtitle,
        color = LocalAppColors.current.tvLv2.copy(alpha = 0.54f),
        fontSize = 10.sp,
        modifier = Modifier.padding(end = 15.dp),
      )
    }
  }
}

@Composable
private fun ElectricityDataRow(elecInf: ElecInf) {
  val accent = LocalAppColors.current.tvLv4
  val secondary = LocalAppColors.current.tvLv2.copy(alpha = 0.6f)
  val feeValue = remember(elecInf) { sanitizeNumber(elecInf.getEleCost()) }
  val kilowattValue = remember(elecInf) { elecInf.elecSpend }
  val yuanUnit = stringResource(Res.string.electricity_unit_yuan)
  val kwUnit = stringResource(Res.string.electricity_unit_kilowatt)
  val feeText = remember(feeValue, yuanUnit) { buildValueAnnotated(feeValue, yuanUnit) }
  val kwText = remember(kilowattValue, kwUnit) { buildValueAnnotated(kilowattValue, kwUnit) }
  // 旧 XML 数字用 @font/impact，仅 Android 提供；其它平台为 null 时回退默认字体并加粗
  // todo 待后续把字体迁移到 commonMain 中，目前因为存在 xml 引用所以不进行迁移
  val numberFontFamily = remember { ConfigRes.impactFontFamily() }
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val width = this.maxWidth
    Row(
      modifier = Modifier.fillMaxWidth().padding(
        start = width * 0.12f,
        end = width * 0.12f,
        top = 17.dp,
        bottom = 23.dp,
      ),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      ElectricityDataColumn(
        valueText = feeText,
        color = accent,
        valueFontFamily = numberFontFamily,
        label = stringResource(Res.string.electricity_label_fee),
        labelColor = secondary,
      )
      ElectricityDataColumn(
        valueText = kwText,
        color = accent,
        valueFontFamily = numberFontFamily,
        label = stringResource(Res.string.electricity_label_kilowatt),
        labelColor = secondary,
      )
    }
  }
}

@Composable
private fun ElectricityDataColumn(
  valueText: AnnotatedString,
  color: Color,
  valueFontFamily: FontFamily?,
  label: String,
  labelColor: Color,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    // fontFamily 默认值是 null —— Compose 在 null 时会回退到平台默认字体，
    // 所以这里非 Android 平台传 null 即可，不需要再分支判断
    Text(
      text = valueText, color = color, fontFamily = valueFontFamily,
      style = TextStyle(fontWeight = if (valueFontFamily == null) FontWeight.Bold else null)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = label, color = labelColor, fontSize = 13.sp)
  }
}

private fun sanitizeNumber(value: String): String {
  val asDouble = value.toDoubleOrNull() ?: return value
  return if (asDouble < 0) "0.0" else asDouble.toString()
}

private fun buildValueAnnotated(value: String, unit: String): AnnotatedString =
  buildAnnotatedString {
    withStyle(SpanStyle(fontSize = 36.sp)) { append(value) }
    withStyle(SpanStyle(fontSize = 13.sp)) { append(unit) }
  }

@Composable
private fun ElectricityEmpty(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(106.dp)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = LocalAppColors.current.tvLv2.copy(alpha = 0.6f),
      fontSize = 15.sp,
    )
  }
}
