package com.cyxbs.pages.course.home.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.components.view.ui.rememberBottomSheetState
import com.cyxbs.components.view.ui.Window
import kotlinx.coroutines.flow.first
import kotlin.math.hypot
import kotlin.math.max

/**
 * 移动端课表 item 点击后出现的 BottomSheetDialog
 *
 * @author 985892345
 * @date 2025/3/27
 */
@Composable
fun MobileCourseBottomSheetDialog(
  dialogContents: MutableState<List<BottomSheetDialogContent>>,
) {
  if (dialogContents.value.isNotEmpty()) {
    Window(
      dismissOnBackPress = {
        dialogContents.value = emptyList()
      }
    ) {
      val bottomSheetState = rememberBottomSheetState()
      BottomSheetCompose(
        modifier = Modifier,
        bottomSheetState = bottomSheetState,
        dismissOnClickOutside = true,
        scrimColor = Color.Transparent,
      ) {
        Box(modifier = Modifier.navigationBarsPadding().fillMaxWidth().height(280.dp)) {
          Spacer(
            modifier = Modifier.fillMaxWidth().height(36.dp).background(
              brush = Brush.verticalGradient(
                colors = listOf(Color(0x005369BC), Color(0x205369BC))
              )
            )
          )
          Box(
            modifier = Modifier.padding(top = 20.dp)
              .fillMaxSize()
              .then(bottomSheetDraggable())
              .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
              .background(LocalAppColors.current.whiteBlack)
          ) {
            CourseBottomSheetDialogContent(dialogContents.value)
          }
        }
      }
      LaunchedEffect(Unit) {
        bottomSheetState.expand()
        bottomSheetState.stateFlow.first { it == BottomSheetValueState.Collapsed }
        dialogContents.value = emptyList()
      }
    }
  }
}

@Composable
private fun CourseBottomSheetDialogContent(
  itemDialogContents: List<BottomSheetDialogContent>
) {
  val pagerState = rememberPagerState(
    initialPage = if (itemDialogContents.size == 1) 0 else itemDialogContents.size * 1000,
  ) {
    if (itemDialogContents.size == 1) 1 else Int.MAX_VALUE
  }
  Column(modifier = Modifier.fillMaxSize()) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxWidth().weight(1F),
    ) {
      itemDialogContents[it % itemDialogContents.size].BottomSheetDialogContent()
    }
    // 底部的圆点指示器
    Spacer(modifier = Modifier.fillMaxWidth().height(24.dp).drawWithCache {
      val radius = 4.dp.toPx()
      val interval = 16.dp.toPx()
      val beginX = size.width / 2 - (itemDialogContents.size - 1) * interval / 2
      val beginY = size.height / 2
      onDrawBehind {
        val itemCount = itemDialogContents.size
        val currentPage = pagerState.currentPage
        val currentPageOffset = pagerState.currentPageOffsetFraction
        val absoluteOffset = currentPage + currentPageOffset
        val relativeOffset = if (absoluteOffset % itemCount > itemCount - 1) { // 当划出边界时
          (1 - (absoluteOffset - absoluteOffset.toInt())) * (itemCount - 1) // 这里可以表示从右边界到左边界(或相反)经过的值
        } else absoluteOffset % itemCount
        repeat(itemCount) {
          drawCircle(Color(0xFF888888), radius, Offset(beginX + it * interval, beginY))
        }
        val relativeOffsetInt = relativeOffset.toInt()
        val path = getWaterDropIndicator(radius, relativeOffset - relativeOffsetInt, interval)
        path.translate(Offset(beginX + relativeOffsetInt * interval, beginY))
        drawPath(path, Color(0xFF788EFA))
      }
    })
  }
}

// 基本思路是两个圆点之间的上下方有两个半径很大的圆, 小圆点就在这两个大圆之间被挤压着移动
private fun getWaterDropIndicator(
  radius: Float,
  fraction: Float, // 0.0 -> 1.0
  interval: Float,
) : Path {
  var path = Path()
  // 中间大圆的坐标
  val outerX = interval / 2
  val outerY = interval
  val outerR = hypot(outerX, outerY) - radius
  // 绘制当前移动点的圆
  val nowX = fraction * interval
  val nowR = hypot(outerX - nowX, outerY) - outerR
  path.addRoundRect(RoundRect(Rect(Offset(nowX, 0F), nowR), CornerRadius(nowR)))
  // 绘制跟随移动的圆
  val startMove = 0.6F
  val k = 1 / (1 - startMove)
  val b = 1 - k
  val followX = max(0F, k * fraction + b) * interval
  val followR = hypot(outerX - followX, outerY) - outerR
  path.addRoundRect(RoundRect(Rect(Offset(followX, 0F), followR), CornerRadius(followR)))
  // 与两个圆上下端点构成的四边形相并
  val path2 = Path()
  path2.moveTo(nowX, nowR)
  path2.lineTo(nowX, -nowR)
  path2.lineTo(followX, -followR)
  path2.lineTo(followX, followR)
  path2.close()
  path += path2
  // 排除上下两个大圆
  path2.reset()
  path2.addRoundRect(RoundRect(Rect(Offset(outerX, outerY), outerR), CornerRadius(outerR)))
  path2.addRoundRect(RoundRect(Rect(Offset(outerX, -outerY), outerR), CornerRadius(outerR)))
  path -= path2
  return path
}
