package npm

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * npm JavaScript 发布包的共享 Runtime 与项目 Module 分包配置。
 *
 * 默认将 Kotlin/JS 基础模块放到独立 Runtime 包；Runtime 包自身将 [bundleKotlinRuntime] 设为 true，
 * 其他业务包则在发布产物中移除这些模块，并把 Runtime 与跨项目相对 import 改写为 npm 请求。
 * 普通业务模块应用 `manager.npmJs` 后即可使用默认配置，只有切换 Registry、Runtime 版本或
 * Kotlin 工具链导致基础 Module 文件名变化时，才需要显式修改本扩展。
 */
abstract class NpmJsPackageExtension {
  /**
   * 当前模块发布到 Registry 的完整 npm 包名。
   *
   * 默认根据 Gradle project path 生成；会被 App、目录或其他长期发布包直接引用的模块应显式设置
   * 稳定坐标，避免以后移动 Gradle 模块时意外更换远端协议标识。
   */
  abstract val packageName: Property<String>

  /** 当前包是否就是共享 Runtime 包，设为 true 时保留所有 Kotlin/JS 基础模块。 */
  abstract val bundleKotlinRuntime: Property<Boolean>

  /** 业务包需要依赖的共享 Runtime npm 包名。 */
  abstract val runtimePackageName: Property<String>

  /** 业务包写入 package.json 的共享 Runtime 精确版本。 */
  abstract val runtimePackageVersion: Property<String>

  /** 由共享 Runtime 包提供的 Kotlin/JS Module 文件名。 */
  abstract val runtimeModuleFiles: SetProperty<String>

  /** 提供最终 JavaScript Module 的聚合 production library 目录。 */
  abstract val moduleSourceDirectory: DirectoryProperty

  /** 提供当前 npm 包名、版本、入口和类型声明的原始 production library 目录。 */
  abstract val packageMetadataDirectory: DirectoryProperty

  /** 除当前模块 production library 外，生成聚合 Module 所需的额外任务路径。 */
  abstract val additionalSourceTaskPaths: SetProperty<String>

  /** 执行 pack 与 publish 时使用的 npm CLI 可执行文件。 */
  abstract val npmExecutable: Property<String>

  /** publish 任务的 npm Registry 地址，可在切换内部仓库时统一覆盖。 */
  abstract val registryUrl: Property<String>

  /** publish 任务写入的 npm dist-tag。 */
  abstract val publishTag: Property<String>

  /** publish 任务使用的 npm access，作用域公开包默认为 public。 */
  abstract val publishAccess: Property<String>
}

/** 项目内 npm JavaScript Runtime 的稳定发布约定。 */
object NpmJsPackageDefaults {
  const val DISTRIBUTION_PROJECT_PATH = ":cyxbs-functions:code:npm:distribution"
  const val NPM_SCOPE = "@cyxbs-mobile"

  val RUNTIME_MODULE_FILES = setOf(
    "kotlin-kotlin-stdlib.mjs",
    "kotlin_org_jetbrains_kotlin_kotlin_dom_api_compat.mjs",
    "kotlinx-atomicfu.mjs",
    "kotlinx-coroutines-core.mjs",
    "kotlinx-serialization-kotlinx-serialization-core.mjs",
    "kotlinx-serialization-kotlinx-serialization-json.mjs",
  )
}

/**
 * 将 Gradle 模块路径转换为项目内唯一且可直接发布的 npm 包名。
 *
 * 例如 `:cyxbs-functions:code:npm:service-test:js-impl` 会得到
 * `@cyxbs-mobile/cyxbs-functions-code-npm-service-test-js-impl`。路径片段统一转为小写，非 npm
 * 安全字符折叠为 `-`，因此模块移动会被视为 npm 坐标变更。
 */
fun npmPackageNameFromProjectPath(projectPath: String): String {
  val normalizedPath = projectPath
    .trim(':')
    .lowercase()
    .replace(Regex("[^a-z0-9._~-]+"), "-")
    .trim('-')
  require(normalizedPath.isNotEmpty()) {
    "Cannot derive an npm package name from Gradle project path '$projectPath'."
  }
  return "${NpmJsPackageDefaults.NPM_SCOPE}/$normalizedPath"
}

/**
 * 校验项目发布坐标符合统一 scope 与 npm 小写包名约定。
 *
 * 显式稳定包名会进入 KSP 协议、依赖图和客户端缓存键，因此在 Gradle 配置期拒绝错误坐标，避免
 * 直到 pack 或 publish 阶段才发现不同产物使用了不一致的名称。
 *
 * @return 未修改的 [packageName]，便于配置代码在校验后直接赋值。
 * @throws GradleException 包名不属于项目 scope、包含非法字符或超过 npm 长度限制。
 */
