package com.cyxbs.pages.course.view.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.course.view.header.CourseBottomSheetHeaderBackground
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 手机端 BottomSheet 样式课表，带有整学期页面
 *
 * @author 985892345
 * @date 2025/2/16
 */
@Stable
abstract class CourseBottomSheetFrame : CourseSemesterFrame() {

  // BottomSheetCompose State
  open val bottomSheetState by lazy {
    BottomSheetState()
  }

  // BottomSheetCompose peekHeight
  open val peekHeight: Dp
    get() = 70.dp

  @Composable
  override fun CourseCompose() {
    BottomSheetCompose(
      bottomSheetState = bottomSheetState,
      scrimColor = Color.Transparent,
      peekHeight = peekHeight,
    ) {
      Column {
        CourseBottomSheetHeaderBackground(
          modifier = Modifier.then(bottomSheetDraggable()).clickableNoIndicator {
            if (bottomSheetState.state == BottomSheetValueState.Collapsed) {
              coroutineScope?.launch { bottomSheetState.expand() }
            }
          }
        ) {
          CourseHeader(Modifier)
        }
        super.CourseCompose()
      }
    }
  }

  @Composable
  open fun CourseHeader(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
      Row(modifier = Modifier.align(Alignment.BottomStart)) {
        CourseHeaderTitle(
          modifier = Modifier.align(Alignment.Bottom)
            .padding(start = 16.dp, bottom = 4.dp)
        )
        CourseHeaderSubtitle(
          modifier = Modifier.align(Alignment.Bottom)
            .padding(start = 13.dp, bottom = 6.dp)
        )
      }
      CourseHeaderBack(
        modifier = Modifier.align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = 2.dp)
      )
    }
  }

  @Composable
  fun CourseHeaderTitle(modifier: Modifier) {
    Text(
      text = pagerState.currentPage.let {
        if (it == 0) "整学期"
        else "第${Num2CN.number2ChineseNumber(it)}周"
      },
      modifier = modifier,
      color = LocalAppColors.current.tvLv2,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp
    )
  }

  @Composable
  fun CourseHeaderSubtitle(modifier: Modifier) {
    // 0 -> 1 -> 0
    val pageFraction by rememberDerivedStateOfStructure(this) {
      val fraction = pagerState.currentPageOffsetFraction
      1 - minOf(abs(fraction + pagerState.currentPage - initialPage), 1F)
    }
    Text(
      text = "(本周)",
      modifier = modifier.graphicsLayer {
        alpha = pageFraction
        scaleX = pageFraction
        scaleY = pageFraction
      },
      fontSize = 15.sp,
      color = LocalAppColors.current.tvLv2,
    )
  }

  @Composable
  fun CourseHeaderBack(modifier: Modifier) {
    // 0 -> 1 -> 0
    val pageFraction by rememberDerivedStateOfStructure(this) {
      val fraction = pagerState.currentPageOffsetFraction
      1 - minOf(abs(fraction + pagerState.currentPage - initialPage), 1F)
    }
    Text(
      text = "回到本周",
      modifier = modifier.graphicsLayer {
        alpha = 1 - pageFraction
        translationX = pageFraction * size.width
      }.clip(CircleShape).background(
        brush = Brush.horizontalGradient(
          colors = listOf(Color.Blue, Color(0xFF8686FF)),
        )
      ).clickableNoIndicator {
        coroutineScope?.launch {
          pagerState.animateScrollToPage(initialPage)
        }
      }.padding(vertical = 8.dp, horizontal = 16.dp),
      color = Color.White,
      fontSize = 13.sp,
    )
  }
}