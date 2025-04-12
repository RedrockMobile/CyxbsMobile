package com.cyxbs.pages.course.home.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.view.header.CourseBottomSheetHeaderBackground
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