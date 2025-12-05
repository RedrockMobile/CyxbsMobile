package com.cyxbs.pages.course.home.item.decoration

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.course.home.item.CourseAffairItem
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.cyxbs.pages.course.view.item.LocalCourseItemState
import kotlinx.datetime.DayOfWeek

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/18
 */
class AffairDecorationViewModel(
  val hierarchy: CourseItemViewModel.ItemHierarchy<CourseAffairItem>,
) : BaseViewModel(), CoursePageDecoration {

  companion object {
    val Comparable = compareBy<CourseAffairItem> { -it.page } // page 越小越在上
      .thenBy { -it.affairDateModel.date.value.dayOfWeek.ordinal } // dayOfWeek 越小越在上
      .thenBy { it.affairDateModel.whatTime.value.timePair.value.first } // beginTime 越大越在上
      .thenBy { it.affairDateModel.whatTime.value.timePair.value.second } // finalTime 越大越在上
  }

  @Composable
  override fun CoursePage(nextContent: @Composable (() -> Unit)) {
    CoursePageCompose(nextContent)
  }
}


@Composable
private fun AffairDecorationViewModel.CoursePageCompose(
  nextContent: @Composable (() -> Unit)
) {
  val page = coursePage.page
  Box {
    nextContent()
    DayOfWeek.entries.forEach {
      DayOfWeekCompose(page, it)
    }
  }
}

@Composable
private fun AffairDecorationViewModel.DayOfWeekCompose(
  page: Int,
  dayOfWeek: DayOfWeek,
) {
  val overlayResultList by hierarchy.observe(page, dayOfWeek).collectAsState()
  overlayResultList.fastForEach { itemState ->
    CompositionLocalProvider(LocalCourseItemState provides itemState) {
      itemState.item.CourseItemContent()
    }
  }
}