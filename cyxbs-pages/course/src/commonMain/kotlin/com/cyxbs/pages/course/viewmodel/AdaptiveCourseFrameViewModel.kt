package com.cyxbs.pages.course.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.pages.course.frame.AdaptiveCourseFrame

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AdaptiveCourseFrameViewModel(
  val stuNum: String
) : BaseViewModel() {

  // 课表主页框架
  val frame = AdaptiveCourseFrame(stuNum)
}