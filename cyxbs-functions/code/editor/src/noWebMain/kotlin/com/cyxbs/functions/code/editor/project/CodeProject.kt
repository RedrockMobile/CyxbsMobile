package com.cyxbs.functions.code.editor.project

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.Serializable

/**
 * 一个由编辑器管理的真实本地项目。
 *
 * 路由只传递 [projectId]，磁盘位置由已授权项目根目录和 [storageDirectoryName] 共同解析，避免把
 * Android URI 或 iOS 安全作用域 URL 写入 Deeplink 和项目 manifest。
 */
@Serializable
data class CodeProject(
  val projectId: String,
  val name: String,
  val languageId: String,
  val storageDirectoryName: String = projectId,
  val lastOpenedAtEpochMilliseconds: Long,
  val isPinned: Boolean = false,
  val activeFilePath: String? = null,
)

/** 首页展示的历史项目及其当前可访问目录；目录权限失效时 [directory] 为 null。 */
data class HistoricalCodeProject(
  val project: CodeProject,
  val directory: PlatformFile?,
  val directoryDisplayPath: String,
) {
  val isAvailable: Boolean
    get() = directory != null
}

/** 从真实目录读取后交给编辑器的工作区快照。 */
data class CodeProjectWorkspace(
  val project: CodeProject,
  val sourceFiles: Map<String, String>,
  val directoryPaths: Set<String>,
  val activeFilePath: String,
  val directory: PlatformFile,
  val directoryDisplayPath: String,
)

/** 项目文件重命名请求；路径始终是相对于项目根目录的规范路径。 */
data class CodeProjectFileRename(
  val oldPath: String,
  val newPath: String,
)

/**
 * 创建项目时使用的最小语言模板。
 *
 * 模板目前由客户端为已支持语言提供；后续语言包增加项目模板协议后，只需替换模板来源，项目仓库和
 * Navigation 参数不需要变化。
 */
data class CodeProjectTemplate(
  val languageId: String,
  val displayName: String,
  val defaultProjectName: String,
  val activeFilePath: String,
  val sourceFiles: Map<String, String>,
)

/** 首批可创建的真实项目模板。 */
object CodeProjectTemplates {
  val all: List<CodeProjectTemplate> = listOf(
    CodeProjectTemplate(
      languageId = "java",
      displayName = "Java",
      defaultProjectName = "JavaProject",
      activeFilePath = "src/Main.java",
      sourceFiles = mapOf(
        "src/Main.java" to """
          public class Main {
            public static void main(String[] args) {
              System.out.println("Hello, Java!");
            }
          }
        """.trimIndent(),
      ),
    ),
    CodeProjectTemplate(
      languageId = "javascript",
      displayName = "JavaScript",
      defaultProjectName = "JavaScriptProject",
      activeFilePath = "src/main.js",
      sourceFiles = mapOf(
        "src/main.js" to """
          const message = "Hello, JavaScript!";
          console.log(message);
        """.trimIndent(),
      ),
    ),
  )

  /** 按 Catalog 的稳定语言 ID 或别名查找当前可创建的模板。 */
  fun find(languageId: String): CodeProjectTemplate? =
    all.firstOrNull { it.languageId == languageId }
}
