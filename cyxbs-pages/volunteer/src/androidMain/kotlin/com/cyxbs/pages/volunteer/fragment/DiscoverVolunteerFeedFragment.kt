package com.cyxbs.pages.volunteer.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.config.route.DISCOVER_VOLUNTEER
import com.cyxbs.components.config.route.DISCOVER_VOLUNTEER_RECORD
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.pages.volunteer.R
import com.cyxbs.pages.volunteer.adapter.VolunteerFeedAdapter
import com.cyxbs.pages.volunteer.adapter.VolunteerFeedUnbindAdapter
import com.cyxbs.pages.volunteer.event.VolunteerLoginEvent
import com.cyxbs.pages.volunteer.event.VolunteerLogoutEvent
import com.cyxbs.pages.volunteer.viewmodel.DiscoverVolunteerFeedViewModel
import com.mredrock.cyxbs.common.ui.BaseFeedFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DiscoverVolunteerFeedFragment : BaseFeedFragment<DiscoverVolunteerFeedViewModel>() {


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EventBus.getDefault().register(this) // 不建议使用 EventBus
        val accountService = IAccountService::class.impl()
        //对登录状态判断
        if (accountService.isLogin()) {
            setAdapter(VolunteerFeedUnbindAdapter())
        }
        accountService.state
            .onEach {
                if (it is AccountState.Login) {
                    setAdapter(VolunteerFeedUnbindAdapter())
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)
        init()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this) // 不建议使用 EventBus
    }

    private fun init() {
        setTitle(getString(R.string.volunteer_service_inquire_string))
        setAdapter(VolunteerFeedUnbindAdapter())

        viewModel.volunteerData.observe { volunteerTime ->
            //当data变化了，那说明用户主动再登录了
            //改变了，刷新
            volunteerTime?.let {
                val adapter = getAdapter()
                if (adapter is VolunteerFeedAdapter) {
                    adapter.refresh(it)
                } else {
                    setAdapter(VolunteerFeedAdapter(it))
                }

            }
        }
        viewModel.loadFailed.observe {
            it ?: return@observe
            val adapter = getAdapter()
            if (it && adapter is VolunteerFeedUnbindAdapter) {
                adapter.refresh("查询失败，请稍后再试")
            }
        }
        setOnClickListener {
            doIfLogin(getString(R.string.volunteer_service_inquire_string)) {
                if (!viewModel.isQuerying) {
                    if (viewModel.volunteerData.value != null) {
                        EventBus.getDefault().postSticky(VolunteerLoginEvent(viewModel.volunteerData.value!!))
                        startActivity(DISCOVER_VOLUNTEER_RECORD) {
                            putExtra("volunteerTime", defaultGson.toJson(viewModel.volunteerData.value))
                        }
                    } else {
                        startActivity(DISCOVER_VOLUNTEER)
                    }
                }
            }
        }
    }

    override var hasTopSplitLine = true

    //onResume
    override fun onRefresh() {
        //首先判断是否登录，没登录，那直接return
        if (!IAccountService::class.impl().isLogin()) {
            return
        }
        //再判断vm是否有数据，有数据直接加载，再return
        if (viewModel.volunteerData.value != null) {
            viewModel.volunteerData.value?.let {
                val adapter = getAdapter()
                if (adapter is VolunteerFeedAdapter) {
                    adapter.refresh(it)
                } else {
                    setAdapter(VolunteerFeedAdapter(it))
                }

            }
            return
        }
        if (viewModel.requestUnBind) {
            val adapter = getAdapter()
            if (adapter is VolunteerFeedUnbindAdapter) {
                adapter.refresh(getString(R.string.volunteer_unbind))
            } else {
                setAdapter(VolunteerFeedUnbindAdapter())
            }
            return
        }
        //再判断是否绑定，绑定，就请求。能到这里，说明vm已经没有数据了
        if (!viewModel.isBind) {
            viewModel.loadVolunteerTime()
            val adapter = getAdapter()
            if (adapter is VolunteerFeedUnbindAdapter) {
                adapter.refresh("查询中...")
            }
        }
    }

    //用于首次登录志愿者后的数据后传给发现首页刷新，把数据给vm
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun getVolunteerTime(volunteerLoginEvent: VolunteerLoginEvent) {
        if (viewModel.volunteerData.value == null && !viewModel.isQuerying) {
            viewModel.volunteerData.value = volunteerLoginEvent.volunteerTime
        }
    }

    //用于处理VolunteerRecordActivity的取消绑定事件，清除vm数据，并重设adapter
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun unbindVolunteer(volunteerLogoutEvent: VolunteerLogoutEvent) {
        viewModel.unbind()
    }


}
