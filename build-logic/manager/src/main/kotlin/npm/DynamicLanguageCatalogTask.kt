package npm

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

/**
 * 根据各语言 Project 的自描述配置生成确定性的 `catalog.json`。
 *
 * ```text
 * language Project
 *   ├─ npmJsPackage.packageName ─┐
 *   └─ npmJsLanguage { ... } ────┼─> 冻结描述 ─> 校验/排序 ─> catalog.json
 *                                └─> publishNpmJsPackage ─> publishNpmPackage
 * ```
 *
 * [languageDescriptors] 的 key 是 Gradle project path，value 是配置期冻结的描述 JSON；任务动作
 * 不再访问其他 Project，因而输入变化可被 Gradle 正确追踪。
 */
@CacheableTask
abstract class GenerateDynamicLanguageCatalogTask : DefaultTask() {
  /** Project path 到完整语言描述的映射。 */
  @get:Input
  abstract val languageDescriptors: MapProperty<String, String>

  /** 仅包含生成后 catalog.json 的目录。 */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  /**
   * 校验语言描述之间的身份冲突，并按 languageId 排序生成 JSON。
   *
   * @throws GradleException 语言字段非法，或不同 Project 声明了重复身份、包名。
   */
  @TaskAction
  fun generate() {
    val descriptors = languageDescriptors.get().map { (projectPath, json) ->
      parseAndValidateDescriptor(projectPath, json)
    }.sortedBy { it.get("languageId").asString }
    if (descriptors.isEmpty()) {
      throw GradleException("Dynamic language Catalog must include at least one language Project.")
    }
    validateUniqueIdentities(descriptors)

    val output = outputDirectory.get().asFile
    fileSystemOperations.delete { delete(output) }
    output.mkdirs()
    val catalog = JsonObject().apply {
      add("languages", JsonArray().apply { descriptors.forEach(::add) })
    }
    output.resolve(CATALOG_FILE_NAME).writeText(GSON.toJson(catalog))
  }

  /** 解析单个 Project 描述，并对端上协议涉及的字段执行发布前校验。 */
  private fun parseAndValidateDescriptor(projectPath: String, descriptorJson: String): JsonObject {
    val descriptor = runCatching { JsonParser.parseString(descriptorJson).asJsonObject }
      .getOrElse { cause ->
        throw GradleException("$projectPath produced an invalid dynamic language descriptor.", cause)
      }
    val languageId = descriptor.requiredString(projectPath, "languageId")
    val displayName = descriptor.requiredString(projectPath, "displayName")
    val packageName = descriptor.requiredString(projectPath, "npmPackageName")
    if (!LANGUAGE_ID_REGEX.matches(languageId)) {
      throw GradleException("$projectPath has invalid dynamic language id '$languageId'.")
    }
    if (displayName.isBlank() || displayName.length > MAX_DISPLAY_NAME_LENGTH) {
      throw GradleException("$projectPath has an invalid dynamic language displayName.")
    }
    validateNpmPackageName(packageName)
    descriptor.validateStringArray(projectPath, "aliases", LANGUAGE_ID_REGEX)
    descriptor.validateStringArray(projectPath, "fileExtensions", FILE_EXTENSION_REGEX)
    return descriptor
  }

  /** 拒绝跨语言重复 ID、别名和 npm 包坐标。 */
  private fun validateUniqueIdentities(descriptors: List<JsonObject>) {
    val identities = mutableSetOf<String>()
    val packageNames = mutableSetOf<String>()
    descriptors.forEach { descriptor ->
      val languageId = descriptor.get("languageId").asString
      val aliases = descriptor.getAsJsonArray("aliases").map { it.asString }
      (listOf(languageId) + aliases).forEach { identity ->
        if (!identities.add(identity)) {
          throw GradleException("Dynamic language Catalog contains duplicate id or alias '$identity'.")
        }
      }
      val packageName = descriptor.get("npmPackageName").asString
      if (!packageNames.add(packageName)) {
        throw GradleException("Dynamic language Catalog contains duplicate package '$packageName'.")
      }
    }
  }

  /** 读取必填字符串属性并给出包含来源 Project 的错误。 */
  private fun JsonObject.requiredString(projectPath: String, name: String): String {
    val value = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    return value ?: throw GradleException("$projectPath must provide dynamic language '$name'.")
  }

