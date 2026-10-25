package com.cyxbs.pages.schedule.ui.todo.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.domain.model.OccurrenceStatus
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoTime
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_todo_urgency_flag
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** 清单卡片展示标题、时间点/时间段和备注，不再显示“关联课表”图标。 */
@Composable
internal fun ScheduleTodoCard(
  modifier: Modifier = Modifier,
  item: ScheduleTodoItemUi,
  highlighted: Boolean,
  isPinned: Boolean,
  manageMode: Boolean,
  selected: Boolean,
  onSelect: () -> Unit,
  onLongPress: () -> Unit,
  onOpen: () -> Unit,
  onComplete: (() -> Unit)?,
  onTogglePin: (() -> Unit)?,
  isLinkedToCalendar: Boolean,
  onToggleCalendarLink: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalAppColors.current
  val completed = item.occurrence.status == OccurrenceStatus.COMPLETED
  // 调用方只有在该条目确实支持完成态切换时才传回调；避免重复系列兜底卡片显示无效圆圈。
  val canComplete = onComplete != null
  val pinIcon = ConfigRes.configIcPin()
  val deleteIcon = ConfigRes.configIcDelete()
  val restoreIcon = ConfigRes.configIcRestore()
  val hasUrgencyBadge = item.isOverdue || item.isDueSoon
  val reminderText = item.occurrence.reminder?.let { reminder ->
    formatScheduleTodoReminder(reminder.offsetMinutes)
  }
  val timeIcon = rememberIcAddtodoTime()
  val cardShape = RoundedCornerShape(16.dp)
  val hasLeadingSwipeAction = (completed && onComplete != null) || onTogglePin != null
  val actionWidth = if (hasLeadingSwipeAction) 110.dp else 62.dp
  val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
  var dragOffsetPx by remember(item.key) { mutableFloatStateOf(0f) }
  var settleAnimation by remember(item.key) { mutableStateOf<Job?>(null) }
  val cardCoroutineScope = rememberCoroutineScope()
  val isLightTheme = MaterialTheme.colors.isLight
  val swipeActionTint = scheduleTodoSwipeActionTintColor()
  val baseCardColor = if (isLightTheme) ScheduleTodoCardContainerColor else colors.middleBg
  val highlightAlpha = remember(item.key) { Animatable(0f) }
  LaunchedEffect(highlighted) {
    if (highlighted) {
      // 卡片本体保持不透明，仅让顶层色片快速淡入淡出，避免透出背后的侧滑操作区。
      highlightAlpha.snapTo(0f)
      highlightAlpha.animateTo(
        targetValue = if (isLightTheme) 0.12f else 0.20f,
        animationSpec = tween(durationMillis = 220),
      )
      highlightAlpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 460),
      )
    } else {
      highlightAlpha.snapTo(0f)
    }
  }

  /** 松手后把卡片平滑吸附到收起或完全展开位置；新拖动会取消尚未结束的旧动画。 */
  fun settleSwipe(targetOffsetPx: Float) {
    settleAnimation?.cancel()
    settleAnimation = cardCoroutineScope.launch {
      animate(
        initialValue = dragOffsetPx,
        targetValue = targetOffsetPx,
        animationSpec = tween(durationMillis = 180),
      ) { value, _ ->
        dragOffsetPx = value
      }
    }
  }

  LaunchedEffect(manageMode) {
    if (manageMode) settleSwipe(0f)
  }

  Box(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .width(actionWidth)
        .height(28.dp)
        .padding(end = 13.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (hasLeadingSwipeAction) {
        ScheduleTodoSwipeAction(
          icon = if (completed) restoreIcon else pinIcon,
          contentDescription = when {
            completed -> "恢复未完成"
            isPinned -> "取消置顶"
            else -> "置顶"
          },
          backgroundColor = if (completed) {
            ScheduleTodoRestoreActionBackgroundColor
          } else {
            ScheduleTodoPinActionBackgroundColor
          },
          tint = swipeActionTint,
          showCancelMark = isPinned && !completed,
          onClick = {
            settleSwipe(0f)
            if (completed) onComplete?.invoke() else onTogglePin?.invoke()
          },
        )
      }
      ScheduleTodoSwipeAction(
        icon = deleteIcon,
        contentDescription = "删除",
        backgroundColor = ScheduleTodoDeleteActionBackgroundColor,
        tint = swipeActionTint,
        onClick = {
          settleSwipe(0f)
          onDelete()
        },
      )
    }

    Surface(
      color = baseCardColor,
      shape = cardShape,
      modifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
        .pointerInput(item.key, manageMode) {
          if (!manageMode) {
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
                settleSwipe(
                  if (dragOffsetPx <= -actionWidthPx / 2f) -actionWidthPx else 0f
                )
              },
              onDragCancel = { settleSwipe(0f) },
            )
          }
        }
        .clip(cardShape)
        .combinedClickable(
          onClick = {
            when {
              manageMode -> onSelect()
              dragOffsetPx != 0f -> settleSwipe(0f)
              else -> onOpen()
            }
          },
          onLongClick = {
            if (!manageMode) {
              settleSwipe(0f)
              onLongPress()
            }
          },
        ),
    ) {
      Box {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (manageMode || canComplete) {
              Box(
                modifier = Modifier
                  // 待办的圆圈与批量方框保持等宽；普通日程在非批量状态下不保留空占位。
                  .size(24.dp)
                  .clickableNoIndicator {
                    if (manageMode) onSelect() else onComplete?.invoke()
                  },
                contentAlignment = Alignment.Center,
              ) {
                if (manageMode && selected) {
                  ScheduleTodoCheckedBox()
                } else {
                  Icon(
                    imageVector = when {
                      manageMode -> Icons.Outlined.CheckBoxOutlineBlank
                      completed -> Icons.Outlined.CheckCircle
                      else -> Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = when {
                      manageMode -> "选择"
                      completed -> "恢复未完成"
                      else -> "标记完成"
                    },
                    tint = when {
                      // 批量方框只表达选中状态，不能继承事项的完成色。
                      manageMode -> if (MaterialTheme.colors.isLight) {
                        ScheduleTodoPendingIndicatorColor
                      } else {
                        colors.tvLv3.copy(alpha = 0.46f)
                      }
                      // Figma 的完成圆圈使用“完成色”，与批量选择主色承担不同语义。
                      completed -> if (MaterialTheme.colors.isLight) {
                        ScheduleTodoCompletedIndicatorColor
                      } else {
                        colors.tvLv3.copy(alpha = 0.46f)
                      }
                      else -> if (MaterialTheme.colors.isLight) {
                        ScheduleTodoPendingIndicatorColor
                      } else {
                        colors.tvLv3.copy(alpha = 0.46f)
                      }
                    },
                    modifier = Modifier.size(if (completed && !manageMode) 22.dp else 24.dp),
                  )
                }
              }
              // 仅当前置状态控件存在时保留标题间距，普通日程标题直接使用卡片内容起点。
              Spacer(modifier = Modifier.width(12.dp))
            }
            Row(
              modifier = Modifier
                .weight(1f)
                .padding(end = if (hasUrgencyBadge) 64.dp else 0.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = item.occurrence.title,
                color = if (completed) colors.tvLv3.copy(alpha = 0.3f) else colors.tvLv3,
                fontSize = 18.sp,
                fontWeight = if (item.isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = 0.9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .weight(1f, fill = false)
                  .wrapContentHeight()
                  .drawWithContent {
                    drawContent()
                    if (completed) {
                      // Figma 浅色稿使用独立的 2px、主文字色 10% 透明度横线，不能复用字体装饰线。
                      drawLine(
                        color = colors.tvLv3.copy(alpha = 0.1f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                      )
                    }
                  },
              )
              if (isPinned) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  painter = painterResource(pinIcon),
                  contentDescription = "已置顶",
                  tint = ScheduleTodoPinnedIndicatorTintColor,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.size(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ScheduleTodoInfoPill(
              text = item.timeText,
              icon = timeIcon,
              modifier = Modifier.weight(1f, fill = false),
            )
            reminderText?.let { text ->
              ScheduleTodoInfoPill(text = text)
            }
            if (item.schedule.kind == ScheduleKind.TODO &&
              item.occurrence.timing != ScheduleTiming.Unscheduled
            ) {
              ScheduleTodoCalendarLinkButton(
                selected = isLinkedToCalendar,
                onClick = onToggleCalendarLink,
              )
            }
          }
          if (item.occurrence.description.isNotBlank()) {
            Spacer(modifier = Modifier.size(7.dp))
            ScheduleTodoInfoPill("备注：${item.occurrence.description}")
          }
        }
        ScheduleTodoUrgencyBadge(
          item = item,
          modifier = Modifier.align(Alignment.TopEnd),
        )
        if (highlighted) {
          // 只为当前目标创建覆盖层；graphicsLayer 逐帧更新透明度，不驱动整张复杂卡片重组。
          Box(
            modifier = Modifier
              .matchParentSize()
              .graphicsLayer { alpha = highlightAlpha.value }
              .background(ScheduleTodoAccentColor),
          )
        }
      }
    }
  }
}
/**
 * 批量管理的选中方框。
 *
 * 外框保持 Figma 的 18dp 实心尺寸，内部勾使用独立的较短路径，避免 Material CheckBox 默认勾过大。
 */
@Composable
private fun ScheduleTodoCheckedBox() {
  Canvas(
    modifier = Modifier
      .size(24.dp)
      .semantics { contentDescription = "取消选择" },
  ) {
    val boxInset = 3.dp.toPx()
    drawRoundRect(
      color = ScheduleTodoAccentColor,
      topLeft = Offset(boxInset, boxInset),
      size = Size(18.dp.toPx(), 18.dp.toPx()),
      cornerRadius = CornerRadius(2.dp.toPx()),
    )
    val strokeWidth = 1.6.dp.toPx()
    val checkMiddle = Offset(10.6.dp.toPx(), 14.1.dp.toPx())
    drawLine(
      color = ScheduleTodoOnAccentColor,
      start = Offset(8.2.dp.toPx(), 11.8.dp.toPx()),
      end = checkMiddle,
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
    drawLine(
      color = ScheduleTodoOnAccentColor,
      start = checkMiddle,
      end = Offset(15.9.dp.toPx(), 9.1.dp.toPx()),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
  }
}

/** 把临期或超期状态固定覆盖在卡片右上角，尺寸和异形圆角与 Figma 标签保持一致。 */
@Composable
private fun ScheduleTodoUrgencyBadge(
  item: ScheduleTodoItemUi,
  modifier: Modifier = Modifier,
) {
  val label = when {
    item.isOverdue -> "已超期"
    item.isDueSoon -> "临期"
    else -> return
  }
  val color = if (item.isOverdue) ScheduleTodoOverdueColor else ScheduleTodoDueSoonColor
  val containerColor = if (item.isOverdue) {
    ScheduleTodoOverdueContainerColor
  } else {
    ScheduleTodoDueSoonContainerColor
  }
  Surface(
    color = containerColor,
    contentColor = color,
    shape = RoundedCornerShape(bottomStart = 10.dp, topEnd = 16.dp),
    modifier = modifier.size(
      width = if (item.isOverdue) 85.dp else 75.dp,
      height = 30.dp,
    ),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painter = painterResource(Res.drawable.schedule_ic_todo_urgency_flag),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.7.sp,
        maxLines = 1,
      )
    }
  }
}

/**
 * 卡片时间、提醒和备注共用原有信息胶囊。
 *
 * [icon] 仅用于时间项复用编辑弹窗的自绘时间图标；其余背景、文字和间距保持卡片原样。
 */
@Composable
private fun ScheduleTodoInfoPill(
  text: String,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val contentColor = if (MaterialTheme.colors.isLight) {
    ScheduleTodoInfoContentColor
  } else {
    colors.tvLv3.copy(alpha = 0.6f)
  }
  Surface(
    color = if (MaterialTheme.colors.isLight) {
      ScheduleTodoInfoContainerColor
    } else {
      colors.negative.copy(alpha = 0.55f)
    },
    contentColor = contentColor,
    shape = RoundedCornerShape(5.dp),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      icon?.let {
        Icon(
          imageVector = it,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(15.dp),
        )
      }
      Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.65.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * “关联到课表”状态按钮。
 *
 * [selected] 来自 Schedule 快照中的持久化原子；点击事件由页面交给 ViewModel，沿用 local-first 更新与远端重试链路。
 */
@Composable
private fun ScheduleTodoCalendarLinkButton(
  selected: Boolean,
  onClick: () -> Unit,
) {
  val colors = LocalAppColors.current
  val isLight = MaterialTheme.colors.isLight
  val backgroundColor = if (selected) {
    ScheduleTodoCalendarLinkSelectedColor
  } else if (isLight) {
    ScheduleTodoInfoContainerColor
  } else {
    colors.negative.copy(alpha = 0.55f)
  }
  val iconColor = if (isLight) {
    if (selected) ScheduleTodoOnAccentColor else ScheduleTodoInfoContentColor
  } else {
    // 深色模式与左侧时间、提醒文字保持相同亮度，由背景色表达关联状态。
    colors.tvLv3.copy(alpha = 0.6f)
  }
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.size(28.dp).clickableNoIndicator(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        painter = painterResource(ConfigRes.configIcCalendarSync()),
        contentDescription = if (selected) "取消关联到课表" else "关联到课表",
        tint = iconColor,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 * Figma 清单批量管理底栏。
 *
 * 全选和删除继续作用于去重后的 ScheduleId；中间操作由调用页面按需提供，清单主页用于置顶，具体
 * 分组页用于移出分组，“全部”聚合页则隐藏该操作。
 */
@Composable
internal fun ScheduleTodoManageBar(
  selectedCount: Int,
  totalCount: Int,
  middleActionLabel: String?,
  onSelectAll: () -> Unit,
  onMiddleAction: (() -> Unit)?,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  Surface(
    color = if (MaterialTheme.colors.isLight) ScheduleTodoCardContainerColor else colors.topBg,
    elevation = 8.dp,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    // 外层背景延伸到系统导航栏；只有内部按钮行上移到安全区内。
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .height(80.dp)
        .padding(horizontal = 35.dp, vertical = 20.dp),
      horizontalArrangement = if (middleActionLabel == null) {
        Arrangement.SpaceEvenly
      } else {
        Arrangement.SpaceBetween
      },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ScheduleTodoManageButton(
        label = "全选",
        backgroundColor = if (MaterialTheme.colors.isLight) {
          ScheduleTodoSelectAllContainerColor
        } else {
          colors.negative
        },
        contentColor = colors.tvLv3,
        enabled = totalCount > 0,
        onClick = onSelectAll,
      )
      if (middleActionLabel != null && onMiddleAction != null) {
        ScheduleTodoManageButton(
          label = middleActionLabel,
          backgroundColor = ScheduleTodoAccentColor.copy(alpha = 0.9f),
          contentColor = if (MaterialTheme.colors.isLight) ScheduleTodoOnAccentColor else colors.tvLv1,
          enabled = selectedCount > 0,
          onClick = onMiddleAction,
        )
      }
      ScheduleTodoManageButton(
        label = "删除",
        backgroundColor = ScheduleTodoOverdueContainerColor,
        contentColor = ScheduleTodoOverdueColor,
        enabled = selectedCount > 0,
        onClick = onDelete,
      )
    }
  }
}

/** 批量管理使用固定尺寸文字按钮，避免图标与设计稿的三等分操作产生视觉偏差。 */
@Composable
private fun ScheduleTodoManageButton(
  label: String,
  backgroundColor: Color,
  contentColor: Color,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = backgroundColor.copy(alpha = if (enabled) 1f else 0.42f),
    contentColor = contentColor.copy(alpha = if (enabled) 1f else 0.42f),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier
      .size(width = 92.dp, height = 38.dp)
      .clickableNoIndicator(enabled = enabled, onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
      )
    }
  }
}
