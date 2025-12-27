package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.LocalCourseItemState

/**
 * .
 *
 * @author 985892345
 * @date 2025/11/16
 */
@Stable
interface CourseItemModifier {

  val itemState: CourseItemState
    @Composable
    get() = LocalCourseItemState.current

  @Composable
  fun createModifier(): Modifier
}