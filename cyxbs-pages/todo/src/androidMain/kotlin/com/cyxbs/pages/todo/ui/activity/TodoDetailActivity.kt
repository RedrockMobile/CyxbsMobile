package com.cyxbs.pages.todo.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.todo.R
import com.cyxbs.pages.todo.adapter.RepeatTimeRvAdapter
import com.cyxbs.pages.todo.model.bean.RemindMode
import com.cyxbs.pages.todo.model.bean.Todo
import com.cyxbs.pages.todo.model.database.TodoDatabase
import com.cyxbs.pages.todo.ui.dialog.CalendarDialog
import com.cyxbs.pages.todo.ui.dialog.DetailAlarmDialog
import com.cyxbs.pages.todo.ui.dialog.SelectCategoryDialog
import com.cyxbs.pages.todo.ui.dialog.SelectRepeatDialog
import com.cyxbs.pages.todo.util.transformRepeat
import com.cyxbs.pages.todo.viewmodel.TodoViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch

/**
 * description:
 * author: sanhuzhen
 * date: 2024/8/20 17:31
 */
class TodoDetailActivity : BaseActivity() {
    lateinit var todo: Todo
    private val repeatTimeAdapter by lazy { RepeatTimeRvAdapter(1) }
    private var SelectRepeatTimeList = ArrayList<String>()
    private val viewModel by viewModels<TodoViewModel>()

    private val edRemark by R.id.todo_inner_detail_remark_ed.view<AppCompatEditText>()
    private val etTitle by R.id.todo_detail_et_todo_title.view<AppCompatEditText>()
    private val tvDeadline by R.id.todo_detail_tv_deadline.view<AppCompatTextView>()
    private val ivDeadlineDel by R.id.todo_detail_iv_deadline_del.view<ImageView>()
    private val tvRepeatTime by R.id.todo_tv_inner_detail_no_repeat_time.view<AppCompatTextView>()
    private val back by R.id.todo_inner_detail_back.view<TextView>()
    private val line by R.id.todo_detail_line.view<View>()
    private val tvClassify by R.id.todo_detail_tv_classify.view<TextView>()
    private val rvRepeatTime by R.id.todo_rv_inner_detail_repeat_time.view<RecyclerView>()
    private val tvSaveGrey by R.id.todo_thing_detail_no_save.view<TextView>()
    private val tvSave by R.id.todo_thing_detail_save.view<TextView>()

    companion object {
        fun startActivity(todo: Todo, context: Context) {
            context.startActivity(
                Intent(context, TodoDetailActivity::class.java).apply {
                    putExtra("todo", defaultGson.toJson(todo))
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //配置Window的背景颜色，使得共享动画时颜色正常
        //一定要放在onCreate之前，不然在共享动画时没有效果
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.todo_activity_detail)

        //下面的逻辑是为了处理端内跳转
        fun initTodo() {
            //这里反序列化两次是为了防止内外拿到同一个引用
            viewModel.rawTodo = defaultGson.fromJson(intent.getStringExtra("todo"), Todo::class.java)

            initView()

            initClick()
        }

        if (intent.getBooleanExtra("is_from_receive", false)) {
            //如果来自端内跳转, 则重新加载todo
            val todoId = intent.getStringExtra("todo_id").toString().toInt()
            if (todoId <= 0) {
                toast("没有这条代办的信息哦")
                finish()
            }

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    TodoDatabase.instance.todoDao().queryById(todoId)?.let {
                        todo = it
                        initTodo()
                    } ?: run {
                        "没有这条代办的信息哦".toast()
                    }
                }
            }

        } else {
            todo = defaultGson.fromJson(intent.getStringExtra("todo"), Todo::class.java)

            initTodo()
        }
    }

