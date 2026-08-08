package npm

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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
 *                        Runtime import 改写为 npm 子路径
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
    val declaredMain = packageJson.string("main")
      ?: throw GradleException("npm package.json does not declare a main entry.")
    val declaredTypes = packageJson.string("types")
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

    val includedModules = if (bundleKotlinRuntime.get()) {
      existingRuntimeFiles
    } else {
      collectReachableBusinessModules(
        moduleRoot = moduleRoot,
        entryModule = declaredMain,
        runtimeFiles = existingRuntimeFiles,
      )
    }

    fileSystemOperations.delete {
      delete(outputRoot)
    }
    fileSystemOperations.copy {
      from(moduleRoot)
      into(outputRoot)
      include(includedModules)
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
      rewriteRuntimeImports(outputRoot, existingRuntimeFiles)
    }
    updatePackageJson(outputRoot, existingRuntimeFiles.isNotEmpty())
    validateOutput(outputRoot)
  }

  /**
   * 从业务 main 入口遍历 Kotlin/JS 生成的相对 ESM import。
   *
   * 遇到 Runtime Module 时终止该分支，因为它会由独立 npm 包提供；其余相对 Module 必须存在于
   * 聚合目录且位于目录内部。
   */
  private fun collectReachableBusinessModules(
    moduleRoot: File,
    entryModule: String,
    runtimeFiles: Set<String>,
  ): Set<String> {
    val root = moduleRoot.canonicalFile
    val pending = ArrayDeque<String>().apply { add(entryModule) }
    val result = linkedSetOf<String>()
    while (pending.isNotEmpty()) {
      val modulePath = pending.removeFirst()
      if (modulePath.substringAfterLast('/') in runtimeFiles || !result.add(modulePath)) continue
      val module = root.resolve(modulePath).canonicalFile
      ensureInsideRoot(root, module, modulePath)
      if (!module.isFile) {
        throw GradleException("Aggregated Kotlin/JS Module '$modulePath' does not exist.")
      }
      relativeImports(module.readText()).forEach { requestedName ->
        val dependency = module.parentFile.resolve(requestedName).canonicalFile
        ensureInsideRoot(root, dependency, requestedName)
        pending.add(dependency.relativeTo(root).invariantSeparatorsPath)
      }
    }
    return result
  }

  /** 将 Kotlin 编译器生成的相对 Runtime import 改写为 npm 包子路径。 */
  private fun rewriteRuntimeImports(outputRoot: File, runtimeFiles: Set<String>) {
    val packageName = runtimePackageName.get()
    outputRoot.walkTopDown()
      .filter { it.isFile && it.extension == "mjs" }
      .forEach { module ->
        val original = module.readText()
        val rewritten = RELATIVE_IMPORT.replace(original) { match ->
          val requestedName = match.groupValues[1]
          val runtimeFile = requestedName.substringAfterLast('/')
          if (runtimeFile in runtimeFiles) {
            match.value.replace(requestedName, "$packageName/$runtimeFile")
          } else {
            match.value
          }
        }
        if (rewritten != original) {
          module.writeText(rewritten)
        }
      }
  }

  /** 写入分包后的 main、类型声明和共享 Runtime 精确依赖。 */
  private fun updatePackageJson(outputRoot: File, usesRuntime: Boolean) {
    val packageJsonFile = outputRoot.resolve(PACKAGE_JSON)
    val packageJson = readPackageJson(packageJsonFile)
    if (bundleKotlinRuntime.get()) {
      packageJson.addProperty("main", KOTLIN_STDLIB_MODULE)
      packageJson.remove("types")
    } else if (usesRuntime) {
      val dependencies = packageJson.getAsJsonObject("dependencies") ?: JsonObject()
      dependencies.addProperty(runtimePackageName.get(), runtimePackageVersion.get())
      packageJson.add("dependencies", dependencies)
    }
    packageJsonFile.writeText(
      GsonBuilder().setPrettyPrinting().create().toJson(packageJson) + "\n",
    )
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

  private companion object {
    const val PACKAGE_JSON = "package.json"
    const val KOTLIN_STDLIB_MODULE = "kotlin-kotlin-stdlib.mjs"
    val RELATIVE_IMPORT = Regex(
      """(?:\bfrom\s*|\bimport\s*(?:\(\s*)?)["'](\.{1,2}/[^"']+\.mjs)["']""",
    )
  }
}
