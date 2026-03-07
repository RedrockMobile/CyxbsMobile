package com.cyxbs.pages.course.frame.decoration

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.frame.item.LinkLessonItem
import com.cyxbs.pages.course.view.frame.item.LinkLessonItemFactory
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.model.LinkLessonRepository
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
  val hierarchy: CourseItemHierarchy<LinkLessonItem>,
) : BaseViewModel(), CoursePageDecoration {

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

    }.launchIn(viewModelScope)
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
        add(LinkLessonWhatTime(0, lesson))
        // 添加进每周
        lesson.week.forEach { week ->
          add(LinkLessonWhatTime(week, lesson))
        }
      }
    })
  }

  @Composable
  override fun CoursePageContent() {
    hierarchy.CoursePageItemListContent()
  }
}

private data class LinkLessonWhatTime(
  val page: Int,
  val lesson: LessonByWeeks,
) : ItemHierarchyWhatTime<LinkLessonItem>() {
  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = lesson.dayOfWeek,
      beginTime = lesson.beginTime,
      finalTime = lesson.finalTime,
    )
  )

  override fun createItem(coroutineScope: CoroutineScope): LinkLessonItem {
    return LinkLessonItemFactory.get().createLinkLessonItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      lesson = lesson,
    )
  }

  override fun compareTo(other: ItemHierarchyWhatTime<LinkLessonItem>): Int {
    return 0.compareBy(other) {
      -it.now.value.page // page 越小越在上
    }.compareBy(other) {
      -((other as? LinkLessonWhatTime)?.lesson?.week?.get(0) ?: 0) // 起始周越小越在上
    }.compareBy(other) {
      -it.now.value.dayOfWeek.ordinal // dayOfWeek 越小越在上
    }.compareBy(other) {
      it.now.value.beginTime.value // beginTime 越大越在上
    }.compareBy(other) {
      it.now.value.finalTime.value // finalTime 越大越在上
    }
  }

  private inline fun Int.compareBy(
    other: ItemHierarchyWhatTime<LinkLessonItem>,
    compare: (ItemHierarchyWhatTime<LinkLessonItem>) -> Int
  ): Int {
    if (this != 0) return this
    val a = compare.invoke(this@LinkLessonWhatTime)
    val b = compare.invoke(other)
    return a - b
  }
}
