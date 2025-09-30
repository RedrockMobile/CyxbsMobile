package rule

import ProjectChecker
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * 模块依赖检查规则，检查是否应依赖 api 模块
 *
 * @author 985892345
 * @date 2024/11/10
 */
object ModuleDependCheckRule : ProjectChecker.ICheckRule {

  // TODO 用于特殊情况时忽略 api 模块检查，正常情况下就应该依赖 api 模块而不是实现模块
  // 以下模块在依赖其他模块时忽略检查
  private val ignoreRootProjectPaths = setOf<String>(
    ":cyxbs-application"
  )

  // 以下模块在被依赖时忽略检查
  private val ignoreDependencyProjectPaths = setOf<String>(
  )

  override fun onConfig(project: Project) {
    if (ignoreRootProjectPaths.any { project.path.startsWith(it) }) return
    val regex = Regex("[Ii]mplementation|[Aa]pi")
    project.afterEvaluate {
      configurations.all {
        if (name.contains(regex)) {
          dependencies.forEach { dependency ->
            if (dependency is ProjectDependency) {
              checkProjectDependency(project, dependency.path)
            }
          }
        }
      }
    }
  }

  private fun checkProjectDependency(root: Project, projectPath: String) {
    if (ignoreDependencyProjectPaths.contains(projectPath)) return
    val dependency = root.findProject(projectPath)!!
    val apiProject = dependency.subprojects.find { it.name.startsWith("api") }
    if (apiProject != null) {
      throw IllegalStateException("${root.path} 模块依赖配置有误，不应该依赖 ${dependency.path} 模块，" +
          "而应该依赖其 api 模块: ${apiProject.path}\n" +
          "${root.projectDir.resolve("build.gradle.kts")}")
    }
  }
}