package com.cyxbs.pages.course.api

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/14
 */
interface ILessonService2 {

  /**
   * 有缓存则先回调获取缓存，然后触发网络请求
   * 如果请求失败，则不回调
   * 请求成功则与缓存进行比对，发生改变时才回调
   */
  fun getAndRequestLesson(stuNum: String): Flow<List<LessonByWeeks>>

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