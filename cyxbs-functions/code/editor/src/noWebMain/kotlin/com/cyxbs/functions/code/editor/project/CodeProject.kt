package com.cyxbs.functions.code.editor.project

import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageProjectTemplate
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.Serializable

/**
 * 一个由编辑器管理的真实本地项目。
 *
 * 路由只传递 [projectId]。受管项目由项目根目录和 [storageDirectoryName] 解析，外部项目由当前安装
 * 保存的 bookmark 解析，避免把 Android URI 或 iOS 安全作用域 URL 写入 Deeplink 和 manifest。
 */
@Serializable
data class CodeProject(
  val projectId: String,
  val name: String,
  val languageId: String,
  val storageKind: CodeProjectStorageKind = CodeProjectStorageKind.MANAGED_ROOT,
  val storageDirectoryName: String = projectId,
  val directoryDisplayPathHint: String? = null,
  val lastOpenedAtEpochMilliseconds: Long,
  val isPinned: Boolean = false,
  val activeFilePath: String? = null,
)

/** 项目目录定位方式；外部目录依赖 Settings 中按项目保存的 bookmark。 */
@Serializable
enum class CodeProjectStorageKind {
  MANAGED_ROOT,
  EXTERNAL_BOOKMARK,
}

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

/**
 * 按项目保存在 Settings 中的轻量编辑会话。
 *
 * 源码、滚动位置和撤销栈不写入该结构：源码仍以真实文件为准，滚动与撤销由当前页面的 KodeMirror
 * 会话缓存管理。跨页面只恢复打开标签、活动文件和各文件主光标。
 */
@Serializable
data class CodeProjectEditorSession(
  val projectId: String,
  val openFilePaths: List<String>,
  val activeFilePath: String,
  val cursorPositions: Map<String, Int> = emptyMap(),
)

/** 项目文件重命名请求；路径始终是相对于项目根目录的规范路径。 */
data class CodeProjectFileRename(
  val oldPath: String,
  val newPath: String,
)

/** 磁盘源码已被外部修改，调用方必须刷新或显式解决冲突后再保存。 */
class CodeProjectSourceConflictException(
  val relativePath: String,
) : IllegalStateException("文件已在编辑器外被修改，请先从磁盘刷新：$relativePath")

/**
 * 创建项目时使用的最小语言模板。
 *
 * 这是编辑器项目层的写盘模型，具体源码必须由动态语言包提供；项目仓库不感知 npm 协议和 Runtime。
 */
data class CodeProjectTemplate(
  val languageId: String,
  val displayName: String,
  val defaultProjectName: String,
  val activeFilePath: String,
  val sourceFiles: Map<String, String>,
)

/**
 * 把动态语言协议模板转换为项目写盘模型，并在接触文件系统前收紧不可信 npm 数据边界。
 *
 * 路径保持语言包声明的原值，不做静默修正；这样损坏模板会明确失败，而不会创建与语言包预期不同
 * 的目录。项目仓库仍会在每次实际文件操作前重复执行路径校验，防止其他调用方绕过本入口。
 */
internal fun DynamicLanguageProjectTemplate.toCodeProjectTemplate(
  language: DynamicLanguageInfo,
): CodeProjectTemplate {
  require(defaultProjectName.isNotBlank()) { "动态语言项目模板缺少默认项目名。" }
  require(defaultProjectName == defaultProjectName.trim()) { "默认项目名不能包含首尾空白。" }
  require(sourceFiles.isNotEmpty()) { "动态语言项目模板至少需要一个源码文件。" }

  val filesByPath = linkedMapOf<String, String>()
  sourceFiles.forEach { file ->
    requireSafeProjectTemplatePath(file.path)
    require(filesByPath.put(file.path, file.source) == null) {
      "动态语言项目模板包含重复路径：${file.path}"
    }
  }
  requireSafeProjectTemplatePath(activeFilePath)
  require(activeFilePath in filesByPath) {
    "动态语言项目模板的活动文件不存在：$activeFilePath"
  }
  return CodeProjectTemplate(
    languageId = language.languageId,
    displayName = language.displayName,
    defaultProjectName = defaultProjectName,
    activeFilePath = activeFilePath,
    sourceFiles = filesByPath,
  )
}

/** 动态模板不得写入项目根目录外，也不能覆盖编辑器维护的隐藏协议文件。 */
private fun requireSafeProjectTemplatePath(path: String) {
  val segments = path.split('/')
  require(
    path.isNotBlank() &&
      path == path.trim() &&
      !path.startsWith('/') &&
      '\\' !in path &&
      segments.firstOrNull() != ".cyxbs-project.json" &&
      segments.none { it.isEmpty() || it == "." || it == ".." },
  ) { "非法动态语言项目模板路径：$path" }
}
