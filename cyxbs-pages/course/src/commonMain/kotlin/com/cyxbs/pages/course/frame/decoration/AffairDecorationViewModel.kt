package com.cyxbs.pages.course.frame.decoration

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.frame.item.AffairItemFactory
import com.cyxbs.pages.course.frame.item.CourseAffairItem
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/18
 */
class AffairDecorationViewModel(
  val hierarchy: CourseItemHierarchy<CourseAffairItem>,
) : BaseViewModel(), CoursePageDecoration {

  init {
    IAffairService2::class.impl()
      .observeAffairGroupModel()
      .flatMapLatest { groupModel ->
        groupModel?.itemList ?: flowOf(emptyList())
      }.mapLatest { idModels ->
        hierarchy.reset(emptyList())
        supervisorScope {
          idModels.forEach { idModel ->
            idModel.whatTimeDate.value.forEach { (whatTimeModel, dateModels) ->
              dateModels.forEach { dateModel ->
                val whatTime = AffairItemWhatTime(isSemester = false, affairDateModel = dateModel)
                launch {
                  dateModel.enable.mergeFlow.collect {
                    if (it) hierarchy.add(whatTime) else hierarchy.remove(whatTime)
                  }
                }
              }
            }
            launch {
              idModel.addedDateModel.collect { dateModel ->
                val whatTime = AffairItemWhatTime(isSemester = false, affairDateModel = dateModel)
                launch {
                  dateModel.enable.mergeFlow.collect {
                    if (it) hierarchy.add(whatTime) else hierarchy.remove(whatTime)
                  }
                }
              }
            }
          }
        }
      }.launchIn(viewModelScope)
  }

  @Composable
  override fun CoursePageContent() {
    hierarchy.CoursePageItemListContent()
  }
}

private data class AffairItemWhatTime(
  val isSemester: Boolean,
  val affairDateModel: AffairDateModel,
) : ItemHierarchyWhatTime<CourseAffairItem>() {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = SchoolCalendar.getFirstMonDay().let {
        if (it == null) -1
        else if (isSemester) 0
        else it.daysUntil(affairDateModel.date.value) / 7 + 1
      },
      dayOfWeek = affairDateModel.date.value.dayOfWeek,
      beginTime = affairDateModel.whatTime.value.timePair.value.first,
      finalTime = affairDateModel.whatTime.value.timePair.value.second,
    )
  )

  override fun createItem(coroutineScope: CoroutineScope): CourseAffairItem {
    return AffairItemFactory.get().createAffairItemModel(
      whatTime = this,
      coroutineScope = coroutineScope,
      affairDateModel = affairDateModel,
    )
  }

  override fun compareTo(other: ItemHierarchyWhatTime<CourseAffairItem>): Int {
    return 0.compareBy(other) {
      -it.now.value.page // page 越小越在上
    }.compareBy(other) {
      -it.now.value.dayOfWeek.ordinal // dayOfWeek 越小越在上
    }.compareBy(other) {
      it.now.value.beginTime.value // beginTime 越大越在上
    }.compareBy(other) {
      it.now.value.finalTime.value // finalTime 越大越在上
    }
  }

  private inline fun Int.compareBy(
    other: ItemHierarchyWhatTime<CourseAffairItem>,
    compare: (ItemHierarchyWhatTime<CourseAffairItem>) -> Int
  ): Int {
    if (this != 0) return this
    val a = compare.invoke(this@AffairItemWhatTime)
    val b = compare.invoke(other)
    return a - b
  }
}
