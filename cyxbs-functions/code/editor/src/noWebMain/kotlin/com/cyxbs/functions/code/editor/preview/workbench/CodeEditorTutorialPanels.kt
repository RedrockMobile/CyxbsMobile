package com.cyxbs.functions.code.editor.preview.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.School
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.functions.code.editor.workbench.CodeEditorSidePanel
import com.cyxbs.functions.code.editor.workbench.CodeEditorToolWindow
import com.cyxbs.functions.code.editor.workbench.EditorWorkbenchColors
import com.cyxbs.functions.code.tutorials.DynamicTutorialCompletedStep
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletionKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentBlock
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialContentKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialLesson
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialStep
import kotlin.math.roundToInt

/** 教程侧栏和底部工具窗口使用的稳定标识。 */
internal const val TUTORIALS_PANEL_ID = "tutorials"
internal const val TUTORIAL_TOOL_WINDOW_ID = "tutorial"

/**
 * 当前进入的课时及步骤。
 *
 * npm 教程会话由页面持有；该不可变模型只承载 UI 所需的课程内容和即时校验反馈，避免面板反向持有
 * Runtime。[isCompleted] 表示最后一步已经通过，仍保留正文便于用户复习。
 */
internal data class ActiveCodeEditorTutorial(
  val course: DynamicTutorialCourse,
  val lesson: DynamicTutorialLesson,
  val stepIndex: Int = 0,
  val completedSteps: Set<DynamicTutorialCompletedStep> = emptySet(),
  val feedback: String? = null,
  val isCompleted: Boolean = false,
) {
  val step: DynamicTutorialStep
    get() = lesson.steps[stepIndex]

  /** 通过当前步骤后进入下一步；最后一步通过后保留正文并标记整课完成。 */
  fun advance(): ActiveCodeEditorTutorial {
    val completed = completedSteps + DynamicTutorialCompletedStep(lesson.lessonId, step.stepId)
    val nextStepIndex = stepIndex + 1
    return if (nextStepIndex < lesson.steps.size) {
      copy(
        stepIndex = nextStepIndex,
        completedSteps = completed,
        feedback = null,
        isCompleted = false,
      )
    } else {
      val allCourseStepsCompleted = course.lessons.all { courseLesson ->
        courseLesson.steps.all { courseStep ->
          DynamicTutorialCompletedStep(courseLesson.lessonId, courseStep.stepId) in completed
        }
      }
      copy(
        completedSteps = completed,
        feedback = "已完成 ${lesson.title}。",
        isCompleted = allCourseStepsCompleted,
      )
    }
  }

  /** 返回上一教程步骤；第一步保持原位，且清除上一次校验反馈。 */
  fun previous(): ActiveCodeEditorTutorial {
    return copy(
      stepIndex = (stepIndex - 1).coerceAtLeast(0),
      feedback = null,
      isCompleted = false,
    )
  }
}

/** 创建由教程业务注入的课程路径侧栏。 */
@Composable
internal fun rememberCodeEditorTutorialSidePanel(
  manifest: DynamicTutorialManifest?,
  status: String,
  isLoading: Boolean,
  activeCourseId: String?,
  completedCourseIds: Set<String>,
  onOpenCourse: (String) -> Unit,
): CodeEditorSidePanel {
  return CodeEditorSidePanel(
    id = TUTORIALS_PANEL_ID,
    title = "教程",
    icon = Icons.Default.School,
  ) {
    TutorialCoursePath(
      manifest = manifest,
      status = status,
      isLoading = isLoading,
      activeCourseId = activeCourseId,
      completedCourseIds = completedCourseIds,
      onOpenCourse = { courseId ->
        onOpenCourse(courseId)
        if (layoutMode != com.cyxbs.functions.code.editor.workbench.CodeEditorWorkbenchLayoutMode.Expanded) {
          closePanel()
        }
      },
    )
  }
}

/** AIDE 风格的卡片课程路径；课程顺序和前置关系完全来自动态 Manifest。 */
@Composable
private fun TutorialCoursePath(
  manifest: DynamicTutorialManifest?,
  status: String,
  isLoading: Boolean,
  activeCourseId: String?,
  completedCourseIds: Set<String>,
  onOpenCourse: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = manifest?.languageId?.uppercase()?.plus(" 学习路径") ?: "教程",
      color = EditorWorkbenchColors.PrimaryText,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = status,
      color = EditorWorkbenchColors.SecondaryText,
      fontSize = 10.sp,
      lineHeight = 14.sp,
    )
    if (isLoading) {
      Text("正在下载教程包…", color = EditorWorkbenchColors.Accent, fontSize = 11.sp)
    }
    manifest?.courses
      ?.sortedBy { it.order }
      ?.forEachIndexed { index, course ->
        Row(modifier = Modifier.fillMaxWidth()) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp),
          ) {
            Box(
              modifier = Modifier
                .size(20.dp)
                .background(
                  color = if (course.courseId == activeCourseId) {
                    EditorWorkbenchColors.Accent
                  } else {
                    EditorWorkbenchColors.EditorBackground
                  },
                  shape = CircleShape,
                ),
              contentAlignment = Alignment.Center,
            ) {
              if (course.courseId in completedCourseIds) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "已完成",
                  tint = if (course.courseId == activeCourseId) Color.White else EditorWorkbenchColors.Accent,
                  modifier = Modifier.size(12.dp),
                )
              } else {
                Text(
                  text = (index + 1).toString(),
                  color = if (course.courseId == activeCourseId) Color.White else EditorWorkbenchColors.SecondaryText,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            if (index != manifest.courses.lastIndex) {
              Spacer(
                modifier = Modifier
                  .width(1.dp)
                  .height(72.dp)
                  .background(EditorWorkbenchColors.Divider),
              )
            }
          }
          Surface(
            modifier = Modifier
              .weight(1F)
              .clickable { onOpenCourse(course.courseId) },
            color = if (course.courseId == activeCourseId) {
              Color(0x283F76D3)
            } else {
              EditorWorkbenchColors.EditorBackground
            },
            shape = RoundedCornerShape(8.dp),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = course.title,
                  color = EditorWorkbenchColors.PrimaryText,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1F),
                )
                Text(
                  text = "${course.estimatedMinutes} 分钟",
                  color = EditorWorkbenchColors.SecondaryText,
                  fontSize = 9.sp,
                )
              }
              Text(
                text = course.description,
                color = EditorWorkbenchColors.SecondaryText,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
  }
}