    private fun initView() {
        viewModel.isChanged.observe(this) {
            if (it) {
                tvSaveGrey.gone()
                tvSave.visible()
            } else {
                tvSaveGrey.visible()
                tvSave.gone()
            }
        }

        etTitle.setText(todo.title)
        edRemark.setText(todo.detail)
        tvClassify.text = when (todo.type) {
            "study" -> "学习"
            "life" -> "生活"
            else -> "其他"
        }
        tvDeadline.text = todo.endTime
        if (!TextUtils.isEmpty(tvDeadline.text))
            ivDeadlineDel.visible()
        else
            ivDeadlineDel.gone()

        val repeatMode: Int = todo.remindMode.repeatMode
        if (todo.remindMode.repeatMode == RemindMode.NONE) {
            tvRepeatTime.visible()
        } else {
            tvRepeatTime.gone()
            val selectRepeatTimeList: List<String> = if (repeatMode == RemindMode.WEEK) {
                todo.remindMode.week
            } else {
                todo.remindMode.day
            }.map {
                it.toString()
            }
            SelectRepeatTimeList = transformRepeat(selectRepeatTimeList, repeatMode)

            repeatTimeAdapter.submitList(SelectRepeatTimeList)
        }

        rvRepeatTime.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = repeatTimeAdapter.apply {
                setOnItemClick { position ->
                    if (position in SelectRepeatTimeList.indices) {
                        val updatedList = SelectRepeatTimeList.toMutableList()
                        updatedList.removeAt(position)
                        repeatTimeAdapter.submitList(updatedList)
                        SelectRepeatTimeList = updatedList as ArrayList<String> // 更新数据源

                        if (todo.remindMode.repeatMode == RemindMode.WEEK) {
                            val selectRepeatTimeList = viewModel.rawTodo?.remindMode?.week
                                ?.map { it.toString() }
                                ?.toList() ?: listOf()
                            viewModel.setChangeState(
                                judge() ||
                                        transformRepeat(
                                            selectRepeatTimeList,
                                            RemindMode.WEEK
                                        ) != SelectRepeatTimeList
                            )
                            todo.remindMode.week.removeAt(position)
                        } else if (todo.remindMode.repeatMode == RemindMode.MONTH) {
                            val selectRepeatTimeList = viewModel.rawTodo?.remindMode?.day
                                ?.map { it.toString() }
                                ?.toList() ?: listOf()
                            viewModel.setChangeState(
                                judge() ||
                                        transformRepeat(
                                            selectRepeatTimeList,
                                            RemindMode.MONTH
                                        ) != SelectRepeatTimeList
                            )
                            todo.remindMode.day.removeAt(position)
                        }
                    }
                    if (SelectRepeatTimeList.isEmpty()) {
                        rvRepeatTime.gone()
                        tvRepeatTime.visible()
                        viewModel.setChangeState(true)
                        todo.remindMode.repeatMode = RemindMode.NONE
                    }
                }
                setOnChangeRepeatTime {
                    selectRepeatTime()
                }
            }
        }
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    private fun initClick() {
        if (todo.isChecked == 1) {
            //已经check，不允许修改
            getString(R.string.todo_string_cant_modify).toast()
            etTitle.apply {
                isFocusable = false
                isFocusableInTouchMode = false
            }
            edRemark.apply {
                isFocusable = false
                isFocusableInTouchMode = false
            }
        } else {
            etTitle.addTextChangedListener {
                viewModel.setChangeState(judge())
            }
            edRemark.addTextChangedListener {
                viewModel.setChangeState(judge())
                if (it != null) {
                    if (it.length == 100) {
                        "已超100字，无法再输入".toast()
                    }
                }
            }
        }


        back.setOnClickListener {
            if (viewModel.isChanged.value == true) {
                DetailAlarmDialog.Builder(this)
                    .setPositiveClick {
                        finish()
                    }.setNegativeClick {
                        dismiss()
                    }.show()
            } else {
                finish()
            }
        }

        ivDeadlineDel.setOnClickListener {
            tvDeadline.text = ""
            it.gone()
            todo.endTime = ""
            viewModel.setChangeState(judge())
        }

        tvDeadline.setOnClickListener {
            onClickProxy {
                CalendarDialog(
                    this,
                    R.style.BottomSheetDialogThemeNight,
                    0
                ) { year, month, day, hour, minute ->
                    tvDeadline.apply {
                        text = when {
                            hour < 24 -> {
                                val time = "${year}年${month}月${day}日 ${
                                    String.format(
                                        "%02d",
                                        hour
                                    )
                                }:${String.format("%02d", minute)}"
                                todo.endTime = time.replace(" ", "")
                                time
                            }

                            else -> {
                                todo.endTime = "${year}年${month}月${day}日00:00"
                                "${year}年${month}月${day}日"
                            }
                        }
                        ivDeadlineDel.visible()
                        setTextColor(getColor(com.cyxbs.components.config.R.color.config_level_two_font_color))
                        viewModel.setChangeState(judge())
                    }
                }.show()
            }
        }

