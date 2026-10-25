package com.cyxbs.components.config.time

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.components.init.appCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * 不触发 Compose 状态订阅的当天日期。
 *
 * 该值由 [Today] 的跨日协程更新；只需要读取当前日期、不希望订阅重组时使用。
 */
var TodayNoEffect: Date = Clock.System.todayIn(TimeZone.currentSystemDefault()).toDate()
  private set

/**
 * 可被 Compose 观察的当天日期。
 *
 * 首次访问时启动跨日更新协程。它与 [Date] 的纯日期运算分文件存放，避免协议转换和 JVM
 * 单元测试仅使用 [Date] 时就初始化 Android Application、主线程 Looper 等运行时依赖。
 */
val Today by mutableStateOf(TodayNoEffect).apply {
  appCoroutineScope.launch {
    while (true) {
      val now = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time.toMillisecondOfDay().milliseconds
      delay(1.days - now)
      value = TodayNoEffect.plusDays(1)
      TodayNoEffect = value
    }
  }
}
