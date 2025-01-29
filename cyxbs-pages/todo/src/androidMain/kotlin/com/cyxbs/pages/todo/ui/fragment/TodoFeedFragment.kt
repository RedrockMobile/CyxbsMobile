package com.cyxbs.pages.todo.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.service.startActivity
import com.cyxbs.pages.todo.R
import com.cyxbs.pages.todo.adapter.TodoFeedAdapter
import com.cyxbs.pages.todo.model.bean.DelPushWrapper
import com.cyxbs.pages.todo.model.bean.RemindMode
import com.cyxbs.pages.todo.model.bean.Todo
import com.cyxbs.pages.todo.model.bean.TodoListPushWrapper
import com.cyxbs.pages.todo.ui.activity.TodoInnerMainActivity
import com.cyxbs.pages.todo.viewmodel.TodoViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * description: 首页的邮子清单
 * author: sanhuzhen
 * date: 2024/8/20 17:30
 */
class TodoFeedFragment : BaseFragment() {


    private val todoList = ArrayList<Todo>()
    private val mRv by R.id.todo_rv_feed_todo_list.view<RecyclerView>()
    private val mTv by R.id.todo_tv_feed_empty_notify.view<TextView>()
    private val mCl by R.id.todo_cl_todo_feed.view<ConstraintLayout>()

    private val mAdapter by lazy { TodoFeedAdapter() }
    private val mViewModel: TodoViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.todo_fragment_todo_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        mCl.setOnClickListener {
            startActivity(TodoInnerMainActivity::class)
        }
        mRv.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mAdapter.apply {
                onFinishCheck {
                    if (it in todoList.indices) {
                        val syncTime = com.cyxbs.components.init.appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)

                        if (todoList[it].remindMode.repeatMode != RemindMode.NONE) {
                            val notifyDateTime = getNextNoticeTime(todoList[it])
                            notifyItemChanged(it)
                            if (!notifyDateTime.isNullOrEmpty()) {
                                // 如果有下次提醒时间，推送更新的todo
                                todoList[it].remindMode.notifyDateTime = notifyDateTime
                                mViewModel.pushTodo(
                                    TodoListPushWrapper(
                                        listOf(todoList[it]),
                                        syncTime,
                                        TodoListPushWrapper.NONE_FORCE,
                                        0
                                    )
                                )
                            } else {
                                // 没有提醒时间，删除todo
                                mViewModel.delTodo(
                                    DelPushWrapper(
                                        listOf(todoList[it].todoId),
                                        syncTime
                                    )
                                )
                            }
                        } else {
                            // 没有提醒模式，直接标记为已完成并删除
                            todoList[it].isChecked = 1
                            mViewModel.delTodo(
                                DelPushWrapper(
                                    listOf(todoList[it].todoId),
                                    syncTime
                                )
                            )
                        }
                    }
                }
            }
        }
        mViewModel.allTodo.observe {
            it.todoArray?.let { todos ->
                val filteredList =
                    todos.filter { todo -> todo.isChecked == 0 && todo.todoId > 3 }
                        .take(3) // 只取未选中的前3个
                todoList.apply {
                    clear()
                    addAll(filteredList)
                }
                if (todoList.isEmpty()) {
                    mTv.visible()
                    mRv.gone()
                    mTv.text = getString(R.string.todo_string_empty_todo_notify)
                } else {
                    mRv.visible()
                    mTv.gone()
                }
                mAdapter.submitList(filteredList.toList())
            }

        }
    }

    override fun onResume() {
        super.onResume()
        mViewModel.getAllTodo()
    }

    private fun getNextNoticeTime(todo: Todo): String? {
        val dateFormat = SimpleDateFormat("yyyy年M月d日HH:mm", Locale.getDefault())
        val now = Calendar.getInstance()

        // 初始化当前时间，如果 notifyDateTime 为空则使用当前时间
        val startTime = if (todo.remindMode.notifyDateTime.isNullOrEmpty()) {
            now.clone() as Calendar
        } else {
            Calendar.getInstance().apply {
                time = dateFormat.parse(todo.remindMode.notifyDateTime.toString())
                    ?: return null  // 解析失败则返回 null
            }
        }

        // 处理截止时间，如果为空表示无穷，否则解析截止时间
        val endTime = if (!todo.endTime.isNullOrEmpty()) {
            Calendar.getInstance().apply {
                time = dateFormat.parse(todo.endTime.toString()) ?: return null
            }
        } else {
            null // 截止时间为空，表示无穷
        }

        // 根据重复模式计算下一个提醒时间
        when (todo.remindMode.repeatMode) {
            RemindMode.DAY -> {
                // 每天重复，直接加一天
                startTime.add(Calendar.DAY_OF_MONTH, 1)
            }

            RemindMode.WEEK -> {
                val today = (startTime.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
                val sortedWeekDays = todo.remindMode.week.sorted()

                // 找到下一个有效的提醒日
                val nextValidDay =
                    sortedWeekDays.firstOrNull { it > today } ?: sortedWeekDays.firstOrNull()

                if (nextValidDay != null) {
                    var daysToAdd = nextValidDay - today
                    if (daysToAdd <= 0) {
                        // 如果下一个有效提醒日已经过去，则推迟到下周
                        daysToAdd += 7
                    }
                    startTime.add(Calendar.DAY_OF_MONTH, daysToAdd)
                }
            }

            RemindMode.MONTH -> {
                val today = startTime.get(Calendar.DAY_OF_MONTH)
                val sortedDays = todo.remindMode.day.sorted()

                // 找到下一个有效的提醒日
                val nextValidDay = sortedDays.firstOrNull { it > today } ?: sortedDays.firstOrNull()

                if (nextValidDay != null) {
                    if (nextValidDay <= today) {
                        // 今天的提醒时间已过，推迟到下个月
                        startTime.add(Calendar.MONTH, 1)
                    }
                    // 设置为下一个有效提醒日
                    startTime.set(Calendar.DAY_OF_MONTH, nextValidDay)
                }
            }

            else -> return null // 不支持的重复模式，返回 null
        }

        // 如果有截止时间，检查是否超出
        endTime?.let {
            if (startTime.after(it)) {
                return null  // 如果提醒时间超过截止时间，返回 null
            }
        }

        return dateFormat.format(startTime.time)
    }
}