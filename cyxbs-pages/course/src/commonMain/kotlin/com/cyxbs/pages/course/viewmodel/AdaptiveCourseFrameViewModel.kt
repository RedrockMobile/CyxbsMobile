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

  /**
   * 课表主页框架；学号在框架内部是可变状态，Frame 及其数据作用域随当前 ViewModel 自动释放。
   */
  val frame = AdaptiveCourseFrame.create(
    owner = this,
    initialStuNum = initialStuNum,
  )
}
