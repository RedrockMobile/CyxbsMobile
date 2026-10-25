package com.cyxbs.pages.schedule.data.migration

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 旧数据迁移期限测试，确保保留的迁移代码在约定日期后不会再访问旧服务。 */
class LegacyScheduleMigrationWindowTest {

  /** 截止前仍允许迁移，截止瞬间及之后必须关闭入口。 */
  @Test
  fun migrationWindow_closesAtConfiguredDeadline() {
    assertTrue(
      LegacyScheduleMigrationCoordinator.isMigrationWindowOpen(
        Instant.parse("2028-08-31T23:59:59.999Z")
      )
    )
    assertFalse(
      LegacyScheduleMigrationCoordinator.isMigrationWindowOpen(
        LegacyScheduleMigrationCoordinator.MIGRATION_EXPIRES_AT
      )
    )
    assertFalse(
      LegacyScheduleMigrationCoordinator.isMigrationWindowOpen(
        Instant.parse("2028-09-01T00:00:00.001Z")
      )
    )
  }
}
