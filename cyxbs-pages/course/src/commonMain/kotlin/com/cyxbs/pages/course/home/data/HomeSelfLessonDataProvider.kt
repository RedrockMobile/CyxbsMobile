package com.cyxbs.pages.course.home.data

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.SelfLessonItemFactory
import com.cyxbs.pages.course.home.item.SelfLessonItem
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.view.data.CourseDataProvider
import com.cyxbs.pages.course.view.item.CourseItemWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/10
 */
object HomeSelfLessonDataProvider : CourseDataProvider<SelfLessonItem>() {

  private val itemFactory = SelfLessonItemFactory.get()

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .flatMapLatest {
        createLessonFlow(it)
      }.onEach {
        resetData(it)
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

  private fun resetData(data: List<LessonByWeeks>?) {
    clear() // 对于课程来说每一次更新可以使用全量更新
    data ?: return
    data.forEach { lesson ->
      // 添加进整学期
//      add(itemFactory.createSelfLessonItem(0, lesson))
      // 添加进每周
      lesson.week.forEach { week ->
//        add(itemFactory.createSelfLessonItem(week, lesson))
      }
    }
  }

  override fun compare(
    a: CourseItemWrapper<SelfLessonItem>,
    b: CourseItemWrapper<SelfLessonItem>,
  ): Int {
    if (a.page == 0 && b.page == 0) {
      val weekDiff = a.item.lesson.week.first() - b.item.lesson.week.first()
      return if (weekDiff != 0) -weekDiff else super.compare(a, b)
    }
    return super.compare(a, b)
  }
}