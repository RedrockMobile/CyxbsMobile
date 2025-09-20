package com.cyxbs.pages.todo.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.pages.todo.adapter.SwipeDeleteRecyclerView
import com.cyxbs.pages.todo.adapter.TodoAllAdapter
import com.cyxbs.pages.todo.model.bean.DelPushWrapper
import com.cyxbs.pages.todo.model.bean.Todo
import com.cyxbs.pages.todo.model.bean.TodoListPushWrapper
import com.cyxbs.pages.todo.viewmodel.TodoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * @Project: CyxbsMobile_Android
 * @File: UpdateTodo
 * @Author: 86199
 * @Date: 2024/9/15
 * @Description: 用于计算提醒时间的帮助类
 */
object TodoHelper {

    private val dateTimeFormatter = SimpleDateFormat("yyyy年M月d日HH:mm", Locale.getDefault())

    fun updateTodoItem(
        todoItem: Todo,
        appContext: Context,
        viewModel: TodoViewModel,
        todoAllAdapter: TodoAllAdapter,
        recyclerView: SwipeDeleteRecyclerView,
        lifecycleOwner: LifecycleOwner // 添加 LifecycleOwner 参数
    ) {
        if (todoItem.endTime == todoItem.remindMode.notifyDateTime && todoItem.endTime != "") {
            val syncTime = appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)
            viewModel.delTodo(DelPushWrapper(listOf(todoItem.todoId), syncTime, 1))
            val currentList = todoAllAdapter.currentList.toMutableList()
            currentList.remove(todoItem)
            todoAllAdapter.submitList(currentList)
        } else if (todoItem.endTime == "" && todoItem.remindMode.repeatMode != 0) {
            var currentSystemTime = Calendar.getInstance()
            if (todoItem.remindMode.notifyDateTime != "") {
                currentSystemTime =
                    todoItem.remindMode.notifyDateTime?.let { parseDateTime(it) }
                        ?: Calendar.getInstance()
            }

            lifecycleOwner.lifecycleScope.launch {
                val nextRemindTime = withContext(Dispatchers.Default) {
                    when (todoItem.remindMode.repeatMode) {
                        1 -> calculateNextDailyRemindTime(currentSystemTime)
                        2 -> calculateNextWeeklyRemindTime(
                            currentSystemTime,
                            todoItem.remindMode.week
                        )

                        3 -> calculateNextMonthlyRemindTime(
                            currentSystemTime,
                            todoItem.remindMode.day
                        )

                        else -> currentSystemTime
                    }
                }
                handleNextRemindTime(
                    nextRemindTime,
                    todoItem,
                    viewModel,
                    todoAllAdapter,
                    recyclerView
                )
            }
        } else {
            var currentSystemTime = Calendar.getInstance()
            if (todoItem.remindMode.notifyDateTime != "") {
                currentSystemTime =
                    todoItem.remindMode.notifyDateTime?.let { parseDateTime(it) }
                        ?: Calendar.getInstance()
            }
            val endTime = todoItem.endTime?.let { parseDateTime(it) }
            lifecycleOwner.lifecycleScope.launch {
                val nextRemindTime = withContext(Dispatchers.Default) {
                    when (todoItem.remindMode.repeatMode) {
                        0 -> endTime
                        1 -> endTime?.let { calculateNextDailyRemindTime(currentSystemTime, it) }
                        2 -> endTime?.let {
                            calculateNextWeeklyRemindTime(
                                currentSystemTime,
                                todoItem.remindMode.week,
                                it
                            )
                        }

                        3 -> endTime?.let {
                            calculateNextMonthlyRemindTime(
                                currentSystemTime,
                                todoItem.remindMode.day,
                                it
                            )
                        }

                        else -> currentSystemTime
                    }
                }
                handleNextRemindTime(
                    nextRemindTime,
                    todoItem,
                    viewModel,
                    todoAllAdapter,
                    recyclerView
                )
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun handleNextRemindTime(
        nextRemindTime: Calendar?,
        todoItem: Todo,
        viewModel: TodoViewModel,
        todoAllAdapter: TodoAllAdapter,
        recyclerView: SwipeDeleteRecyclerView
    ) {
        nextRemindTime?.let {
            if (todoItem.remindMode.notifyDateTime == "") {
                todoItem.remindMode.notifyDateTime = formatDateTime(it)
                val syncTime = com.cyxbs.components.init.appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)
                viewModel.pushTodo(TodoListPushWrapper(listOf(todoItem), syncTime, 1, 0))
                todoAllAdapter.notifyDataSetChanged()
            } else {
                todoItem.remindMode.notifyDateTime = formatDateTime(it)
                val syncTime = com.cyxbs.components.init.appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)
                viewModel.pushTodo(TodoListPushWrapper(listOf(todoItem), syncTime, 1, 0))
                val currentList = todoAllAdapter.currentList.toMutableList()
                currentList.remove(todoItem)
                todoAllAdapter.submitList(currentList) {
                    recyclerView.scrollToPosition(getTopItems(todoAllAdapter))
                }
            }
        }
    }

