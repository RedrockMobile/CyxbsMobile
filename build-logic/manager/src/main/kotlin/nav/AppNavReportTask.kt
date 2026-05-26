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

    val file = outputFile.asFile.get()
    file.parentFile.mkdirs()
    file.writeText(buildString {
      appendLine("# AppNav Deeplink 汇总")
      appendLine()
      appendLine("> 打包时由 build-logic/manager/${AppNavReportTask::class.qualifiedName} 自动生成")
      appendLine("> 该文件需要被 git 提交用于后续使用")
      appendLine()
      appendLine("- versionCode: ${Config.versionCode}")
      appendLine("- versionName: ${Config.versionName}")
      appendLine("- date: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
      appendLine()
      appendLine("## 调试方法")
      appendLine()
      appendLine("使用 idea 文档中的 ▶ 运行下面脚本，输入要测试的 deeplink")
      appendLine()
      appendLine("### Windows / macOS / Linux")
      appendLine()
      appendLine("> win 没 bash 可使用终端运行 PowerShell 版")
      appendLine()
      appendLine("idea 中点击左侧 ▶ 可直接运行")
      appendLine("```sh")
      appendLine("#!/usr/bin/env bash")
      appendLine("while true; do")
      appendLine("  printf \"输入 deeplink (回车退出): \"")
      appendLine("  read -r link")
      appendLine("  [ -z \"\$link\" ] && break")
      appendLine("  adb shell am start -a android.intent.action.VIEW -d \"\$link\"")
      appendLine("done")
      appendLine("```")
      appendLine()
      appendLine("### Windows (PowerShell 版)")
      appendLine()
      appendLine("```powershell")
      appendLine("while (\$true) {")
      appendLine("  \$link = Read-Host \"输入 deeplink (回车退出)\"")
      appendLine("  if ([string]::IsNullOrEmpty(\$link)) { break }")
      appendLine("  adb shell am start -a android.intent.action.VIEW -d \"\$link\"")
      appendLine("}")
      appendLine("```")
      appendLine()
      if (moduleBlocks.isEmpty()) {
        appendLine("未找到任何由 KSP 生成的 AppNavReport 文档，请检查 ksp-navigation 实现")
      } else {
        moduleBlocks.forEach { (modulePath, files) ->
          appendLine("## $modulePath")
          appendLine()
          files.sortedBy { it.name }.forEach { entryFile ->
            appendLine(entryFile.readText().trim())
            appendLine()
          }
        }
      }
    })
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
