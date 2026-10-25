package com.cyxbs.pages.schedule.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 日历事件与 replay 控制信号共用的有序发布协议。
 *
 * [Change] 是唯一会暴露给公开 [ScheduleRepository.calendarChanges] 的真实业务事件；[ReplayEvicted]
 * 只替换同一 replay=1 流中的旧事件，公开 collector 必须静默抑制它。两者禁止拆成独立 Flow，否则 Delete
 * barrier 可能与旧增量重排，late collector 仍会把已删除日程误认为新提交。
 */
internal sealed interface ScheduleCalendarPublication {
  /** publication 所属账号，转发层必须与当前 construction-bound session 同时校验。 */
  val accountId: String

  /** 包装一个真实日历变化；只有该分支可以进入公开 calendarChanges。 */
  data class Change(
    val value: ScheduleCalendarChange,
  ) : ScheduleCalendarPublication {
    override val accountId: String
      get() = value.accountId
  }

  /**
   * 淘汰此前 replay 的控制屏障。
   *
   * 屏障不代表 Schedule 发生新的可公开变化，也不授予删除 Provider 数据的权限；它只占据同一有序流的
   * replay 槽，使 active 与 late 公开 collector 都观察不到伪造事件。
   */
  data class ReplayEvicted(
    override val accountId: String,
  ) : ScheduleCalendarPublication
}

/**
 * construction-bound repository 对有序日历 publication 流的只读内部访问合同。
 *
 * 实现必须让真实 [ScheduleCalendarPublication.Change] 与 [ScheduleCalendarPublication.ReplayEvicted] 来自同一个
 * Flow。稳定账号代理应直接复用该有序 replay 源，避免额外 replay 槽需要异步 acknowledgment；不实现本合同的
 * fallback delegate 仍可把公开 calendarChanges 包装为纯 Change 流。
 */
internal interface ScheduleCalendarPublicationAccess {
  val calendarPublications: Flow<ScheduleCalendarPublication>
}
