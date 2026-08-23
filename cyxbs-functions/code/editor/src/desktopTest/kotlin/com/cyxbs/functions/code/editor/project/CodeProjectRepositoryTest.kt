package com.cyxbs.functions.code.editor.project

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
    root.deleteRecursively()
    preferences.removeNode()
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
    assertTrue(repository.createFile(workspace.project.projectId, "src/model/Student.java"))
    assertFalse(repository.createFile(workspace.project.projectId, "src/model/Student.java"))
    assertTrue(
      Files.isRegularFile(
        root.resolve(workspace.project.projectId).resolve("src/model/Student.java"),
      ),
    )

    assertFailsWith<IllegalArgumentException> {
      repository.createFile(workspace.project.projectId, "../outside.java")
    }
    Unit
  }
}
