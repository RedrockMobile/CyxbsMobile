package com.cyxbs.pages.noclass.page.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.widget.helper.show.CourseNowTimeHelper
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.launchByLifecycleScope
import com.cyxbs.pages.noclass.bean.NoClassSpareTime
import com.cyxbs.pages.noclass.page.viewmodel.other.CourseViewModel
import kotlinx.coroutines.flow.first
import java.util.*

/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.page.ui.fragment
 * @ClassName:      NoClassWeekFragment
 * @Author:         Yan
 * @CreateDate:     2022年09月06日 10:26:00
 * @UpdateRemark:   更新说明：
 * @Version:        1.0
 * @Description:
 */
class NoClassWeekFragment : NoClassPageFragment(){
  
  companion object {
    fun newInstance(week: Int,data:NoClassSpareTime): NoClassWeekFragment {
      return NoClassWeekFragment().apply {
        arguments = bundleOf(
          this::mNoClassSpareTime.name to data,
          this::mWeek.name to week
        )
      }
    }
  }
  
  private val mParentViewModel by activityViewModels<CourseViewModel>()

  private var mNoClassSpareTime by arguments<NoClassSpareTime>()
  // 第几周
  override val mWeek by arguments<Int>()
  
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initCreateAffair()
    setWeekNum()
    addLessons(mNoClassSpareTime)
    initObserve()
  }
  
  private fun initObserve(){
    mParentViewModel.noclassData.observe(viewLifecycleOwner){
      mNoClassSpareTime = it[mWeek]!!
      clearLesson()
      addLessons(mNoClassSpareTime)
    }
  }
  
  /**
   * 设置星期数
   */
  private fun setWeekNum() {
    val nowWeek = SchoolCalendar.getWeekOfTerm()
    if (nowWeek == null || nowWeek <= ICourseService.maxWeek + 1) {
      // 因为开学第一周的数据来自其他地方，存在此时没有被加载的情况，所以采用观察的形式
      // nowWeek 是可能返回负数的，这点需要额外注意一下
      launchByLifecycleScope {
        val firstDate = SchoolCalendar.observeFirstMonDay().first()
        val date = firstDate.plusWeeks(mWeek - 1)
        setMonth(date)
        val isInThisWeek = Date.now() in date .. date.plusDays(6)
        onIsInThisWeek(isInThisWeek)
      }
    } else {
      // 这里说明 nowWeek 大于 ICourseService.maxWeek + 1
      // 此时处于放假期间，考虑到 jwzx 会在开学前更新课表，所以这里不显示日期
      onIsInThisWeek(false)
    }
  }
  
  private val mCourseNowTimeHelper by lazy {
    CourseNowTimeHelper.attach(this)
  }
  
  /**
   * 今天是否在本周内的回调
   */
  private fun onIsInThisWeek(boolean: Boolean) {
    mCourseNowTimeHelper.setVisible(boolean)
    if (boolean) {
      val calendar = Calendar.getInstance()
      val weekNum = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
      /*
      * 星期天：1 -> 7
      * 星期一：2 -> 1
      * 星期二：3 -> 2
      * 星期三：4 -> 3
      * 星期四：5 -> 4
      * 星期五：6 -> 5
      * 星期六：7 -> 6
      *
      * 左边一栏是 Calendar.get(Calendar.DAY_OF_WEEK) 得到的数字，
      * 右边一栏是 weekNum 对应的数字
      * */
      showToday(weekNum)
    }
  }
  
}