package nav

import Config
import nav.AppNavReportTask.Companion.register
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 聚合当前 application 模块依赖的所有模块下 KSP 生成的 AppNav 中间报告，
 * 写出到当前 app 模块根目录下的 AppNav.md。
 *
 * - 输入：依赖模块的 `build/generated/ksp/android/androidMain/resources/AppNavReport`
 * - 输出：`<app-module>/AppNav.md`，包含 [Config.versionCode] / [Config.versionName] 与聚合的 deeplink 模板
 * - 由 `manager.app-android.gradle.kts` 调用 [register] 注册，并 finalizedBy 到 `assembleDebug` / `assembleRelease`
 *
 * 单条中间报告由 ksp-navigation 模块的 `KspNavigationSymbolProcess` 在每个 @AppNav 类上生成。
 */
abstract class AppNavReportTask : DefaultTask() {

  /**
   * Gradle 增量构建的真实输入：所有依赖模块的 `AppNavReport` 目录集合。
   *
   * - 目录里 md 文件内容/路径有变化时，task 才会重新执行
   * - 使用 [PathSensitivity.RELATIVE]，输入 hash 不含绝对路径，便于跨机器命中 build cache
   * - 内容来自 [register] 中的 modulePathToDir provider 的 values 部分，与 [modulePathToReportDir] 同源
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val reportDirs: ConfigurableFileCollection

  /**
   * 模块 path 到该模块 `AppNavReport` 目录的映射，仅用于在汇总时按模块分组生成 `## modulePath` 二级标题。
   *
   * - 标 [Internal] 是因为它只是「目录所属模块」的元数据，不应参与增量判断
   * - 真正的文件层面增量已经由 [reportDirs] 覆盖
   * - 与 [reportDirs] 同源，由 [register] 中的 modulePathToDir provider 填充
   */
  @get:Internal
  abstract val modulePathToReportDir: MapProperty<String, Directory>

  /** 聚合输出的 Markdown 文件，路径由 [register] 设为 `<app-module>/AppNav.md`。 */
  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun aggregate() {
    val moduleBlocks = modulePathToReportDir.get().asSequence()
      .mapNotNull { (modulePath, dir) ->
        val files = dir.asFile.listFiles().orEmpty()
          .filter { it.isFile && it.extension == "md" }
        if (files.isEmpty()) null else modulePath to files
      }
      .sortedBy { it.first }
      .toList()

    // 按模块聚合每个模块下所有 entry 的 md 片段，模块之间空一行隔开。
    // 单独构建是为了避开 trimIndent —— entry md 自带的 0-indent 行会破坏外层 raw string 的最小公共缩进推断。
    val moduleSection = if (moduleBlocks.isEmpty()) {
      "未找到任何由 KSP 生成的 AppNavReport 文档，请检查 ksp-navigation 实现"
    } else {
      moduleBlocks.joinToString(separator = "\n\n") { (modulePath, files) ->
        val entries = files.sortedBy { it.name }
          .joinToString(separator = "\n\n") { it.readText().trim() }
        "## $modulePath\n\n$entries"
      }
    }

    val taskName = AppNavReportTask::class.qualifiedName
    // raw string 里 ${'$'} 表示字面 `$`，避免和 Kotlin 字符串模板冲突，用于 bash / PowerShell 脚本里的变量。
    val d = "$"

    val staticContent = """
      # AppNav Deeplink 汇总

      > 打包时由 build-logic/manager/$taskName 自动生成
      > 该文件需要被 git 提交用于后续使用

      - versionCode: ${Config.versionCode}
      - versionName: ${Config.versionName}

      ## 调试方法

      使用 idea 文档中的 ▶ 运行下面脚本，输入要测试的 deeplink

      ### Windows / macOS / Linux

      > win 没 bash 可使用终端运行 PowerShell 版

      idea 中点击左侧 ▶ 可直接运行
      ```sh
      #!/usr/bin/env bash
      while true; do
        printf "输入 deeplink (回车退出): "
        read -r link
        [ -z "${d}link" ] && break
        adb shell am start -a android.intent.action.VIEW -d "${d}link"
      done
      ```

      ### Windows (PowerShell 版)

      ```powershell
      while (${d}true) {
        ${d}link = Read-Host "输入 deeplink (回车退出)"
        if ([string]::IsNullOrEmpty(${d}link)) { break }
        adb shell am start -a android.intent.action.VIEW -d "${d}link"
      }
      ```

      ## 模板说明

      URL 模板用 `{}` / `[]` 区分 required / optional，object fields 段递归展开复杂结构。

      ### URL 模板

      - `name={Type}` — required 字段，调用方必须提供
      - `name=[Type]` — optional 字段（构造参数有默认值），调用方可省略
      - 类型末尾的 `?` 表示 Kotlin nullable，值允许为 null（与 optional 不同：nullable 仍要求字段出现）
      - 集合 / Map 直接以原始 Kotlin 类型出现，例如 `{List<TextInfo>}`、`{Map<String, TextInfo>}`，编码方式遵循 kotlinx.serialization JSON 形式

      ### object fields

      仅当字段含可展开的内部结构时才会出现。展开规则：

      - `name: Type` — required 字段，`[name]: Type` — optional 字段
      - 普通 `@Serializable` 类：列出每个非 `@Transient` 的主构造参数
      - `enum`：列出所有 entry 名
      - `Map<K, V>`：展开为 `value: V { ... }`（仅 V 是复杂类型时才进一步展开）
      - `Collection<E>` / `Array<E>`：展开为 `value: E { ... }`
      - 带类型形参的类（如 `Wrapper<T>`）：按外层泛型实参替换 `T` 后再展开

      ### 示例

      ```text
      deeplink: cyxbs://test/abc?title={String}&content={String}&map=[Map<String, TextInfo>]&button=[ButtonInfo?]
      object fields:
        [map]: Map<String, TextInfo> {
          value: TextInfo {
            text: String
            [isBold]: Boolean
          }
        }
        [button]: ButtonInfo? {
          text: String
          [action]: String?
        }
      ```

      解读：`title` / `content` 必填；`map` 可省略，若提供则为 `Map<String, TextInfo>` 的 JSON；`button` 可省略且允许为 null。`TextInfo` 中只有 `text` 必填，其它字段可省略。
    """.trimIndent()

    val file = outputFile.asFile.get()
    file.parentFile.mkdirs()
    file.writeText("$staticContent\n\n$moduleSection\n")
  }

