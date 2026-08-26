package com.cyxbs.functions.code.editor.project

import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.PreferencesSettings
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 真实项目目录、历史项目索引、命名和路径安全边界的桌面端回归。 */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalPathApi::class)
class CodeProjectRepositoryTest {
  private lateinit var root: Path
  private lateinit var preferences: Preferences
  private lateinit var repository: CodeProjectRepository
  private val externalRoots = mutableListOf<Path>()
  private var now = 1_000L

  /** 每个测试使用独立临时目录和 Preferences 节点，不读取开发机真实项目记录。 */
  @BeforeTest
  fun setUp() {
    root = Files.createTempDirectory("cyxbs-code-project-test-")
    preferences = Preferences.userRoot().node("cyxbs-code-project-test-${UUID.randomUUID()}")
    repository = CodeProjectRepository(
      settings = PreferencesSettings(preferences),
      projectsRoot = PlatformFile(root.toString()),
      clock = { now++ },
    )
  }

  /** 只清理当前测试创建的数据，不触碰业务默认目录。 */
  @AfterTest
  fun tearDown() {
    externalRoots.forEach(Path::deleteRecursively)
    externalRoots.clear()
    root.deleteRecursively()
    preferences.removeNode()
  }

  @Test
  fun importsExistingDirectoryWithoutCopyingSources() = runBlocking {
    val externalRoot = createExternalProject("existing-java-project")
    externalRoot.resolve("src").let(Files::createDirectories)
    externalRoot.resolve("src/Main.java").writeText("public class Main {}")
    repository = repositoryWithPicker(externalRoot)

    val imported = requireNotNull(repository.importProject(TEST_LANGUAGES))

    assertEquals(CodeProjectStorageKind.EXTERNAL_BOOKMARK, imported.project.storageKind)
    assertEquals("java", imported.project.languageId)
    assertEquals("src/Main.java", imported.activeFilePath)
    assertEquals("public class Main {}", imported.sourceFiles.getValue("src/Main.java"))
    assertEquals(externalRoot.toString(), imported.directoryDisplayPath)
    assertFalse(Files.exists(root.resolve(imported.project.projectId)))
    assertTrue(Files.isRegularFile(externalRoot.resolve(".cyxbs-project.json")))
  }

  @Test
  fun restoresImportedProjectFromBookmarkAndWritesBackToOriginalDirectory() = runBlocking {
    val externalRoot = createExternalProject("bookmark-project")
    externalRoot.resolve("main.js").writeText("console.log('first')")
    repository = repositoryWithPicker(externalRoot)
    val imported = requireNotNull(repository.importProject(TEST_LANGUAGES))

    val restoredRepository = CodeProjectRepository(
      settings = PreferencesSettings(preferences),
      projectsRoot = PlatformFile(root.toString()),
      clock = { now++ },
    )
    val reopened = restoredRepository.openProject(imported.project.projectId)
    restoredRepository.saveSource(
      projectId = imported.project.projectId,
      relativePath = "main.js",
      source = "console.log('updated')",
      expectedSource = "console.log('first')",
    )

    assertEquals("javascript", reopened.project.languageId)
    assertEquals("console.log('updated')", externalRoot.resolve("main.js").readText())
    assertTrue(restoredRepository.historicalProjects().single().isAvailable)
  }

  @Test
  fun reimportingSameDirectoryKeepsSingleStableProject() = runBlocking {
    val externalRoot = createExternalProject("stable-project")
    externalRoot.resolve("src").let(Files::createDirectories)
    externalRoot.resolve("src/App.kt").writeText("fun main() = Unit")
    repository = repositoryWithPicker(externalRoot)

    val first = requireNotNull(repository.importProject(TEST_LANGUAGES))
    val second = requireNotNull(repository.importProject(TEST_LANGUAGES))

    assertEquals(first.project.projectId, second.project.projectId)
    assertEquals("kotlin", second.project.languageId)
    assertEquals(listOf(first.project.projectId), repository.historicalProjects().map { it.project.projectId })
  }

