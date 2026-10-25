package com.cyxbs.pages.schedule.ui.main

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHEDULE_MAIN
import com.cyxbs.pages.schedule.api.ScheduleMainNavArgument
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.canSubmitScheduleMutation
import com.cyxbs.pages.schedule.ui.todo.main.ScheduleTodoPage
import com.cyxbs.pages.schedule.viewmodel.ScheduleMainViewModel

/**
 * 日程正式主页导航入口。
 *
 * `cyxbs://schedule` 是唯一正式入口，页面内部负责在清单与时间轴之间切换；定位参数可携带系列 ID
 * 与重复实例 identity，供首页 Feed 进入后滚动并高亮对应日程。
 */
@AppNav(route = NAV_SCHEDULE_MAIN)
class ScheduleMainNavEntry : AppNavEntry<ScheduleMainNavArgument>() {

  /** 日程读写依赖精确登录账号。 */
  override fun isNeedLogin(argument: ScheduleMainNavArgument): Boolean = true

  /** 主页面保持单例，新的定位参数由页面组合状态消费。 */
  override fun getContentKey(argument: ScheduleMainNavArgument): String = "schedule_main_singleton"

  /** 创建共享日程 ViewModel，并渲染整合后的清单/时间轴主页。 */
  @Composable
  override fun Content(argument: ScheduleMainNavArgument) {
    val viewModel = viewModel { ScheduleMainViewModel() }
    ScheduleTodoPage(argument = argument, viewModel = viewModel)
  }
}

/**
 * 日程主页编辑入口的展示门禁。
 *
 * local-first 模式允许离线编辑，read-only 模式关闭新增与修改；同步健康度不参与编辑能力判断。
 */
internal fun isScheduleMainEditorEnabled(
  mutationMode: ScheduleRepositoryMutationMode,
): Boolean = mutationMode.canSubmitScheduleMutation()
