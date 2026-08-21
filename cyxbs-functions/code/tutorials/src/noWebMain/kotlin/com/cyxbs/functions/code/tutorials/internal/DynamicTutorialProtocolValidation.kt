package com.cyxbs.functions.code.tutorials.internal

import com.cyxbs.functions.code.tutorials.DynamicTutorialInfo
import com.cyxbs.functions.code.tutorials.DynamicTutorialProtocolException
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCompletionKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourseSummary
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialGuideTargetKind
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile

/** 验证教程 npm 包的轻量目录，并返回同一实例供会话缓存。 */
internal fun DynamicTutorialManifest.validatedFor(
  tutorial: DynamicTutorialInfo,
): DynamicTutorialManifest {
  protocolRequire(languageId == tutorial.languageId) {
    "Tutorial manifest language '$languageId' does not match Catalog language '${tutorial.languageId}'."
  }
  protocolRequire(courses.isNotEmpty() && courses.size <= MAX_COURSES) {
    "Tutorial manifest must contain 1..$MAX_COURSES courses."
  }
  protocolRequire(courses.map { it.courseId }.distinct().size == courses.size) {
    "Tutorial manifest contains duplicate course IDs."
  }
  courses.forEach(DynamicTutorialCourseSummary::validateSummary)
  return this
}

/** 验证按 ID 加载的完整课程与 Manifest 摘要一致，避免 UI 在两个版本的数据之间跳变。 */
internal fun DynamicTutorialCourse.validatedAgainst(
  expectedSummary: DynamicTutorialCourseSummary,
): DynamicTutorialCourse {
  protocolRequire(summary == expectedSummary) {
    "Tutorial course '${expectedSummary.courseId}' does not match its manifest summary."
  }
  protocolRequire(lessons.isNotEmpty() && lessons.size <= MAX_LESSONS_PER_COURSE) {
    "Tutorial course '${summary.courseId}' must contain 1..$MAX_LESSONS_PER_COURSE lessons."
  }
  protocolRequire(lessons.map { it.lessonId }.distinct().size == lessons.size) {
    "Tutorial course '${summary.courseId}' contains duplicate lesson IDs."
  }

  var sourceCharacters = 0L
  var contentCharacters = 0L
  lessons.forEach { lesson ->
    validateIdentity(lesson.lessonId, "lesson ID")
    validateText(lesson.title, "lesson title")
    validateText(lesson.description, "lesson description", allowEmpty = true)
    protocolRequire(lesson.initialFiles.isNotEmpty() && lesson.initialFiles.size <= MAX_FILES_PER_LESSON) {
      "Tutorial lesson '${lesson.lessonId}' must contain 1..$MAX_FILES_PER_LESSON initial files."
    }
    protocolRequire(lesson.initialFiles.map { it.path }.distinct().size == lesson.initialFiles.size) {
      "Tutorial lesson '${lesson.lessonId}' contains duplicate file paths."
    }
    lesson.initialFiles.forEach { file ->
      file.validateSourceFile()
      sourceCharacters += file.source.length
    }
    protocolRequire(lesson.activeFilePath in lesson.initialFiles.map { it.path }) {
      "Tutorial lesson '${lesson.lessonId}' active file does not exist in its initial workspace."
    }
    protocolRequire(lesson.steps.isNotEmpty() && lesson.steps.size <= MAX_STEPS_PER_LESSON) {
      "Tutorial lesson '${lesson.lessonId}' must contain 1..$MAX_STEPS_PER_LESSON steps."
    }
    protocolRequire(lesson.steps.map { it.stepId }.distinct().size == lesson.steps.size) {
      "Tutorial lesson '${lesson.lessonId}' contains duplicate step IDs."
    }
    val sourceByPath = lesson.initialFiles.associateBy(DynamicTutorialSourceFile::path)
    lesson.steps.forEach { step ->
      validateIdentity(step.stepId, "step ID")
      validateText(step.title, "step title")
      protocolRequire(step.content.isNotEmpty() && step.content.size <= MAX_CONTENT_BLOCKS_PER_STEP) {
        "Tutorial step '${step.stepId}' must contain 1..$MAX_CONTENT_BLOCKS_PER_STEP content blocks."
      }
      step.content.forEach { block ->
        validateText(block.text, "tutorial content")
        contentCharacters += block.text.length
        block.languageId?.let { validateIdentity(it, "content language ID") }
      }
      step.guideTarget?.let { target ->
        when (target.kind) {
          DynamicTutorialGuideTargetKind.LAYOUT_ANCHOR -> {
            validateIdentity(target.anchorId.orEmpty(), "layout anchor ID")
            protocolRequire(target.filePath == null && target.from == null && target.to == null) {
              "Layout guide target '${step.stepId}' contains editor range fields."
            }
          }

          DynamicTutorialGuideTargetKind.EDITOR_RANGE -> {
            val sourceFile = target.filePath?.let(sourceByPath::get)
            val from = target.from
            val to = target.to
            protocolRequire(
              target.anchorId == null && sourceFile != null && from != null && to != null &&
                from >= 0 && to >= from && to <= sourceFile.source.length,
            ) {
              "Editor guide target '${step.stepId}' is outside its initial source file."
            }
          }
        }
      }
      when (step.completion.kind) {
        DynamicTutorialCompletionKind.MANUAL,
        DynamicTutorialCompletionKind.RUN_SUCCEEDED,
        -> Unit

        DynamicTutorialCompletionKind.OUTPUT_CONTAINS -> {
          validateText(step.completion.expected.orEmpty(), "expected output")
        }

        DynamicTutorialCompletionKind.SOURCE_CONTAINS -> {
          validateText(step.completion.expected.orEmpty(), "expected source")
          protocolRequire(step.completion.filePath?.let(sourceByPath::containsKey) == true) {
            "Source completion for '${step.stepId}' references an unknown file."
          }
        }
      }
    }
  }
  protocolRequire(sourceCharacters <= MAX_SOURCE_CHARACTERS_PER_COURSE) {
    "Tutorial course '${summary.courseId}' initial source exceeds its size limit."
  }
  protocolRequire(contentCharacters <= MAX_CONTENT_CHARACTERS_PER_COURSE) {
    "Tutorial course '${summary.courseId}' content exceeds its size limit."
  }
  return this
}

