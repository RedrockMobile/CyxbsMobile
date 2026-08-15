package com.cyxbs.functions.code.npm.internal

import com.cyxbs.components.init.DesktopProjectEnvironment
import okio.Path
import okio.Path.Companion.toPath

/**
 * Desktop 直接读取根项目 build 中的 npm 调试源，不复制到应用缓存目录。
 *
 * 宿主未初始化项目路径时返回 null，默认 Transport 会继续访问正常 Registry。
 */
internal actual fun defaultDebugNpmPackageRootDirectory(): Path? {
  return DesktopProjectEnvironment.projectDirectory
    ?.resolve("build/npm/debug-source")
    ?.toString()
    ?.toPath(normalize = true)
}
