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

/** 根据各语言教程 Project 的自描述配置生成确定性的 `catalog.json`。 */
@CacheableTask
abstract class GenerateDynamicTutorialCatalogTask : DefaultTask() {
  /** Project path 到冻结教程描述 JSON 的映射。 */
  @get:Input
  abstract val tutorialDescriptors: MapProperty<String, String>

  /** 仅包含生成后 catalog.json 的目录。 */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  /** 校验教程身份冲突，并按语言 ID 排序生成静态目录。 */
  @TaskAction
  fun generate() {
    val descriptors = tutorialDescriptors.get().map { (projectPath, json) ->
      parseAndValidateDescriptor(projectPath, json)
    }.sortedBy { it.get("languageId").asString }
    if (descriptors.isEmpty()) {
      throw GradleException("Dynamic tutorial Catalog must include at least one tutorial Project.")
    }
    validateUniqueIdentities(descriptors)

    val output = outputDirectory.get().asFile
    fileSystemOperations.delete { delete(output) }
    output.mkdirs()
    val catalog = JsonObject().apply {
      add("tutorials", JsonArray().apply { descriptors.forEach(::add) })
    }
    output.resolve(CATALOG_FILE_NAME).writeText(GSON.toJson(catalog))
  }

  /** 解析单个 Project 描述，并在发布前校验端上会使用的稳定字段。 */
  private fun parseAndValidateDescriptor(projectPath: String, descriptorJson: String): JsonObject {
    val descriptor = runCatching { JsonParser.parseString(descriptorJson).asJsonObject }
      .getOrElse { cause ->
        throw GradleException("$projectPath produced an invalid dynamic tutorial descriptor.", cause)
      }
    val languageId = descriptor.requiredString(projectPath, "languageId")
    val displayName = descriptor.requiredString(projectPath, "displayName")
    val packageName = descriptor.requiredString(projectPath, "npmPackageName")
    if (!LANGUAGE_ID_REGEX.matches(languageId)) {
      throw GradleException("$projectPath has invalid dynamic tutorial language id '$languageId'.")
    }
    if (displayName.isBlank() || displayName.length > MAX_DISPLAY_NAME_LENGTH) {
      throw GradleException("$projectPath has an invalid dynamic tutorial displayName.")
    }
    validateNpmPackageName(packageName)
    descriptor.validateStringArray(projectPath, "aliases", LANGUAGE_ID_REGEX)
    return descriptor
  }

  /** 拒绝不同教程包之间重复的语言身份和 npm 坐标。 */
  private fun validateUniqueIdentities(descriptors: List<JsonObject>) {
    val identities = mutableSetOf<String>()
    val packageNames = mutableSetOf<String>()
    descriptors.forEach { descriptor ->
      val languageId = descriptor.get("languageId").asString
      val aliases = descriptor.getAsJsonArray("aliases").map { it.asString }
      (listOf(languageId) + aliases).forEach { identity ->
        if (!identities.add(identity)) {
          throw GradleException("Dynamic tutorial Catalog contains duplicate id or alias '$identity'.")
        }
      }
      val packageName = descriptor.get("npmPackageName").asString
      if (!packageNames.add(packageName)) {
        throw GradleException("Dynamic tutorial Catalog contains duplicate package '$packageName'.")
      }
    }
  }

  /** 读取必填字符串，并在失败信息中保留来源 Project。 */
  private fun JsonObject.requiredString(projectPath: String, name: String): String {
    return get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
      ?: throw GradleException("$projectPath must provide dynamic tutorial '$name'.")
  }

  /** 校验别名数组的类型、格式和重复项。 */
  private fun JsonObject.validateStringArray(
    projectPath: String,
    name: String,
    pattern: Regex,
  ) {
    val array = get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray
      ?: throw GradleException("$projectPath must provide dynamic tutorial '$name' as an array.")
    val values = array.mapIndexed { index, element ->
      if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
        throw GradleException("$projectPath has a non-string '$name' item at index $index.")
      }
      element.asString
    }
    if (values.distinct().size != values.size || values.any { !pattern.matches(it) }) {
      throw GradleException("$projectPath has invalid or duplicate dynamic tutorial '$name'.")
    }
  }

  private companion object {
    const val CATALOG_FILE_NAME = "catalog.json"
    const val MAX_DISPLAY_NAME_LENGTH = 128
    val GSON = GsonBuilder().setPrettyPrinting().create()
    val LANGUAGE_ID_REGEX = Regex("[a-z][a-z0-9-]{0,63}")
  }
}

/**
 * 注册教程 Catalog 生成任务，并建立教程实现先发布、Catalog 后发布的任务图。
 *
 * @param tutorialProjects 需要进入 Catalog 的语言教程实现 Project。
 */
fun Project.generateDynamicTutorialCatalog(vararg tutorialProjects: Project) {
  val projectPaths = tutorialProjects.map(Project::getPath)
  if (projectPaths.distinct().size != projectPaths.size) {
    throw GradleException("$path contains duplicate dynamic tutorial Projects.")
  }
  val generateCatalog = tasks.register<GenerateDynamicTutorialCatalogTask>(
    "generateDynamicTutorialCatalog",
  ) {
    group = "npm"
    description = "读取已登记教程 Project 并生成静态 catalog.json。"
    outputDirectory.set(layout.buildDirectory.dir("generated/dynamicTutorialCatalog/npm"))
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
      val tutorialProject = rootProject.project(projectPath)
      if (!tutorialProject.pluginManager.hasPlugin("manager.npmJs")) {
        throw GradleException("$projectPath must apply manager.npmJs.")
      }
      if (tutorialProject.extensions.findByType(DynamicTutorialPackageExtension::class.java) == null) {
        throw GradleException("$projectPath must call npmJsTutorial in its build.gradle.kts.")
      }
      tutorialProject.dynamicTutorialDescriptorJson()
    }
    generateCatalog.configure { tutorialDescriptors.set(descriptors) }
  }
}

/** 把教程 Project 的 npm 与教程扩展冻结为 Catalog 任务输入。 */
private fun Project.dynamicTutorialDescriptorJson(): String {
  val tutorial = extensions.getByType<DynamicTutorialPackageExtension>()
  val npmPackage = extensions.getByType<NpmJsPackageExtension>()
  return TUTORIAL_GSON.toJson(JsonObject().apply {
    addProperty("languageId", tutorial.languageId.get())
    addProperty("displayName", tutorial.displayName.get())
    addProperty("npmPackageName", validateNpmPackageName(npmPackage.packageName.get()))
    add("aliases", JsonArray().apply { tutorial.aliases.get().forEach(::add) })
  })
}

private val TUTORIAL_GSON = GsonBuilder().setPrettyPrinting().create()
