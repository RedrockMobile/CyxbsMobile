package com.cyxbs.pages.schedule.domain.calendar

import kotlin.jvm.JvmInline

/**
 * 平台日历事件的短期定位引用。
 *
 * 该值是平台私有 opaque identifier，不参与业务身份、URI 或内容指纹；平台清理数据后可以失效，调用方必须
 * 依赖 canonical v2 URI 重新发现事件。长度上限用于拒绝异常平台返回并约束后续 durable link。
 */
@JvmInline
value class PlatformCalendarEventRef(val value: String) {
  init {
    require(value.isNotEmpty()) { "Platform calendar event reference must not be empty" }
    require(value.length <= MAX_LENGTH) { "Platform calendar event reference exceeds $MAX_LENGTH characters" }
  }

  companion object {
    const val MAX_LENGTH: Int = 512
  }
}

/**
 * 从平台日历 gateway 已验证的托管事件。
 *
 * 该类型只包含对账所需的规范身份、内容指纹与平台事件引用；planner 不假设未验证的外部数据能安全转为此类型，
 * 只接受 gateway 确认属于当前 scope、应用包名、v2 URI 且位于托管日历中的事件。平台引用可能失效，因此
 * 身份和重发现依赖 v2 URI，而非该引用。
 *
 * @property id v2 投影身份，由已验证的 canonical URI 严格解析得出。
 * @property fingerprint canonical 内容指纹；与目标投影一致时无需更新。
 * @property platformEventRef 平台事件定位引用；仅用于 update/delete，不作为身份依据。
 */
data class ManagedCalendarEvent(
  val id: CalendarProjectionId,
  val fingerprint: String,
  val platformEventRef: PlatformCalendarEventRef,
)

/** 对账规划的单个操作类型。 */
sealed interface CalendarExportAction {
  /** 当前托管日历中不存在该投影；需创建新事件。 */
  data class Create(val projection: CalendarEventProjection) : CalendarExportAction

  /**
   * 托管日历中存在对应投影，但内容指纹不一致；需用新投影更新现有事件。
   *
   * [existingEventRef] 复用已分配的平台引用，避免删除重建破坏用户在系统端的收藏或提醒历史。
   */
  data class Update(
    val projection: CalendarEventProjection,
    val existingEventRef: PlatformCalendarEventRef,
  ) : CalendarExportAction

  /**
   * 托管日历中存在的事件已不在目标投影中；需删除该投影副本。
   *
   * 只有当事件确实属于当前 scope 且可验证 v2 URI 时才会产生此操作；第三方事件、非法 URI 或错误 scope
   * 不会出现在 planner 输入中，因此不会误删。
   */
  data class Delete(val event: ManagedCalendarEvent) : CalendarExportAction

  /** 托管事件的身份与指纹均与目标投影一致；无需任何写操作。 */
  data class NoOp(val event: ManagedCalendarEvent) : CalendarExportAction

  /**
   * 投影明确标记为 unsupported；需告知用户或记录日志，绝不能降级为有限窗口单次事件。
   *
   * 该操作只表达诊断意图，gateway 不执行任何 Provider 写入。
   */
  data class Unsupported(val item: UnsupportedCalendarProjection) : CalendarExportAction
}

/**
 * 一次对账的完整执行计划。
 *
 * [actions] 保持稳定排序，便于测试和幂等执行；同一投影身份只会出现一次，重复或冲突由 planner 提前解决。
 */
data class CalendarExportPlan(
  val scope: CalendarExportScope,
  val actions: List<CalendarExportAction>,
)

/**
 * 纯 common 对账规划器。
 *
 * 只接受 gateway 已验证的托管事件，规划 Create/Update/Delete/NoOp/Unsupported 操作。Provider eventId 仅
 * 作缓存；应用重装或日历清除后，planner 能按 v2 URI 重新发现已有事件并恢复关联。
 */
object CalendarExportPlanner {
  /**
   * 断言 legacy Provider exporter 的整批计划不含单事件删除。
   *
   * legacy 路径尚未具备删除事件所需的全批确认语义，因此必须在任何计数、账号/快照复核或 Provider 回调之前
   * 一次性拒绝整批计划。这样即使稳定排序将 Create、Update 排在 Delete 前面，也不会产生前缀写入。
   * 此门禁仅约束未完成 W16/W17 finalized handoff 的旧 exporter；它不替代用户确认后由托管日历清理流程执行的
   * 整个日历删除。
   *
   * @param plan 即将交给 legacy Provider exporter 的完整计划。
   * @throws IllegalStateException 当计划包含任何 [CalendarExportAction.Delete] 时，拒绝整批执行。
   */
  fun assertLegacyProviderPlanDeleteFree(plan: CalendarExportPlan) {
    check(plan.actions.none { it is CalendarExportAction.Delete }) {
      "Legacy Provider export plan must not contain Delete actions"
    }
  }

