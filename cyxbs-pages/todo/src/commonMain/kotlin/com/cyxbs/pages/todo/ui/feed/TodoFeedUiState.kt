package com.cyxbs.pages.todo.ui.feed

/**
 * 邮子清单 feed 卡片的展示状态。
 *
 * 把旧版 [com.cyxbs.pages.todo.ui.fragment.TodoFeedFragment] 里散落的「加载中 / 空 / 有数据」
 * 三种 RecyclerView+空态切换收敛成单一状态，由 androidMain 的桥接层（TodoService）从
 * TodoViewModel 的数据映射而来，commonMain UI 仅按状态被动渲染。
 */
sealed interface TodoFeedUiState {

  /** 数据尚未返回，对应「查询中…」 */
  data object Loading : TodoFeedUiState

  /** 没有未完成待办，对应「还没有待做事项哦~快去添加吧！」 */
  data object Empty : TodoFeedUiState

  /** 有未完成待办（最多展示 3 条） */
  data class Data(val items: List<TodoFeedItemUi>) : TodoFeedUiState
}

/**
 * feed 列表项的轻量 UI 模型（不依赖 Room 的 `Todo` bean，便于放在 commonMain）。
 *
 * @param id 对应 `Todo.todoId`，回调时用于在 androidMain 定位原始 bean
 * @param title 待办标题
 * @param timeText 已格式化好的提醒/截止时间文案；为 null 时不展示铃铛与时间行
 * @param isOverTime 是否已超时（决定红色样式与超时铃铛）
 */
data class TodoFeedItemUi(
  val id: Long,
  val title: String,
  val timeText: String?,
  val isOverTime: Boolean,
)
