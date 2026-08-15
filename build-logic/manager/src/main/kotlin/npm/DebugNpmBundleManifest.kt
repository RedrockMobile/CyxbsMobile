package npm

/**
 * 一个入口在共享本地调试源中可达的完整 npm 包清单。
 *
 * Desktop 直接按包名读取共享目录；Android 安装任务读取本清单，只推送当前入口实际需要的包。
 * 清单保存在入口模块自己的 build 目录，不参与发布，也不会被其他入口覆盖。
 */
internal data class DebugNpmBundleManifest(
  /** 触发本次准备任务的入口 npm 包名。 */
  val entryPackage: String,

  /** 入口可达的全部本地包，包含内容未变化但需要覆盖旧 debug 文件的稳定包。 */
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
