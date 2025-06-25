package com.cyxbs.pages.course.home.data

import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.home.item.AffairItemFactory
import com.cyxbs.pages.course.view.data.CourseDataProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
      .observeAffairModelStateFlow()
      .flatMapLatest {
        it?.items ?: flowOf(emptyList())
      }.flatMapLatest { itemModels ->
        combine(itemModels.map { it.whatTimeList }) { array ->
          array.map { map ->
            map.map { entry ->
              entry.key
            }
          }.flatten()
        }
      }.flatMapLatest { whatTimeModels ->
        combine(whatTimeModels.map { it.dateList }) { array ->
          array.map { map ->
            map.map { entry ->
              entry.key
            }
          }.flatten()
        }
      }.combine(SchoolCalendar.observeFirstMonDay()) { affairs, firstDate ->
        // Compose 新课表事务魔改了 server 下方的事务数据结构含义，week 字段不再作为周数，而是日期
        // 所以需要获取开学第一天的日期才能计算出周数
        affairs to firstDate
      }.onEach {
        resetData(it.first, it.second)
      }.launchIn(appCoroutineScope)
  }

  private fun resetData(dateModels: List<AffairDateModel>, firstDate: Date) {
    clear()
    if (dateModels.isEmpty()) return
    dateModels.fastForEach {
      val page = firstDate.daysUntil(it.date.value) / 7 + 1
      if (page >= 1) {
        add(itemFactory.createAffairItemModel(0, it))
        add(itemFactory.createAffairItemModel(page, it))
      }
    }
  }
}