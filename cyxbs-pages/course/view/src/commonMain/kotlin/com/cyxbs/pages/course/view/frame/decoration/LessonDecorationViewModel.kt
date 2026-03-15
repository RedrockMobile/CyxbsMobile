package com.cyxbs.pages.course.view.frame.decoration

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.impl.CourseLessonItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseLessonItemFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * 普通课程展示
 *
 * @author 985892345
 * @date 2026/3/7
 */
class LessonDecorationViewModel(
  val stuNum: String,
  val hierarchy: CourseItemHierarchy<CourseLessonItem>,
  val platformItemFactory: PlatformCourseLessonItemFactory,
) : BaseViewModel(), CoursePageDecoration {

  private val lessonService = ILessonService2::class.impl()

  init {
    lessonService.observeLesson(
      stuNum = stuNum,
      needOldData = true,
      forceRequest = true,
    ).distinctUntilChanged().onEach {
      hierarchy.reset(buildList {
        it.forEach { lesson ->
          // 添加进整学期
          add(LessonWhatTime(0, lesson, platformItemFactory))
          // 添加进每周
          lesson.week.forEach { week ->
            add(LessonWhatTime(week, lesson, platformItemFactory))
          }
        }
      })
    }.launchIn(viewModelScope)
  }

  @Composable
  override fun CoursePageContent() {
    hierarchy.CoursePageItemListContent()
  }
}

private data class LessonWhatTime(
  val page: Int,
  val lesson: LessonByWeeks,
  val platformItemFactory: PlatformCourseLessonItemFactory, // 创建平台配置 factory 的 key
) : ItemHierarchyWhatTime<CourseLessonItem>() {
  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = lesson.dayOfWeek,
      beginTime = lesson.beginTime,
      finalTime = lesson.finalTime,
    )
  )

  override fun createItem(coroutineScope: CoroutineScope): CourseLessonItem {
    return CourseLessonItem(
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
      -((other as? LessonWhatTime)?.lesson?.week?.get(0) ?: 0) // 起始周越小越在上
    }.compareBy(other) {
      -it.now.value.dayOfWeek.ordinal // dayOfWeek 越小越在上
    }.compareBy(other) {
      it.now.value.beginTime.value // beginTime 越大越在上
    }.compareBy(other) {
      it.now.value.finalTime.value // finalTime 越大越在上
    }
  }
}
