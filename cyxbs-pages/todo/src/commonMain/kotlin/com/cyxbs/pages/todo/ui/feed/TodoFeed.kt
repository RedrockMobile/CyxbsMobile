package com.cyxbs.pages.todo.ui.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableSingle
import cyxbsmobile.cyxbs_pages.todo.generated.resources.Res
import cyxbsmobile.cyxbs_pages.todo.generated.resources.todo_feed_empty_notify
import cyxbsmobile.cyxbs_pages.todo.generated.resources.todo_feed_loading
import cyxbsmobile.cyxbs_pages.todo.generated.resources.todo_feed_title
import cyxbsmobile.cyxbs_pages.todo.generated.resources.todo_ic_feed_notice
import cyxbsmobile.cyxbs_pages.todo.generated.resources.todo_ic_feed_overtime_notice
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 邮子清单 feed 卡片
 *
 * 整体布局与旧版 TodoFeedFragment + todo_fragment_todo_feed.xml + todo_rv_item_feed.xml 一致：
 * - 顶部标题「邮子清单」
 * - 下方最多 3 条未完成待办；无数据时显示「查询中…」或「还没有待做事项哦~快去添加吧！」
 * - 底部一条淡分割线
 *
 * 这是纯被动 UI：数据与点击行为（跳转、勾选后删除/更新）由 androidMain 的 TodoService 注入，
 * 详见 [com.cyxbs.pages.todo.api.ITodoService.TodoFeed]。
 *
 * @param onCardClick 点击整张卡片（跳邮子清单主页）
 * @param onItemClick 点击某条待办标题（跳详情页），参数为 [TodoFeedItemUi.id]
 * @param onItemCheck 勾选某条待办完成（动画结束后触发），参数为 [TodoFeedItemUi.id]
 */
@Composable
fun TodoFeed(
  state: TodoFeedUiState,
  onCardClick: () -> Unit,
  onItemClick: (Long) -> Unit,
  onItemCheck: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickableSingle { onCardClick() }
      .padding(top = 9.dp),
  ) {
    Text(
      text = stringResource(Res.string.todo_feed_title),
      color = colors.tvLv2,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 14.dp, top = 15.dp),
    )
    // 标题 top 15dp + 标题高度后，列表区大致落在卡片 top 50dp 处
    Spacer(modifier = Modifier.height(11.dp))
    when (state) {
      TodoFeedUiState.Loading -> TodoFeedHint(stringResource(Res.string.todo_feed_loading))
      TodoFeedUiState.Empty -> TodoFeedHint(stringResource(Res.string.todo_feed_empty_notify))
      is TodoFeedUiState.Data -> Column(modifier = Modifier.fillMaxWidth()) {
        state.items.forEach { item ->
          TodoFeedItem(item = item, onItemClick = onItemClick, onItemCheck = onItemCheck)
        }
      }
    }
    Spacer(modifier = Modifier.height(15.dp))
  }
}

@Composable
private fun TodoFeedHint(text: String) {
  val colors = LocalAppColors.current
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 39.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = colors.tvLv1.copy(alpha = 0.6f),
      fontSize = 15.sp,
    )
  }
}

@Composable
private fun TodoFeedItem(
  item: TodoFeedItemUi,
  onItemClick: (Long) -> Unit,
  onItemCheck: (Long) -> Unit,
) {
  val colors = LocalAppColors.current
  // 本地完成态：点击勾选圈后立即置灰（对齐旧版点击瞬间变色），动画结束再触发 onItemCheck
  var checked by remember(item.id) { mutableStateOf(false) }

  val titleColor = when {
    checked -> Color(0xFFB9C2CE) // todo_check_item_color
    item.isOverTime -> Color(0xFFFF6262) // todo_text_overtime_color
    else -> colors.tvLv2
  }
  val timeColor = when {
    checked -> Color(0xFFB9C2CE)
    item.isOverTime -> Color(0xFFA19EBB) // todo_textTime_overtime_color
    else -> colors.tvLv4.copy(alpha = 0.52f) // todo_item_nf_time_color #862A4E84
  }
  // 勾选圈未选色：超时 todo_check_overtime_color，否则 todo_inner_check_eclipse_color #7515315B
  val circleUncheckedColor =
    if (item.isOverTime) Color(0xFFFFB7B7) else colors.tvLv3.copy(alpha = 0.46f)

  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      TodoCheckCircle(
        checked = checked,
        uncheckedColor = circleUncheckedColor,
        onClick = { checked = true },
        onAnimEnd = { onItemCheck(item.id) },
        modifier = Modifier.padding(start = 15.dp),
      )
      // 旧 XML 标题 marginStart 45dp = 圈 start 15dp + 圈 17dp + 13dp 间隔
      Spacer(modifier = Modifier.width(13.dp))
      Text(
        text = item.title,
        color = titleColor,
        fontSize = 15.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .weight(1f)
          .padding(end = 15.dp)
          .clickableSingle { onItemClick(item.id) },
      )
    }
    if (item.timeText != null) {
      Row(
        modifier = Modifier.padding(start = 45.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Image(
          painter = painterResource(
            if (item.isOverTime) Res.drawable.todo_ic_feed_overtime_notice
            else Res.drawable.todo_ic_feed_notice,
          ),
          contentDescription = null,
          modifier = Modifier.size(width = 11.dp, height = 13.dp),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
          text = item.timeText,
          color = timeColor,
          fontSize = 11.sp,
        )
      }
    }
  }
}
