package com.cyxbs.pages.schedule.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.*
import com.cyxbs.pages.schedule.domain.repository.*
import com.cyxbs.pages.schedule.ui.edit.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 日程主页面状态与异步命令的生命周期持有者。
 *
 * snapshot 直接暴露仓库事实流；所有写命令在 ViewModel scope 中执行，页面销毁时协程会取消，UI 不直接触碰 DTO。
 * 是否允许编辑只取决于当前账号是否存在 local-first delegate，不能由一次同步状态动态改变。
 */
class ScheduleMainViewModel(
  /** 页面与其共享编辑弹窗必须使用同一仓库；internal 仅用于同模块 UI 依赖注入，不暴露存储实现。 */
  internal val repository: ScheduleRepository = ScheduleRepositoryProvider.repository,
) : BaseViewModel() {
  val snapshot: StateFlow<ScheduleSnapshot> = repository.snapshot

  /** 读取当前精确账号 delegate 的编辑能力；没有 delegate 时为只读。 */
  val mutationMode: ScheduleRepositoryMutationMode
    get() = repository.mutationMode

  private val _isManageMode = MutableStateFlow(false)
  val isManageMode: StateFlow<Boolean> = _isManageMode
  private val _selectedIds = MutableStateFlow<Set<ScheduleId>>(emptySet())
  val selectedIds: StateFlow<Set<ScheduleId>> = _selectedIds

  /** 初始化当前账号的本地可信快照；同步失败由仓库状态表达，不改变 local-first 编辑能力。 */
  fun initialize() = launchByViewModelScope { repository.initialize() }

  /**
   * 判断当前账号是否可以向仓库下发编辑命令。
   *
   * local-first 始终允许离线写入，read-only 始终拒绝；同步状态不参与门禁。
   */
  fun canSubmitMutation(): Boolean = mutationMode.canSubmitScheduleMutation()

  /**
   * 请求当前仓库重新同步。
   *
   * RequestSync 用于首次进入、网络恢复或主动对账，提交 typed confirmed+pending；它不是编辑命令，因此不套用
   * [canSubmitMutation]，仓库仍负责当前账号门禁。
   */
  fun sync() = launchByViewModelScope {
    repository.execute(ScheduleCommand.RequestSync)
  }

  /**
   * 在 ViewModel scope 中保存编辑状态，并按 [scope]/[recurrenceId] 路由到整系列、单实例或拆分命令。
   * [newCategory] 仅用于所选固定默认分类尚不存在的情况，并与日程 CREATE/PATCH 放入同一原子命令。
   * 调用立即返回，实际仓库写入异步完成；范围所需实例 ID 缺失时由路由层拒绝执行。
   */
  fun saveSchedule(
    state: EditScheduleModelState,
    scope: EditScope,
    recurrenceId: RecurrenceId?,
    newCategory: ScheduleCategory? = null,
  ) =
    launchByViewModelScope {
      if (!canSubmitMutation()) return@launchByViewModelScope
      repository.applyScheduleEdit(
        state,
        scope,
        recurrenceId,
        ScheduleRepositoryProvider.idGenerators,
        ScheduleRepositoryProvider.clock,
        newCategory,
      )
    }

  /** 异步按范围删除；没有当前账号 delegate 时退出，单实例删除仍由命令路由转换为 occurrence 操作。 */
  fun deleteScheduleScoped(id: ScheduleId, scope: EditScope, recurrenceId: RecurrenceId?) =
    launchByViewModelScope {
      if (!canSubmitMutation()) return@launchByViewModelScope
      repository.applyScheduleDelete(id, scope, recurrenceId, ScheduleRepositoryProvider.clock)
    }

  /**
   * 异步切换完成态：非重复项写系列命令，重复项按稳定 [recurrenceId] 更新或创建实例例外。
   * 已存在例外时保留其余原子字段，只改变状态与更新时间，避免完成操作覆盖内容修改。
   */
  fun completeSchedule(id: ScheduleId, recurrenceId: RecurrenceId?, completed: Boolean = true) =
    launchByViewModelScope {
      if (!canSubmitMutation()) return@launchByViewModelScope
      repository.applyScheduleCompletion(
        scheduleId = id,
        recurrenceId = recurrenceId,
        completed = completed,
        clock = ScheduleRepositoryProvider.clock,
      )
    }

  /**
   * 切换日程的课表投射状态，并沿用普通 Update 的 local-first 保存与远端重试链路。
   *
   * 无时间日程不能投射；[onChanged] 只在本地命令完成后回调，远端失败不会撤销已经保存的 pending。
   */
  fun toggleCourseProjection(id: ScheduleId, onChanged: (Boolean) -> Unit = {}) =
    launchByViewModelScope {
      if (!canSubmitMutation()) return@launchByViewModelScope
      val schedule = snapshot.value.schedules.firstOrNull { it.id == id } ?: return@launchByViewModelScope
      // 原生事务的课表身份不可取消；关联清单只改变 todoState，不改变其事务来源。
      if (schedule.kind == ScheduleKind.AFFAIR) return@launchByViewModelScope
      if (schedule.timing == ScheduleTiming.Unscheduled) return@launchByViewModelScope
      val linked = !schedule.linkedToCourse
      repository.execute(
        ScheduleCommand.Update(
          schedule.copy(
            linkedToCourse = linked,
            updatedAt = ScheduleRepositoryProvider.clock.now(),
          ),
        ),
      )
      onChanged(linked)
    }

  fun enterManageMode() { _isManageMode.value = true; _selectedIds.value = emptySet() }
  fun exitManageMode() { _isManageMode.value = false; _selectedIds.value = emptySet() }
  fun toggleSelect(id: ScheduleId) { _selectedIds.value = _selectedIds.value.toMutableSet().apply { if (!add(id)) remove(id) } }
  fun selectAll(ids: List<ScheduleId>) { _selectedIds.value = ids.toSet() }
  fun clearSelection() { _selectedIds.value = emptySet() }
  /**
   * 快照当前选择后，在仓库冻结的批量 binding 内串行删除。
   *
   * 每条命令前复核当前账号仍可编辑；账号 façade 把整批选择冻结到调用开始时的 delegate，避免切号后把剩余旧选择
   * 发给新账号。无论仓库提前停止、拒绝/抛错还是全部完成，finally 都会收起管理模式并清空选择。
   */
  fun batchDelete() {
    val ids = _selectedIds.value
    launchByViewModelScope {
      try {
        repository.executeSerially(
          commands = ids.map(ScheduleCommand::Delete),
          shouldContinue = ::canSubmitMutation,
        )
      } finally {
        exitManageMode()
      }
    }
  }
}
