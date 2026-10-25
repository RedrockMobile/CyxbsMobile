package com.cyxbs.pages.course.service

/**
 * iOS 端课表平台桥。
 *
 * 用于在 [com.cyxbs.pages.course.model.LessonRepository.requestLesson] 拉到新数据并写入
 * KMP 缓存之后，再把这份数据同步给 iOS 原生侧（目前主要是写一份 App Group 共享缓存供
 * CyxbsWidgetExtension 课表小组件读取）。
 *
 * Android 端无对应实现；commonMain 通过 [com.cyxbs.components.config.service.implOrNull]
 * 取实例，未实现时直接跳过。
 */
interface CourseIosPlatform {
  /**
   * 课表数据更新成功（请求成功并写入 KMP 缓存之后）回调。
   *
   * @param stuNum 学号
   * @param nowWeek 当前周
   * @param stuLessonBeanJson 后端返回的整个 `StuLessonBean` 的 JSON 字符串，
   *  Swift 端可按需解析为旧 `ScheduleModel` / `CurriculumModel` 等用于 Widget 渲染
   */
  fun onLessonUpdated(stuNum: String, nowWeek: Int, stuLessonBeanJson: String)
}
