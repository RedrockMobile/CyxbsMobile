package com.cyxbs.pages.course.home.item

import androidx.compose.runtime.Stable
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.home.item.impl.DefaultAffairItemModel
import com.cyxbs.pages.course.view.item.CourseItemModel

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
    page: Int,
    affairDateModel: AffairDateModel,
  ): AffairItemModel

  companion object {
    fun get(): AffairItemFactory {
      return AffairItemFactory::class.implOrNull() ?: DefaultAffairItemModel
    }
  }
}

@Stable
interface AffairItemModel : CourseItemModel {
  val affairDateModel: AffairDateModel
}