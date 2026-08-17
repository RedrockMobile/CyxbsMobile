package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.schedule.calendar.ScheduleCalendarExportController
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 当前账号仓库就绪后登记一个锁外启动令牌。
 *
 * 初始化 mutex 内只冻结 repository 与 exact session，不读取 Provider、不申请权限、不启动协程；调用方释放 mutex
 * 后再由 handoff 恢复用户已开启的单向导出。
 */
internal actual suspend fun onScheduleRepositoryInitialized(
  repository: ScheduleRepository,
  session: AccountSession,
): ScheduleRepositoryInitializationHandoff = registerScheduleCalendarExportInitialization(
  repository,
  session,
)

/** 为不再携带旧日历 capability 的 repository 创建一次性初始化 handoff。 */
internal fun registerScheduleCalendarExportInitialization(
  repository: ScheduleRepository,
  session: AccountSession,
): ScheduleRepositoryInitializationHandoff = AndroidScheduleCalendarExportInitializationHandoff {
  ScheduleCalendarExportController.resumeIfEnabled(
    context = appContext,
    repository = repository,
    session = session,
  )
}

/**
 * 只允许 release 一次的 Android 初始化令牌。
 *
 * [releaseAction] 由注册时冻结的 repository/session 构成，只有初始化 mutex 已释放后才可触发；函数化边界同时允许
 * host 测试验证 one-shot 合同，而无需访问全局 appContext 或真实账号服务。
 */
internal class AndroidScheduleCalendarExportInitializationHandoff(
  private val releaseAction: () -> Unit,
) : ScheduleRepositoryInitializationHandoff {
  private val released = AtomicBoolean(false)

  /** mutex 释放后恢复已开启导出；重复 release 无副作用。 */
  override fun releaseAfterInitializationMutex() {
    if (!released.compareAndSet(false, true)) return
    releaseAction()
  }
}
