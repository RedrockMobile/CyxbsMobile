package com.cyxbs.pages.schedule.data.model

import com.cyxbs.pages.schedule.recurrence.Recurrence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CMP todo 数据层的主模型。
 *
 * 该模型对齐后端 todo 协议，不携带 Room、Gson、Serializable 等 Android-only 依赖。
 * 字段命名以 `/Users/guoxiangrui/GolandProjects/magipoke-todo` 后端实现为准，例如过期字段使用
 * `is_overed`，而不是旧 Android 侧曾使用过的 `is_over`。
 */
@Serializable
data class ScheduleEntity(
  /** todo 唯一 id，由客户端生成并上传给后端；新数据层不再依赖 Room 自增主键。 */
  @SerialName("todo_id")
  val todoId: Long,
  /** todo 标题，业务上不能为空。 */
  @SerialName("title")
  val title: String,
  /** todo 详情/备注，允许为空字符串。 */
  @SerialName("detail")
  val detail: String = "",
  /**
   * 非重复 todo 的完成状态：0 未完成，1 已完成；保持 Int 是为了对齐后端协议。
   * 重复系列中单次 occurrence 的完成状态记录在 [Recurrence.overrides]。
   */
  @SerialName("is_done")
  val isDone: Int = 0,
  /** 提醒与重复规则，对应后端 `remind_mode`。 */
  @SerialName("remind_mode")
  val remindMode: ScheduleRemindMode = ScheduleRemindMode(),
  /** 最后修改时间戳，单位沿用旧接口语义，由客户端/服务端同步流程使用。 */
  @SerialName("last_modify_time")
  val lastModifyTime: Long,
  /** todo 分类，取值见 [TYPE_STUDY]、[TYPE_LIFE]、[TYPE_OTHER]。 */
  @SerialName("type")
  val type: String = TYPE_OTHER,
  /** 开始时间字符串；新版本支持字段，与 end_time 一起表示时间段类型的 todo，不下发时表示截止类型 todo。 */
  @SerialName("start_time")
  val startTime: String = "",
  /** 截止时间字符串；第一阶段继续沿用旧端与后端的中文时间格式。 */
  @SerialName("end_time")
  val endTime: String = "",
  /** 是否已过期：0 未过期，1 已过期；字段名对齐后端真实字段 `is_overed`。 */
  @SerialName("is_overed")
  val isOvered: Int = 0,
  /** 是否置顶：0 未置顶，1 已置顶；置顶变化后续会作为完整 todo upsert 上传。 */
  @SerialName("is_pinned")
  val isPinned: Int = 0,
  /**
   * RFC5545 重复规则（schedule 模块新增，作为重复的事实源）。
   *
   * 与后端 additive 新增的 `recurrence` 列对齐；null 表示单次（或仅由旧 [remindMode] 描述、
   * 读时由 LegacyRecurrenceMigration 合成）。
   */
  @SerialName("recurrence")
  val recurrence: Recurrence? = null,
  /** 提前多少分钟提醒（写入系统日历闹钟偏移）；-1 表示不提醒。 */
  @SerialName("remind_minutes")
  val remindMinutes: Int = -1,
) {
  companion object {
    /** 学习分类，对应旧 UI 的“学习”。 */
    const val TYPE_STUDY = "study"
    /** 生活分类，对应旧 UI 的“生活”。 */
    const val TYPE_LIFE = "life"
    /** 其他分类，对应旧 UI 的“其他”。 */
    const val TYPE_OTHER = "other"
  }
}
