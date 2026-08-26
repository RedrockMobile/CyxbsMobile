package com.cyxbs.functions.code.editor.project

import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.name
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 当前平台已经授权的项目存储根目录。
 *
 * [directory] 用于实际读写，[displayPath] 只用于界面展示；Android 的 content URI 不应直接暴露给
 * 用户，iOS 的安全作用域 URL 也不能作为跨安装稳定标识。
 */
internal data class CodeProjectStorageRoot(
  val directory: PlatformFile,
  val displayPath: String,
)

/**
 * 恢复或选择当前平台的项目根目录。
 *
 * [requestIfMissing] 为 false 时绝不弹出系统选择器；为 true 时，Android 与 iOS 可以请求用户选择
 * 一个卸载后仍保留的文档目录。Desktop 继续使用应用数据目录，不改变既有开发体验。
 */
internal expect suspend fun resolveDefaultCodeProjectStorageRoot(
  settings: Settings,
  requestIfMissing: Boolean,
): CodeProjectStorageRoot?

/** 选择一个已经存在的项目目录；取消系统选择器时返回 null。 */
internal suspend fun selectExternalCodeProjectDirectory(): PlatformFile? = FileKit.openDirectoryPicker()

/**
 * 保存单个外部项目目录的跨平台 bookmark。
 *
 * bookmark 只进入应用 Settings，不写入项目 manifest，避免泄露 URI、沙盒路径或安全作用域数据。
 */
@OptIn(ExperimentalEncodingApi::class)
internal suspend fun saveExternalCodeProjectDirectory(
  settings: Settings,
  projectId: String,
  directory: PlatformFile,
) {
  val bookmark = directory.bookmarkData()
  settings.putString(
    externalCodeProjectBookmarkKey(projectId),
    Base64.Default.encode(bookmark.bytes),
  )
}

/** 恢复单个外部项目目录；bookmark 失效时移除旧记录并返回 null。 */
@OptIn(ExperimentalEncodingApi::class)
internal fun restoreExternalCodeProjectDirectory(
  settings: Settings,
  projectId: String,
): PlatformFile? {
  val key = externalCodeProjectBookmarkKey(projectId)
  val encoded = settings.getStringOrNull(key) ?: return null
  val directory = runCatching {
    PlatformFile.fromBookmarkData(BookmarkData(Base64.Default.decode(encoded)))
  }.getOrNull()
  if (directory != null && directory.exists() && directory.isDirectory()) return directory
  settings.remove(key)
  return null
}

/** 删除外部项目目录授权记录，不删除真实目录和源码。 */
internal fun removeExternalCodeProjectDirectory(settings: Settings, projectId: String) {
  settings.remove(externalCodeProjectBookmarkKey(projectId))
}

/** 生成适合界面展示的路径；content URI 退化为目录名，避免暴露不可读的长 URI。 */
internal fun PlatformFile.externalCodeProjectDisplayPath(): String {
  val path = runCatching { absolutePath() }.getOrNull().orEmpty()
  return path.takeUnless { it.isBlank() || it.startsWith("content://") } ?: name.ifBlank { "外部项目" }
}

/**
 * 恢复或请求移动端的外部文档目录。
 *
 * Android bookmark 会持久化 SAF Tree URI 权限，iOS bookmark 会保存 security-scoped URL。
 * bookmark 随应用数据卸载后会消失，但源码和 manifest 仍在用户目录；重装后重新选择同一目录即可恢复。
 */
@OptIn(ExperimentalEncodingApi::class)
internal suspend fun resolveExternalCodeProjectStorageRoot(
  settings: Settings,
  requestIfMissing: Boolean,
  selectedDirectoryIsProjectRoot: Boolean = false,
  fixedDisplayPath: String? = null,
  directoryPicker: (suspend () -> PlatformFile?)? = null,
  directoryValidator: (PlatformFile) -> Boolean = { true },
): CodeProjectStorageRoot? {
  settings.getStringOrNull(CODE_PROJECT_ROOT_BOOKMARK_SETTINGS_KEY)?.let { encodedBookmark ->
    val restored = runCatching {
      PlatformFile.fromBookmarkData(
        BookmarkData(Base64.Default.decode(encodedBookmark)),
      )
    }.getOrNull()
    if (restored != null && directoryValidator(restored)) {
      return restored.toCodeProjectStorageRoot(
        selectedDirectoryIsProjectRoot = selectedDirectoryIsProjectRoot,
        fixedDisplayPath = fixedDisplayPath,
      )
    }
    settings.remove(CODE_PROJECT_ROOT_BOOKMARK_SETTINGS_KEY)
  }

  if (!requestIfMissing) return null
  val selectedDirectory = if (directoryPicker != null) {
    directoryPicker()
  } else {
    FileKit.openDirectoryPicker()
  }?.takeIf(directoryValidator) ?: return null
  val bookmark = selectedDirectory.bookmarkData()
  settings.putString(
    CODE_PROJECT_ROOT_BOOKMARK_SETTINGS_KEY,
    Base64.Default.encode(bookmark.bytes),
  )
  return selectedDirectory.toCodeProjectStorageRoot(
    selectedDirectoryIsProjectRoot = selectedDirectoryIsProjectRoot,
    fixedDisplayPath = fixedDisplayPath,
  )
}

/**
 * 将平台选择结果转换成项目根目录。
 *
 * Android 会直接选择固定的 `Download/CyxbsProjects`，因此不能再追加同名子目录；iOS 仍选择父目录，
 * 并在其中创建统一子目录，避免项目文件散落到文档提供方根目录。
 */
private fun PlatformFile.toCodeProjectStorageRoot(
  selectedDirectoryIsProjectRoot: Boolean,
  fixedDisplayPath: String?,
): CodeProjectStorageRoot {
  val projectsDirectory = if (selectedDirectoryIsProjectRoot) {
    this
  } else {
    this / CODE_PROJECTS_DIRECTORY_NAME
  }
  projectsDirectory.createDirectories()
  val parentLabel = name.ifBlank { "文档" }
  return CodeProjectStorageRoot(
    directory = projectsDirectory,
    displayPath = fixedDisplayPath ?: "$parentLabel/$CODE_PROJECTS_DIRECTORY_NAME",
  )
}

internal const val CODE_PROJECT_ROOT_BOOKMARK_SETTINGS_KEY =
  "code.editor.project-root-bookmark"
internal const val CODE_PROJECTS_DIRECTORY_NAME = "CyxbsProjects"
private const val CODE_EXTERNAL_PROJECT_BOOKMARK_SETTINGS_KEY_PREFIX =
  "code.editor.external-project-bookmark."

private fun externalCodeProjectBookmarkKey(projectId: String): String =
  CODE_EXTERNAL_PROJECT_BOOKMARK_SETTINGS_KEY_PREFIX + projectId
