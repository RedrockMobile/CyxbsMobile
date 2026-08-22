package com.cyxbs.pages.schedule.ui.feed

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoAccentColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoCalendarLinkSelectedColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoDueSoonColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoInfoContainerColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoOnAccentColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoOverdueColor
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoPendingIndicatorColor
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_feed_empty_notify
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_feed_loading
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_feed_title
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_feed_notice
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_feed_urgent_arrow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 邮子清单 feed 卡片
 *
 * 主页摘要遵循当前邮子清单设计：
 * - 顶部标题「邮子清单」
 * - 下方展示最多三张未完成卡片；临期/超期横条由 [ScheduleUrgentBanner] 在整个 Feed 区域之前独立绘制
 * - 无数据时显示“今天好好休息一下吧”，加载期间显示查询提示
 *
 * 这是纯被动 UI：数据来自 [ScheduleFeedUiState]，导航与精确实例完成命令由 ViewModel 回调注入；
 * 组件本身不观察仓库，也不再依赖旧 ScheduleService。
 *
 * @param onCardClick 点击整张卡片（跳邮子清单主页）
 * @param onItemClick 点击标题后跳到该系列/实例详情，参数包含稳定的系列 ID 与 recurrence ID。
 * @param onItemCheck 勾选动画结束后完成精确实例，参数 identity 与 [onItemClick] 一致。
 * @param onTogglePin 左滑后切换系列的端上置顶状态，不会发起网络请求。
 * @param onDelete 左滑后删除精确事项；重复实例与普通事项的范围由 ViewModel 路由。
 */
@Composable
fun ScheduleFeed(
  state: ScheduleFeedUiState,
  onCardClick: () -> Unit,
  onItemClick: (ScheduleId, RecurrenceId?) -> Unit,
  onItemCheck: (ScheduleId, RecurrenceId?) -> Unit,
  onTogglePin: (ScheduleId) -> Unit,
  onDelete: (ScheduleId, RecurrenceId?) -> Unit,
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
      text = stringResource(Res.string.schedule_feed_title),
      color = colors.tvLv2,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 14.dp, top = 15.dp),
    )
    Spacer(modifier = Modifier.height(11.dp))
    when (state) {
      ScheduleFeedUiState.Loading -> ScheduleFeedHint(stringResource(Res.string.schedule_feed_loading))
      ScheduleFeedUiState.Empty -> ScheduleFeedHint(stringResource(Res.string.schedule_feed_empty_notify))
      is ScheduleFeedUiState.Data -> Column(modifier = Modifier.fillMaxWidth()) {
        state.items.forEach { item ->
          ScheduleFeedItem(
            item = item,
            onItemClick = onItemClick,
            onItemCheck = onItemCheck,
            onTogglePin = onTogglePin,
            onDelete = onDelete,
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(15.dp))
  }
}

/**
 * 主页专用的临期/超期提醒横条，由发现页放在整个 Feed 容器上方。
 *
 * 数量来自完整未完成集合，不受 Feed 最多三张卡片的展示上限影响；点击横条或右侧箭头均进入邮子清单。
 */
@Composable
fun ScheduleUrgentBanner(
  count: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val contentColor = if (MaterialTheme.colors.isLight) ScheduleTodoOnAccentColor else colors.tvLv1
  Surface(
    color = ScheduleTodoAccentColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 26.dp, bottomEnd = 26.dp),
    modifier = modifier
      .fillMaxWidth()
      .height(52.dp)
      .clickableSingle { onClick() },
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        // Figma 中 45dp 箭头图层右边距为 4dp；图层自身已包含约 9dp 的透明留白。
        .padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = buildAnnotatedString {
          append("你有 ")
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(count.toString()) }
          append(" 项待办即将到期或已超期")
        },
        color = contentColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.42.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Image(
        painter = painterResource(Res.drawable.schedule_ic_feed_urgent_arrow),
        contentDescription = null,
        modifier = Modifier.size(45.dp),
      )
    }
  }
}

