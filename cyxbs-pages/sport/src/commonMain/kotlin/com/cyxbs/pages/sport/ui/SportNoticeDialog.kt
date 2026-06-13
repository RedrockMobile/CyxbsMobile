package com.cyxbs.pages.sport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.pages.sport.model.SportNoticeRepository
import cyxbsmobile.cyxbs_pages.sport.generated.resources.Res
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_notice_confirm
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_notice_load_fail
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_notice_title
import org.jetbrains.compose.resources.stringResource

/**
 * 体育打卡信息说明弹窗
 *
 * 复刻旧 sport_dialog_feed.xml：标题 + 后端下发的 3 组「小标题 + 内容」+ 确认按钮。
 * 数据来自 [SportNoticeRepository]，拉取失败时显示统一兜底文案。
 */
@Composable
fun SportNoticeDialog(onDismiss: () -> Unit) {
  val result by SportNoticeRepository.noticeData.collectAsStateWithLifecycle()
  val colors = LocalAppColors.current
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(colors.whiteBlack)
        .padding(start = 23.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
    ) {
      Text(
        text = stringResource(Res.string.sport_notice_title),
        color = colors.tvLv2,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
      )
      val notices = result?.getOrNull()
      if (result?.isFailure == true) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = stringResource(Res.string.sport_notice_load_fail),
          color = colors.tvLv2,
          fontSize = 16.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      } else if (notices != null) {
        notices.take(3).forEach { item ->
          Spacer(modifier = Modifier.height(16.dp))
          Text(text = item.title, color = colors.tvLv2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = item.content, color = colors.tvLv2, fontSize = 14.sp)
        }
      }
      Spacer(modifier = Modifier.height(24.dp))
      SportNoticeConfirmButton(
        text = stringResource(Res.string.sport_notice_confirm),
        onClick = onDismiss,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }
  }
}

@Composable
private fun SportNoticeConfirmButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .width(129.dp)
      .height(34.dp)
      .clip(RoundedCornerShape(27.dp))
      .background(Color(0xFF4A44E4))
      .clickableSingle(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = text, color = Color.White, fontSize = 14.sp)
  }
}
