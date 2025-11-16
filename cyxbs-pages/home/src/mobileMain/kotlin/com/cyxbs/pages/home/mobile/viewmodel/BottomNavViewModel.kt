package com.cyxbs.pages.home.mobile.viewmodel

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.navigation.HomeNavArgument
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.notification.api.INotificationService
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/4
 */
class BottomNavViewModel(
  val homeNavArgument: HomeNavArgument,
) : BaseViewModel() {

  val height: Dp = 60.dp
  val offsetYRadio: MutableFloatState = mutableFloatStateOf(0F)
  val alpha: MutableFloatState = mutableFloatStateOf(1F)

  val discoverItem = BottomNavItem(
    Res.string.home_nav_discover,
    Res.drawable.home_ic_explore_selected,
    Res.drawable.home_ic_explore_unselected,
  )

  val fairgroundItem = BottomNavItem(
    Res.string.home_nav_fairground,
    Res.drawable.home_ic_fairground_selectored,
    Res.drawable.home_ic_fairground_unselectored,
  )

  val mineItem = BottomNavItem(
    Res.string.home_nav_mine,
    Res.drawable.home_ic_mine_selected,
    Res.drawable.home_ic_mine_unselected,
    Res.drawable.home_ic_mine_red_dot_unselected,
    redObserveFlow = INotificationService::class.impl().unreadCount.map { it > 0 },
  )

  val items = persistentListOf(discoverItem, fairgroundItem, mineItem)
  val selectedItem: MutableStateFlow<BottomNavItem> = MutableStateFlow(
    when (homeNavArgument.page) {
      "discover" -> discoverItem
      "fairground" -> fairgroundItem
      "mine" -> mineItem
      else -> discoverItem
    }
  )

  fun select(item: BottomNavItem) {
    selectedItem.value = item
    item.select()
  }

  @Stable
  inner class BottomNavItem(
    val title: StringResource,
    val selectedIcon: DrawableResource, // 选中时图标
    val unselectedIcon: DrawableResource, // 未选中时图标
    val unselectedRedDotIcon: DrawableResource = unselectedIcon, // 未选中时存在红点的图标，
    val redObserveFlow: Flow<Boolean> = emptyFlow(), // 监听红点状态的 Job
  ) {

    private val redDot = MutableStateFlow(false)

    // redObserveFlow 的监听需要使用到 selectedItem，
    // 如果直接以类变量初始化，则此时 selectedItem 还没有初始化，会导致空指针
    private val redJob by lazy {
      redObserveFlow.distinctUntilChanged().collectLaunch {
        if (it && selectedItem.value !== this) {
          redDot.value = true
        }
      }
    }

    fun select() {
      if (redDot.value) {
        redJob.cancel() // 底部导航栏按钮只观察一次红点，因为掌邮使用时间不长，一般不会有增量消息
        redDot.value = false
      }
    }

    fun observerRedDot(): StateFlow<Boolean> {
      redJob.hashCode() // 触发第一次初始化
      return redDot.asStateFlow()
    }
  }
}