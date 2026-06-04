package com.cyxbs.pages.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.Platform
import com.cyxbs.components.config.appPlatform
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.navigation.NAV_ABOUT
import com.cyxbs.components.navigation.NAV_COURSE_FIND
import com.cyxbs.components.navigation.NAV_EDIT_INFO
import com.cyxbs.components.navigation.NAV_EMPTY_ROOM
import com.cyxbs.components.navigation.NAV_FAIRGROUND
import com.cyxbs.components.navigation.NAV_FOOD
import com.cyxbs.components.navigation.NAV_MAP
import com.cyxbs.components.navigation.NAV_SCHOOL_CAR
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.CourseNavArgument
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.login.api.LoginNavArgument

/**
 *
 *
 * @author 985892345
 * @date 2025/9/22
 */

// 在这里注册你的 Compose 页面用于 desktop 端的测试
// 如果你的页面没有返回键，则按 ESC 键进行返回
private val itemList = listOf(
  ActionItem("地图", Platform.Web) {
    AppScheme.jump("cyxbs://$NAV_MAP")
  },
  ActionItem("我的课表") {
    val stuNum = IAccountService::class.impl().stuNum
    if (stuNum == null) {
      toast("学号不存在，请检查是否已登陆")
    } else {
      CourseNavArgument(stuNum = stuNum).navigate()
    }
  },
  ActionItem("关于我们") {
    AppScheme.jump("cyxbs://$NAV_ABOUT")
  },
  ActionItem("美食咨询处") {
    AppScheme.jump("cyxbs://$NAV_FOOD")
  },
  ActionItem("空教室查询") {
    AppScheme.jump("cyxbs://$NAV_EMPTY_ROOM")
  },
  ActionItem("校车查询", Platform.Web){
    AppScheme.jump("cyxbs://$NAV_SCHOOL_CAR")
  },
  ActionItem("查询课表") {
    AppScheme.jump("cyxbs://$NAV_COURSE_FIND")
  },
  ActionItem("邮乐园") {
    AppScheme.jump("cyxbs://$NAV_FAIRGROUND")
  },
  ActionItem("资料编辑") {
    AppScheme.jump("cyxbs://$NAV_EDIT_INFO")
  },


  // 退出登陆放到最后，其他测试页面放到上面👆
  ActionItem("退出登录") {
    IAccountEditService::class.impl().onLogout()
    LoginNavArgument.navigate(HomeNavArgument(), clearStack = true)
  },
)

@Composable
fun AdaptiveHomePage(argument: HomeNavArgument) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(74.dp),
      modifier = Modifier,
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      userScrollEnabled = false,
    ) {
      items(
        items = itemList,
        key = {
          it.name
        }) {
        SelectorItem(it)
      }
    }
  }
}

@Stable
class ActionItem(
  val name: String,
  vararg val excludePlatform: Platform, // 排除某些平台
  val onClick: () -> Unit
)

// 选择器的单个Item
@Composable
private fun SelectorItem(item: ActionItem, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .padding(3.dp)
      .size(74.dp, 29.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(color = 0xFFF5F6F8.dark(0xFF111111))
      .clickable {
        if (item.excludePlatform.contains(appPlatform)) {
          toast("当前平台不支持此功能")
          return@clickable
        }
        item.onClick.invoke()
      }
  ) {
    // 文字
    Text(
      modifier = Modifier.align(Alignment.Center),
      text = item.name,
      fontSize = 12.sp,
      color = LocalAppColors.current.tvLv3
    )
  }

}