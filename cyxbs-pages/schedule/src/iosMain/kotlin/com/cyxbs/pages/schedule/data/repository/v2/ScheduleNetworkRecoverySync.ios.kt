package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue

/**
 * 使用 iOS Network.framework 监听网络路径恢复，并复用公共 pending 判断与 Sync 入口。
 *
 * monitor 与调用方 [scope] 同生命周期；系统首次直接回调在线状态不会触发，只有明确观察到离线后再恢复才会请求。
 */
@OptIn(ExperimentalForeignApi::class)
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
  val monitor = nw_path_monitor_create()
  nw_path_monitor_set_update_handler(monitor) { path ->
    trigger.onNetworkAvailabilityChanged(
      nw_path_get_status(path) == nw_path_status_satisfied,
    )
  }
  nw_path_monitor_set_queue(
    monitor,
    dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u),
  )
  nw_path_monitor_start(monitor)
  ownerJob.invokeOnCompletion {
    nw_path_monitor_cancel(monitor)
  }
}
