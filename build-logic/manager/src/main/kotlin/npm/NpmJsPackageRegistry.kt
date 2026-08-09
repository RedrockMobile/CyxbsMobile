package npm

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import javax.inject.Inject

/**
 * 收集当前构建中所有可独立发布的 npm JS 模块元数据。
 *
 * 注册表只保存各模块 production `package.json` 的惰性文件引用。真正的包名、版本和 main 文件
 * 由 [PrepareNpmJsPackageTask] 执行时读取，避免在 Gradle 配置阶段提前访问尚未生成的构建产物。
 */
internal abstract class NpmJsPackageRegistry @Inject constructor(
  objects: ObjectFactory,
) {

  /** 所有可发布模块的 production package.json；后注册的模块也会进入已配置任务的输入。 */
  val packageMetadataFiles: ConfigurableFileCollection = objects.fileCollection()

  /**
   * 所有可发布模块经过切分后的目录。
   *
   * ADB 调试任务会从入口 package.json 遍历这组目录，只处理真实可达的本地项目依赖。
   */
  val preparedPackageDirectories: ConfigurableFileCollection = objects.fileCollection()

  /**
   * 注册 [project] 的 npm 发布元数据。
   *
   * 文件由模块的 Kotlin/JS production 任务生成，缺失时 Gradle 输入校验会阻止切包继续执行，
   * 避免因模块边界不完整而重新把依赖代码打回上层业务包。
   */
  fun register(project: Project) {
    packageMetadataFiles.from(
      project.layout.buildDirectory.file(PRODUCTION_PACKAGE_JSON).map { metadataFile ->
        metadataFile.asFile
      },
    )
    // 输入文件与生产任务建立依赖，保证从 clean 状态执行任一 prepare 都能获得完整所有权表。
    packageMetadataFiles.builtBy(project.tasks.named(PRODUCTION_DISTRIBUTION_TASK))
  }

  private companion object {
    const val PRODUCTION_PACKAGE_JSON = "dist/js/productionLibrary/package.json"
    const val PRODUCTION_DISTRIBUTION_TASK = "jsNodeProductionLibraryDistribution"
  }
}

/** 返回根项目唯一的 npm 模块注册表，使不同约定插件共享同一份模块所有权信息。 */
internal fun Project.npmJsPackageRegistry(): NpmJsPackageRegistry {
  return rootProject.extensions.findByType<NpmJsPackageRegistry>()
    ?: rootProject.extensions.create<NpmJsPackageRegistry>("npmJsPackageRegistry")
}
