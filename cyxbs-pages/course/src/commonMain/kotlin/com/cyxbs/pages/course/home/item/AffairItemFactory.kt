package com.cyxbs.pages.course.home.item

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.affair.api.IAffairService2
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
    affair: IAffairService2.Affair,
    whatTime: IAffairService2.AffairWhatTime,
    date: Date,
  ): AffairItemModel
}

interface AffairItemModel : CourseItemModel {
  val affair: IAffairService2.Affair
  val whatTime: IAffairService2.AffairWhatTime
  val date: Date
}