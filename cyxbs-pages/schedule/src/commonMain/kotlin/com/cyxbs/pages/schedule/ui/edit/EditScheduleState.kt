package com.cyxbs.pages.schedule.ui.edit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.recurrence.Recurrence
import com.cyxbs.pages.schedule.ui.timeline.ScheduleDateTime
import com.cyxbs.pages.schedule.ui.timeline.parseScheduleDateTime

/**
 * 添加 / 编辑弹窗（[EditScheduleDialog]）的表单状态：标题、备注、分组、时间（开始/结束 + 截止/时间段切换）、
 * 重复规则草稿（[RecurrenceDraft]，对齐 RFC5545）。仅承载「日程可编辑数据」与派生标志。
 *
 * 与旧 todo 版的差异：重复从 `TodoRemindMode`（弱化自定义模型）换成了 [RecurrenceDraft]（RRULE 子集）。
 * 保存时读 [outputRecurrence] 写入 [ScheduleEntity.recurrence]。
 *
 * @param origin 编辑的原始日程（新建为 null），用作 [isChanged] 比较基准与重复 base（保留 exdate/overrides）。
 */
@Stable
class EditScheduleState(val origin: ScheduleEntity?) {
  val title = TextFieldState(origin?.title ?: "")
  val detail = TextFieldState(origin?.detail ?: "")
  var type by mutableStateOf(origin?.type ?: ScheduleEntity.TYPE_OTHER)
  var startTime by mutableStateOf(origin?.startTime ?: "")
  var endTime by mutableStateOf(origin?.endTime ?: "")

  // 新建默认优先「时间段」；编辑时按数据判断（有 startTime 为时间段）。
  var isInterval by mutableStateOf(
    if (origin == null) true else origin.startTime?.isNotBlank() == true,
  )

  // 重复规则草稿：编辑时从原 recurrence 反解，新建为「不重复」。
  var recurrence by mutableStateOf(origin?.recurrence.toDraft())

  /** 锚点日期：开始时间优先、否则截止时间、再否则今天。用于把草稿补全成完整 RRULE。 */
  val anchorDate: Date
    get() = parseScheduleDateTime(outputStartTime)?.date
      ?: parseScheduleDateTime(outputEndTime)?.date
      ?: Date.now()

  /** 时间段类型下需开始/结束都合法；截止类型恒为 true。 */
  val intervalValid: Boolean
    get() = !isInterval || isIntervalValid(startTime, endTime)

  /** 可保存：标题非空且时间段合法。 */
  val canConfirm: Boolean
    get() = title.text.isNotBlank() && intervalValid

  /** 相对初始是否有改动，用于未保存退出确认。 */
  val isChanged: Boolean
    get() = title.text.toString() != (origin?.title ?: "") ||
      detail.text.toString() != (origin?.detail ?: "") ||
      type != (origin?.type ?: ScheduleEntity.TYPE_OTHER) ||
      startTime != (origin?.startTime ?: "") ||
      endTime != (origin?.endTime ?: "") ||
      recurrence != origin?.recurrence.toDraft()

  /** 保存写库用的标题（去首尾空格）。 */
  val outputTitle: String get() = title.text.toString().trim()

  /** 保存写库用的备注（去首尾空格）。 */
  val outputDetail: String get() = detail.text.toString().trim()

  /** 开始时间：仅「时间段」类型有效，空串归一为 null。 */
  val outputStartTime: String? get() = if (isInterval) startTime.takeIf { it.isNotBlank() } else null

  /** 结束 / 截止时间，空串归一为 null。 */
  val outputEndTime: String? get() = endTime.takeIf { it.isNotBlank() }

  /** 保存写库用的重复定义（不重复为 null）；编辑整条系列时保留原 exdate/overrides。 */
  val outputRecurrence: Recurrence? get() = recurrence.toRecurrence(anchorDate, base = origin?.recurrence)

  /**
   * 用当前表单产出一个完整 [ScheduleEntity]（用于「编辑整条系列」与「此次及后续」的新建源）。
   *
   * @param base 作为不可编辑字段（id/置顶/完成等）的来源；新建时传 null。
   */
  fun toEntity(base: ScheduleEntity?): ScheduleEntity = ScheduleEntity(
    todoId = base?.todoId ?: 0L,
    title = outputTitle,
    detail = outputDetail,
    type = type,
    startTime = outputStartTime,
    endTime = outputEndTime ?: "",
    recurrence = outputRecurrence,
    remindMode = base?.remindMode ?: com.cyxbs.pages.schedule.data.model.ScheduleRemindMode(),
    remindMinutes = base?.remindMinutes ?: -1,
    isDone = base?.isDone ?: 0,
    isPinned = base?.isPinned ?: 0,
    isOvered = base?.isOvered ?: 0,
    lastModifyTime = base?.lastModifyTime ?: 0L,
  )

  /** 校验时间段：开始、结束都可解析且开始 < 结束。 */
  private fun isIntervalValid(startTime: String, endTime: String): Boolean {
    val s = parseScheduleDateTime(startTime) ?: return false
    val e = parseScheduleDateTime(endTime) ?: return false
    return compareDateTime(s, e) < 0
  }

  /** 比较两个解析后的时间；无时分按 0 分处理。 */
  private fun compareDateTime(a: ScheduleDateTime, b: ScheduleDateTime): Int {
    val dateCmp = a.date.compareTo(b.date)
    if (dateCmp != 0) return dateCmp
    return (a.minuteOfDay ?: 0).compareTo(b.minuteOfDay ?: 0)
  }
}

/** 按 [editSchedule] 记忆一份 [EditScheduleState]；切换编辑对象时重建。 */
@Composable
internal fun rememberEditScheduleState(editSchedule: ScheduleEntity?): EditScheduleState =
  remember(editSchedule) { EditScheduleState(editSchedule) }
