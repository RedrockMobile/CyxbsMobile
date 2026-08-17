package com.cyxbs.pages.schedule.data.local.room3

import android.content.Context
import androidx.room3.Room

/**
 * 创建 Android 平台的 Schedule Room3 业务数据库。
 *
 * 数据库存放在应用私有目录。Schedule v2 尚未上线，当前 schema 采用 break change：已有开发数据库须由开发者清库
 * 后重建；本 builder 不注册伪 migration 或 destructive fallback。调用方应长期持有结果，并只在明确生命周期结束后调用
 * [closeScheduleRoomDatabase]。
 */
fun buildScheduleRoomDatabase(context: Context): ScheduleRoomDatabase =
  Room.databaseBuilder<ScheduleRoomDatabase>(
    context = context.applicationContext,
    name = "schedule-room3.db",
  ).setDriver(bundledScheduleRoomDriver())
    .build()
