package com.cyxbs.components.log.storage

private const val REDACTED = "[REDACTED]"

private val cookieRegex = Regex(
  pattern = """\b(cookie|set-cookie)\b[\"']?\s*[:=]\s*[^\r\n]*""",
  options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)
private val secretRegex = Regex(
  pattern = """\b(authorization|access[_-]?token|refresh[_-]?token|device[_-]?token|push[_-]?token|token|password|passwd|secret|student[_-]?id|student[_-]?number|phone|mobile)\b[\"']?\s*[:=]\s*(?:\"[^\"]*\"|'[^']*'|[^\s,;&]+)""",
  option = RegexOption.IGNORE_CASE,
)
private val bearerRegex = Regex(
  pattern = """\bBearer\s+[A-Za-z0-9._~+/=-]+""",
  option = RegexOption.IGNORE_CASE,
)
private val emailRegex = Regex(
  pattern = """[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""",
  option = RegexOption.IGNORE_CASE,
)
private val longNumberRegex = Regex("""\b\d{8,19}\b""")

/** Best-effort defense in depth. Callers must still avoid logging personal or credential data. */
internal fun String.sanitizeForFileLog(): String =
  replace(cookieRegex) { match -> "${match.groupValues[1]}=$REDACTED" }
    .replace(bearerRegex, "Bearer $REDACTED")
    .replace(secretRegex) { match -> "${match.groupValues[1]}=$REDACTED" }
    .replace(emailRegex, REDACTED)
    .replace(longNumberRegex, REDACTED)
