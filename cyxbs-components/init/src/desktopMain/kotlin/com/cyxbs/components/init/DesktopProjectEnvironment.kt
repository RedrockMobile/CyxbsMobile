package com.cyxbs.components.init

import java.nio.file.Path

/**
 * Desktop 本地开发环境共享的根项目路径。
 *
 * 路径由最终应用的 BuildConfig 在进程启动时注入，底层模块借此定位只存在于源码工程中的构建
 * 产物，而无需依赖应用模块或猜测进程工作目录。正式安装环境中路径可以不存在，具体功能应自行
 * 检查目标文件并回退正常线上流程。
 */
object DesktopProjectEnvironment {

  /** 已规范化的绝对项目路径；release 包/壳模块未配置时返回 null。 */
  var projectDirectory: Path? = null
    private set

  /** 当前 Desktop 壳应用 id，由宿主在启动阶段写入。 */
  lateinit var appId: String
    private set

  /**
   * 初始化当前进程唯一的项目根路径。
   *
   * 空项目路径表示当前不是源码工程内的调试运行，此时 [projectDirectory] 为 null，使用方应回退
   * 正常线上资源。该方法由最终宿主在启动阶段调用，再次调用会整体更新当前宿主信息。
   *
   * @param appId 项目 id
   * @param projectDirectory Gradle 生成的项目根目录。
   * @throws IllegalArgumentException 非空路径无法转换为合法路径。
   */
  fun initialize(appId: String, projectDirectory: String) {
    this.appId = appId
    this.projectDirectory = if (projectDirectory.isNotBlank()) Path.of(projectDirectory).toAbsolutePath().normalize() else null
  }
}
