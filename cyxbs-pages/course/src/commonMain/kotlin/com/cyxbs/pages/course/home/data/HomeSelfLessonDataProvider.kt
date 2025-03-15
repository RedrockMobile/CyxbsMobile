package com.cyxbs.pages.course.home.data

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.SelfLessonItem
import com.cyxbs.pages.course.view.data.CourseDataProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/10
 */
class HomeSelfLessonDataProvider : CourseDataProvider() {

  init {
    IAccountService::class.impl()
      .userInfo
      .flatMapLatest {
        createLessonFlow(it)
      }.onEach {
        resetData(it)
      }.launchIn(appCoroutineScope)
  }

  private fun createLessonFlow(user: UserInfo?): Flow<List<LessonByWeeks>?> = flow {
    val lessonService = ILessonService2::class.impl()
    if (user == null) {
      emit(null)
    } else {
      val cacheLesson = lessonService.getLesson(user.stuNum)
      logg("cacheLesson = $cacheLesson")
      if (cacheLesson != null) {
        emit(cacheLesson.data)
      }
      lessonService.requestLesson(user.stuNum).onSuccess {
        logg("requestLesson = $it")
        emit(it)
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
    }
  }

  private fun resetData(data: List<LessonByWeeks>?) {
    clear()
    data ?: return
    data.forEach { lesson ->
      // 添加进整学期
      add(SelfLessonItem(0, lesson))
      // 添加进每周
      lesson.week.forEach { week ->
        add(SelfLessonItem(week, lesson))
      }
    }
  }
}