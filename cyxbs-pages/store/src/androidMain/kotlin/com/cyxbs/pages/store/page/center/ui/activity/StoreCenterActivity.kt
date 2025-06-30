package com.cyxbs.pages.store.page.center.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.STORE_ENTRY
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.extensions.color
import com.cyxbs.components.utils.extensions.dp2pxF
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.pages.store.R
import com.cyxbs.pages.store.page.center.ui.fragment.StampShopFragment
import com.cyxbs.pages.store.page.center.ui.fragment.StampTaskFragment
import com.cyxbs.pages.store.page.center.viewmodel.StoreCenterViewModel
import com.cyxbs.pages.store.page.record.ui.activity.StampDetailActivity
import com.cyxbs.pages.store.utils.widget.SlideUpLayout
import com.cyxbs.pages.store.utils.widget.TextRollView
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ndhzs.slideshow.viewpager.transformer.ScaleInTransformer
import kotlinx.coroutines.launch

/**
 * 邮票中心界面
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2021/8/7
 */
@KClassProvider(clazz = Activity::class, name = STORE_ENTRY)
class StoreCenterActivity : BaseActivity() {
  
  private val mViewModel by viewModels<StoreCenterViewModel>()
  
  private lateinit var mTvStampBigNumber: TextRollView // 邮票中心首页最大的邮票数字
  private lateinit var mTvStampSideNumber: TextView // 邮票中心首页右上角的邮票数字
  private lateinit var mTvShopHint: TextView // 显示"你还有待领取的商品，请尽快领取"
  private lateinit var mTabLayout: TabLayout // 邮票中心首页显示邮票小店和邮票任务的 TabLayout
  private lateinit var mViewPager2: ViewPager2 // 邮票中心首页下方管理邮票小店和邮票任务的 VP2
  private lateinit var mRefreshLayout: SwipeRefreshLayout // 邮票中心首页下滑刷新控件
  private lateinit var mSlideUpLayout: SlideUpLayout // 邮票中心首页掌控下方板块上下移动的自定义控件

  private var enterTime: Long = 0

