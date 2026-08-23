package com.cyxbs.functions.code.tutorials

/** 校验教程 npm 返回的完整进度集合，并拒绝重复课程或越界快照。 */
internal fun List<DynamicTutorialProgress>.validatedFor(
  tutorial: DynamicTutorialInfo,
): List<DynamicTutorialProgress> {
  progressRequire(size <= MAX_PROGRESS_ENTRIES) {
    "Tutorial package returned too many progress entries."
  }
  progressRequire(map { it.courseId }.distinct().size == size) {
    "Tutorial package returned duplicate course progress entries."
  }
  return onEach { it.validatedFor(tutorial) }
}

/**
 * 校验单条进度的协议身份和资源边界。
 *
 * 客户端不解释教程包私有的 Storage schema，但仍必须在把动态数据交给编辑器前限制列表数量、
 * 路径和源码总量，避免损坏或恶意 npm 包制造无上限内存占用。
 */
internal fun DynamicTutorialProgress.validatedFor(
  tutorial: DynamicTutorialInfo,
): DynamicTutorialProgress {
  val identities = listOf(
    languageId,
    npmPackageName,
    npmPackageVersion,
    courseId,
    lessonId,
    stepId,
  )
  val sourceCharacterCount = workspace.sumOf { it.source.length.toLong() } +
    lessonWorkspaces.sumOf { lessonWorkspace ->
      lessonWorkspace.workspace.sumOf { it.source.length.toLong() }
    }
  progressRequire(identities.all(::isValidIdentity)) {
    "Tutorial progress contains an invalid stable identity."
  }
  progressRequire(languageId == tutorial.languageId && npmPackageName == tutorial.npmPackageName) {
    "Tutorial progress identity does not match the loaded npm package."
  }
  progressRequire(completedSteps.size <= MAX_COMPLETED_STEPS &&
    completedSteps.distinct().size == completedSteps.size &&
    completedSteps.all { isValidIdentity(it.lessonId) && isValidIdentity(it.stepId) }) {
    "Tutorial progress contains invalid completed step identities."
  }
  progressRequire(workspace.isValidWorkspace() &&
    (activeFilePath == null || activeFilePath in workspace.map { it.path })) {
    "Tutorial progress contains an invalid active workspace."
  }
  progressRequire(lessonWorkspaces.size <= MAX_LESSON_WORKSPACES &&
    lessonWorkspaces.map { it.lessonId }.distinct().size == lessonWorkspaces.size &&
    lessonWorkspaces.all { lessonWorkspace ->
      isValidIdentity(lessonWorkspace.lessonId) &&
        lessonWorkspace.workspace.isValidWorkspace() &&
        lessonWorkspace.activeFilePath in lessonWorkspace.workspace.map { it.path }
    }) {
    "Tutorial progress contains invalid lesson workspaces."
  }
  progressRequire(sourceCharacterCount <= MAX_WORKSPACE_SOURCE_CHARACTERS) {
    "Tutorial progress source snapshots exceed the client boundary."
  }
  return this
}

/** 限制单份工作区的文件数量、唯一安全相对路径和路径长度。 */
private fun List<com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialSourceFile>
  .isValidWorkspace(): Boolean =
  size <= MAX_WORKSPACE_FILES &&
    map { it.path }.distinct().size == size &&
    all { file -> isValidWorkspacePath(file.path) }

/**
 * 教程快照只允许使用 `/` 分隔的相对路径。
 *
 * 当前编辑器尚未直接把快照写入磁盘，但提前拒绝绝对路径和 `..` 可避免未来接入真实项目时把
 * npm 包内的持久化数据变成目录穿越入口。
 */
private fun isValidWorkspacePath(path: String): Boolean =
  isValidIdentity(path, MAX_FILE_PATH_LENGTH) &&
    !path.startsWith('/') &&
    '\\' !in path &&
    path.split('/').none { segment -> segment.isEmpty() || segment == "." || segment == ".." }

/** 稳定 ID 不允许首尾空格、空值或超出协议长度。 */
private fun isValidIdentity(value: String, maxLength: Int = MAX_IDENTITY_LENGTH): Boolean =
  value.isNotBlank() && value == value.trim() && value.length <= maxLength

/** 把教程进度协议错误转换成统一的动态教程异常。 */
private inline fun progressRequire(condition: Boolean, lazyMessage: () -> String) {
  if (!condition) throw DynamicTutorialProtocolException(lazyMessage())
}

private const val MAX_PROGRESS_ENTRIES = 128
private const val MAX_COMPLETED_STEPS = 2_048
private const val MAX_WORKSPACE_FILES = 128
private const val MAX_LESSON_WORKSPACES = 64
private const val MAX_IDENTITY_LENGTH = 256
private const val MAX_FILE_PATH_LENGTH = 1_024
private const val MAX_WORKSPACE_SOURCE_CHARACTERS = 2L * 1024L * 1024L
