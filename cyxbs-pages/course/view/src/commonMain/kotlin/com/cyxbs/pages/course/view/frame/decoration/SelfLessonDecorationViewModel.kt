package com.cyxbs.pages.course.view.frame.decoration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.account.api.IAccountService
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * 自己的课程展示
 *
 * @author 985892345
 * @date 2025/10/12
 */
@Stable
class SelfLessonDecorationViewModel(
  val hierarchy: CourseItemHierarchy<CourseLessonItem>,
  val platformItemFactory: PlatformCourseLessonItemFactory,
) : BaseViewModel(), CoursePageDecoration {

  private val lessonService = ILessonService2::class.impl()

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .flatMapLatest {
        if (it == "2020214988") {
          // 老登因为没有课程，所以在这里单独 mock 成其他人的课程
          createLessonFlow("2024210480")
        } else createLessonFlow(it)
      }.onEach {
        hierarchy.reset(buildList {
          it?.forEach { lesson ->
            // 添加进整学期
            add(SelfLessonWhatTime(0, lesson, platformItemFactory))
            // 添加进每周
            lesson.week.forEach { week ->
              add(SelfLessonWhatTime(week, lesson, platformItemFactory))
            }
          }
        })
      }.catch {

      }.launchIn(viewModelScope)
  }

  private fun createLessonFlow(stuNum: String?): Flow<List<LessonByWeeks>?> = flow {
    if (stuNum == null) {
      emit(null)
    } else {
      // 先获取缓存
      val cacheLesson = lessonService.getCacheLesson(stuNum)
      var nowLesson = cacheLesson?.data
      if (nowLesson != null) {
        emit(nowLesson)
      }
      // 进行一次请求
      lessonService.requestLesson(stuNum).onSuccess {
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
      lessonService.observeLesson(
        stuNum = stuNum,
        needOldData = false,
        forceRequest = false,
      ).collect {
        if (it != nowLesson) {
          nowLesson = it
          emit(it)
        }
      }
    }
  }

  @Composable
  override fun CoursePageContent() {
    hierarchy.CoursePageItemListContent()
  }
}

private data class SelfLessonWhatTime(
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
      -((other as? SelfLessonWhatTime)?.lesson?.week?.get(0) ?: 0) // 起始周越小越在上
    }.compareBy(other) {
      -it.now.value.dayOfWeek.ordinal // dayOfWeek 越小越在上
    }.compareBy(other) {
      it.now.value.beginTime.value // beginTime 越大越在上
    }.compareBy(other) {
      it.now.value.finalTime.value // finalTime 越大越在上
    }
  }
}
