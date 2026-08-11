package npm

import com.google.gson.JsonParser
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** 验证多个语言 Project 的冻结描述可以稳定生成 Catalog，并在发布前拒绝身份冲突。 */
class GenerateDynamicLanguageCatalogTaskTest {

  /** 语言按 ID 排序，且任务原样保留从各 Project 读取的 npm packageName。 */
  @Test
  fun generatesSortedCatalogFromProjectDescriptors() {
    withTask { task, output ->
      task.languageDescriptors.putAll(
        mapOf(
          ":language:python" to descriptor(
            languageId = "python",
            packageName = "@cyxbs-mobile/language-python",
            aliases = listOf("py"),
          ),
          ":language:javascript" to descriptor(
            languageId = "javascript",
            packageName = "@cyxbs-mobile/language-javascript",
            aliases = listOf("js"),
          ),
        ),
      )

      task.generate()

      val catalog = JsonParser.parseString(output.resolve("catalog.json").readText()).asJsonObject
      assertFalse(catalog.has("protocolVersion"))
      val languages = catalog.getAsJsonArray("languages")
      assertEquals(listOf("javascript", "python"), languages.map {
        it.asJsonObject.get("languageId").asString
      })
      assertEquals(
        "@cyxbs-mobile/language-javascript",
        languages.first().asJsonObject.get("npmPackageName").asString,
      )
    }
  }

  /** 不同 Project 不能声明相同别名，否则端上按别名查找会出现歧义。 */
  @Test
  fun rejectsDuplicateIdentityAcrossProjects() {
    withTask { task, _ ->
      task.languageDescriptors.putAll(
        mapOf(
          ":language:javascript" to descriptor(
            languageId = "javascript",
            packageName = "@cyxbs-mobile/language-javascript",
            aliases = listOf("script"),
          ),
          ":language:typescript" to descriptor(
            languageId = "typescript",
            packageName = "@cyxbs-mobile/language-typescript",
            aliases = listOf("script"),
          ),
        ),
      )

      assertFailsWith<GradleException> { task.generate() }
    }
  }

  /** 创建隔离的 Gradle Task 和输出目录并在测试后清理。 */
  private fun withTask(block: (GenerateDynamicLanguageCatalogTask, java.io.File) -> Unit) {
    val root = Files.createTempDirectory("dynamic-language-catalog-test").toFile()
    try {
      val output = root.resolve("output")
      val task = ProjectBuilder.builder().withProjectDir(root).build().tasks.register(
        "generateDynamicLanguageCatalogUnderTest",
        GenerateDynamicLanguageCatalogTask::class.java,
      ).get().apply {
        outputDirectory.set(output)
      }
      block(task, output)
    } finally {
      root.deleteRecursively()
    }
  }

  /** 生成与语言模块 npmJsLanguage 配置冻结格式一致的测试描述。 */
  private fun descriptor(
    languageId: String,
    packageName: String,
    aliases: List<String>,
  ): String {
    val aliasJson = aliases.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    return """
      {
        "languageId": "$languageId",
        "displayName": "$languageId",
        "npmPackageName": "$packageName",
        "aliases": $aliasJson,
        "fileExtensions": ["$languageId"]
      }
    """.trimIndent()
  }
}
