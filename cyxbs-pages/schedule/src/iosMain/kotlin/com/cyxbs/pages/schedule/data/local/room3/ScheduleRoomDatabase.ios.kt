package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.Room
import platform.Foundation.NSHomeDirectory

/**
 * 创建 iOS 平台的 Schedule Room3 业务数据库。
 *
 * [path] 未传入时使用应用 Home 目录的独立业务文件，且不兼容旧 P0 probe/Settings 数据。Schedule v2 尚未上线，旧开发
 * 库须显式清除后重建；此处不注册 migration 或 destructive fallback，避免伪造不兼容 schema 的升级路径。
 */
fun buildScheduleRoomDatabase(path: String = "${NSHomeDirectory()}/schedule-room3.db"): ScheduleRoomDatabase =
  Room.databaseBuilder<ScheduleRoomDatabase>(
    name = path,
  ).setDriver(bundledScheduleRoomDriver())
    .build()
