package com.cyxbs.functions.code.editor.project

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory

/**
 * 将较长的本地项目路径收缩为保留末尾目录的显示文本。
 *
 * [maxSegments] 只控制界面展示，不会改变项目实际保存位置；Windows 分隔符会统一显示为斜杠，
 * 方便在各平台保持一致的末尾省略效果。
 */
internal fun projectDirectoryLabel(
  path: String,
  maxSegments: Int = 3,
): String {
  require(maxSegments > 0) { "maxSegments 必须大于 0。" }
  val normalized = path.replace('\\', '/').trimEnd('/')
  val segments = normalized.split('/').filter(String::isNotEmpty)
  if (segments.size <= maxSegments) return path
  return "…/" + segments.takeLast(maxSegments).joinToString("/")
}

/**
 * 使用当前平台的系统文件处理器打开项目目录。
 *
 * 桌面端会打开 Finder 或资源管理器；Android、iOS 交给系统文件应用处理。项目目录不存在或平台
 * 没有可处理该目录的应用时返回失败，由界面决定如何提示，避免平台异常终止编辑器。
 */
internal fun openProjectDirectory(directory: PlatformFile): Result<Unit> = runCatching {
  require(directory.exists() && directory.isDirectory()) { "项目目录已不可访问。" }
  FileKit.openFileWithDefaultApplication(directory)
}
