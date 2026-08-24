package com.cyxbs.pages.schedule.api

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import kotlinx.coroutines.flow.Flow

/**
 * Schedule 对其他模块公开的只读 occurrence。
 *
 * [identity] 在同一 occurrence 生命周期内稳定，不能用当前展示时间替代；重复实例移动后仍保持原 identity。
 */
data class ScheduleOccurrenceView(
  val identity: String,
  val scheduleId: ScheduleId,
  val recurrenceId: RecurrenceId?,
  val title: String,
  val description: String,
  val timing: ScheduleOccurrenceTiming,
)

/** Schedule occurrence 的四种排期语义，不包含任何课表布局或 Decoration 信息。 */
sealed interface ScheduleOccurrenceTiming {

  /** 有明确开始时间和持续分钟数的时间段。 */
  data class Timed(
    val start: MinuteTimeDate,
    val durationMinutes: Int,
    val timeZoneId: String,
  ) : ScheduleOccurrenceTiming

  /** 只占一个时间点的截止事项。 */
  data class Deadline(
    val due: MinuteTimeDate,
    val timeZoneId: String,
  ) : ScheduleOccurrenceTiming

  /** 按本地日期覆盖连续自然日的全天事项。 */
  data class AllDay(
    val startDate: Date,
    val durationDays: Int,
  ) : ScheduleOccurrenceTiming

  /** 未设置时间；课表等时间视图应直接忽略。 */
  data object Unscheduled : ScheduleOccurrenceTiming
}

/**
 * Schedule 向其他功能模块公开的只读服务。
 *
 * API 只暴露日程数据和 Schedule 自己的详情内容，不感知 CoursePageDecoration、CourseItem、课表页码或
 * 重叠层级。调用方负责把 occurrence 映射为自己的 UI。
 */
interface IScheduleService2 {

  /**
   * 持续观察半开时间窗口内、已允许投射到课表的有效 occurrence。
   *
   * 实现会完成重复规则展开、单次例外合并、账号隔离与“关联到课表”过滤；调用方不能据此修改日程。
   */
  fun observeLinkedOccurrencesInRange(
    startInclusive: MinuteTimeDate,
    endExclusive: MinuteTimeDate,
  ): Flow<List<ScheduleOccurrenceView>>

  /**
   * 展示 Schedule 自己维护的日程详情/编辑内容。
   *
   * [embeddedInHost] 为 true 时调用方已经提供外层弹窗容器；该参数不绑定任何具体课表实现。
   */
  @Composable
  fun ScheduleDetailContent(
    occurrence: ScheduleOccurrenceView,
    embeddedInHost: Boolean,
    onDismiss: () -> Unit,
    /** 内容进入或退出编辑态时通知外层宿主，用于锁定当前正在编辑的日程。 */
    onEditModeChanged: (Boolean) -> Unit = {},
  )
}
