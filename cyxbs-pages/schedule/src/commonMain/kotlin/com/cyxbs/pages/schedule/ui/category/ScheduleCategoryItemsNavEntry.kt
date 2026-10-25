package com.cyxbs.pages.schedule.ui.category

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_CATEGORY_ITEMS
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel
import kotlinx.serialization.Serializable

/**
 * 打开全部或指定分组的完整日程列表。
 *
 * [categoryId] 为 null 时表示不按分组过滤；名称仅用于首帧标题，具体分组仍以仓库中的最新名称为准。
 */
@Serializable
data class ScheduleCategoryItemsNavArgument(
  val categoryId: String?,
  val categoryName: String,
) : AppNavArgument

/** 分组日程入口；独立 ViewModel 只持有该页面的批量选择与编辑会话。 */
@AppNav(route = NAV_SCHEDULE_CATEGORY_ITEMS)
class ScheduleCategoryItemsNavEntry : AppNavEntry<ScheduleCategoryItemsNavArgument>() {
  override fun isNeedLogin(argument: ScheduleCategoryItemsNavArgument): Boolean = true

  override fun getContentKey(argument: ScheduleCategoryItemsNavArgument): String =
    "schedule_category_items_${argument.categoryId ?: "all"}"

  @Composable
  override fun Content(argument: ScheduleCategoryItemsNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel() }
    ScheduleCategoryItemsPage(argument, viewModel, argument::popBackStack)
  }
}
