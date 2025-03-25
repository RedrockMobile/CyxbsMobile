package com.cyxbs.pages.course.view.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.cyxbs.components.utils.compose.dark

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/16
 */

/**
 * 课程头部 BottomSheet 背景
 */
@Composable
fun CourseBottomSheetHeaderBackground(
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