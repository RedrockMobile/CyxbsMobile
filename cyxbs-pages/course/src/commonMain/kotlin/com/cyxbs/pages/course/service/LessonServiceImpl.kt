package com.cyxbs.pages.course.service

import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.model.LessonRepository
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
@ImplProvider
object LessonServiceImpl : ILessonService2 {

  override fun getLesson(stuNum: String): ILessonService2.CacheLesson? {
    return LessonRepository.getLesson(stuNum)
  }

  override suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>> {
    return LessonRepository.requestLesson(stuNum)
  }
}