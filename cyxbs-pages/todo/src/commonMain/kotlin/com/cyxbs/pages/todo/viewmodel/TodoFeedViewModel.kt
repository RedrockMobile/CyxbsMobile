package com.cyxbs.pages.todo.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.todo.ui.feed.TodoFeedUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 邮子清单 feed 专用 ViewModel。
 *
 * 与落地页（TodoInnerMainActivity / TodoDetailActivity 各自独立 Activity）的 [TodoViewModel]
 * **不共用**，仅承载 feed 自身的展示与交互。
 *
 * feed 的数据层（Room / RxJava / Widget）与跳转能力都是平台相关的，故设计成 expect/actual：
 * - 公共的状态承载（[uiState]）与动作签名放在 [CommonTodoFeedViewModel]；
 * - androidMain 的 actual 提供真实现，其余平台给空实现占位（todo 暂未接入非 Android 平台）。
 *
 * @author 985892345
 */
expect class TodoFeedViewModel() : CommonTodoFeedViewModel

abstract class CommonTodoFeedViewModel : BaseViewModel() {

  protected val _uiState = MutableStateFlow<TodoFeedUiState>(TodoFeedUiState.Empty)
  val uiState: StateFlow<TodoFeedUiState> = _uiState.asStateFlow()

  /** 拉取/刷新待办列表（对齐旧 TodoFeedFragment.onResume 的刷新时机） */
  open fun refresh() {}

  /** 点击整张卡片：跳转邮子清单主页 */
  open fun onCardClick() {}

  /** 点击某条待办标题：跳转详情页 */
  open fun onItemClick(id: Long) {}

  /** 勾选某条待办完成：按重复提醒规则更新下次提醒或删除 */
  open fun onItemCheck(id: Long) {}
}
