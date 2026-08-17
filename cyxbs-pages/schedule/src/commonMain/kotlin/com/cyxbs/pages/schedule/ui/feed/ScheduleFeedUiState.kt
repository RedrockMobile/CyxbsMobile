package com.cyxbs.pages.schedule.ui.feed

import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId

/**
 * 邮子清单 feed 卡片的展示状态。
 *
 * 将「加载中 / 空 / 有数据」三种视图切换收敛成单一状态，由 [com.cyxbs.pages.schedule.viewmodel.ScheduleFeedViewModel]
 * 直接观察 Schedule v2 共享快照并映射；commonMain UI 仅按状态被动渲染，不再依赖旧 ScheduleService 桥接。
 */
sealed interface ScheduleFeedUiState {

  /** 数据尚未返回，对应「查询中…」 */
  data object Loading : ScheduleFeedUiState

  /** 没有未完成待办，对应「还没有待做事项哦~快去添加吧！」 */
  data object Empty : ScheduleFeedUiState

  /** 有未完成待办（最多展示 3 条） */
  data class Data(val items: List<ScheduleFeedItemUi>) : ScheduleFeedUiState
}

/**
 * feed 列表项的轻量 UI 模型（不依赖 Room 的 `Schedule` bean，便于放在 commonMain）。
 *
 * @param id 稳定的 UUIDv7 系列标识，用于导航与仓库命令
 * @param recurrenceId 重复实例的原始 occurrence 锚点；移动后仍保持不变，非重复项为 null
 * @param title 待办标题
 * @param timeText 已格式化好的提醒/截止时间文案；为 null 时不展示铃铛与时间行
 * @param isOverTime 是否已超时（决定红色样式与超时铃铛）
 */
data class ScheduleFeedItemUi(
  val id: ScheduleId,
  val recurrenceId: RecurrenceId?,
  val title: String,
  val timeText: String?,
  val isOverTime: Boolean,
)
