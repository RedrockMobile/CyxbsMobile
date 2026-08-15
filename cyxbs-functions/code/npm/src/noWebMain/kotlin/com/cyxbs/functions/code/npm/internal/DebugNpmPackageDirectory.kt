package com.cyxbs.functions.code.npm.internal

import okio.Path

/**
 * 返回当前平台使用的 npm 调试源目录；未配置本地调试环境时允许返回 null 并完全回退 Registry。
 */
internal expect fun defaultDebugNpmPackageRootDirectory(): Path?
