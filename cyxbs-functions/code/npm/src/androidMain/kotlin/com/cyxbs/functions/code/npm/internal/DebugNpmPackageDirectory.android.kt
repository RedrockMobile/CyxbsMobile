package com.cyxbs.functions.code.npm.internal

import okio.FileSystem
import okio.Path

/** Android 使用 App 私有临时目录，ADB 调试任务会把 tgz 原子写入该位置。 */
internal actual fun defaultDebugNpmPackageRootDirectory(): Path? {
  return FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cyxbs-code" / "npm" / "debug"
}
