package com.cyxbs.pages.mine.page.feedback.center.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.pages.mine.R
import com.cyxbs.pages.mine.page.feedback.center.adapter.FeedbackCenterAdapter
import com.cyxbs.pages.mine.page.feedback.center.viewmodel.FeedbackCenterViewModel
import com.cyxbs.pages.mine.page.feedback.edit.ui.FeedbackEditActivity
import com.cyxbs.pages.mine.page.feedback.history.list.HistoryListActivity
import com.cyxbs.components.utils.utils.Jump2QQHelper
import kotlinx.coroutines.launch

/**
 * @Date : 2021/8/23   20:51
 * @By ysh
 * @Usage :
 * @Request : God bless my code
 **/
class FeedbackCenterActivity : BaseActivity() {

    private val viewModel by viewModels<FeedbackCenterViewModel>()

    /**
     * 初始化adapter
     */
    private val mAdapter by lazy {
        FeedbackCenterAdapter()
    }

    private val mineRecyclerview by R.id.mine_recyclerview.view<RecyclerView>()
    private val fabCenterBack by R.id.fab_center_back.view<View>()
    private val btnQuestion by R.id.btn_question.view<View>()
    private val ivHistory by R.id.iv_history.view<View>()
    private val tvQqTwo by R.id.tv_qq_two.view<TextView>()

    private var enterTime: Long = 0

    override fun onResume() {
        super.onResume()
        enterTime = System.currentTimeMillis()
        appCoroutineScope.launch {
            TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_FKZX_HOMEPAGE)
        }
    }

    override fun onPause() {
        super.onPause()
        val exitTime = System.currentTimeMillis()
        val duration = exitTime - enterTime
        appCoroutineScope.launch {
            TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_FKZX,duration,enterTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mine_activity_feedback_center)
        initView()
        observeData()
        initListener()
    }

    /**
     * 初始化view
     */
    private fun initView() {
        mAdapter.setEventHandler(EventHandler())
        mineRecyclerview.run {
            layoutManager = LinearLayoutManager(context)
            adapter = mAdapter
        }
    }

    /**
     * 监听数据
     */
    private fun observeData() {
        viewModel.apply {
            contentList.observe{
                mAdapter.setData(it)
            }
        }
    }

    /**
     * 初始化listener
     */
    private fun initListener() {
        fabCenterBack.setOnSingleClickListener {
            finish()
        }
        btnQuestion.setOnSingleClickListener {
            startActivity(Intent(this@FeedbackCenterActivity, FeedbackEditActivity::class.java))
        }
        ivHistory.setOnSingleClickListener {
            startActivity(Intent(this@FeedbackCenterActivity, HistoryListActivity::class.java))
        }
        tvQqTwo.text = Jump2QQHelper.FEED_BACK_QQ_GROUP
        tvQqTwo.setOnSingleClickListener {
            Jump2QQHelper.onFeedBackClick()
        }
    }

    /**
     * 每个item的监听事件
     */
    inner class EventHandler {
        var position: Int = 0
        fun onItemClick(itemView: View, title: String, content: String) {
            val intent =
                Intent(this@FeedbackCenterActivity, FeedbackDetailActivity::class.java).apply {
                    putExtra("title", title)
                    putExtra("content", content)
                }
            startActivity(intent)
        }
    }
}