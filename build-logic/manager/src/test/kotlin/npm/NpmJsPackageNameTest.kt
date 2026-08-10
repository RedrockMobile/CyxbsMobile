package npm

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证 npm 发布插件同时支持内部模块的默认路径坐标与正式模块的显式稳定坐标。
 */
class NpmJsPackageNameTest {

  /** 默认行为继续从 Gradle project path 生成唯一的小写包名。 */
  @Test
  fun derivesDefaultPackageNameFromProjectPath() {
    assertEquals(
      "@cyxbs-mobile/cyxbs-functions-code-npm-service-test-js-impl",
      npmPackageNameFromProjectPath(":cyxbs-functions:code:npm:service-test:js-impl"),
    )
  }

  /** 正式发布包可以使用与 Gradle 模块位置无关的项目 scope 坐标。 */
  @Test
  fun acceptsStableProjectPackageName() {
    val packageName = "@cyxbs-mobile/language-javascript"

    assertEquals(packageName, validateNpmPackageName(packageName))
  }

  /** 错误 scope 或包含大写字符的坐标必须在配置期失败。 */
  @Test
  fun rejectsPackageNamesOutsideProjectConvention() {
    assertFailsWith<GradleException> {
      validateNpmPackageName("@other-scope/language-javascript")
    }
    assertFailsWith<GradleException> {
      validateNpmPackageName("@cyxbs-mobile/Language-JavaScript")
    }
  }
}
