package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLessonSummary
import kotlinx.serialization.Serializable

/** 一个已完成教程步骤的稳定身份。 */
@Serializable
data class DynamicTutorialCompletedStep(
  val lessonId: String,
  val stepId: String,
)

/** 一个课时独立保存的多文件代码现场。 */
@Serializable
data class DynamicTutorialLessonWorkspace(
  val lessonId: String,
  val workspace: List<DynamicTutorialSourceFile>,
  val activeFilePath: String,
)

/**
 * 一门课程可跨页面生命周期恢复的学习进度。
 *
 * 课程、课时和步骤只按教程协议中的稳定 ID 关联，不按列表下标关联。[npmPackageVersion] 用于判断
 * [workspace] 是否仍能安全恢复：教程包升级后保留学习进度，但客户端应使用新包提供的初始源码。
 */
@Serializable
data class DynamicTutorialProgress(
  val languageId: String,
  val npmPackageName: String,
  val npmPackageVersion: String,
  val courseId: String,
  val lessonId: String,
  val stepId: String,
  val completedSteps: List<DynamicTutorialCompletedStep> = emptyList(),
  val workspace: List<DynamicTutorialSourceFile> = emptyList(),
  val activeFilePath: String? = null,
  val lessonWorkspaces: List<DynamicTutorialLessonWorkspace> = emptyList(),
  val isCourseCompleted: Boolean = false,
)

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
