package com.cyxbs.pages.schedule.ui.todo

import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory

/**
 * 邮子清单的三个惰性默认分类。
 *
 * 它们只是编辑器候选项，不会在页面初始化时写 Room 或请求后端。用户首次选择并保存日程时，保存命令才会把
 * 对应 Category 与 Schedule 放入同一个日常聚合请求；稳定 UUID 使请求失败后的本地 pending 仍能被同一候选识别。
 */
internal val ScheduleTodoDefaultCategories: List<ScheduleCategory> = listOf(
  ScheduleCategory(CategoryId("019d0000-0000-7000-8000-000000000001"), 0, "学习", null, 0),
  ScheduleCategory(CategoryId("019d0000-0000-7000-8000-000000000002"), 0, "生活", null, 1),
  ScheduleCategory(CategoryId("019d0000-0000-7000-8000-000000000003"), 0, "其他", null, 2),
)

/** “学习 / 生活 / 其他”是产品固定分组；即使已成为仓库事实，也不向 UI 暴露删除入口。 */
internal fun isScheduleTodoFixedCategory(category: ScheduleCategory): Boolean =
  ScheduleTodoDefaultCategories.any { fixed ->
    fixed.name.equals(category.name.trim(), ignoreCase = true)
  }

/**
 * 将服务端/本地真实分类与默认候选合并。
 *
 * 同 identity 或同名分类优先使用真实资源，避免后端已有“学习”等分类时再创建重复项；其余真实分类按自身
 * sortOrder 追加。返回的默认候选仍不是仓库事实，调用方不得用它们清理或覆盖 snapshot。
 */
internal fun mergeScheduleTodoCategories(
  actual: List<ScheduleCategory>,
): List<ScheduleCategory> {
  val remaining = actual.toMutableList()
  val defaults = ScheduleTodoDefaultCategories.map { candidate ->
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
internal fun findMissingScheduleTodoDefaultCategory(
  selectedId: CategoryId?,
  actual: List<ScheduleCategory>,
): ScheduleCategory? = ScheduleTodoDefaultCategories.firstOrNull { candidate ->
  candidate.id == selectedId && actual.none { it.id == candidate.id }
}
