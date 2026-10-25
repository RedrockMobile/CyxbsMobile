package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository

/**
 * 初始化 callback 在仓库 mutex 内完成注册后交还的单次启动令牌。
 *
 * callback 只能安装与本次初始化绑定的平台 entry，不能启动平台读取或导出；同一 `initialize()` 在 `withLock` 正常
 * 返回、mutex 已释放后才调用 [releaseAfterInitializationMutex]。重复调用以及已被 replacement 取代的 handoff
 * 都必须无副作用，接口不暴露 repository、Settings 或 EventKit authority。
 */
internal fun interface ScheduleRepositoryInitializationHandoff {
  fun releaseAfterInitializationMutex()
}

/** 没有平台导出能力的目标返回此 inert handoff，保持初始化后续流程一致。 */
internal val NoOpScheduleRepositoryInitializationHandoff =
  ScheduleRepositoryInitializationHandoff { }

/**
 * 在 Schedule 仓库初始化 mutex 内注册当前账号的平台日历导出。
 *
 * [repository] 与 [session] 均冻结在同一次初始化内。实现只能同步安装 exact binding 并返回 one-shot handoff；真正
 * start/reconcile 必须由调用方在 mutex 释放后调用 handoff，不能反向触发 [ScheduleRepository.initialize]。
 */
internal expect suspend fun onScheduleRepositoryInitialized(
  repository: ScheduleRepository,
  session: AccountSession,
): ScheduleRepositoryInitializationHandoff
