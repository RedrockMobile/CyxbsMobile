package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** iOS 日历设置页可展示的稳定 source 候选。 */
/**
 * iOS 日历设置的状态机。
 *
 * 状态只解释用户配置与 EventKit 只读检查，不创建 calendar/event、不启动导出 runtime，也不调用任何 CRUD。
 */
internal enum class IosScheduleCalendarSettingsStatus {
  UNCONFIGURED,
  PERMISSION_NOT_REQUESTED,
  PERMISSION_REQUESTING,
  PERMISSION_CANCELLED,
  PERMISSION_DENIED,
  PERMISSION_RESTRICTED,
  PERMISSION_WRITE_ONLY,
  SOURCE_MISSING,
  CALENDAR_MISSING,
  CALENDAR_MOVED_TO_OTHER_SOURCE,
  AMBIGUOUS_REQUIRES_RESELECTION,
  CONFIGURED,
  ACCOUNT_UNAVAILABLE,
}

/** 设置页的完整纯状态；source 列表只来自显式只读加载，不会请求系统权限。 */
internal data class IosScheduleCalendarSettingsState(
  val status: IosScheduleCalendarSettingsStatus,
  val sources: List<IosEventKitSettingsSource> = emptyList(),
  val selectedSourceIdentifier: String? = null,
  val cachedCalendarIdentifier: String? = null,
  val exportEnabled: Boolean = false,
)

/**
 * 仅供 controller 合同测试在失效 runtime fence 后暂停一个显式 intent 事务。
 *
 * 生产默认实现不挂起；该端口不接触 EventKit、repository 或持久化内容，只固定复现 Default dispatcher 上 source
 * 选择与 disable 交错时，后一个事务必须等待前一个 signal 完成的锁语义。
 */
internal fun interface IosScheduleCalendarIntentMutationBoundary {
  suspend fun awaitAfterRuntimeInvalidated()
}

/** 生产默认值：显式 intent 事务不额外挂起。 */
private val NoOpIosScheduleCalendarIntentMutationBoundary =
  IosScheduleCalendarIntentMutationBoundary { }

/**
 * 账号会话严格隔离的 iOS 日历设置 controller。
 *
 * 每项操作冻结 [AccountSession] 与 `accountCoroutineScopeFor(session)`；每次 suspend 前后以及每次 StateFlow/偏好
 * 写入前后都会重新核验 exact session、账号与协程活性。相同账号重新登录也会得到新 session，
 * 所以迟到授权 completion 不能覆盖新一代 UI 或配置。
 */