/** 限制教程包反馈文本，防止单次校验结果挤占编辑器内存。 */
internal fun DynamicTutorialEvaluationResult.validated(): DynamicTutorialEvaluationResult {
  val validatedFeedback = feedback
  protocolRequire(validatedFeedback == null || validatedFeedback.length <= MAX_FEEDBACK_CHARACTERS) {
    "Tutorial evaluation feedback exceeds its size limit."
  }
  return this
}

/** 校验课程卡片字段；未知前置课程留给课程路径按锁定状态解释。 */
private fun DynamicTutorialCourseSummary.validateSummary() {
  validateIdentity(courseId, "course ID")
  validateText(title, "course title")
  validateText(description, "course description", allowEmpty = true)
  protocolRequire(order >= 0 && estimatedMinutes in 1..MAX_ESTIMATED_MINUTES) {
    "Tutorial course '$courseId' contains an invalid order or estimated duration."
  }
  protocolRequire(
    prerequisiteCourseIds.size <= MAX_PREREQUISITES &&
      prerequisiteCourseIds.distinct().size == prerequisiteCourseIds.size,
  ) {
    "Tutorial course '$courseId' contains invalid prerequisite IDs."
  }
  prerequisiteCourseIds.forEach { validateIdentity(it, "prerequisite course ID") }
}

/** 校验工作区相对路径和单文件源码上限。 */
private fun DynamicTutorialSourceFile.validateSourceFile() {
  protocolRequire(
    path.isNotBlank() && path.length <= MAX_FILE_PATH_LENGTH &&
      !path.startsWith('/') && '\\' !in path &&
      path.split('/').none { it.isBlank() || it == "." || it == ".." },
  ) {
    "Tutorial source file '$path' is not a safe workspace-relative path."
  }
  protocolRequire(source.length <= MAX_SOURCE_CHARACTERS_PER_FILE) {
    "Tutorial source file '$path' exceeds its size limit."
  }
}

/** 稳定身份不能依赖空白、超长或首尾空格文本。 */
private fun validateIdentity(value: String, field: String) {
  protocolRequire(value.isNotBlank() && value.length <= MAX_IDENTITY_LENGTH && value == value.trim()) {
    "Tutorial $field is empty, padded or too long."
  }
}

/** 展示文本使用统一单字段上限，课程级别另有总量限制。 */
private fun validateText(value: String, field: String, allowEmpty: Boolean = false) {
  protocolRequire(value.length <= MAX_TEXT_CHARACTERS && (allowEmpty || value.isNotBlank())) {
    "Tutorial $field is empty or too long."
  }
}

/** 把 npm 包协议错误统一转换成业务可识别异常。 */
private inline fun protocolRequire(condition: Boolean, lazyMessage: () -> String) {
  if (!condition) throw DynamicTutorialProtocolException(lazyMessage())
}

private const val MAX_COURSES = 256
private const val MAX_LESSONS_PER_COURSE = 128
private const val MAX_STEPS_PER_LESSON = 256
private const val MAX_FILES_PER_LESSON = 128
private const val MAX_CONTENT_BLOCKS_PER_STEP = 64
private const val MAX_PREREQUISITES = 64
private const val MAX_ESTIMATED_MINUTES = 24 * 60
private const val MAX_IDENTITY_LENGTH = 256
private const val MAX_FILE_PATH_LENGTH = 1_024
private const val MAX_TEXT_CHARACTERS = 64 * 1024
private const val MAX_SOURCE_CHARACTERS_PER_FILE = 512 * 1024
private const val MAX_SOURCE_CHARACTERS_PER_COURSE = 2L * 1024L * 1024L
private const val MAX_CONTENT_CHARACTERS_PER_COURSE = 2L * 1024L * 1024L
private const val MAX_FEEDBACK_CHARACTERS = 64 * 1024
