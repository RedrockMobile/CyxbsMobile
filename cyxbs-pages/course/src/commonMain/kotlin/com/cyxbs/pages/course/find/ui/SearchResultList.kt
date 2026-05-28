package com.cyxbs.pages.course.find.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.course.find.bean.FindStuBean
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_find_course_link_head_ing
import org.jetbrains.compose.resources.painterResource

/**
 * 搜索结果列表。点击整行 = 打开课表，长按整行 = 触发关联/取消关联确认。
 */
@Composable
internal fun SearchResultList(
  list: List<FindStuBean>,
  linkedStuNum: String?,
  onItemClick: (FindStuBean) -> Unit,
  onItemLongClick: (FindStuBean) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(items = list, key = { it.stuNum }) { bean ->
      StuResultRow(
        bean = bean,
        isLinked = linkedStuNum == bean.stuNum,
        onClick = { onItemClick(bean) },
        onLongClick = { onItemLongClick(bean) },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StuResultRow(
  bean: FindStuBean,
  isLinked: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.middleBg, RoundedCornerShape(12.dp))
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = bean.name,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = colors.tvLv1,
        )
        if (bean.stuNum.isNotEmpty()) {
          Text(
            text = "  ${bean.stuNum}",
            fontSize = 13.sp,
            color = colors.tvLv2.copy(alpha = 0.6f),
          )
        }
      }
      val subText = listOfNotNull(
        bean.major.takeIf { it.isNotBlank() },
        bean.depart.takeIf { it.isNotBlank() },
      ).joinToString("  ")
      if (subText.isNotEmpty()) {
        Text(
          text = subText,
          fontSize = 12.sp,
          color = colors.tvLv2.copy(alpha = 0.7f),
          modifier = Modifier.padding(top = 2.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    // 已关联时才显示头像作为身份标识；未关联无图标，关联通过长按整行触发
    if (isLinked) {
      Icon(
        painter = painterResource(Res.drawable.course_ic_find_course_link_head_ing),
        contentDescription = "已关联",
        tint = Color.Unspecified,
        modifier = Modifier.padding(start = 8.dp).size(19.dp),
      )
    }
  }
}
