package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.calendar.IosScheduleCalendarExportRuntimeRegistry
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import kotlinx.coroutines.Job

/**
 * 在 iOS Schedule repository 初始化 mutex 内安装 process-resident EventKit runtime binding。
 *
 * 兼容现有 suspend expect 入口；实际注册完全由 [registerIosScheduleCalendarExportInitialization] 同步完成，因此
 * Room production hook 与此入口取得相同的 exact-session handoff。返回后仍不能启动 runtime，调用方必须在 mutex
 * 正常释放后消费该 handoff。
 */
internal actual suspend fun onScheduleRepositoryInitialized(
  repository: ScheduleRepository,
  session: AccountSession,
): ScheduleRepositoryInitializationHandoff =
  registerIosScheduleCalendarExportInitialization(repository, session)

/**
 * 同步登记 iOS EventKit runtime 的 exact-session binding，并返回 post-mutex handoff。
 *
 * 函数只冻结 direct [repository]、完整 [session]、`accountCoroutineScopeFor(session)` 与 owner Job 并注册 entry；不读取
 * snapshot/偏好、不创建 gateway、不访问 EventKit。若账号、scope 或 owner 不可用就返回 inert handoff。返回值是既有 registry
 * 的 opaque one-shot token，只有同一次 Room `initialize()` 在 operationMutex 释放后调用时才允许 runtime start/reconcile。
 */
internal fun registerIosScheduleCalendarExportInitialization(
  repository: ScheduleRepository,
  session: AccountSession,
): ScheduleRepositoryInitializationHandoff {
  if (session.accountId == null) return NoOpScheduleRepositoryInitializationHandoff
  val accountService = IAccountService::class.impl()
  val scope = accountService.accountCoroutineScopeFor(session)
    ?: return NoOpScheduleRepositoryInitializationHandoff
  val owner = scope.coroutineContext[Job]
    ?: return NoOpScheduleRepositoryInitializationHandoff
  return IosScheduleCalendarExportRuntimeRegistry.register(
    accountService = accountService,
    repository = repository,
    session = session,
    scope = scope,
    owner = owner,
  )
}
