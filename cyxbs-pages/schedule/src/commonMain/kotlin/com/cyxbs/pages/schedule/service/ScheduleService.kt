package com.cyxbs.pages.schedule.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.schedule.api.IScheduleService
import com.cyxbs.pages.schedule.ui.feed.ScheduleFeed
import com.cyxbs.pages.schedule.ui.feed.ScheduleFeedUiState
import com.cyxbs.pages.schedule.viewmodel.ScheduleFeedViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import com.cyxbs.pages.schedule.ui.feed.ScheduleUrgentBanner as ScheduleUrgentBannerContent

/**
 * 邮子清单 feed 的供给方（commonMain）。
 *
 * feed UI 与装配都在 commonMain，平台差异（数据层、跳转）收口在
 * [ScheduleFeedViewModel] 的 expect/actual 里，故本类无需 expect/actual。
 *
 * Author: RayleighZ / 迁移 985892345
 */
@ImplProvider
object ScheduleService : IScheduleService {

  /** 在整个发现页 Feed 容器之前绘制提醒；无临期或超期事项时不产生任何布局。 */
  @Composable
  override fun ScheduleUrgentBanner(modifier: Modifier) {
    val viewModel = viewModel { ScheduleFeedViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val count = (state as? ScheduleFeedUiState.Data)?.urgentCount ?: return
    if (count <= 0) return
    ScheduleUrgentBannerContent(
      count = count,
      onClick = viewModel::onCardClick,
      modifier = modifier,
    )
  }

  @Composable
  override fun ScheduleFeed(modifier: Modifier) {
    val viewModel = viewModel { ScheduleFeedViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 对齐旧 ScheduleFeedFragment.onResume：每次回到前台刷新
    LifecycleResumeEffect(viewModel) {
      viewModel.refresh()
      onPauseOrDispose { }
    }
    ScheduleFeed(
      state = state,
      onCardClick = viewModel::onCardClick,
      onItemClick = viewModel::onItemClick,
      onItemCheck = viewModel::onItemCheck,
      onTogglePin = viewModel::onTogglePin,
      onDelete = viewModel::onDelete,
      onToggleCourseProjection = viewModel::onToggleCourseProjection,
      modifier = modifier,
    )
  }
}
