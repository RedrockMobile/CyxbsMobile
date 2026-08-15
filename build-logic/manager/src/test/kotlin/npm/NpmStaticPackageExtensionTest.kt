package npm

import org.gradle.api.Project
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证静态 npm 资源使用独立源集，并同步为 Android Studio 可识别的 Resources Root。 */
class NpmStaticPackageExtensionTest {

  /** 默认源集必须是 src/resources，不能占用 Java/Kotlin 的 src/main 目录约定。 */
  @Test
  fun usesResourcesSourceSetAndRegistersIdeaResourceRoot() {
    withProject { project ->
      project.pluginManager.apply("idea")
      val extension = project.createNpmStaticPackageExtension()

      assertEquals(
        setOf(project.file("src/resources")),
        extension.resources.srcDirs,
      )

      project.configureNpmStaticIdeaModel()
      assertTrue(
        project.extensions.getByType<IdeaModel>().module.resourceDirs.contains(
          project.file("src/resources"),
        ),
      )
    }
  }

  /** 静态包应先生成统一调试源，再由独立 Android 任务消费，且不得配置 Kotlin/JS Runtime。 */
  @Test
  fun registersAndroidDebugBundleInstallWithoutRuntime() {
    withProject { project ->
      project.version = "1.2.3"
      project.createNpmStaticPackageExtension()

      project.configureNpmStaticPackaging()

      val prepareTask = project.tasks.named<PrepareDebugNpmBundleTask>("prepareDebugNpmBundle").get()
      assertFalse(prepareTask.runtimePackageDirectory.isPresent)
      assertFalse(prepareTask.runtimePackageName.isPresent)
      assertFalse(prepareTask.runtimeStableVersion.isPresent)
      assertEquals(
        project.layout.buildDirectory.dir("npm/debug-bundle").get().asFile,
        prepareTask.workingDirectory.get().asFile,
      )
      assertEquals(
        project.rootProject.layout.buildDirectory.dir("npm/debug-source").get().asFile,
        prepareTask.debugSourceDirectory.get().asFile,
      )

      val installTask = project.tasks.named<InstallAndroidDebugNpmBundleTask>(
        "installAndroidDebugNpmBundle",
      ).get()
      assertEquals("com.mredrock.cyxbs.test", installTask.applicationId.get())
      assertEquals(prepareTask.manifestFile.get(), installTask.manifestFile.get())
      assertTrue(
        installTask.taskDependencies.getDependencies(installTask).contains(prepareTask),
      )
    }
  }

  /** 创建隔离 Project 并在测试后清理目录。 */
  private fun withProject(block: (Project) -> Unit) {
    val root = Files.createTempDirectory("npm-static-extension-test").toFile()
    try {
      val parent = ProjectBuilder.builder()
        .withProjectDir(root.resolve("root").apply { mkdirs() })
        .build()
      block(
        ProjectBuilder.builder()
          .withName("catalog")
          .withParent(parent)
          .withProjectDir(root.resolve("catalog").apply { mkdirs() })
          .build(),
      )
    } finally {
      root.deleteRecursively()
    }
  }
}
