package com.cyxbs.pages.todo.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.todo.api.ITodoService
import com.cyxbs.pages.todo.ui.feed.TodoFeed
import com.cyxbs.pages.todo.viewmodel.TodoFeedViewModel
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 邮子清单 feed 的供给方（commonMain）。
 *
 * feed UI（[TodoFeed]）与装配都在 commonMain，平台差异（数据层、跳转）收口在
 * [TodoFeedViewModel] 的 expect/actual 里，故本类无需 expect/actual。
 *
 * Author: RayleighZ / 迁移 985892345
 */
@ImplProvider
object TodoService : ITodoService {

  @Composable
  override fun TodoFeed(modifier: Modifier) {
    val viewModel = viewModel { TodoFeedViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 对齐旧 TodoFeedFragment.onResume：每次回到前台刷新
    LifecycleResumeEffect(viewModel) {
      viewModel.refresh()
      onPauseOrDispose { }
    }
    TodoFeed(
      state = state,
      onCardClick = viewModel::onCardClick,
      onItemClick = viewModel::onItemClick,
      onItemCheck = viewModel::onItemCheck,
      modifier = modifier,
    )
  }
}
