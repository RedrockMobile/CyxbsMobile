package com.cyxbs.pages.course.home.item.decoration

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.SelfLessonItem
import com.cyxbs.pages.course.home.item.SelfLessonItemFactory
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import com.cyxbs.pages.course.view.item.LocalCourseItemState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.DayOfWeek
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/12
 */
@Stable
class SelfLessonDecorationViewModel(
  val hierarchy: CourseItemViewModel.ItemHierarchy<SelfLessonItem>,
) : BaseViewModel(), CoursePageDecoration {

  companion object {
    val Comparable = compareBy<SelfLessonItem> { -it.page } // page 越小越在上
      .thenBy { -it.lesson.week[0] } // 起始周越小越在上
      .thenBy { -it.lesson.dayOfWeek.ordinal } // dayOfWeek 越小越在上
      .thenBy { it.lesson.beginTime } // beginTime 越大越在上
      .thenBy { it.lesson.finalTime } // finalTime 越大越在上
  }

  private val itemFactory = SelfLessonItemFactory.get()

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .flatMapLatest {
        createLessonFlow(it)
//        createLessonFlow("2024210480")
//        createLessonFlow("2022211292")
      }.onEach {
        hierarchy.reset(buildList {
          it?.forEach { lesson ->
            // 添加进整学期
            add(itemFactory.createSelfLessonItem(0, lesson))
            // 添加进每周
            lesson.week.forEach { week ->
              add(itemFactory.createSelfLessonItem(week, lesson))
            }
          }
        })
      }.catch {

      }.launchIn(appCoroutineScope)
  }

  private fun createLessonFlow(stuNum: String?): Flow<List<LessonByWeeks>?> = flow {
    if (stuNum == null) {
      emit(null)
    } else {
      // 先获取缓存
      val cacheLesson = LessonRepository.getCacheLesson(stuNum)
      var nowLesson = cacheLesson?.data
      if (nowLesson != null) {
        emit(nowLesson)
      }
      // 进行一次请求
      LessonRepository.requestLesson(stuNum).onSuccess {
        if (it != nowLesson) {
          nowLesson = it
          emit(it)
        }
      }.onFailure {
        if (cacheLesson != null) {
          val diffDay = Clock.System.now() - cacheLesson.requestTime
          if (diffDay < 1.days) {
            toastLong("课表正在使用缓存\n不能保证数据正确性")
          } else {
            toastLong("已 ${diffDay.inWholeDays} 天未更新课表\n建议联网更新")
          }
        }
      }
      // 观察后续课程的更新
      LessonRepository.observeLesson(
        stuNum = stuNum,
        needOldData = false,
        needRequest = false,
      ).collect {
        if (it != nowLesson) {
          nowLesson = it
          emit(it)
        }
      }
    }
  }

  @Composable
  override fun CoursePage(nextContent: @Composable (() -> Unit)) {
    CoursePageCompose(nextContent)
  }
}

@Composable
private fun SelfLessonDecorationViewModel.CoursePageCompose(
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
private fun SelfLessonDecorationViewModel.DayOfWeekCompose(
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