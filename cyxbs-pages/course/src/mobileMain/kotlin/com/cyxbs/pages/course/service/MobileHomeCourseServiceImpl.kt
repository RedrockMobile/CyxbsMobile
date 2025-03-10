package com.cyxbs.pages.course.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.utils.compose.BottomSheetState
import com.cyxbs.pages.course.api.IMobileHomeCourseService
import com.cyxbs.pages.course.home.compose.HomeCourseCompose
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
@ImplProvider
object MobileHomeCourseServiceImpl : IMobileHomeCourseService {

  override var headerAlpha: Float by mutableFloatStateOf(1F)

  override var contentAlpha: Float by mutableFloatStateOf(1F)

  @Composable
  override fun Content(
    modifier: Modifier,
    bottomBarHeight: Dp,
    outerHeader: @Composable (BottomSheetState) -> Unit
  ) {
    HomeCourseCompose(
      modifier = modifier,
      bottomBarHeight = bottomBarHeight,
      outerHeader = outerHeader
    )
  }
}