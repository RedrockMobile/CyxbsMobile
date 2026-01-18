package com.cyxbs.pages.course.frame.item

import androidx.compose.runtime.Stable
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.frame.item.impl.DefaultAffairItem
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import kotlinx.coroutines.CoroutineScope

/**
 * LinkLessonItem 工厂，由具体平台实现
 * - 如果由 commonMain 去实现，则点击事件等无法具体平台定制化
 * - mobileMain 实现了移动端的 MobileAffairItem
 *
 * @author 985892345
 * @date 2025/3/25
 */
interface AffairItemFactory {

  fun createAffairItemModel(
    whatTime: ItemHierarchyWhatTime<CourseAffairItem>,
    coroutineScope: CoroutineScope,
    affairDateModel: AffairDateModel,
  ): CourseAffairItem

  companion object {
    fun get(): AffairItemFactory {
      return AffairItemFactory::class.implOrNull() ?: DefaultAffairItem
    }
  }
}

@Stable
abstract class CourseAffairItem(
  whatTime: ItemHierarchyWhatTime<CourseAffairItem>,
  coroutineScope: CoroutineScope,
  val affairDateModel: AffairDateModel,
) : CourseItem(whatTime, coroutineScope) {
}