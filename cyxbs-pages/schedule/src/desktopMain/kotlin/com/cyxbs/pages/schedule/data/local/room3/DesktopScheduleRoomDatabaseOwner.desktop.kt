package com.cyxbs.pages.schedule.data.local.room3

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir

/**
 * 可被生产 owner 与隔离测试复用的 Desktop Room3 数据库资源。
 *
 * 数据库路径在创建时固定，避免生产数据库悄然退回进程工作目录；测试可传入临时路径隔离业务数据。
 */
internal class DesktopScheduleRoomDatabaseResources(
  databasePath: String,
) {
  /** 使用固定业务路径懒打开数据库；调用方负责仅在进程退出时关闭生产实例。 */
  val database: ScheduleRoomDatabase by lazy {
    buildScheduleRoomDatabase(databasePath)
  }

}

/**
 * Desktop 进程唯一的 Schedule Room3 数据库 owner。
 *
 * 数据库只在首次生产 factory 组装时打开，并在 Desktop 进程生命周期内保持同一连接；账号切换或同账号的
 * repository 代次刷新仅重建 facade，绝不能关闭或重建此数据库。数据库路径固定在 [FileKit.filesDir]；初始化
 * 异常直接向调用方传播。
 */
internal object DesktopScheduleRoomDatabaseOwner {
  /**
   * 进程级资源；测试只能构造独立 [DesktopScheduleRoomDatabaseResources]，不能替换本生产实例。
   */
  internal val resources: DesktopScheduleRoomDatabaseResources by lazy {
    DesktopScheduleRoomDatabaseResources(
      databasePath = (FileKit.filesDir / DATABASE_FILE_NAME).absolutePath(),
    )
  }

  private const val DATABASE_FILE_NAME = "schedule-room3-production.db"
}
