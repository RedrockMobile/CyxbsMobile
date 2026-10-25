package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/**
 * 创建 iOS 平台的 Schedule Room3 业务数据库。
 *
 * [path] 由调用方指定；生产环境使用应用支持目录下的 `schedule/schedule.db` 文件，既保持 Schedule 数据独立，
 * 也避免开发期同为 version 1 的旧结构绕过 destructive migration 后触发 Room identity 校验失败。旧库尚未上线且
 * 不再读取；远端保有完整日程，本地临时数据按产品约定允许丢弃。
 */
@OptIn(ExperimentalForeignApi::class)
fun buildScheduleRoomDatabase(
  path: String,
): ScheduleRoomDatabase {
  // SQLite 不会主动创建缺失的父目录；生产路径和测试注入路径都在打开数据库前统一准备目录。
  val parentDirectory = path.substringBeforeLast('/', missingDelimiterValue = "")
  if (
    parentDirectory.isNotEmpty() &&
    !NSFileManager.defaultManager.fileExistsAtPath(parentDirectory)
  ) {
    check(
      NSFileManager.defaultManager.createDirectoryAtPath(
        path = parentDirectory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
    ) { "无法创建 Schedule 数据库目录：$parentDirectory" }
  }
  return Room.databaseBuilder<ScheduleRoomDatabase>(
    name = path,
  ).setDriver(bundledScheduleRoomDriver())
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
}
