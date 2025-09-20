package com.cyxbs.pages.notification.ui.fragment

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.createViewModelLazy
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.adapter.ReceivedItineraryNotificationRvAdapter
import com.cyxbs.pages.notification.bean.ReceivedItineraryMsgBean
import com.cyxbs.pages.notification.viewmodel.ItineraryViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.properties.Delegates

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/4
 * @Description:
 *
 */
class ReceivedItineraryFragment : BaseFragment(R.layout.notification_fragment_itinerary_received) {

    private val receivedItineraryRv by R.id.notification_itinerary_rv_received.view<RecyclerView>()

    // SentItineraryFragment页面的 rv数据
    private var data = listOf<ReceivedItineraryMsgBean>()

    // rv适配器
    private val adapter by lazy { ReceivedItineraryNotificationRvAdapter(::addToSchedule) }

    // 宿主fragment
    private var parentFragment by Delegates.notNull<ItineraryNotificationFragment>()

    // 获取宿主fragment(ItineraryNotificationFragment) 的 ViewModel
    private val itineraryViewModel by createViewModelLazy(
        ItineraryViewModel::class,
        { requireParentFragment().viewModelStore }
    )

    // 是否已经触发过 onResume()
    private var hasResumed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragment = getParentFragment() as ItineraryNotificationFragment
        initObserver()
        initCollect()
        initRv()
    }

    // ViewPager2 滑到当前页时才会触发 onResume()
    override fun onResume() {
        super.onResume()
        if (!hasResumed) {
            hasResumed = true
            itineraryViewModel.changeItineraryReadStatus(
                itineraryViewModel.receivedItineraryList.value.mapNotNull {
                    if (!it.hasRead) it.id else null
                },
                true
            )
        }
    }

    private fun initRv() {
        receivedItineraryRv.adapter = adapter

        // 动画效果
        val resId = R.anim.notification_layout_animation_fall_down
        val anim = AnimationUtils.loadLayoutAnimation(requireContext(), resId)
        receivedItineraryRv.layoutAnimation = anim
        receivedItineraryRv.layoutManager = LinearLayoutManager(this.context)
    }

    private fun initObserver() {
        itineraryViewModel.receivedItineraryList.onEach {
            data = it.reversed()
            adapter.submitList(data)
            //让数据更改有动画效果
            receivedItineraryRv.scheduleLayoutAnimation()
        }.launchIn(viewLifecycleScope)
    }

    private fun initCollect() {
        itineraryViewModel.add2scheduleIsSuccessfulEvent.collectLaunch {
            if (it.second) {
                val tempList = adapter.currentList.toMutableList().apply {
                    this[it.first] = this[it.first].copy(hasAdd = true)
                }
                adapter.submitList(tempList)
                data = tempList
            }
        }
    }

    private fun addToSchedule(index: Int) {
        val itemData = adapter.currentList[index]
        itineraryViewModel.addItineraryToSchedule(
            index,
            0,
            itemData
        )
    }
}
