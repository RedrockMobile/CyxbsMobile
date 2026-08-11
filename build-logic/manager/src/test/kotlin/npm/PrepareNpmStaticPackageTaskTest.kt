package npm

import com.google.gson.JsonParser
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证静态 npm 包只复制业务资源，并统一生成发布元数据。 */
class PrepareNpmStaticPackageTaskTest {

  /** 生成的包只包含静态文件和 package.json，不引入 JavaScript Runtime。 */
  @Test
  fun preparesStaticAssetsAndGeneratedMetadata() {
    val root = Files.createTempDirectory("npm-static-package-test").toFile()
    try {
      val source = root.resolve("source").apply { mkdirs() }
      source.resolve("catalog.json").writeText("{\"protocolVersion\":1}")
      val output = root.resolve("output")
      val task = ProjectBuilder.builder().withProjectDir(root).build().tasks.register(
        "prepareNpmStaticPackageUnderTest",
        PrepareNpmStaticPackageTask::class.java,
      ).get().apply {
        sourceDirectories.from(source)
        outputDirectory.set(output)
        packageName.set("@cyxbs-mobile/language-catalog")
        packageVersion.set("1.2.3")
      }

      task.preparePackage()

      val packageJson = JsonParser.parseString(output.resolve("package.json").readText()).asJsonObject
      assertEquals("@cyxbs-mobile/language-catalog", packageJson.get("name").asString)
      assertEquals("1.2.3", packageJson.get("version").asString)
      assertEquals(listOf("catalog.json"), packageJson.getAsJsonArray("files").map { it.asString })
      assertTrue(output.resolve("catalog.json").isFile)
      assertFalse(output.walkTopDown().any { it.extension == "js" || it.extension == "mjs" })
    } finally {
      root.deleteRecursively()
    }
  }

  /** package.json 必须由插件生成，避免业务源文件绕过统一版本。 */
  @Test
  fun rejectsSourceOwnedPackageJson() {
    val root = Files.createTempDirectory("npm-static-package-json-test").toFile()
    try {
      val source = root.resolve("source").apply {
        mkdirs()
        resolve("package.json").writeText("{}")
      }
      val task = ProjectBuilder.builder().withProjectDir(root).build().tasks.register(
        "prepareNpmStaticPackageUnderTest",
        PrepareNpmStaticPackageTask::class.java,
      ).get().apply {
        sourceDirectories.from(source)
        outputDirectory.set(root.resolve("output"))
        packageName.set("@cyxbs-mobile/language-catalog")
        packageVersion.set("1.2.3")
      }

      assertFailsWith<GradleException> { task.preparePackage() }
    } finally {
      root.deleteRecursively()
    }
  }

  /** 多个资源目录出现同一路径时必须失败，避免后添加的目录静默覆盖已有资源。 */
  @Test
  fun rejectsDuplicatePathsAcrossSourceDirectories() {
    val root = Files.createTempDirectory("npm-static-package-collision-test").toFile()
    try {
      val firstSource = root.resolve("first").apply {
        mkdirs()
        resolve("catalog.json").writeText("{\"source\":\"first\"}")
      }
      val secondSource = root.resolve("second").apply {
        mkdirs()
        resolve("catalog.json").writeText("{\"source\":\"second\"}")
      }
      val task = ProjectBuilder.builder().withProjectDir(root).build().tasks.register(
        "prepareNpmStaticPackageUnderTest",
        PrepareNpmStaticPackageTask::class.java,
      ).get().apply {
        sourceDirectories.from(firstSource, secondSource)
        outputDirectory.set(root.resolve("output"))
        packageName.set("@cyxbs-mobile/language-catalog")
        packageVersion.set("1.2.3")
      }

      assertFailsWith<GradleException> { task.preparePackage() }
    } finally {
      root.deleteRecursively()
    }
  }
}
