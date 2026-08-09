package npm

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject

/**
 * 从同一份 Kotlin/JS production library 中切分共享 Runtime 与独立业务 npm 包。
 *
 * Module 必须来自聚合 production 编译，保证所有分包使用相同的 DCE 符号集合和 mangled ABI。
 * Runtime 包只保留基础 Module；业务包从自己的 main 入口遍历相对 import，仅保留可达业务 Module，
 * 再将 Runtime import 改写为 npm 子路径。
 *
 * 任务执行过程如下：
 * ```text
 * 读取当前模块 package.json，得到包名、版本、main 与类型声明
 *                 │
 *                 ▼
 * 从 main 遍历聚合产物中的静态 import、re-export 和字面量动态 import()
 *                 │
 *       ┌─────────┴─────────┐
 *       ▼                   ▼
 * Runtime 包            业务包
 * 保留基础 Module        保留入口可达的业务 Module
 *       │                Runtime import 改写为 npm 子路径
 *       │                跨项目 import 改写为独立 npm 包
 *       └─────────┬─────────┘
 *                 ▼
 * 校验 main 与相对依赖，输出到 build/npm/package
 * ```
 *
 * 本任务只进行本地文件转换：不会调用 npm CLI、不会生成 tgz，也不会访问 Registry。
 * 输出目录既是 [PackNpmJsPackageTask] 和 [PublishNpmJsPackageTask] 的输入，也可以供开发者直接
 * 检查最终会被发布的文件、package.json 与 import 路径。
 */
