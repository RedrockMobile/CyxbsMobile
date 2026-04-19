package com.cyxbs.pages.course.view.decoration.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.impl.CourseAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseAffairItemFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * 自己的事务展示
 *
 * @author 985892345
 * @date 2025/10/18
 */
class AffairPageDecoration(
  val courseFrame: AbstractCourseFrame,
  // 根据不同平台对 item 进行定制化操作
  val platformItemFactory: PlatformCourseAffairItemFactory,
) : CoursePageDecoration<CourseAffairItem>() {

  private suspend fun observeAffair() {
    IAffairService2::class.impl()
      .observeAffairGroupModel()
      .flatMapLatest { groupModel ->
        groupModel?.itemList ?: flowOf(emptyList())
      }.mapLatest { idModels ->
        itemHierarchy.reset(emptyList())
        supervisorScope {
          idModels.forEach { idModel ->
            val whatTimeByDateModel = hashMapOf<AffairDateModel, AffairItemWhatTime>()
            idModel.whatTimeDate.value.forEach { (whatTimeModel, dateModels) ->
              dateModels.forEach { dateModel ->
                val whatTime = AffairItemWhatTime(
                  isSemester = false,
                  affairDateModel = dateModel,
                  courseFrame = courseFrame,
                  platformItemFactory = platformItemFactory
                )
                whatTimeByDateModel[dateModel] = whatTime
                itemHierarchy.add(whatTime)
              }
            }
            launch {
              idModel.addedDateModel.collect { dateModel ->
                val whatTime = AffairItemWhatTime(
                  isSemester = false,
                  affairDateModel = dateModel,
                  courseFrame = courseFrame,
                  platformItemFactory = platformItemFactory
                )
                whatTimeByDateModel[dateModel] = whatTime
                itemHierarchy.add(whatTime)
              }
            }
            launch {
              idModel.removedDateModel.collect { dateModel ->
                whatTimeByDateModel[dateModel]?.let {
                  itemHierarchy.remove(it)
                }
              }
            }
          }
        }
      }.collect()
  }

  @Composable
  override fun CoursePageContent() {
    super.CoursePageContent()
    LaunchedEffect(Unit) {
      observeAffair()
    }
  }

  // 查找 CourseItemState
  suspend fun findCourseItemState(dateModelEditor: AffairDateModelEditor): CourseItemState? {
    return itemHierarchy.observe(
      page = courseFrame.getPage(dateModelEditor.date) ?: return null,
      dayOfWeek = dateModelEditor.date.dayOfWeek,
    ).mapNotNull { list ->
      list.firstOrNull {
        (it.item as CourseAffairItem).affairDateModel === dateModelEditor.dateModel
      }
    }.firstOrNull()
  }
}

private data class AffairItemWhatTime(
  val isSemester: Boolean,
  val affairDateModel: AffairDateModel,
  val courseFrame: AbstractCourseFrame,
  val platformItemFactory: PlatformCourseAffairItemFactory, // 创建平台配置 factory 的 key
) : ItemHierarchyWhatTime<CourseAffairItem>() {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = courseFrame.beginDate.value.let {
        if (it == null) -1
        else if (isSemester) 0
        else courseFrame.getPage(affairDateModel.date.value) ?: -1
      },
      dayOfWeek = affairDateModel.date.value.dayOfWeek,
      beginTime = affairDateModel.whatTime.value.timePair.value.first,
      finalTime = affairDateModel.whatTime.value.timePair.value.second,
    )
  )

  override fun createItem(coroutineScope: CoroutineScope): CourseAffairItem {
    return CourseAffairItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      courseFrame = courseFrame,
      affairDateModel = affairDateModel,
      platformItemFactory = platformItemFactory,
    )
  }
}
