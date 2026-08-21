package npm

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create

/**
 * 可被动态教程 Catalog 收集的语言教程包描述。
 *
 * npm 坐标始终读取同一 Project 的 [NpmJsPackageExtension.packageName]，避免教程发布坐标与
 * Catalog 中的下载坐标发生漂移。
 */
abstract class DynamicTutorialPackageExtension {
  /** 教程对应的稳定语言 ID，例如 `java`。 */
  abstract val languageId: Property<String>

  /** 面向课程入口展示的语言名称。 */
  abstract val displayName: Property<String>

  /** 可用于查找教程语言的短名称。 */
  abstract val aliases: ListProperty<String>
}

/**
 * 在当前 `manager.npmJs` 模块中声明可被教程 Catalog 收集的元数据。
 *
 * 新语言教程只需调用本函数并加入 Catalog 的 Project 列表，无需重复填写 npm 包名。
 */
fun Project.npmJsTutorial(configure: DynamicTutorialPackageExtension.() -> Unit) {
  if (!pluginManager.hasPlugin("manager.npmJs")) {
    throw GradleException("$path must apply manager.npmJs before calling npmJsTutorial.")
  }
  extensions.create<DynamicTutorialPackageExtension>("npmJsTutorialMetadata").apply {
    aliases.convention(emptyList())
    configure()
  }
}
