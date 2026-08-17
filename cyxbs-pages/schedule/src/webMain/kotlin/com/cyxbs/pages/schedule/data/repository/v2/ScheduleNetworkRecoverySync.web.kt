package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope

/** Web 当前为 READ_ONLY unavailable façade，不会产生本地临时提交，因此无需绑定网络恢复同步。 */
@Suppress("UNUSED_PARAMETER")
internal actual fun bindScheduleNetworkRecoverySync(
  repository: ScheduleRepository,
  scope: CoroutineScope,
) = Unit
