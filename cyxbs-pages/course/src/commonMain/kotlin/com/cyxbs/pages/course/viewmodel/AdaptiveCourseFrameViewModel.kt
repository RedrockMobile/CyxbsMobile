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
  initialStuNum: String
) : BaseViewModel() {

  // 课表主页框架；stuNum 在框架内部是可变 State，外部通过 frame.updateStuNum 切换
  val frame = AdaptiveCourseFrame(initialStuNum = initialStuNum)
}