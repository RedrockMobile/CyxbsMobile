package com.cyxbs.pages.notification.ui.fragment

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.adapter.SystemNotificationRvAdapter
import com.cyxbs.pages.notification.bean.ChangeReadStatusToBean
import com.cyxbs.pages.notification.ui.activity.WebActivity
import com.cyxbs.pages.notification.viewmodel.SystemMsgViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Author by OkAndGreat
 * Date on 2022/4/27 17:32.
 *
 */
class SysNotificationFragment : BaseFragment(R.layout.notification_fragment_system) {
    private val notification_rv_sys by R.id.notification_rv_sys.view<RecyclerView>()

    //rv的适配器
    private lateinit var adapter: SystemNotificationRvAdapter

    private val viewModel: SystemMsgViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRv()
        initObserver()
    }

    private fun initRv() {
        adapter =
            SystemNotificationRvAdapter {
                viewModel.changeMsgStatus(ChangeReadStatusToBean(listOf(it.id.toString())))
                WebActivity.startWebViewActivity(it.redirect_url, requireContext())
            }
        notification_rv_sys.adapter = adapter

        val resId = R.anim.notification_layout_animation_fall_down
        val anim = AnimationUtils.loadLayoutAnimation(requireContext(), resId)
        notification_rv_sys.layoutAnimation = anim
        notification_rv_sys.layoutManager = LinearLayoutManager(this.context)
    }

    private fun initObserver() {
        viewModel.systemMsgFlow.onEach { list ->
            adapter.submitList(list)
        }.launchIn(viewLifecycleScope)
    }
}