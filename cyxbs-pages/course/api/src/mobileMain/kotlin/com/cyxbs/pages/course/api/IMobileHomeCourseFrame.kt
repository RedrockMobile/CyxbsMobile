package com.cyxbs.pages.course.api

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.cyxbs.components.view.ui.BottomSheetState

/**
 * 主页课表框架
 *
 * @author 985892345
 * @date 2025/3/30
 */
interface IMobileHomeCourseFrame {

  val pagerState: PagerState

  val bottomSheetState: BottomSheetState

  @Composable
  fun HomeCourseContent(
    modifier: Modifier,
    bottomBarHeight: Dp,
  )
}