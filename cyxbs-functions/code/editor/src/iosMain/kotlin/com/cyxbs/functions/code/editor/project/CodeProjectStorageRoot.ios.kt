package com.cyxbs.functions.code.editor.project

import com.russhwolf.settings.Settings

/**
 * iOS 使用系统文档选择器保存项目，可选择“我的 iPhone”或云盘等应用沙盒外目录。
 */
internal actual suspend fun resolveDefaultCodeProjectStorageRoot(
  settings: Settings,
  requestIfMissing: Boolean,
): CodeProjectStorageRoot? = resolveExternalCodeProjectStorageRoot(settings, requestIfMissing)
