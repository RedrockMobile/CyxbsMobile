package com.cyxbs.pages.schedule.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

/** Controller 开启动作的纯 Android host 合同测试。 */
class ScheduleCalendarExportControllerDelegationTest {
  /** 权限齐全时先持久化开启，再且仅启动一次单向 worker。 */
  @Test
  fun grantedPermissionPersistsEnabledBeforeStartingWorker() {
    val effects = mutableListOf<String>()

    ScheduleCalendarExportController.applyEnableTransition(
      accountId = "20260001",
      permissionsGranted = true,
      persist = { accountId, enabled -> effects += "persist:$accountId:$enabled" },
      start = { effects += "start" },
    )

    assertEquals(listOf("persist:20260001:true", "start"), effects)
  }

  /** 权限缺失时明确保持关闭，且不得触发任何 Provider worker。 */
  @Test
  fun missingPermissionPersistsDisabledWithoutStartingWorker() {
    val effects = mutableListOf<String>()

    ScheduleCalendarExportController.applyEnableTransition(
      accountId = "20260001",
      permissionsGranted = false,
      persist = { accountId, enabled -> effects += "persist:$accountId:$enabled" },
      start = { effects += "start" },
    )

    assertEquals(listOf("persist:20260001:false"), effects)
  }

  /** 未登录没有可隔离的账号 scope，不能写设置或启动导出。 */
  @Test
  fun missingAccountHasNoSideEffects() {
    val effects = mutableListOf<String>()

    ScheduleCalendarExportController.applyEnableTransition(
      accountId = null,
      permissionsGranted = true,
      persist = { _, _ -> effects += "persist" },
      start = { effects += "start" },
    )

    assertEquals(emptyList(), effects)
  }
}
