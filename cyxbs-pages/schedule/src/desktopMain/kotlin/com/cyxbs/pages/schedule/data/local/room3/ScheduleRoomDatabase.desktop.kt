package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.Room

/**
 * 创建 Desktop 平台的 Schedule Room3 数据库。
 *
 * [path] 必须是调用方隔离的新业务数据库文件或临时路径，且不复用 P0 probe 文件。没有可用 migration 时直接清库
 * 重建；远端保有完整日程，本地临时数据按产品约定允许丢弃。
 */
fun buildScheduleRoomDatabase(path: String): ScheduleRoomDatabase =
  Room.databaseBuilder<ScheduleRoomDatabase>(name = path)
    .setDriver(bundledScheduleRoomDriver())
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
