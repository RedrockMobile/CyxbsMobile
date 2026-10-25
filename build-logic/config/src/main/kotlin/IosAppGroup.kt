import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * 分别从 Xcode 管理的 entitlement 取得主 App 与 Widget Extension 的共享容器标识符。
 *
 * 返回 Provider 以便 BuildConfig 任务跟踪文件内容；换届后在 Signing & Capabilities 改组即可触发重生成。
 */
object IosAppGroup {

  /** 只读取主 App 的 entitlement，供 config 模块生成主 App 使用的标识符。 */
  fun mainAppId(project: Project): Provider<String> {
    val iosAppDirectory = project.rootProject.layout.projectDirectory.dir("cyxbs-applications/pro/iosApp")
    return project.providers.fileContents(
      iosAppDirectory.file("CyxbsMobile2019_iOS/掌上重邮.entitlements")
    ).asText.map { readSoleId(it, "主 App") }
  }

  /** 只读取扩展的 entitlement；Swift 运行时也直接从自身签名读取同一标识符。 */
  fun widgetExtensionId(project: Project): Provider<String> {
    val iosAppDirectory = project.rootProject.layout.projectDirectory.dir("cyxbs-applications/pro/iosApp")
    return project.providers.fileContents(
      iosAppDirectory.file("CyxbsWidgetExtension.entitlements")
    ).asText.map { readSoleId(it, "Widget Extension") }
  }

  /** 共享快照要求两端加入同一个 App Group；校验不改变各模块字段的独立来源。 */
  fun validatedWidgetExtensionId(project: Project): Provider<String> {
    return widgetExtensionId(project).zip(mainAppId(project)) { widgetId, appId ->
      if (widgetId != appId) {
        throw GradleException("主 App 与 Widget Extension 的 App Group 不一致：$appId / $widgetId")
      }
      widgetId
    }
  }

  /** 只支持唯一的 group.* 标识符，避免后续增加其他共享组时意外选错。 */
  private fun readSoleId(plist: String, targetName: String): String {
    val blocks = Regex(
      """<key>\s*com\.apple\.security\.application-groups\s*</key>\s*<array>(.*?)</array>""",
      RegexOption.DOT_MATCHES_ALL,
    ).findAll(plist).toList()
    if (blocks.size != 1) {
      throw GradleException("$targetName 必须恰好声明一个 App Groups entitlement")
    }
    val ids = Regex("""<string>\s*([^<]+?)\s*</string>""")
      .findAll(blocks.single().groupValues[1]).map { it.groupValues[1].trim() }.toList()
    if (ids.size != 1 || !ids.single().matches(Regex("""group\.[A-Za-z0-9.-]+"""))) {
      throw GradleException("$targetName 必须恰好选择一个有效的 group.* 标识符")
    }
    return ids.single()
  }
}
