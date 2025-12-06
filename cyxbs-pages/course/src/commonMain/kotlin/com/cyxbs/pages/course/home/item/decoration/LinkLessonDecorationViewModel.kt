package com.cyxbs.pages.course.home.item.decoration

import androidx.compose.runtime.Composable
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.LinkLessonItem
import com.cyxbs.pages.course.home.item.LinkLessonItemFactory
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.model.LinkLessonRepository
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/18
 */
class LinkLessonDecorationViewModel(
  val hierarchy: CourseItemViewModel.ItemHierarchy<LinkLessonItem>,
) : BaseViewModel(), CoursePageDecoration {

  companion object {
    val Comparable = compareBy<LinkLessonItem> { -it.page } // page 越小越在上
      .thenBy { -it.lesson.week[0] } // 起始周越小越在上
      .thenBy { -it.lesson.dayOfWeek.ordinal } // dayOfWeek 越小越在上
      .thenBy { it.lesson.beginTime } // beginTime 越大越在上
      .thenBy { it.lesson.finalTime } // finalTime 越大越在上
  }

  private val itemFactory = LinkLessonItemFactory.get()

  init {
    LinkLessonRepository.state.map {
      it.linkNum
    }.flatMapLatest {
      createLessonFlow(it)
    }.flatMapLatest { list ->
      LinkLessonRepository.enableShow.map {
        if (it) list else emptyList()
      }
    }.onEach {
      resetData(it)
    }.catch {

    }.launchIn(appCoroutineScope)
  }

  private fun createLessonFlow(linkStuNum: String): Flow<List<LessonByWeeks>> {
    return if (linkStuNum.isEmpty()) flowOf(emptyList())
    else LessonRepository.observeLesson(
      stuNum = linkStuNum,
      needOldData = true,
    )
  }

  private fun resetData(data: List<LessonByWeeks>) {
    hierarchy.reset(buildList {
      data.forEach { lesson ->
        // 添加进整学期
        add(itemFactory.createLinkLessonItem(0, lesson))
        // 添加进每周
        lesson.week.forEach { week ->
          add(itemFactory.createLinkLessonItem(week, lesson))
        }
      }
    })
  }

  @Composable
  override fun CoursePageContent() {
    hierarchy.CoursePageItemListContent()
  }
}