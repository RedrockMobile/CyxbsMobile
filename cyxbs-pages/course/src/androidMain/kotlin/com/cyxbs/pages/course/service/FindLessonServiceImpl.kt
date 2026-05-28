package com.cyxbs.pages.course.service

import android.content.Context
import com.cyxbs.pages.course.api.CourseNavArgument
import com.cyxbs.pages.course.api.FindCourseNavArgument
import com.cyxbs.pages.course.api.IFindLessonService

/**
 * 旧 [IFindLessonService] 的实现：已下线 KSP `@ImplProvider` 注册，
 * 实现内部统一改走 Navigation3 路由（[FindCourseNavArgument] / [CourseNavArgument]）。
 *
 * grep 显示外部无 [IFindLessonService] 引用，保留此类仅作为历史代码参考。
 *
 * @author 985892345
 * @date 2022/9/22 15:54
 */
object FindLessonServiceImpl : IFindLessonService {

  override fun startActivity(context: Context) {
    FindCourseNavArgument().navigate()
  }

  override fun startActivityByStuNum(context: Context, stuNum: String) {
    FindCourseNavArgument(directStuNum = stuNum).navigate()
  }

  override fun startActivityByStuName(context: Context, stuName: String) {
    FindCourseNavArgument(initialQuery = stuName).navigate()
  }
}
