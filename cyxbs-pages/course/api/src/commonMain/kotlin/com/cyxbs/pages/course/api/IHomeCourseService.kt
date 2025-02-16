package com.cyxbs.pages.course.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.utils.compose.BottomSheetState

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
interface IHomeCourseService {

  var headerAlpha: Float

  var contentAlpha: Float

  @Composable
  fun Content(
    modifier: Modifier,
    bottomBarHeight: Dp,
    outerHeader: @Composable (BottomSheetState) -> Unit,
  )
}