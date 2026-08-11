package npm

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create

/**
 * 一个可被动态语言 Catalog 收集的 Kotlin/JS 语言包描述。
 *
 * npm 坐标不在这里重复声明，而是始终读取同一 Project 的 [NpmJsPackageExtension.packageName]，
 * 避免语言包发布坐标与 Catalog 坐标分离。
 */
abstract class DynamicLanguagePackageExtension {
  /** 语言稳定 ID，例如 `javascript`。 */
  abstract val languageId: Property<String>

  /** 面向教学 UI 的展示名称。 */
  abstract val displayName: Property<String>

  /** 可用于查找语言的短名称，例如 `js`。 */
  abstract val aliases: ListProperty<String>

  /** 不包含前导点的文件扩展名。 */
  abstract val fileExtensions: ListProperty<String>
}

/**
 * 在当前 `manager.npmJs` 模块中声明可被 Catalog 收集的语言元数据。
 *
 * 该函数直接写在语言模块自己的 build.gradle.kts 中，不需要额外约定插件。
 */
fun Project.npmJsLanguage(configure: DynamicLanguagePackageExtension.() -> Unit) {
  if (!pluginManager.hasPlugin("manager.npmJs")) {
    throw GradleException("$path must apply manager.npmJs before calling npmJsLanguage.")
  }
  extensions.create<DynamicLanguagePackageExtension>("npmJsLanguageMetadata").apply {
    aliases.convention(emptyList())
    fileExtensions.convention(emptyList())
    configure()
  }
}
