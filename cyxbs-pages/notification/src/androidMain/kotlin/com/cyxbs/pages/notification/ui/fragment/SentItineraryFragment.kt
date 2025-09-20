package com.cyxbs.pages.notification.ui.fragment

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.createViewModelLazy
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.adapter.SentItineraryNotificationRvAdapter
import com.cyxbs.pages.notification.bean.SentItineraryMsgBean
import com.cyxbs.pages.notification.ui.activity.NotificationActivity
import com.cyxbs.pages.notification.ui.dialog.RevokeReminderDialog
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
class SentItineraryFragment : BaseFragment(R.layout.notification_fragment_itinerary_sent) {

    private val sentItineraryRv by R.id.notification_itinerary_rv_sent.view<RecyclerView>()

    // SentItineraryFragment页面的 rv数据
    private var data = listOf<SentItineraryMsgBean>()

    // rv适配器
    private val adapter by lazy { SentItineraryNotificationRvAdapter(::cancelReminder) }

    // 宿主fragment
    private var parentFragment by Delegates.notNull<ItineraryNotificationFragment>()

    // 宿主fragment对应的Activity,算是整个消息页面的根容器
    private var myActivity by Delegates.notNull<NotificationActivity>()

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
        myActivity = parentFragment.requireActivity() as NotificationActivity
        initObserver()
        initRv()
    }

    // ViewPager2 滑到当前页时才会触发 onResume()
    override fun onResume() {
        super.onResume()
        if (!hasResumed) {
            hasResumed = true
            itineraryViewModel.changeItineraryReadStatus(
                itineraryViewModel.sentItineraryList.value.mapNotNull {
                    if (!it.hasRead) it.id else null
                },
                true
            )
        }
    }


    private fun initRv() {
        sentItineraryRv.adapter = adapter

        // 动画效果
        val resId = R.anim.notification_layout_animation_fall_down
        val anim = AnimationUtils.loadLayoutAnimation(myActivity, resId)
        sentItineraryRv.layoutAnimation = anim

        sentItineraryRv.layoutManager = LinearLayoutManager(this.context)
    }

    private fun initObserver() {
        itineraryViewModel.sentItineraryList.onEach {
            data = it.reversed()
            adapter.submitList(data)
            //让数据更改有动画效果
            sentItineraryRv.scheduleLayoutAnimation()
        }.launchIn(viewLifecycleScope)

        itineraryViewModel.cancelReminderIsSuccessfulEvent.collectLaunch {
            if (it.second) {  // 取消提醒成功
                val tempList = adapter.currentList.toMutableList().apply {
                    this[it.first] = this[it.first].copy(hasCancel = true)
                }
                adapter.submitList(tempList)
                data = tempList
            }
        }

    }

    private fun cancelReminder(itineraryId: Int, index: Int) {
        val revokeReminderDialog = RevokeReminderDialog(myActivity)
        revokeReminderDialog.setConfirmSelected {
            itineraryViewModel.cancelItineraryReminder(itineraryId, index)
            it.cancel()
        }.show()
    }
}
