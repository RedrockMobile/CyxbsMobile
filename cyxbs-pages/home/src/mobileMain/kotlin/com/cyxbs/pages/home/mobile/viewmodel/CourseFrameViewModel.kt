package com.cyxbs.pages.home.mobile.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.IMobileHomeCourseFrame

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/30
 */
class CourseFrameViewModel : BaseViewModel() {
  // 由 course 模块实现
  val frame = IMobileHomeCourseFrame::class.impl()
}