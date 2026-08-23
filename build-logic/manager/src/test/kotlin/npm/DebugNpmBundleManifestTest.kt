package npm

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 验证共享 debug npm 源只接受安全、版本化且可稳定复现的归档路径。 */
class DebugNpmBundleManifestTest {

  /** 普通包与作用域包都以版本作为 tgz 文件名，从而允许同包多版本共存。 */
  @Test
  fun mapsPackageCoordinateToVersionedArchivePath() {
    assertEquals(
      "lesson-runtime/1.2.3.tgz",
      debugNpmArchiveRelativePath("lesson-runtime", "1.2.3"),
    )
    assertEquals(
      "@cyxbs-mobile/language-java/0.2.1-debug.20260822153045.tgz",
      debugNpmArchiveRelativePath(
        "@cyxbs-mobile/language-java",
        "0.2.1-debug.20260822153045",
      ),
    )
  }

  /** 包名和版本进入文件系统前必须拒绝路径穿越及非受控版本格式。 */
  @Test
  fun rejectsUnsafePackageCoordinate() {
    assertFailsWith<GradleException> {
      debugNpmArchiveRelativePath("../outside", "1.0.0")
    }
    assertFailsWith<GradleException> {
      debugNpmArchiveRelativePath("lesson-runtime", "1.0.0/../../outside")
    }
  }
}
