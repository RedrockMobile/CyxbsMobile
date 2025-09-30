package com.cyxbs.pages.course.model

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.bean.StuLessonBean
import com.cyxbs.pages.course.network.CourseApiService
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
object LessonRepository {

  // 保存进 AccountSettings 中的 key
  private const val SETTING_KEY_LESSON = "lesson"
  private const val SETTING_KEY_LESSON_REQUEST_TIME = "lesson_request_time"

  private val mLessonCache = mutableMapOf<String, ILessonService2.CacheLesson>()

  private val mLessonObserveFlowObject = SynchronizedObject()
  private val mLessonObserveFlowMap = mutableMapOf<String, MutableSharedFlow<List<LessonByWeeks>>>()

  /**
   * 观察课程
   * @param needCache 是否需要第一次缓存课程的数据，如果缓存不存在时则会主动发起请求
   * @param needRequest 是否需要发起请求更新课表数据（掌邮时长较短，除了主页课表外一般情况下不需要主动去请求课程数据）
   */
  fun observeLesson(
    stuNum: String?,
    needCache: Boolean = true,
    needRequest: Boolean = false,
  ): Flow<List<LessonByWeeks>> {
    stuNum ?: return emptyFlow()
    return synchronized(mLessonObserveFlowObject) {
      mLessonObserveFlowMap.getOrPut(stuNum) {
        MutableSharedFlow(
          extraBufferCapacity = 1,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
      }
    }.onStart {
      var needRequestLocal = needRequest
      if (needCache) {
        val cache = getCacheLesson(stuNum)
        if (cache != null) {
          emit(cache.data)
        } else {
          needRequestLocal = true
        }
      }
      if (needRequestLocal) {
        // 使用应用级别的协程去请求数据
        appCoroutineScope.launch { requestLesson(stuNum) }
      }
    }
  }

  /**
   * 获取课程缓存
   */
  fun getCacheLesson(stuNum: String?): ILessonService2.CacheLesson? {
    stuNum ?: return null
    // 先取内存级缓存
    val cache = mLessonCache[stuNum]
    if (cache != null) return cache
    // 再读取磁盘
    val accountSettings = AccountSettings.get(stuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_LESSON)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<StuLessonBean>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_LESSON)
        if (isDebug()) toast("课表数据转换异常, ${it.message}")
      }.mapCatching { bean ->
        val requestTime = Instant.fromEpochMilliseconds(accountSettings.getLongOrNull(SETTING_KEY_LESSON_REQUEST_TIME)!!)
        val data = bean.data.mapNotNull { it.toLessonByWeeks() }
        ILessonService2.CacheLesson(requestTime, data)
      }.onSuccess {
        mLessonCache[stuNum] = it
      }.getOrNull()
    }
  }

  /**
   * 请求课程
   */
  suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>> {
    val requestTime = Clock.System.now()
    return runCatchingCoroutine {
      CourseApiService::class.impl().getStuLesson(stuNum)
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.onSuccess {
      // 设置 nowWeek
      SchoolCalendar.updateFirstCalendar(it.nowWeek)
    }.onSuccess {
      // 保存进磁盘
      val accountSettings = AccountSettings.get(stuNum)
      accountSettings.putLong(SETTING_KEY_LESSON_REQUEST_TIME, requestTime.toEpochMilliseconds())
      accountSettings.putString(SETTING_KEY_LESSON, defaultJson.encodeToString<StuLessonBean>(it))
    }.mapCatching { bean ->
      bean.data.mapNotNull { it.toLessonByWeeks() }
    }.onSuccess {
      // 保存进内存
      val oldCache = mLessonCache[stuNum]
      mLessonCache[stuNum] = ILessonService2.CacheLesson(requestTime, it)
      if (oldCache?.data != it) {
        mLessonObserveFlowMap[stuNum]?.tryEmit(it)
      }
    }
  }
}