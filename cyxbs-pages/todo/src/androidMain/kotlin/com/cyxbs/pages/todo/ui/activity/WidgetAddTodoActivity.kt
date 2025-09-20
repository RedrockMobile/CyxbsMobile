package com.cyxbs.pages.todo.ui.activity

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.TODO_ADD_TODO_BY_WIDGET
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.pages.todo.R
import com.cyxbs.pages.todo.model.bean.TodoListPushWrapper
import com.cyxbs.pages.todo.ui.dialog.AddTodoDialog
import com.cyxbs.pages.todo.viewmodel.TodoViewModel
import com.g985892345.provider.api.annotation.KClassProvider

@KClassProvider(clazz = Activity::class, name = TODO_ADD_TODO_BY_WIDGET)
class WidgetAddTodoActivity : BaseActivity() {

    private val mViewModel by viewModels<TodoViewModel>()
    private lateinit var dialog: AddTodoDialog
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.todo_activity_add_widget)
        dialog = AddTodoDialog(this, R.style.BottomSheetDialogThemeNight) {
            val syncTime = com.cyxbs.components.init.appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)
            val firstPush = if (syncTime == 0L) 1 else 0
            mViewModel.apply {
                pushTodo(
                    TodoListPushWrapper(
                        listOf(it), syncTime, TodoListPushWrapper.NONE_FORCE, firstPush
                    )
                )
                isPushed.observe(this@WidgetAddTodoActivity) {
                    finish()
                }
            }

        }.apply {
            //点击外部不允许hide
            setCanceledOnTouchOutside(false)
            //禁止用户手动拖拽关闭
            setCancelable(false)
            setOnKeyListener { _, keyCode, event ->
                val needDone =
                    keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN
                if (needDone) {
                    hide()
                    finish()
                }
                needDone
            }
        }
        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dialog.hide()
        finish()
        super.onBackPressed()
    }

}