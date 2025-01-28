package com.cyxbs.pages.grades.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.webView.LiteJsWebView
import com.cyxbs.components.config.route.DISCOVER_GRADES
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.grades.R
import com.cyxbs.pages.grades.bean.Exam
import com.cyxbs.pages.grades.bean.analyze.isSuccessful
import com.cyxbs.pages.grades.ui.adapter.ExamAdapter
import com.cyxbs.pages.grades.ui.fragment.GPAFragment
import com.cyxbs.pages.grades.ui.fragment.NoDataFragment
import com.cyxbs.pages.grades.ui.viewModel.ContainerViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior


/**
 * @CreateBy: FxyMine4ever
 *
 * @CreateAt:2018/9/16
 */

@KClassProvider(clazz = Activity::class, name= DISCOVER_GRADES)
class ContainerActivity : BaseActivity() {

    private val viewModel by viewModels<ContainerViewModel>()

    private lateinit var mAdapter: ExamAdapter
    private val data = mutableListOf<Exam>()

    private val mTvGradesRefresh by R.id.tv_grades_no_refresh.view<TextView>()
    private val mRvExamMain by R.id.rv_exam_main.view<RecyclerView>()
    private val mTvGradesStuNum by R.id.tv_grades_stuNum.view<TextView>()
    private val parent by R.id.fl_grades_bottom_sheet.view<FrameLayout>()
    private val mTvGradesName by R.id.tv_grades_name.view<TextView>()
    private val mWvExamMain by R.id.wv_exam_main.view<LiteJsWebView>()
    private val mIvGradesAvatar by R.id.iv_grades_avatar.view<ImageView>()
    private val common_toolbar by R.id.toolbar.view<JToolbar>()

    private val mBottomSheetBehavior by lazy {
        BottomSheetBehavior.from(parent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.grades_activity_container)
        common_toolbar.apply {
            setBackgroundColor(
                ContextCompat.getColor(
                    this@ContainerActivity,
                    R.color.gradle_toolbar_bg,
                )
            )
            init(
                activity = this@ContainerActivity,
                title = "考试与成绩",
                withSplitLine = false,
            )
            setTitleLocationAtLeft(true)
        }
        init()
    }

    private fun init() {
        if (!IAccountService::class.impl().isLogin()) {
            return
        }
        initExam()
        initBottomSheet()
        initObserver()
        viewModel.getAnalyzeData()
    }

    private fun initObserver() {
        viewModel.analyzeData.observe {
            if (it.isSuccessful) {
                mTvGradesRefresh.gone()
                replaceFragment(GPAFragment())
            } else {
                mTvGradesRefresh.visible()
                mTvGradesRefresh.text = getString(R.string.grades_no_data_stdNum)
                replaceFragment(NoDataFragment())
                mTvGradesRefresh.setOnSingleClickListener {
                    viewModel.getAnalyzeData() // 刷新数据
                }
            }
        }
    }

    private fun initExam() {
        viewModel.getStatus()
        mAdapter = ExamAdapter(data)
        mRvExamMain.adapter = mAdapter
        mRvExamMain.layoutManager = LinearLayoutManager(this@ContainerActivity)

        //观察数据
        viewModel.examData.observe(this@ContainerActivity, Observer { list ->
            data.addAll(list)
            mAdapter.notifyDataSetChanged()
        })

        viewModel.nowStatus.observe(this@ContainerActivity, Observer { status ->
            if (status.examModel == "magipoke") {
                mRvExamMain.visibility = View.VISIBLE
                mWvExamMain.visibility = View.GONE
                loadExam()
            } else {
                loadH5(status.url)
            }
        })

    }

    private fun loadExam() {
        viewModel.loadData(IAccountService::class.impl().stuNum.orEmpty())
    }

    private fun initBottomSheet() {
        initHeader()
    }

    private fun initHeader() {
        val userInfo = IAccountService::class.impl().userInfo.value ?: return
        Glide.with(this).load(userInfo.photoSrc).into(mIvGradesAvatar)
        mTvGradesStuNum.text = userInfo.stuNum
        mTvGradesName.text = userInfo.username
    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val transaction: FragmentTransaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.grades_bottom_sheet_frame_layout, fragment)
        transaction.commit()
    }


    override fun onBackPressed() {
        val behavior = mBottomSheetBehavior
        if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadH5(baseUrl: String) {
        mRvExamMain.visibility = View.GONE
        mWvExamMain.visibility = View.VISIBLE
        //H5使用Vue，需要开启js
        mWvExamMain.setBackgroundColor(0)
        mWvExamMain.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
        }
        val stuNum = IAccountService::class.impl().stuNum
        val uiType =
            if (
                this.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK
                == Configuration.UI_MODE_NIGHT_YES
            ) 1 else 0
        val url = "$baseUrl/?stuNum=$stuNum&uiType=$uiType"
        mWvExamMain.loadUrl(url)
    }
}
