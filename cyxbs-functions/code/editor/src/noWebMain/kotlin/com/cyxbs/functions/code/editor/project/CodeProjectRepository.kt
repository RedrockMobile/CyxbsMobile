package com.cyxbs.functions.code.editor.project

import com.cyxbs.components.config.sp.defaultSettings
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * 用户项目目录、项目 manifest 与历史项目索引的唯一入口。
 *
 * Settings 缓存最近顺序、受管根目录 bookmark 和外部项目 bookmark；每个项目自身都保存一份
 * [.cyxbs-project.json][PROJECT_MANIFEST_FILE_NAME]。应用卸载不会删除用户选择目录中的源码，重装后
 * 重新选择原目录即可从 manifest 恢复项目。所有写操作通过同一 [Mutex] 串行，避免自动保存和目录
 * 扫描相互覆盖。
 */
class CodeProjectRepository(
  private val settings: Settings = defaultSettings,
  private val projectsRoot: PlatformFile? = null,
  private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  },
  private val externalProjectDirectoryPicker: suspend () -> PlatformFile? =
    ::selectExternalCodeProjectDirectory,
) {
  private val mutex = Mutex()

  /**
   * 确保已经取得项目根目录。
   *
   * 移动端首次调用会打开系统目录选择器；取消时返回 false。成功后同步磁盘 manifest，使重装后的
   * 项目立即重新出现在首页。
   */
  suspend fun prepareProjectRoot(): Boolean = mutex.withLock {
    val root = resolveStorageRoot(requestIfMissing = true) ?: return@withLock false
    saveProjects(loadProjects(root))
    true
  }

  /** 返回置顶优先、其余按最近点击时间倒序排列的历史项目。 */
  suspend fun historicalProjects(): List<HistoricalCodeProject> = mutex.withLock {
    val root = resolveStorageRoot(requestIfMissing = false)
    val projects = loadProjects(root).sortedForHistory()
    saveProjects(projects)
    projects.map { project ->
      val directory = resolveProjectDirectory(root, project)?.takeIf {
        it.exists() && it.isDirectory()
      }
      HistoricalCodeProject(
        project = project,
        directory = directory,
        directoryDisplayPath = directory?.let { accessibleDirectory ->
          projectDisplayPath(root, project, accessibleDirectory)
        } ?: project.directoryDisplayPathHint ?: project.storageDirectoryName,
      )
    }
  }

  /**
   * 依据语言模板在用户项目根目录创建项目，并写入可跨安装恢复的 manifest。
   *
   * [projectName] 由创建弹窗显式提供；展示名称不参与磁盘路径拼接，目录仍使用稳定 projectId。
   */
  suspend fun createProject(
    template: CodeProjectTemplate,
    projectName: String,
  ): CodeProjectWorkspace = mutex.withLock {
    val root = resolveStorageRoot(requestIfMissing = true)
      ?: throw CodeProjectException("未选择项目保存目录。")
    val projects = loadProjects(root)
    val normalizedProjectName = normalizeProjectName(projectName)
    if (projects.any { it.name.equals(normalizedProjectName, ignoreCase = true) }) {
      throw CodeProjectException("项目名称已存在：$normalizedProjectName")
    }
    val projectId = uniqueProjectId(template.languageId, projects)
    val project = CodeProject(
      projectId = projectId,
      name = normalizedProjectName,
      languageId = template.languageId,
      storageDirectoryName = projectId,
      lastOpenedAtEpochMilliseconds = clock(),
      activeFilePath = template.activeFilePath,
    )
    val directory = root.directoryFor(project)
    directory.createDirectories(mustCreate = true)
    template.sourceFiles.forEach { (path, source) ->
      requireSafeRelativePath(path)
      resolveFile(directory, path, createParents = true).writeString(source)
    }
    writeProjectManifest(directory, project)
    saveProjects(projects.filterNot { it.projectId == projectId } + project)
    removeIgnoredProject(projectId)
    CodeProjectWorkspace(
      project = project,
      sourceFiles = template.sourceFiles,
      directoryPaths = template.sourceFiles.keys.parentDirectoryPaths(),
      activeFilePath = template.activeFilePath,
      directory = directory,
      directoryDisplayPath = root.displayPathFor(project),
    )
  }

  /**
   * 选择并导入一个已经存在的本地目录；源码继续保留在原位置，不复制到受管项目根目录。
   *
   * 目录中已有 manifest 时复用稳定项目 ID；没有 manifest 时根据源码扩展名推断主要语言并创建。
   * 取消系统目录选择器返回 null，调用方无需把它当作错误提示。
   */
  suspend fun importProject(): CodeProjectWorkspace? = mutex.withLock {
    val directory = externalProjectDirectoryPicker() ?: return@withLock null
    if (!directory.exists() || !directory.isDirectory()) {
      throw CodeProjectException("选择的项目目录不可访问。")
    }
    val snapshot = readWorkspace(directory)
    if (snapshot.sourceFiles.isEmpty()) {
      throw CodeProjectException("所选目录中没有可编辑的源码文件。")
    }

    val root = resolveStorageRoot(requestIfMissing = false)
    val projects = loadProjects(root)
    val manifestProject = readProjectManifest(directory)
    val detectedLanguageId = detectPrimaryLanguage(snapshot.sourceFiles.keys)
    val projectId = manifestProject?.projectId?.takeIf(String::isNotBlank)?.also(::requireSafeProjectId)
      ?: uniqueProjectId(detectedLanguageId, projects)
    val existingProject = projects.firstOrNull { it.projectId == projectId }
    if (existingProject != null) {
      val existingDirectory = resolveProjectDirectory(root, existingProject)
      if (existingDirectory != null && !existingDirectory.isSameDirectoryAs(directory)) {
        throw CodeProjectException("项目 ID 已被另一个目录使用：$projectId")
      }
    }

    val projectName = normalizeProjectName(
      manifestProject?.name?.takeIf(String::isNotBlank) ?: directory.name.ifBlank { "ImportedProject" },
    )
    val duplicateName = projects.firstOrNull { project ->
      project.projectId != projectId && project.name.equals(projectName, ignoreCase = true)
    }
    if (duplicateName != null) {
      throw CodeProjectException("项目名称已存在：$projectName")
    }
    val activeFilePath = manifestProject?.activeFilePath
      ?.takeIf(snapshot.sourceFiles::containsKey)
      ?: chooseInitialActiveFile(snapshot.sourceFiles.keys)
    val importedProject = CodeProject(
      projectId = projectId,
      name = projectName,
      languageId = manifestProject?.languageId?.takeIf(String::isNotBlank) ?: detectedLanguageId,
      storageKind = CodeProjectStorageKind.EXTERNAL_BOOKMARK,
      storageDirectoryName = directory.name.ifBlank { projectId },
      // manifest 只记录可读的目录名；完整路径或 URI 只保存在当前安装的 bookmark 中。
      directoryDisplayPathHint = directory.name.ifBlank { projectName },
      lastOpenedAtEpochMilliseconds = clock(),
      isPinned = manifestProject?.isPinned ?: existingProject?.isPinned ?: false,
      activeFilePath = activeFilePath,
    )
    val previousBookmark = existingProject
      ?.takeIf { it.storageKind == CodeProjectStorageKind.EXTERNAL_BOOKMARK }
      ?.let { restoreExternalCodeProjectDirectory(settings, it.projectId) }
    val previousManifest = readProjectManifestText(directory)
    try {
      saveExternalCodeProjectDirectory(settings, projectId, directory)
      writeProjectManifest(directory, importedProject)
      saveProjects(projects.replace(importedProject))
      removeIgnoredProject(projectId)
      workspaceOf(
        project = importedProject,
        directory = directory,
        displayPath = directory.externalCodeProjectDisplayPath(),
        snapshot = snapshot,
      )
    } catch (throwable: Throwable) {
      restoreProjectManifest(directory, previousManifest)
      if (previousBookmark == null) {
        removeExternalCodeProjectDirectory(settings, projectId)
      } else {
        runCatching { saveExternalCodeProjectDirectory(settings, projectId, previousBookmark) }
      }
      runCatching { saveProjects(projects) }
      throw throwable
    }
  }

  /** 从最近项目 ID 重新解析授权目录并读取全部受支持源码。 */
  suspend fun openProject(projectId: String): CodeProjectWorkspace = mutex.withLock {
    val root = resolveStorageRoot(requestIfMissing = false)
    val projects = loadProjects(root)
    val project = projects.firstOrNull { it.projectId == projectId }
      ?: throw CodeProjectException("项目记录不存在：$projectId")
    val directory = resolveProjectDirectory(root, project)
      ?: throw CodeProjectException("项目目录授权已失效，请重新打开项目目录。")
    if (!directory.exists() || !directory.isDirectory()) {
      throw CodeProjectException("项目目录已不可访问：${project.name}")
    }
    val snapshot = readWorkspace(directory)
    val sourceFiles = snapshot.sourceFiles
    val activeFilePath = project.activeFilePath
      ?.takeIf(sourceFiles::containsKey)
      ?: sourceFiles.keys.firstOrNull()
      ?: throw CodeProjectException("项目中没有可编辑的源码文件：${project.name}")
    val openedProject = project.copy(
      lastOpenedAtEpochMilliseconds = clock(),
      activeFilePath = activeFilePath,
    )
    writeProjectManifest(directory, openedProject)
    saveProjects(projects.replace(openedProject))
    CodeProjectWorkspace(
      project = openedProject,
      sourceFiles = sourceFiles,
      directoryPaths = snapshot.directoryPaths,
      activeFilePath = activeFilePath,
      directory = directory,
      directoryDisplayPath = projectDisplayPath(root, openedProject, directory),
    )
  }

  /** 将当前活动文件同时写回 Settings 索引和项目 manifest，用于卸载后恢复。 */
  suspend fun updateActiveFile(projectId: String, activeFilePath: String) = mutex.withLock {
    requireSafeRelativePath(activeFilePath)
    val context = requireProjectContext(projectId)
    val updated = context.project.copy(activeFilePath = activeFilePath)
    writeProjectManifest(context.directory, updated)
    saveProjects(context.projects.replace(updated))
  }

  /**
   * 读取并清理项目编辑会话；外部删除的文件会从标签和光标记录中移除。
   *
   * @param sourceFiles 当前真实磁盘快照，用于限制恢复路径并裁剪越界光标。
   * @return 存在历史会话时返回清理后的状态，否则返回 null。
   */
  suspend fun loadEditorSession(
    projectId: String,
    sourceFiles: Map<String, String>,
  ): CodeProjectEditorSession? = mutex.withLock {
    val stored = loadEditorSessions().firstOrNull { session -> session.projectId == projectId }
      ?: return@withLock null
    val validOpenPaths = stored.openFilePaths
      .filter(sourceFiles::containsKey)
      .distinct()
      .take(MAX_OPEN_FILES_PER_PROJECT)
    val activeFilePath = stored.activeFilePath.takeIf(sourceFiles::containsKey)
      ?: validOpenPaths.firstOrNull()
      ?: sourceFiles.keys.firstOrNull()
      ?: return@withLock null
    val normalizedOpenPaths = if (activeFilePath in validOpenPaths) {
      validOpenPaths
    } else {
      validOpenPaths.take(MAX_OPEN_FILES_PER_PROJECT - 1) + activeFilePath
    }
    val normalized = CodeProjectEditorSession(
      projectId = projectId,
      openFilePaths = normalizedOpenPaths,
      activeFilePath = activeFilePath,
      cursorPositions = stored.cursorPositions.mapNotNull { (path, position) ->
        val source = sourceFiles[path] ?: return@mapNotNull null
        path to position.coerceIn(0, source.length)
      }.toMap(),
    )
    if (normalized != stored) saveEditorSessionLocked(normalized)
    normalized
  }

  /** 保存标签、活动文件和光标；调用方必须先确保路径属于当前项目快照。 */
  suspend fun saveEditorSession(session: CodeProjectEditorSession) = mutex.withLock {
    require(session.projectId.isNotBlank()) { "项目会话缺少 projectId。" }
    require(session.openFilePaths.isNotEmpty()) { "项目会话至少需要一个打开文件。" }
    require(session.activeFilePath in session.openFilePaths) { "活动文件必须位于打开标签中。" }
    session.openFilePaths.forEach(::requireSafeRelativePath)
    session.cursorPositions.forEach { (path, position) ->
      requireSafeRelativePath(path)
      require(position >= 0) { "光标位置不能为负数：$path" }
    }
    val normalizedOpenPaths = session.openFilePaths
      .distinct()
      .let { paths ->
        if (session.activeFilePath in paths.take(MAX_OPEN_FILES_PER_PROJECT)) {
          paths.take(MAX_OPEN_FILES_PER_PROJECT)
        } else {
          paths.filterNot { path -> path == session.activeFilePath }
            .take(MAX_OPEN_FILES_PER_PROJECT - 1) + session.activeFilePath
        }
      }
    saveEditorSessionLocked(
      session.copy(
        openFilePaths = normalizedOpenPaths,
        cursorPositions = session.cursorPositions
          .filterKeys { path -> path in normalizedOpenPaths }
          .entries
          .take(MAX_OPEN_FILES_PER_PROJECT)
          .associate { entry -> entry.key to entry.value },
      ),
    )
  }

  /**
   * 把编辑器文本覆盖写入用户项目目录；调用方应在输入停止后触发。
   *
   * 提供 [expectedSource] 时会先核对磁盘基线，外部程序已修改或删除文件则抛出
   * [CodeProjectSourceConflictException]；磁盘已经等于 [source] 时按幂等成功处理。
   */
  suspend fun saveSource(
    projectId: String,
    relativePath: String,
    source: String,
    expectedSource: String? = null,
  ) = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    val file = resolveFile(context.directory, relativePath, createParents = expectedSource == null)
    if (expectedSource != null) {
      if (!file.isRegularFile()) throw CodeProjectSourceConflictException(relativePath)
      val diskSource = file.readString()
      if (diskSource != expectedSource && diskSource != source) {
        throw CodeProjectSourceConflictException(relativePath)
      }
    }
    file.writeString(source)
  }

  /**
   * 读取项目内单个源码文件的最新磁盘内容，供编辑器在外部修改冲突时执行“重新加载”。
   *
   * 文件已被外部删除或变成目录时直接失败，调用方不得用空文本冒充磁盘内容。
   */
  suspend fun readSource(projectId: String, relativePath: String): String = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    val file = resolveFile(context.directory, relativePath, createParents = false)
    if (!file.isRegularFile()) throw CodeProjectException("源码文件已不存在：$relativePath")
    file.readString()
  }

  /**
   * 无条件用 [source] 覆盖磁盘文件，只有用户在冲突对话框中明确选择覆盖时才能调用。
   *
   * 外部程序已删除文件时会重新创建父目录和文件，因此该接口不能用于普通自动保存。
   */
  suspend fun overwriteSource(projectId: String, relativePath: String, source: String) = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    resolveFile(context.directory, relativePath, createParents = true).writeString(source)
  }

  /**
   * 把冲突中的编辑器文本保存为同目录副本，并将副本设为活动文件。
   *
   * 新文件名保留原扩展名，依次尝试 `-conflict-copy` 与数字后缀；manifest 或 Settings 更新失败
   * 时删除刚创建的副本，避免历史项目记录与真实文件树不一致。
   */
  suspend fun saveSourceConflictCopy(
    projectId: String,
    relativePath: String,
    source: String,
  ): CodeProjectWorkspace = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    val projects = context.projects
    val project = context.project
    val projectDirectory = context.directory
    val copyPath = uniqueConflictCopyPath(projectDirectory, relativePath)
    val copyFile = resolveFile(projectDirectory, copyPath, createParents = true)
    copyFile.writeString(source)
    val updatedProject = project.copy(activeFilePath = copyPath)
    try {
      val snapshot = readWorkspace(projectDirectory)
      writeProjectManifest(projectDirectory, updatedProject)
      saveProjects(projects.replace(updatedProject))
      workspaceOf(updatedProject, projectDirectory, context.displayPath, snapshot)
    } catch (throwable: Throwable) {
      runCatching { copyFile.delete(mustExist = false) }
      runCatching { writeProjectManifest(projectDirectory, project) }
      runCatching { saveProjects(projects) }
      throw throwable
    }
  }

  /**
   * 一次性应用语言服务产生的跨文件源码修改和文件重命名。
   *
   * [updatedSources] 使用重命名后的最终路径。提交前会保存所有受影响文件的原始内容；任一步失败时
   * 尽力恢复原文件并移除新目标，避免只落盘一部分重命名结果。目录重命名不通过该接口处理。
   * 返回更新后的项目元数据，调用方可据此同步活动文件路径。
   */
  suspend fun applySourceTransaction(
    projectId: String,
    updatedSources: Map<String, String>,
    fileRenames: List<CodeProjectFileRename>,
    expectedSources: Map<String, String> = emptyMap(),
  ): CodeProject = mutex.withLock {
    require(updatedSources.isNotEmpty() || fileRenames.isNotEmpty()) {
      "源码事务不能同时缺少内容修改和文件重命名。"
    }
    updatedSources.keys.forEach(::requireSafeRelativePath)
    expectedSources.keys.forEach(::requireSafeRelativePath)
    fileRenames.forEach { rename ->
      requireSafeRelativePath(rename.oldPath)
      requireSafeRelativePath(rename.newPath)
      require(rename.oldPath != rename.newPath) { "文件重命名前后路径不能相同。" }
    }
    require(fileRenames.map(CodeProjectFileRename::oldPath).distinct().size == fileRenames.size) {
      "文件重命名源路径不能重复。"
    }
    require(fileRenames.map(CodeProjectFileRename::newPath).distinct().size == fileRenames.size) {
      "文件重命名目标路径不能重复。"
    }

    val context = requireProjectContext(projectId)
    val projects = context.projects
    val project = context.project
    val projectDirectory = context.directory
    val oldPaths = fileRenames.mapTo(linkedSetOf(), CodeProjectFileRename::oldPath)
    val newPaths = fileRenames.mapTo(linkedSetOf(), CodeProjectFileRename::newPath)
    require(newPaths.none(oldPaths::contains)) {
      "单次源码事务暂不支持文件路径互换，请拆分为两次重命名。"
    }
    fileRenames.forEach { rename ->
      val source = resolveFile(projectDirectory, rename.oldPath, createParents = false)
      if (!source.isRegularFile()) {
        throw CodeProjectException("待重命名文件不存在：${rename.oldPath}")
      }
      val destination = resolveFile(projectDirectory, rename.newPath, createParents = false)
      if (destination.exists()) {
        throw CodeProjectException("文件重命名目标已存在：${rename.newPath}")
      }
    }

    val affectedPaths = linkedSetOf<String>().apply {
      addAll(updatedSources.keys)
      addAll(expectedSources.keys)
      addAll(oldPaths)
      addAll(newPaths)
    }
    val originalSources = affectedPaths.associateWith { path ->
      resolveFile(projectDirectory, path, createParents = false)
        .takeIf(PlatformFile::isRegularFile)
        ?.readString()
    }
    val updatedProject = project.copy(
      activeFilePath = fileRenames.firstOrNull { it.oldPath == project.activeFilePath }?.newPath
        ?: project.activeFilePath,
    )

    expectedSources.forEach { (path, expectedSource) ->
      val diskSource = originalSources[path]
      val desiredSource = updatedSources[path]
      if (diskSource != expectedSource && diskSource != desiredSource) {
        throw CodeProjectSourceConflictException(path)
      }
    }

    try {
      updatedSources.forEach { (path, source) ->
        resolveFile(projectDirectory, path, createParents = true).writeString(source)
      }
      fileRenames.forEach { rename ->
        if (rename.newPath !in updatedSources) {
          val source = originalSources.getValue(rename.oldPath)
            ?: throw CodeProjectException("待重命名文件无法读取：${rename.oldPath}")
          resolveFile(projectDirectory, rename.newPath, createParents = true).writeString(source)
        }
      }
      oldPaths.forEach { path ->
        resolveFile(projectDirectory, path, createParents = false).delete(mustExist = true)
      }
      writeProjectManifest(projectDirectory, updatedProject)
      saveProjects(projects.replace(updatedProject))
      updatedProject
    } catch (throwable: Throwable) {
      affectedPaths.forEach { path ->
        runCatching {
          val file = resolveFile(projectDirectory, path, createParents = originalSources[path] != null)
          val original = originalSources[path]
          if (original == null) {
            if (file.exists()) file.delete(mustExist = false)
          } else {
            file.writeString(original)
          }
        }
      }
      // manifest 与 Settings 也属于同一事务；回滚失败不覆盖最初抛出的文件系统异常。
      runCatching { writeProjectManifest(projectDirectory, project) }
      runCatching { saveProjects(projects) }
      throw throwable
    }
  }

  /** 创建源码文件并写入 [initialSource]；已存在时返回 false，不留下半初始化的空文件。 */
  suspend fun createFile(
    projectId: String,
    relativePath: String,
    initialSource: String = "",
  ): Boolean = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    val file = resolveFile(context.directory, relativePath, createParents = true)
    if (file.exists()) return@withLock false
    file.writeString(initialSource)
    true
  }

  /** 在用户项目目录中创建文件夹；已存在时返回 false。 */
  suspend fun createDirectory(projectId: String, relativePath: String): Boolean = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    val directory = resolveDirectory(context.directory, relativePath, create = false)
    if (directory.exists()) return@withLock false
    resolveDirectory(context.directory, relativePath, create = true)
    true
  }

  /**
   * 重命名或移动项目内的文件/目录，并返回最新磁盘快照。
   *
   * Android SAF 暂不支持目录原子移动，因此底层会回退为递归复制后删除；任一步失败时会尽力
   * 恢复源路径。活动文件位于被移动目录内时会同步改写 manifest 与 Settings。
   */
  suspend fun renamePath(
    projectId: String,
    oldPath: String,
    newPath: String,
  ): CodeProjectWorkspace = mutex.withLock {
    requireSafeRelativePath(oldPath)
    requireSafeRelativePath(newPath)
    require(oldPath != newPath) { "重命名前后路径不能相同。" }

    val context = requireProjectContext(projectId)
    val projects = context.projects
    val project = context.project
    val projectDirectory = context.directory
    val source = resolveFile(projectDirectory, oldPath, createParents = false)
    if (!source.exists()) throw CodeProjectException("待移动路径不存在：$oldPath")
    if (source.isDirectory() && newPath.startsWith("$oldPath/")) {
      throw CodeProjectException("不能把文件夹移动到自身内部。")
    }
    val destination = resolveFile(projectDirectory, newPath, createParents = true)
    if (destination.exists()) throw CodeProjectException("目标路径已存在：$newPath")

    movePathWithFallback(source, destination)
    val updatedProject = project.copy(
      activeFilePath = project.activeFilePath?.remapPath(oldPath, newPath),
    )
    try {
      val snapshot = readWorkspace(projectDirectory)
      writeProjectManifest(projectDirectory, updatedProject)
      saveProjects(projects.replace(updatedProject))
      workspaceOf(updatedProject, projectDirectory, context.displayPath, snapshot)
    } catch (throwable: Throwable) {
      runCatching { movePathWithFallback(destination, source) }
      runCatching { writeProjectManifest(projectDirectory, project) }
      runCatching { saveProjects(projects) }
      throw throwable
    }
  }

  /**
   * 删除项目内的文件或目录，并返回最新磁盘快照。
   *
   * 删除前先移动到项目隔离目录，确保 manifest 或 Settings 写入失败时仍可恢复；项目必须至少保留
   * 一个受支持源码文件。隔离目录清理失败不会让已经完成的逻辑删除回滚，下次扫描会继续忽略它。
   */
  suspend fun deletePath(projectId: String, relativePath: String): CodeProjectWorkspace = mutex.withLock {
    requireSafeRelativePath(relativePath)
    val context = requireProjectContext(projectId)
    val projects = context.projects
    val project = context.project
    val projectDirectory = context.directory
    val source = resolveFile(projectDirectory, relativePath, createParents = false)
    if (!source.exists()) throw CodeProjectException("待删除路径不存在：$relativePath")

    val beforeSnapshot = readWorkspace(projectDirectory)
    val remainingSourcePaths = beforeSnapshot.sourceFiles.keys.filterNot { path ->
      path == relativePath || path.startsWith("$relativePath/")
    }
    if (remainingSourcePaths.isEmpty()) {
      throw CodeProjectException("项目至少需要保留一个可编辑源码文件。")
    }

    val trashDirectory = projectDirectory / PROJECT_TRASH_DIRECTORY_NAME
    trashDirectory.createDirectories()
    val trashTarget = uniqueTrashTarget(trashDirectory, source.name)
    movePathWithFallback(source, trashTarget)
    val updatedProject = project.copy(
      activeFilePath = project.activeFilePath
        ?.takeUnless { path -> path == relativePath || path.startsWith("$relativePath/") }
        ?: remainingSourcePaths.first(),
    )
    val workspace = try {
      val snapshot = readWorkspace(projectDirectory)
      writeProjectManifest(projectDirectory, updatedProject)
      saveProjects(projects.replace(updatedProject))
      workspaceOf(updatedProject, projectDirectory, context.displayPath, snapshot)
    } catch (throwable: Throwable) {
      runCatching { movePathWithFallback(trashTarget, source) }
      runCatching { writeProjectManifest(projectDirectory, project) }
      runCatching { saveProjects(projects) }
      throw throwable
    }

    // 隔离区只承担事务恢复，不作为长期回收站；清理失败时保留数据比破坏已提交状态更安全。
    runCatching { trashTarget.deleteRecursively() }
    runCatching {
      if (trashDirectory.exists() && trashDirectory.list().isEmpty()) {
        trashDirectory.delete(mustExist = false)
      }
    }
    workspace
  }

  /**
   * 修改项目置顶状态，并同时写回 Settings 与项目 manifest。
   *
   * 置顶只改变历史列表分组，不覆盖最近点击时间；取消置顶后会自动回到正常时间顺序。
   */
  suspend fun setProjectPinned(projectId: String, isPinned: Boolean) = mutex.withLock {
    val context = requireProjectContext(projectId)
    val projects = context.projects
    val project = context.project
    if (project.isPinned == isPinned) return@withLock
    val updated = project.copy(isPinned = isPinned)
    writeProjectManifest(context.directory, updated)
    saveProjects(projects.replace(updated))
  }

  /** 从历史列表中移除入口，但不删除用户源码和 manifest。 */
  suspend fun forgetProject(projectId: String) = mutex.withLock {
    removeExternalCodeProjectDirectory(settings, projectId)
    saveProjects(loadIndexedProjects().filterNot { it.projectId == projectId })
    saveEditorSessions(loadEditorSessions().filterNot { session -> session.projectId == projectId })
    val ignored = loadIgnoredProjectIds() + projectId
    saveIgnoredProjectIds(ignored.toList().takeLast(MAX_IGNORED_PROJECTS))
  }

  /** 返回当前已授权的项目目录，供系统文件管理器打开。 */
  suspend fun projectDirectory(projectId: String): PlatformFile = mutex.withLock {
    val context = requireProjectContext(projectId)
    context.directory.takeIf { it.exists() && it.isDirectory() }
      ?: throw CodeProjectException("项目目录已不可访问：${context.project.name}")
  }

  /** 根据磁盘快照构造工作区，统一校验 manifest 中的活动文件仍然存在。 */
  private fun workspaceOf(
    project: CodeProject,
    directory: PlatformFile,
    displayPath: String,
    snapshot: WorkspaceSnapshot,
  ): CodeProjectWorkspace {
    val activeFilePath = project.activeFilePath
      ?.takeIf(snapshot.sourceFiles::containsKey)
      ?: snapshot.sourceFiles.keys.firstOrNull()
      ?: throw CodeProjectException("项目中没有可编辑的源码文件：${project.name}")
    val normalizedProject = project.copy(activeFilePath = activeFilePath)
    return CodeProjectWorkspace(
      project = normalizedProject,
      sourceFiles = snapshot.sourceFiles,
      directoryPaths = snapshot.directoryPaths,
      activeFilePath = activeFilePath,
      directory = directory,
      directoryDisplayPath = displayPath,
    )
  }

  /** 优先使用平台原子移动；不支持目录原子移动的平台回退到可恢复的递归复制。 */
  private suspend fun movePathWithFallback(source: PlatformFile, destination: PlatformFile) {
    try {
      source.atomicMove(destination)
      return
    } catch (atomicFailure: Throwable) {
      if (!source.exists() && destination.exists()) return
      if (destination.exists()) {
        // 目标可能由外部程序在检查后创建；宁可保留 SAF 失败产生的副本，也不能误删未知数据。
        throw atomicFailure
      }
      try {
        source.copyRecursivelyTo(destination)
        source.deleteRecursively()
      } catch (fallbackFailure: Throwable) {
        // 删除源目录中途失败时，从完整目标副本恢复缺失项，再移除未提交的目标路径。
        if (destination.exists()) {
          if (source.exists()) runCatching { destination.copyRecursivelyTo(source) }
          runCatching { destination.deleteRecursively() }
        }
        fallbackFailure.addSuppressed(atomicFailure)
        throw fallbackFailure
      }
    }
  }

  /** 复制完整文件树，保留语言源码之外的资源文件。 */
  private suspend fun PlatformFile.copyRecursivelyTo(destination: PlatformFile) {
    if (isDirectory()) {
      destination.createDirectories()
      list().forEach { child ->
        child.copyRecursivelyTo(destination / child.name)
      }
    } else {
      copyTo(destination)
    }
  }

  /** 先删除子项再删除目录本身，兼容 kotlinx-io 不接受非空目录删除的平台。 */
  private suspend fun PlatformFile.deleteRecursively() {
    if (isDirectory()) list().forEach { child -> child.deleteRecursively() }
    delete(mustExist = false)
  }

  /** 为同一次或遗留删除生成不冲突的隔离目标。 */
  private fun uniqueTrashTarget(trashDirectory: PlatformFile, sourceName: String): PlatformFile {
    val baseName = "${clock()}-$sourceName"
    var candidate = trashDirectory / baseName
    var suffix = 2
    while (candidate.exists()) {
      candidate = trashDirectory / "$baseName-$suffix"
      suffix++
    }
    return candidate
  }

  /** 生成保留原扩展名且不会覆盖现有文件的冲突副本路径。 */
  private fun uniqueConflictCopyPath(projectDirectory: PlatformFile, relativePath: String): String {
    val parentPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
    val fileName = relativePath.substringAfterLast('/')
    val extensionStart = fileName.lastIndexOf('.').takeIf { index -> index > 0 }
    val baseName = extensionStart?.let { index -> fileName.substring(0, index) } ?: fileName
    val extension = extensionStart?.let(fileName::substring) ?: ""
    var suffix = 1
    while (true) {
      val suffixText = if (suffix == 1) "" else "-$suffix"
      val copyName = "$baseName-conflict-copy$suffixText$extension"
      val candidate = if (parentPath.isEmpty()) copyName else "$parentPath/$copyName"
      if (!resolveFile(projectDirectory, candidate, createParents = false).exists()) return candidate
      suffix++
    }
  }

  /** 恢复固定测试目录或平台 bookmark；只在显式创建流程中允许弹出选择器。 */
  private suspend fun resolveStorageRoot(requestIfMissing: Boolean): CodeProjectStorageRoot? {
    projectsRoot?.let { root ->
      root.createDirectories()
      return CodeProjectStorageRoot(root, root.absolutePath())
    }
    return resolveDefaultCodeProjectStorageRoot(settings, requestIfMissing)
  }

  /** 合并 Settings 索引与磁盘 manifest；manifest 让项目可在应用重装后恢复。 */
  private suspend fun loadProjects(root: CodeProjectStorageRoot?): List<CodeProject> {
    val ignored = loadIgnoredProjectIds()
    val discoveredProjects = root?.let { discoverProjects(it.directory) }.orEmpty()
    val merged = (loadIndexedProjects() + discoveredProjects)
      .filterNot { it.projectId in ignored }
      .groupBy(CodeProject::projectId)
      .mapNotNull { (_, versions) ->
        versions.maxByOrNull(CodeProject::lastOpenedAtEpochMilliseconds)
      }
    return merged.sortedForHistory().take(MAX_PROJECTS)
  }

  /** 扫描根目录下受控数量的直接子目录，只接受格式正确且 ID 匹配的 manifest。 */
  private suspend fun discoverProjects(root: PlatformFile): List<CodeProject> {
    val projects = mutableListOf<CodeProject>()
    val directories = runCatching { root.list() }.getOrElse { return emptyList() }
    for (directory in directories) {
      if (projects.size >= MAX_DISCOVERED_DIRECTORIES) break
      if (!directory.isDirectory()) continue
      val manifest = directory / PROJECT_MANIFEST_FILE_NAME
      if (!manifest.isRegularFile() || manifest.size() > MAX_MANIFEST_BYTES) continue
      val project = runCatching {
        json.decodeFromString(
          CodeProjectManifest.serializer(),
          manifest.readString(),
        ).project.copy(
          storageKind = CodeProjectStorageKind.MANAGED_ROOT,
          storageDirectoryName = directory.name,
          directoryDisplayPathHint = directory.name,
        )
      }.getOrNull() ?: continue
      if (project.projectId.isNotBlank() && project.languageId.isNotBlank()) {
        projects += project
      }
    }
    return projects
  }

  /** 递归读取显式目录与受支持文本源码，并限制目录深度、文件数量和总源码体积。 */
  private suspend fun readWorkspace(root: PlatformFile): WorkspaceSnapshot {
    val result = linkedMapOf<String, String>()
    val directoryPaths = linkedSetOf<String>()
    var totalCharacters = 0L

    suspend fun visit(directory: PlatformFile, prefix: String, depth: Int) {
      if (depth > MAX_DIRECTORY_DEPTH) throw CodeProjectException("项目目录层级超过限制。")
      directory.list().sortedBy(PlatformFile::name).forEach { child ->
        if (child.name == PROJECT_MANIFEST_FILE_NAME) return@forEach
        if (child.name in IGNORED_DIRECTORY_NAMES && child.isDirectory()) return@forEach
        val relativePath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
        when {
          child.isDirectory() -> {
            directoryPaths += relativePath
            visit(child, relativePath, depth + 1)
          }
          child.isRegularFile() && child.isSupportedSourceFile() -> {
            if (result.size >= MAX_SOURCE_FILES) {
              throw CodeProjectException("项目源码文件数量超过限制。")
            }
            if (child.size() > MAX_SINGLE_SOURCE_BYTES) {
              throw CodeProjectException("源码文件过大：$relativePath")
            }
            val source = child.readString()
            totalCharacters += source.length
            if (totalCharacters > MAX_TOTAL_SOURCE_CHARACTERS) {
              throw CodeProjectException("项目源码总量超过限制。")
            }
            result[relativePath] = source
          }
        }
      }
    }

    visit(root, prefix = "", depth = 0)
    return WorkspaceSnapshot(sourceFiles = result, directoryPaths = directoryPaths)
  }

  /** 磁盘扫描的不可变结果，显式保留没有源码子项的空目录。 */
  private data class WorkspaceSnapshot(
    val sourceFiles: Map<String, String>,
    val directoryPaths: Set<String>,
  )

  /** 按相对路径逐级创建父目录，兼容 Android SAF URI 与普通文件路径。 */
  private fun resolveFile(root: PlatformFile, path: String, createParents: Boolean): PlatformFile {
    val segments = path.split('/')
    var parent = root
    segments.dropLast(1).forEach { segment ->
      parent = parent / segment
      if (createParents) parent.createDirectories()
    }
    return parent / segments.last()
  }

  /** 按相对路径解析目录；[create] 为 true 时逐级创建缺失目录。 */
  private fun resolveDirectory(root: PlatformFile, path: String, create: Boolean): PlatformFile {
    var directory = root
    path.split('/').forEach { segment ->
      directory = directory / segment
      if (create) directory.createDirectories()
    }
    return directory
  }

  /** 项目 manifest 与源码同目录保存，是跨卸载恢复的唯一事实来源。 */
  private suspend fun writeProjectManifest(directory: PlatformFile, project: CodeProject) {
    (directory / PROJECT_MANIFEST_FILE_NAME).writeString(
      json.encodeToString(
        CodeProjectManifest.serializer(),
        CodeProjectManifest(project = project),
      ),
    )
  }

  /** 读取已有 manifest；格式损坏时拒绝覆盖，避免导入操作破坏其他工具留下的元数据。 */
  private suspend fun readProjectManifest(directory: PlatformFile): CodeProject? {
    val manifest = directory / PROJECT_MANIFEST_FILE_NAME
    if (!manifest.exists()) return null
    if (!manifest.isRegularFile() || manifest.size() > MAX_MANIFEST_BYTES) {
      throw CodeProjectException("项目 manifest 不可读取或超过大小限制。")
    }
    return runCatching {
      json.decodeFromString(CodeProjectManifest.serializer(), manifest.readString()).project
    }.getOrElse { throwable ->
      throw CodeProjectException("项目 manifest 格式无效。", throwable)
    }
  }

  /** 保存导入前的 manifest 原文，失败回滚时不重新序列化未知字段。 */
  private suspend fun readProjectManifestText(directory: PlatformFile): String? {
    val manifest = directory / PROJECT_MANIFEST_FILE_NAME
    return manifest.takeIf(PlatformFile::isRegularFile)?.readString()
  }

  /** 恢复导入前的 manifest；原目录没有 manifest 时移除本次新增文件。 */
  private suspend fun restoreProjectManifest(directory: PlatformFile, original: String?) {
    val manifest = directory / PROJECT_MANIFEST_FILE_NAME
    if (original == null) {
      if (manifest.exists()) manifest.delete(mustExist = false)
    } else {
      manifest.writeString(original)
    }
  }

  /** 从 Settings 读取项目索引；损坏数据按空索引处理，真实项目目录不会被删除。 */
  private fun loadIndexedProjects(): List<CodeProject> {
    val raw = settings.getStringOrNull(PROJECTS_SETTINGS_KEY) ?: return emptyList()
    return runCatching { json.decodeFromString(CodeProjectState.serializer(), raw).projects }
      .getOrElse { emptyList() }
      .distinctBy(CodeProject::projectId)
      .take(MAX_PROJECTS)
  }

  /** 原子更新 Settings 中的轻量历史项目索引，并冻结为与界面一致的排序。 */
  private fun saveProjects(projects: List<CodeProject>) {
    settings.putString(
      PROJECTS_SETTINGS_KEY,
      json.encodeToString(
        CodeProjectState.serializer(),
        CodeProjectState(projects = projects.sortedForHistory().take(MAX_PROJECTS)),
      ),
    )
  }

  /** 会话损坏只丢弃 UI 恢复信息，不影响真实项目和源码。 */
  private fun loadEditorSessions(): List<CodeProjectEditorSession> {
    val raw = settings.getStringOrNull(PROJECT_EDITOR_SESSIONS_SETTINGS_KEY) ?: return emptyList()
    return runCatching {
      json.decodeFromString(CodeProjectEditorSessionState.serializer(), raw).sessions
    }.getOrElse { emptyList() }
      .distinctBy(CodeProjectEditorSession::projectId)
      .take(MAX_PROJECTS)
  }

  /** 替换单个项目会话，同时保留其他历史项目的恢复信息。 */
  private fun saveEditorSessionLocked(session: CodeProjectEditorSession) {
    saveEditorSessions(loadEditorSessions().filterNot { it.projectId == session.projectId } + session)
  }

  private fun saveEditorSessions(sessions: List<CodeProjectEditorSession>) {
    settings.putString(
      PROJECT_EDITOR_SESSIONS_SETTINGS_KEY,
      json.encodeToString(
        CodeProjectEditorSessionState.serializer(),
        CodeProjectEditorSessionState(sessions = sessions.takeLast(MAX_PROJECTS)),
      ),
    )
  }

  private fun loadIgnoredProjectIds(): Set<String> {
    val raw = settings.getStringOrNull(IGNORED_PROJECTS_SETTINGS_KEY) ?: return emptySet()
    return runCatching {
      json.decodeFromString(IgnoredCodeProjectState.serializer(), raw).projectIds.toSet()
    }.getOrElse { emptySet() }
  }

  private fun saveIgnoredProjectIds(projectIds: List<String>) {
    settings.putString(
      IGNORED_PROJECTS_SETTINGS_KEY,
      json.encodeToString(
        IgnoredCodeProjectState.serializer(),
        IgnoredCodeProjectState(projectIds = projectIds.distinct()),
      ),
    )
  }

  private fun removeIgnoredProject(projectId: String) {
    saveIgnoredProjectIds(loadIgnoredProjectIds().filterNot { it == projectId })
  }

  /** 统一解析受管目录和外部 bookmark，业务文件操作无需感知项目来源。 */
  private suspend fun requireProjectContext(projectId: String): ProjectContext {
    val root = resolveStorageRoot(requestIfMissing = false)
    val projects = loadProjects(root)
    val project = projects.firstOrNull { it.projectId == projectId }
      ?: throw CodeProjectException("项目记录不存在：$projectId")
    val directory = resolveProjectDirectory(root, project)
      ?.takeIf { it.exists() && it.isDirectory() }
      ?: throw CodeProjectException("项目目录已不可访问：${project.name}")
    return ProjectContext(
      projects = projects,
      project = project,
      directory = directory,
      displayPath = projectDisplayPath(root, project, directory),
    )
  }

  /** 外部项目从独立 bookmark 恢复；受管项目仍由统一根目录拼接稳定目录名。 */
  private fun resolveProjectDirectory(
    root: CodeProjectStorageRoot?,
    project: CodeProject,
  ): PlatformFile? = when (project.storageKind) {
    CodeProjectStorageKind.MANAGED_ROOT -> root?.directoryFor(project)
    CodeProjectStorageKind.EXTERNAL_BOOKMARK ->
      restoreExternalCodeProjectDirectory(settings, project.projectId)
  }

  /** 可访问时展示真实路径，权限失效时由调用方回退到 manifest 中的目录名提示。 */
  private fun projectDisplayPath(
    root: CodeProjectStorageRoot?,
    project: CodeProject,
    directory: PlatformFile,
  ): String = when (project.storageKind) {
    CodeProjectStorageKind.MANAGED_ROOT -> root?.displayPathFor(project)
      ?: project.directoryDisplayPathHint
      ?: project.storageDirectoryName
    CodeProjectStorageKind.EXTERNAL_BOOKMARK -> directory.externalCodeProjectDisplayPath()
  }

  /** 通过平台可解析绝对标识比较目录；失败时保守地认为不是同一目录。 */
  private fun PlatformFile.isSameDirectoryAs(other: PlatformFile): Boolean = runCatching {
    absolutePath().trimEnd('/', '\\') == other.absolutePath().trimEnd('/', '\\')
  }.getOrDefault(false)

  /** 按常见源码扩展名数量选择项目主语言；并列时按最先出现的源码保持稳定。 */
  private fun detectPrimaryLanguage(sourcePaths: Collection<String>): String {
    val languageIds = sourcePaths.mapNotNull { path ->
      when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "java" -> "java"
        "js", "mjs", "cjs", "ts", "tsx" -> "javascript"
        "py" -> "python"
        "kt", "kts" -> "kotlin"
        else -> null
      }
    }
    return languageIds.groupingBy(String::lowercase).eachCount()
      .maxByOrNull(Map.Entry<String, Int>::value)
      ?.key
      ?: throw CodeProjectException("所选目录中没有可识别语言的源码文件。")
  }

  /** 优先选择各语言惯用入口文件，找不到时使用扫描顺序中的第一个源码。 */
  private fun chooseInitialActiveFile(sourcePaths: Collection<String>): String =
    sourcePaths.firstOrNull { path ->
      path.substringAfterLast('/') in PREFERRED_ENTRY_FILE_NAMES
    } ?: sourcePaths.first()

  /** 一次文件操作所需的完整项目定位信息。 */
  private data class ProjectContext(
    val projects: List<CodeProject>,
    val project: CodeProject,
    val directory: PlatformFile,
    val displayPath: String,
  )

  private fun CodeProjectStorageRoot.directoryFor(project: CodeProject): PlatformFile =
    directory / project.storageDirectoryName

  private fun CodeProjectStorageRoot.displayPathFor(project: CodeProject): String =
    "${displayPath.trimEnd('/', '\\')}/${project.storageDirectoryName}"

  /** 校验用户输入的展示名称；名称不会直接作为文件路径，但仍拒绝控制字符和异常长度。 */
  private fun normalizeProjectName(projectName: String): String {
    val normalized = projectName.trim()
    if (normalized.isEmpty()) throw CodeProjectException("项目名称不能为空。")
    if (normalized.length > MAX_PROJECT_NAME_LENGTH) {
      throw CodeProjectException("项目名称不能超过 $MAX_PROJECT_NAME_LENGTH 个字符。")
    }
    if (normalized.any(Char::isISOControl)) {
      throw CodeProjectException("项目名称不能包含控制字符。")
    }
    return normalized
  }

  /** manifest 项目 ID 会进入 Settings key，必须保持短小且只包含稳定 ASCII 路径字符。 */
  private fun requireSafeProjectId(projectId: String) {
    if (
      projectId.length > MAX_PROJECT_ID_LENGTH ||
      projectId.any { character ->
        character !in 'a'..'z' &&
          character !in 'A'..'Z' &&
          character !in '0'..'9' &&
          character != '-' && character != '_' && character != '.'
      }
    ) {
      throw CodeProjectException("项目 manifest 中的项目 ID 无效。")
    }
  }

  /** 生成仅由安全路径字符组成的稳定 ID；同毫秒创建时继续增加后缀。 */
  private fun uniqueProjectId(languageId: String, projects: List<CodeProject>): String {
    val existingIds = projects.mapTo(hashSetOf(), CodeProject::projectId)
    val base = "${languageId.filter { it.isLetterOrDigit() || it == '-' }}-${clock()}"
    if (base !in existingIds) return base
    var suffix = 2
    while ("$base-$suffix" in existingIds) suffix++
    return "$base-$suffix"
  }

  private fun List<CodeProject>.replace(project: CodeProject): List<CodeProject> =
    filterNot { it.projectId == project.projectId } + project

  /** 将活动文件从旧文件/目录前缀映射到新路径。 */
  private fun String.remapPath(oldPath: String, newPath: String): String = when {
    this == oldPath -> newPath
    startsWith("$oldPath/") -> newPath + removePrefix(oldPath)
    else -> this
  }

  /** 置顶项目始终在前；同一分组内按最后一次成功打开的时间倒序。 */
  private fun List<CodeProject>.sortedForHistory(): List<CodeProject> = sortedWith(
    compareByDescending<CodeProject>(CodeProject::isPinned)
      .thenByDescending(CodeProject::lastOpenedAtEpochMilliseconds),
  )

  /** 拒绝绝对路径、反斜杠和目录穿越，保证所有文件操作停留在项目根目录。 */
  private fun requireSafeRelativePath(path: String) {
    val segments = path.split('/')
    require(
      path.isNotBlank() &&
        path == path.trim() &&
        !path.startsWith('/') &&
        '\\' !in path &&
        segments.firstOrNull() !in RESERVED_PROJECT_PATHS &&
        segments.none { it.isEmpty() || it == "." || it == ".." },
    ) { "非法项目相对路径：$path" }
  }

  private fun PlatformFile.isSupportedSourceFile(): Boolean {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in SUPPORTED_SOURCE_EXTENSIONS
  }

  /** 从模板文件路径推导首次创建项目时已经存在的父目录。 */
  private fun Collection<String>.parentDirectoryPaths(): Set<String> = buildSet {
    this@parentDirectoryPaths.forEach { path ->
      val segments = path.split('/').dropLast(1)
      segments.indices.forEach { index -> add(segments.take(index + 1).joinToString("/")) }
    }
  }

  @Serializable
  private data class CodeProjectState(
    val schemaVersion: Int = PROJECTS_SCHEMA_VERSION,
    val projects: List<CodeProject> = emptyList(),
  )

  @Serializable
  private data class CodeProjectManifest(
    val schemaVersion: Int = PROJECT_MANIFEST_SCHEMA_VERSION,
    val project: CodeProject,
  )

  @Serializable
  private data class IgnoredCodeProjectState(
    val projectIds: List<String> = emptyList(),
  )

  @Serializable
  private data class CodeProjectEditorSessionState(
    val schemaVersion: Int = PROJECT_EDITOR_SESSIONS_SCHEMA_VERSION,
    val sessions: List<CodeProjectEditorSession> = emptyList(),
  )

  private companion object {
    const val PROJECTS_SETTINGS_KEY = "code.editor.recent-projects"
    const val IGNORED_PROJECTS_SETTINGS_KEY = "code.editor.ignored-projects"
    const val PROJECT_EDITOR_SESSIONS_SETTINGS_KEY = "code.editor.project-sessions"
    const val PROJECTS_SCHEMA_VERSION = 3
    const val PROJECT_MANIFEST_SCHEMA_VERSION = 1
    const val PROJECT_EDITOR_SESSIONS_SCHEMA_VERSION = 1
    const val PROJECT_MANIFEST_FILE_NAME = ".cyxbs-project.json"
    const val PROJECT_TRASH_DIRECTORY_NAME = ".cyxbs-trash"
    const val MAX_PROJECTS = 20
    const val MAX_IGNORED_PROJECTS = 100
    const val MAX_PROJECT_NAME_LENGTH = 64
    const val MAX_PROJECT_ID_LENGTH = 128
    const val MAX_OPEN_FILES_PER_PROJECT = 50
    const val MAX_DISCOVERED_DIRECTORIES = 200
    const val MAX_MANIFEST_BYTES = 64L * 1024L
    const val MAX_DIRECTORY_DEPTH = 16
    const val MAX_SOURCE_FILES = 1_000
    const val MAX_SINGLE_SOURCE_BYTES = 2L * 1024L * 1024L
    const val MAX_TOTAL_SOURCE_CHARACTERS = 8L * 1024L * 1024L

    val IGNORED_DIRECTORY_NAMES = setOf(
      ".git", ".gradle", PROJECT_TRASH_DIRECTORY_NAME, "build", "node_modules",
    )
    val RESERVED_PROJECT_PATHS = setOf(PROJECT_MANIFEST_FILE_NAME, PROJECT_TRASH_DIRECTORY_NAME)
    val SUPPORTED_SOURCE_EXTENSIONS = setOf(
      "java", "js", "mjs", "cjs", "ts", "tsx", "py", "kt", "kts", "json", "md", "txt",
    )
    val PREFERRED_ENTRY_FILE_NAMES = setOf(
      "Main.java", "main.js", "main.mjs", "main.cjs", "main.ts", "main.py", "Main.kt", "main.kts",
    )
  }
}

/** 项目目录缺失、授权失效或超过客户端资源边界。 */
class CodeProjectException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
