package com.cyxbs.pages.schedule.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.schedule.data.model.ScheduleEntity
import com.cyxbs.pages.schedule.data.model.ScheduleRemindMode
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncRepository
import com.cyxbs.pages.schedule.data.repository.ScheduleSyncState
import com.cyxbs.pages.schedule.ui.edit.EditScheduleModelState
import com.cyxbs.pages.schedule.ui.edit.EditScope
import com.cyxbs.pages.schedule.ui.edit.applyScheduleDelete
import com.cyxbs.pages.schedule.ui.edit.applyScheduleEdit
import kotlinx.coroutines.flow.StateFlow

/**
 * todo 主页面 ViewModel。
 *
 * 订阅 [ScheduleSyncRepository] 的状态，暴露给 UI 层。
 * 提供 UI 操作方法：创建、编辑、删除、置顶、完成。
 */
class ScheduleMainViewModel : BaseViewModel() {

  private val repository = ScheduleSyncRepository()

  /** 所有 todo 列表。 */
  val allSchedules: StateFlow<List<ScheduleEntity>> = repository.todos

  /** 同步状态。 */
  val syncState: StateFlow<ScheduleSyncState> = repository.syncState

  /** 分组候选池。 */
  val categories: StateFlow<List<String>> = repository.categories

  /** 新增自定义分类。 */
  fun addCategory(name: String) {
    launchByViewModelScope { repository.addCategory(name) }
  }

  /** 删除分类（仅未被使用的）。 */
  fun removeCategory(name: String) {
    launchByViewModelScope { repository.removeCategory(name) }
  }

  /** 是否处于批量管理模式。 */
  private val _isManageMode = kotlinx.coroutines.flow.MutableStateFlow(false)
  val isManageMode: StateFlow<Boolean> = _isManageMode

  /** 管理模式下的已选 todo id 集合。 */
  private val _selectedIds = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
  val selectedIds: StateFlow<Set<Long>> = _selectedIds

  /**
   * 初始化 ViewModel。
   *
   * 调用 repository.initialize() 加载本地快照并触发后台同步。
   * 首次使用且本地为空时，自动插入 3 条引导 todo。
   */
  fun initialize() {
    launchByViewModelScope {
      repository.initialize()

      // 新手教程：首次使用且列表为空时插入引导 todo
      if (repository.isFirstUse() && repository.todos.value.isEmpty()) {
        repository.markFirstUseDone()
      }
    }
  }

  /**
   * 主动触发一次同步。
   *
   * 用于手动刷新。
   */
  fun sync() {
    launchByViewModelScope {
      repository.sync()
    }
  }

  /**
   * 创建 todo。
   */
  fun createSchedule(
    title: String,
    detail: String = "",
    type: String = ScheduleEntity.TYPE_OTHER,
    startTime: String = "",
    endTime: String = "",
    remindMode: ScheduleRemindMode = ScheduleRemindMode(),
  ) {
    launchByViewModelScope {
      repository.createSchedule(title, detail, type, startTime, endTime, remindMode)
    }
  }

  /**
   * 更新 todo。
   */
  fun updateSchedule(todo: ScheduleEntity) {
    launchByViewModelScope {
      repository.updateSchedule(todo)
    }
  }

  /**
   * 删除 todo。
   */
  fun deleteSchedule(todoId: Long) {
    launchByViewModelScope {
      repository.deleteSchedule(todoId)
    }
  }

  /**
   * 置顶 / 取消置顶。
   */
  fun pinSchedule(todoId: Long, isPinned: Boolean) {
    launchByViewModelScope {
      repository.pinSchedule(todoId, isPinned)
    }
  }

  /**
   * 完成 todo。
   */
  fun completeSchedule(todoId: Long, occurrenceDate: Date? = null) {
    launchByViewModelScope {
      repository.completeSchedule(todoId, occurrenceDate)
    }
  }

  /**
   * 统一编辑入口保存：新建 / 按三态更新。
   *
   * @param occurrenceDate 编辑「重复系列某一次」时的锚点日期；编辑整条或新建可为 null。
   */
  fun saveSchedule(
    state: EditScheduleModelState,
    scope: EditScope,
    occurrenceDate: Date?,
  ) {
    launchByViewModelScope {
      repository.applyScheduleEdit(state, scope, occurrenceDate)
    }
  }

  /**
   * 统一编辑入口删除：按三态删除。
   */
  fun deleteScheduleScoped(
    todoId: Long,
    scope: EditScope,
    occurrenceDate: Date?,
  ) {
    launchByViewModelScope {
      repository.applyScheduleDelete(todoId, scope, occurrenceDate)
    }
  }

  /** 进入批量管理模式。 */
  fun enterManageMode() {
    _isManageMode.value = true
    _selectedIds.value = emptySet()
  }

  /** 退出批量管理模式，清空选中。 */
  fun exitManageMode() {
    _isManageMode.value = false
    _selectedIds.value = emptySet()
  }

  /** 切换某个 todo 的选中状态。 */
  fun toggleSelect(todoId: Long) {
    val current = _selectedIds.value.toMutableSet()
    if (!current.add(todoId)) current.remove(todoId)
    _selectedIds.value = current
  }

  /** 选中当前列表中的全部 todo。 */
  fun selectAll(todoIds: List<Long>) {
    _selectedIds.value = todoIds.toSet()
  }

  /** 清空选中。 */
  fun clearSelection() {
    _selectedIds.value = emptySet()
  }

  /** 批量置顶当前选中的 todo。 */
  fun batchPin() {
    val ids = _selectedIds.value.toList()
    if (ids.isEmpty()) return
    launchByViewModelScope {
      ids.forEach { repository.pinSchedule(it, true) }
      _isManageMode.value = false
      _selectedIds.value = emptySet()
    }
  }

  /** 批量删除当前选中的 todo。 */
  fun batchDelete() {
    val ids = _selectedIds.value.toList()
    if (ids.isEmpty()) return
    launchByViewModelScope {
      ids.forEach { repository.deleteSchedule(it) }
      _isManageMode.value = false
      _selectedIds.value = emptySet()
    }
  }

  /**
   * 清空账号数据。
   *
   * 用于退出登录时清理。
   */
  fun clearAccount() {
    launchByViewModelScope {
      repository.clearAccount()
    }
  }
}