/** 把当前步骤放到 Run 之前的 Tutorial 工具窗口中。 */
internal fun codeEditorTutorialToolWindow(
  tutorial: ActiveCodeEditorTutorial,
  onPrevious: () -> Unit,
  onCheck: () -> Unit,
): CodeEditorToolWindow {
  return CodeEditorToolWindow(
    id = TUTORIAL_TOOL_WINDOW_ID,
    title = "Tutorial",
    icon = Icons.Default.School,
  ) {
    TutorialToolWindowContent(
      tutorial = tutorial,
      onPrevious = onPrevious,
      onCheck = onCheck,
    )
  }
}

/** 教程正文保持紧凑，允许用户在上方继续编辑而不被模态组件拦截。 */
@Composable
private fun TutorialToolWindowContent(
  tutorial: ActiveCodeEditorTutorial,
  onPrevious: () -> Unit,
  onCheck: () -> Unit,
) {
  val step = tutorial.step
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1F)) {
        Text(
          text = tutorial.lesson.title,
          color = EditorWorkbenchColors.SecondaryText,
          fontSize = 10.sp,
        )
        Text(
          text = step.title,
          color = EditorWorkbenchColors.PrimaryText,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Text(
        text = "${tutorial.stepIndex + 1} / ${tutorial.lesson.steps.size}",
        color = EditorWorkbenchColors.SecondaryText,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
      )
    }
    step.content.forEach { block -> TutorialContentBlock(block) }
    tutorial.feedback?.let { feedback ->
      Text(
        text = feedback,
        color = if (tutorial.isCompleted) Color(0xFF64C493) else Color(0xFFFFC66D),
        fontSize = 10.sp,
        lineHeight = 14.sp,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (tutorial.stepIndex > 0 && !tutorial.isCompleted) {
        TutorialAction(text = "上一步", emphasized = false, onClick = onPrevious)
        Spacer(Modifier.width(6.dp))
      }
      if (!tutorial.isCompleted && step.completion.kind != DynamicTutorialCompletionKind.RUN_SUCCEEDED &&
        step.completion.kind != DynamicTutorialCompletionKind.OUTPUT_CONTAINS
      ) {
        TutorialAction(
          text = if (step.completion.kind == DynamicTutorialCompletionKind.MANUAL) "继续" else "检查",
          emphasized = true,
          onClick = onCheck,
        )
      }
      if (tutorial.isCompleted) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = Color(0xFF64C493),
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("本课已完成", color = Color(0xFF64C493), fontSize = 11.sp)
      }
    }
  }
}

/** 按正文种类建立稳定视觉层级，不允许 npm 包传递任意 Compose UI。 */
@Composable
private fun TutorialContentBlock(block: DynamicTutorialContentBlock) {
  val background = when (block.kind) {
    DynamicTutorialContentKind.PARAGRAPH -> Color.Transparent
    DynamicTutorialContentKind.TIP -> Color(0x1F6CB6FF)
    DynamicTutorialContentKind.CODE -> Color(0xFF111720)
  }
  Text(
    text = block.text,
    color = EditorWorkbenchColors.PrimaryText,
    fontFamily = if (block.kind == DynamicTutorialContentKind.CODE) FontFamily.Monospace else FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    modifier = Modifier
      .fillMaxWidth()
      .background(background, RoundedCornerShape(6.dp))
      .padding(if (block.kind == DynamicTutorialContentKind.PARAGRAPH) 0.dp else 8.dp),
  )
}

/** 小型非模态操作按钮，与深色工具窗口保持一致。 */
@Composable
private fun TutorialAction(
  text: String,
  emphasized: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = if (emphasized) EditorWorkbenchColors.Accent else EditorWorkbenchColors.EditorBackground,
    shape = RoundedCornerShape(5.dp),
    modifier = Modifier.clickable(onClick = onClick),
  ) {
    Text(
      text = text,
      color = Color.White,
      fontSize = 10.sp,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}

/** 在布局锚点旁展示教程提示；实际高亮镂空由 guided-tour 组件统一绘制。 */
@Composable
internal fun BoxScope.TutorialGuideHint(
  targetBounds: Rect?,
  text: String,
) {
  targetBounds ?: return
  val guideWidthPx = with(LocalDensity.current) { GuideHintWidth.toPx() }
  Surface(
    color = EditorWorkbenchColors.PanelBackground,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier
      .offset {
        IntOffset(
          x = (targetBounds.right - guideWidthPx).roundToInt().coerceAtLeast(8),
          y = (targetBounds.bottom + 12).roundToInt(),
        )
      }
      .widthIn(max = GuideHintWidth),
  ) {
    Text(
      text = text,
      color = EditorWorkbenchColors.PrimaryText,
      fontSize = 11.sp,
      lineHeight = 15.sp,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
    )
  }
}

private val GuideHintWidth = 180.dp