fun validateNpmPackageName(packageName: String): String {
  if (packageName.length > MAX_NPM_PACKAGE_NAME_LENGTH ||
    !NPM_PACKAGE_NAME_REGEX.matches(packageName)
  ) {
    throw GradleException(
      "npm package name '$packageName' must match " +
          "'${NpmJsPackageDefaults.NPM_SCOPE}/[a-z0-9][a-z0-9._~-]*' and contain at most " +
          "$MAX_NPM_PACKAGE_NAME_LENGTH characters.",
    )
  }
  return packageName
}

/**
 * 在业务模块脚本执行前创建可覆盖的 npm 包配置。
 *
 * 这里只建立 Provider 约定，不读取 Kotlin/JS 任务；[configureNpmJsPackaging] 必须等目标和
 * production distribution 配置完成后再注册打包任务。
 */
fun Project.createNpmJsPackageExtension(): NpmJsPackageExtension {
  val packageProject = this
  val distributionProject = if (path == NpmJsPackageDefaults.DISTRIBUTION_PROJECT_PATH) {
    null
  } else {
    project(NpmJsPackageDefaults.DISTRIBUTION_PROJECT_PATH)
  }
  val runtimeProject = distributionProject ?: packageProject
  return extensions.create<NpmJsPackageExtension>("npmJsPackage").apply {
    packageName.convention(npmPackageNameFromProjectPath(path))
    bundleKotlinRuntime.convention(false)
    if (distributionProject == null) {
      runtimePackageName.convention(packageName)
    } else {
      // 延迟读取 Runtime 项目的显式坐标，避免它移动模块或覆盖包名后业务包仍依赖旧路径坐标。
      runtimePackageName.convention(runtimeProject.provider {
        runtimeProject.extensions.getByType<NpmJsPackageExtension>().packageName.get()
      })
    }
    // Provider 延迟读取 distribution 的 project.version，避免插件应用阶段仍得到 unspecified。
    runtimePackageVersion.convention(runtimeProject.provider {
      runtimeProject.version.toString().also { runtimeVersion ->
        require(runtimeVersion != Project.DEFAULT_VERSION) {
          "${runtimeProject.path} must declare project.version before npm packaging."
        }
      }
    })
    runtimeModuleFiles.convention(NpmJsPackageDefaults.RUNTIME_MODULE_FILES)
    moduleSourceDirectory.convention(layout.buildDirectory.dir("dist/js/productionLibrary"))
    packageMetadataDirectory.convention(layout.buildDirectory.dir("dist/js/productionLibrary"))
    additionalSourceTaskPaths.convention(emptySet())
    npmExecutable.convention("npm")
    registryUrl.convention("https://registry.npmjs.org")
    publishTag.convention("latest")
    publishAccess.convention("public")
    if (distributionProject != null) {
      // 业务包必须与 Runtime 从同一次 production DCE 结果切分，避免跨包 mangled ABI 不一致。
      moduleSourceDirectory.set(
        distributionProject.layout.buildDirectory.dir("dist/js/productionLibrary"),
      )
      additionalSourceTaskPaths.add(
        "${distributionProject.path}:jsNodeProductionLibraryDistribution",
      )
    }
  }
}

/**
 * 为应用当前 manager 插件的项目注册统一 npm 分包任务。
 *
 * 具体模块仍通过 Kotlin/JS 的 production library 生成原始产物；[PrepareNpmJsPackageTask] 再生成
 * 可直接执行 npm pack 或 npm publish 的最终目录。业务模块会自动注册到全局
 * distribution，其 pack 与 publish 任务也会分别保证 Runtime 已打包与已发布。
 *
 * 模块关系如下，其中箭头表示 Gradle 的 `jsMainApi` 依赖：
 * ```text
 * distribution ──> 所有应用 manager.npmJs 的业务模块 ──> 业务使用的 js-bridge
 * ```
 * 这里是 distribution 聚合业务模块，而不是业务模块依赖 distribution。业务模块仅在执行 npm
 * 打包任务时复用 distribution 的聚合 production 产物，因此不会形成 Kotlin 源码依赖环。
 *
 * 对外任务按副作用从小到大分为：
 * ```text
 * prepareNpmJsPackage  生成可发布目录；不生成 tgz，不访问网络
 *          │
 *          ▼
 * packNpmJsPackage     生成 Runtime、项目依赖与当前业务包的独立 tgz；不访问 Registry
 *
 * installDebugNpmBundle 生成 debug tgz，ADB 原子替换 App 私有源并重启；不发布到 Registry
 *
 * publishNpmJsPackage  按依赖拓扑检查 Runtime、项目依赖与当前包；仅发布远端缺失版本
 * ```
 * 日常发布只需执行 `publishNpmJsPackage`；检查分包内容时执行 prepare，CI 留存制品执行 pack，
 * Android 真机验证执行 install。所有待发布包都会比较本地与远端精确版本的 integrity：一致时
 * 复用，缺失时发布，不一致时要求提升版本。`ensureNpmJsRuntimePublished` 是 publish 的内部依赖，
 * 不是业务方需要直接调用的入口。
 */
