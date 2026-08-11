package npm

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** 验证 npm 业务包发布任务可安全重试，且不会覆盖同版本的不同内容。 */
class PublishNpmJsPackageTaskTest {

  /** 远端精确版本不存在时，应先完成 integrity 检查，再且仅再执行一次 publish。 */
  @Test
  fun publishesPackageWhenExactVersionIsMissing() {
    withFixture(ViewResult.MISSING) { fixture ->
      fixture.task.publish()

      assertEquals(
        listOf(
          "pack --dry-run --json",
          "view $PACKAGE_COORDINATE dist.integrity --json --registry $REGISTRY_URL",
          "publish --registry $REGISTRY_URL --tag latest --access public",
        ),
        fixture.commands(),
      )
    }
  }

  /** 远端同版本 integrity 与本地一致时，只检查并复用，不能再次调用 npm publish。 */
  @Test
  fun reusesPackageWhenExactVersionIntegrityMatches() {
    withFixture(ViewResult.MATCHING) { fixture ->
      fixture.task.publish()

      assertEquals(
        listOf(
          "pack --dry-run --json",
          "view $PACKAGE_COORDINATE dist.integrity --json --registry $REGISTRY_URL",
        ),
        fixture.commands(),
      )
    }
  }

  /** 远端同版本内容不同时必须提示提升版本，并在产生远端修改前失败。 */
  @Test
  fun rejectsDifferentContentAtTheSameVersion() {
    withFixture(ViewResult.DIFFERENT) { fixture ->
      val failure = assertFailsWith<GradleException> {
        fixture.task.publish()
      }

      assertContains(failure.message.orEmpty(), PACKAGE_COORDINATE)
      assertContains(failure.message.orEmpty(), "Bump the project version")
      assertFalse(fixture.commands().any { it.startsWith("publish ") })
    }
  }

  /** 创建包含最终 package.json、fake npm CLI 与待测 Gradle Task 的隔离目录。 */
  private fun withFixture(
    viewResult: ViewResult,
    block: (Fixture) -> Unit,
  ) {
    val root = Files.createTempDirectory("npm-js-publish-test").toFile()
    try {
      val packageDirectory = root.resolve("package").apply { mkdirs() }
      packageDirectory.resolve("package.json").writeText(
        """
        {
          "name": "$PACKAGE_NAME",
          "version": "$PACKAGE_VERSION"
        }
        """.trimIndent(),
      )
      val commandLog = root.resolve("npm-commands.txt")
      val npmExecutable = root.resolve("fake-npm.sh").apply {
        writeText(fakeNpmScript(commandLog, viewResult))
        check(setExecutable(true)) { "Cannot mark fake npm executable." }
      }
      val project = ProjectBuilder.builder().withProjectDir(root).build()
      val task = project.tasks.register(
        "publishNpmJsPackageUnderTest",
        PublishNpmJsPackageTask::class.java,
      ).get().apply {
        this.packageDirectory.set(packageDirectory)
        this.npmExecutable.set(npmExecutable.absolutePath)
        registryUrl.set(REGISTRY_URL)
        publishTag.set("latest")
        publishAccess.set("public")
      }

      block(Fixture(task, commandLog))
    } finally {
      root.deleteRecursively()
    }
  }

  /** 生成只实现 pack、view、publish 的最小 npm CLI，并把每次调用写入日志。 */
  private fun fakeNpmScript(commandLog: File, viewResult: ViewResult): String {
    val viewCommand = when (viewResult) {
      ViewResult.MISSING -> "echo 'npm ERR! code E404' >&2; exit 1"
      ViewResult.MATCHING -> "printf '%s\\n' '\"$LOCAL_INTEGRITY\"'"
      ViewResult.DIFFERENT -> "printf '%s\\n' '\"sha512-remote\"'"
    }
    return """
      #!/bin/sh
      printf '%s\n' "${'$'}*" >> ${commandLog.absolutePath.shellLiteral()}
      case "${'$'}1" in
        pack)
          printf '%s\n' '[{"integrity":"$LOCAL_INTEGRITY"}]'
          ;;
        view)
          $viewCommand
          ;;
        publish)
          exit 0
          ;;
        *)
          echo "unsupported fake npm command: ${'$'}1" >&2
          exit 2
          ;;
      esac
    """.trimIndent()
  }

  /** 将临时路径转换为 POSIX shell 单引号字面量。 */
  private fun String.shellLiteral(): String = "'" + replace("'", "'\"'\"'") + "'"

  /** 测试关心的远端精确版本状态。 */
  private enum class ViewResult {
    MISSING,
    MATCHING,
    DIFFERENT,
  }

  /** 一次发布任务测试所需的对象。 */
  private data class Fixture(
    val task: PublishNpmJsPackageTask,
    val commandLog: File,
  ) {
    /** 返回 fake npm CLI 按执行顺序记录的参数。 */
    fun commands(): List<String> {
      return if (commandLog.isFile) commandLog.readLines() else emptyList()
    }
  }

  private companion object {
    const val PACKAGE_NAME = "@cyxbs-mobile/example"
    const val PACKAGE_VERSION = "1.2.3"
    const val PACKAGE_COORDINATE = "$PACKAGE_NAME@$PACKAGE_VERSION"
    const val REGISTRY_URL = "https://registry.example.test"
    const val LOCAL_INTEGRITY = "sha512-local"
  }
}
