package com.cyxbs.pages.qa.home

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.QA_ENTRY
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.home.fragment.QaAllFragment
import com.cyxbs.pages.qa.home.fragment.QaFreshmanFragment
import com.cyxbs.pages.qa.home.fragment.QaLifeFragment
import com.cyxbs.pages.qa.home.fragment.QaOtherFragment
import com.cyxbs.pages.qa.home.fragment.QaStudyFragment
import com.cyxbs.pages.qa.publish.ui.activity.PublishActivity
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.properties.Delegates

/**
 * description ： QA的首页
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/11 23:07
 */

@KClassProvider(clazz = Activity::class, name = QA_ENTRY)
class HomeActivity : BaseActivity() {

    private val qaHomeBtnSearch by R.id.qa_home_btn_search.view<EditText>()
    private val qaHomeBtnPublish by R.id.qa_home_btn_publish.view<LinearLayout>()
    private val qaHomeBtnReturn by R.id.qa_home_iv_return.view<ImageView>()
    private val mTabLayout: TabLayout by R.id.qa_home_tab_layout.view()
    private val mVP: ViewPager2 by R.id.qa_home_vp2.view()
    private var tab1View by Delegates.notNull<View>()
    private var tab2View by Delegates.notNull<View>()
    private var tab3View by Delegates.notNull<View>()
    private var tab4View by Delegates.notNull<View>()
    private var tab5View by Delegates.notNull<View>()

    // 持有监听实例，方便 onDestroy 移除
    private lateinit var pageChangeCallback: ViewPager2.OnPageChangeCallback
    private lateinit var tabSelectedListener: TabLayout.OnTabSelectedListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_home)
        initTab()
        initClick()
        setTabIndicatorWidth(mTabLayout, 16)
    }

    private fun initTab() {
        mVP.adapter = FragmentVpAdapter(this)
            .add { QaAllFragment() }
            .add { QaFreshmanFragment() }
            .add { QaLifeFragment() }
            .add { QaStudyFragment() }
            .add { QaOtherFragment() }

        // 切换回调
        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                hideOtherTabDots(position)
            }
        }
        mVP.registerOnPageChangeCallback(pageChangeCallback)

        val tabs = arrayOf("全部", "新生类", "生活类", "学习类", "其他")
        TabLayoutMediator(mTabLayout, mVP) { tab, position ->
            tab.text = tabs[position]
        }.attach()

        val tab1 = mTabLayout.getTabAt(0)
        val tab2 = mTabLayout.getTabAt(1)
        val tab3 = mTabLayout.getTabAt(2)
        val tab4 = mTabLayout.getTabAt(3)
        val tab5 = mTabLayout.getTabAt(4)

        tab1View = LayoutInflater.from(this).inflate(R.layout.qa_tablayout_item_all, null)
        tab1?.customView = tab1View
        tab2View = LayoutInflater.from(this).inflate(R.layout.qa_tablayout_item_freshman, null)
        tab2?.customView = tab2View
        tab3View = LayoutInflater.from(this).inflate(R.layout.qa_tablayout_item_life, null)
        tab3?.customView = tab3View
        tab4View = LayoutInflater.from(this).inflate(R.layout.qa_tablayout_item_study, null)
        tab4?.customView = tab4View
        tab5View = LayoutInflater.from(this).inflate(R.layout.qa_tablayout_item_other, null)
        tab5?.customView = tab5View

        for (i in 1..4) {
            val sp = getSp("tab_dot_cache")
            val count = sp.getInt("tab_dot_$i", 0)
            val tab = mTabLayout.getTabAt(i)
            val dotView = tab?.customView?.findViewById<TextView>(R.id.qa_dot_view)
            dotView?.apply {
                text = if (count >= 10) "9+" else count.toString()
                visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }


        tabSelectedListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.qa_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(R.color.qa_text_title_color.color))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.qa_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(R.color.qa_text_title_color.color))
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        }
        mTabLayout.addOnTabSelectedListener(tabSelectedListener)
    }


    private fun initClick() {
        qaHomeBtnReturn.setOnClickListener {
            finish()
        }
        qaHomeBtnSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SearchActivity.strartActivity(qaHomeBtnSearch.text.toString(), this)
                val sp = this.getSp("search_keyword")
                sp.edit().apply {
                    putString("keyword", qaHomeBtnSearch.text.toString())
                    apply()
                }
                true
            } else {
                false
            }
        }
        qaHomeBtnPublish.setOnClickListener {
            PublishActivity.startActivity(this)
        }
    }

    fun setTabIndicatorWidth(tabLayout: TabLayout, shortenDp: Int) {
        tabLayout.post {
            val tabStrip = tabLayout.getChildAt(0) as ViewGroup
            for (i in 0 until tabLayout.tabCount) {
                val tabView = tabStrip.getChildAt(i)
                val textView = tabView.findViewById<TextView>(R.id.qa_tv_tl_tab)
                val textWidth = textView.width
                val indicatorWidth = textWidth - dp2px(shortenDp.toFloat())

                val drawable =
                    ContextCompat.getDrawable(tabLayout.context, R.drawable.qa_ic_tab_indicator)
                if (drawable != null) {
                    val leftInset = (textWidth - indicatorWidth) / 2
                    val rightInset = leftInset
                    val insetDrawable = InsetDrawable(drawable, leftInset, 0, rightInset, 0)
                    tabLayout.setSelectedTabIndicator(insetDrawable)
                }
            }
        }
    }


    fun dp2px(dp: Float): Int {
        return (dp * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
    }

    //更新角标这里给 QaAllFragment使用
    fun updateTabDot(tabIndex: Int, newCount: Int) {
        val sp = getSp("tab_dot_cache")
        val key = "tab_dot_$tabIndex"

        // 读取之前的缓存值
        val oldCount = sp.getInt(key, 0)
        val totalCount = oldCount + newCount

        // 保存新的总数
        sp.edit().putInt(key, totalCount).apply()

        // 更新 UI
        val tab = mTabLayout.getTabAt(tabIndex)
        val dotView = tab?.customView?.findViewById<TextView>(R.id.qa_dot_view)
        dotView?.apply {
            text = if (totalCount >= 10) "9+" else totalCount.toString()
            visibility = if (totalCount > 0) View.VISIBLE else View.GONE
        }
    }


    fun hideOtherTabDots(currentIndex: Int) {
        val sp = getSp("tab_dot_cache")
        val key = "tab_dot_$currentIndex"

        // 清空对应 tab 的缓存
        sp.edit().putInt(key, 0).apply()

        // 隐藏 UI
        val tab = mTabLayout.getTabAt(currentIndex)
        val dotView = tab?.customView?.findViewById<TextView>(R.id.qa_dot_view)
        dotView?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        // 解绑监听，避免内存泄漏
        mVP.unregisterOnPageChangeCallback(pageChangeCallback)
        mTabLayout.removeOnTabSelectedListener(tabSelectedListener)
    }
}
