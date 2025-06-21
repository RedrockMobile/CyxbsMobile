package com.cyxbs.pages.course.home.data

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.home.item.AffairItemFactory
import com.cyxbs.pages.course.view.data.CourseDataProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 *
 *
 * @author 985892345
 * @date 2025/3/10
 */
object HomeAffairDataProvider : CourseDataProvider() {

  private val itemFactory = AffairItemFactory::class.impl()

  init {
    IAffairService2::class.impl()
      .observeAffair(needRequest = true)
      .combine(SchoolCalendar.observeFirstMonDay()) { affairs, firstDate ->
        // Compose 新课表事务魔改了 server 下方的事务数据结构含义，week 字段不再作为周数，而是日期
        // 所以需要获取开学第一天的日期才能计算出周数
        affairs to firstDate
      }.onEach {
        resetData(it.first, it.second)
      }.launchIn(appCoroutineScope)
  }

  private fun resetData(data: List<IAffairService2.Affair>?, firstDate: Date) {
    // 挂起等待开学第一天日期
    clear()
    data ?: return
    data.forEach { affair ->
      affair.whatTime.forEach { time ->
        time.date.forEach { date ->
          add(
            itemFactory.createAffairItemModel(
              page = 0,
              affair = affair,
              whatTime = time,
              date = date,
            )
          )
          add(
            itemFactory.createAffairItemModel(
              page = firstDate.daysUntil(date) / 7 + 1,
              affair = affair,
              whatTime = time,
              date = date,
            )
          )
        }
      }
    }
  }
}