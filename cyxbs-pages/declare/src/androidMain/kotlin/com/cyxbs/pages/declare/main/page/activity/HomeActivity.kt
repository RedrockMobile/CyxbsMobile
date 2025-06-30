package com.cyxbs.pages.declare.main.page.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DECLARE_ENTRY
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnDoubleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.pages.declare.R
import com.cyxbs.pages.declare.detail.page.activity.DetailActivity
import com.cyxbs.pages.declare.main.page.adapter.HomeRvAdapter
import com.cyxbs.pages.declare.main.page.viewmodel.HomeViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import kotlinx.coroutines.launch

@KClassProvider(clazz = Activity::class, name = DECLARE_ENTRY)
class HomeActivity : BaseActivity() {
    companion object {
        /**
         * 启动表态主页
         */
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, HomeActivity::class.java))
        }
    }

    private val mViewModel by viewModels<HomeViewModel>()

    private val declareHomeRecyclerview by R.id.declare_home_recyclerview.view<RecyclerView>()
    private val declareHomeToolbarTv by R.id.declare_home_toolbar_tv.view<TextView>()
    private val declareHomeToolbarPost by R.id.declare_home_toolbar_post.view<View>()
    private val declareIvToolbarArrowLeft by R.id.declare_iv_toolbar_arrow_left.view<View>()
    private val declareHomeNoData by R.id.declare_home_no_data.view<View>()
    private val declareHomeCl by R.id.declare_home_cl.view<View>()
    private val declareHomeNoNet by R.id.declare_home_no_net.view<View>()

    private var enterTime: Long = 0

    override fun onPause() {
        super.onPause()
        val exitTime = System.currentTimeMillis()
        val duration = exitTime - enterTime
        appCoroutineScope.launch {
            TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_BTGC,duration,enterTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.declare_activity_home)
        val declareHomeRvAdapter = HomeRvAdapter()
        declareHomeRecyclerview.run {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = declareHomeRvAdapter
            declareHomeToolbarTv.setOnDoubleClickListener {
                smoothScrollToPosition(0)
            }
        }
        declareHomeRvAdapter.setOnItemClickedListener {
            DetailActivity.startActivity(this, it)
        }
        declareHomeToolbarPost.setOnClickListener {
            //跳至自己发布过的话题页面
            PostedActivity.startActivity(this)
        }
        declareIvToolbarArrowLeft.setOnClickListener {
            finish()
        }
        mViewModel.homeLiveData.observe {
            if (it.isEmpty()) {
                declareHomeNoData.visible()
            } else {
                declareHomeNoData.gone()
                declareHomeRvAdapter.submitList(it)
            }
        }
        mViewModel.homeErrorLiveData.observe {
            if (it) {
                declareHomeCl.gone()
                declareHomeNoNet.visible()
            } else {
                declareHomeCl.visible()
                declareHomeNoNet.gone()
            }
        }
        mViewModel.permLiveData.observe {
            declareHomeToolbarPost.run {
                if (it.isPerm) visible() else gone()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mViewModel.hasPerm()
        mViewModel.getHomeData()
        enterTime = System.currentTimeMillis()
        appCoroutineScope.launch {
            TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_BTGC_HOMEPAGE)
        }
    }
}