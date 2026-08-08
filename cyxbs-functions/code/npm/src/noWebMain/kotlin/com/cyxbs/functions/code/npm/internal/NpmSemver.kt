package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.NpmResolutionException

/**
 * npm 依赖解析使用的轻量语义版本。
 *
 * 当前实现覆盖教学语言包常见的精确版本、部分版本、wildcard、caret、tilde、比较器组合、连字符区间和
 * `||`。npm alias、git、file、workspace 等会明确失败，避免把无法理解的范围静默解析成错误版本。
 */
internal data class NpmSemver(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val prerelease: List<String> = emptyList(),
  val build: String? = null,
) : Comparable<NpmSemver> {

  override fun compareTo(other: NpmSemver): Int {
    compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
    compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
    compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
    if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
      return when {
        prerelease.isEmpty() && other.prerelease.isNotEmpty() -> 1
        prerelease.isNotEmpty() && other.prerelease.isEmpty() -> -1
        else -> 0
      }
    }
    repeat(maxOf(prerelease.size, other.prerelease.size)) { index ->
      val left = prerelease.getOrNull(index) ?: return -1
      val right = other.prerelease.getOrNull(index) ?: return 1
      comparePrerelease(left, right).takeIf { it != 0 }?.let { return it }
    }
    return 0
  }

  override fun toString(): String = buildString {
    append("$major.$minor.$patch")
    if (prerelease.isNotEmpty()) append('-').append(prerelease.joinToString("."))
    if (build != null) append('+').append(build)
  }

  companion object {
    /** 严格解析 registry 返回的完整版本；不接受缺失段或前导零。 */
    fun parseOrNull(value: String): NpmSemver? {
      val match = FULL_VERSION.matchEntire(value.trim().removePrefix("v")) ?: return null
      return NpmSemver(
        major = match.groupValues[1].toIntOrNull() ?: return null,
        minor = match.groupValues[2].toIntOrNull() ?: return null,
        patch = match.groupValues[3].toIntOrNull() ?: return null,
        prerelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.').orEmpty(),
        build = match.groupValues[5].takeIf(String::isNotEmpty),
      )
    }

    private val FULL_VERSION = Regex(
      """(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
        """(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?""" +
        """(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?""",
    )
  }
}

/** 已校验的 npm semver 范围；同一实例可重复用于本地池与远端版本集合。 */
internal class NpmVersionRange private constructor(
  private val alternatives: List<List<Comparator>>,
) {

  /** 返回版本是否满足任意一组 `||` 分支。 */
  fun matches(version: NpmSemver): Boolean {
    return alternatives.any { comparators ->
      val prereleaseAllowed = version.prerelease.isEmpty() || comparators.any { comparator ->
        comparator.version.prerelease.isNotEmpty() &&
          comparator.version.major == version.major &&
          comparator.version.minor == version.minor &&
          comparator.version.patch == version.patch
      }
      prereleaseAllowed && comparators.all { it.matches(version) }
    }
  }

  companion object {
    /**
     * 解析常见 npm semver 范围。
     *
     * @throws NpmResolutionException 范围为空、使用不支持的 npm 协议或语法无效。
     */
    @Throws(NpmResolutionException::class)
    fun parse(value: String): NpmVersionRange {
      val normalized = value.trim()
      if (normalized.isEmpty()) return any()
      if (normalized.startsWith("npm:") || normalized.startsWith("git") ||
        normalized.startsWith("file:") || normalized.startsWith("workspace:") ||
        "://" in normalized
      ) {
        throw NpmResolutionException("Unsupported npm version specification '$value'.")
      }
      val branches = normalized.split("||").map { branch -> parseBranch(branch.trim(), value) }
      return NpmVersionRange(branches)
    }

    private fun any() = NpmVersionRange(listOf(emptyList()))

    /** 将单个 OR 分支转换为所有比较器均需满足的集合。 */
    private fun parseBranch(branch: String, original: String): List<Comparator> {
      if (branch.isEmpty() || branch == "*" || branch.equals("x", ignoreCase = true)) {
        return emptyList()
      }
      HYPHEN_RANGE.matchEntire(branch)?.let { match ->
        val lower = parsePartial(match.groupValues[1], original)
        val upper = parsePartial(match.groupValues[2], original)
        return listOf(
          Comparator(Operator.GREATER_OR_EQUAL, lower.minimum()),
          Comparator(Operator.LESS, upper.nextExclusive()),
        )
      }
      return branch.split(Regex("\\s+")).flatMap { token -> parseToken(token, original) }
    }

    private fun parseToken(token: String, original: String): List<Comparator> {
      if (token.isEmpty() || token == "*") return emptyList()
      val operatorText = COMPARATOR.find(token)?.value.orEmpty()
      val rawVersion = token.removePrefix(operatorText).removePrefix("v")
      val partial = parsePartial(rawVersion, original)
      return when (operatorText) {
        "^" -> listOf(
          Comparator(Operator.GREATER_OR_EQUAL, partial.minimum()),
          Comparator(Operator.LESS, partial.caretUpperBound()),
        )
        "~", "~>" -> listOf(
          Comparator(Operator.GREATER_OR_EQUAL, partial.minimum()),
          Comparator(Operator.LESS, partial.tildeUpperBound()),
        )
        ">" -> listOf(Comparator(Operator.GREATER, partial.minimum()))
        ">=" -> listOf(Comparator(Operator.GREATER_OR_EQUAL, partial.minimum()))
        "<" -> listOf(Comparator(Operator.LESS, partial.minimum()))
        "<=" -> listOf(Comparator(Operator.LESS_OR_EQUAL, partial.maximum()))
        "", "=" -> if (partial.precision == 3 && !partial.hasWildcard) {
          listOf(Comparator(Operator.EQUAL, partial.minimum()))
        } else {
          listOf(
            Comparator(Operator.GREATER_OR_EQUAL, partial.minimum()),
            Comparator(Operator.LESS, partial.nextExclusive()),
          )
        }
        else -> throw NpmResolutionException("Unsupported npm version range '$original'.")
      }
    }

    private fun parsePartial(value: String, original: String): PartialVersion {
      val match = PARTIAL_VERSION.matchEntire(value)
        ?: throw NpmResolutionException("Invalid npm version range '$original'.")
      val parts = listOf(match.groupValues[1], match.groupValues[2], match.groupValues[3])
      val parsed = parts.map { part ->
        when {
          part.isEmpty() || part == "*" || part.equals("x", ignoreCase = true) -> null
          part.length > 1 && part.startsWith('0') ->
            throw NpmResolutionException("Invalid npm version range '$original'.")
          else -> part.toIntOrNull()
            ?: throw NpmResolutionException("Invalid npm version range '$original'.")
        }
      }
      val firstMissing = parsed.indexOfFirst { it == null }.let { if (it < 0) 3 else it }
      if (parsed.drop(firstMissing).any { it != null }) {
        throw NpmResolutionException("Invalid npm version range '$original'.")
      }
      val prerelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.').orEmpty()
      return PartialVersion(
        major = parsed[0],
        minor = parsed[1],
        patch = parsed[2],
        prerelease = prerelease,
        precision = firstMissing,
        hasWildcard = firstMissing < 3,
      )
    }

    private val COMPARATOR = Regex("^(?:>=|<=|>|<|=|\\^|~>|~)")
    private val HYPHEN_RANGE = Regex("^\\s*(\\S+)\\s+-\\s+(\\S+)\\s*$")
    private val PARTIAL_VERSION = Regex(
      """([0-9]+|[xX*])(?:\.([0-9]+|[xX*]))?(?:\.([0-9]+|[xX*]))?""" +
        """(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z.-]+)?""",
    )
  }
}

private data class PartialVersion(
  val major: Int?,
  val minor: Int?,
  val patch: Int?,
  val prerelease: List<String>,
  val precision: Int,
  val hasWildcard: Boolean,
) {
  fun minimum() = NpmSemver(major ?: 0, minor ?: 0, patch ?: 0, prerelease)

  fun maximum() = NpmSemver(
    major ?: Int.MAX_VALUE,
    minor ?: Int.MAX_VALUE,
    patch ?: Int.MAX_VALUE,
    prerelease,
  )

  fun nextExclusive(): NpmSemver = when (precision) {
    0 -> NpmSemver(Int.MAX_VALUE, 0, 0)
    1 -> NpmSemver(requireNotNull(major) + 1, 0, 0)
    2 -> NpmSemver(requireNotNull(major), requireNotNull(minor) + 1, 0)
    else -> NpmSemver(requireNotNull(major), requireNotNull(minor), requireNotNull(patch) + 1)
  }

  fun caretUpperBound(): NpmSemver {
    val major = major ?: return NpmSemver(Int.MAX_VALUE, 0, 0)
    val minor = minor ?: 0
    val patch = patch ?: 0
    return when {
      precision <= 1 -> NpmSemver(major + 1, 0, 0)
      major > 0 -> NpmSemver(major + 1, 0, 0)
      precision == 2 -> NpmSemver(0, minor + 1, 0)
      minor > 0 -> NpmSemver(0, minor + 1, 0)
      else -> NpmSemver(0, 0, patch + 1)
    }
  }

  fun tildeUpperBound(): NpmSemver {
    val major = major ?: return NpmSemver(Int.MAX_VALUE, 0, 0)
    return if (minor == null) NpmSemver(major + 1, 0, 0)
    else NpmSemver(major, minor + 1, 0)
  }
}

private data class Comparator(
  val operator: Operator,
  val version: NpmSemver,
) {
  fun matches(candidate: NpmSemver): Boolean = when (operator) {
    Operator.EQUAL -> candidate.compareTo(version) == 0
    Operator.GREATER -> candidate > version
    Operator.GREATER_OR_EQUAL -> candidate >= version
    Operator.LESS -> candidate < version
    Operator.LESS_OR_EQUAL -> candidate <= version
  }
}

private enum class Operator {
  EQUAL,
  GREATER,
  GREATER_OR_EQUAL,
  LESS,
  LESS_OR_EQUAL,
}

/** npm prerelease 标识符遵循：数字小于非数字，数字之间按整数比较。 */
private fun comparePrerelease(left: String, right: String): Int {
  val leftNumeric = left.all(Char::isDigit)
  val rightNumeric = right.all(Char::isDigit)
  return when {
    leftNumeric && rightNumeric -> compareNumericIdentifiers(left, right)
    leftNumeric -> -1
    rightNumeric -> 1
    else -> left.compareTo(right)
  }
}

/** 避免超长数字标识符溢出 Int；去除前导零后按长度和字典序比较。 */
private fun compareNumericIdentifiers(left: String, right: String): Int {
  val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
  val normalizedRight = right.trimStart('0').ifEmpty { "0" }
  return compareValues(normalizedLeft.length, normalizedRight.length)
    .takeIf { it != 0 }
    ?: normalizedLeft.compareTo(normalizedRight)
}
