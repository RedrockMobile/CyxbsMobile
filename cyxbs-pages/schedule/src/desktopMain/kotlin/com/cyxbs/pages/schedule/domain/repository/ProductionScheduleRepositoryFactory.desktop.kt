package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.data.local.room3.DesktopScheduleRoomDatabaseOwner
import com.cyxbs.pages.schedule.data.local.room3.DesktopScheduleRoomDatabaseResources
import com.cyxbs.pages.schedule.data.local.room3.KtorScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.local.room3.RoomScheduleRepositoryFactory
import com.cyxbs.pages.schedule.data.local.room3.ScheduleV2RepositoryGateway
import com.cyxbs.pages.schedule.data.remote.v3.KtorScheduleV2Gateway
import com.cyxbs.pages.schedule.data.remote.v3.ScheduleV2ApiService
import kotlin.time.Clock

/**
 * 创建 Desktop 生产环境的 Room3 local-first 仓库工厂。
 *
 * 该入口复用进程级数据库，只在 [ScheduleRepositoryFactory.create] 时冻结账号并构造 typed gateway；创建本身不
 * 发起网络请求。Schedule ID、本地 revision 与旧设备身份都不属于平台网络组装参数。
 */
actual fun createProductionScheduleRepositoryFactory(
  clock: Clock,
): ScheduleRepositoryFactory = createDesktopRoomScheduleRepositoryFactory(
  resources = DesktopScheduleRoomDatabaseOwner.resources,
  clock = clock,
)

/**
 * 从固定 Desktop 数据库资源组装最终 Room v2 repository。
 *
 * [gatewayFactory] 每次接收调用方传入的 exact [AccountSession]；测试可用它验证账号绑定和初始化的一次 Sync，
 * 生产默认值从 KtProvider 获取 Ktorfit API，不增加 Schedule 私有网络栈、队列或 receipt。
 */
internal fun createDesktopRoomScheduleRepositoryFactory(
  resources: DesktopScheduleRoomDatabaseResources,
  clock: Clock,
  gatewayFactory: (AccountSession) -> ScheduleV2RepositoryGateway = { session ->
    KtorScheduleV2RepositoryGateway(KtorScheduleV2Gateway(ScheduleV2ApiService::class.impl(), session))
  },
): ScheduleRepositoryFactory = RoomScheduleRepositoryFactory(
  database = resources.database,
  gatewayFactory = gatewayFactory,
  nowMillis = { clock.now().toEpochMilliseconds() },
)
