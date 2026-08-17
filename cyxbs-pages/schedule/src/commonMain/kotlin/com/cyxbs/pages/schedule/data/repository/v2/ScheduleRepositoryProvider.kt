package com.cyxbs.pages.schedule.data.repository.v2

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.schedule.data.repository.ScheduleIdGenerators
import com.cyxbs.pages.schedule.data.repository.UuidV7ScheduleIdGenerators
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.createProductionScheduleRepositoryFactory
import com.cyxbs.pages.schedule.domain.uuid.UuidV7Generator
import kotlin.time.Clock

/**
 * Schedule v2 仓库的进程级唯一持有者。
 *
 * 主页面、Feed 与课程等入口必须共享此实例，避免各自创建仓库后产生互不一致的 StateFlow 与账号绑定。实际后端经
 * 平台 seam 组装：Android、iOS 与 Desktop production 使用进程生命周期 owner 持有的 Room；Web 在远端能力部署前
 * 使用不持久化的 READ_ONLY unavailable façade，初始化与命令会 fail-closed，不能把本地空快照作为业务事实。
 */
object ScheduleRepositoryProvider {
  /**
   * 进程内共享的日程标识生成器，统一维护普通创建与拆分系列所需 UUID 的单调状态。
   */
  val idGenerators: ScheduleIdGenerators by lazy { UuidV7ScheduleIdGenerators(UuidV7Generator()) }
  /** 仓库统一使用的系统时钟；集中暴露便于所有持久化时间戳保持同一时间来源。 */
  val clock: Clock = Clock.System

  /**
   * 延迟创建并全局共享的稳定代理。
   *
   * 对象身份在登录、登出与游客切换中不变；每次 Login 创建绑定学号的不可变 façade，先发布对应 Loading 流再
   * 初始化。Logout/Tourist 只公开空事实，并由代理拒绝后续命令。平台若提供可靠网络状态，还会在离线恢复且
   * 当前账号确有本地临时提交时请求一次 Sync。
   */
  val repository: ScheduleRepository by lazy {
    AccountSwitchingScheduleRepository(
      factory = createProductionScheduleRepositoryFactory(clock),
      scope = appCoroutineScope,
    ).also { repository ->
      repository.bindAccounts(IAccountService::class.impl().session)
      bindScheduleNetworkRecoverySync(repository, appCoroutineScope)
    }
  }
}