@Composable
private fun ScheduleFeedHint(text: String) {
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
private fun ScheduleFeedItem(
  item: ScheduleFeedItemUi,
  onItemClick: (ScheduleId, RecurrenceId?) -> Unit,
  onItemCheck: (ScheduleId, RecurrenceId?) -> Unit,
  onTogglePin: (ScheduleId) -> Unit,
  onDelete: (ScheduleId, RecurrenceId?) -> Unit,
) {
  val colors = LocalAppColors.current
  // 本地完成态：点击勾选圈后立即置灰（对齐旧版点击瞬间变色），动画结束再触发 onItemCheck
  var checked by remember(item.id, item.recurrenceId) { mutableStateOf(false) }
  // “关联课表”真实链路尚未开放；当前只保留与设计稿一致的页面会话内选中反馈。
  var calendarLinked by remember(item.id, item.recurrenceId) { mutableStateOf(false) }
  val actionWidth = 110.dp
  val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
  var dragOffsetPx by remember(item.id, item.recurrenceId) { mutableFloatStateOf(0f) }
  var settleAnimation by remember(item.id, item.recurrenceId) { mutableStateOf<Job?>(null) }
  val coroutineScope = rememberCoroutineScope()

  /** 将拖动位置平滑吸附到收起或完全展开位置；新手势会取消尚未结束的旧动画。 */
  fun settleSwipe(targetOffsetPx: Float) {
    settleAnimation?.cancel()
    settleAnimation = coroutineScope.launch {
      animate(
        initialValue = dragOffsetPx,
        targetValue = targetOffsetPx,
        animationSpec = tween(durationMillis = 180),
      ) { value, _ -> dragOffsetPx = value }
    }
  }

  val titleColor = when {
    checked -> colors.tvLv3.copy(alpha = 0.45f)
    item.isOverTime -> ScheduleTodoOverdueColor
    item.isDueSoon -> ScheduleTodoDueSoonColor
    else -> colors.tvLv2
  }
  // Figma 只用标题强调临期/超期；时间和提醒始终保持统一灰蓝色，避免整行被警示色吞没。
  val timeColor = if (checked) colors.tvLv3.copy(alpha = 0.45f) else colors.tvLv4.copy(alpha = 0.52f)
  // 设计稿只用文字和时间表达临期/超期；未完成圆圈始终保持中性灰色，不能继承红黄状态色。
  val circleUncheckedColor = if (MaterialTheme.colors.isLight) {
    ScheduleTodoPendingIndicatorColor
  } else {
    colors.tvLv3.copy(alpha = 0.46f)
  }

  // Feed 事项是同一容器中的列表行，没有详情页那种独立圆角卡片底色。
  val cardColor = colors.middleBg
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  ) {
    Row(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .width(actionWidth)
        .height(42.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ScheduleFeedSwipeAction(
        text = if (item.isPinned) "取消" else "置顶",
        backgroundColor = ScheduleTodoAccentColor,
        modifier = Modifier.weight(1f),
        onClick = {
          settleSwipe(0f)
          onTogglePin(item.id)
        },
      )
      ScheduleFeedSwipeAction(
        text = "删除",
        backgroundColor = ScheduleTodoOverdueColor,
        modifier = Modifier.weight(1f),
        onClick = {
          settleSwipe(0f)
          onDelete(item.id, item.recurrenceId)
        },
      )
    }

    Surface(
      color = cardColor,
      shape = RectangleShape,
      modifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
        .pointerInput(item.id, item.recurrenceId) {
          detectHorizontalDragGestures(
            onDragStart = {
              settleAnimation?.cancel()
              settleAnimation = null
            },
            onHorizontalDrag = { change, amount ->
              change.consume()
              dragOffsetPx = (dragOffsetPx + amount).coerceIn(-actionWidthPx, 0f)
            },
            onDragEnd = {
              settleSwipe(if (dragOffsetPx <= -actionWidthPx / 2f) -actionWidthPx else 0f)
            },
            onDragCancel = { settleSwipe(0f) },
          )
        }
        .clip(RectangleShape)
        .clickableNoIndicator {
          if (dragOffsetPx != 0f) settleSwipe(0f)
          else onItemClick(item.id, item.recurrenceId)
        },
    ) {
      Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
          Row(
            modifier = Modifier.padding(end = 74.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ScheduleCheckCircle(
              checked = checked,
              uncheckedColor = circleUncheckedColor,
              onClick = { checked = true },
              onAnimEnd = { onItemCheck(item.id, item.recurrenceId) },
              modifier = Modifier.padding(start = 15.dp),
            )
            Spacer(modifier = Modifier.width(13.dp))
            Text(
              text = item.title,
              color = titleColor,
              fontSize = 15.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          }
          if (item.timeText != null || item.reminderText != null) {
            Row(
              modifier = Modifier.padding(start = 45.dp, top = 4.dp, end = 74.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Image(
                painter = painterResource(Res.drawable.schedule_ic_feed_notice),
                contentDescription = null,
                modifier = Modifier.size(width = 11.dp, height = 13.dp),
              )
              Spacer(modifier = Modifier.width(7.dp))
              Text(
                text = listOfNotNull(item.timeText, item.reminderText).joinToString(separator = "  "),
                color = timeColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        Box(
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 28.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
              if (calendarLinked) ScheduleTodoCalendarLinkSelectedColor
              else if (MaterialTheme.colors.isLight) ScheduleTodoInfoContainerColor
              else colors.tvLv4.copy(alpha = 0.2f)
            )
            .clickableNoIndicator {
              calendarLinked = !calendarLinked
              toast(
                if (calendarLinked) "已标记关联到课表，功能将在后续开放"
                else "已取消关联标记"
              )
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(ConfigRes.configIcCalendarSync()),
            contentDescription = "关联到课表",
            tint = if (calendarLinked) ScheduleTodoOnAccentColor else Color.White,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

/** Feed 左滑后使用设计稿的等宽文字动作块；它与详情页的 28dp 图标按钮属于两套独立样式。 */
@Composable
private fun ScheduleFeedSwipeAction(
  text: String,
  backgroundColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    color = backgroundColor,
    contentColor = ScheduleTodoOnAccentColor,
    shape = RectangleShape,
    modifier = modifier.fillMaxSize().clickableNoIndicator(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = text,
        color = ScheduleTodoOnAccentColor,
        fontSize = 14.sp,
      )
    }
  }
}
