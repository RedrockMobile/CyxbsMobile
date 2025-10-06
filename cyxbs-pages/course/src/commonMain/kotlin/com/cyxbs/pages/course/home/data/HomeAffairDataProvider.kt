package com.cyxbs.pages.course.home.data

import androidx.compose.ui.util.fastForEach
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.home.item.AffairItemFactory
import com.cyxbs.pages.course.home.item.CourseAffairItem
import com.cyxbs.pages.course.view.data.CourseDataProvider
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 *
 *
 * @author 985892345
 * @date 2025/3/10
 */
object HomeAffairDataProvider : CourseDataProvider<CourseAffairItem>() {

  private val itemFactory = AffairItemFactory.get()

  private val dateModelSet = mutableSetOf<AffairDateModel>()
  private val dateModelRemovedList = mutableListOf<AffairDateModel>()

  init {
    SchoolCalendar.observeFirstMonDay()
      .onEach {
        clear() // 开学日期发生改变，清空数据
      }
      .flatMapLatest {
        IAffairService2::class.impl()
          .observeAffairModelStateFlow()
      }
      .onEach {
        if (it == null) {
          clear() // 事务数据为空，说明退出登陆了，清空数据
        }
      }
      .filterNotNull()
      .flatMapLatest {
        it.itemList
      }.flatMapLatest { idModels ->
        idModels.map { idModel ->
          idModel.whatTimeDate.map {
            it.values
          }.map {
            it.flatten()
          }
        }.let { listFlow ->
          combine(listFlow) {
            it.toList().flatten()
          }
        }
      }
      .onEach {
//        resetData(it)
      }
      .launchIn(appCoroutineScope)
  }

//  private fun resetData(dateModels: List<AffairDateModel>) {
//    clear()
//    if (dateModels.isEmpty()) return
//    dateModels.fastForEach {
//      val page = firstDate.daysUntil(it.date.value) / 7 + 1
//      if (page >= 1) {
//        add(itemFactory.createAffairItemModel(0, it))
//        add(itemFactory.createAffairItemModel(page, it))
//      }
//    }
//  }
}