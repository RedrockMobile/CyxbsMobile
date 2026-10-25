package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Clock

/**
 * Web 当前没有 Schedule 持久化与远端能力，因此返回显式只读的 unavailable façade。
 *
 * [clock] 仅为满足跨平台生产工厂签名；Web 实现不创建本地事实、网络请求、候选、队列或重试状态。
 */
actual fun createProductionScheduleRepositoryFactory(
  clock: Clock,
): ScheduleRepositoryFactory = ScheduleRepositoryFactory(::WebUnavailableScheduleRepository)

/** Web 平台能力缺席时的最小只读仓库；初始化和所有命令都不会触发 I/O。 */
private class WebUnavailableScheduleRepository(
  session: AccountSession,
) : ScheduleRepository {
  private val unavailable = ScheduleRemoteError.BackendNotDeployed
  private val mutableSnapshot = MutableStateFlow(
    ScheduleSnapshot(
      status = ScheduleRepositoryStatus.Unavailable(pendingCount = 0, error = unavailable),
      accountId = requireNotNull(session.accountId) {
        "WebUnavailableScheduleRepository requires a logged-in account"
      },
    ),
  )

  override val snapshot: StateFlow<ScheduleSnapshot> = mutableSnapshot
  override val mutationMode: ScheduleRepositoryMutationMode = ScheduleRepositoryMutationMode.READ_ONLY
  override val calendarChanges: Flow<ScheduleCalendarChange> = emptyFlow()

  /** 快照在构造时已经稳定可读；Web 初始化不读取存储也不访问网络。 */
  override suspend fun initialize() = Unit

  /** Web 不接受任何业务或同步命令，并明确标记本次没有尝试远端投递。 */
  override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult =
    ScheduleSyncResult.Failure(unavailable, attempted = false)
}
