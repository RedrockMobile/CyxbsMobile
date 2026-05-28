package com.cyxbs.pages.course.find.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.find.bean.FindStuHistoryEntity
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_find_course_history_delete
import org.jetbrains.compose.resources.painterResource

/**
 * Idle 状态下的内容：历史记录区（固定 100dp、内部可滚动）+ "我的关联"卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdleContent(
  history: List<FindStuHistoryEntity>,
  linkState: ILinkService2.LinkStu,
  onHistoryClick: (FindStuHistoryEntity) -> Unit,
  onHistoryLongClick: (FindStuHistoryEntity) -> Unit,
  onHistoryDelete: (FindStuHistoryEntity) -> Unit,
  onLinkCardClick: (stuNum: String) -> Unit,
  onLinkCardDelete: () -> Unit,
) {
  Column(modifier = Modifier.padding(horizontal = 18.dp)) {
    Text(
      text = "历史记录",
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold,
      color = LocalAppColors.current.tvLv1,
    )
    // 历史记录区固定 100dp 高度（对齐老 XML），内部 FlowRow 可纵向滚动；
    // 空记录也保留高度，让关联卡片位置稳定
    Box(
      modifier = Modifier
        .padding(top = 8.dp)
        .fillMaxWidth()
        .height(100.dp),
    ) {
      if (history.isEmpty()) {
        Text(
          text = "暂无历史记录",
          fontSize = 13.sp,
          color = LocalAppColors.current.tvLv2.copy(alpha = 0.5f),
          modifier = Modifier.padding(top = 8.dp),
        )
      } else {
        FlowRow(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          history.forEach { entity ->
            HistoryChip(
              entity = entity,
              onClick = { onHistoryClick(entity) },
              onLongClick = { onHistoryLongClick(entity) },
              onDelete = { onHistoryDelete(entity) },
            )
          }
        }
      }
    }
    Spacer(modifier = Modifier.size(21.dp))
    LinkCard(
      linkState = linkState,
      onClick = onLinkCardClick,
      onDelete = onLinkCardDelete,
    )
  }
}

@Composable
private fun HistoryChip(
  entity: FindStuHistoryEntity,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onDelete: () -> Unit,
) {
  Box(
    modifier = Modifier
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(SearchBgLight.dark(SearchBgDark))
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
        )
        .height(34.dp)
        .padding(horizontal = 13.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = entity.name.ifEmpty { entity.stuNum },
        color = LocalAppColors.current.tvLv2,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Image(
      painter = painterResource(Res.drawable.course_ic_find_course_history_delete),
      contentDescription = "删除",
      modifier = Modifier.align(Alignment.TopEnd)
        .size(12.dp)
        .clickable(onClick = onDelete),
    )
  }
}
