package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Desktop 当前没有项目级可靠网络恢复信号，因此不建立轮询；首次初始化与用户主动同步仍走仓库既有入口。
 */
@Suppress("UNUSED_PARAMETER")
internal actual fun bindScheduleNetworkRecoverySync(
  repository: ScheduleRepository,
  scope: CoroutineScope,
) = Unit
