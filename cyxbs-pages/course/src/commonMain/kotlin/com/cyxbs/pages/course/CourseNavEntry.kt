package com.cyxbs.pages.course

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_COURSE
import com.cyxbs.pages.course.api.CourseNavArgument
import com.cyxbs.pages.course.viewmodel.AdaptiveCourseFrameViewModel

/**
 * 课表单页
 *
 * @author 985892345
 * @date 2025/11/16
 */
@AppNav(route = NAV_COURSE)
class CourseNavEntry : AppNavEntry<CourseNavArgument>() {

  override fun isNeedLogin(argument: CourseNavArgument): Boolean {
    return true
  }

  // stableKey 非空时复用同一个查找详情；裸 deeplink 使用固定的 self key。
  override fun getContentKey(argument: CourseNavArgument): String {
    return argument.stableKey
      ?: argument.stuNum?.takeIf(String::isNotBlank)?.let { "course:$it" }
      ?: "course:self"
  }

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun buildMetadata(argument: CourseNavArgument): Map<String, Any> {
    return if (argument.stableKey != null) {
      // 由查找页等场景以 ListDetailSceneStrategy 作为 detailPane 调起
      ListDetailSceneStrategy.detailPane()
    } else {
      emptyMap()
    }
  }

  @Composable
  override fun Content(argument: CourseNavArgument) {
    val accountService = remember { IAccountService::class.impl() }
    val currentStuNum = accountService.stuNumFlow.collectAsState(accountService.stuNum).value
    // 裸课表 deeplink 表示“我的课表”；显式学号继续保留查看他人课表的原有行为。
    val resolvedStuNum = argument.stuNum?.takeIf(String::isNotBlank) ?: currentStuNum.orEmpty()
    if (resolvedStuNum.isBlank()) return

    val courseFrameViewModel = viewModel { AdaptiveCourseFrameViewModel(resolvedStuNum) }
    // 当复用同一个 NavEntry 但目标学号变化时，触发 frame 内部学号更新，
    // CoursePageDecorationManager 会重建并订阅新的课表数据。
    LaunchedEffect(resolvedStuNum) {
      courseFrameViewModel.frame.updateStuNum(resolvedStuNum)
    }
    courseFrameViewModel.frame.HomeCourseContent(modifier = Modifier)
  }
}
