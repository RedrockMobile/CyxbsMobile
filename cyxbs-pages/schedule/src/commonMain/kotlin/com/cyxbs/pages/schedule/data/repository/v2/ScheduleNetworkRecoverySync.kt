package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryAccountRequiredException
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 记录网络不可用到恢复的边沿，并只在恢复瞬间仍有本地临时提交时请求一次同步。
 *
 * 网络状态回调可能来自不同线程，因此内部只同步维护一个布尔状态；是否存在 pending 与实际同步均在锁外执行，
 * 避免把仓库读取或网络调用带进临界区。
 */
internal class ScheduleNetworkRecoverySyncTrigger(
  private val hasPending: () -> Boolean,
  private val requestSync: () -> Unit,
) {
  private val guard = SynchronizedObject()
  private var hasObservedUnavailable = false

  /**
   * 接收一次网络可用性变化。
   *
   * 首次直接收到可用状态不会触发同步；只有此前观察到不可用、随后恢复可用，且 [hasPending] 为真时才调用
   * [requestSync]。同一个恢复边沿最多触发一次。
   */
  fun onNetworkAvailabilityChanged(isAvailable: Boolean) {
    val isRecovery = synchronized(guard) {
      when {
        !isAvailable -> {
          hasObservedUnavailable = true
          false
        }

        hasObservedUnavailable -> {
          hasObservedUnavailable = false
          true
        }

        else -> false
      }
    }
    if (isRecovery && hasPending()) {
      requestSync()
    }
  }
}

/** 返回状态中尚未被远端确认的本地提交数量；初始化中或损坏状态不参与自动同步。 */
internal fun ScheduleRepositoryStatus.pendingMutationCount(): Int = when (this) {
  is ScheduleRepositoryStatus.Ready -> pendingCount
  is ScheduleRepositoryStatus.Recovered -> pendingCount
  is ScheduleRepositoryStatus.Unavailable -> pendingCount
  ScheduleRepositoryStatus.Loading,
  is ScheduleRepositoryStatus.Corrupted,
  -> 0
}

/**
 * 在平台确认网络恢复后发起一次现有 Sync。
 *
 * 真正进入协程时会再次检查 pending，避免等待调度期间其他响应已经清空临时提交。若同时发生登出或切号，稳定代理
 * 会拒绝本次调用；新账号仍由自己的初始化 Sync 负责，不需要补偿。
 */
internal fun requestScheduleSyncIfPending(
  repository: ScheduleRepository,
  scope: CoroutineScope,
) {
  scope.launch {
    if (repository.snapshot.value.status.pendingMutationCount() == 0) return@launch
    try {
      repository.execute(ScheduleCommand.RequestSync)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (_: ScheduleRepositoryAccountRequiredException) {
      // 网络恢复回调与登出/切号并发时无需同步；新的账号初始化会自行完成首次 Sync。
    }
  }
}

/**
 * 绑定平台已有的网络恢复信号。
 *
 * 支持可靠网络回调的平台负责把离线到在线事件交给 [ScheduleNetworkRecoverySyncTrigger]；没有现成信号的平台保持
 * 无操作，不能通过轮询或额外重试状态模拟网络恢复。
 */
internal expect fun bindScheduleNetworkRecoverySync(
  repository: ScheduleRepository,
  scope: CoroutineScope,
)
