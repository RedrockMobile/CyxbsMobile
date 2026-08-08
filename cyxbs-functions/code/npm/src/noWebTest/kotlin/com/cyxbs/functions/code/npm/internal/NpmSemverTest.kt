package com.cyxbs.functions.code.npm.internal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** npm 客户端解析会遇到的常见 semver 范围测试。 */
class NpmSemverTest {

  @Test
  fun matchCommonNpmRanges() {
    assertTrue(NpmVersionRange.parse("^1.2.3").matches(version("1.9.0")))
    assertFalse(NpmVersionRange.parse("^1.2.3").matches(version("2.0.0")))
    assertTrue(NpmVersionRange.parse("^0.2.3").matches(version("0.2.9")))
    assertFalse(NpmVersionRange.parse("^0.2.3").matches(version("0.3.0")))
    assertTrue(NpmVersionRange.parse("^0").matches(version("0.9.0")))
    assertFalse(NpmVersionRange.parse("^0").matches(version("1.0.0")))
    assertTrue(NpmVersionRange.parse("^0.0").matches(version("0.0.9")))
    assertTrue(NpmVersionRange.parse("~1.2.3").matches(version("1.2.9")))
    assertFalse(NpmVersionRange.parse("~1.2.3").matches(version("1.3.0")))
    assertTrue(NpmVersionRange.parse("1.x").matches(version("1.8.4")))
    assertTrue(NpmVersionRange.parse(">=1.2.0 <2.0.0").matches(version("1.5.0")))
    assertTrue(NpmVersionRange.parse("1.2.0 - 1.4.0").matches(version("1.4.0")))
    assertTrue(NpmVersionRange.parse("^1.0.0 || ^3.0.0").matches(version("3.1.0")))
  }

  @Test
  fun excludePrereleaseUnlessRangeExplicitlyIncludesIt() {
    assertFalse(NpmVersionRange.parse("^1.0.0").matches(version("1.1.0-beta.1")))
    assertTrue(NpmVersionRange.parse(">=1.1.0-beta.1 <1.1.0").matches(version("1.1.0-beta.2")))
    assertFalse(NpmVersionRange.parse(">=1.1.0-beta.1 <2.0.0").matches(version("1.2.0-beta.1")))
  }

  private fun version(value: String): NpmSemver = requireNotNull(NpmSemver.parseOrNull(value))
}
