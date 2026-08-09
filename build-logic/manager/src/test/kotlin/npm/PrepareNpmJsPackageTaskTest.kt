package npm

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证聚合 Kotlin/JS 产物在 npm 发布阶段能够保持严格的项目模块边界。
 */
class PrepareNpmJsPackageTaskTest {

  /**
   * 静态 import、re-export 与字面量动态 import() 都应指向独立 npm 包。
   *
   * 依赖项目的入口文件不得复制到当前包；Kotlin Runtime 仍通过带子路径的共享包加载。
   */
  @Test
  fun rewritesAllSupportedImportsWithoutEmbeddingProjectDependencies() {
    val testRoot = Files.createTempDirectory("npm-js-package-test").toFile()
    try {
      val moduleRoot = testRoot.resolve("modules").apply { mkdirs() }
      val currentMetadata = testRoot.resolve("current").apply { mkdirs() }
      val dependencyMetadata = testRoot.resolve("dependency").apply { mkdirs() }
      val outputRoot = testRoot.resolve("output")
      val mainName = "current.mjs"
      val dependencyMain = "dependency.mjs"
      val runtimeMain = "kotlin-kotlin-stdlib.mjs"

      moduleRoot.resolve(mainName).writeText(
        """
        import { runtimeValue } from "./$runtimeMain";
        export { dependencyValue } from "./$dependencyMain";
        const loadDependency = () => import("./$dependencyMain");
        export { runtimeValue, loadDependency };
        """.trimIndent(),
      )
      moduleRoot.resolve(dependencyMain).writeText("export const dependencyValue = 1;")
      moduleRoot.resolve(runtimeMain).writeText("export const runtimeValue = 2;")
      currentMetadata.resolve("current.d.mts").writeText("export declare const runtimeValue: number;")
      val currentPackageJson = currentMetadata.resolve("package.json").apply {
        writePackageJson(
          packageName = CURRENT_PACKAGE,
          version = "1.0.0",
          main = mainName,
          types = "current.d.mts",
        )
      }
      val dependencyPackageJson = dependencyMetadata.resolve("package.json").apply {
        writePackageJson(
          packageName = DEPENDENCY_PACKAGE,
          version = DEPENDENCY_VERSION,
          main = dependencyMain,
        )
      }

      val project = ProjectBuilder.builder().withProjectDir(testRoot).build()
      val task = project.tasks.register(
        "prepareNpmJsPackageUnderTest",
        PrepareNpmJsPackageTask::class.java,
      ).get().apply {
        moduleSourceDirectory.set(moduleRoot)
        packageMetadataDirectory.set(currentMetadata)
        modulePackageMetadataFiles.from(currentPackageJson, dependencyPackageJson)
        outputDirectory.set(outputRoot)
        bundleKotlinRuntime.set(false)
        runtimePackageName.set(RUNTIME_PACKAGE)
        runtimePackageVersion.set("1.0.0")
        runtimeModuleFiles.set(setOf(runtimeMain))
      }

      task.preparePackage()

      val preparedSource = outputRoot.resolve(mainName).readText()
      assertContains(preparedSource, "from \"$RUNTIME_PACKAGE/$runtimeMain\"")
      assertContains(preparedSource, "from \"$DEPENDENCY_PACKAGE\"")
      assertContains(preparedSource, "import(\"$DEPENDENCY_PACKAGE\")")
      assertFalse(outputRoot.resolve(dependencyMain).exists())
      assertFalse(outputRoot.resolve(runtimeMain).exists())
      assertTrue(outputRoot.resolve("current.d.mts").isFile)
      val packageJson = outputRoot.resolve("package.json").readText()
      assertContains(packageJson, "\"$DEPENDENCY_PACKAGE\": \"$DEPENDENCY_VERSION\"")
      assertContains(packageJson, "\"$RUNTIME_PACKAGE\": \"1.0.0\"")
    } finally {
      testRoot.deleteRecursively()
    }
  }

  /** 写入测试所需的最小 production package.json。 */
  private fun File.writePackageJson(
    packageName: String,
    version: String,
    main: String,
    types: String? = null,
  ) {
    val typesField = types?.let { ",\n  \"types\": \"$it\"" }.orEmpty()
    writeText(
      """
      {
        "name": "$packageName",
        "version": "$version",
        "main": "$main"$typesField
      }
      """.trimIndent(),
    )
  }

  private companion object {
    const val CURRENT_PACKAGE = "@cyxbs-mobile/current"
    const val DEPENDENCY_PACKAGE = "@cyxbs-mobile/dependency"
    const val DEPENDENCY_VERSION = "2.3.4"
    const val RUNTIME_PACKAGE = "@cyxbs-mobile/runtime"
  }
}
