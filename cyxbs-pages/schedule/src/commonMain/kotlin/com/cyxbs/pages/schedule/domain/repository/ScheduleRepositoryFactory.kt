package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.components.account.api.AccountSession

/**
 * 创建指定持久化后端 Schedule 仓库的平台中立合同。
 *
 * 工厂只负责把一个不可变账号分区键绑定到已配置的数据后端；生产 Provider 选择何种后端、以及账号切换时
 * 如何原子替换 façade，仍由应用层决定。因而 Room、Settings 和未来 Web 实现不会在 common API 中互相依赖
 * 或触发迁移/双写。
 */
fun interface ScheduleRepositoryFactory {
  /**
   * 为不可变 [session] 创建账号绑定的仓库 façade。
   *
   * [AccountSession.generation] 是平台 hook 的冻结 binding identity；即使同学号重新登录也必须创建新 façade，
   * Android 日历恢复会据此 fail-closed 拒绝上一代 delegate。工厂不得借此方法执行 I/O 或网络请求。
   */
  fun create(session: AccountSession): ScheduleRepository
}

/**
 * 当前没有经过认证账号时拒绝命令的稳定错误。
 *
 * Provider 会在登出和游客状态发布空事实；调用方若继续写入，必须收到可识别的失败而非写入匿名或上一个账号分区。
 */
class ScheduleRepositoryAccountRequiredException : IllegalStateException(
  "Schedule repository requires an authenticated account",
)
