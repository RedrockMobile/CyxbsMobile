package com.cyxbs.pages.course.api

import kotlinx.datetime.Instant

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/14
 */
interface ILessonService2 {

  /**
   * 获取课程
   * - 不存在缓存，则返回 null
   */
  fun getLesson(stuNum: String): CacheLesson?

  /**
   * 请求课程
   */
  suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>>

  class CacheLesson(
    val requestTime: Instant,
    val data: List<LessonByWeeks>,
  )
}