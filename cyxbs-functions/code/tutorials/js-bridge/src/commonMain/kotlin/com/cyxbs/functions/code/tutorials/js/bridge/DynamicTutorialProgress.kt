package com.cyxbs.functions.code.tutorials.js.bridge

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
 * 该对象属于教程 npm 协议，由 npm 包自行决定如何持久化和迁移。课程、课时和步骤只按稳定 ID
 * 关联，不按列表下标关联。[npmPackageVersion] 供客户端判断源码现场是否可直接恢复；教程包升级时
 * 可以在 [DynamicTutorialService.savedProgress] 内迁移旧 ID 或源码后再返回。
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
