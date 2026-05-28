package com.cyxbs.pages.course.find

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_COURSE_FIND
import com.cyxbs.pages.course.api.FindCourseNavArgument
import com.cyxbs.pages.course.find.ui.FindCourseScreen
import com.cyxbs.pages.course.find.viewmodel.FindCourseViewModel

/**
 * 查找他人课表页（合并搜索 + 结果，宽屏作为 ListDetailSceneStrategy 的 listPane）
 *
 * @author 985892345
 * @date 2026/5/27
 */
@AppNav(route = NAV_COURSE_FIND)
class FindCourseNavEntry : AppNavEntry<FindCourseNavArgument>() {

  override fun isNeedLogin(argument: FindCourseNavArgument): Boolean = true

  // 单实例：同一时刻只保留一个查找页 NavEntry，参数变更通过 LaunchedEffect 处理
  override fun getContentKey(argument: FindCourseNavArgument): String = NAV_COURSE_FIND

  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  override fun buildMetadata(argument: FindCourseNavArgument): Map<String, Any> {
    return ListDetailSceneStrategy.listPane(
      detailPlaceholder = { FindCoursePlaceholder(argument) }
    )
  }

  @Composable
  override fun Content(argument: FindCourseNavArgument) {
    viewModel { FindCourseViewModel() }
    FindCourseScreen(
      argument = argument,
    )
  }
}

/** 宽屏右侧 detailPane 未选中时的占位（仅用于 ListDetailSceneStrategy.listPane 的 detailPlaceholder） */
@Composable
private fun FindCoursePlaceholder(argument: FindCourseNavArgument) {
  Box(
    modifier = Modifier.fillMaxSize().background(LocalAppColors.current.topBg),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "选择左侧的查询结果以查看课表",
      color = LocalAppColors.current.tvLv2,
      fontSize = 14.sp,
    )
  }
}
