package npm

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

/** 不需要 JavaScript 入口的静态 npm 包发布配置。 */
abstract class NpmStaticPackageExtension @Inject constructor(
  objects: ObjectFactory,
) {
  /** 当前模块的稳定 npm 坐标；默认从 Gradle 模块路径生成。 */
  abstract val packageName: Property<String>

  /**
   * 需要原样放入 npm tgz 的资源源集。
   *
   * 默认包含 `src/resources`，并作为 Resources Root 同步给 Android Studio。可以通过
   * [SourceDirectorySet.srcDir] 追加生成目录，但所有根目录都不得自行提供 package.json 或产生
   * 相同相对路径。
   */
  val resources: SourceDirectorySet = objects.sourceDirectorySet(
    "npmStaticResources",
    "Static resources copied into the npm package.",
  )

  /** 执行 pack 与 publish 时使用的 npm CLI。 */
  abstract val npmExecutable: Property<String>

  /** publish 使用的 Registry 根地址。 */
  abstract val registryUrl: Property<String>

  /** publish 使用的 npm dist-tag。 */
  abstract val publishTag: Property<String>

  /** publish 使用的 npm access。 */
  abstract val publishAccess: Property<String>
}

/** 创建静态 npm 包扩展并设置项目统一默认值。 */
fun Project.createNpmStaticPackageExtension(): NpmStaticPackageExtension {
  return extensions.create<NpmStaticPackageExtension>("npmStaticPackage").apply {
    packageName.convention(npmPackageNameFromProjectPath(path))
    resources.srcDir(layout.projectDirectory.dir("src/resources"))
    npmExecutable.convention("npm")
    registryUrl.convention("https://registry.npmjs.org")
    publishTag.convention("latest")
    publishAccess.convention("public")
  }
}

/** 把静态 npm 资源源集注册为 IDEA Resources Root，便于 Android Studio 正确展示目录类型。 */
fun Project.configureNpmStaticIdeaModel() {
  val extension = extensions.getByType<NpmStaticPackageExtension>()
  extensions.getByType<IdeaModel>().module.resourceDirs.addAll(extension.resources.srcDirs)
}

/**
 * 注册静态包的 prepare、pack 与幂等 publish 任务。
 *
 * 静态包不加入 Kotlin/JS distribution，也不携带 Runtime；任务名不带 `Js`，防止调用方误认为
 * 包内必须存在可执行 Module。
 */
fun Project.configureNpmStaticPackaging() {
  val extension = extensions.getByType<NpmStaticPackageExtension>()
  val packageVersion = version.toString()
  if (packageVersion == Project.DEFAULT_VERSION) {
    throw GradleException("$path must declare project.version when using manager.npmStatic.")
  }
  val preparePackage = tasks.register<PrepareNpmStaticPackageTask>("prepareNpmPackage") {
    group = "npm"
    description = "生成静态 npm 发布目录；不生成 tgz，也不访问 Registry。"
    sourceDirectories.from(extension.resources.srcDirs)
    outputDirectory.set(layout.buildDirectory.dir("npm/package"))
    packageName.set(extension.packageName.map(::validateNpmPackageName))
    this.packageVersion.set(packageVersion)
  }
  tasks.register<PackNpmJsPackageTask>("packNpmPackage") {
    group = "npm"
    description = "生成当前静态 npm 包的 tgz；不访问 Registry。"
    dependsOn(preparePackage)
    packageDirectory.set(preparePackage.flatMap { it.outputDirectory })
    destinationDirectory.set(layout.buildDirectory.dir("npm/tarball"))
    npmExecutable.set(extension.npmExecutable)
  }
  val prepareDebugBundle = tasks.register<PrepareDebugNpmBundleTask>("prepareDebugNpmBundle") {
    group = "npm"
    description = "比较 Registry，并把静态 npm 包汇总到根项目共享调试源。"
    dependsOn(preparePackage)
    packageDirectory.set(preparePackage.flatMap { it.outputDirectory })
    localPackageDirectories.from(preparePackage.flatMap { it.outputDirectory })
    npmExecutable.set(extension.npmExecutable)
    registryUrl.set(extension.registryUrl)
    workingDirectory.set(layout.buildDirectory.dir("npm/debug-bundle"))
    debugSourceDirectory.set(rootProject.layout.buildDirectory.dir("npm/debug-source"))
    manifestFile.set(layout.buildDirectory.file("npm/debug-bundle/manifest.json"))
    // 每次执行都重新比较 Registry，确保撤销本地改动时稳定 tgz 也能覆盖旧 debug tgz。
    outputs.upToDateWhen { false }
  }
  tasks.register<InstallAndroidDebugNpmBundleTask>("installAndroidDebugNpmBundle") {
    group = "npm"
    description = "准备统一 npm 调试源，将当前静态包安装到 Android 并重启应用。"
    // 安装阶段读取当前 Gradle 属性与本机 Android SDK，不复用 configuration cache。
    notCompatibleWithConfigurationCache("npm debug bundle installation depends on the local Android environment")
    dependsOn(prepareDebugBundle)
    manifestFile.set(prepareDebugBundle.flatMap { it.manifestFile })
    debugSourceDirectory.set(rootProject.layout.buildDirectory.dir("npm/debug-source"))
    applicationId.set(
      providers.gradleProperty("npmDebugApplicationId")
        .orElse("com.mredrock.cyxbs.test"),
    )
  }
  tasks.register<PublishNpmJsPackageTask>("publishNpmPackage") {
    group = "npm"
    description = "按精确版本 integrity 检查并按需发布当前静态 npm 包。"
    dependsOn(preparePackage)
    packageDirectory.set(preparePackage.flatMap { it.outputDirectory })
    npmExecutable.set(extension.npmExecutable)
    registryUrl.set(extension.registryUrl)
    publishTag.set(extension.publishTag)
    publishAccess.set(extension.publishAccess)
  }
}
