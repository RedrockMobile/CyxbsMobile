package com.cyxbs.pages.course.widget.fragment.page.expose

import android.widget.TextView
import com.cyxbs.components.config.time.Date
import com.ndhzs.netlayout.view.NetLayout2

/**
 * ...
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/31 17:51
 */
interface IWeekWrapper {
  
  /**
   * 周数列表
   */
  val nlWeek: NetLayout2
  val tvMonth: TextView
  val tvMonWeek: TextView
  val tvMonMonth: TextView
  val tvTueWeek: TextView
  val tvTueMonth: TextView
  val tvWedWeek: TextView
  val tvWedMonth: TextView
  val tvThuWeek: TextView
  val tvThuMonth: TextView
  val tvFriWeek: TextView
  val tvFriMonth: TextView
  val tvSatWeek: TextView
  val tvSatMonth: TextView
  val tvSunWeek: TextView
  val tvSunMonth: TextView
  
  /**
   * 遍历星期数
   */
  fun forEachWeekView(block: (week: TextView, month: TextView) -> Unit)
  
  /**
   * 得到 [weekNum] 对应的 View
   * @param weekNum 星期数，星期一为 1
   */
  fun getWeekWeekView(weekNum: Int, block: (week: TextView, month: TextView) -> Unit)
  
  /**
   * 设置月份
   * @param monDay 当前页星期一的 Calendar
   */
  fun setMonth(monDay: Date)
  
  /**
   * 得到当前星期数开始的距离(距离课表控件左边缘的距离)
   * @param weekNum 星期数，星期一为 1
   */
  fun getWeekNumStartWidth(weekNum: Int): Int
  
  /**
   * 得到当前星期数结束的距离(距离课表控件左边缘的距离)
   * @param weekNum 星期数，星期一为 1
   */
  fun getWeekNumEndWidth(weekNum: Int): Int
}