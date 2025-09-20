package com.cyxbs.pages.notification.ui.fragment

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.base.ui.viewModelBy
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.dp2pxF
import com.cyxbs.components.utils.utils.impl.defaultImpl
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.viewmodel.ItineraryViewModel
import com.cyxbs.pages.notification.viewmodel.NotificationViewModel
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
class ItineraryNotificationFragment : BaseFragment(R.layout.notification_fragment_itinerary) {

    private var tab2View by Delegates.notNull<View>()  // 发送
    private var tab1View by Delegates.notNull<View>()  // 接收

    private val itineraryDisplayContainer by R.id.notification_itinerary_vp2.view<ViewPager2>()
    private val itineraryTypeTab by R.id.notification_itinerary_tl_itiner_type.view<TabLayout>()
    private val itineraryTypeTabShadow by R.id.notification_itinerary_tl_shadow_source.view<View>()

    // 获取本fragment的viewModel，通过该viewModel与本fragment的两个子fragment通信
    private val itineraryViewModel by viewModels<ItineraryViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewPager2()
        initTabLayout()
        initObserver()
    }

    private fun initViewPager2() {
        itineraryDisplayContainer.adapter = FragmentVpAdapter(this)
            .add { ReceivedItineraryFragment() }
            .add { SentItineraryFragment() }
        // 解决多级 VP2 的滑动冲突，这种解决方式是最轻量级的一种
        (itineraryDisplayContainer.getChildAt(0) as RecyclerView).also { rv ->
            rv.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                var initialX = 0F
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (e.action == MotionEvent.ACTION_DOWN) {
                        initialX = e.x
                        itineraryDisplayContainer.parent.requestDisallowInterceptTouchEvent(true)
                    } else if (e.action == MotionEvent.ACTION_MOVE) {
                        if (e.x > initialX && !rv.canScrollHorizontally(-1)) {
                            // 滑到最左侧并且手指不能向右滑动时，此时允许父 vp2 拦截滑动事件
                            itineraryDisplayContainer.parent.requestDisallowInterceptTouchEvent(false)
                        } else if (e.x < initialX && !rv.canScrollHorizontally(1)) {
                            // 滑到最右侧并且手指不能向左滑动时，此时允许父 vp2 拦截滑动事件
                            itineraryDisplayContainer.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }
    }

    private fun initTabLayout() {
        val tabs = arrayOf("接收", "发送")
        // tab的文字颜色
        itineraryTypeTab.addOnTabSelectedListener(object :
            TabLayout.OnTabSelectedListener by defaultImpl() {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.customView?.findViewById<TextView>(R.id.notification_tv_tl_tab)
                    ?.setTextColor(R.color.notification_itinerary_tabLayout_text_selected.color)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.customView?.findViewById<TextView>(R.id.notification_tv_tl_tab)
                    ?.setTextColor(R.color.notification_itinerary_tabLayout_text_unselect.color)
            }
        })
        //添加title
        TabLayoutMediator(itineraryTypeTab, itineraryDisplayContainer) { tab, position ->
            tab.text = tabs[position]
        }.attach()
        //添加分隔线,tab就是tablayout
        val linearLayout = itineraryTypeTab.getChildAt(0) as LinearLayout
        linearLayout.apply {
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.notification_shape_tab_divider_vertical
            )
        }

        // 设置tabLayout的阴影
        initShadowShape()

        val tab1 = itineraryTypeTab.getTabAt(0)
        tab1View = LayoutInflater.from(requireContext())
            .inflate(R.layout.notification_item_itinerary_tab1, itineraryTypeTab.parent as ViewGroup, false)
        tab1?.customView = tab1View

        val tab2 = itineraryTypeTab.getTabAt(1)
        tab2View = LayoutInflater.from(requireContext())
            .inflate(R.layout.notification_item_itinerary_tab2, itineraryTypeTab.parent as ViewGroup, false)
        tab2?.customView = tab2View
    }

    private fun initShadowShape() {
        val shapePathModel = ShapeAppearanceModel.builder()
            .setBottomLeftCorner(RoundedCornerTreatment())
            .setBottomRightCorner(RoundedCornerTreatment())
            .setBottomLeftCornerSize(16F.dp2pxF)
            .setBottomRightCornerSize(16F.dp2pxF)
            .build()

        val backgroundDrawable = MaterialShapeDrawable(shapePathModel).apply {
            setTint(Color.parseColor("#00000000"))
            paintStyle = Paint.Style.FILL
            shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
            initializeElevationOverlay(requireContext())
            elevation = 12F.dp2pxF
            setShadowColor(appContext.getColor(R.color.notification_itinerary_tl_shadow_source))
        }
        (itineraryTypeTabShadow.parent as ViewGroup).clipChildren = false
        itineraryTypeTabShadow.background = backgroundDrawable
    }

    private fun initObserver() {
        itineraryViewModel.receivedItineraryList.onEach { list ->
            changeTabRedDotsVisibility(0, list.any { !it.hasRead })
        }.launchIn(viewLifecycleScope)

        // 同上
        itineraryViewModel.sentItineraryList.onEach { list ->
            changeTabRedDotsVisibility(1, list.any { !it.hasRead })
        }.launchIn(viewLifecycleScope)
    }

    //改变TabLayout小红点的显示状态
    private fun changeTabRedDotsVisibility(position: Int, visibility: Boolean) {
        val vis = if (visibility) View.VISIBLE else View.INVISIBLE
        when (position) {
            0 -> tab1View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
            1 -> tab2View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
        }
    }
}
