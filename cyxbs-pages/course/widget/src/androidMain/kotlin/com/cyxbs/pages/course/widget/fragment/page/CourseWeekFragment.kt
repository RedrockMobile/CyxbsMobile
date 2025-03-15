package com.cyxbs.pages.course.widget.fragment.page

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.widget.helper.show.CourseNowTimeHelper
import com.cyxbs.components.utils.extensions.lazyUnlock
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.launch
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 每一周的页面模板
 *
 * 因为 [CourseWeekFragment] 和 [CourseSemesterFragment] 高度相似，有很多初始化方法都是一样的，
 * 为了减少书写，你可以参考主页课表的写法，使用一个帮助类来整合这两个 Fragment 需要的方法
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/12 14:27
 */
abstract class CourseWeekFragment : CoursePageFragment() {
  
  /**
   * 建议写法：
   * ```
   * companion object {
   *   fun newInstance(week: Int): XXXWeekFragment {
   *     return XXXWeekFragment().apply {
   *       arguments = bundleOf(
   *         this::mWeek.name to week
   *       )
   *     }
   *   }
   * }
   *
   * override val mWeek by arguments<Int>()
   * ```
   */
  protected abstract val mWeek: Int
  
  @CallSuper
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initWeekNum()
  }
  
  /**
   * 设置星期数
   */
  protected open fun initWeekNum() {
    val nowWeek = SchoolCalendar.getWeekOfTerm()
    if (nowWeek == null || nowWeek <= ICourseService.maxWeek + 1) {
      // 因为开学第一周的数据来自其他地方，存在此时没有被加载的情况，所以采用观察的形式
      // nowWeek 是可能返回负数的，这点需要额外注意一下
      launch {
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
  
  protected open val mCourseNowTimeHelper by lazyUnlock {
    CourseNowTimeHelper.attach(this)
  }
  
  /**
   * 今天是否在本周内的回调
   * @param boolean 今天是否处于本周内
   */
  protected open fun onIsInThisWeek(boolean: Boolean) {
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