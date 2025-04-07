package com.cyxbs.pages.notification.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.compose.ui.util.fastSumBy
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.utils.impl.defaultImpl
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.api.ILaunchNotificationService.NotificationPage
import com.cyxbs.pages.notification.model.ActivityMessageRepository
import com.cyxbs.pages.notification.model.ItineraryRepository
import com.cyxbs.pages.notification.model.SystemMessageRepository
import com.cyxbs.pages.notification.ui.fragment.ItineraryNotificationFragment
import com.cyxbs.pages.notification.ui.fragment.SysNotificationFragment
import com.cyxbs.pages.notification.ui.fragment.UfieldNotificationFragment
import com.cyxbs.pages.notification.viewmodel.NotificationViewModel
import com.cyxbs.pages.notification.widget.VerticalSwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.properties.Delegates

class NotificationActivity : BaseActivity() {

    companion object {
        fun start(page: NotificationPage) {
            val context = appTopActivity.get() ?: return
            context.startActivity(
                Intent(
                    context,
                    NotificationActivity::class.java
                ).apply {
                    putExtra(NotificationActivity::initPage.name, page)
                },
            )
        }
    }

    private val viewModel by viewModels<NotificationViewModel>()

    private val initPage by intent<NotificationPage>()

    private var tab3View by Delegates.notNull<View>()  // 行程通知
    private var tab2View by Delegates.notNull<View>()  // 系统通知
    private var tab1View by Delegates.notNull<View>()  // 活动通知

    private val notification_main_container_bg by R.id.notification_main_column_container_background.view<View>()

    private val notification_rl_home_back by R.id.notification_rl_home_back.view<RelativeLayout>()
    private val notification_home_vp2 by R.id.notification_home_vp2.view<ViewPager2>()
    private val notification_home_tl by R.id.notification_home_tl.view<TabLayout>()
    private val notification_refresh by R.id.notification_refresh.view<VerticalSwipeRefreshLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notification_activity_main)
        initViewClickListener()
        initVp2()
        initTabLayout()
        initObserver()
        initRefreshLayout()
    }

    private fun initViewClickListener() {
        notification_rl_home_back.setOnSingleClickListener { finish() }
    }

    private fun initVp2() {
        notification_home_vp2.adapter = FragmentVpAdapter(this)
            .add { UfieldNotificationFragment() }       // whichPageIsIn = 0
            .add { SysNotificationFragment() }          // whichPageIsIn = 1
            .add { ItineraryNotificationFragment() }    // whichPageIsIn = 2
        notification_home_vp2.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 2) {
                    // 行程通知页有个子 tabLayout，所以需要遮挡背景圆角
                    notification_main_container_bg.visible()
                } else {
                    notification_main_container_bg.gone()
                }
            }
        })

    }

    @SuppressLint("InflateParams")
    private fun initTabLayout() {
        val tabs = arrayOf(
            "活动通知",
            "系统通知",
            "行程通知"
        )
        TabLayoutMediator(
            notification_home_tl,
            notification_home_vp2
        ) { tab, position -> tab.text = tabs[position] }.attach()

        val tab1 = notification_home_tl.getTabAt(0)
        val tab2 = notification_home_tl.getTabAt(1)
        val tab3 = notification_home_tl.getTabAt(2)

        //设置三个tab的自定义View
        tab1View = LayoutInflater.from(this).inflate(R.layout.notification_item_tab1, null)
        tab1?.customView = tab1View
        tab2View = LayoutInflater.from(this).inflate(R.layout.notification_item_tab2, null)
        tab2?.customView = tab2View
        tab3View = LayoutInflater.from(this).inflate(R.layout.notification_item_tab3, null)
        tab3?.customView = tab3View

        //改变文字颜色
        val onTabSelectedListener = object : TabLayout.OnTabSelectedListener by defaultImpl() {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.notification_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(R.color.notification_home_tabLayout_text_selected.color))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.notification_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(R.color.notification_home_tabLayout_text_unselect.color))
            }
        }
        notification_home_tl.addOnTabSelectedListener(onTabSelectedListener)
        if (!mIsActivityRebuilt) {
            when (initPage) {
                NotificationPage.ACTIVITY -> tab1?.select()
                NotificationPage.SYSTEM -> tab2?.select()
                NotificationPage.ITINERARY -> tab3?.select()
            }
        }
    }

    private fun initObserver() {
        // 活动通知红点
        ActivityMessageRepository.activityMessageFlow.map { list ->
            list.fastSumBy { if (it.clicked) 0 else 1 }
        }.onEach {
            changeTabRedDotsVisibility(0, it != 0)
        }.launchIn(lifecycleScope)

        // 系统通知红点
        SystemMessageRepository.systemMessageFlow.map { list ->
            list.fastSumBy { if (it.has_read) 0 else 1 }
        }.onEach {
            changeTabRedDotsVisibility(1, it != 0)
        }.launchIn(lifecycleScope)

        // 行程通知红点
        ItineraryRepository.sentItineraryFlow.map { list ->
            list.fastSumBy { if (it.hasRead) 0 else 1 }
        }.combine(ItineraryRepository.receivedItineraryFlow) { a, b ->
            a + b.fastSumBy { if (it.hasRead) 0 else 1 }
        }.onEach {
            changeTabRedDotsVisibility(2, it != 0)
        }.launchIn(lifecycleScope)
    }

    private fun initRefreshLayout() {
        notification_refresh.setOnRefreshListener {
            viewModel.refreshAllNotification()
        }
        viewModel.refreshState.observe {
            notification_refresh.isRefreshing = it
        }
    }

    //改变TabLayout小红点的显示状态
    private fun changeTabRedDotsVisibility(position: Int, visibility: Boolean) {
        val vis = if (visibility) View.VISIBLE else View.INVISIBLE
        when (position) {
            0 -> tab1View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
            1 -> tab2View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
            2 -> tab3View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
        }
    }
}