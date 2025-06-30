package com.cyxbs.pages.ufield.ui.activity

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.UFIELD_CENTER_ENTRY
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.pages.ufield.R
import com.cyxbs.pages.ufield.ui.fragment.campaignfragment.CheckFragment
import com.cyxbs.pages.ufield.ui.fragment.campaignfragment.JoinFragment
import com.cyxbs.pages.ufield.ui.fragment.campaignfragment.PublishFragment
import com.cyxbs.pages.ufield.ui.fragment.campaignfragment.WatchFragment
import com.cyxbs.pages.ufield.viewmodel.MessageViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import kotlin.properties.Delegates

@KClassProvider(clazz = Activity::class, name = UFIELD_CENTER_ENTRY)
class CampaignActivity : BaseActivity() {


    private val mTabLayout by R.id.ufield_activity_campaign_tl.view<TabLayout>()

    private val mViewPager2 by R.id.ufield_activity_campaign_vp2.view<ViewPager2>()

    private val campaignBack by R.id.ufield_activity_campaign_rl_back.view<RelativeLayout>()

    private var tab1View by Delegates.notNull<View>()

    private var tab2View by Delegates.notNull<View>()

    private var tab3View by Delegates.notNull<View>()

    private var tab4View by Delegates.notNull<View>()

    private val mViewModel by viewModels<MessageViewModel>()

    private var enterTime: Long = 0

    override fun onResume() {
        super.onResume()
        enterTime = System.currentTimeMillis()
        appCoroutineScope.launch {
            TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_HDZX_HOMEPAGE)
        }
    }

    override fun onStop() {
        super.onStop()
        val exitTime = System.currentTimeMillis()
        val duration = exitTime - enterTime
        appCoroutineScope.launch {
            TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_HDZX,duration,enterTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ufield_activity_campaign)
        initViewClickListener()
        initViewPager2()
        initTabLayout()
        mViewModel.getAllMsg()
    }

    private fun initViewClickListener() {
        campaignBack.setOnSingleClickListener { finish() }
    }

    private fun initViewPager2() {
        mViewPager2.adapter = FragmentVpAdapter(this)
            .add { WatchFragment() }
            .add { JoinFragment() }
            .add { PublishFragment() }
            .add { CheckFragment() }
    }

    private fun initTabLayout() {
        val tabs = arrayOf(
            getString(R.string.ufield_campaign_watch),
            getString(R.string.ufield_campaign_join),
            getString(R.string.ufield_campaign_publish),
            getString(R.string.ufield_campaign_check),
        )
        TabLayoutMediator(
            mTabLayout, mViewPager2
        ) { tab,
            position ->
            tab.text = tabs[position]
        }.attach()
        //设置tabView
        val tab1 = mTabLayout.getTabAt(0)
        val tab2 = mTabLayout.getTabAt(1)
        val tab3 = mTabLayout.getTabAt(2)
        val tab4 = mTabLayout.getTabAt(3)
        tab1View = LayoutInflater.from(this).inflate(R.layout.ufield_activity_campaign_tab1, null)
        tab1?.customView = tab1View
        tab2View = LayoutInflater.from(this).inflate(R.layout.ufield_activity_campaign_tab2, null)
        tab2?.customView = tab2View
        tab3View = LayoutInflater.from(this).inflate(R.layout.ufield_activity_campaign_tab3, null)
        tab3?.customView = tab3View
        tab4View = LayoutInflater.from(this).inflate(R.layout.ufield_activity_campaign_tab4, null)
        tab4?.customView = tab4View
        //设置选中和未选中时的颜色
        val onTabSelectedListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.ufield_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(com.cyxbs.components.config.R.color.config_level_one_font_color.color))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.ufield_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(R.color.uField_alpha_level_three_font_color.color))

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        }
        mTabLayout.addOnTabSelectedListener(onTabSelectedListener)
    }
}