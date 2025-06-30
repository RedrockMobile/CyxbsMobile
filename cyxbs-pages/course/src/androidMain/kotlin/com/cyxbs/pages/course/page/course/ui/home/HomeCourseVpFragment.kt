package com.cyxbs.pages.course.page.course.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.cyxbs.components.config.config.SchoolCalendar
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.pages.course.page.course.ui.home.base.HomeCourseVpLinkFragment
import com.cyxbs.pages.course.page.course.ui.home.viewmodel.HomeCourseViewModel
import com.cyxbs.pages.course.page.find.ui.find.activity.FindLessonActivity
import com.cyxbs.pages.course.widget.fragment.page.CoursePageFragment
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.launch

/**
 * ...
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/20 16:53
 */
class HomeCourseVpFragment : HomeCourseVpLinkFragment() {
  
  private val mViewModel by viewModels<HomeCourseViewModel>()

  private var enterTime: Long = 0

  override fun onResume() {
    super.onResume()
    enterTime = System.currentTimeMillis()
    appCoroutineScope.launch {
      TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_KBCX_HOMEPAGE)
    }
  }

  override fun onPause() {
    super.onPause()
    val duration = System.currentTimeMillis() - enterTime
    appCoroutineScope.launch {
      TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_KBCX,duration,enterTime)
    }
  }
  
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initTouch()
    initViewPager()
    initObserve()
  }
  
  override fun createFragment(position: Int): CoursePageFragment {
    return if (position == 0) HomeSemesterFragment() else HomeWeekFragment.newInstance(position)
  }
  
  private fun initTouch() {
    mIvLink.setOnSingleClickListener {
      val isShowingDouble = isShowingDoubleLesson() ?: return@setOnSingleClickListener
      if (isShowingDouble) {
        showSingleLink()
        mViewModel.changeLinkStuVisible(false)
      } else {
        showDoubleLink()
        mViewModel.changeLinkStuVisible(true)
      }
    }
    
    mIvLink.setOnLongClickListener {
      val linkNum = mViewModel.linkStu.value.linkNum
      return@setOnLongClickListener if (linkNum.isNotBlank()) {
        FindLessonActivity.startByStuNum(it.context, linkNum)
        true
      } else false
    }
  
    // 点击第几周按钮可以刷新课表数据，自己加的功能
    mTvWhichWeek.setOnLongClickListener {
      toast("重新加载课表数据")
      mViewModel.refreshDataObserve()
      true
    }
  }
  
  private fun initViewPager() {
    /**
     * 观察第几周，因为如果是初次进入应用，会因为得不到周数而不主动翻页，所以只能观察该数据
     * 但这是因为主页课表比较特殊而采取观察，其他界面可以直接使用 [mNowWeek] 变量
     */
    SchoolCalendar.observeWeekOfTerm()
      .firstElement()
      .observeOn(AndroidSchedulers.mainThread())
      .safeSubscribeBy {
        // 初次加载时移到对应的周数
        // 这里课表的翻页不建议带有动画，因为数据过多会较卡
        mViewPager.setCurrentItem(if (it >= mVpAdapter.itemCount) 0 else it, false)
      }
    
    mViewPager.registerOnPageChangeCallback(
      object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
          mViewModel.currentItem.value = position
        }
      }
    )
  }
  
  private fun initObserve() {
    mViewModel.linkStu.collectLaunch {
      if (it.isNull()) {
        mIvLink.gone()
      } else {
        if (it.isShowLink) {
          showDoubleLink()
        } else {
          showSingleLink()
        }
      }
    }
    
    mViewModel.courseService.headerAlphaState.observe {
      mHeader.alpha = it
    }
    mViewModel.courseService.courseVpAlphaState.observe {
      mViewPager.alpha = it
    }
  }
}