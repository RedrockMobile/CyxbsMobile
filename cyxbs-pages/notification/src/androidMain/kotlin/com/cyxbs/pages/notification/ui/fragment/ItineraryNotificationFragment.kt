package com.cyxbs.pages.notification.ui.fragment

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.base.ui.viewModelBy
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.dp2pxF
import com.cyxbs.pages.notification.R
import com.cyxbs.pages.notification.ui.activity.NotificationActivity
import com.cyxbs.pages.notification.util.Constant.IS_SWITCH1_SELECT
import com.cyxbs.pages.notification.util.NotificationSp
import com.cyxbs.pages.notification.viewmodel.ItineraryViewModel
import com.cyxbs.pages.notification.viewmodel.NotificationViewModel
import com.cyxbs.pages.notification.widget.ScaleInTransformer
import com.cyxbs.components.utils.utils.impl.defaultImpl
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

    // 是否需要展示tab的红点
    private var shouldShowTabRedDots by Delegates.notNull<Boolean>()

    // 父页面（NotificationActivity）的实例
    private var myActivity by Delegates.notNull<NotificationActivity>()

    // 使用和父页面（NotificationActivity）同一个ViewModel来与父页面通信
    private val parentViewModel by activityViewModels<NotificationViewModel>()

    // 获取本fragment的viewModel，通过该viewModel与本fragment的两个子fragment通信
    private val itineraryViewModel by viewModelBy { ItineraryViewModel(parentViewModel) }

    // 当前DisplayContainer展示哪个页面
    private var currentPageIndex: Int
        get() = itineraryViewModel.currentPageIndex.value!!
        set(value) = itineraryViewModel.changeCurrentPageIndex(value)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        myActivity = requireActivity() as NotificationActivity
        shouldShowTabRedDots = myActivity.NotificationSp.getBoolean(IS_SWITCH1_SELECT, true)
//        initUIModeConfig()
        initCollect()
        initObserver()
        initViewPager2()
        initTabLayout()
    }
    /*
    // 动态适配夜间模式的一些配置
    private fun initUIModeConfig() {
        //  app应用了何种模式
        var uiMode = resources.configuration.uiMode
        val uiModeManager =  myActivity.getSystemService(AppCompatActivity.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES) { // 系统当前为夜间模式
            itineraryTypeTab.background = myActivity.getDrawable(R.drawable.notification_shape_itinerary_cl_container_night)
        }
    }
     */

    private fun initViewPager2() {
        itineraryDisplayContainer.adapter = FragmentVpAdapter(this)
            .add { ReceivedItineraryFragment() }
            .add { SentItineraryFragment() }
        // 防止多级VP2的滑动冲突，此处禁止 vp 自由滑动
        itineraryDisplayContainer.isUserInputEnabled = false
        itineraryDisplayContainer.setPageTransformer(ScaleInTransformer())
        itineraryDisplayContainer.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPageIndex = position
            }
        })

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
                myActivity,
                R.drawable.notification_shape_tab_divider_vertical
            )
//            dividerPadding = 3
        }

        // 设置tabLayout的阴影
        initShadowShape()

        val tab1 = itineraryTypeTab.getTabAt(0)
        tab1View = LayoutInflater.from(myActivity)
            .inflate(R.layout.notification_item_itinerary_tab1, itineraryTypeTab.parent as ViewGroup, false)
        tab1?.customView = tab1View

        val tab2 = itineraryTypeTab.getTabAt(1)
        tab2View = LayoutInflater.from(myActivity)
            .inflate(R.layout.notification_item_itinerary_tab2, itineraryTypeTab.parent as ViewGroup, false)
        tab2?.customView = tab2View
        // 取消当前显示fragment页面的对应的tab的红点
        changeTabRedDotsVisibility(currentPageIndex, View.INVISIBLE)
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
            initializeElevationOverlay(myActivity)
            elevation = 12F.dp2pxF
            setShadowColor(com.cyxbs.components.init.appContext.getColor(R.color.notification_itinerary_tl_shadow_source))
