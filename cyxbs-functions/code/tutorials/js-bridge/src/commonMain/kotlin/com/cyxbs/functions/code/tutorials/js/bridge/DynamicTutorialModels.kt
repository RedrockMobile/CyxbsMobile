package com.cyxbs.functions.code.tutorials.js.bridge

import kotlinx.serialization.Serializable

/** 一个语言教程包的轻量目录，供侧边栏在不下载完整课程内容时绘制课程路径。 */
@Serializable
data class DynamicTutorialManifest(
  val languageId: String,
  val courses: List<DynamicTutorialCourseSummary>,
)

/** 侧边栏卡片式课程路径中的单张课程卡片。 */
@Serializable
data class DynamicTutorialCourseSummary(
  val courseId: String,
  val title: String,
  val description: String,
  val order: Int,
  val estimatedMinutes: Int,
  val prerequisiteCourseIds: List<String> = emptyList(),
)

/** 包含全部课时与步骤的课程正文。 */
@Serializable
data class DynamicTutorialCourse(
  val summary: DynamicTutorialCourseSummary,
  val lessons: List<DynamicTutorialLesson>,
)

/** 课程中的一个可独立进入、保存进度的课时。 */
@Serializable
data class DynamicTutorialLesson(
  val lessonId: String,
  val title: String,
  val description: String,
  val initialFiles: List<DynamicTutorialSourceFile>,
  val activeFilePath: String,
  val steps: List<DynamicTutorialStep>,
)

/** 教程首次打开课时时写入编辑器工作区的文件。 */
@Serializable
data class DynamicTutorialSourceFile(
  val path: String,
  val source: String,
)

/**
 * 一步教程指引。
 *
 * [content] 显示在底部 Tutorial 工具窗口；[guideTarget] 只描述语义锚点或源码区间，不包含屏幕
 * 坐标，因此竖屏、宽屏和窗口动态变化时都可由端上重新测量。
 */
@Serializable
data class DynamicTutorialStep(
  val stepId: String,
  val title: String,
  val content: List<DynamicTutorialContentBlock>,
  val guideTarget: DynamicTutorialGuideTarget? = null,
  val completion: DynamicTutorialCompletionRule = DynamicTutorialCompletionRule(),
)

/** 教程正文块类型；客户端可按类型渲染文本、提示或只读代码。 */
@Serializable
enum class DynamicTutorialContentKind {
  PARAGRAPH,
  TIP,
  CODE,
}

/** 一段不携带 Compose 组件的声明式教程正文。 */
@Serializable
data class DynamicTutorialContentBlock(
  val kind: DynamicTutorialContentKind,
  val text: String,
  val languageId: String? = null,
)

/** 引导目标种类。 */
@Serializable
enum class DynamicTutorialGuideTargetKind {
  /** 由业务布局通过稳定 ID 注册的按钮或面板。 */
  LAYOUT_ANCHOR,

  /** 编辑器中某个文件的 UTF-16 源码区间。 */
  EDITOR_RANGE,
}

/**
 * 可跨平台解析的引导目标。
 *
 * [anchorId] 仅用于 [DynamicTutorialGuideTargetKind.LAYOUT_ANCHOR]；源码目标使用 [filePath]、
 * [from] 与 [to]。端上校验字段组合，教程包不得传递像素位置。
 */
@Serializable
data class DynamicTutorialGuideTarget(
  val kind: DynamicTutorialGuideTargetKind,
  val anchorId: String? = null,
  val filePath: String? = null,
  val from: Int? = null,
  val to: Int? = null,
)

/** 教程步骤可由哪类业务事件完成。 */
@Serializable
enum class DynamicTutorialCompletionKind {
  MANUAL,
  RUN_SUCCEEDED,
  OUTPUT_CONTAINS,
  SOURCE_CONTAINS,
}

/** 教程步骤的声明式完成条件。 */
@Serializable
data class DynamicTutorialCompletionRule(
  val kind: DynamicTutorialCompletionKind = DynamicTutorialCompletionKind.MANUAL,
  val expected: String? = null,
  val filePath: String? = null,
)

/** 客户端在运行、编辑或手动检查后提交给教程包的当前状态。 */
@Serializable
data class DynamicTutorialEvaluationRequest(
  val courseId: String,
  val lessonId: String,
  val stepId: String,
  val workspace: List<DynamicTutorialSourceFile>,
  val runExecuted: Boolean = false,
  val standardOutput: String = "",
  val standardError: String = "",
)

/** 教程包对当前步骤的判定结果。 */
@Serializable
data class DynamicTutorialEvaluationResult(
  val completed: Boolean,
  val feedback: String? = null,
)

/** 编辑器与教程包共同使用的稳定布局锚点，后续可只追加新值。 */
object DynamicTutorialAnchorIds {
  const val RUN_BUTTON = "editor.toolbar.run"
  const val TUTORIAL_TOOL_WINDOW = "editor.tool-window.tutorial"
  const val FILE_TREE = "editor.sidebar.files"
}
