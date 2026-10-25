package com.cyxbs.pages.schedule.data.local.room3

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Schedule 的 Room3 业务数据库。
 *
 * 当前客户端尚未发布，因此以 version 1 固定当前无 batch 的双快照 schema，不保留开发期间的历史版本或迁移。
 * 各平台 builder 在 schema 不匹配且没有迁移时直接清库重建；远端保有完整数据，本地临时日程也允许丢弃。
 * 数据库只存在于 noWeb source set；Web 不得引用本类或 bundled SQLite。
 */
@Database(
  entities = [
    ScheduleAccountMetadataEntity::class,
    ScheduleCategoryStateEntity::class,
    ScheduleStateEntity::class,
    ScheduleOccurrenceAdjustmentStateEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
@ColumnTypeConverters(ScheduleRoomConverters::class)
@ConstructedBy(ScheduleRoomDatabaseConstructor::class)
abstract class ScheduleRoomDatabase : RoomDatabase() {
  /** 返回新协议的 remote/pending 双快照 DAO；业务合并与网络接线在 repository 层实现。 */
  abstract fun scheduleDao(): ScheduleRoomDao
}

/** Room3 KSP 生成的业务数据库构造器声明，不能手写 actual 实现。 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ScheduleRoomDatabaseConstructor : RoomDatabaseConstructor<ScheduleRoomDatabase> {
  override fun initialize(): ScheduleRoomDatabase
}

/**
 * 创建跨 Android、Desktop 与 iOS 一致的 bundled SQLite driver。
 *
 * 每个 builder 独立创建 driver；调用方负责持有和关闭对应数据库，不能跨平台共享实例。
 */
internal fun bundledScheduleRoomDriver(): BundledSQLiteDriver = BundledSQLiteDriver()

/**
 * 关闭业务数据库。
 *
 * close 不可逆，仅供测试与明确生命周期结束的 owner 使用；长期 repository 不应在仍有消费者时调用。
 */
fun ScheduleRoomDatabase.closeScheduleRoomDatabase() = close()