fun Project.configureNpmJsPackaging() {
  val packageProject = this
  val packageRegistry = npmJsPackageRegistry().apply {
    // manager.npmJs 与 manager.npmJsBridge 都注册到同一张表，供切包任务识别项目边界。
    register(packageProject)
  }
  val distributionProject = if (path == NpmJsPackageDefaults.DISTRIBUTION_PROJECT_PATH) {
    null
  } else {
    project(NpmJsPackageDefaults.DISTRIBUTION_PROJECT_PATH)
  }
  val extension = extensions.getByType<NpmJsPackageExtension>()
  val preparePackage = tasks.register<PrepareNpmJsPackageTask>("prepareNpmJsPackage") {
    group = "npm"
    description = "本地生成完成 Runtime 分包的 npm 发布目录；不生成 tgz，也不访问网络。"
    dependsOn("jsNodeProductionLibraryDistribution")
    dependsOn(extension.additionalSourceTaskPaths)
    moduleSourceDirectory.set(extension.moduleSourceDirectory)
    packageMetadataDirectory.set(extension.packageMetadataDirectory)
    modulePackageMetadataFiles.from(packageRegistry.packageMetadataFiles)
    outputDirectory.set(layout.buildDirectory.dir("npm/package"))
    bundleKotlinRuntime.set(extension.bundleKotlinRuntime)
    runtimePackageName.set(extension.runtimePackageName)
    runtimePackageVersion.set(extension.runtimePackageVersion)
    runtimeModuleFiles.set(extension.runtimeModuleFiles)
  }
  packageRegistry.preparedPackageDirectories.from(
    preparePackage.flatMap { it.outputDirectory },
  )
  packageRegistry.preparedPackageDirectories.builtBy(preparePackage)
  val packPackage = tasks.register<PackNpmJsPackageTask>("packNpmJsPackage") {
    group = "npm"
    description = "按依赖拓扑生成 Runtime、项目依赖与当前 npm 包的独立 tgz；不访问 Registry。"
    dependsOn(preparePackage)
    packageDirectory.set(preparePackage.flatMap { it.outputDirectory })
    destinationDirectory.set(layout.buildDirectory.dir("npm/tarball"))
    npmExecutable.set(extension.npmExecutable)
  }
  val publishPackage = tasks.register<PublishNpmJsPackageTask>("publishNpmJsPackage") {
    group = "npm"
    description = "按依赖拓扑检查并发布共享 Runtime、项目依赖与当前包。"
    dependsOn(preparePackage)
    packageDirectory.set(preparePackage.flatMap { it.outputDirectory })
    npmExecutable.set(extension.npmExecutable)
    registryUrl.set(extension.registryUrl)
    publishTag.set(extension.publishTag)
    publishAccess.set(extension.publishAccess)
  }
  if (distributionProject != null) {
    // distribution 也使用同一插件。等待其插件完成应用，可同时避免业务模块与 distribution
    // 的配置先后顺序影响依赖注册及任务查找。
    distributionProject.pluginManager.withPlugin("manager.npmJs") {
      // 每个业务模块都作为 distribution 的 jsMainApi 依赖进入同一次 Kotlin/JS production DCE。
      // 因而公共 Kotlin Runtime 和跨模块 mangled 符号只生成一次，之后才能安全切成多个 npm 包。
      val distributionDependency = distributionProject.dependencies.project(
        mapOf("path" to packageProject.path),
      )
      distributionProject.dependencies.add("jsMainApi", distributionDependency)

      val prepareRuntime = distributionProject.tasks.named<PrepareNpmJsPackageTask>(
        "prepareNpmJsPackage",
      )
      val packRuntime = distributionProject.tasks.named<PackNpmJsPackageTask>(
        "packNpmJsPackage",
      )
      packPackage.configure {
        // 从任一业务模块执行 pack 时，同时产出共享 Runtime tgz。两份 tgz 各自保存在所属模块的
        // build/npm/tarball 下，便于本地安装时验证与线上发布相同的依赖组合。
        dependsOn(packRuntime)
      }
      val ensureRuntime = packageProject.tasks.register<EnsureNpmJsRuntimePublishedTask>(
        "ensureNpmJsRuntimePublished",
      ) {
        // 不设置 group，避免它出现在业务方的 npm 任务入口中；publish 会自动依赖它。
        description = "内部任务：联网校验并按需发布当前业务包依赖的共享 Kotlin/JS Runtime。"
        dependsOn(prepareRuntime)
        runtimePackageDirectory.set(prepareRuntime.flatMap { it.outputDirectory })
        runtimePackageName.set(extension.runtimePackageName)
        runtimePackageVersion.set(extension.runtimePackageVersion)
        npmExecutable.set(extension.npmExecutable)
        registryUrl.set(extension.registryUrl)
        publishTag.set(extension.publishTag)
        publishAccess.set(extension.publishAccess)
      }
      publishPackage.configure {
        // Gradle 依赖保证 Runtime 检查发生在业务包 publish 之前。若 Runtime 同版本内容不一致，
        // ensure 会直接失败，当前业务包不会被上传，避免发布引用错误 ABI 的版本。
        dependsOn(ensureRuntime)
      }
      val installDebugBundle = packageProject.tasks.register<InstallDebugNpmBundleTask>(
        "installDebugNpmBundle",
      ) {
        group = "npm"
        description = "比较 Registry 稳定包；存在变化时生成 debug bundle、ADB 覆盖并重启。"
        // 注入阶段需要读取当前 Gradle 属性与本机 Android SDK，不复用该任务的 configuration cache。
        notCompatibleWithConfigurationCache("npm debug bundle installation depends on the local Android environment")
        dependsOn(preparePackage, prepareRuntime)
        packageDirectory.set(preparePackage.flatMap { it.outputDirectory })
        localPackageDirectories.from(packageRegistry.preparedPackageDirectories)
        runtimePackageDirectory.set(prepareRuntime.flatMap { it.outputDirectory })
        runtimePackageName.set(extension.runtimePackageName)
        runtimeStableVersion.set(extension.runtimePackageVersion)
        npmExecutable.set(extension.npmExecutable)
        registryUrl.set(extension.registryUrl)
        applicationId.set(
          packageProject.providers.gradleProperty("npmDebugApplicationId")
            .orElse("com.mredrock.cyxbs.test"),
        )
        workingDirectory.set(packageProject.layout.buildDirectory.dir("npm/debug-bundle"))
        // 每次执行都需要重新查询 Registry；任务内部会在所有候选包均未变化时跳过 ADB 与重启。
        outputs.upToDateWhen { false }
      }
      packageProject.gradle.projectsEvaluated {
        // 当前包改为独立 npm 依赖后，pack/publish 必须先完成直接项目依赖；依赖任务自身会继续
        // 处理下一层，从而形成与 Gradle 源码依赖一致的发布拓扑，而不会发布无关语言包。
        directNpmJsPackageDependencies().forEach { dependencyProject ->
          val dependencyPrepare = dependencyProject.tasks.named<PrepareNpmJsPackageTask>(
            "prepareNpmJsPackage",
          )
          packPackage.configure {
            dependsOn(dependencyProject.tasks.named<PackNpmJsPackageTask>("packNpmJsPackage"))
          }
          publishPackage.configure {
            dependsOn(
              dependencyProject.tasks.named<PublishNpmJsPackageTask>("publishNpmJsPackage"),
            )
          }
          // 注册表包含所有模块目录，但任务执行时只遍历入口可达包；显式依赖保证直接依赖的
          // prepare 已进入当前任务图，其他目录的 builtBy 负责补齐更深层依赖。
          installDebugBundle.configure {
            dependsOn(dependencyPrepare)
          }
        }
      }
    }
  }
}