//            shadowVerticalOffset = 13F.dp2pxF.toInt()
        }
        (itineraryTypeTabShadow.parent as ViewGroup).clipChildren = false
        itineraryTypeTabShadow.background = backgroundDrawable
    }

    private fun initObserver() {
        // 如果在宿主Activity中获取行程消息成功，则从宿主Activity的viewModel拿数据
        parentViewModel.itineraryMsg.observe {
            itineraryViewModel.getReceivedItinerary()
            itineraryViewModel.getSentItinerary()
        }

        // 也许后续会考虑把观察者的遍历逻辑放入携程中
        itineraryViewModel.receivedItineraryList.observe { list ->
            // 如果receivedItineraryList中存在未读行程, 获取其中所有未读行程 的id
            var isVisibleRedDot = false
            val tempList = mutableListOf<Int>()
            list.forEach {
                if (!it.hasRead){
                    tempList.add(it.id)
                    if (!isVisibleRedDot) {
                        changeTabRedDotsVisibility(0, View.VISIBLE)
                        isVisibleRedDot = true
                    }
                }
            }
            if (!isVisibleRedDot) changeTabRedDotsVisibility(0, View.INVISIBLE)
            itineraryViewModel.setUnReadReceivedItineraryIds(tempList)
        }

        // 同上
        itineraryViewModel.sentItineraryList.observe { list ->
            // 如果sentItineraryList中存在未读行程, 获取其中所有未读行程 的id
            var isVisibleRedDot = false
            val tempList = mutableListOf<Int>()
            list.forEach {
                if (!it.hasRead){
                    tempList.add(it.id)
                    if (!isVisibleRedDot) {
                        changeTabRedDotsVisibility(1, View.VISIBLE)
                        isVisibleRedDot = true
                    }
                }
            }
            if (!isVisibleRedDot) changeTabRedDotsVisibility(1, View.INVISIBLE)
            itineraryViewModel.setUnReadSentItineraryIds(tempList)
        }

        // 观察 放置两种行程消息的viewPager的当前页面index，接收为 0，发送为 1
        // 把这个index放入ViewModel持久化保存以便发生Activity重建时可以记住之前的位置
        itineraryViewModel.currentPageIndex.observe {
            changeTabRedDotsVisibility(it, View.INVISIBLE)
            when (it) {
                0 -> {
                    itineraryViewModel.allUnReadReceivedItineraryIds.apply {
                        if (this.value.isNullOrEmpty()) {
                            return@observe
                        }
                        if (myActivity.whichPageIsIn == 2) { // 行程页面可见
                            itineraryViewModel.changeItineraryReadStatus(this.value!!, 0)
                        }
                    }
                }

                1 -> {
                    itineraryViewModel.allUnReadSentItineraryIds.apply {
                        if (this.value.isNullOrEmpty()) {
                            return@observe
                        }
                        if (myActivity.whichPageIsIn == 2) { // 行程页面可见
                            itineraryViewModel.changeItineraryReadStatus(this.value!!, 1)
                        }
                    }
                }
            }
        }

    }

    /**
     * 未读消息更新时
     */
    private fun initCollect() {
        itineraryViewModel.newUnReadSentItineraryIds.collectLaunch {
            if (!it.isNullOrEmpty() && myActivity.whichPageIsIn == 2 && currentPageIndex == 1) {
                itineraryViewModel.changeItineraryReadStatus(it, 1)
            }
        }
        itineraryViewModel.newUnReadReceivedItineraryIds.collectLaunch {
            if (!it.isNullOrEmpty() && myActivity.whichPageIsIn == 2 && currentPageIndex == 0) {
                itineraryViewModel.changeItineraryReadStatus(it, 0)
            }
        }
    }

    //改变TabLayout小红点的显示状态
    private fun changeTabRedDotsVisibility(position: Int, visibility: Int) {
        if ((visibility != View.INVISIBLE) and (visibility != View.VISIBLE))
            throw Exception("参数只可以是View.INVISIBLE 或者 View.VISIBLE！！！")
        var vis = visibility
        if (!shouldShowTabRedDots || position == currentPageIndex)
            vis = View.INVISIBLE
        when (position) {
            0 -> {
                tab1View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
            }

            1 -> {
                tab2View.findViewById<View>(R.id.notification_iv_tl_red_dots).visibility = vis
            }
        }
    }
}
