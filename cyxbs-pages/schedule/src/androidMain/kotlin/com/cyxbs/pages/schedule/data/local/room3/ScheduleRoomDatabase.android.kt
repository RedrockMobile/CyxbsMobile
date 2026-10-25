package com.cyxbs.pages.schedule.data.local.room3

import android.content.Context
import androidx.room3.Room

/**
 * 创建 Android 平台的 Schedule Room3 业务数据库。
 *
 * 数据库存放在应用私有目录。正式结构使用新的 `schedule.db` 文件，避免开发期同为 version 1 的旧结构绕过
 * destructive migration 后触发 Room identity 校验失败。旧库尚未上线且不再读取；远端保有完整日程，本地临时数据
 * 按产品约定允许丢弃。调用方应长期持有结果，并只在明确生命周期结束后调用 [closeScheduleRoomDatabase]。
 */
fun buildScheduleRoomDatabase(context: Context): ScheduleRoomDatabase =
  Room.databaseBuilder<ScheduleRoomDatabase>(
    context = context.applicationContext,
    name = "schedule.db",
  ).setDriver(bundledScheduleRoomDriver())
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