internal class IosScheduleCalendarSettingsController(
  private val accountService: IAccountService,
  private val gatewayFactory: (com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope) -> IosEventKitSettingsGateway,
  private val preferences: IosScheduleCalendarPreferenceStore = IosScheduleCalendarExportSettings,
  /** 在第一笔 intent durable 写前同步失效旧 runtime generation，封住旧 completion 污染新 locator 的窗口。 */
  private val runtimeIntentInvalidate: (AccountSession) -> Unit = IosScheduleCalendarExportRuntimeRegistry::invalidate,
  /** 持久化完整 intent 后通知 exact-session runtime；controller 本身不持有 repository。 */
  private val runtimeIntentSignal: (AccountSession) -> Unit = IosScheduleCalendarExportRuntimeRegistry::signal,
  private val intentMutationBoundary: IosScheduleCalendarIntentMutationBoundary =
    NoOpIosScheduleCalendarIntentMutationBoundary,
) {
  /**
   * source 选择与 disable 必须串行完成“invalidate → durable writes → signal”完整事务。
   *
   * account scope 在生产中可运行于 `Dispatchers.Default` 的不同线程；若只靠 runtime 的一个 pending Boolean，后启动
   * 操作会先 invalidate、旧操作再 signal，从而错误释放新操作的 fence。此 mutex 串行 source 的只读预检和
   * 控制面事务；controller 不具备 EventKit CRUD，也不扩大 runtime 的 outbound 能力。
   */
  private val explicitIntentMutex = Mutex()

  private val _state = MutableStateFlow(
    IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE),
  )
  val state: StateFlow<IosScheduleCalendarSettingsState> = _state

  private var requestJob: Job? = null
  private var requestSession: AccountSession? = null

  /** 读取权限、source 和缓存精确可用性；普通加载绝不请求系统权限。 */
  fun refresh(): Job? = launchForCurrentSession { session, accountId, gateway ->
    publish(session, accountId, loadState(session, accountId, gateway))
  }

  /**
   * 仅由用户点击“请求完整日历权限”时调用。
   *
   * 此方法是 controller 唯一触发 gateway [IosEventKitSettingsGateway.requestFullAccess] 的路径；重复点击不会并发
   * 发起第二个系统弹窗。
   */
  fun requestFullAccess() {
    if (requestJob?.isActive == true) return
    // 必须在创建 account scope Job 前冻结 session；gatewayFactory 及 scope 获取都可能同步触发切号，不能在启动后
    // 重新读取当前 session，否则取消旧请求会向新账号发布 PERMISSION_CANCELLED。
    val frozenSession = accountService.session.value
    if (frozenSession.accountId == null) {
      _state.value = IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE)
      return
    }
    val launch = launchFor(frozenSession) { session, accountId, gateway ->
      publish(
        session,
        accountId,
        IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.PERMISSION_REQUESTING),
      )
      currentCoroutineContext().ensureActive()
      checkExactSession(session, accountId)
      when (val result = gateway.requestFullAccess()) {
        IosEventKitPermissionResult.Granted -> {
          currentCoroutineContext().ensureActive()
          checkExactSession(session, accountId)
          publish(session, accountId, loadState(session, accountId, gateway))
        }

        is IosEventKitPermissionResult.Rejected -> {
          currentCoroutineContext().ensureActive()
          checkExactSession(session, accountId)
          publish(
            session,
            accountId,
            IosScheduleCalendarSettingsState(result.reason.toRequestResultStatus()),
          )
        }
      }
    }
    requestJob = launch
    requestSession = frozenSession.takeIf { launch != null }
    launch?.invokeOnCompletion {
      if (requestJob === launch) {
        requestJob = null
        requestSession = null
      }
    }
  }

  /**
   * 取消当前页面发起的授权等待。
   *
   * EventKit 弹窗本身不可取消；这里仅取消本代 Kotlin continuation，并在仍为同一 session 的新 account-scope
   * 协程中发布取消状态，因此迟到平台 completion 没有写回入口。
   */
  fun cancelFullAccessRequest() {
    val frozenSession = requestSession ?: return
    requestJob?.cancel()
    requestJob = null
    requestSession = null
    launchFor(
      frozenSession,
      block = { session, accountId, _ ->
        publish(
          session,
          accountId,
          IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.PERMISSION_CANCELLED),
        )
      },
    )
  }

  /**
   * 用户从明确 source picker 选中一项后保存设置。
   *
   * source 必须仍在只读列表中；旧 calendar cache 若缺失、迁移到其他 source 或不再可证明精确可用，会先清空。
   * 写入顺序固定为 source → invalid calendar 清空 → enabled=true，且整个序列前后均复核同一 exact session。
   */
  fun selectSource(sourceIdentifier: String): Job? =
    launchForCurrentSession { session, accountId, gateway ->
      explicitIntentMutex.withLock {
        val sources = gateway.sources()
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        val available = (sources as? IosEventKitSettingsReadResult.Available)?.value
          ?: run {
            publish(
              session,
              accountId,
              IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION),
            )
            return@launchForCurrentSession
          }
        if (available.none { it.identifier == sourceIdentifier }) {
          publish(
            session,
            accountId,
            IosScheduleCalendarSettingsState(
              status = IosScheduleCalendarSettingsStatus.SOURCE_MISSING,
              sources = available,
            ),
          )
          return@launchForCurrentSession
        }
        val preference = preferences.get(accountId)
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        val cachedCalendar =
          when (gateway.checkCachedSelection(sourceIdentifier, preference.calendarIdentifier)) {
            IosEventKitCachedSelection.Available,
            IosEventKitCachedSelection.NoCalendarHint -> preference.calendarIdentifier

            IosEventKitCachedSelection.CalendarMissing,
            IosEventKitCachedSelection.CalendarMovedToOtherSource -> null

            IosEventKitCachedSelection.SourceMissing,
            IosEventKitCachedSelection.Ambiguous,
            IosEventKitCachedSelection.Unavailable -> {
              publish(
                session,
                accountId,
                IosScheduleCalendarSettingsState(
                  status = IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION,
                  sources = available,
                ),
              )
              return@launchForCurrentSession
            }
          }
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        // 先失效旧 runtime，再改写任何 durable intent。否则 EventKit 已提交但尚未返回的旧 completion 可在 source/cache
        // 写入期间把旧 locator 回填到新配置；失效本身不启动新 Full，完整 intent 写完后才由 signal 启动。
        val sourceChanged = preference.sourceIdentifier != sourceIdentifier
        val currentSession = accountService.session.value
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        runtimeIntentInvalidate(currentSession)
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        // 仅测试 seam：锁仍持有时暂停，证明并发 disable 不可能先打开第二个 fence 后让本事务 signal 误释放它。
        intentMutationBoundary.awaitAfterRuntimeInvalidated()
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        // source 切换会令旧 calendar/event locator 跨 source 失效；固定按 source → cache 清理 → enabled 写入。
        preferences.updateSourceIdentifier(accountId, sourceIdentifier)
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        if (sourceChanged || cachedCalendar == null) {
          preferences.updateCalendarIdentifier(accountId, null)
          checkExactSession(session, accountId)
          currentCoroutineContext().ensureActive()
          preferences.clearEventReferences(accountId)
          checkExactSession(session, accountId)
          currentCoroutineContext().ensureActive()
        }
        preferences.setEnabled(accountId, true)
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        // 只有 source、无效 cache 清理和 enabled 全部 durable 后才允许唤醒已预先失效的新 generation。
        checkExactSession(session, accountId)
        runtimeIntentSignal(currentSession)
        checkExactSession(session, accountId)
        currentCoroutineContext().ensureActive()
        publish(
          session,
          accountId,
          IosScheduleCalendarSettingsState(
            status = IosScheduleCalendarSettingsStatus.CONFIGURED,
            sources = available,
            selectedSourceIdentifier = sourceIdentifier,
            cachedCalendarIdentifier = cachedCalendar.takeUnless { sourceChanged },
            exportEnabled = true,
          ),
        )
      }
    }

  /** 关闭前先失效同 session 的旧 generation，保留已有 EventKit 数据和 source/calendar cache。 */
  fun disable(): Job? = launchForCurrentSession { session, accountId, gateway ->
    explicitIntentMutex.withLock {
      val currentSession = accountService.session.value
      checkExactSession(session, accountId)
      currentCoroutineContext().ensureActive()
      runtimeIntentInvalidate(currentSession)
      checkExactSession(session, accountId)
      currentCoroutineContext().ensureActive()
      intentMutationBoundary.awaitAfterRuntimeInvalidated()
      checkExactSession(session, accountId)
      currentCoroutineContext().ensureActive()
      preferences.setEnabled(accountId, false)
      checkExactSession(session, accountId)
      currentCoroutineContext().ensureActive()
      runtimeIntentSignal(currentSession)
      checkExactSession(session, accountId)
      currentCoroutineContext().ensureActive()
      publish(session, accountId, loadState(session, accountId, gateway))
    }
  }

  /** 从冻结账号 scope 启动工作；登出/游客与 exact-session scope 缺失均 fail-closed。 */
  private fun launchForCurrentSession(
    block: suspend (AccountSession, String, IosEventKitSettingsGateway) -> Unit,
  ): Job? {
    val session = accountService.session.value
    val accountId = session.accountId ?: run {
      _state.value = IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.ACCOUNT_UNAVAILABLE)
      return null
    }
    return launchFor(session, block)
  }

  /** 使用同一 identity 冻结 session 与 scope，防止在两次读取之间切号。 */
  private fun launchFor(
    frozenSession: AccountSession,
    block: suspend (AccountSession, String, IosEventKitSettingsGateway) -> Unit,
  ): Job? {
    val frozenAccountId = frozenSession.accountId ?: return null
    val scope = accountService.accountCoroutineScopeFor(frozenSession) ?: return null
    val gateway = gatewayFactory(IosScheduleCalendarExportSettings.scopeForAccount(frozenAccountId))
    return scope.launch {
      try {
        checkExactSession(frozenSession, frozenAccountId)
        currentCoroutineContext().ensureActive()
        block(frozenSession, frozenAccountId, gateway)
        checkExactSession(frozenSession, frozenAccountId)
        currentCoroutineContext().ensureActive()
      } catch (cancelled: CancellationException) {
        throw cancelled
      }
    }
  }

  /** 把 gateway 当前只读状态映射为设置状态，且不创建/修改 EventKit 对象。 */
  private suspend fun loadState(
    session: AccountSession,
    accountId: String,
    gateway: IosEventKitSettingsGateway,
  ): IosScheduleCalendarSettingsState {
    checkExactSession(session, accountId)
    return when (gateway.fullAccessStatus()) {
      IosEventKitFullAccessStatus.NOT_DETERMINED ->
        IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED)

      IosEventKitFullAccessStatus.DENIED ->
        IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.PERMISSION_DENIED)

      IosEventKitFullAccessStatus.RESTRICTED ->
        IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.PERMISSION_RESTRICTED)

      IosEventKitFullAccessStatus.WRITE_ONLY ->
        IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.PERMISSION_WRITE_ONLY)

      IosEventKitFullAccessStatus.UNKNOWN ->
        IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION)

      IosEventKitFullAccessStatus.FULL_ACCESS -> loadFullAccessState(session, accountId, gateway)
    }
  }

  /** full-access 后先读取 source 列表，再严格检查已缓存 source/calendar 的当前归属。 */
  private suspend fun loadFullAccessState(
    session: AccountSession,
    accountId: String,
    gateway: IosEventKitSettingsGateway,
  ): IosScheduleCalendarSettingsState {
    val sources = (gateway.sources() as? IosEventKitSettingsReadResult.Available)?.value
      ?: return IosScheduleCalendarSettingsState(IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION)
    checkExactSession(session, accountId)
    val preference = preferences.get(accountId)
    checkExactSession(session, accountId)
    val sourceIdentifier = preference.sourceIdentifier
      ?: return IosScheduleCalendarSettingsState(
        status = IosScheduleCalendarSettingsStatus.UNCONFIGURED,
        sources = sources,
      )
    val status = when (gateway.checkCachedSelection(sourceIdentifier, preference.calendarIdentifier)) {
      IosEventKitCachedSelection.Available,
      IosEventKitCachedSelection.NoCalendarHint -> if (preference.enabled) {
        IosScheduleCalendarSettingsStatus.CONFIGURED
      } else {
        IosScheduleCalendarSettingsStatus.UNCONFIGURED
      }

      IosEventKitCachedSelection.SourceMissing -> IosScheduleCalendarSettingsStatus.SOURCE_MISSING
      IosEventKitCachedSelection.CalendarMissing -> IosScheduleCalendarSettingsStatus.CALENDAR_MISSING
      IosEventKitCachedSelection.CalendarMovedToOtherSource ->
        IosScheduleCalendarSettingsStatus.CALENDAR_MOVED_TO_OTHER_SOURCE

      IosEventKitCachedSelection.Ambiguous,
      IosEventKitCachedSelection.Unavailable -> IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION
    }
    checkExactSession(session, accountId)
    return IosScheduleCalendarSettingsState(
      status = status,
      sources = sources,
      selectedSourceIdentifier = sourceIdentifier,
      cachedCalendarIdentifier = preference.calendarIdentifier,
      exportEnabled = preference.enabled && status == IosScheduleCalendarSettingsStatus.CONFIGURED,
    )
  }

  /**
   * 保留显式授权请求返回的 typed 终态。
   *
   * 平台状态重读可能仍是 `NOT_DETERMINED`，此时不能把本次拒绝或 store 不确定性伪装成“从未请求”。
   * source/calendar 类失败虽不应由授权 seam 正常返回，仍按各自的安全恢复状态处理，避免未来实现漏映射。
   */
  private fun IosEventKitGatewayFailure.toRequestResultStatus(): IosScheduleCalendarSettingsStatus = when (this) {
    IosEventKitGatewayFailure.PERMISSION_REQUIRED ->
      IosScheduleCalendarSettingsStatus.PERMISSION_NOT_REQUESTED

    IosEventKitGatewayFailure.PERMISSION_DENIED,
    IosEventKitGatewayFailure.PERMISSION_REVOKED -> IosScheduleCalendarSettingsStatus.PERMISSION_DENIED

    IosEventKitGatewayFailure.SOURCE_DISAPPEARED -> IosScheduleCalendarSettingsStatus.SOURCE_MISSING
    IosEventKitGatewayFailure.CALENDAR_DISAPPEARED -> IosScheduleCalendarSettingsStatus.CALENDAR_MISSING
    IosEventKitGatewayFailure.AMBIGUOUS_CALENDAR,
    IosEventKitGatewayFailure.AMBIGUOUS_EVENT,
    IosEventKitGatewayFailure.FOREIGN_IDENTITY,
    IosEventKitGatewayFailure.UNSUPPORTED_PROJECTION,
    IosEventKitGatewayFailure.READ_AFTER_WRITE_MISMATCH,
    IosEventKitGatewayFailure.STORE_AMBIGUOUS ->
      IosScheduleCalendarSettingsStatus.AMBIGUOUS_REQUIRES_RESELECTION
  }

  /** 每次 UI StateFlow 写入前后都复核 exact session、账号与当前协程活性。 */
  private suspend fun publish(
    session: AccountSession,
    accountId: String,
    value: IosScheduleCalendarSettingsState,
  ) {
    checkExactSession(session, accountId)
    currentCoroutineContext().ensureActive()
    _state.value = value
    checkExactSession(session, accountId)
    currentCoroutineContext().ensureActive()
  }

  /**
   * 在异步边界确认冻结会话仍是账号服务当前的 exact instance。
   *
   * scope 在启动时已通过 [IAccountService.accountCoroutineScopeFor] 冻结；此处不维护独立状态机，只防止
   * 同账号重新登录或切号后的迟到 completion 写入旧账号配置和 UI。
   */
  private suspend fun checkExactSession(
    session: AccountSession,
    accountId: String,
  ) {
    currentCoroutineContext().ensureActive()
    if (accountService.session.value !== session || session.accountId != accountId) {
      throw CancellationException("iOS calendar settings session is no longer current")
    }
  }
}
