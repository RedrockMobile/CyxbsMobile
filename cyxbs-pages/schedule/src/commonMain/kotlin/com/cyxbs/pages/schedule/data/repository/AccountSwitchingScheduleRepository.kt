package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.pages.schedule.domain.calendar.onScheduleRepositoryInitialized
import com.cyxbs.pages.schedule.domain.calendar.ScheduleRepositoryInitializationHandoff
import com.cyxbs.pages.schedule.domain.repository.ScheduleCalendarChange
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryAccountRequiredException
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryFactory
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleRemoteError
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
 * [repositoryInitialized] 默认执行平台日历与迁移初始化，测试可替换为空实现，避免 façade 单元测试访问平台全局状态。
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
class AccountSwitchingScheduleRepository internal constructor(
  private val factory: ScheduleRepositoryFactory,
  private val scope: CoroutineScope,
  private val repositoryInitialized: suspend (
    repository: ScheduleRepository,
    session: AccountSession,
  ) -> ScheduleRepositoryInitializationHandoff = ::onScheduleRepositoryInitialized,
) : ScheduleRepository {
  /** 一个不可变账号会话对应的 delegate 及 façade 为它启动的任务。 */
  private class Binding(
    val session: AccountSession,
    val accountId: String,
    val delegate: ScheduleRepository,
  ) {
    var snapshotJob: Job? = null
    var initializationJob: Job? = null
    var initializationCompleted: Boolean = false
    var initializationFailure: Throwable? = null

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

  /**
   * 当前精确账号 delegate 的写入模式；未登录、账号切换空窗或初始化已失败时为只读。
   *
   * delegate 一经绑定即可公开 local-first：真正的命令仍会在 [awaitCurrentBindingInitialization] 等待初始化完成。
   * 不能用 `initializationCompleted` 控制该普通 getter，否则初始化成功但没有新快照时 Compose 不会重组，页面会
   * 永久停留在首帧读取到的只读状态。
   */
  override val mutationMode: ScheduleRepositoryMutationMode
    get() {
      val binding = currentBinding() ?: return ScheduleRepositoryMutationMode.READ_ONLY
      return synchronized(guard) {
        if (
          publication.value.binding === binding &&
          binding.initializationFailure == null
        ) {
          binding.delegate.mutationMode
        } else {
          ScheduleRepositoryMutationMode.READ_ONLY
        }
      }
    }

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
   * 只清理调用开始时仍与 [expectedAccountId] 一致的 delegate。
   *
   * 设置页的服务端请求可能跨越一次账号切换，因此必须在 façade 内完成账号校验，不能误删随后登录账号的数据。
   */
  override suspend fun clearLocalAccountData(expectedAccountId: String) {
    val binding = currentBinding() ?: throw ScheduleRepositoryAccountRequiredException()
    check(binding.accountId == expectedAccountId) { "Schedule repository account changed before local clear" }
    binding.initializationJob?.join()
    check(isCurrent(binding, expectedAccountId)) { "Schedule repository account changed before local clear" }
    binding.delegate.clearLocalAccountData(expectedAccountId)
    if (isCurrent(binding, expectedAccountId)) {
      synchronized(guard) {
        // 清空是损坏状态的显式恢复入口；delegate 已发布可信空快照后，façade 必须同步解除只读门禁。
        binding.initializationCompleted = true
        binding.initializationFailure = null
      }
      publishSnapshot(binding, binding.delegate.snapshot.value)
    }
  }

  /**
   * 等待当前 delegate 完成初始化后，再把命令交给调用开始时捕获的同一 delegate。
   *
   * 失败记录页等独立入口可能在自动初始化尚未结束时就发起编辑；façade 必须在统一边界等待初始化，不能把
   * Room delegate 的内部时序要求泄漏给每个 UI。等待期间若发生切号，本次命令会被拒绝而不是重定向到新账号；
   * 已经进入 delegate 的调用仍可在原账号完成，其快照与事件继续受 binding 发布门禁隔离。
   */
  override suspend fun execute(command: ScheduleCommand): ScheduleSyncResult? {
    val readiness = awaitCurrentBindingInitialization()
    return readiness.failure?.let { failure ->
      ScheduleSyncResult.Failure(ScheduleRemoteError.InvalidResponse(failure), attempted = false)
    } ?: readiness.binding.delegate.execute(command)
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
    val readiness = awaitCurrentBindingInitialization()
    return readiness.failure?.let { failure ->
      val result = ScheduleSyncResult.Failure(
        ScheduleRemoteError.InvalidResponse(failure),
        attempted = false,
      )
      List(commands.size) { result }
    } ?: readiness.binding.delegate.executeSerially(commands, shouldContinue)
  }

  /** 当前 binding 完成初始化后的结果；失败只读返回，不再继续调用未初始化的 delegate。 */
  private data class BindingReadiness(
    val binding: Binding,
    val failure: Throwable?,
  )

  /**
   * 捕获当前账号 binding 并等待其唯一初始化任务。
   *
   * 返回前再次校验 binding identity，避免等待期间切号后把旧页面命令提交给旧账号，或错误地改投新账号。
   */
  private suspend fun awaitCurrentBindingInitialization(): BindingReadiness {
    val binding = currentBinding() ?: throw ScheduleRepositoryAccountRequiredException()
    binding.initializationJob?.join()
    check(isCurrent(binding, binding.accountId)) {
      "Schedule repository account changed before command execution"
    }
    return BindingReadiness(
      binding = binding,
      failure = synchronized(guard) { binding.initializationFailure },
    )
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
          repositoryInitialized(binding.delegate, binding.session)
            .releaseAfterInitializationMutex()
        }
        synchronized(guard) {
          binding.initializationCompleted = true
          binding.initializationFailure = null
        }
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Throwable) {
        // 初始化失败只属于此 delegate；后续账号变化仍应能创建并初始化新的 delegate。
        synchronized(guard) {
          binding.initializationCompleted = true
          binding.initializationFailure = failure
        }
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
