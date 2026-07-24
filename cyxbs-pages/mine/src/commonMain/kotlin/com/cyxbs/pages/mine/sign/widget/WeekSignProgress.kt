package com.cyxbs.pages.mine.sign.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.mine.sign.model.bean.SignStatus
import com.cyxbs.pages.mine.sign.util.SignUtil
import com.cyxbs.pages.mine.sign.viewmodel.SignComposeViewModel
import com.cyxbs.pages.mine.sign.viewmodel.SignEvent
import cyxbsmobile.cyxbs_pages.mine.generated.resources.Res
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_sign_bubble
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_sign_diamond
import org.jetbrains.compose.resources.painterResource

/**  
 * description: 签到组件
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 15:19
 */
@Composable
fun WeekSignProgress(
  modifier: Modifier = Modifier,
  signStatus: SignStatus
) {
  val viewmodel: SignComposeViewModel = viewModel()
  // 保存六根线的状态
  val weekSignStates = remember {
    List(6) {
      WeekSignState()
    }
  }
  // 将weekInfo转为State
  val lineStates = remember(signStatus.weekInfo) {
    signStatus.weekInfo.toWeekLineStates()
  }
  val dayStates = remember(signStatus.weekInfo) {
    signStatus.weekInfo.toWeekDayStates()
  }
  val todayIndex = SignUtil.getTodayOfWeek()
  val todayScore = remember(signStatus.weekInfo, todayIndex) {
    signStatus.weekInfo.getTodayScore(todayIndex)
  }

  val greyColor = 0xFFE1E6F0.dark(0xFF5A5A5A)
  val blueColor = 0xFF3A35D2.dark(0xFF2CDEFF)
  val lightBlueColor = 0xFFB3E5FC.dark(0x552CDEFF)

  val slotSize = 21.dp // 点的大小
  val bubbleHeight = 29.dp // 气泡高度
  val bubbleGap = 8.dp // 间距
  /**
   * 绘制每段线时保证起点和终点为相邻两个圆的中心
   * 然后圆点也均分Arrangement.SpaceBetween，就能保证能重合在一起
   * 高度则是把线的CenterY设置为气泡高度+间距+半径，这样也能对齐了
   */
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(84.dp) // 约束一个总的高度
  ) {
    // 先绘制线，然后把圈覆盖在上面
    Canvas(Modifier.matchParentSize()) {
      val slotWidth = slotSize.toPx()
      val firstCenterX = slotWidth / 2
      val centerDistance = (size.width - slotWidth) / 6 // 7个圆心6个间距
      val centerY = (bubbleHeight + bubbleGap + slotSize / 2).toPx() // 气泡高度+间距+半径
      val stokeWidth = 5.dp.toPx()
      repeat(6) { index ->
        // 计算开始和结束位置
        val startX = firstCenterX + centerDistance * index
        val endX = firstCenterX + centerDistance * (index + 1)
        val lineColor = when (lineStates[index]) {
          WeekLineState.GREY -> greyColor
          WeekLineState.BLUE -> blueColor
          WeekLineState.LIGHT_BLUE -> lightBlueColor
        }
        drawWeekLine(
          start = Offset(startX, centerY),
          end = Offset(endX, centerY),
          backgroundColor = greyColor,
          foregroundColor = lineColor,
          progress = weekSignStates[index].progress,
          strokeWidth = stokeWidth
        )
      }
    }

    // 画气泡+圆点+周几
    Row(
      modifier = Modifier.matchParentSize(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      dayStates.forEachIndexed { index, dayState ->
        Column(
          modifier = Modifier.width(slotSize).height(84.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // 仅今天显示气泡，其他列必须占同样高度
          if (index == todayIndex) {
            SignScoreBubble(score = todayScore)
          } else {
            Spacer(Modifier.height(bubbleHeight))
          }
          Spacer(Modifier.height(bubbleGap))
          // 圆点
          Box(
            modifier = Modifier.height(slotSize),
            contentAlignment = Alignment.Center
          ) {
            WeekDayCircle(weekDayState = dayState)
          }
          Spacer(Modifier.height(9.dp))
          // 周几
          Text(
            modifier = Modifier.requiredWidth(28.dp),
            text = weekTexts[index],
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = LocalAppColors.current.tvLv2.copy(alpha = 0.35f),
          )
        }
      }
    }
  }

  // 先缓存事件，再等待weekInfo
  val pendingAnimation = remember {
    mutableStateOf<SignEvent.AnimateWeekLine?>(null)
  }
  LaunchedEffect(viewmodel.updateProgressEvent) {
    viewmodel.updateProgressEvent.collect { event ->
      when (event) {
        is SignEvent.AnimateWeekLine -> {
          pendingAnimation.value = event
        }
      }
    }
  }
  // 真正执行动画的地方
  LaunchedEffect(
    pendingAnimation.value,
    signStatus.weekInfo,
  ) {
    val event = pendingAnimation.value ?: return@LaunchedEffect

    // 新状态尚未完成重组时，保留事件，等待下一次重组
    if (event.weekInfo != signStatus.weekInfo) return@LaunchedEffect

    val index = event.index
    if (lineStates.getOrNull(index) == WeekLineState.BLUE) {
      weekSignStates[index].setProgress(0f)
      weekSignStates[index].animateProgress(1f)
    }

    // animateProgress 完成后再清掉，避免 Effect 被提前取消
    pendingAnimation.value = null
  }

}

private val weekTexts = listOf(
  "周一", "周二", "周三", "周四", "周五", "周六", "周日",
)

/**
 * 每天状态的圆圈
 */
@Composable
fun WeekDayCircle(
  modifier: Modifier = Modifier,
  weekDayState: WeekDayState
) {
  when (weekDayState) {
    WeekDayState.PENDING -> {
      Box(
        modifier = modifier
          .size(15.dp)
          .background(
            color = 0xFFE1E6F0.dark(0xFF5A5A5A),
            shape = CircleShape
          )
      )
    }

    WeekDayState.TODAY_PENDING -> {
      Image(
        modifier = modifier.size(21.dp),
        painter = painterResource(Res.drawable.mine_ic_sign_diamond),
        contentDescription = null
      )
    }

    WeekDayState.SIGNED -> {
      Box(
        modifier = modifier
          .size(16.dp)
          .background(
            color = 0xFF3A35D2.dark(0xFF2CDEFF),
            shape = CircleShape
          )
          .border(
            width = 4.dp,
            color = Color.White,
            shape = CircleShape
          )
      )
    }
  }
}

/**
 * 绘制带progress的线
 */
private fun DrawScope.drawWeekLine(
  start: Offset,
  end: Offset,
  backgroundColor: Color,
  foregroundColor: Color,
  progress: Float,
  strokeWidth: Float,
) {
  val safeProgress = progress.coerceIn(0f, 1f)

  // 灰色底线
  drawLine(
    color = backgroundColor,
    start = start,
    end = end,
    strokeWidth = strokeWidth,
    cap = StrokeCap.Butt,
  )

  // 状态前景线
  drawLine(
    color = foregroundColor,
    start = start,
    end = Offset(
      x = start.x + (end.x - start.x) * safeProgress,
      y = start.y + (end.y - start.y) * safeProgress,
    ),
    strokeWidth = strokeWidth,
    cap = StrokeCap.Butt,
  )
}

/**
 * 积分气泡
 */
@Composable
private fun SignScoreBubble(
  score: Int,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.requiredSize(
      width = 53.dp,
      height = 29.dp,
    ),
    contentAlignment = Alignment.TopCenter,
  ) {
    Image(
      modifier = Modifier.matchParentSize(),
      painter = painterResource(Res.drawable.mine_ic_sign_bubble),
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
    )

    Text(
      modifier = Modifier.padding(top = 4.dp),
      text = "${score}积分",
      fontSize = 11.sp,
      color = 0xA24841E2.dark(0xFF1D1D1D),
    )
  }
}
