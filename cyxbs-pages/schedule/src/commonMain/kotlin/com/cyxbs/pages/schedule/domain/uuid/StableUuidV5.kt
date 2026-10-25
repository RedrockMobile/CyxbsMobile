package com.cyxbs.pages.schedule.domain.uuid

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * 使用业务命名空间和稳定特征生成 RFC 9562 UUIDv5。
 *
 * 该方法只用于“同一外部资源必须得到同一 Schedule ID”的场景；[identity] 应由账号、来源类型和来源主键组成，
 * 不得放入标题、时间等允许用户修改的字段。
 */
internal fun stableUuidV5(
  namespace: String,
  identity: String,
): String {
  require(namespace.isNotBlank()) { "UUIDv5 namespace must not be blank" }
  require(identity.isNotBlank()) { "UUIDv5 identity must not be blank" }
  val digest = (UUID_NAMESPACE_DNS +
    "${namespace}|${identity}".encodeUtf8().toByteArray())
    .toByteString()
    .sha1()
    .toByteArray()
  val bytes = digest.copyOf(16)
  bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x50).toByte()
  bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
  val hex = CharArray(32)
  bytes.forEachIndexed { index, byte ->
    val value = byte.toInt() and 0xFF
    hex[index * 2] = HEX[value ushr 4]
    hex[index * 2 + 1] = HEX[value and 0x0F]
  }
  val compact = hex.concatToString()
  return "${compact.substring(0, 8)}-${compact.substring(8, 12)}-" +
    "${compact.substring(12, 16)}-${compact.substring(16, 20)}-${compact.substring(20)}"
}

/** RFC 9562 预定义 DNS namespace；这里只作为稳定 UUIDv5 的固定二进制命名空间。 */
private val UUID_NAMESPACE_DNS = byteArrayOf(
  0x6B, 0xA7.toByte(), 0xB8.toByte(), 0x10, 0x9D.toByte(), 0xAD.toByte(), 0x11, 0xD1.toByte(),
  0x80.toByte(), 0xB4.toByte(), 0x00, 0xC0.toByte(), 0x4F, 0xD4.toByte(), 0x30, 0xC8.toByte(),
)

private const val HEX = "0123456789abcdef"
