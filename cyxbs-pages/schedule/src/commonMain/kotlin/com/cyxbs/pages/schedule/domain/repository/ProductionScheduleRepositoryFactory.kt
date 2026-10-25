package com.cyxbs.pages.schedule.domain.repository

import kotlin.time.Clock

/**
 * 创建当前平台生产环境使用的账号绑定仓库工厂。
 *
 * Android、iOS 与 Desktop 在此边界接入由进程生命周期 owner 持有的 Room 数据库；Web 暂时返回显式 unavailable
 * 的只读 façade。Schedule ID 由编辑入口单独生成，不属于仓库网络或持久化组装参数；该 seam 也不承担账号监听、
 * 服务定位或数据库生命周期管理，其 [ScheduleRepositoryFactory.create] 不得执行 I/O。
 */
expect fun createProductionScheduleRepositoryFactory(
  clock: Clock,
): ScheduleRepositoryFactory
