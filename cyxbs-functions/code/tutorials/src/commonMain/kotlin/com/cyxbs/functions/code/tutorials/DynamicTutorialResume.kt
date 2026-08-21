package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLesson
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile

/** 已按当前教程包内容校准的恢复结果。 */
data class DynamicTutorialResumeState(
  val lesson: DynamicTutorialLesson,
  val stepIndex: Int,
  val completedSteps: Set<DynamicTutorialCompletedStep>,
  val workspace: List<DynamicTutorialSourceFile>,
  val activeFilePath: String,
  val isCourseCompleted: Boolean,
  val restoredWorkspace: Boolean,
)

/**
 * 将持久进度映射到当前版本的课程正文。
 *
 * 已删除的课时和步骤会被忽略，新增步骤会自然成为未完成项。只有 npm 包版本一致且活动文件仍存在
 * 时才恢复源码现场，避免新课程模板加载旧代码；进度本身始终按稳定 ID 延续。
 */
fun DynamicTutorialCourse.resolveResumeState(
  progress: DynamicTutorialProgress?,
  npmPackageVersion: String,
  requestedLessonId: String? = null,
): DynamicTutorialResumeState {
  require(lessons.isNotEmpty()) { "Course '${summary.courseId}' does not contain any lesson." }
  require(lessons.all { it.steps.isNotEmpty() }) {
    "Course '${summary.courseId}' contains a lesson without tutorial steps."
  }
  val validSteps = lessons.flatMap { lesson ->
    lesson.steps.map { step -> DynamicTutorialCompletedStep(lesson.lessonId, step.stepId) }
  }.toSet()
  val completedSteps = progress
    ?.takeIf { it.courseId == summary.courseId }
    ?.completedSteps
    ?.filterTo(linkedSetOf()) { it in validSteps }
    .orEmpty()
  val requestedLesson = (requestedLessonId ?: progress
    ?.takeIf { it.courseId == summary.courseId }
    ?.lessonId)
    ?.let { lessonId -> lessons.firstOrNull { it.lessonId == lessonId } }
  val lesson = requestedLesson ?: lessons.firstOrNull { candidate ->
    candidate.steps.any { step ->
      DynamicTutorialCompletedStep(candidate.lessonId, step.stepId) !in completedSteps
    }
  } ?: lessons.last()
  val requestedStepIndex = progress
    ?.takeIf { it.courseId == summary.courseId && it.lessonId == lesson.lessonId }
    ?.stepId
    ?.let { stepId -> lesson.steps.indexOfFirst { it.stepId == stepId } }
    ?.takeIf { it >= 0 }
  val stepIndex = requestedStepIndex ?: lesson.steps.indexOfFirst { step ->
    DynamicTutorialCompletedStep(lesson.lessonId, step.stepId) !in completedSteps
  }.takeIf { it >= 0 } ?: lesson.steps.lastIndex

  val savedLessonWorkspace = progress
    ?.takeIf {
      it.courseId == summary.courseId && it.npmPackageVersion == npmPackageVersion
    }
    ?.lessonWorkspaces
    ?.firstOrNull { it.lessonId == lesson.lessonId }
    ?: progress
      ?.takeIf {
        it.courseId == summary.courseId &&
          it.lessonId == lesson.lessonId &&
          it.npmPackageVersion == npmPackageVersion &&
          it.activeFilePath != null
      }
      ?.let { legacy ->
        DynamicTutorialLessonWorkspace(
          lessonId = legacy.lessonId,
          workspace = legacy.workspace,
          activeFilePath = requireNotNull(legacy.activeFilePath),
        )
      }
  val canRestoreWorkspace = savedLessonWorkspace != null &&
    savedLessonWorkspace.workspace.isNotEmpty() &&
    savedLessonWorkspace.workspace.map { it.path }.distinct().size ==
    savedLessonWorkspace.workspace.size &&
    savedLessonWorkspace.activeFilePath in savedLessonWorkspace.workspace.map { it.path }
  val workspace = if (canRestoreWorkspace) {
    requireNotNull(savedLessonWorkspace).workspace
  } else {
    lesson.initialFiles
  }
  val activeFilePath = if (canRestoreWorkspace) {
    requireNotNull(savedLessonWorkspace).activeFilePath
  } else {
    lesson.activeFilePath
  }
  require(activeFilePath in workspace.map { it.path }) {
    "Tutorial active file '$activeFilePath' is missing from its workspace."
  }
  return DynamicTutorialResumeState(
    lesson = lesson,
    stepIndex = stepIndex,
    completedSteps = completedSteps,
    workspace = workspace,
    activeFilePath = activeFilePath,
    isCourseCompleted = validSteps.isNotEmpty() && completedSteps.containsAll(validSteps),
    restoredWorkspace = canRestoreWorkspace,
  )
}
