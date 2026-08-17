package com.cyxbs.pages.schedule.domain.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Android 初始化 handoff 的最小单向启动合同测试。 */
class ScheduleCalendarExportInitializationHandoffTest {
  /** 初始化 mutex 释放后的启动动作只能执行一次，重复 release 不得重复建立 exporter。 */
  @Test
  fun releaseStartsExporterExactlyOnce() {
    var releases = 0
    val handoff = AndroidScheduleCalendarExportInitializationHandoff {
      releases += 1
    }

    handoff.releaseAfterInitializationMutex()
    handoff.releaseAfterInitializationMutex()

    assertEquals(1, releases)
  }

  /** 首次启动动作失败也会烧毁令牌，调用方重试 release 不得重复执行副作用。 */
  @Test
  fun failedReleaseIsStillOneShot() {
    var releases = 0
    val handoff = AndroidScheduleCalendarExportInitializationHandoff {
      releases += 1
      error("simulated exporter start failure")
    }

    assertFailsWith<IllegalStateException> { handoff.releaseAfterInitializationMutex() }
    handoff.releaseAfterInitializationMutex()

    assertEquals(1, releases)
  }
}
