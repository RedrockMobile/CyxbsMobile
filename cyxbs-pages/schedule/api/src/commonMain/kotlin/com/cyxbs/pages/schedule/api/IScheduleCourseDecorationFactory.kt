package com.cyxbs.pages.schedule.api

import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration

/**
 * 课表「日程装饰物」工厂，给 course 模块在不直接依赖 schedule 实现模块的前提下注册日程渲染。
 *
 * schedule 实现模块用 `@ImplProvider` 提供实现；course 的课表框架通过
 * `IScheduleCourseDecorationFactory::class.impl().create(frame)` 拿到装饰物，
 * 追加进 `CoursePageDecorationManager`，与 affair 的 `AffairPageDecoration` 并存。
 */
interface IScheduleCourseDecorationFactory {

  /** 创建绑定到 [courseFrame] 的日程装饰物（每个课表框架一个）。 */
  fun create(courseFrame: AbstractCourseFrame): CoursePageDecoration<*>
}
