package com.cyxbs.pages.schedule.domain.calendar

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository

/** 非移动端不提供系统日历桥接，完整账号 session 只透传以保持 expect/actual 合同并交还 inert handoff。 */
internal actual suspend fun onScheduleRepositoryInitialized(
  repository: ScheduleRepository,
  session: AccountSession,
): ScheduleRepositoryInitializationHandoff = NoOpScheduleRepositoryInitializationHandoff
