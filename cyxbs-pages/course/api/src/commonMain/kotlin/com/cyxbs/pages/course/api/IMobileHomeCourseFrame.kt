package com.cyxbs.pages.course.api

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModel
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetState

/**
 * 主页课表框架
 *
 * @author 985892345
 * @date 2025/3/30
 */
interface IMobileHomeCourseFrame : AutoCloseable {

  val pagerState: PagerState

  val bottomSheetState: BottomSheetState

  @Composable
  fun HomeCourseContent(
    modifier: Modifier,
    bottomBarHeight: Dp,
  )
}

/**
 * 主页课表 Frame 工厂。
 *
 * 创建方法强制接收 [ViewModel]，实现方必须在返回 Frame 前将其注册为 ViewModel 的
 * Closeable，从 API 层杜绝取得未绑定生命周期的主页课表 Frame。
 */
interface IMobileHomeCourseFrameFactory {

  /** 创建并绑定到 [owner] 生命周期的主页课表 Frame。 */
  fun create(owner: ViewModel): IMobileHomeCourseFrame
}
