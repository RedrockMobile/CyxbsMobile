@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.cyxbs.pages.schedule.data.local.room3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

/**
 * iOS Room3 owner 的隔离资源测试。
 *
 * 所有数据库路径仅指向系统临时目录；测试不读取 NSUserDefaults、真实业务数据库或 EventKit。
 */
class IosScheduleRoomDatabaseOwnerTest {
  /** 资源必须只由注入路径构造，并在首次访问时打开独立 Room 数据库。 */
  @Test
  fun resourcesUseInjectedPathAndOpenDatabaseLazily() {
    val databasePath = temporaryDatabasePath()
    val resources = IosScheduleRoomDatabaseResources(databasePath)
    try {
      assertEquals(databasePath, resources.databasePath)
      assertNotNull(resources.database)
    } finally {
      resources.database.closeScheduleRoomDatabase()
      NSFileManager.defaultManager.removeItemAtPath(databasePath, error = null)
      NSFileManager.defaultManager.removeItemAtPath("$databasePath-wal", error = null)
      NSFileManager.defaultManager.removeItemAtPath("$databasePath-shm", error = null)
    }
  }

  /** 生成仅供本测试使用的绝对临时数据库路径，不会命中生产 Home 文件。 */
  private fun temporaryDatabasePath(): String =
    "${NSTemporaryDirectory()}schedule-room3-ios-test-${Uuid.random()}.db"
}
