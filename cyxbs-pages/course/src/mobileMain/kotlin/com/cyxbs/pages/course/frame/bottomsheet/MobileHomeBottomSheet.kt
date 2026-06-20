package com.cyxbs.pages.course.frame.bottomsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.frame.MobileHomeCourseFrame

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/12
 */

@Composable
fun MobileHomeBottomSheet(
  modifier: Modifier,
  frame: MobileHomeCourseFrame,
  peekHeightExtra: Dp,
  header: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  BottomSheetCompose(
    modifier = modifier,
    bottomSheetState = frame.bottomSheetState,
    scrimColor = Color.Transparent,
    peekHeight = frame.peekHeightState.value + peekHeightExtra,
  ) {
    CourseBottomSheetBackground(
      headerHeight = frame.peekHeightState.value
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier = Modifier.fillMaxWidth().height(frame.peekHeightState.value)
            .then(bottomSheetDraggable()).clickableNoIndicator {
              if (frame.bottomSheetState.state == BottomSheetValueState.Collapsed) {
                frame.bottomSheetState.expandAsync()
              }
            }
        ) {
          header()
        }
        content()
      }
    }
  }
}

/**
 * BottomSheet 背景
 */
@Composable
private fun CourseBottomSheetBackground(
  headerHeight: Dp,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    // 阴影
    Spacer(
      modifier = Modifier.fillMaxWidth().height(headerHeight).background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0x00365789), Color(0x3D365789))
        )
      )
    )
    // 圆角
    Box(
      modifier = Modifier.padding(top = 15.dp)
        .fillMaxSize()
        .background(color = LocalAppColors.current.topBg, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
      // 拖动的 tips
      Spacer(
        modifier = Modifier.align(Alignment.TopCenter)
          .padding(top = 10.dp)
          .size(38.dp, 5.dp)
          .background(color = 0xFFE2EDFB.dark(Color.Black), shape = RoundedCornerShape(6.dp))
      )
    }
    content()
  }
}