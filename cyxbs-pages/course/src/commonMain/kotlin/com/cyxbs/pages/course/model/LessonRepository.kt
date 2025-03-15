package com.cyxbs.pages.course.model

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.network.Network
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.bean.StuLessonBean
import com.cyxbs.pages.course.network.createCourseApiService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString

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

  fun getLesson(stuNum: String?): ILessonService2.CacheLesson? {
    stuNum ?: return null
    // 先取内存级缓存
    val cache = mLessonCache[stuNum]
    if (cache != null) return cache
    // 再读取磁盘
    val accountSettings = AccountSettings.get(stuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_LESSON)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<List<StuLessonBean.StuLesson>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_LESSON)
      }.mapCatching { list ->
        val requestTime = Instant.fromEpochMilliseconds(accountSettings.getLongOrNull(SETTING_KEY_LESSON_REQUEST_TIME)!!)
        val data = list.map { it.toLessonByWeeks() }
        ILessonService2.CacheLesson(requestTime, data)
      }.onSuccess {
        mLessonCache[stuNum] = it
      }.getOrNull()
    }
  }

  suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>> {
    val requestTime = Clock.System.now()
    return runCatchingCoroutine {
      Network.createCourseApiService().getStuLesson(stuNum)
    }.onSuccess {
      // 设置 nowWeek
      SchoolCalendar.updateFirstCalendar(it.nowWeek)
    }.onSuccess {
      // 保存进磁盘
      val accountSettings = AccountSettings.get(stuNum)
      accountSettings.putLong(SETTING_KEY_LESSON_REQUEST_TIME, requestTime.toEpochMilliseconds())
      accountSettings.putString(
        SETTING_KEY_LESSON,
        defaultJson.encodeToString<List<StuLessonBean.StuLesson>>(it.data)
      )
    }.mapCatching { bean ->
      bean.throwApiExceptionIfFail()
      bean.data.map { it.toLessonByWeeks() }
    }.onSuccess {
      // 保存进内存
      mLessonCache[stuNum] = ILessonService2.CacheLesson(requestTime, it)
    }
  }
}