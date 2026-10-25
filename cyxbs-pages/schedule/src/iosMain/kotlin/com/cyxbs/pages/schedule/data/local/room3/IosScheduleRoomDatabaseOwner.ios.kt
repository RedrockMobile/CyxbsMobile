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
 * 数据库路径固定为应用支持目录下的 `schedule/schedule.db`，不再读取开发期同为 version 1 的旧
 * `schedule-room3-production.db`，避免旧结构绕过 destructive migration 后触发 Room identity 校验失败。
 * 账号切换与同账号的 repository 代次刷新只创建新的 facade，绝不能关闭或重建数据库。数据库初始化失败会向
 * 调用者传播，禁止退回 Settings-backed repository。
 */
internal object IosScheduleRoomDatabaseOwner {
  /** 生产资源仅创建一次；测试必须自行构造 [IosScheduleRoomDatabaseResources]。 */
  internal val resources: IosScheduleRoomDatabaseResources by lazy {
    IosScheduleRoomDatabaseResources(
      databasePath = "${NSHomeDirectory()}/Library/Application Support/schedule/schedule.db",
    )
  }
}
