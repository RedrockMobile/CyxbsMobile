package com.cyxbs.functions.code.editor.project

import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.filesDir

/**
 * Desktop 保留现有应用数据目录，避免开发和热更新时反复弹出目录选择器。
 */
internal actual suspend fun resolveDefaultCodeProjectStorageRoot(
  settings: Settings,
  requestIfMissing: Boolean,
): CodeProjectStorageRoot? {
  val directory = FileKit.filesDir / "cyxbs-code" / "projects"
  directory.createDirectories()
  return CodeProjectStorageRoot(
    directory = directory,
    displayPath = directory.absolutePath(),
  )
}
