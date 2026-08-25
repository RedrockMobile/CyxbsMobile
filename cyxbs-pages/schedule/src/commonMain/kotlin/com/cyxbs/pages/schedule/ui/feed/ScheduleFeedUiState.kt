package com.cyxbs.pages.schedule.ui.feed

import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.repository.ScheduleSnapshot
import com.cyxbs.pages.schedule.ui.todo.ScheduleTodoItemUi
import com.cyxbs.pages.schedule.ui.todo.formatScheduleTodoReminder
import com.cyxbs.pages.schedule.ui.todo.projectScheduleTodo
import com.cyxbs.pages.schedule.ui.todo.sortScheduleTodoPending
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * 邮子清单 feed 卡片的展示状态。
 *
 * 将「加载中 / 空 / 有数据」三种视图切换收敛成单一状态，由 [com.cyxbs.pages.schedule.viewmodel.ScheduleFeedViewModel]
 * 直接观察 Schedule v2 共享快照并映射；commonMain UI 仅按状态被动渲染，不再依赖旧 ScheduleService 桥接。
 */
sealed interface ScheduleFeedUiState {

  /** 数据尚未返回，对应「查询中…」 */
  data object Loading : ScheduleFeedUiState

  /** 没有未完成待办，显示当前主页设计稿的休息提示。 */
  data object Empty : ScheduleFeedUiState

  /** 有未完成待办，并携带主页提醒横条所需的全量临期/超期数量。 */
  data class Data(
    /** 主页最多展示三项，顺序与清单页的未完成列表保持一致。 */
    val items: List<ScheduleFeedItemUi>,
    /** 未来 24 小时临期与已超期事项的合计数量；该数量不会被三项展示上限截断。 */
    val urgentCount: Int,
  ) : ScheduleFeedUiState
}

/**
 * feed 列表项的轻量 UI 模型（不依赖 Room 的 `Schedule` bean，便于放在 commonMain）。
 *
 * @param id 稳定的 UUIDv7 系列标识，用于导航与仓库命令
 * @param recurrenceId 重复实例的原始 occurrence 锚点；移动后仍保持不变，非重复项为 null
 * @param title 待办标题
 * @param timeText 已格式化好的截止时间文案；为 null 时不展示时间
 * @param reminderText 由提醒偏移量生成的完整文案；为空时不占用 Feed 时间行宽度
 * @param isOverTime 是否已超时（决定红色样式与超时铃铛）
 * @param isDueSoon 是否会在未来 24 小时内到期；已超期时恒为 false
 * @param isPinned 是否已保存在当前账号的端上置顶顺序中；该字段不会上传服务端
 * @param canToggleCourseProjection 是否允许用户切换课表投射；原生事务固定属于课表，无时间清单也不能切换
 * @param isProjectedToCourse 是否已持久化为投射到课表
 */
data class ScheduleFeedItemUi(
  val id: ScheduleId,
  val recurrenceId: RecurrenceId?,
  val title: String,
  val timeText: String?,
  val reminderText: String?,
  val isOverTime: Boolean,
  val isDueSoon: Boolean,
  val isPinned: Boolean,
  val canToggleCourseProjection: Boolean,
  val isProjectedToCourse: Boolean,
)

/**
 * 将 Schedule v2 快照投影为主页 Feed 状态。
 *
 * 横条数量基于完整未完成集合计算，卡片才截取前三项；因此第四项之后的临期或超期事项也不会漏报。
 */
internal fun projectScheduleFeed(
  snapshot: ScheduleSnapshot,
  now: Instant,
  viewerTimeZone: TimeZone,
  pinnedIds: List<ScheduleId> = emptyList(),
): ScheduleFeedUiState {
  val projection = projectScheduleTodo(snapshot, now, viewerTimeZone)
  val items = sortScheduleTodoPending(projection.pending, pinnedIds)
    .take(3)
    .map { item ->
      item.toFeedItem(
        isPinned = item.schedule.id in pinnedIds,
        isProjectedToCourse = item.schedule.linkedToCourse,
      )
    }
  return if (items.isEmpty()) {
    ScheduleFeedUiState.Empty
  } else {
    ScheduleFeedUiState.Data(items = items, urgentCount = projection.urgentCount)
  }
}

/** 映射 Feed 轻量模型；时间文案和临期状态均沿用清单页已经计算完成的结果。 */
private fun ScheduleTodoItemUi.toFeedItem(
  isPinned: Boolean,
  isProjectedToCourse: Boolean,
) = ScheduleFeedItemUi(
  id = schedule.id,
  recurrenceId = occurrence.recurrenceId,
  title = occurrence.title,
  timeText = timeText.takeIf(String::isNotBlank),
  reminderText = occurrence.reminders
    .mapNotNull { reminder -> formatScheduleTodoReminder(reminder.offsetMinutes) }
    .joinToString(separator = "、")
    .takeIf(String::isNotEmpty),
  isOverTime = isOverdue,
  isDueSoon = isDueSoon,
  isPinned = isPinned,
  canToggleCourseProjection = schedule.kind == ScheduleKind.TODO &&
    occurrence.timing !is ScheduleTiming.Unscheduled,
  isProjectedToCourse = isProjectedToCourse,
)
