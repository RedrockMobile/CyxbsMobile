package com.cyxbs.pages.todo.ui.activity

import android.app.Activity
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.TODO_ADD_TODO_BY_WIDGET
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
    
    // 返回按钮回调 - 根据 dialog 是否显示来控制是否启用
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // 当回调被触发时，关闭 dialog 并结束 Activity
            dialog.hide()
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.todo_activity_add_widget)
        
        // 注册返回按钮回调
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        
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
            // 监听 dialog 的显示/隐藏状态，动态控制回调的启用状态
            setOnShowListener {
                onBackPressedCallback.isEnabled = true
            }
            setOnDismissListener {
                onBackPressedCallback.isEnabled = false
            }
        }
        dialog.show()
    }



}