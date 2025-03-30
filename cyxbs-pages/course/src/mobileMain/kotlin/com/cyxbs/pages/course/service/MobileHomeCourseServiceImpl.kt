package com.cyxbs.pages.course.service

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.pages.course.api.IMobileHomeCourseService
import com.cyxbs.pages.course.home.compose.MobileHomeCourseFrame
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/15
 */
@ImplProvider
object MobileHomeCourseServiceImpl : IMobileHomeCourseService {

  @Composable
  override fun Content(
    modifier: Modifier,
    bottomBarHeight: Dp,
    coverContent: @Composable (BottomSheetState) -> Unit
  ) {
    val viewModel = viewModel { MobileHomeCourseViewModel() }
    Box(modifier) {
      key(viewModel.frame) {
        viewModel.frame.Content()
      }
      coverContent(viewModel.frame.bottomSheetState)
    }
    DisposableEffect(bottomBarHeight) {
      viewModel.frame.set(bottomBarHeight)
      onDispose {}
    }
  }
}

// 单纯只是为了提升 MobileHomeCourseFrame 的生命周期
private class MobileHomeCourseViewModel : BaseViewModel() {
  val frame = MobileHomeCourseFrame()
}