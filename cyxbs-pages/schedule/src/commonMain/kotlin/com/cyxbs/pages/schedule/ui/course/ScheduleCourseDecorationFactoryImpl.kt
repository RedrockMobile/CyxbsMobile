package com.cyxbs.pages.schedule.ui.course

import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.schedule.api.IScheduleCourseDecorationFactory
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * [IScheduleCourseDecorationFactory] 的实现：创建 [SchedulePageDecoration]。
 *
 * 经 KtProvider 暴露，让 course 模块只依赖 schedule:api 即可在课表注册日程渲染（与 affair 并存）。
 */
@ImplProvider(clazz = IScheduleCourseDecorationFactory::class)
object ScheduleCourseDecorationFactoryImpl : IScheduleCourseDecorationFactory {
  override fun create(courseFrame: AbstractCourseFrame): CoursePageDecoration<*> =
    SchedulePageDecoration(courseFrame = courseFrame)
}
