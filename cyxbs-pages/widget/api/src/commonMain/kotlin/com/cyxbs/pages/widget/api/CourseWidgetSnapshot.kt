package com.cyxbs.pages.widget.api

import kotlinx.serialization.Serializable

/**
 * 课表最终渲染快照的跨平台协议。
 *
 * 该协议属于小组件宿主，只描述已经完成重叠裁剪和样式决策后的结果，不暴露课程、关联人或日程等
 * 业务数据源；课表模块负责生产，小组件只负责持久化和绘制。
 */
@Serializable
data class CourseWidgetSnapshot(
  val schemaVersion: Int = 1,
  val generatedAtEpochMillis: Long,
  val firstWeekBeginEpochDays: Int? = null,
  val maxWeek: Int,
  /** 普通 Widget 线性时间轴的起止分钟；为空时兼容旧快照比例。 */
  val timelineBeginMinute: Int? = null,
  val timelineEndMinute: Int? = null,
  /** 课表侧已经换算到稳定时间轴上的顶部时刻刻度。 */
  val timelineMarks: List<CourseWidgetTimelineMark> = emptyList(),
  /** 大号周课表使用的纵向分段；由课表侧决定折叠文案、时间范围与展开权重。 */
  val oversizedTimelineSections: List<CourseWidgetTimelineSection> = emptyList(),
  val weeks: List<CourseWidgetWeekSnapshot>,
)

/** 顶部时间轴的单个刻度；Widget 只消费文字和位置，不推断课表时间规则。 */
@Serializable
data class CourseWidgetTimelineMark(
  val label: String,
  val ratio: Float,
)

/**
 * 大号周课表的一段纵向时间轴。
 *
 * [collapsedWeight] 与 [expandedWeight] 都是相对权重；[expandable] 为 false 时 Widget 始终使用
 * collapsedWeight。Widget 只保存展开状态，不推断课节、午间等业务含义。
 */
@Serializable
data class CourseWidgetTimelineSection(
  val id: String,
  val collapsedLabel: String,
  val expandedLabel: String,
  val beginMinute: Int,
  val endMinute: Int,
  val collapsedWeight: Float,
  val expandedWeight: Float,
  val expandable: Boolean,
)

/** 单周内按最终绘制顺序排列的课表条目。 */
@Serializable
data class CourseWidgetWeekSnapshot(
  val week: Int,
  val items: List<CourseWidgetRenderItem>,
)

/** 单个可渲染课表条目，时间为空时表示全天条目。 */
@Serializable
data class CourseWidgetRenderItem(
  val id: String,
  /** 课表侧决定的绘制层序号，值越小优先级越高；Widget 不据此推断课程、事务等业务类型。 */
  val renderLayer: Int = 0,
  /** ISO 星期，周一为 1、周日为 7。 */
  val dayOfWeek: Int,
  val title: String,
  val content: String,
  val beginMinute: Int? = null,
  val endMinute: Int? = null,
  /** 条目原始起止时间在课表稳定时间轴上的比例，用于 Widget 按真实时段横向排布。 */
  val beginRatio: Float? = null,
  val endRatio: Float? = null,
  val visibleRanges: List<CourseWidgetVisibleRange> = emptyList(),
  val isAllDay: Boolean = false,
  val lightStyle: CourseWidgetItemStyle,
  val darkStyle: CourseWidgetItemStyle,
  val backgroundPattern: CourseWidgetBackgroundPattern = CourseWidgetBackgroundPattern.SOLID,
  val action: CourseWidgetAction,
)

/** 条目经过重叠裁剪后仍可见的时间段及其在课表时间轴上的比例。 */
@Serializable
data class CourseWidgetVisibleRange(
  val beginMinute: Int,
  val endMinute: Int,
  val beginRatio: Float,
  val endRatio: Float,
)

/** 浅色或深色模式下的文字、背景与可选斜纹颜色。ARGB 使用 Long 避免跨平台 Int 符号差异。 */
@Serializable
data class CourseWidgetItemStyle(
  val contentArgb: Long,
  val backgroundArgb: Long,
  val stripeArgb: Long? = null,
  /** 圆角底卡颜色；为空表示升级前快照，由 Widget 使用对应明暗模式的课表默认底色。 */
  val containerArgb: Long? = null,
)

/** 小组件背景图案。 */
@Serializable
enum class CourseWidgetBackgroundPattern {
  SOLID,
  DIAGONAL_STRIPE,
}

/** 点击条目的通用展示语义；[itemId] 为空时仅表示对应周。 */
@Serializable
data class CourseWidgetAction(
  val week: Int,
  val itemId: String? = null,
  /**
   * 根条目当前详情弹窗会展示的重叠条目 ID，按展示顺序去重。
   *
   * 它仅用于课表侧实时数据定位，Activity 不得将其当作详情内容缓存。
   */
  val overlapItemIds: List<String> = emptyList(),
)

/** 最终渲染快照的发布出口；小组件模块负责持久化并唤醒自身 Widget。 */
interface ICourseWidgetSnapshotPublisher {
  /** 以新快照替换旧快照；调用方已完成所有课程业务数据和重叠关系的合并。 */
  suspend fun replace(snapshot: CourseWidgetSnapshot)
}
