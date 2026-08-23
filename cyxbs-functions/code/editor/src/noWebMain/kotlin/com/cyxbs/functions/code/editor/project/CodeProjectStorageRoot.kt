package com.cyxbs.functions.code.editor.project

import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.fromBookmarkData
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
