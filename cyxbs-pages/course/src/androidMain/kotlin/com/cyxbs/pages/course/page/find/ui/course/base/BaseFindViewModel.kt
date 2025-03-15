package com.cyxbs.pages.course.page.find.ui.course.base

import androidx.lifecycle.LiveData
import com.cyxbs.pages.course.page.course.data.LessonData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.time.SchoolCalendar

/**
 * ...
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/12 13:33
 */
abstract class BaseFindViewModel<D : LessonData> : BaseViewModel() {
  
  abstract val findLessonData: LiveData<Map<Int, List<D>>>
  
  val nowWeek: Int  // 当前周数
    get() = SchoolCalendar.getWeekOfTerm() ?: 0
}