  /**
   * 根据目标投影与现有托管事件生成对账计划。
   *
   * 若托管事件集中同一投影 ID 出现多次且内容不一致，保留与目标投影指纹相同的一条，或任选一条更新，
   * 并只清理同 scope 内的重复项。调用方必须保证 [managedEvents] 的每项都已通过 gateway 完整验证。
   *
   * @param result 由 [ScheduleCalendarProjectionFactory] 产生的规范投影结果。
   * @param managedEvents 当前托管日历中已确认的 v2 事件；未通过验证的外部数据不得出现。
   * @param scope 本次对账的导出空间；只规划该 scope 下的操作，不跨 scope。
   */
  fun plan(
    result: ScheduleCalendarProjectionResult,
    managedEvents: List<ManagedCalendarEvent>,
    scope: CalendarExportScope,
  ): CalendarExportPlan {
    require(result.events.all { it.id.scope == scope }) {
      "Projection result contains events outside the target scope"
    }
    require(managedEvents.all { it.id.scope == scope }) {
      "Managed events contain items outside the target scope"
    }
    result.events.forEach { projection ->
      val nativeExceptions = projection.nativeOccurrenceExceptions
      require(nativeExceptions == nativeExceptions.sortedBy { it.externalUri }) {
        "Native occurrence exceptions must use deterministic canonical order"
      }
      require(nativeExceptions.map { it.id }.distinct().size == nativeExceptions.size) {
        "Native occurrence exception identities must be unique"
      }
      require(nativeExceptions.all { exception ->
        projection.id.kind == CalendarProjectionKind.SERIES_MASTER &&
            projection.recurrenceRule != null &&
            exception.id.scope == scope &&
            exception.id.scheduleId == projection.id.scheduleId &&
            exception.id.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION &&
            CalendarProjectionUriCodec.encode(exception.id) == exception.externalUri
      }) {
        "Native occurrence exception plan does not belong to its series master"
      }
    }

    val targetById = result.events.associateBy { it.id }
    val managedById = managedEvents.groupBy { it.id }
    val unsupportedScheduleIds = result.unsupported.mapTo(mutableSetOf()) { it.scheduleId }

    val actions = mutableListOf<CalendarExportAction>()

    // 1. 处理目标投影：create 或 update
    for (projection in result.events) {
      val existing = managedById[projection.id].orEmpty()
      when {
        existing.isEmpty() -> {
          actions += CalendarExportAction.Create(projection)
        }
        existing.size == 1 -> {
          val event = existing.single()
          if (event.fingerprint == projection.fingerprint) {
            actions += CalendarExportAction.NoOp(event)
          } else {
            actions += CalendarExportAction.Update(projection, event.platformEventRef)
          }
        }
        else -> {
          // 同 ID 多条：优先保留指纹匹配的，否则更新第一条并删除其余
          val matched = existing.firstOrNull { it.fingerprint == projection.fingerprint }
          if (matched != null) {
            actions += CalendarExportAction.NoOp(matched)
            existing.filter { it.platformEventRef != matched.platformEventRef }
              .forEach { actions += CalendarExportAction.Delete(it) }
          } else {
            val chosen = existing.first()
            actions += CalendarExportAction.Update(projection, chosen.platformEventRef)
            existing.drop(1).forEach { actions += CalendarExportAction.Delete(it) }
          }
        }
      }
    }

    // 2. 删除不在目标中的托管事件
    for ((id, events) in managedById) {
      if (id !in targetById && id.scheduleId !in unsupportedScheduleIds) {
        events.forEach { actions += CalendarExportAction.Delete(it) }
      }
      // unsupported 表示当前版本无法安全表达最新系列，而不是业务事实已删除；保留既有 Provider master，
      // 避免新增 occurrence exception 时静默清空用户此前已导出的整个系列。
    }

    // 3. 附加 unsupported 诊断项
    result.unsupported.forEach { actions += CalendarExportAction.Unsupported(it) }

    return CalendarExportPlan(
      scope = scope,
      actions = actions.sortedBy { action ->
        when (action) {
          is CalendarExportAction.Create -> "1-create-${action.projection.externalUri}"
          is CalendarExportAction.Update -> "2-update-${action.projection.externalUri}"
          is CalendarExportAction.Delete -> "3-delete-${action.event.id.scheduleId.value}"
          is CalendarExportAction.NoOp -> "4-noop-${action.event.id.scheduleId.value}"
          is CalendarExportAction.Unsupported -> "5-unsupported-${action.item.scheduleId.value}"
        }
      },
    )
  }
}
