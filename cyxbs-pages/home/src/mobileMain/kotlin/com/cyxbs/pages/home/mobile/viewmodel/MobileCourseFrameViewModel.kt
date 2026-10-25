package com.cyxbs.pages.home.mobile.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.IMobileHomeCourseFrameFactory

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/30
 */
class MobileCourseFrameViewModel : BaseViewModel() {
  /** 主页课表 Frame，其数据作用域随当前 ViewModel 自动释放。 */
  val frame = IMobileHomeCourseFrameFactory::class.impl().create(this)
}
