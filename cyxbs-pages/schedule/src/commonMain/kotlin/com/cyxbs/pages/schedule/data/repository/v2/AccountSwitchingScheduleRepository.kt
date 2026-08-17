package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.pages.schedule.domain.calendar.onScheduleRepositoryInitialized
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryAccountRequiredException
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryFactory
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * 在登录账号变化时保持对象 identity 不变的 Schedule 仓库代理。
 *
 * 每个登录会话只绑定一个 delegate。切号会先发布新账号的 Loading 快照，再取消旧 delegate 的初始化与快照收集；
 * 所有异步输出在发布前还会校验来源 binding，因而旧账号的迟到快照和日历事件不会进入新账号视图。
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
class AccountSwitchingScheduleRepository internal constructor(
  private val factory: ScheduleRepositoryFactory,
  private val scope: CoroutineScope,
) : ScheduleRepository {
  /** 一个不可变账号会话对应的 delegate 及 façade 为它启动的任务。 */
  private class Binding(
    val session: AccountSession,
    val accountId: String,
    val delegate: ScheduleRepository,
  ) {
    var snapshotJob: Job? = null
    var initializationJob: Job? = null

    /** 账号失效后停止 façade 侧工作；已经进入 delegate 的命令不由这里强制取消。 */
    fun cancelBackgroundWork() {
      snapshotJob?.cancel()
      initializationJob?.cancel()
    }
  }

  /** binding 与其公开快照必须一起替换，避免切号期间短暂暴露旧账号事实。 */
  private data class Publication(
    val binding: Binding?,
    val snapshot: ScheduleSnapshot,
  )

  /** 日历事件携带来源 binding，经过 flatMapLatest 缓冲后仍可做最终身份校验。 */
  private data class BoundCalendarChange(
    val binding: Binding,
    val change: ScheduleCalendarChange,
  )

  private val guard = SynchronizedObject()
  private val publication = MutableStateFlow(Publication(null, ScheduleSnapshot()))
  private var authoritativeSessions: StateFlow<AccountSession>? = null

  /**
   * 当前账号快照。
   *
   * 同步读取 `value` 时会先对齐账号 StateFlow 的最新值，避免账号 collector 尚未获得调度时继续看到旧账号数据。
   */
  override val snapshot: StateFlow<ScheduleSnapshot> = object : StateFlow<ScheduleSnapshot> {
    override val value: ScheduleSnapshot
      get() {
        reconcileAuthoritativeSession()
        return publication.value.snapshot
      }

    override val replayCache: List<ScheduleSnapshot>
      get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<ScheduleSnapshot>): Nothing {
      reconcileAuthoritativeSession()
      publication
        .map { current: Publication -> current.snapshot }
        .distinctUntilChanged()
        .collect(collector)
      awaitCancellation()
    }
  }

  /** 当前 delegate 的写入模式；未登录或账号切换空窗稳定为只读。 */
  override val mutationMode: ScheduleRepositoryMutationMode
    get() = currentBinding()?.delegate?.mutationMode ?: ScheduleRepositoryMutationMode.READ_ONLY

  /**
   * 只转发当前精确 binding 的日历变化。
   *
   * flatMapLatest 可能已缓存旧流事件，因此下游交付前再次核验 binding；同时先补齐该 delegate 的最新快照，维持
   * “事件到达时对应事实已可读”的仓库约定。
   */
  override val calendarChanges: Flow<ScheduleCalendarChange> = publication
    .onStart { reconcileAuthoritativeSession() }
    .map { current: Publication -> current.binding }
    .distinctUntilChanged()
    .flatMapLatest { binding ->
      if (binding == null) {
        emptyFlow()
      } else {
        binding.delegate.calendarChanges.map { BoundCalendarChange(binding, it) }
      }
    }
    .mapNotNull { emitted ->
      reconcileAuthoritativeSession()
      if (!isCurrent(emitted.binding, emitted.change.accountId)) return@mapNotNull null
      publishSnapshot(emitted.binding, emitted.binding.delegate.snapshot.value)
      emitted.change.takeIf { isCurrent(emitted.binding, it.accountId) }
    }

  /**
   * 绑定唯一的账号权威流并立即协调其当前值。
   *
   * 该方法只能调用一次；后续会话变化由 façade 自己收集，调用方无需手动通知。
   */
  fun bindAccounts(sessions: StateFlow<AccountSession>) {
    synchronized(guard) {
      check(authoritativeSessions == null) { "Account sessions have already been bound" }
      authoritativeSessions = sessions
    }
    reconcileSession(sessions.value)
    scope.launch {
      sessions.collect(::reconcileSession)
    }
  }

  /**
   * 等待当前 binding 在登录时自动启动的初始化任务；登出状态没有需要初始化的数据源。
   *
   * façade 不重复调用 delegate，也不保存失败结果或创建恢复代次。切号会取消旧初始化任务，因此等待者不会让旧账号
   * 初始化继续占用资源。
   */
  override suspend fun initialize() {
    currentBinding()?.initializationJob?.join()
  }

  /**
   * 把命令直接交给调用开始时捕获的当前 delegate。
   *
   * 切号不会把已进入旧 delegate 的调用重定向到新账号；其快照与事件仍受 binding 发布门禁隔离。
   */
  override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? {
    val binding = currentBinding() ?: throw ScheduleRepositoryAccountRequiredException()
    return binding.delegate.execute(command)
  }

  /**
   * 将整组命令冻结到调用开始时的同一 delegate，避免批处理中途切号后把剩余命令发送到新账号。
   *
   * 账号切换后旧 delegate 可自行完成本次调用，但其异步输出不能通过 façade 的身份门禁。
   */
  override suspend fun executeSerially(
    commands: List<ScheduleCommand>,
    shouldContinue: () -> Boolean,
  ): List<ScheduleSyncResult?> {
    val binding = currentBinding() ?: throw ScheduleRepositoryAccountRequiredException()
    return binding.delegate.executeSerially(commands, shouldContinue)
  }

  /** 同步读取账号权威值，关闭 collector 调度滞后的旧账号窗口。 */
  private fun reconcileAuthoritativeSession() {
    val sessions = synchronized(guard) { authoritativeSessions } ?: return
    reconcileSession(sessions.value)
  }

  /** 返回当前精确 binding；账号变化时先完成一次最小切换。 */
  private fun currentBinding(): Binding? {
    reconcileAuthoritativeSession()
    return synchronized(guard) {
      val session = authoritativeSessions?.value ?: return@synchronized null
      publication.value.binding?.takeIf { it.session == session }
    }
  }

  /**
   * 原子替换账号 binding。
   *
   * 登出和游客都会发布空 Loading 快照；登录则先发布账号明确的 Loading，再于锁外启动快照收集和初始化。
   */
  private fun reconcileSession(session: AccountSession) {
    var created: Binding? = null
    synchronized(guard) {
      if (authoritativeSessions?.value != session) return
      val current = publication.value.binding
      if (current?.session == session || current == null && session.accountId == null &&
        publication.value.snapshot.accountId == null
      ) {
        return
      }

      current?.cancelBackgroundWork()
      val accountId = session.accountId
      if (accountId == null) {
        publication.value = Publication(null, ScheduleSnapshot())
      } else {
        Binding(session, accountId, factory.create(session)).also { binding ->
          created = binding
          publication.value = Publication(binding, ScheduleSnapshot(accountId = accountId))
        }
      }
    }
    created?.let(::startBinding)
  }

  /** 为已经公开的 binding 启动快照收集和一次自动初始化。 */
  private fun startBinding(binding: Binding) {
    val snapshotJob = scope.launch(start = CoroutineStart.LAZY) {
      binding.delegate.snapshot.collect { candidate -> publishSnapshot(binding, candidate) }
    }
    val initializationJob = scope.launch(start = CoroutineStart.LAZY) {
      try {
        binding.delegate.initialize()
        if (isCurrent(binding, binding.accountId)) {
          // 平台日历初始化只在 delegate 完成且仍属于当前账号后登记，并在 delegate 的锁外立即释放一次性令牌。
          onScheduleRepositoryInitialized(binding.delegate, binding.session)
            .releaseAfterInitializationMutex()
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Throwable) {
        // 初始化失败只属于此 delegate；后续账号变化仍应能创建并初始化新的 delegate。
      }
    }

    val accepted = synchronized(guard) {
      if (publication.value.binding !== binding || authoritativeSessions?.value != binding.session) {
        false
      } else {
        binding.snapshotJob = snapshotJob
        binding.initializationJob = initializationJob
        true
      }
    }
    if (accepted) {
      snapshotJob.start()
      initializationJob.start()
    } else {
      snapshotJob.cancel()
      initializationJob.cancel()
    }
  }

  /** 只有当前 binding 且账号匹配的快照才能进入 façade。 */
  private fun publishSnapshot(binding: Binding, candidate: ScheduleSnapshot) {
    if (candidate.accountId != binding.accountId) return
    synchronized(guard) {
      if (publication.value.binding === binding && authoritativeSessions?.value == binding.session) {
        publication.value = Publication(binding, candidate)
      }
    }
  }

  /** 日历事件必须同时匹配当前 binding identity 与账号。 */
  private fun isCurrent(binding: Binding, accountId: String): Boolean = synchronized(guard) {
    publication.value.binding === binding &&
      authoritativeSessions?.value == binding.session &&
      binding.accountId == accountId
  }
}
