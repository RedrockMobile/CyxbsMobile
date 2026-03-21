package com.cyxbs.pages.course.view.decoration.impl

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.impl.CourseLinkLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLinkLessonItemFactory
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
 * 关联人的课程展示
 *
 * @author 985892345
 * @date 2025/10/18
 */
class LinkLessonDecorationViewModel(
  val hierarchy: CourseItemHierarchy<CourseLinkLessonItem>,
  val platformItemFactory: PlatformCourseLinkLessonItemFactory,
) : BaseViewModel(), CoursePageDecoration {

  private val linkService = ILinkService2::class.impl()
  private val lessonService = ILessonService2::class.impl()

  init {
    linkService.state.map {
      it.linkNum
    }.flatMapLatest {
      createLessonFlow(it)
    }.flatMapLatest { list ->
      linkService.enableShow.map {
        if (it) list else emptyList()
      }
    }.onEach {
      resetData(it)
    }.catch {

    }.launchIn(viewModelScope)
  }

  private fun createLessonFlow(linkStuNum: String): Flow<List<LessonByWeeks>> {
    return if (linkStuNum.isEmpty()) flowOf(emptyList())
    else lessonService.observeLesson(
      stuNum = linkStuNum,
      needOldData = true,
    )
  }

  private fun resetData(data: List<LessonByWeeks>) {
    hierarchy.reset(buildList {
      data.forEach { lesson ->
        // 添加进整学期
        add(LinkLessonWhatTime(0, lesson, platformItemFactory))
        // 添加进每周
        lesson.week.forEach { week ->
          add(LinkLessonWhatTime(week, lesson, platformItemFactory))
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
  val platformItemFactory: PlatformCourseLinkLessonItemFactory,
) : ItemHierarchyWhatTime<CourseLinkLessonItem>() {
  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = lesson.dayOfWeek,
      beginTime = lesson.beginTime,
      finalTime = lesson.finalTime,
    )
  )

  override fun createItem(coroutineScope: CoroutineScope): CourseLinkLessonItem {
    return CourseLinkLessonItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      lesson = lesson,
      platformItemFactory = platformItemFactory,
    )
  }

  override fun compareTo(other: CourseItemWhatTime): Int {
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
}
