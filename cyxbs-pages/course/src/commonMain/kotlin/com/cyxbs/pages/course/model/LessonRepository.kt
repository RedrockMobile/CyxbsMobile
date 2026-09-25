package com.cyxbs.pages.course.model

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.bean.StuLessonBean
import com.cyxbs.pages.course.network.CourseApiService
import com.cyxbs.pages.course.service.CourseIosPlatform
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
   * 解析课程仓库实际读写的数据源学号。
   *
   * 该替换仅用于 Debug 环境给无课程的测试账号提供稳定数据；缓存、网络请求和更新 Flow 必须全部
   * 使用解析后的同一学号，否则 Widget 展示与点击详情会分别命中两份数据。
   */
  private fun resolveDataStuNum(stuNum: String): String {
    return if (stuNum == "2020214988") "2024210480" else stuNum
  }

  private fun getLessonObserveFlow(stuNum: String): MutableSharedFlow<List<LessonByWeeks>> {
    return mLessonObserveFlowMap[stuNum] ?: synchronized(mLessonObserveFlowObject) {
      mLessonObserveFlowMap.getOrPut(stuNum) {
        MutableSharedFlow( // 事件类型，首次监听不会下发旧数据
          extraBufferCapacity = 1,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
      }
    }
  }

  /**
   * 观察课程
   * @param needOldData 是否需要第一次缓存课程的数据，如果缓存不存在时则会主动发起请求
   * @param forceRequest 是否强制请求一次新数据（掌邮时长较短，除了主页课表外一般情况下不需要主动去请求课程数据）
   */
  fun observeLesson(
    stuNum: String?,
    needOldData: Boolean = true,
    forceRequest: Boolean = false,
  ): Flow<List<LessonByWeeks>> {
    stuNum ?: return emptyFlow()
    val dataStuNum = resolveDataStuNum(stuNum)
    return getLessonObserveFlow(dataStuNum).onStart {
      var needRequest = forceRequest
      if (needOldData) {
        val cache = getCacheLesson(dataStuNum)
        if (cache != null) {
          emit(cache.data)
        } else {
          needRequest = true
        }
      }
      if (needRequest) {
        // 使用应用级别的协程去请求数据
        appCoroutineScope.launch { requestLesson(dataStuNum) }
      }
    }
  }

  /**
   * 获取课程缓存
   */
  fun getCacheLesson(stuNum: String?): ILessonService2.CacheLesson? {
    stuNum ?: return null
    val dataStuNum = resolveDataStuNum(stuNum)
    // 先取内存级缓存
    val cache = mLessonCache[dataStuNum]
    if (cache != null) return cache
    // 再读取磁盘
    val accountSettings = AccountSettings.get(dataStuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_LESSON)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<StuLessonBean>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_LESSON)
        if (isDebug()) toast("课表数据转换异常, ${it.message}")
      }.mapCatching { bean ->
        val requestTime = Instant.fromEpochMilliseconds(
          accountSettings.getLongOrNull(SETTING_KEY_LESSON_REQUEST_TIME)!!
        )
        val data = bean.data.mapNotNull { it.toLessonByWeeks() }
        ILessonService2.CacheLesson(requestTime, data)
      }.onSuccess {
        mLessonCache[dataStuNum] = it
      }.getOrNull()
    }
  }

  /**
   * 请求课程
   */
  suspend fun requestLesson(stuNum: String): Result<List<LessonByWeeks>> {
    val dataStuNum = resolveDataStuNum(stuNum)
    val requestTime = Clock.System.now()
    return runCatchingCoroutine {
      CourseApiService::class.impl().getStuLesson(dataStuNum)
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.onSuccess {
      // 设置 nowWeek
      SchoolCalendar.updateFirstCalendar(it.nowWeek)
    }.onSuccess {
      // 保存进磁盘
      val accountSettings = AccountSettings.get(dataStuNum)
      val stuLessonBeanJson= defaultJson.encodeToString<StuLessonBean>(it)
      accountSettings.putLong(SETTING_KEY_LESSON_REQUEST_TIME, requestTime.toEpochMilliseconds())
      accountSettings.putString(SETTING_KEY_LESSON, stuLessonBeanJson)
      // 同步给 iOS 原生侧（仅 iOS 实现，其它平台 implOrNull 直接返回 null）。
      // 目前用于让 CyxbsWidgetExtension 课表小组件读到最新的 App Group 共享缓存。
      CourseIosPlatform::class.implOrNull()?.onLessonUpdated(
        stuNum = dataStuNum,
        nowWeek = it.nowWeek,
        stuLessonBeanJson = stuLessonBeanJson,
      )
    }.onSuccess { bean ->

    }.mapCatching { bean ->
      bean.data.mapNotNull { it.toLessonByWeeks() }
    }.onSuccess {
      // 保存进内存
      val oldCache = mLessonCache[dataStuNum]
      mLessonCache[dataStuNum] = ILessonService2.CacheLesson(requestTime, it)
      if (oldCache?.data != it) {
        mLessonObserveFlowMap[dataStuNum]?.tryEmit(it)
      }
    }.onFailure {
      if (isDebug()) {
        toast("请求课表数据异常, ${it.message}")
        logg("请求课表数据异常: ${it.stackTraceToString()}")
      }
    }
  }
}
