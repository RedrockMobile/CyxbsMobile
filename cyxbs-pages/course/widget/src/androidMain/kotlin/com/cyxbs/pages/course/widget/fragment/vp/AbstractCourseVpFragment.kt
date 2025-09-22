package com.cyxbs.pages.course.widget.fragment.vp

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.pages.course.widget.fragment.vp.expose.ICourseVp
import com.cyxbs.pages.course.widget.widget.NestedDispatchLayout
import com.cyxbs.components.utils.extensions.lazyUnlock

/**
 * 封装了 Vp2 的课表 Fragment
 *
 * ```
 * 我推荐的课表框架为：
 *                                               *CourseVpFragment
 *                                                      ↓
 *            +---------------------------+---------------------------+---------------------------+
 *            ↓                           ↓                           ↓                           ↓
 * CourseSemesterFragment         CourseWeekFragment          CourseWeekFragment         CourseWeekFragment
 *
 * ```
 * - `*CourseVpFragment` 指 [AbstractCourseVpFragment] 或者 [AbstractHeaderCourseVpFragment] 的子类
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/19 21:11
 */
abstract class AbstractCourseVpFragment : BaseFragment(), ICourseVp {
  
  companion object {
    /**
     * 课表能显示的最大周数
     *
     * 与 [ICourseService.maxWeek] 值相同
     */
    val maxWeek = ICourseService.maxWeek
  }
  
  override val mVpAdapter: FragmentStateAdapter by lazyUnlock { CourseAdapter(this) }
  
  override val mNowWeek: Int
    get() = SchoolCalendar.getWeekOfTerm() ?: 0
  
  override val mPageCount: Int
    get() = ICourseService.maxWeek + 1 // 最大周数 + 整学期界面
  
  @CallSuper
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    mViewPager.adapter = mVpAdapter
    mViewPager.offscreenPageLimit = 1 // 预加载一页，减少页面刚加载时就滑动的卡顿
    /**
     * 取消 VP2 中 RV 的嵌套滑动，为了解决 [NestedDispatchLayout] 头注释中描述的 bug
     */
    mViewPager.getChildAt(0).isNestedScrollingEnabled = false
  }
  
  private inner class CourseAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = mPageCount
    override fun createFragment(position: Int): Fragment = this@AbstractCourseVpFragment.createFragment(position)
  }
}