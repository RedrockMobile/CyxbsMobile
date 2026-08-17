package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.data.local.room3.IosScheduleRoomDatabaseOwner
import com.cyxbs.pages.schedule.data.local.room3.IosScheduleRoomDatabaseResources
import com.cyxbs.pages.schedule.data.local.room3.KtorScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.remote.v3.KtorScheduleV2Gateway
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleV2ApiService
import kotlin.time.Clock

/**
 * 创建 iOS 生产环境的 Schedule v2 Room repository 工厂。
 *
 * Factory 只绑定进程级数据库、墙钟和 exact-session v3 gateway；创建 factory 或 facade 不会执行网络、生成设备身份，
 * 也不会注册日历回调。账号切换后的平台日历初始化统一由 common AccountSwitching 层调用
 * `onScheduleRepositoryInitialized` 处理。
 */
actual fun createProductionScheduleRepositoryFactory(
  clock: Clock,
): ScheduleRepositoryFactory = createIosRoomScheduleRepositoryFactory(
  resources = IosScheduleRoomDatabaseOwner.resources,
  clock = clock,
)

/**
 * 从固定 iOS Room 资源创建可隔离测试的 v3 factory。
 *
 * [gatewayFactory] 只接收本次 facade 的 exact [AccountSession]；默认从 KtProvider 获取 Ktorfit API。平台日历
 * 初始化由账号 façade 统一触发，这里不再携带额外 hook。
 */
internal fun createIosRoomScheduleRepositoryFactory(
  resources: IosScheduleRoomDatabaseResources,
  clock: Clock,
  gatewayFactory: (AccountSession) -> ScheduleV2RepositoryGateway = { session ->
    KtorScheduleV2RepositoryGateway(KtorScheduleV2Gateway(ScheduleV2ApiService::class.impl(), session))
  },
): ScheduleRepositoryFactory = RoomScheduleRepositoryFactory(
  database = resources.database,
  gatewayFactory = gatewayFactory,
  nowMillis = { clock.now().toEpochMilliseconds() },
)