  companion object {

    /**
     * 在当前 application 模块上注册 generateAppNavReport 任务，并 finalizedBy 到 assemble*。
     */
    fun register(project: Project) {
      val generateAppNavReport = project.tasks.register("generateAppNavReport", AppNavReportTask::class.java) {
        group = "cyxbs"
        description = "Aggregate @AppNav deeplink reports from dependency modules into <app>/AppNav.md."
        // task 执行时再按 path 解析依赖模块，避免配置阶段 evaluate 顺序差异导致 findProject 返回 null
        val modulePathToDir = project.provider {
          project.collectDependencyProjectPaths().mapNotNull { path ->
            val dir = project.rootProject.findProject(path)?.layout?.buildDirectory
              ?.dir("generated/ksp/android/androidMain/resources/AppNavReport")
              ?.orNull
              ?: return@mapNotNull null
            path to dir
          }.toMap()
        }
        modulePathToReportDir.putAll(modulePathToDir)
        reportDirs.from(modulePathToDir.map { it.values })
        outputFile.set(project.layout.projectDirectory.file("AppNav.md"))
      }
      project.afterEvaluate {
        listOf("assembleDebug", "assembleRelease").forEach { taskName ->
          project.tasks.named(taskName).configure {
            finalizedBy(generateAppNavReport)
          }
        }
      }
    }

    /**
     * 递归收集当前 app 模块依赖到的所有项目路径（包含传递依赖）。
     *
     * 这里只返回 path，不直接持有 Project，因为配置阶段不同模块的 evaluate 顺序不固定，
     * 直接 `rootProject.findProject(path)` 可能拿不到目标 Project。
     */
    private fun Project.collectDependencyProjectPaths(): Set<String> {
      val visited = linkedSetOf<String>()
      fun visit(target: Project) {
        target.configurations.forEach { configuration ->
          configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
            if (!visited.add(dependency.path)) return@forEach
            val depProject = rootProject.findProject(dependency.path) ?: return@forEach
            visit(depProject)
          }
        }
      }
      visit(this)
      return visited
    }
  }
}