  override fun onResume() {
    super.onResume()
    enterTime = System.currentTimeMillis()
    appCoroutineScope.launch {
      TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_YPZX_HOMEPAGE)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    val exitTime = System.currentTimeMillis()
    val duration = exitTime - enterTime
    appCoroutineScope.launch {
      TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_YPZX_HOMEPAGE,duration,enterTime)
    }
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.store_activity_store_center)
    initView() // 初始化 View
    initViewPager2() // 设置 VP2(底部邮票小店与邮票任务界面)
    initTabLayout() // 设置 TabLayout 与 VP2 的联动
    initRefreshLayout() // 设置刷新
    initSlideUpLayoutWithLeftTopStamp() // 设置向上滑时与右上角邮票小图标的联合效果
    initJump() // 一些简单不传参的跳转写这里
    initObserve()
  }
  
  private fun initView() {
    mTvShopHint = findViewById(R.id.store_tv_stamp_center_hint)
    mTvStampBigNumber = findViewById(R.id.store_tv_stamp_number)
    mTvStampSideNumber = findViewById(R.id.store_tv_stamp_number_left_top)
    mViewPager2 = findViewById(R.id.store_vp_stamp_center)
    mRefreshLayout = findViewById(R.id.store_refreshLayout_stamp_center)
    mSlideUpLayout = findViewById(R.id.store_slideUpLayout_stamp_center)
  }
  
  private fun initViewPager2() {
    mViewPager2.adapter = FragmentVpAdapter(this)
      .add { StampShopFragment() }
      .add { StampTaskFragment() }
    mViewPager2.setPageTransformer(ScaleInTransformer())
    mViewPager2.offscreenPageLimit = 1
    mViewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageScrolled(
        position: Int,
        positionOffset: Float,
        positionOffsetPixels: Int
      ) {
        if (position + positionOffset >= 0.97F) {
          // 通知 StampTaskFragment 加载 RecyclerView, 这么做的原因我写在了 ViewModel 的这个回调上
          mViewModel.loadStampTaskRv()
        }
      }
    })
  }
  
  // 设置 TabLayout
  private fun initTabLayout() {
    mTabLayout = findViewById(R.id.store_tl_stamp_center)
    val tabs = arrayOf(
      getString(R.string.store_stamp_center_small_shop),
      getString(R.string.store_stamp_center_stamp_task)
    )
    TabLayoutMediator(
      mTabLayout, mViewPager2
    ) { tab, position -> tab.text = tabs[position] }.attach()
    
    // 以下代码是设置邮票任务的小圆点
    if (mViewModel.isShowTabLayoutBadge) {
      val tab = mTabLayout.getTabAt(1)
      if (tab != null) {
        val badge = tab.orCreateBadge
        badge.backgroundColor = R.color.store_stamp_center_tabLayout_tab_badge.color
        try {
          /*
          * 视觉说这个小圆点大了, 官方没提供方法修改, 只好靠反射拿了 :)
          * 官方中 badgeRadius 是 final 常量, 但反射却能修改, 原因在于它在构造器中被初始化, 不会被内联优化, 所以是可以改的
          * */
          val field = badge.javaClass.getDeclaredField("badgeRadius")
          field.isAccessible = true
          field.set(badge, 3.5F.dp2pxF)
        } catch (_: Exception) {
        }
        
        // 滑到邮票任务页面时就取消小圆点
        mViewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
          override fun onPageSelected(position: Int) {
            if (position == 1) {
              tab.removeBadge()
              mViewModel.isShowTabLayoutBadge = false
              mViewPager2.unregisterOnPageChangeCallback(this)
            }
          }
        })
      }
    }
  }
  
  private fun initRefreshLayout() {
    /*
    * 官方刷新控件不能修改偏移的误差值, 在左右滑动时与 ViewPager2 出现滑动冲突问题
    * 修改 mTouchSlop 可以修改允许的滑动偏移值, 原因可以看 SwipeRefreshLayout 的 1081 行
    * */
    /*
    * 其实这个是我放错位置的原因，完全没必要去用反射，正确写法是把 SwipeRefreshLayout 放到每个 Fragment 中，
    * 但懒得改了，就这样吧
    * - 2022/9/17 郭祥瑞
    * */
    try {
      val field = mRefreshLayout.javaClass.getDeclaredField("mTouchSlop")
      field.isAccessible = true
      field.set(mRefreshLayout, 220)
    } catch (_: Exception) {
    }
    
    // 下面这个 setOnChildScrollUpCallback() 返回 false 就代表刷新控件可以拦截滑动
    mRefreshLayout.setOnChildScrollUpCallback { _, _ -> !mSlideUpLayout.isUnfold() }
    mRefreshLayout.setOnRefreshListener { mViewModel.refresh() }
    mRefreshLayout.isRefreshing = true // 默认开始加载时打开刷新动画
  }
  
  // 用于设置向上滑时与右上角邮票小图标的联合效果
  private fun initSlideUpLayoutWithLeftTopStamp() {
    mTvStampSideNumber.alpha = 0F // 初始时隐藏, 后面会还原
    val displayMetrics = resources.displayMetrics
    val windowWidth = displayMetrics.widthPixels // 获取屏幕总宽度
    mSlideUpLayout.setMoveListener {
      if (mTvStampSideNumber.alpha == 0F) {
        mTvStampSideNumber.alpha = 1F
      }
      mTvStampSideNumber.translationX = it * (windowWidth - mTvStampSideNumber.left)
    }
  }
  
  // 一些简单不传参的跳转写这里
  private fun initJump() {
    val btnBack: ImageButton = findViewById(R.id.store_iv_toolbar_no_line_arrow_left)
    btnBack.setOnSingleClickListener { finish() /*左上角返回键*/ }
    
    val ivDetail: ImageView = findViewById(R.id.store_iv_stamp_center_stamp_bg)
    ivDetail.setOnSingleClickListener {
      startActivity(Intent(this, StampDetailActivity::class.java)) /*跳到邮票明细界面*/
    }
  }
  
  // 对于 ViewModel 数据的观察
  @SuppressLint("SetTextI18n")
  private fun initObserve() {
    var isFirstLoad = true // 是否是第一次进入界面
    mViewModel.stampCenterData.observe {
      val text = it.userAmount.toString()
      if (!mTvStampBigNumber.hasText()) { // 如果是第一次进入界面, 肯定没有文字
        mTvStampBigNumber.setTextOnlyAlpha(text) // 第一次进入界面就只使用隐现的动画
      } else {
        mSlideUpLayout.setUnfoldCallBackOnlyOnce {
          mTvStampBigNumber.setText(text, true) // 正上方的大的邮票显示
        }
      }
      
      mTvStampSideNumber.text = " $text" // 右上方小的邮票显示, 空一格是为了增加与左边图片的距离
      
      // 如果有商品要领就显示 "你还有待领取的商品，请尽快领取"
      if (it.unGotGood) {
        mTvShopHint.text = "你还有待领取的商品，请尽快领取"
      } else {
        mTvShopHint.text = ""
      }
    }
    
    // 对数据是否请求成功的观察（状态）
    mViewModel.refreshIsSuccessfulState.observe {
      mRefreshLayout.isRefreshing = false
      isFirstLoad = false
    }
  
    // 对数据是否请求成功的观察（事件）
    mViewModel.refreshIsSuccessfulEvent.collectLaunch {
      if (it) {
        // 处于刷新状态且不是第一次刷新
        if (mRefreshLayout.isRefreshing && !isFirstLoad) {
          toast("刷新成功")
        }
      } else {
        toast("获取邮票数据失败")
      }
    }
  }
  
  // 从邮货详细界面以及经过邮票任务界面跳转后的返回刷新数据
  override fun onRestart() {
    mViewModel.refresh()
    super.onRestart()
  }
}