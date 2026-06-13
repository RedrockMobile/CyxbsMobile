package com.cyxbs.pages.sport.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.sport.model.SportDetailBean
import com.cyxbs.pages.sport.model.SportDetailRepository
import cyxbsmobile.cyxbs_pages.sport.generated.resources.Res
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_award
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_error
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_not_login
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_other_remain
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_run_remain
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_subtitle
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_times
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_feed_title
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_feed_tips
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 体育打卡 feed 卡片
 *
 * 整体布局与旧版 DiscoverSportFeedFragment + sport_fragment_discover_feed.xml 一致：
 * - 顶部一行：标题「体育打卡」+ 说明图标（左）+ 副标题「实际以智慧体育为准」（右）
 * - 三列「跑步剩余 / 其他剩余 / 奖励」，按状态展示：
 *   - 未登录：居中提示「登录后才能查看体育打卡哦」
 *   - 已登录但数据未到（加载中）：三列显示占位 X / Y / Z（对齐旧 XML 默认文案）
 *   - 有数据：显示真实剩余次数
 *   - 出错：居中提示「当前数据错误，正在努力修复中」
 *
 * 数据来自 [SportDetailRepository]（与体育打卡详情页共享，监听登录态自动刷新）。
 *
 * @param onJumpDetail 点击卡片跳转体育打卡详情页的行为。跳转依赖 Android Activity 路由，
 *   故由平台层（androidMain 的 SportServiceImpl）注入。
 */
@Composable
fun SportFeed(
  modifier: Modifier = Modifier,
  onJumpDetail: () -> Unit,
) {
  val result by SportDetailRepository.sportData.collectAsStateWithLifecycle()
  val accountState by IAccountService::class.impl().state.collectAsStateWithLifecycle()
  val isLogin = accountState is AccountState.Login
  val loginDialogState = rememberLoginDialogState()
  var showNoticeDialog by remember { mutableStateOf(false) }
  val loginFunction = stringResource(Res.string.sport_feed_title)

  // 请求失败时整卡不可点（对齐旧 showError 把点击置 null）
  val isError = result?.isFailure == true

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickableSingle(enabled = !isError) {
        // 未登录弹登录框，已登录跳详情页（跳转由平台注入）
        loginDialogState.doIfLogin(function = loginFunction) { onJumpDetail() }
      },
  ) {
    SportFeedHeader(onTipsClick = { showNoticeDialog = true })
    Spacer(modifier = Modifier.height(16.dp))
    val bean = result?.getOrNull()
    when {
      bean != null -> SportFeedDataRow(
        run = remainRun(bean).toString(),
        other = remainOther(bean).toString(),
        award = bean.award.toString(),
      )

      isError -> SportFeedHint(stringResource(Res.string.sport_feed_error))
      !isLogin -> SportFeedHint(stringResource(Res.string.sport_feed_not_login))
      // 已登录但数据尚未返回（加载中）：占位 X / Y / Z，对齐旧 XML 默认显示
      else -> SportFeedDataRow(run = "X", other = "Y", award = "Z")
    }
  }

  if (showNoticeDialog) {
    SportNoticeDialog(onDismiss = { showNoticeDialog = false })
  }
}

// 跑步剩余 = 所需 - 已跑，且 >= 0
private fun remainRun(bean: SportDetailBean): Int =
  (bean.runTotal - bean.runDone).coerceAtLeast(0)

// 其他剩余：跑步还有剩余时仅算其他项；跑步已达标后用总剩余兜底（对齐旧逻辑）
private fun remainOther(bean: SportDetailBean): Int =
  (
      if (bean.runTotal - bean.runDone > 0) {
        bean.otherTotal - bean.otherDone
      } else {
        (bean.runTotal + bean.otherTotal) - bean.otherDone - bean.runDone
      }
      ).coerceAtLeast(0)

@Composable
private fun SportFeedHeader(onTipsClick: () -> Unit) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 23.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(Res.string.sport_feed_title),
      color = colors.tvLv2,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 14.dp),
    )
    Image(
      painter = painterResource(Res.drawable.sport_ic_feed_tips),
      contentDescription = null,
      modifier = Modifier
        .padding(start = 10.dp)
        .clickableSingle(onClick = onTipsClick),
    )
    Spacer(modifier = Modifier.weight(1f))
    Text(
      text = stringResource(Res.string.sport_feed_subtitle),
      color = colors.tvLv2.copy(alpha = 0.54f),
      fontSize = 11.sp,
      modifier = Modifier.padding(end = 15.dp),
    )
  }
}

@Composable
private fun SportFeedDataRow(run: String, other: String, award: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 36.dp, end = 33.dp, top = 14.dp, bottom = 41.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    SportFeedColumn(valueText = run, label = stringResource(Res.string.sport_feed_run_remain))
    SportFeedColumn(valueText = other, label = stringResource(Res.string.sport_feed_other_remain))
    SportFeedColumn(valueText = award, label = stringResource(Res.string.sport_feed_award))
  }
}

@Composable
private fun SportFeedColumn(valueText: String, label: String) {
  // sport_feed_text_color / sport_text_color / sport_text_color_light（随主题切换）
  val numberColor = 0xFF2A4E84.dark(0xFFFFFFFF)
  val timesColor = 0xFF15315B.dark(0xFFFFFFFF)
  val labelColor = 0xFF697C9B.dark(0xFF606061)
  // 旧 XML 数字用 @font/impact，仅 Android 提供；其它平台为 null 时回退默认字体并加粗
  val numberFontFamily = remember { ConfigRes.impactFontFamily() }
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        text = valueText,
        color = numberColor,
        fontSize = 40.sp,
        fontFamily = numberFontFamily,
        style = TextStyle(fontWeight = if (numberFontFamily == null) FontWeight.Bold else null),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(Res.string.sport_feed_times),
        color = timesColor,
        fontSize = 14.sp,
        modifier = Modifier.padding(bottom = 6.dp),
      )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(text = label, color = labelColor, fontSize = 14.sp)
  }
}

@Composable
private fun SportFeedHint(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(106.dp)
      .padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = 0xFF70819C.dark(0x33F0F0F2L),
      fontSize = 15.sp,
    )
  }
}
