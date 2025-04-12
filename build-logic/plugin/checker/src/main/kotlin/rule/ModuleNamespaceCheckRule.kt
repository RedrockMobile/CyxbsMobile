package rule

import ProjectChecker
import org.gradle.api.Project
import java.io.File
import java.util.Scanner

/**
 * 项目命名空间检查，主要用于新建项目的时候没有按规范写好模块包名
 *
 * @author 985892345
 * 2022/12/20 17:42
 */
object ModuleNamespaceCheckRule : ProjectChecker.ICheckRule {

  /**
   * 得到正确的 namespace
   */
  fun getCorrectNamespace(project: Project): String {
    if (project.name == "lib_common") {
      // lib_common 未迁移，这里特殊处理
      return "com.mredrock.cyxbs.common"
    }
    return "com${project.path.replace(":", ".").replace("-", ".")}"
  }
  
  override fun onConfig(project: Project) {
    if (project.name == "lib_common") {
      return // lib_common 未迁移，这里特殊处理，不进行检查
    }
    val sourceSetList = listOf(
      "androidMain",
      "commonMain",
      "mobileMain",
      "desktopMain",
      "iosMain",
      "wasmJsMain",
      "jbComposeMain",
    ).map { SourceSet(project, it) }

    if (sourceSetList.all { it.kotlinFileChildren.isEmpty() }) {
      // 如果都不存在，则应该是新模块，自动帮他创建文件夹
      val sc = Scanner(System.`in`)
      println("检测到新建模块 ${project.name}，请按以下规则输入新模块需要的源集：（支持输入多个源集）\n" +
          "c 表示 commonMain (全平台)\n" +
          "m 表示 mobileMain (仅移动端，包含安卓与 iOS，androidMain + iosMain = mobileMain)\n" +
          "a 表示 androidMain (仅安卓)\n" +
          "d 表示 desktopMain (仅桌面端)\n" +
          "i 表示 iosMain (仅 iOS)\n" +
          "w 表示 wasmJsMain (仅网页端)\n" +
          "j 表示 jbComposeMain (仅使用 jb Compose 的平台，androidMain + jbComposeMain = commonMain)\n")
      sc.nextLine().forEach { char ->
        sourceSetList.find { it.name.startsWith(char) }?.codeFile?.mkdirs()
      }
    } else {
      // 包名检测
      sourceSetList.filter {
        !it.project.path.startsWith(":cyxbs-applications") // cyxbs-applications 下的模块不检查包名
      }.filter {
        !it.name.startsWith("ios") // ios 相关源集不检查包名，因为 ios 开发中并不存在包名概念
      }.forEach {
        if (it.kotlinFileChildren.isNotEmpty() && !it.codeFile.exists()) {
          // *Main/kotlin 下有文件，但是 *Main/kotlin/[namespace] 却不存在，则说明包名有问题
          val rule = """
            ${project.name} 模块 ${it.name} 源集包名错误，应该改为：${it.namespace}
            ${it.kotlinFile}
            
            若需删掉 ${it.name} 源集，请手动进入文件管理器中删除
          
          """.trimIndent()
          throw RuntimeException("${project.name} 模块包名错误！\n" + rule)
        }
      }
    }
  }

  private class SourceSet(
    val project: Project,
    val name: String,
  ) {
    val namespace = getCorrectNamespace(project)

    val kotlinFile = project.projectDir
      .resolve("src")
      .resolve(name)
      .resolve("kotlin")

    val kotlinFileChildren = kotlinFile.list() ?: emptyArray()

    val codeFile = kotlinFile.resolve(namespace.replace(".", File.separator))
  }
}