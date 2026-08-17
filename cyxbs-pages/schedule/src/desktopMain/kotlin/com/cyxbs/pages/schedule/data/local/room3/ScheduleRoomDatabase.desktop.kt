package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.Room

/**
 * 创建 Desktop 平台的 Schedule Room3 数据库。
 *
 * [path] 必须是调用方隔离的新业务数据库文件或临时路径，且不复用 P0 probe 文件。Schedule v2 尚未上线，旧开发库须
 * 显式清除后重建；此处不注册 migration 或 destructive fallback，避免伪造不兼容 schema 的升级路径。
 */
fun buildScheduleRoomDatabase(path: String): ScheduleRoomDatabase =
  Room.databaseBuilder<ScheduleRoomDatabase>(name = path)
    .setDriver(bundledScheduleRoomDriver())
    .build()
