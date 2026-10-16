package com.cyxbs.components.log

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val fileLogJson = Json {
  encodeDefaults = true
  explicitNulls = false
}

@Serializable
private data class ExportManifest(
  val recordType: String = "manifest",
  val schemaVersion: Int = 1,
  val generatedAtMillis: Long,
  val sourceFileCount: Int,
  val exportedLogBytes: Long,
  val truncated: Boolean,
)

@Serializable
private data class PersistedLogRecord(
  val recordType: String = "log",
  val schemaVersion: Int = 1,
  val session: String,
  val sequence: Long,
  val timestampMillis: Long,
  val level: String,
  val tag: String,
  val message: String,
  val stackTrace: String? = null,
  val truncated: Boolean = false,
)

internal data class EncodedLogLine(
  val bytes: ByteArray,
  val truncated: Boolean,
)

internal fun encodeExportManifest(
  generatedAtMillis: Long,
  sourceFileCount: Int,
  exportedLogBytes: Long,
  truncated: Boolean,
): ByteArray = (fileLogJson.encodeToString(
  ExportManifest(
    generatedAtMillis = generatedAtMillis,
    sourceFileCount = sourceFileCount,
    exportedLogBytes = exportedLogBytes,
    truncated = truncated,
  )
) + "\n").encodeToByteArray()

internal fun encodeLogLine(
  event: FileLogEvent,
  session: String,
  sequence: Long,
  maxBytes: Int,
): EncodedLogLine? {
  var tag = truncateUtf8(event.tag, maxBytes / 8)
  var message = truncateUtf8(event.message, maxBytes / 2)
  var stackTrace = event.stackTrace?.let { truncateUtf8(it, maxBytes / 3) }
  var truncated = event.snapshotTruncated ||
    tag != event.tag || message != event.message || stackTrace != event.stackTrace

  repeat(12) {
    val record = PersistedLogRecord(
      session = session,
      sequence = sequence,
      timestampMillis = event.timestampMillis,
      level = event.level.name,
      tag = tag,
      message = message,
      stackTrace = stackTrace,
      truncated = truncated,
    )
    val bytes = (fileLogJson.encodeToString(record) + "\n").encodeToByteArray()
    if (bytes.size <= maxBytes) return EncodedLogLine(bytes, truncated)

    truncated = true
    when {
      !stackTrace.isNullOrEmpty() -> stackTrace = shrink(stackTrace)
      message.isNotEmpty() -> message = shrink(message)
      tag.isNotEmpty() -> tag = shrink(tag)
      else -> return null
    }
  }
  return null
}

private fun shrink(value: String): String {
  if (value.length <= 1) return ""
  val end = (value.length / 2).let { index ->
    if (index > 0 && value[index - 1].isHighSurrogate()) index - 1 else index
  }
  return value.substring(0, end)
}

private fun truncateUtf8(value: String, maxBytes: Int): String {
  if (maxBytes <= 0) return ""
  if (value.encodeToByteArray().size <= maxBytes) return value

  var low = 0
  var high = value.length
  while (low < high) {
    val middle = (low + high + 1) / 2
    val safeMiddle = if (middle > 0 && middle < value.length && value[middle - 1].isHighSurrogate()) {
      middle - 1
    } else {
      middle
    }
    if (value.substring(0, safeMiddle).encodeToByteArray().size <= maxBytes) {
      low = middle
    } else {
      high = middle - 1
    }
  }
  val safeEnd = if (low > 0 && low < value.length && value[low - 1].isHighSurrogate()) low - 1 else low
  return value.substring(0, safeEnd)
}
