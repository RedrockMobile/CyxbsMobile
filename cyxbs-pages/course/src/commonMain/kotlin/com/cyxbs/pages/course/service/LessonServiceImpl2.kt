package com.cyxbs.pages.course.service

import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.model.LessonRepository
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.Flow

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
@ImplProvider
object LessonServiceImpl2 : ILessonService2 {

  override fun observeLesson(
    stuNum: String?,
    needOldData: Boolean,
    forceRequest: Boolean
  ): Flow<List<LessonByWeeks>> {
    return LessonRepository.observeLesson(
      stuNum = stuNum,
      needOldData = needOldData,
      forceRequest = forceRequest,
    )
  }

  override fun getCacheLesson(stuNum: String?): ILessonService2.CacheLesson? {
    return LessonRepository.getCacheLesson(stuNum)
  }

  override suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>> {
    return LessonRepository.requestLesson(stuNum)
  }
}