    private fun calculateNextDailyRemindTime(
        currentRemindTime: Calendar,
        endTime: Calendar? = null
    ): Calendar? {
        val nextRemindTime = (currentRemindTime.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (endTime != null) {
            nextRemindTime.set(Calendar.HOUR_OF_DAY, endTime.get(Calendar.HOUR_OF_DAY))
            nextRemindTime.set(Calendar.MINUTE, endTime.get(Calendar.MINUTE))
            if (nextRemindTime.after(endTime)) return endTime
        }
        return nextRemindTime
    }

    private fun calculateNextWeeklyRemindTime(
        currentRemindTime: Calendar,
        weekDays: List<Int>,
        endTime: Calendar? = null
    ): Calendar? {
        val nextRemindTime = (currentRemindTime.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (endTime != null) {
            nextRemindTime.set(Calendar.HOUR_OF_DAY, endTime.get(Calendar.HOUR_OF_DAY))
            nextRemindTime.set(Calendar.MINUTE, endTime.get(Calendar.MINUTE))
        }

        val validWeekDays = weekDays.filter { it in 1..7 }
        if (validWeekDays.isEmpty()) return null

        nextRemindTime.add(Calendar.DAY_OF_MONTH, 1)
        while (nextRemindTime.get(Calendar.DAY_OF_WEEK) !in validWeekDays) {
            nextRemindTime.add(Calendar.DAY_OF_MONTH, 1)
            if (endTime != null && nextRemindTime.after(endTime)) return endTime
        }
        return nextRemindTime
    }

    private fun calculateNextMonthlyRemindTime(
        currentRemindTime: Calendar,
        days: List<Int>,
        endTime: Calendar? = null
    ): Calendar? {
        val nextRemindTime = currentRemindTime.clone() as Calendar
        val sortedDays = days.sorted()
        while (true) {
            for (day in sortedDays) {
                val possibleRemindTime = nextRemindTime.apply {
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (possibleRemindTime.after(currentRemindTime)) {
                    if (endTime != null) {
                        possibleRemindTime.set(
                            Calendar.HOUR_OF_DAY,
                            endTime.get(Calendar.HOUR_OF_DAY)
                        )
                        possibleRemindTime.set(Calendar.MINUTE, endTime.get(Calendar.MINUTE))
                    }
                    if (endTime == null || possibleRemindTime.before(endTime)) return possibleRemindTime
                }
            }
            nextRemindTime.add(Calendar.MONTH, 1)
            nextRemindTime.set(Calendar.DAY_OF_MONTH, sortedDays[0])
            if (endTime != null && nextRemindTime.after(endTime)) return endTime
        }
    }

    private fun parseDateTime(dateTimeStr: String?): Calendar? {
        return dateTimeStr?.let {
            runCatching {
                Calendar.getInstance().apply {
                    time = dateTimeFormatter.parse(it)
                }
            }.getOrNull()
        }
    }

    private fun formatDateTime(dateTime: Calendar): String {
        return dateTimeFormatter.format(dateTime.time)
    }

    private fun getTopItems(todoAllAdapter: TodoAllAdapter): Int {
        return todoAllAdapter.currentList.count { it.isPinned == 1 }
    }
}
