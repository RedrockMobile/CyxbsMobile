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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
  header: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  BottomSheetCompose(
    modifier = modifier,
    bottomSheetState = frame.bottomSheetState,
    scrimColor = Color.Transparent,
    peekHeight = frame.peekHeight,
  ) {
    Column {
      CourseBottomSheetHeaderBackground(
        modifier = Modifier.then(bottomSheetDraggable()).clickableNoIndicator {
          if (frame.bottomSheetState.state == BottomSheetValueState.Collapsed) {
            coroutineScope.launch { frame.bottomSheetState.expand() }
          }
        }
      ) {
        header()
      }
      content()
    }
  }
}

/**
 * 课程头部 BottomSheet 背景
 */
@Composable
private fun CourseBottomSheetHeaderBackground(
  modifier: Modifier = Modifier,
  headerHeight: Dp = 70.dp,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier.fillMaxWidth().height(headerHeight)
  ) {
    // 阴影
    Spacer(
      modifier = Modifier.fillMaxSize().background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0x00365789), Color(0x3D365789))
        )
      )
    )
    Box(
      modifier = Modifier.padding(top = 15.dp)
        .fillMaxSize()
        .background(color = LocalAppColors.current.topBg, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
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