package npm

import org.gradle.api.GradleException

/**
 * 一个入口在共享本地调试源中可达的完整 npm 包清单。
 *
 * Desktop 按包名和版本读取共享目录；Android 安装任务读取本清单，只推送当前入口实际需要的
 * 精确版本。清单保存在入口模块自己的 build 目录，不参与发布，也不会被其他入口覆盖。
 */
internal data class DebugNpmBundleManifest(
  /** 触发本次准备任务的入口 npm 包名。 */
  val entryPackage: String,

  /** 入口可达的全部本地包，包含内容未变化、继续复用稳定坐标的包。 */
  val packages: List<DebugNpmBundlePackage>,
)

/** 共享调试源中一个已完成校验与打包的 npm 归档。 */
internal data class DebugNpmBundlePackage(
  /** package.json 中的精确包名。 */
  val name: String,

  /** 实际 tgz 中的稳定版本或 debug 预发布版本。 */
  val version: String,

  /** 相对于根项目 build/npm/debug-source 的 tgz 路径。 */
  val relativeArchivePath: String,

  /** 内容或其本地依赖坐标是否偏离 Registry 中的稳定版本。 */
  val changed: Boolean,
)

/**
 * 将 npm 包坐标映射为共享调试源中的版本化归档路径。
 *
 * 同一包的多个 debug 版本必须共存，否则后构建入口会覆盖先构建入口仍在使用的共同依赖。
 * 包名和版本在进入文件系统前再次校验，避免清单或构建输入利用路径片段逃逸调试源。
 */
internal fun debugNpmArchiveRelativePath(packageName: String, version: String): String {
  if (!DEBUG_NPM_PACKAGE_NAME.matches(packageName)) {
    throw GradleException("Invalid npm package name '$packageName' in debug bundle.")
  }
  if (!DEBUG_NPM_PACKAGE_VERSION.matches(version)) {
    throw GradleException("Invalid npm package version '$version' for '$packageName'.")
  }
  val segments = packageName.split('/')
  return if (segments.size == 1) {
    "${segments[0]}/$version.tgz"
  } else {
    "${segments[0]}/${segments[1]}/$version.tgz"
  }
}

private val DEBUG_NPM_PACKAGE_NAME =
  Regex("""(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*""")
private val DEBUG_NPM_PACKAGE_VERSION = Regex(
  """(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-debug\.\d{14})?""",
)
