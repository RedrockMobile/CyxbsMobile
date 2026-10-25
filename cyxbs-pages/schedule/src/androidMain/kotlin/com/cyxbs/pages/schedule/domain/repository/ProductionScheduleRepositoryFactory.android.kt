package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.schedule.data.local.room3.KtorScheduleRepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleRoomDatabase
import com.cyxbs.pages.schedule.data.local.room3.ScheduleRepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.buildScheduleRoomDatabase
import com.cyxbs.pages.schedule.data.remote.ScheduleApiService
import com.cyxbs.pages.schedule.data.remote.KtorScheduleGateway
import kotlin.time.Clock

/**
 * Android production 使用进程唯一 Room 数据库和账号绑定的 Schedule gateway。
 *
 * 旧数据迁移依赖真实 local-first 仓库保存确定性 ID 与 pending，因此上线实现不能继续使用验收阶段的内存 mock。
 */
actual fun createProductionScheduleRepositoryFactory(
  clock: Clock,
): ScheduleRepositoryFactory = createAndroidRoomScheduleRepositoryFactory(
  database = AndroidScheduleRoomDatabaseOwner.database,
  clock = clock,
)

/**
 * 从指定数据库与墙钟组装 Android Schedule Room 工厂。
 *
 * [gatewayFactory] 默认从 KtProvider 获取 Ktorfit API，并绑定当前 [AccountSession]；测试可注入纯内存 fake
 * 验证会话、数据库和时间组装。墙钟只在 repository 执行业务命令时读取，工厂创建保持零 I/O。
 */
internal fun createAndroidRoomScheduleRepositoryFactory(
  database: ScheduleRoomDatabase,
  clock: Clock,
  gatewayFactory: (AccountSession) -> ScheduleRepositoryGateway = { session ->
    KtorScheduleRepositoryGateway(KtorScheduleGateway(ScheduleApiService::class.impl(), session))
  },
): ScheduleRepositoryFactory = RoomScheduleRepositoryFactory(
  database = database,
  gatewayFactory = gatewayFactory,
  nowMillis = { clock.now().toEpochMilliseconds() },
)

/** Android 进程唯一的 Schedule Room 数据库 owner；账号切换不会关闭或重建数据库。 */
private object AndroidScheduleRoomDatabaseOwner {
  /** 使用 application context 惰性创建业务数据库，避免 Activity 泄漏或无请求时提前打开文件。 */
  val database: ScheduleRoomDatabase by lazy { buildScheduleRoomDatabase(appContext) }
}
