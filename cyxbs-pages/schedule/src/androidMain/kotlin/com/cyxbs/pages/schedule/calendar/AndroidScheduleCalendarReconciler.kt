package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity

/**
 * 将 Schedule 当前目标投影与 Android CalendarProvider 中已记录的 eventId 做幂等对账。
 *
 * Provider 中的 `cyxbs://schedule` deep link 是映射事实源；目标状态始终由 Schedule 数据重新计算。
 * 每个操作独立记录成败，下一次数据变化或“重新导入”可继续重试。
 */
internal object AndroidScheduleCalendarReconciler {

  /**
   * Calendar Provider 的最小操作边界。生产实现委托 [AndroidScheduleCalendarProvider]；测试可替换为
   * fake，以验证对账状态转换而不访问设备日历数据库。
   */
  internal interface Gateway {
    fun hasPermission(): Boolean
    fun getManagedEvents(): Map<String, Long>
    fun add(projection: ScheduleCalendarProjection): Long?
    fun update(eventId: Long, projection: ScheduleCalendarProjection): Boolean
    fun delete(eventId: Long): Boolean
  }

  private object ProviderGateway : Gateway {
    override fun hasPermission() = AndroidScheduleCalendarProvider.hasPermission()
    override fun getManagedEvents() = AndroidScheduleCalendarProvider.getManagedEvents()
    override fun add(projection: ScheduleCalendarProjection) =
      AndroidScheduleCalendarProvider.add(projection)
    override fun update(eventId: Long, projection: ScheduleCalendarProjection) =
      AndroidScheduleCalendarProvider.update(eventId, projection)
    override fun delete(eventId: Long) = AndroidScheduleCalendarProvider.delete(eventId)
  }

  /** 仅供测试替换；每个测试结束必须恢复为 null，避免污染其他用例。 */
  internal var testGateway: Gateway? = null
  private val gateway: Gateway get() = testGateway ?: ProviderGateway

  /**
   * 对账当前全部日程。
   *
   * 带例外的重复系列只物化“上月到未来两年”的滚动窗口，控制系统事件数量；纯 RRULE 系列不受该
   * 窗口限制。先删除目标中已不存在的 key，再更新/新增目标事件。
   *
   * @param todos 当前账号完整的 Schedule 快照，不能传仅可见日期的子集。
   * @param today 展开窗口基准；参数化便于测试和稳定重导入结果。
   * @return 所有 Provider 操作是否均成功；部分失败时成功项仍会保存映射。
   */
  fun reconcileAll(todos: List<ScheduleEntity>, today: Date = Date.now()): Boolean {
    if (!gateway.hasPermission()) return false
    val target = todos.flatMap {
      ScheduleCalendarProjectionFactory.create(
        todo = it,
        rangeStart = today.minusMonths(1),
        rangeEnd = today.plusYears(2),
      )
    }.associateBy { it.key }
    val old = gateway.getManagedEvents()
    var success = true

    (old.keys - target.keys).forEach { key ->
      val deleted = gateway.delete(old.getValue(key))
      if (!deleted) success = false
    }
    target.forEach { (key, projection) ->
      val oldId = old[key]
      if (oldId == null) {
        if (gateway.add(projection) == null) success = false
      } else if (!gateway.update(oldId, projection)) {
        // 用户可能已在系统日历删除事件；update 失败时以同一 deep link 自愈创建。
        if (gateway.add(projection) == null) success = false
      }
    }
    return success
  }

  /**
   * 仅对账一个 todo，不触碰其他日程的系统事件。
   *
   * 主要用于单条 CRUD 和真机集成测试；[todo] 为 null 时删除该 [todoId] 的所有投影。与全量对账相同，
   * 带例外系列使用上月到未来两年的窗口。
   */
  fun reconcileTodo(todoId: Long, todo: ScheduleEntity?, today: Date = Date.now()): Boolean {
    if (!gateway.hasPermission()) return false
    val prefix = "$todoId:"
    val target = todo?.let {
      ScheduleCalendarProjectionFactory.create(
        todo = it,
        rangeStart = today.minusMonths(1),
        rangeEnd = today.plusYears(2),
      )
    }.orEmpty().associateBy { it.key }
    val old = gateway.getManagedEvents().filterKeys { it.startsWith(prefix) }
    var success = true

    (old.keys - target.keys).forEach { key ->
      if (!gateway.delete(old.getValue(key))) success = false
    }
    target.forEach { (key, projection) ->
      val oldId = old[key]
      if (oldId == null) {
        if (gateway.add(projection) == null) success = false
      } else if (!gateway.update(oldId, projection) && gateway.add(projection) == null) {
        success = false
      }
    }
    return success
  }

  /**
   * 自动导入关闭时仍清理已删除、整体完成或关闭提醒的 todo；不修改其他既有事件。
   *
   * 删除后的 todo 已不在 [todos] 中，因此必须从 Provider deep link 反解析 todoId，不能只遍历当前快照。
   */
  fun removeInactiveTodos(todos: List<ScheduleEntity>): Boolean {
    val activeById = todos.associateBy { it.todoId }
    var success = true
    gateway.getManagedEvents().forEach { (key, eventId) ->
      val todoId = key.substringBefore(':').toLongOrNull()
      val todo = todoId?.let(activeById::get)
      if (todo == null || todo.remindMinutes < 0 || todo.isDone == 1) {
        if (!gateway.delete(eventId)) success = false
      }
    }
    return success
  }

  /** 删除该 todo 已导出的事件；用于关闭提醒、完成和删除。 */
  fun removeTodo(todoId: Long): Boolean {
    val prefix = "$todoId:"
    var success = true
    gateway.getManagedEvents()
      .filterKeys { it.startsWith(prefix) }
      .forEach { (_, eventId) ->
        if (!gateway.delete(eventId)) success = false
      }
    return success
  }

  /** 清理所有由 Schedule 记录的系统事件，不影响其他业务日历。 */
  fun clearAll(): Boolean {
    var success = true
    gateway.getManagedEvents().forEach { (_, eventId) ->
      if (!gateway.delete(eventId)) success = false
    }
    return success
  }
}
