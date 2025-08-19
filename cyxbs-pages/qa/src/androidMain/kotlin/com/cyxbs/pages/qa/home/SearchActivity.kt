package com.cyxbs.pages.qa.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.home.fragment.QaAllFragment
import com.cyxbs.pages.qa.home.fragment.QaFreshmanFragment
import com.cyxbs.pages.qa.home.fragment.QaLifeFragment
import com.cyxbs.pages.qa.home.fragment.QaOtherFragment
import com.cyxbs.pages.qa.home.fragment.QaStudyFragment
import com.cyxbs.pages.qa.home.viewmodel.SearchViewModel
import com.cyxbs.pages.qa.publish.ui.activity.PublishActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.properties.Delegates

/**
 * description ： 搜索页
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/14 16:22
 */
class SearchActivity : BaseActivity() {
    private val searchViewModel: SearchViewModel by lazy { ViewModelProvider(this)[SearchViewModel::class.java] }
    private val qaSearchBtnReturn by R.id.qa_search_iv_return.view<ImageView>()
    private val qaSearchBtnSearch by R.id.qa_search_btn_search.view<EditText>()
    private val qaSearchBtnPublish by R.id.qa_search_btn_publish.view<LinearLayout>()
    private val mTabLayout: TabLayout by R.id.qa_search_tab_layout.view()
    private val mVP: ViewPager2 by R.id.qa_search_vp2.view()
    private var tab1View by Delegates.notNull<View>()
    private var tab2View by Delegates.notNull<View>()
    private var tab3View by Delegates.notNull<View>()
    private var tab4View by Delegates.notNull<View>()
    private var tab5View by Delegates.notNull<View>()

    companion object {
        fun strartActivity(name: String, context: Context) {
            context.startActivity(
                Intent(context, SearchActivity::class.java).apply {
                    putExtra("name", name)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_search)
        initView()
        initClick()
    }

    private fun initView() {
        qaSearchBtnSearch.setText(intent.getStringExtra("name") ?: "")
        initTab()
        searchViewModel.getQaData(intent.getStringExtra("name") ?: "")
    }

    @SuppressLint("MissingInflatedId")
    fun initTab() {
        mVP.adapter = FragmentVpAdapter(this)
            .add { QaAllFragment() }
            .add { QaFreshmanFragment() }
            .add { QaLifeFragment() }
            .add { QaStudyFragment() }
            .add { QaOtherFragment() }
        val tabs = arrayOf(
            "全部",
            "新生类",
            "生活类",
            "学习类",
            "其他",
        )
        TabLayoutMediator(
            mTabLayout, mVP
        ) { tab,
            position ->
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
        val onTabSelectedListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.qa_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(com.cyxbs.components.config.R.color.config_level_one_font_color.color))
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.customView?.findViewById<TextView>(R.id.qa_tv_tl_tab)
                    ?.setTextColor(ColorStateList.valueOf(com.cyxbs.components.config.R.color.config_alpha_forty_level_two_font_color.color))

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }

        }
        mTabLayout.addOnTabSelectedListener(onTabSelectedListener)

    }

    private fun initClick() {
        qaSearchBtnReturn.setOnClickListener {
            finish()
        }
        qaSearchBtnSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchViewModel.getQaData(qaSearchBtnSearch.text.toString())
                val sp = this.getSp("search_keyword")
                sp.edit().apply {
                    putString("keyword", qaSearchBtnSearch.text.toString())   // 存字符串
                    apply()                             // 提交异步生效（推荐）
                }
                true
            } else {
                false
            }
        }


        //前往发布问题
        qaSearchBtnPublish.setOnClickListener {
            PublishActivity.startActivity(this)
        }
    }


}