@CacheableTask
abstract class PrepareNpmJsPackageTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val moduleSourceDirectory: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageMetadataDirectory: DirectoryProperty

  /**
   * 所有可独立发布项目的 production package.json。
   *
   * 任务通过其中的 main、name 和 version 建立 Module 所有权，禁止把其他项目的 Module
   * 重新复制进当前 npm 包。
  */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val modulePackageMetadataFiles: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:Input
  abstract val bundleKotlinRuntime: Property<Boolean>

  @get:Input
  abstract val runtimePackageName: Property<String>

  @get:Input
  abstract val runtimePackageVersion: Property<String>

  @get:Input
  abstract val runtimeModuleFiles: SetProperty<String>

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  /**
   * 生成最终 npm 目录。
   *
   * 任务只清空自己声明的输出目录，不修改 Kotlin/JS 原始 distribution。入口缺失、相对依赖越界、
   * Runtime import 残留或切分后依赖缺失都会立即失败，避免发布不可执行的包。
   *
   * @throws GradleException 当聚合产物、入口、Runtime Module 或任一相对依赖不完整时抛出；
   * 此时不会继续执行后续 pack/publish 任务。
   */
  @TaskAction
  fun preparePackage() {
    val moduleRoot = moduleSourceDirectory.get().asFile
    val metadataRoot = packageMetadataDirectory.get().asFile
    val outputRoot = outputDirectory.get().asFile
    val packageJson = readPackageJson(metadataRoot.resolve(PACKAGE_JSON))
    val currentPackageName = packageJson.string("name")
      ?: throw GradleException("npm package.json does not declare a package name.")
    val declaredMain = packageJson.string("main")
      ?: throw GradleException("npm package.json does not declare a main entry.")
    val declaredTypes = packageJson.string("types")
    val modulePackages = readModulePackages(modulePackageMetadataFiles.files)
    val configuredRuntimeFiles = runtimeModuleFiles.get()
    val existingRuntimeFiles = configuredRuntimeFiles.filterTo(linkedSetOf()) {
      moduleRoot.resolve(it).isFile
    }
    if (existingRuntimeFiles.isEmpty()) {
      throw GradleException(
        "No configured Kotlin/JS Runtime Module was found in '$moduleRoot'. " +
            "Update npmJsPackage.runtimeModuleFiles when the Kotlin toolchain changes.",
      )
    }
    if (bundleKotlinRuntime.get() && existingRuntimeFiles != configuredRuntimeFiles) {
      val missing = configuredRuntimeFiles - existingRuntimeFiles
      throw GradleException("Aggregated Kotlin/JS Runtime is missing Modules: $missing")
    }

    val packageGraph = if (bundleKotlinRuntime.get()) {
      NpmPackageGraph(includedModules = existingRuntimeFiles)
    } else {
      collectReachableBusinessModules(
        moduleRoot = moduleRoot,
        entryModule = declaredMain,
        runtimeFiles = existingRuntimeFiles,
        currentPackageName = currentPackageName,
        modulePackages = modulePackages,
      )
    }

    fileSystemOperations.delete {
      delete(outputRoot)
    }
    fileSystemOperations.copy {
      from(moduleRoot)
      into(outputRoot)
      include(packageGraph.includedModules)
    }
    fileSystemOperations.copy {
      from(metadataRoot)
      into(outputRoot)
      include(PACKAGE_JSON)
      if (!bundleKotlinRuntime.get() && declaredTypes != null) {
        include(declaredTypes)
      }
    }

    if (!bundleKotlinRuntime.get()) {
      rewriteExternalImports(
        outputRoot = outputRoot,
        moduleRoot = moduleRoot,
        runtimeFiles = existingRuntimeFiles,
        currentPackageName = currentPackageName,
        modulePackages = modulePackages,
      )
    }
    updatePackageJson(
      outputRoot = outputRoot,
      usesRuntime = existingRuntimeFiles.isNotEmpty(),
      externalPackages = packageGraph.externalPackages,
    )
    validateOutput(outputRoot)
  }

  /**
   * 从业务 main 入口遍历 Kotlin/JS 生成的相对 ESM import。
   *
   * 遇到 Runtime Module 或其他已注册项目的入口 Module 时终止该分支，因为它们会由独立 npm
   * 包提供；其余相对 Module 必须存在于聚合目录且位于目录内部。
   */
  private fun collectReachableBusinessModules(
    moduleRoot: File,
    entryModule: String,
    runtimeFiles: Set<String>,
    currentPackageName: String,
    modulePackages: Map<String, NpmModulePackage>,
  ): NpmPackageGraph {
    val root = moduleRoot.canonicalFile
    val pending = ArrayDeque<String>().apply { add(entryModule) }
    val result = linkedSetOf<String>()
    val externalPackages = linkedMapOf<String, String>()
    while (pending.isNotEmpty()) {
      val modulePath = pending.removeFirst()
      if (modulePath.substringAfterLast('/') in runtimeFiles) continue
      val module = root.resolve(modulePath).canonicalFile
      ensureInsideRoot(root, module, modulePath)
      if (!module.isFile) {
        throw GradleException("Aggregated Kotlin/JS Module '$modulePath' does not exist.")
      }
      val owner = modulePackages[modulePath]
      if (owner != null && owner.packageName != currentPackageName) {
        val previousVersion = externalPackages.put(owner.packageName, owner.version)
        if (previousVersion != null && previousVersion != owner.version) {
          throw GradleException(
            "npm package '${owner.packageName}' has conflicting versions " +
                "'$previousVersion' and '${owner.version}'.",
          )
        }
        continue
      }
      if (!result.add(modulePath)) continue
      relativeImports(module.readText()).forEach { requestedName ->
        val dependency = module.parentFile.resolve(requestedName).canonicalFile
        ensureInsideRoot(root, dependency, requestedName)
        pending.add(dependency.relativeTo(root).invariantSeparatorsPath)
      }
    }
    return NpmPackageGraph(
      includedModules = result,
      externalPackages = externalPackages,
    )
  }

  /**
   * 将聚合产物中的 Runtime 与跨项目相对 import 改写为 npm 请求。
   *
   * 当前项目内部的相对 import 保持不变；目标属于其他已注册项目时使用其包名，并由
   * [updatePackageJson] 写入精确版本。静态 import、re-export 和字面量动态 import() 共用同一
   * 正则，因此不会因加载方式不同重新携带依赖项目代码。
   */
  private fun rewriteExternalImports(
    outputRoot: File,
    moduleRoot: File,
    runtimeFiles: Set<String>,
    currentPackageName: String,
    modulePackages: Map<String, NpmModulePackage>,
  ) {
    val sourceRoot = moduleRoot.canonicalFile
    outputRoot.walkTopDown()
      .filter { it.isFile && it.extension == "mjs" }
      .forEach { module ->
        val modulePath = module.relativeTo(outputRoot).invariantSeparatorsPath
        val sourceModule = sourceRoot.resolve(modulePath).canonicalFile
        val original = module.readText()
        val rewritten = RELATIVE_IMPORT.replace(original) { match ->
          val requestedName = match.groupValues[1]
          val dependency = sourceModule.parentFile.resolve(requestedName).canonicalFile
          ensureInsideRoot(sourceRoot, dependency, requestedName)
          val dependencyPath = dependency.relativeTo(sourceRoot).invariantSeparatorsPath
          val owner = modulePackages[dependencyPath]
          val replacement = when {
            dependency.name in runtimeFiles -> {
              "${runtimePackageName.get()}/${dependency.name}"
            }
            owner != null && owner.packageName != currentPackageName -> {
              owner.packageName
            }
            else -> null
          }
          replacement?.let { match.value.replace(requestedName, it) } ?: match.value
        }
        if (rewritten != original) {
          module.writeText(rewritten)
        }
      }
  }

  /**
   * 写入分包后的 main、类型声明、共享 Runtime 与直接跨项目依赖的精确版本。
   *
   * 聚合 production 的 package.json 会包含所有业务模块声明的 npm 依赖。Runtime 包只复制显式配置的
   * Kotlin/JS 基础 Module，因此必须删除这些聚合依赖；否则任意业务包的第三方依赖都会成为所有入口的
   * 伪传递依赖。开发期的 `devDependencies` 不参与客户端解析，仍按原元数据保留。
   */
  private fun updatePackageJson(
    outputRoot: File,
    usesRuntime: Boolean,
    externalPackages: Map<String, String>,
  ) {
    val packageJsonFile = outputRoot.resolve(PACKAGE_JSON)
    val packageJson = readPackageJson(packageJsonFile)
    if (bundleKotlinRuntime.get()) {
      packageJson.addProperty("main", KOTLIN_STDLIB_MODULE)
      packageJson.remove("types")
      RUNTIME_DEPENDENCY_FIELDS.forEach(packageJson::remove)
    } else {
      val dependencies = packageJson.getAsJsonObject("dependencies") ?: JsonObject()
      if (usesRuntime) {
        addExactDependency(
          dependencies = dependencies,
          packageName = runtimePackageName.get(),
          version = runtimePackageVersion.get(),
        )
      }
      externalPackages.toSortedMap().forEach { (packageName, version) ->
        addExactDependency(dependencies, packageName, version)
      }
      packageJson.add("dependencies", dependencies)
    }
    packageJsonFile.writeText(
      GsonBuilder().setPrettyPrinting().create().toJson(packageJson) + "\n",
    )
  }

  /** 写入精确依赖；已有不一致版本时拒绝静默覆盖 Gradle 生成的元数据。 */
  private fun addExactDependency(
    dependencies: JsonObject,
    packageName: String,
    version: String,
  ) {
    val existingVersion = dependencies.string(packageName)
    if (existingVersion != null && existingVersion != version) {
      throw GradleException(
        "npm dependency '$packageName' already uses '$existingVersion', expected '$version'.",
      )
    }
    dependencies.addProperty(packageName, version)
  }

  /** 从所有已注册模块元数据建立聚合 .mjs 文件到 npm 坐标的唯一映射。 */
  private fun readModulePackages(
    metadataFiles: Set<File>,
  ): Map<String, NpmModulePackage> {
    val result = linkedMapOf<String, NpmModulePackage>()
    val packageNames = linkedMapOf<String, String>()
    metadataFiles.sortedBy(File::getAbsolutePath).forEach { metadataFile ->
      val packageJson = readPackageJson(metadataFile)
      val main = packageJson.string("main")
        ?: throw GradleException("'$metadataFile' does not declare an npm main entry.")
      val packageName = packageJson.string("name")
        ?: throw GradleException("'$metadataFile' does not declare an npm package name.")
      val version = packageJson.string("version")
        ?: throw GradleException("'$metadataFile' does not declare an npm package version.")
      val packageModule = NpmModulePackage(packageName, version)
      val previousOwner = result.put(main, packageModule)
      if (previousOwner != null && previousOwner != packageModule) {
        throw GradleException(
          "Kotlin/JS Module '$main' belongs to both '${previousOwner.packageName}' " +
              "and '$packageName'.",
        )
      }
      val previousMain = packageNames.put(packageName, main)
      if (previousMain != null && previousMain != main) {
        throw GradleException(
          "npm package '$packageName' declares multiple main Modules: '$previousMain' and '$main'.",
        )
      }
    }
    return result
  }

  /** 校验最终入口与所有相对 import 均能在当前分包中解析。 */
  private fun validateOutput(outputRoot: File) {
    val packageJson = readPackageJson(outputRoot.resolve(PACKAGE_JSON))
    val main = packageJson.string("main")
      ?: throw GradleException("npm package.json does not declare a main entry.")
    if (!outputRoot.resolve(main).isFile) {
      throw GradleException("npm main entry '$main' is missing from the prepared package.")
    }
    outputRoot.walkTopDown()
      .filter { it.isFile && it.extension == "mjs" }
      .forEach { module ->
        relativeImports(module.readText()).forEach { requestedName ->
          val dependency = module.parentFile.resolve(requestedName).canonicalFile
          ensureInsideRoot(outputRoot.canonicalFile, dependency, requestedName)
          if (!dependency.isFile) {
            val modulePath = module.relativeTo(outputRoot).invariantSeparatorsPath
            throw GradleException(
              "Module '$modulePath' imports missing relative Module '$requestedName'.",
            )
          }
        }
      }
  }

  /** 读取并校验 Gradle/Kotlin 生成的 package.json。 */
  private fun readPackageJson(file: File): JsonObject {
    if (!file.isFile) throw GradleException("Kotlin/JS distribution has no package.json.")
    return try {
      JsonParser.parseString(file.readText()).asJsonObject
    } catch (throwable: Throwable) {
      throw GradleException("Kotlin/JS distribution has an invalid package.json.", throwable)
    }
  }

  /** 拒绝通过 ../ 等相对路径访问聚合或输出目录之外的文件。 */
  private fun ensureInsideRoot(root: File, file: File, requestedName: String) {
    if (!file.toPath().startsWith(root.toPath())) {
      throw GradleException("JavaScript Module '$requestedName' escapes package root.")
    }
  }

  /** 提取静态 import、re-export 与动态 import() 中的相对 mjs 请求。 */
  private fun relativeImports(source: String): Sequence<String> {
    return RELATIVE_IMPORT.findAll(source).map { it.groupValues[1] }
  }

  private fun JsonObject.string(name: String): String? {
    return get(name)?.takeUnless { it.isJsonNull }?.asString
  }

  /** 当前包从入口遍历得到的自有 Module 与直接外部项目依赖。 */
  private data class NpmPackageGraph(
    val includedModules: Set<String>,
    val externalPackages: Map<String, String> = emptyMap(),
  )

  /** 一个聚合 Kotlin/JS Module 对应的独立 npm 包坐标。 */
  private data class NpmModulePackage(
    val packageName: String,
    val version: String,
  )

  private companion object {
    const val PACKAGE_JSON = "package.json"
    const val KOTLIN_STDLIB_MODULE = "kotlin-kotlin-stdlib.mjs"
    val RUNTIME_DEPENDENCY_FIELDS = setOf(
      "dependencies",
      "peerDependencies",
      "optionalDependencies",
      "bundledDependencies",
      "bundleDependencies",
    )
    val RELATIVE_IMPORT = Regex(
      """(?:\bfrom\s*|\bimport\s*(?:\(\s*)?)["'](\.{1,2}/[^"']+\.mjs)["']""",
    )
  }
}
