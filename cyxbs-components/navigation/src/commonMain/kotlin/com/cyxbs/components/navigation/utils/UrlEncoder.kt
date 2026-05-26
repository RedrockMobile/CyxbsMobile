package com.cyxbs.components.navigation.utils

import com.eygraber.uri.Uri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * 将 kotlinx.serialization 对象序列化到 [Uri.Builder] 的 QueryParameter 部分。
 *
 * scheme、host、path 等其余部分由调用方在具体业务里继续填充后再 [Uri.Builder.build]，
 * 编解码完全对称于 [UrlDecoder]。
 *
 * ## 使用示例
 * ```
 * val uri = Obj.serializer().encodeToUrl(obj, defaultJson)
 *   .scheme(AppScheme.SCHEME)
 *   .path(NAV_HOME)
 *   .build()
 * ```
 *
 * ## 编码规则
 * - primitive、String、enum 字段以 `key=value` 形式追加；enum 使用 element name（不依赖 ordinal）
 * - 复杂对象（class / object / map / polymorphic）以单条 `key=jsonString` 形式追加
 * - List 字段使用重复 key 形式 `key=v1&key=v2` 追加；元素为复杂对象时每个元素再做 JSON 编码
 * - `null` 会被编码为字符串 `"null"`，与 [UrlDecoder] 的 `decodeNotNullMark` 保持对齐
 * - URL 转义由 [Uri.Builder.appendQueryParameter] 自动完成
 */
@OptIn(ExperimentalSerializationApi::class)
internal class UrlEncoder(
  private val builder: Uri.Builder,
  private val json: Json,
  override val serializersModule: SerializersModule = json.serializersModule
) : AbstractEncoder() {
  private var currentName: String? = null

  override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
    currentName = descriptor.getElementName(index)
    return true
  }

  override fun encodeValue(value: Any) {
    builder.appendQueryParameter(requireName(), value.toString())
  }

  override fun encodeNull() {
    builder.appendQueryParameter(requireName(), "null")
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    builder.appendQueryParameter(requireName(), enumDescriptor.getElementName(index))
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    val name = descriptor.getElementName(index)
    currentName = name
    encodeFieldByKind(name, serializer, value)
  }

  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    val name = descriptor.getElementName(index)
    currentName = name
    if (value == null) {
      builder.appendQueryParameter(name, "null")
    } else {
      encodeFieldByKind(name, serializer, value)
    }
  }

  private fun <T> encodeFieldByKind(name: String, serializer: SerializationStrategy<T>, value: T) {
    val kind = serializer.descriptor.kind
    when {
      // primitive / enum 走 AbstractEncoder 默认实现，最终落到 encodeValue / encodeEnum
      kind.isPrimitiveLike() -> serializer.serialize(this, value)
      // 重复 key 形式与 UrlListDecoder 对齐
      kind == StructureKind.LIST -> serializer.serialize(UrlListEncoder(name, builder, json, serializersModule), value)
      // 复杂结构使用 JSON 字符串作为单条 value
      else -> builder.appendQueryParameter(name, json.encodeToString(serializer, value))
    }
  }

  private fun requireName(): String =
    currentName ?: throw SerializationException("Current URL parameter name is null")
}

@OptIn(ExperimentalSerializationApi::class)
private class UrlListEncoder(
  private val name: String,
  private val builder: Uri.Builder,
  private val json: Json,
  override val serializersModule: SerializersModule
) : AbstractEncoder() {
  // 只处理顶层列表的逐元素写入；嵌套的复杂结构会落到 JSON 分支。

  override fun encodeValue(value: Any) {
    builder.appendQueryParameter(name, value.toString())
  }

  override fun encodeNull() {
    builder.appendQueryParameter(name, "null")
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    builder.appendQueryParameter(name, enumDescriptor.getElementName(index))
  }

  override fun <T> encodeSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T
  ) {
    encodeElementByKind(serializer, value)
  }

  override fun <T : Any> encodeNullableSerializableElement(
    descriptor: SerialDescriptor,
    index: Int,
    serializer: SerializationStrategy<T>,
    value: T?
  ) {
    if (value == null) {
      builder.appendQueryParameter(name, "null")
    } else {
      encodeElementByKind(serializer, value)
    }
  }

  private fun <T> encodeElementByKind(serializer: SerializationStrategy<T>, value: T) {
    if (serializer.descriptor.kind.isPrimitiveLike()) {
      serializer.serialize(this, value)
    } else {
      builder.appendQueryParameter(name, json.encodeToString(serializer, value))
    }
  }
}

/**
 * 将对象编码为只填充了 QueryParameter 的 [Uri.Builder]，其余部分由具体业务继续填充。
 *
 * 对于 cyxbs:// 协议应统一使用 [com.cyxbs.components.navigation.encodeToUrl]，
 */
internal fun <T> SerializationStrategy<T>.encodeToUrl(
  value: T,
  json: Json,
): Uri.Builder {
  val builder = Uri.Builder()
  UrlEncoder(builder, json).encodeSerializableValue(this, value)
  return builder
}