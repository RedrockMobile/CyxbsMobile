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
  /** 创建来源决定课表上的视觉语义；该字段不会随清单关联状态变化。 */
  val kind: ScheduleOccurrenceKind,
  /** 当前是否属于清单；事务关联清单后为 true，但 [kind] 仍保持 [ScheduleOccurrenceKind.AFFAIR]。 */
  val isInTodoList: Boolean,
  val title: String,
  val description: String,
  val timing: ScheduleOccurrenceTiming,
  /** 分组配置的课表配色；null 时由课表继续使用当前默认视觉。 */
  val categoryColor: ScheduleOccurrenceColor? = null,
)

/**
 * Schedule 已解析好的课表配色。
 *
 * API 只传递 ARGB 数值，不把 Category JSON 格式或 Compose [androidx.compose.ui.graphics.Color]
 * 泄漏给 course:view；调用方按当前主题选择对应的一组颜色。
 */
data class ScheduleOccurrenceColor(
  val lightBackgroundArgb: Long,
  val lightContentArgb: Long,
  val darkBackgroundArgb: Long,
)

/**
 * 清单与关联事务共用的默认课表配色。
 *
 * 分组预设第一项与课表无分组颜色时的兜底均引用此值，避免两个模块分别维护相同 ARGB。
 */
val ScheduleDefaultOccurrenceColor = ScheduleOccurrenceColor(
  lightBackgroundArgb = 0xFFEFEFEF,
  lightContentArgb = 0xFF8E8E8E,
  // 深色背景保留约 55% 不透明度，在可辨识度和课表底色透出之间保持平衡。
  darkBackgroundArgb = 0x8C5A5A5A,
)

/** 供外部只读判断日程来源，避免暴露 Schedule 模块内部领域类型。 */
enum class ScheduleOccurrenceKind {
  TODO,
  AFFAIR,
}

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
 * Schedule 向其他功能模块公开的服务。
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
    /** 注册内容的关闭拦截；外层蒙层点击和返回键必须先通过该回调，null 表示解除注册。 */
    onDismissRequestChanged: (((suspend () -> Boolean)?) -> Unit) = {},
    /** 将范围选择、未保存确认等内容交给外层 Window 根布局绘制；null 表示清除。 */
    onWindowOverlayContentChanged: (((@Composable () -> Unit)?) -> Unit) = {},
  )

  /**
   * 展示“从课表创建事务”的 Schedule 编辑内容。
   *
   * 保存前只持有 [initialTiming] 草稿；确认后由 Schedule 模块创建原生事务日程，调用方无需接触仓库命令。
   * [onCreated] 在本地命令完成后调用，即使远端暂时不可用，本地待同步数据也已经可被课表观察到。
   */
  @Composable
  fun ScheduleCreateAffairContent(
    initialTiming: ScheduleOccurrenceTiming.Timed,
    embeddedInHost: Boolean,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
    /** 创建表单始终处于编辑态，外层重叠宿主可据此只保留当前页。 */
    onEditModeChanged: (Boolean) -> Unit = {},
    /** 注册内容的关闭拦截；外层蒙层点击和返回键必须先通过该回调，null 表示解除注册。 */
    onDismissRequestChanged: (((suspend () -> Boolean)?) -> Unit) = {},
    /** 将范围选择、未保存确认等内容交给外层 Window 根布局绘制；null 表示清除。 */
    onWindowOverlayContentChanged: (((@Composable () -> Unit)?) -> Unit) = {},
  )
}
