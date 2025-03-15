package com.cyxbs.pages.course.widget.fragment.page.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.CallSuper
import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.course.widget.R
import com.cyxbs.pages.course.widget.fragment.page.expose.IWeekWrapper
import com.ndhzs.netlayout.view.NetLayout2

/**
 * ...
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/31 17:56
 */
@Suppress("LeakingThis")
abstract class WeekWrapperImpl : TodayImpl(), IWeekWrapper {
  
  override val nlWeek           by R.id.course_nl_week.view<NetLayout2>()
  override val tvMonth          by R.id.course_tv_month.view<TextView>()
  override val tvMonWeek        by R.id.course_tv_mon_week.view<TextView>()
  override val tvMonMonth       by R.id.course_tv_mon_month.view<TextView>()
  override val tvTueWeek        by R.id.course_tv_tue_week.view<TextView>()
  override val tvTueMonth       by R.id.course_tv_tue_month.view<TextView>()
  override val tvWedWeek        by R.id.course_tv_wed_week.view<TextView>()
  override val tvWedMonth       by R.id.course_tv_wed_month.view<TextView>()
  override val tvThuWeek        by R.id.course_tv_thu_week.view<TextView>()
  override val tvThuMonth       by R.id.course_tv_thu_month.view<TextView>()
  override val tvFriWeek        by R.id.course_tv_fri_week.view<TextView>()
  override val tvFriMonth       by R.id.course_tv_fri_month.view<TextView>()
  override val tvSatWeek        by R.id.course_tv_sat_week.view<TextView>()
  override val tvSatMonth       by R.id.course_tv_sat_month.view<TextView>()
  override val tvSunWeek        by R.id.course_tv_sun_week.view<TextView>()
  override val tvSunMonth       by R.id.course_tv_sun_month.view<TextView>()
  
  override fun forEachWeekView(block: (week: TextView, month: TextView) -> Unit) {
    block.invoke(tvMonWeek, tvMonMonth)
    block.invoke(tvTueWeek, tvTueMonth)
    block.invoke(tvWedWeek, tvWedMonth)
    block.invoke(tvThuWeek, tvThuMonth)
    block.invoke(tvFriWeek, tvFriMonth)
    block.invoke(tvSatWeek, tvSatMonth)
    block.invoke(tvSunWeek, tvSunMonth)
  }
  
  override fun getWeekWeekView(weekNum: Int, block: (week: TextView, month: TextView) -> Unit) {
    checkWeekNum(weekNum)
    when (weekNum) {
      1 -> block.invoke(tvMonWeek, tvMonMonth)
      2 -> block.invoke(tvTueWeek, tvTueMonth)
      3 -> block.invoke(tvWedWeek, tvWedMonth)
      4 -> block.invoke(tvThuWeek, tvThuMonth)
      5 -> block.invoke(tvFriWeek, tvFriMonth)
      6 -> block.invoke(tvSatWeek, tvSatMonth)
      7 -> block.invoke(tvSunWeek, tvSunMonth)
    }
  }
  
  @CallSuper
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    nlWeek.syncColumnWeight(course) // 同步列比重
  }
  
  @SuppressLint("SetTextI18n")
  override fun setMonth(monDay: Date) {
    tvMonth.text = "${monDay.monthNumber}月"
    var date = monDay
    forEachWeekView { _, month ->
      month.text = "${date.dayOfMonth}日"
      date = date.plusDays( 1) // 天数加 1
    }
  }
  
  override fun getWeekNumStartWidth(weekNum: Int): Int {
    checkWeekNum(weekNum)
    return course.getColumnsWidth(0, weekNum - 1)
  }
  
  override fun getWeekNumEndWidth(weekNum: Int): Int {
    checkWeekNum(weekNum)
    return course.getColumnsWidth(0, weekNum)
  }
  
  private fun checkWeekNum(weekNum: Int) {
    require(weekNum in 1 .. 7) { "weekNum = $weekNum，超出边界！" }
  }
}