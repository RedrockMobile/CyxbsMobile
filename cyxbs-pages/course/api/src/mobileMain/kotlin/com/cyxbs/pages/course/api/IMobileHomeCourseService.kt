package com.cyxbs.pages.course.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.view.ui.BottomSheetState

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
interface IMobileHomeCourseService {

  @Composable
  fun Content(
    modifier: Modifier,
    bottomBarHeight: Dp,
    coverContent: @Composable (BottomSheetState) -> Unit,
  )
}