        tvRepeatTime.setOnClickListener {
            selectRepeatTime()
        }

        tvSave.setOnClickListener {
            //如果没输入标题，就ban掉
            if (etTitle.text.toString().isEmpty()) {
                "掌友，标题不能为空哦".toast()
                return@setOnClickListener
            }
            todo.title = etTitle.text.toString()
            todo.detail = edRemark.text.toString()
            todo.lastModifyTime = System.currentTimeMillis()
            viewModel.updateTodo(todo)
            finish()
        }

        tvClassify.setOnClickListener {
            onClickProxy {
                SelectCategoryDialog(
                    this,
                    R.style.BottomSheetDialogThemeNight
                ) {
                    tvClassify.text = it
                    todo.type = when (it) {
                        "学习" -> "study"
                        "生活" -> "life"
                        else -> "other"
                    }
                    viewModel.setChangeState(judge())
                }.show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.isChanged.value == true) {
            DetailAlarmDialog.Builder(this)
                .setPositiveClick {
                    finish()
                }.setNegativeClick {
                    dismiss()
                }.show()
        } else {
            super.onBackPressed()
        }
    }

    private fun selectRepeatTime() {
        onClickProxy {
            SelectRepeatDialog(
                this,
                R.style.BottomSheetDialogThemeNight,
                0,
                todo
            ) { selectRepeatTimeListIndex, selectRepeatTimeList, repeatMode ->
                todo.remindMode.repeatMode = repeatMode

                if (repeatMode == RemindMode.WEEK) {
                    todo.remindMode.week = selectRepeatTimeListIndex as ArrayList<Int>
                    todo.remindMode.day = ArrayList()
                } else {
                    todo.remindMode.day = selectRepeatTimeListIndex as ArrayList<Int>
                    todo.remindMode.week = ArrayList()
                }
                viewModel.setChangeState(SelectRepeatTimeList != selectRepeatTimeList as ArrayList<String>)
                SelectRepeatTimeList = selectRepeatTimeList

                repeatTimeAdapter.submitList(SelectRepeatTimeList) {
                    rvRepeatTime.scrollToPosition(0)
                }
                if (SelectRepeatTimeList.isNotEmpty()) {
                    rvRepeatTime.visible()
                    tvRepeatTime.gone()
                } else {
                    rvRepeatTime.gone()
                    tvRepeatTime.visible()
                }
            }.show()
        }
    }

    //统一处理此条todo的点击事件（试图修改）
    //如果已经完成，则不handle这次点击事件
    private fun onClickProxy(onClick: () -> Unit) {
        if (todo.isChecked == 1) {
            //已经check，不允许修改
            getString(R.string.todo_string_cant_modify).toast()
        } else {
            onClick.invoke()
        }
    }

    private fun judge() =
        tvClassify.text != viewModel.rawTodo?.type ||
                tvDeadline.text != viewModel.rawTodo?.endTime ||
                etTitle.toString() != viewModel.rawTodo?.title ||
                edRemark.toString() != viewModel.rawTodo?.detail ||
                todo.remindMode.repeatMode != viewModel.rawTodo?.remindMode?.repeatMode

}