  @Test
  fun rejectsImportedDirectoryWithoutRecognizableLanguageSources() = runBlocking {
    val externalRoot = createExternalProject("text-only-project")
    externalRoot.resolve("README.md").writeText("documentation")
    repository = repositoryWithPicker(externalRoot)

    assertFailsWith<CodeProjectException> { repository.importProject(TEST_LANGUAGES) }
    assertFalse(Files.exists(externalRoot.resolve(".cyxbs-project.json")))
  }

  @Test
  fun importsCatalogLanguageWithoutClientExtensionChanges() = runBlocking {
    val externalRoot = createExternalProject("catalog-language-project")
    externalRoot.resolve("src").let(Files::createDirectories)
    externalRoot.resolve("src/Main.demo").writeText("demo source")
    repository = repositoryWithPicker(externalRoot)

    val imported = requireNotNull(repository.importProject(TEST_LANGUAGES))
    val reopened = repository.openProject(imported.project.projectId)

    assertEquals("demo", imported.project.languageId)
    assertTrue("demo" in imported.project.sourceFileExtensions)
    assertEquals("demo source", reopened.sourceFiles.getValue("src/Main.demo"))
  }

  @Test
  fun relinksUnavailableExternalProjectByStableManifestIdentity() = runBlocking {
    val originalRoot = createExternalProject("relink-original")
    originalRoot.resolve("Main.java").writeText("class Main {}")
    repository = repositoryWithPicker(originalRoot)
    val imported = requireNotNull(repository.importProject(TEST_LANGUAGES))
    val manifestText = originalRoot.resolve(".cyxbs-project.json").readText()

    originalRoot.deleteRecursively()
    externalRoots.remove(originalRoot)
    val relocatedRoot = createExternalProject("relink-target")
    relocatedRoot.resolve("Main.java").writeText("class Main { int value; }")
    relocatedRoot.resolve(".cyxbs-project.json").writeText(manifestText)
    repository = repositoryWithPicker(relocatedRoot)

    assertFalse(repository.historicalProjects().single().isAvailable)
    val relinked = requireNotNull(repository.relinkExternalProject(imported.project.projectId))

    assertEquals("class Main { int value; }", relinked.sourceFiles.getValue("Main.java"))
    assertEquals(relocatedRoot.toString(), relinked.directoryDisplayPath)
    assertTrue(repository.historicalProjects().single().isAvailable)
  }

  @Test
  fun rejectsRelinkToDifferentProjectWithoutReplacingHistory() = runBlocking {
    val originalRoot = createExternalProject("relink-wrong-original")
    originalRoot.resolve("Main.java").writeText("class Main {}")
    repository = repositoryWithPicker(originalRoot)
    val imported = requireNotNull(repository.importProject(TEST_LANGUAGES))
    val wrongManifest = originalRoot.resolve(".cyxbs-project.json").readText()
      .replace(imported.project.projectId, "different-project-id")

    originalRoot.deleteRecursively()
    externalRoots.remove(originalRoot)
    val wrongRoot = createExternalProject("relink-wrong-target")
    wrongRoot.resolve("Main.java").writeText("class Wrong {}")
    wrongRoot.resolve(".cyxbs-project.json").writeText(wrongManifest)
    repository = repositoryWithPicker(wrongRoot)

    assertFailsWith<CodeProjectException> {
      repository.relinkExternalProject(imported.project.projectId)
    }
    val historical = repository.historicalProjects().single()
    assertEquals(imported.project.projectId, historical.project.projectId)
    assertFalse(historical.isAvailable)
  }

  @Test
  fun createsRealFilesAndReopensExternalChanges() = runBlocking {
    val created = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "算法练习",
    )
    val mainFile = root.resolve(created.project.projectId).resolve("src/Main.java")

    assertTrue(Files.isRegularFile(mainFile))
    assertEquals(created.sourceFiles.getValue("src/Main.java"), mainFile.readText())

