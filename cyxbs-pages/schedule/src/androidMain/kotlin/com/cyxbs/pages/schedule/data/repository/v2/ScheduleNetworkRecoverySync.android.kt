package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 使用 Android 已有的 [NetworkUtil] 网络状态绑定恢复同步。
 *
 * 监听与调用方 [scope] 同生命周期；账号切换恰好发生在恢复检查与执行之间时，新账号代理会拒绝旧调用，此处只忽略
 * 该正常竞争，不增加补偿或重试。
 */
internal actual fun bindScheduleNetworkRecoverySync(
  repository: ScheduleRepository,
  scope: CoroutineScope,
) {
  val ownerJob = scope.coroutineContext[Job] ?: return
  val trigger = ScheduleNetworkRecoverySyncTrigger(
    hasPending = {
      repository.snapshot.value.status.pendingMutationCount() > 0
    },
    requestSync = {
      requestScheduleSyncIfPending(repository, scope)
    },
  )
  val disposable = NetworkUtil.state.subscribe(
    trigger::onNetworkAvailabilityChanged,
    {
      // NetworkUtil 的状态流正常不会结束；若底层异常则停止本次监听，不建立隐藏重试循环。
    },
  )
  ownerJob.invokeOnCompletion {
    disposable.dispose()
  }
}
