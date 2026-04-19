package com.cyxbs.pages.home.ui.course

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.sp.SP_COURSE_COMPOSE
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.pages.home.R
import com.cyxbs.pages.home.mobile.ui.IOldHomeCourse
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/20
 */
@ImplProvider
object AndroidOldHomeCourseImpl : IOldHomeCourse {

  override val enable: Boolean = !defaultSp.getBoolean(SP_COURSE_COMPOSE, isDebug())

  override val content: @Composable ((Modifier) -> Unit) = { modifier ->
    val bottomNavViewModel = viewModel(BottomNavViewModel::class)
    val courseBottomSheetViewModel = viewModel(CourseBottomSheetViewModel::class)
    val coroutineScope = rememberCoroutineScope()
    AndroidView(
      modifier = modifier.fillMaxSize().systemBarsPadding(),
      factory = {
        HomeCourseLayout(
          context = it,
          lifecycleScope = coroutineScope,
          bottomNavViewModel = bottomNavViewModel,
          courseBottomSheetViewModel = courseBottomSheetViewModel,
        ).apply {
          id = R.id.home_course_container_id
        }
      },
      update = {
        // bottomNavViewModel 和 courseBottomSheetViewModel 正常来说不会改变
      },
      onRelease = {
        it.onRelease()
      }
    )
  }
}