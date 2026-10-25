package com.cyxbs.pages.schedule.service

import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppSnackbarDefaults
import com.cyxbs.components.navigation.showAppSnackbar
import com.cyxbs.pages.schedule.api.IScheduleService
import com.cyxbs.pages.schedule.ui.feed.ScheduleFeed
import com.cyxbs.pages.schedule.ui.feed.ScheduleFeedUiState
import com.cyxbs.pages.schedule.ui.feed.ScheduleUndoCountdownIcon
import com.cyxbs.pages.schedule.viewmodel.ScheduleCompletionUndoDurationMillis
import com.cyxbs.pages.schedule.viewmodel.ScheduleFeedViewModel
import com.cyxbs.pages.schedule.viewmodel.SchedulePendingCompletion
import com.g985892345.provider.api.annotation.ImplProvider
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_feed_completed
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_feed_undo
import org.jetbrains.compose.resources.stringResource
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
    val pendingCompletion by viewModel.pendingCompletion.collectAsStateWithLifecycle()
    ScheduleFeedCompletionSnackbarEffect(
      pendingCompletion = pendingCompletion,
      onUndo = viewModel::undoPendingCompletion,
      onTimeout = viewModel::commitPendingCompletion,
    )
    // 对齐旧 ScheduleFeedFragment.onResume：每次回到前台刷新
    LifecycleResumeEffect(viewModel) {
      viewModel.refresh()
      onPauseOrDispose { }
    }
    ScheduleFeed(
      state = state,
      pendingCompletion = pendingCompletion?.identity,
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

/**
 * 监听 Feed 的待完成请求并展示应用级撤销 Snackbar。
 *
 * Snackbar 实际由应用根组合绘制；本函数只发送展示事件，并把匹配的 [SchedulePendingCompletion.requestId]
 * 回传给 [onTimeout]，避免旧 Snackbar 超时后误提交新的完成请求。
 */
@Composable
private fun ScheduleFeedCompletionSnackbarEffect(
  pendingCompletion: SchedulePendingCompletion?,
  onUndo: () -> Unit,
  onTimeout: (Long) -> Unit,
) {
  val completedText = stringResource(Res.string.schedule_feed_completed)
  val undoText = stringResource(Res.string.schedule_feed_undo)
  LaunchedEffect(pendingCompletion?.requestId, completedText, undoText) {
    val pending = pendingCompletion ?: return@LaunchedEffect
    showAppSnackbar(
      durationMillis = ScheduleCompletionUndoDurationMillis,
      bottomPadding = 72.dp,
      messageContent = {
        Text(text = completedText, fontSize = 14.sp)
      },
      actionTextContent = {
        Text(
          text = undoText,
          color = AppSnackbarDefaults.actionColor,
          fontSize = 14.sp,
        )
      },
      actionIconContent = {
        ScheduleUndoCountdownIcon(
          durationMillis = ScheduleCompletionUndoDurationMillis.toInt(),
          color = AppSnackbarDefaults.actionColor,
          modifier = Modifier.size(15.dp),
        )
      },
      onClick = onUndo,
      onTimeout = { onTimeout(pending.requestId) },
    )
  }
}
