package com.cyxbs.pages.course.api

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/14
 */
interface ILessonService2 {

  /**
   * 观察课程
   * @param needCache 是否需要第一次缓存课程的数据，如果缓存不存在时则会主动发起请求
   * @param needRequest 是否需要发起请求更新课表数据（掌邮时长较短，除了主页课表外一般情况下不需要主动去请求课程数据）
   */
  fun observeLesson(
    stuNum: String?,
    needCache: Boolean = true,
    needRequest: Boolean = false,
  ): Flow<List<LessonByWeeks>>

  /**
   * 获取课程
   * - 不存在缓存，则返回 null
   */
  fun getCacheLesson(stuNum: String?): CacheLesson?

  /**
   * 请求课程
   */
  suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>>

  data class CacheLesson(
    val requestTime: Instant,
    val data: List<LessonByWeeks>,
  )
}