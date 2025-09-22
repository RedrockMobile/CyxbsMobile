package com.cyxbs.pages.todo.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DISCOVER_TODO_MAIN
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.utils.extensions.lazyUnlock
import com.cyxbs.pages.todo.R
import com.cyxbs.pages.todo.model.bean.TodoListPushWrapper
import com.cyxbs.pages.todo.ui.dialog.AddTodoDialog
import com.cyxbs.pages.todo.ui.fragment.TodoAllFragment
import com.cyxbs.pages.todo.ui.fragment.TodoLifeFragment
import com.cyxbs.pages.todo.ui.fragment.TodoOtherFragment
import com.cyxbs.pages.todo.ui.fragment.TodoStudyFragment
import com.cyxbs.pages.todo.viewmodel.TodoViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.properties.Delegates

/**
 * description:
 * author: sanhuzhen
 * date: 2024/8/20 17:31
 */
@KClassProvider(clazz = Activity::class, name = DISCOVER_TODO_MAIN)
class TodoInnerMainActivity : BaseActivity() {

    //在详情页面是否有做出修改的flag
    private var changedFlag = false
    private val todo_inner_home_bar_add by R.id.todo_inner_home_bar_add.view<ImageView>()
    private val todo_inner_home_bar_back by R.id.todo_inner_home_bar_back.view<ImageView>()
    private val mTabLayout: TabLayout by R.id.todo_tab_layout.view()
    private val manageButton by R.id.todo_custom_button.view<FrameLayout>()
    private val changeManageButton by R.id.todo_custom_button_change.view<FrameLayout>()
    private val mVp: ViewPager2 by R.id.view_pager.view()
    private val addButton by R.id.todo_inner_home_bar_add.view<FloatingActionButton>()
    private var tab1View by Delegates.notNull<View>()

    private var tab2View by Delegates.notNull<View>()

    private var tab3View by Delegates.notNull<View>()

    private var tab4View by Delegates.notNull<View>()

    private val todoViewModel by viewModels<TodoViewModel>()

    // 按返回键先退出管理
    private val mBackPressedCallback by lazyUnlock {
        onBackPressedDispatcher.addCallback(this, enabled = changeManageButton.visibility == View.VISIBLE) {
            quitMange()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.todo_activity_inner_main)
        initTab()
        initClick()
        changedFlag = false

    }


    private fun initClick() {
        todo_inner_home_bar_back.setOnClickListener {
            finish()
        }
        manageButton.setOnClickListener {
            manageButton.visibility = View.GONE
            changeManageButton.visibility = View.VISIBLE
            addButton.visibility = View.GONE
            todoViewModel.setEnabled(true)
            mBackPressedCallback.isEnabled = true
        }
        changeManageButton.setOnClickListener {
            quitMange()
        }
        todo_inner_home_bar_add.setOnClickListener {
            AddTodoDialog(this, R.style.BottomSheetDialogThemeNight) {
                val syncTime = com.cyxbs.components.init.appContext.getSp("todo").getLong("TODO_LAST_SYNC_TIME", 0L)
                val firstPush = if (syncTime == 0L) 1 else 0
                todoViewModel.apply {
                    pushTodo(
                        TodoListPushWrapper(
                            listOf(it), syncTime, TodoListPushWrapper.NONE_FORCE, firstPush
                        )
                    )
                }
            }.show()
        }
    }

    private fun quitMange() {
        changeManageButton.visibility = View.GONE
        manageButton.visibility = View.VISIBLE
        addButton.visibility = View.VISIBLE
        todoViewModel.setEnabled(false)
        mBackPressedCallback.isEnabled = false
    }


    @SuppressLint("MissingInflatedId")
    private fun initTab() {
        mVp.adapter = FragmentVpAdapter(this)
            .add { TodoAllFragment() }
            .add { TodoStudyFragment() }
            .add { TodoLifeFragment() }
            .add { TodoOtherFragment() }
        val tabs = arrayOf(
            getString(R.string.todo_string_tab1),
            getString(R.string.todo_string_tab2),
            getString(R.string.todo_string_tab3),
            getString(R.string.todo_string_tab4),
        )
        TabLayoutMediator(
            mTabLayout, mVp
        ) { tab,
            position ->
            tab.text = tabs[position]
        }.attach()
        //设置tabView
        val tab1 = mTabLayout.getTabAt(0)
        val tab2 = mTabLayout.getTabAt(1)
        val tab3 = mTabLayout.getTabAt(2)
        val tab4 = mTabLayout.getTabAt(3)
        tab1View = LayoutInflater.from(this).inflate(R.layout.todo_activity_tab1, null)
        tab1?.customView = tab1View
        tab2View = LayoutInflater.from(this).inflate(R.layout.todo_activity_tab2, null)
        tab2?.customView = tab2View
        tab3View = LayoutInflater.from(this).inflate(R.layout.todo_activity_tab3, null)
        tab3?.customView = tab3View
        tab4View = LayoutInflater.from(this).inflate(R.layout.todo_activity_tab4, null)
        tab4?.customView = tab4View
        val onTabSelectedListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.todo_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(com.cyxbs.components.config.R.color.config_level_one_font_color.color))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.todo_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(com.cyxbs.components.config.R.color.config_alpha_forty_level_two_font_color.color))

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        }
        mTabLayout.addOnTabSelectedListener(onTabSelectedListener)
    }

}