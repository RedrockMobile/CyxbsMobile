package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile
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
