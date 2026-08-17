package com.cyxbs.pages.schedule.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 系统日历单向导出的 Android 生命周期入口。
 *
 * Controller 只负责用户开关、账号代次校验、worker 启停和用户明确请求的受管日历清理；实际投影与 Provider
 * 写入由 [ScheduleCalendarExportCoordinator] 完成。这里没有系统日历回写 Schedule 的入口。
 */
internal object ScheduleCalendarExportController {
  /**
   * 用户明确开启当前账号的系统日历导出。
   *
   * 权限、当前 exact session 与账号 scope 必须同时成立；失败时持久化开关保持关闭。成功后立即执行一次全量导出，
   * 并继续消费 repository 的增量变化。
   */
  fun enable(
    context: Context,
    repository: ScheduleRepository,
    session: AccountSession,
  ) {
    applyEnableTransition(
      accountId = session.accountId,
      permissionsGranted = hasCalendarPermissions(context),
      persist = ScheduleCalendarExportSettings::setEnabled,
      start = { startIfCurrent(context.applicationContext, repository, session) },
    )
  }

  /**
   * 应用“开启导出”的最小状态转换。
   *
   * 无账号时完全无副作用；缺少任一系统日历权限时明确持久化关闭且不得启动 worker。该纯边界让 Android host
   * 测试无需伪造 Context、账号服务或真实 Provider。
   */
  internal fun applyEnableTransition(
    accountId: String?,
    permissionsGranted: Boolean,
    persist: (String, Boolean) -> Unit,
    start: () -> Unit,
  ) {
    val exactAccountId = accountId ?: return
    if (!permissionsGranted) {
      persist(exactAccountId, false)
      return
    }
    persist(exactAccountId, true)
    start()
  }

  /**
   * 仓库初始化完成后恢复已由用户开启的单向导出。
   *
   * 本入口不申请权限；权限缺失时保留设置值，等待设置页重新授权后再次调用 [enable]。
   */
  fun resumeIfEnabled(
    context: Context,
    repository: ScheduleRepository,
    session: AccountSession,
  ) {
    val accountId = session.accountId ?: return
    if (!ScheduleCalendarExportSettings.isEnabled(accountId) || !hasCalendarPermissions(context)) return
    startIfCurrent(context.applicationContext, repository, session)
  }

  /** 关闭后续导出并停止当前账号 worker；不会删除已经写入系统日历的事件。 */
  fun disable(session: AccountSession) {
    val accountId = session.accountId ?: return
    ScheduleCalendarExportSettings.setEnabled(accountId, false)
    ScheduleCalendarExportCoordinatorProvider.stop(
      ScheduleCalendarExportSettings.scopeForAccount(accountId),
    )
  }

  /**
   * 用户明确确认后关闭导出，并删除当前账号的受管系统日历。
   *
   * 删除只作用于应用固定身份创建的 Calendar row；不修改 Schedule 仓库，也不触发任何反向导入。
   */
  fun clearAndDelete(context: Context, session: AccountSession) {
    val accountId = session.accountId ?: return
    disable(session)
    val accountService = IAccountService::class.impl()
    val scope = accountService.accountCoroutineScopeFor(session) ?: return
    scope.launch {
      if (accountService.session.value !== session) return@launch
      AndroidManagedCalendarRegistry(context.applicationContext)
        .clearAndDeleteManagedCalendars(accountId)
    }
  }

  /**
   * 冻结当前账号 scope 并替换该 scope 的唯一 worker。
   *
   * 授权闭包会在每次 Provider 边界复核 exact session、scope owner 与持久化开关；账号切换或关闭后，下一次读写
   * 立即失败关闭。
   */
  private fun startIfCurrent(
    context: Context,
    repository: ScheduleRepository,
    session: AccountSession,
  ) {
    val accountId = session.accountId ?: return
    val accountService = IAccountService::class.impl()
    val scope = accountService.accountCoroutineScopeFor(session) ?: return
    val owner = scope.coroutineContext[Job] ?: return
    if (accountService.session.value !== session || !owner.isActive) return
    val exportScope = ScheduleCalendarExportSettings.scopeForAccount(accountId)
    val ensureAuthorized = {
      check(accountService.session.value === session) { "Calendar export account session changed" }
      check(scope.coroutineContext[Job] === owner && owner.isActive) { "Calendar export account scope changed" }
      check(ScheduleCalendarExportSettings.isEnabled(accountId)) { "Calendar export was disabled" }
      check(hasCalendarPermissions(context)) { "Calendar permissions are unavailable" }
    }
    ScheduleCalendarExportCoordinatorProvider.replace(
      exportScope,
      ScheduleCalendarExportCoordinator(
        context = context,
        repository = repository,
        accountId = accountId,
        exportScope = exportScope,
        coroutineScope = scope,
        ensureAuthorized = ensureAuthorized,
      ),
    )
  }

  /** 系统日历读写权限必须同时存在，单向导出不在后台自行申请权限。 */
  private fun hasCalendarPermissions(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
      context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