    mainFile.writeText("public class Main { }")
    val reopened = repository.openProject(created.project.projectId)

    assertEquals("public class Main { }", reopened.sourceFiles.getValue("src/Main.java"))
    assertEquals("算法练习", reopened.project.name)
    assertEquals("src/Main.java", reopened.activeFilePath)
    assertEquals(created.project.projectId, repository.historicalProjects().single().project.projectId)
  }

  @Test
  fun restoresEditorSessionAndRemovesStaleFiles() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "恢复标签",
    )
    val projectId = workspace.project.projectId
    assertTrue(repository.createFile(projectId, "src/Second.java", "class Second {}"))
    repository.saveEditorSession(
      CodeProjectEditorSession(
        projectId = projectId,
        openFilePaths = listOf("src/Main.java", "src/Second.java", "src/Removed.java"),
        activeFilePath = "src/Second.java",
        cursorPositions = mapOf(
          "src/Main.java" to 10_000,
          "src/Second.java" to 6,
          "src/Removed.java" to 3,
        ),
      ),
    )
    Files.delete(root.resolve(projectId).resolve("src/Second.java"))
    val reopened = repository.openProject(projectId)

    val session = requireNotNull(repository.loadEditorSession(projectId, reopened.sourceFiles))

    assertEquals(listOf("src/Main.java"), session.openFilePaths)
    assertEquals("src/Main.java", session.activeFilePath)
    assertEquals(reopened.sourceFiles.getValue("src/Main.java").length, session.cursorPositions["src/Main.java"])
    assertFalse(session.cursorPositions.containsKey("src/Removed.java"))
  }

  @Test
  fun forgettingProjectAlsoRemovesEditorSession() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "清理标签",
    )
    repository.saveEditorSession(
      CodeProjectEditorSession(
        projectId = workspace.project.projectId,
        openFilePaths = listOf(workspace.activeFilePath),
        activeFilePath = workspace.activeFilePath,
      ),
    )

    repository.forgetProject(workspace.project.projectId)

    assertEquals(
      null,
      repository.loadEditorSession(workspace.project.projectId, workspace.sourceFiles),
    )
  }

  @Test
  fun restoresProjectFromManifestAfterApplicationSettingsAreLost() = runBlocking {
    val created = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "Java 入门",
    )
    val manifest = root.resolve(created.project.projectId).resolve(".cyxbs-project.json")
    assertTrue(Files.isRegularFile(manifest))

    // 模拟卸载后应用 Settings 与 bookmark 消失，但用户选择目录中的项目文件仍然存在。
    preferences.clear()
    val restoredRepository = CodeProjectRepository(
      settings = PreferencesSettings(preferences),
      projectsRoot = PlatformFile(root.toString()),
      clock = { now++ },
    )

    val restoredRecent = restoredRepository.historicalProjects().single()
    val restoredWorkspace = restoredRepository.openProject(restoredRecent.project.projectId)

    assertEquals(created.project.projectId, restoredRecent.project.projectId)
    assertTrue(restoredRecent.isAvailable)
    assertEquals(created.sourceFiles, restoredWorkspace.sourceFiles)
  }

  @Test
  fun persistsActiveFileAndSortsHistoryByLatestOpen() = runBlocking {
    val first = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "Java 项目",
    )
    val second = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("javascript")),
      "JavaScript 项目",
    )
    repository.updateActiveFile(second.project.projectId, "src/main.js")
    repository.openProject(first.project.projectId)

    val recent = repository.historicalProjects()

    assertEquals(first.project.projectId, recent.first().project.projectId)
    assertEquals("src/main.js", recent.last().project.activeFilePath)
    assertTrue(recent.all(HistoricalCodeProject::isAvailable))
  }

  @Test
  fun pinsHistoryAndRemovesEntryWithoutDeletingSource() = runBlocking {
    val first = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "置顶项目",
    )
    val second = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("javascript")),
      "普通项目",
    )

    repository.setProjectPinned(first.project.projectId, isPinned = true)
    repository.openProject(second.project.projectId)

    val pinnedHistory = repository.historicalProjects()
    assertEquals(first.project.projectId, pinnedHistory.first().project.projectId)
    assertTrue(pinnedHistory.first().project.isPinned)

    repository.setProjectPinned(first.project.projectId, isPinned = false)
    assertEquals(second.project.projectId, repository.historicalProjects().first().project.projectId)

    repository.forgetProject(second.project.projectId)
    assertEquals(listOf(first.project.projectId), repository.historicalProjects().map { it.project.projectId })
    assertTrue(Files.isDirectory(root.resolve(second.project.projectId)))
  }

  @Test
  fun rejectsBlankAndDuplicateProjectNames() = runBlocking {
    val template = requireNotNull(CodeProjectTemplates.find("java"))
    repository.createProject(template, "同名项目")

    assertFailsWith<CodeProjectException> {
      repository.createProject(template, "   ")
    }
    assertFailsWith<CodeProjectException> {
      repository.createProject(template, "同名项目")
    }
    Unit
  }

  @Test
  fun createsFilesAndDirectoriesInsideProjectOnly() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "文件操作",
    )

    assertTrue(repository.createDirectory(workspace.project.projectId, "src/model"))
    assertTrue(
      repository.createFile(
        workspace.project.projectId,
        "src/model/Student.java",
        "public class Student {}",
      ),
    )
    assertFalse(repository.createFile(workspace.project.projectId, "src/model/Student.java"))
    assertTrue(
      Files.isRegularFile(
        root.resolve(workspace.project.projectId).resolve("src/model/Student.java"),
      ),
    )
    assertEquals(
      "public class Student {}",
      root.resolve(workspace.project.projectId).resolve("src/model/Student.java").readText(),
    )

    assertFailsWith<IllegalArgumentException> {
      repository.createFile(workspace.project.projectId, "../outside.java")
    }
    Unit
  }

  @Test
  fun restoresEmptyDirectoriesWhenProjectIsReopened() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "空目录恢复",
    )

    assertTrue(repository.createDirectory(workspace.project.projectId, "src/generated/model"))
    val reopened = repository.openProject(workspace.project.projectId)

    assertTrue("src" in reopened.directoryPaths)
    assertTrue("src/generated" in reopened.directoryPaths)
    assertTrue("src/generated/model" in reopened.directoryPaths)
  }

  @Test
  fun appliesCrossFileSourceTransactionAndPersistsRenamedActiveFile() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "事务重命名",
    )
    val projectId = workspace.project.projectId
    assertTrue(repository.createFile(projectId, "src/OldName.java"))
    repository.saveSource(projectId, "src/OldName.java", "public class OldName {}")
    repository.updateActiveFile(projectId, "src/OldName.java")

    val updatedProject = repository.applySourceTransaction(
      projectId = projectId,
      updatedSources = mapOf(
        "src/Main.java" to "public class Main { NewName value; }",
        "src/NewName.java" to "public class NewName {}",
      ),
      fileRenames = listOf(
        CodeProjectFileRename("src/OldName.java", "src/NewName.java"),
      ),
    )
    val reopened = repository.openProject(projectId)

    assertEquals("src/NewName.java", updatedProject.activeFilePath)
    assertEquals("src/NewName.java", reopened.activeFilePath)
    assertEquals("public class NewName {}", reopened.sourceFiles.getValue("src/NewName.java"))
    assertEquals("public class Main { NewName value; }", reopened.sourceFiles.getValue("src/Main.java"))
    assertFalse(Files.exists(root.resolve(projectId).resolve("src/OldName.java")))
  }

  @Test
  fun rejectsRenameCollisionWithoutChangingExistingFiles() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "重命名冲突",
    )
    val projectId = workspace.project.projectId
    assertTrue(
      repository.createFile(
        projectId,
        "src/OldName.java",
        "public class OldName {}",
      ),
    )

    assertFailsWith<CodeProjectException> {
      repository.applySourceTransaction(
        projectId = projectId,
        updatedSources = mapOf("src/Main.java" to "changed"),
        fileRenames = listOf(CodeProjectFileRename("src/OldName.java", "src/Main.java")),
      )
    }
    val reopened = repository.openProject(projectId)

    assertEquals(
      workspace.sourceFiles.getValue("src/Main.java"),
      reopened.sourceFiles.getValue("src/Main.java"),
    )
    assertEquals("public class OldName {}", reopened.sourceFiles.getValue("src/OldName.java"))
  }

  @Test
  fun renamesDirectoryWithSourceAndResourceFilesAndRemapsActiveFile() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "目录移动",
    )
    val projectId = workspace.project.projectId
    assertTrue(
      repository.createFile(
        projectId,
        "src/model/Student.java",
        "public class Student {}",
      ),
    )
    val resource = root.resolve(projectId).resolve("src/model/schema.bin")
    resource.writeText("resource")
    repository.updateActiveFile(projectId, "src/model/Student.java")

    val renamed = repository.renamePath(projectId, "src/model", "src/domain")

    assertEquals("src/domain/Student.java", renamed.activeFilePath)
    assertEquals("public class Student {}", renamed.sourceFiles.getValue("src/domain/Student.java"))
    assertEquals("resource", root.resolve(projectId).resolve("src/domain/schema.bin").readText())
    assertFalse(Files.exists(root.resolve(projectId).resolve("src/model")))
  }

  @Test
  fun deletesActiveDirectoryThroughIsolationAreaAndSelectsRemainingSource() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "目录删除",
    )
    val projectId = workspace.project.projectId
    assertTrue(repository.createFile(projectId, "src/temp/Temp.java", "class Temp {}"))
    repository.updateActiveFile(projectId, "src/temp/Temp.java")

    val afterDelete = repository.deletePath(projectId, "src/temp")

    assertEquals("src/Main.java", afterDelete.activeFilePath)
    assertFalse(afterDelete.sourceFiles.containsKey("src/temp/Temp.java"))
    assertFalse(Files.exists(root.resolve(projectId).resolve("src/temp")))
    assertFalse(Files.exists(root.resolve(projectId).resolve(".cyxbs-trash")))
  }

  @Test
  fun refusesToDeleteLastSourceFile() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "保留源码",
    )

    assertFailsWith<CodeProjectException> {
      repository.deletePath(workspace.project.projectId, "src/Main.java")
    }
    assertTrue(Files.isRegularFile(root.resolve(workspace.project.projectId).resolve("src/Main.java")))
  }

  @Test
  fun rejectsSaveWhenDiskSourceChangedOutsideEditor() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "外部冲突",
    )
    val projectId = workspace.project.projectId
    val original = workspace.sourceFiles.getValue("src/Main.java")
    val mainFile = root.resolve(projectId).resolve("src/Main.java")
    mainFile.writeText("external change")

    assertFailsWith<CodeProjectSourceConflictException> {
      repository.saveSource(
        projectId = projectId,
        relativePath = "src/Main.java",
        source = "editor change",
        expectedSource = original,
      )
    }
    assertEquals("external change", mainFile.readText())
  }

  @Test
  fun reloadsAndExplicitlyOverwritesExternalSource() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "解决外部冲突",
    )
    val projectId = workspace.project.projectId
    val mainFile = root.resolve(projectId).resolve("src/Main.java")
    mainFile.writeText("external change")

    assertEquals("external change", repository.readSource(projectId, "src/Main.java"))
    repository.overwriteSource(projectId, "src/Main.java", "editor change")

    assertEquals("editor change", mainFile.readText())
  }

  @Test
  fun savesConflictCopyWithoutReplacingExternalSource() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "保留冲突副本",
    )
    val projectId = workspace.project.projectId
    val mainFile = root.resolve(projectId).resolve("src/Main.java")
    mainFile.writeText("external change")

    val firstCopy = repository.saveSourceConflictCopy(
      projectId = projectId,
      relativePath = "src/Main.java",
      source = "editor change 1",
    )
    val secondCopy = repository.saveSourceConflictCopy(
      projectId = projectId,
      relativePath = "src/Main.java",
      source = "editor change 2",
    )

    assertEquals("external change", mainFile.readText())
    assertEquals("src/Main-conflict-copy.java", firstCopy.activeFilePath)
    assertEquals("editor change 1", firstCopy.sourceFiles.getValue(firstCopy.activeFilePath))
    assertEquals("src/Main-conflict-copy-2.java", secondCopy.activeFilePath)
    assertEquals("editor change 2", secondCopy.sourceFiles.getValue(secondCopy.activeFilePath))
  }

  @Test
  fun protectsProjectMetadataPathsFromFileOperations() = runBlocking {
    val workspace = repository.createProject(
      requireNotNull(CodeProjectTemplates.find("java")),
      "保留路径",
    )

    assertFailsWith<IllegalArgumentException> {
      repository.createFile(workspace.project.projectId, ".cyxbs-project.json")
    }
    assertFailsWith<IllegalArgumentException> {
      repository.createDirectory(workspace.project.projectId, ".cyxbs-trash/custom")
    }
    Unit
  }

  /** 创建独立于受管项目根目录的现有工程，验证导入过程不会复制源码。 */
  private fun createExternalProject(name: String): Path =
    Files.createTempDirectory("cyxbs-$name-").also(externalRoots::add)

  /** 注入固定目录选择结果，单元测试不打开桌面系统选择器。 */
  private fun repositoryWithPicker(directory: Path): CodeProjectRepository = CodeProjectRepository(
    settings = PreferencesSettings(preferences),
    projectsRoot = PlatformFile(root.toString()),
    clock = { now++ },
    externalProjectDirectoryPicker = { PlatformFile(directory.toString()) },
  )
}