/**
 * 返回当前 JS 产物直接依赖、且同样使用项目 npm 发布约定的 Gradle 模块。
 *
 * 只读取会进入 JS main 产物的配置；宿主 noWebMain 依赖不会误入 npm 发布拓扑。
 */
private fun Project.directNpmJsPackageDependencies(): Set<Project> {
  return NPM_JS_MAIN_CONFIGURATIONS.asSequence()
    .mapNotNull(configurations::findByName)
    .flatMap { configuration ->
      configuration.dependencies.asSequence().filterIsInstance<ProjectDependency>()
    }
    .map { dependency -> rootProject.project(dependency.path) }
    .filter { dependencyProject ->
      dependencyProject.pluginManager.hasPlugin("manager.npmJs") ||
          dependencyProject.pluginManager.hasPlugin("manager.npmJsBridge")
    }
    .toCollection(linkedSetOf())
}

private val NPM_JS_MAIN_CONFIGURATIONS = setOf(
  "commonMainApi",
  "commonMainImplementation",
  "jsMainApi",
  "jsMainImplementation",
)

private const val MAX_NPM_PACKAGE_NAME_LENGTH = 214
private val NPM_PACKAGE_NAME_REGEX = Regex(
  "^${Regex.escape(NpmJsPackageDefaults.NPM_SCOPE)}/[a-z0-9][a-z0-9._~-]*$",
)
