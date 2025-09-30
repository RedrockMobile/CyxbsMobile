package com.cyxbs.pages.home.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.IAdaptiveHomeCourseFrame

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AdaptiveCourseFrameViewModel : BaseViewModel() {

  // 由 course 模块实现
  val frame = IAdaptiveHomeCourseFrame::class.impl()
}