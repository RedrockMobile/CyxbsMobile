package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest

/** 课程路径中一门课程当前可见的学习状态。 */
enum class DynamicTutorialCourseState {
  /** 尚未开始，且所有前置课程均已完成。 */
  AVAILABLE,

  /** 已保存进度，但尚未完成整门课程。 */
  IN_PROGRESS,

  /** 所有步骤均已完成。 */
  COMPLETED,

  /** 至少一门前置课程尚未完成，当前不能进入。 */
  LOCKED,
}

/**
 * 一门课程在当前学习路径中的解析结果。
 *
 * [missingPrerequisiteCourseIds] 保留 Manifest 中的稳定 ID。即使教程包错误引用了不存在的课程，
 * 客户端也会保持锁定而不是绕过约束；展示层可把已知 ID 转换成课程标题。
 */
data class DynamicTutorialCourseAvailability(
  val course: DynamicTutorialCourseSummary,
  val state: DynamicTutorialCourseState,
  val missingPrerequisiteCourseIds: List<String> = emptyList(),
)

/**
 * 按 Manifest 顺序解析整个课程路径。
 *
 * 已完成状态优先于后来新增的前置关系，避免教程 npm 升级后把用户已经完成的课程重新锁住；未完成
 * 课程则必须等待全部前置课程完成。未知前置 ID 和循环依赖都会自然保持 [DynamicTutorialCourseState.LOCKED]。
 */
fun DynamicTutorialManifest.resolveCourseAvailability(
  completedCourseIds: Set<String>,
  startedCourseIds: Set<String> = emptySet(),
): List<DynamicTutorialCourseAvailability> {
  return courses.sortedBy(DynamicTutorialCourseSummary::order).map { course ->
    val missingPrerequisites = course.prerequisiteCourseIds
      .distinct()
      .filterNot(completedCourseIds::contains)
    val state = when {
      course.courseId in completedCourseIds -> DynamicTutorialCourseState.COMPLETED
      missingPrerequisites.isNotEmpty() -> DynamicTutorialCourseState.LOCKED
      course.courseId in startedCourseIds -> DynamicTutorialCourseState.IN_PROGRESS
      else -> DynamicTutorialCourseState.AVAILABLE
    }
    DynamicTutorialCourseAvailability(
      course = course,
      state = state,
      missingPrerequisiteCourseIds = missingPrerequisites,
    )
  }
}
