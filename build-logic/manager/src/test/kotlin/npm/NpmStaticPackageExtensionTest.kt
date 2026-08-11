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

  /** 静态包应复用 debug 注入任务，但不得被迫配置 Kotlin/JS Runtime。 */
  @Test
  fun registersDebugBundleInstallWithoutRuntime() {
    withProject { project ->
      project.version = "1.2.3"
      project.createNpmStaticPackageExtension()

      project.configureNpmStaticPackaging()

      val task = project.tasks.named<InstallDebugNpmBundleTask>("installDebugNpmBundle").get()
      assertFalse(task.runtimePackageDirectory.isPresent)
      assertFalse(task.runtimePackageName.isPresent)
      assertFalse(task.runtimeStableVersion.isPresent)
      assertEquals("com.mredrock.cyxbs.test", task.applicationId.get())
      assertEquals(
        project.layout.buildDirectory.dir("npm/debug-bundle").get().asFile,
        task.workingDirectory.get().asFile,
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
