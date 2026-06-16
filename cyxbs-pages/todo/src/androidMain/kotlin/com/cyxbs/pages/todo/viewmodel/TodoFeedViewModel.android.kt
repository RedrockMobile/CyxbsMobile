package com.cyxbs.pages.todo.viewmodel

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.pages.todo.model.bean.DelPushWrapper
import com.cyxbs.pages.todo.model.bean.RemindMode
import com.cyxbs.pages.todo.model.bean.Todo
import com.cyxbs.pages.todo.model.bean.TodoListPushWrapper
import com.cyxbs.pages.todo.model.database.TodoDatabase
import com.cyxbs.pages.todo.repository.TodoRepository
import com.cyxbs.pages.todo.ui.activity.TodoDetailActivity
import com.cyxbs.pages.todo.ui.activity.TodoInnerMainActivity
import com.cyxbs.pages.todo.ui.feed.TodoFeedItemUi
import com.cyxbs.pages.todo.ui.feed.TodoFeedUiState
import com.cyxbs.pages.todo.ui.widget.TodoWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 邮子清单 feed ViewModel 的 Android 实现。
 *
 * 数据来自 [TodoRepository]（RxJava + 网络）+ [TodoDatabase]（Room 本地缓存），与落地页共享
 * 同一份后端/本地数据；勾选完成后会更新本地缓存、发桌面小组件广播并刷新。
 *
 * 逻辑搬自旧 TodoFeedFragment + TodoFeedAdapter + TodoViewModel 的 feed 相关部分。
 *
 * @author 985892345
 */
actual class TodoFeedViewModel actual constructor() : CommonTodoFeedViewModel() {

  // 当前完整待办列表，用于按 id 回查原始 bean（点击/勾选）
  private var rawTodos: List<Todo> = emptyList()

  init {
    _uiState.value = TodoFeedUiState.Loading
  }

  override fun refresh() {
    // 只读远端用于展示，不全量覆盖本地 Room（避免冲掉落地页尚未同步的本地修改，
    // 全量同步交给落地页 TodoViewModel）；网络失败时读本地缓存兜底。
    TodoRepository.queryAllTodo()
      .mapOrInterceptException {
        viewModelScope.launch(Dispatchers.IO) {
          updateList(TodoDatabase.instance.todoDao().queryAll().orEmpty())
        }
      }
      .safeSubscribeBy { wrapper ->
        updateList(wrapper.todoArray.orEmpty())
      }
  }

  override fun onCardClick() {
    startActivity(TodoInnerMainActivity::class)
  }

  override fun onItemClick(id: Long) {
    val todo = rawTodos.firstOrNull { it.todoId == id } ?: return
    val context = appTopActivity.get() ?: return
    TodoDetailActivity.startActivity(todo, context)
  }

  override fun onItemCheck(id: Long) {
    val todo = rawTodos.firstOrNull { it.todoId == id } ?: return
    val syncTime = appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)
    if (todo.remindMode.repeatMode != RemindMode.NONE) {
      val next = getNextNoticeTime(todo)
      if (!next.isNullOrEmpty()) {
        // 有下次提醒时间：更新这条 todo
        todo.remindMode.notifyDateTime = next
        pushTodo(todo, syncTime)
      } else {
        // 重复已结束：删除
        delTodo(todo.todoId, syncTime)
      }
    } else {
      // 无重复提醒：标记完成并删除
      todo.isChecked = 1
      delTodo(todo.todoId, syncTime)
    }
  }

  private fun pushTodo(todo: Todo, syncTime: Long) {
    TodoRepository.pushTodo(
      TodoListPushWrapper(listOf(todo), syncTime, TodoListPushWrapper.NONE_FORCE, 0),
    ).mapOrInterceptException {
      viewModelScope.launch(Dispatchers.IO) {
        TodoDatabase.instance.todoDao().insertAll(listOf(todo))
        TodoWidget.sendAddTodoBroadcast(appContext)
        refresh()
      }
    }.safeSubscribeBy {
      viewModelScope.launch(Dispatchers.IO) {
        TodoDatabase.instance.todoDao().insertAll(listOf(todo))
      }
      TodoWidget.sendAddTodoBroadcast(appContext)
      setLastSyncTime(it.syncTime)
      refresh()
    }
  }

  private fun delTodo(todoId: Long, syncTime: Long) {
    TodoRepository.delTodo(
      DelPushWrapper(listOf(todoId), syncTime),
    ).mapOrInterceptException {
      viewModelScope.launch(Dispatchers.IO) {
        TodoDatabase.instance.todoDao().deleteTodoById(todoId)
        TodoWidget.sendAddTodoBroadcast(appContext)
        refresh()
      }
    }.safeSubscribeBy {
      viewModelScope.launch(Dispatchers.IO) {
        TodoDatabase.instance.todoDao().deleteTodoById(todoId)
      }
      TodoWidget.sendAddTodoBroadcast(appContext)
      setLastSyncTime(it.syncTime)
      refresh()
    }
  }

  // 过滤未完成、非新手教程项（todoId > 3）的前 3 条，映射成 UI 状态
  private fun updateList(todos: List<Todo>) {
    rawTodos = todos
    val visible = todos.filter { it.isChecked == 0 && it.todoId > 3 }.take(3)
    _uiState.value = if (visible.isEmpty()) {
      TodoFeedUiState.Empty
    } else {
      TodoFeedUiState.Data(visible.map { it.toFeedItemUi() })
    }
  }

  private fun setLastSyncTime(syncTime: Long) {
    appContext.getSp("todo").edit().apply {
      putLong("TODO_LAST_SYNC_TIME", syncTime)
      commit()
    }
  }
}

