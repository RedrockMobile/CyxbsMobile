package com.cyxbs.pages.schedule.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.cyxbs.pages.schedule.data.repository.v2.ScheduleRepositoryProvider
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.domain.repository.ScheduleSyncResult

/**
 * 分类的共享 UI 目录。
 *
 * [actualCategories] 是仓库已经确认或本地 pending 后的真实分类；[selectableCategories] 在此基础上追加三个
 * 惰性默认候选。目录不维护第二份可变状态，创建、更新、删除完成后仍由仓库快照驱动所有页面刷新。
 */
internal class ScheduleCategoryCatalog internal constructor(
  val actualCategories: List<ScheduleCategory>,
  val selectableCategories: List<ScheduleCategory>,
  private val repository: ScheduleRepository,
) {

  /** 返回当前选择尚未落入仓库时需要与日程原子创建的固定默认分类。 */
  fun findMissingDefaultCategory(selectedId: CategoryId?): ScheduleCategory? =
    findMissingDefaultScheduleCategory(selectedId, actualCategories)

  /** 创建完整分类；同名等业务约束由仓库 reducer 统一校验。 */
  suspend fun create(category: ScheduleCategory): ScheduleSyncResult? =
    repository.execute(ScheduleCommand.CreateCategory(category))

  /** 更新完整分类；结果发布后所有使用本目录的页面会从同一仓库快照刷新。 */
  suspend fun update(category: ScheduleCategory): ScheduleSyncResult? =
    repository.execute(ScheduleCommand.UpdateCategory(category))

  /**
   * 删除真实分类。
   *
   * 固定分类不允许删除；其他分类是否仍被日程引用由仓库执行时再次校验，避免 UI 快照过期造成误删。
   */
  suspend fun delete(categoryId: CategoryId): ScheduleSyncResult? {
    val category = actualCategories.firstOrNull { it.id == categoryId }
    require(category == null || !isFixedScheduleCategory(category)) {
      "fixed schedule category cannot be deleted"
    }
    return repository.execute(ScheduleCommand.DeleteCategory(categoryId))
  }
}

/**
 * 订阅当前账号仓库并返回共享分类目录。
 *
 * 仓库 façade 负责账号隔离；切号或任意入口修改分类后，新的快照会同步更新清单、课表弹窗和后续管理页面。
 */
@Composable
internal fun rememberScheduleCategoryCatalog(
  repository: ScheduleRepository = ScheduleRepositoryProvider.repository,
): ScheduleCategoryCatalog {
  val snapshot by repository.snapshot.collectAsState()
  return remember(repository, snapshot.categories) {
    ScheduleCategoryCatalog(
      actualCategories = snapshot.categories,
      selectableCategories = mergeScheduleCategories(snapshot.categories),
      repository = repository,
    )
  }
}

/**
 * 日程编辑器的三个惰性默认分类。
 *
 * 它们只是编辑器候选项，不会在页面初始化时写 Room 或请求后端。用户首次选择并保存日程时，保存命令才会把
 * 对应 Category 与 Schedule 放入同一个日常聚合请求；稳定 UUID 使请求失败后的本地 pending 仍能被同一候选识别。
 */
internal val ScheduleDefaultCategories: List<ScheduleCategory> = listOf(
  ScheduleCategory(CategoryId("019d0000-0000-7000-8000-000000000001"), 0, "学习", null, 0),
  ScheduleCategory(CategoryId("019d0000-0000-7000-8000-000000000002"), 0, "生活", null, 1),
  ScheduleCategory(CategoryId("019d0000-0000-7000-8000-000000000003"), 0, "其他", null, 2),
)

/** “学习 / 生活 / 其他”是产品固定分组；即使已成为仓库事实，也不向 UI 暴露删除入口。 */
internal fun isFixedScheduleCategory(category: ScheduleCategory): Boolean =
  ScheduleDefaultCategories.any { fixed ->
    fixed.name.equals(category.name.trim(), ignoreCase = true)
  }

/**
 * 将服务端/本地真实分类与默认候选合并。
 *
 * 同 identity 或同名分类优先使用真实资源，避免后端已有“学习”等分类时再创建重复项；其余真实分类按自身
 * sortOrder 追加。返回的默认候选仍不是仓库事实，调用方不得用它们清理或覆盖 snapshot。
 */
internal fun mergeScheduleCategories(
  actual: List<ScheduleCategory>,
): List<ScheduleCategory> {
  val remaining = actual.toMutableList()
  val defaults = ScheduleDefaultCategories.map { candidate ->
    val actualIndex = remaining.indexOfFirst {
      it.id == candidate.id || it.name.trim() == candidate.name
    }
    if (actualIndex < 0) candidate else remaining.removeAt(actualIndex)
  }
  return defaults + remaining.sortedWith(
    compareBy<ScheduleCategory> { it.sortOrder }.thenBy { it.id.value },
  )
}

/** 仅当 [selectedId] 指向尚未成为仓库事实的默认候选时，返回需要与日程同批创建的 Category。 */
internal fun findMissingDefaultScheduleCategory(
  selectedId: CategoryId?,
  actual: List<ScheduleCategory>,
): ScheduleCategory? = ScheduleDefaultCategories.firstOrNull { candidate ->
  candidate.id == selectedId && actual.none { it.id == candidate.id }
}
