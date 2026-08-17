package com.cyxbs.pages.schedule.data.local.room3

import platform.Foundation.NSHomeDirectory

/**
 * 可被 iOS 生产 owner 与隔离测试复用的 Room3 持久化资源。
 *
 * [databasePath] 在构造时固定。生产实例只在进程生命周期内惰性打开一次数据库；测试可注入临时路径，避免读取用户业务
 * 数据库或 EventKit。
 */
internal class IosScheduleRoomDatabaseResources(
  internal val databasePath: String,
) {
  /** 使用固定业务路径惰性打开数据库；生产实例只能由进程退出时的明确生命周期管理关闭。 */
  val database: ScheduleRoomDatabase by lazy {
    buildScheduleRoomDatabase(databasePath)
  }

}

/**
 * iOS 进程唯一的 Schedule Room3 数据库 owner。
 *
 * 数据库路径固定在应用 Home 的独立 production 文件，不兼容旧 P0 probe 或 Settings envelope。账号切换与同账号的
 * repository 代次刷新只创建新的 facade，绝不能关闭或重建数据库。数据库初始化失败会向调用者传播，禁止退回
 * Settings-backed repository。
 */
internal object IosScheduleRoomDatabaseOwner {
  /** 生产资源仅创建一次；测试必须自行构造 [IosScheduleRoomDatabaseResources]。 */
  internal val resources: IosScheduleRoomDatabaseResources by lazy {
    IosScheduleRoomDatabaseResources(
      databasePath = "${NSHomeDirectory()}/schedule-room3-production.db",
    )
  }
}