/** feed 时间文案解析格式，对齐旧 TodoFeedAdapter 的 dateFormat */
private val feedDisplayDateFormat = SimpleDateFormat("yyyy年MM月dd日HH:mm", Locale.getDefault())

/**
 * 把 [Todo] 映射成 feed UI 模型，时间文案与超时判断逻辑搬自旧 TodoFeedAdapter.bind/updateUi。
 */
private fun Todo.toFeedItemUi(): TodoFeedItemUi {
  val now = System.currentTimeMillis()
  val notify = remindMode.notifyDateTime
  // endTime 与 notifyDateTime 同时为空串则不展示时间行
  val hideTime = endTime == "" && notify == ""
  val timeText = if (hideTime) {
    null
  } else {
    val raw = if (!notify.isNullOrEmpty()) notify else endTime
    raw?.replace("日", "日  ")?.takeIf { it.isNotEmpty() }
  }
  // 超时判断：endTime 优先，否则 notifyDateTime（解析失败按当前时间兜底，对齐旧逻辑）
  val itemTime = when {
    !endTime.isNullOrEmpty() -> parseFeedTimeOrNull(endTime) ?: now
    !notify.isNullOrEmpty() -> parseFeedTimeOrNull(notify) ?: now
    else -> 0L
  }
  val isOverTime = timeText != null && now > itemTime && itemTime != 0L
  return TodoFeedItemUi(id = todoId, title = title, timeText = timeText, isOverTime = isOverTime)
}

private fun parseFeedTimeOrNull(text: String?): Long? = try {
  text?.let { feedDisplayDateFormat.parse(it)?.time }
} catch (e: Exception) {
  null
}

/**
 * 根据重复模式计算下一次提醒时间，搬自旧 TodoFeedFragment.getNextNoticeTime。
 */
private fun getNextNoticeTime(todo: Todo): String? {
  val dateFormat = SimpleDateFormat("yyyy年M月d日HH:mm", Locale.getDefault())
  val now = Calendar.getInstance()

  val startTime = if (todo.remindMode.notifyDateTime.isNullOrEmpty()) {
    now.clone() as Calendar
  } else {
    Calendar.getInstance().apply {
      time = dateFormat.parse(todo.remindMode.notifyDateTime.toString()) ?: return null
    }
  }

  val endTime = if (!todo.endTime.isNullOrEmpty()) {
    Calendar.getInstance().apply {
      time = dateFormat.parse(todo.endTime.toString()) ?: return null
    }
  } else {
    null
  }

  when (todo.remindMode.repeatMode) {
    RemindMode.DAY -> {
      startTime.add(Calendar.DAY_OF_MONTH, 1)
    }

    RemindMode.WEEK -> {
      val today = (startTime.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
      val sortedWeekDays = todo.remindMode.week.sorted()
      val nextValidDay = sortedWeekDays.firstOrNull { it > today } ?: sortedWeekDays.firstOrNull()
      if (nextValidDay != null) {
        var daysToAdd = nextValidDay - today
        if (daysToAdd <= 0) {
          daysToAdd += 7
        }
        startTime.add(Calendar.DAY_OF_MONTH, daysToAdd)
      }
    }

    RemindMode.MONTH -> {
      val today = startTime.get(Calendar.DAY_OF_MONTH)
      val sortedDays = todo.remindMode.day.sorted()
      val nextValidDay = sortedDays.firstOrNull { it > today } ?: sortedDays.firstOrNull()
      if (nextValidDay != null) {
        if (nextValidDay <= today) {
          startTime.add(Calendar.MONTH, 1)
        }
        startTime.set(Calendar.DAY_OF_MONTH, nextValidDay)
      }
    }

    else -> return null
  }

  endTime?.let {
    if (startTime.after(it)) {
      return null
    }
  }

  return dateFormat.format(startTime.time)
}