  /** 校验字符串数组、重复项和单项格式。 */
  private fun JsonObject.validateStringArray(
    projectPath: String,
    name: String,
    pattern: Regex,
  ) {
    val array = get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
      ?: throw GradleException("$projectPath must provide dynamic language '$name' as an array.")
    val values = array.mapIndexed { index, element ->
      if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
        throw GradleException("$projectPath has a non-string '$name' item at index $index.")
      }
      element.asString
    }
    if (values.distinct().size != values.size || values.any { !pattern.matches(it) }) {
      throw GradleException("$projectPath has invalid or duplicate dynamic language '$name'.")
    }
  }

  private companion object {
    const val CATALOG_FILE_NAME = "catalog.json"
    const val MAX_DISPLAY_NAME_LENGTH = 128
    val GSON = GsonBuilder().setPrettyPrinting().create()
    val LANGUAGE_ID_REGEX = Regex("[a-z][a-z0-9-]{0,63}")
    val FILE_EXTENSION_REGEX = Regex("[a-z0-9][a-z0-9+_-]{0,31}")
  }
}

/**
 * 注册 Catalog 生成任务，并自动建立语言包先发布、Catalog 后发布的任务图。
 *
 * 语言 Project 的扩展会在所有 Project 配置完成后一次性冻结为任务输入；任务执行期不读取 Gradle
 * Project 模型。新增语言只需要把对应 Project 传给本函数。
 *
 * @param languageProjects 需要进入 Catalog 的语言实现 Project。
 */
fun Project.generateDynamicLanguageCatalog(
  vararg languageProjects: Project,
) {
  val projectPaths = languageProjects.map(Project::getPath)
  if (projectPaths.distinct().size != projectPaths.size) {
    throw GradleException("$path contains duplicate dynamic language Projects.")
  }
  val generateCatalog = tasks.register<GenerateDynamicLanguageCatalogTask>(
    "generateDynamicLanguageCatalog",
  ) {
    group = "npm"
    description = "读取已登记语言 Project 并生成静态 catalog.json。"
    outputDirectory.set(layout.buildDirectory.dir("generated/dynamicLanguageCatalog/npm"))
  }
  extensions.getByType<NpmStaticPackageExtension>().resources.srcDir(
    generateCatalog.flatMap { it.outputDirectory },
  )
  tasks.matching { it.name == "prepareNpmPackage" }.configureEach {
    dependsOn(generateCatalog)
  }
  tasks.matching { it.name == "publishNpmPackage" }.configureEach {
    dependsOn(projectPaths.map { "$it:publishNpmJsPackage" })
  }

  gradle.projectsEvaluated {
    val descriptors = projectPaths.associateWith { projectPath ->
      val languageProject = rootProject.project(projectPath)
      if (!languageProject.pluginManager.hasPlugin("manager.npmJs")) {
        throw GradleException("$projectPath must apply manager.npmJs.")
      }
      if (languageProject.extensions.findByType(DynamicLanguagePackageExtension::class.java) == null) {
        throw GradleException("$projectPath must call npmJsLanguage in its build.gradle.kts.")
      }
      languageProject.dynamicLanguageDescriptorJson()
    }
    generateCatalog.configure {
      languageDescriptors.set(descriptors)
    }
  }
}

/** 把语言 Project 的两个扩展冻结为 Catalog 生成任务输入。 */
private fun Project.dynamicLanguageDescriptorJson(): String {
  val language = extensions.getByType<DynamicLanguagePackageExtension>()
  val npmPackage = extensions.getByType<NpmJsPackageExtension>()
  return GSON.toJson(JsonObject().apply {
    addProperty("languageId", language.languageId.get())
    addProperty("displayName", language.displayName.get())
    addProperty("npmPackageName", validateNpmPackageName(npmPackage.packageName.get()))
    add("aliases", language.aliases.get().toJsonArray())
    add("fileExtensions", language.fileExtensions.get().toJsonArray())
  })
}

/** 把 Gradle 字符串列表转换为顺序稳定的 JSON 数组。 */
private fun List<String>.toJsonArray(): JsonArray {
  return JsonArray().apply { this@toJsonArray.forEach(::add) }
}

private val GSON = GsonBuilder().setPrettyPrinting().create()
