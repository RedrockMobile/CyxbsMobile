package com.cyxbs.pages.home.mobile.viewmodel

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.base.ui.BaseViewModel
import cyxbsmobile.cyxbs_pages.home.generated.resources.Res
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_explore_selected
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_explore_unselected
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_fairground_selectored
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_fairground_unselectored
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_mine_red_dot_unselected
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_mine_selected
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_ic_mine_unselected
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_nav_discover
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_nav_fairground
import cyxbsmobile.cyxbs_pages.home.generated.resources.home_nav_mine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
class BottomNavViewModel : BaseViewModel() {

  val discoverItem = BottomNavItem(
    Res.string.home_nav_discover,
    Res.drawable.home_ic_explore_selected,
    Res.drawable.home_ic_explore_unselected,
    Res.drawable.home_ic_explore_unselected,
  )
  val fairgroundItem = BottomNavItem(
    Res.string.home_nav_fairground,
    Res.drawable.home_ic_fairground_selectored,
    Res.drawable.home_ic_fairground_unselectored,
    Res.drawable.home_ic_fairground_unselectored,
  )
  val mineItem = BottomNavItem(
    Res.string.home_nav_mine,
    Res.drawable.home_ic_mine_selected,
    Res.drawable.home_ic_mine_unselected,
    Res.drawable.home_ic_mine_red_dot_unselected,
  )

  val items = persistentListOf(discoverItem, fairgroundItem, mineItem)

  val height: Dp = 60.dp

  val selectedItem: MutableStateFlow<BottomNavItem> = MutableStateFlow(items[0])

  val offsetYRadio: MutableFloatState = mutableFloatStateOf(0F)
  val alpha: MutableFloatState = mutableFloatStateOf(1F)

  fun select(item: BottomNavItem) {
    selectedItem.value = item
  }

  @Stable
  inner class BottomNavItem(
    val title: StringResource,
    val selectedIcon: DrawableResource,
    val unselectedIcon: DrawableResource,
    val unselectedRedDotIcon: DrawableResource,
  ) {

    private val redDot = MutableStateFlow(false)

    fun setRedDot(has: Boolean) {
      if (has && selectedItem.value === this) return // 如果已经处于选中状态，则不显示红点
      redDot.value = has
    }

    fun observerRedDot() = redDot.asStateFlow()
  }
}