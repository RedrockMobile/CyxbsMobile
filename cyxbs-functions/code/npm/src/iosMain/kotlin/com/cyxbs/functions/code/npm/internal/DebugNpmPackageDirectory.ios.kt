package com.cyxbs.functions.code.npm.internal

import okio.FileSystem
import okio.Path

/** iOS 暂时保留应用临时目录约定，为后续本地调试注入能力预留入口。 */
internal actual fun defaultDebugNpmPackageRootDirectory(): Path? {
  return FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cyxbs-code" / "npm" / "debug"
}