/** 仓库测试专用模板；生产代码的项目源码必须由动态语言 npm 包提供。 */
private object CodeProjectTemplates {
  private val templates = listOf(
    CodeProjectTemplate(
      languageId = "java",
      displayName = "Java",
      defaultProjectName = "JavaProject",
      activeFilePath = "src/Main.java",
      sourceFiles = mapOf("src/Main.java" to "public class Main {}"),
    ),
    CodeProjectTemplate(
      languageId = "javascript",
      displayName = "JavaScript",
      defaultProjectName = "JavaScriptProject",
      activeFilePath = "src/main.js",
      sourceFiles = mapOf("src/main.js" to "console.log('Hello')"),
    ),
  )

  /** 按稳定语言 ID 返回可重复使用的仓库测试数据。 */
  fun find(languageId: String): CodeProjectTemplate? =
    templates.firstOrNull { it.languageId == languageId }
}

/** 模拟动态 Catalog；`demo` 用于证明新增语言不需要修改项目仓库扩展名分支。 */
private val TEST_LANGUAGES = listOf(
  DynamicLanguageInfo(
    languageId = "java",
    displayName = "Java",
    npmPackageName = "@cyxbs-mobile/language-java",
    fileExtensions = listOf("java"),
  ),
  DynamicLanguageInfo(
    languageId = "javascript",
    displayName = "JavaScript",
    npmPackageName = "@cyxbs-mobile/language-javascript",
    fileExtensions = listOf("js", "mjs", "cjs"),
  ),
  DynamicLanguageInfo(
    languageId = "kotlin",
    displayName = "Kotlin",
    npmPackageName = "@cyxbs-mobile/language-kotlin",
    fileExtensions = listOf("kt", "kts"),
  ),
  DynamicLanguageInfo(
    languageId = "demo",
    displayName = "Demo",
    npmPackageName = "@cyxbs-mobile/language-demo",
    fileExtensions = listOf("demo"),
  ),
)
