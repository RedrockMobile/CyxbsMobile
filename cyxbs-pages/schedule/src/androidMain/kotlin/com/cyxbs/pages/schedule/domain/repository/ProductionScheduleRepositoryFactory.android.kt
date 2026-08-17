package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.schedule.data.local.room3.KtorScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleRoomDatabase
import com.cyxbs.pages.schedule.data.local.room3.ScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.buildScheduleRoomDatabase
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleV2ApiService
import com.cyxbs.pages.schedule.data.remote.v3.KtorScheduleV2Gateway
import kotlin.time.Clock

/**
 * 创建 Android 生产环境的 Schedule v2 Room 仓库工厂。
 *
 * 工厂复用进程唯一数据库，并在每次 [ScheduleRepositoryFactory.create] 时把同一个不可变账号会话绑定到 Ktor
 * gateway。构造工厂或 repository 只组装依赖，不发起网络请求，也不再注册冲突、旧 semantic runner 或日历
 * initialized hook；单向日历初始化由 common 账号切换层统一负责。
 */
actual fun createProductionScheduleRepositoryFactory(
  clock: Clock,
): ScheduleRepositoryFactory = createAndroidRoomScheduleRepositoryFactory(
  database = AndroidScheduleRoomDatabaseOwner.database,
  clock = clock,
)

/**
 * 从指定数据库与墙钟组装 Android Schedule v2 Room 工厂。
 *
 * [gatewayFactory] 默认从 KtProvider 获取 Ktorfit API，并绑定当前 [AccountSession]；测试可注入纯内存 fake
 * 验证会话、数据库和时间组装。墙钟只在 repository 执行业务命令时读取，工厂创建保持零 I/O。
 */
internal fun createAndroidRoomScheduleRepositoryFactory(
  database: ScheduleRoomDatabase,
  clock: Clock,
  gatewayFactory: (AccountSession) -> ScheduleV2RepositoryGateway = { session ->
    KtorScheduleV2RepositoryGateway(KtorScheduleV2Gateway(ScheduleV2ApiService::class.impl(), session))
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
