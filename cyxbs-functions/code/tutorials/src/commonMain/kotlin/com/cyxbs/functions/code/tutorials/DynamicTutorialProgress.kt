package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLessonSummary

/** 保留教程模块原有公开名称；实际序列化协议定义位于 js-bridge，供 npm 包直接实现进度迁移。 */
typealias DynamicTutorialCompletedStep =
  com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletedStep

/** 保留上层编辑器名称，实际数据所有权属于动态教程 npm 协议。 */
typealias DynamicTutorialLessonWorkspace =
  com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLessonWorkspace

/** 保留上层编辑器名称，读写动作必须通过教程会话转交 npm 包。 */
typealias DynamicTutorialProgress =
  com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialProgress

/** 课程路径展示使用的轻量进度，不需要再次下载完整课程正文。 */
data class DynamicTutorialCourseProgressSummary(
  val completedLessonCount: Int,
  val totalLessonCount: Int,
  val nextLesson: DynamicTutorialLessonSummary?,
) {
  val isKnown: Boolean
    get() = totalLessonCount > 0
}

/**
 * 依据 Manifest 中的稳定步骤 ID 汇总课时进度。
 *
 * 已完成课程优先视为所有课时完成，使教程包增加新步骤后不会把用户已完成的课程倒退为进行中；
 * 未携带课时目录的旧教程包返回未知进度，由 UI 保留原有状态文本。
 */
fun DynamicTutorialCourseSummary.resolveProgress(
  progress: DynamicTutorialProgress?,
): DynamicTutorialCourseProgressSummary {
  if (lessons.isEmpty()) {
    return DynamicTutorialCourseProgressSummary(0, 0, null)
  }
  if (progress?.isCourseCompleted == true) {
    return DynamicTutorialCourseProgressSummary(lessons.size, lessons.size, null)
  }
  val completedSteps = progress?.completedSteps.orEmpty().toSet()
  val completedLessons = lessons.filter { lesson ->
    lesson.stepIds.all { stepId ->
      DynamicTutorialCompletedStep(lesson.lessonId, stepId) in completedSteps
    }
  }
  return DynamicTutorialCourseProgressSummary(
    completedLessonCount = completedLessons.size,
    totalLessonCount = lessons.size,
    nextLesson = lessons.firstOrNull { it !in completedLessons },
  )
}

/**
 * 从按更新时间排列的记录中选择续学课程。
 *
 * 优先选择最近一门未完成课程；只有全部完成时才返回最近完成项供用户复习。
 */
fun List<DynamicTutorialProgress>.preferredResumeCourseId(): String? {
  return asReversed().firstOrNull { !it.isCourseCompleted }?.courseId
    ?: lastOrNull()?.courseId
}
