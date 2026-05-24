package com.cyxbs.components.navigation.utils

import com.eygraber.uri.Uri
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * 将 hierarchical URL 的 query 参数反序列化为 kotlinx.serialization 对象。
 *
 * ## 使用示例
 * ```
 * val uri = Uri.parse(url)
 * val obj = uri.decodeFromUrl(Obj.serializer(), defaultJson)
 * ```
 *
 * ## 支持范围
 * - 只解析 Uri 的 QueryParameter 部分，不关注 scheme、host、path
 * - 支持 `key=value` 形式解析 primitive、String 和 enum；复杂对象使用 JSON 字符串作为 value，
 *    例如 `what=%7B%22age%22%3A1%7D`，未编码但不破坏 URL 结构的 `{age:1}` 也可由 lenient Json 解析。
 * - 重复 key 会完整保留：目标字段是 List 时解析为列表，非 List 字段默认使用最后一个值。
 * - 单个 JSON array value 仍由 Json 解析为 List，例如 `key=%5B1%2C2%5D`。
 *
 * ## 支持示例
 * - `cyxbs://path?name=guo&age=1&enabled=true&score=3.5`
 *   -> `PrimitiveRoute(name = "guo", age = 1, enabled = true, score = 3.5)`
 *
 * - `cyxbs://path?name=guo&what=%7B%22age%22%3A1%2C%22tags%22%3A%5B%22a%22%2C%22b%22%5D%7D`
 *   -> `NestedRoute(name = "guo", what = What(age = 1, tags = listOf("a", "b")))`
 *
 * - `cyxbs://path?name=guo&what={age:1}`
 *   -> `NestedRoute(name = "guo", what = What(age = 1))`
 *
 * - `cyxbs://path?key=1&key=2`
 *   -> 非 List 字段得到 `RepeatedScalarRoute(key = 2)`，List 字段得到 `RepeatedListRoute(key = listOf(1, 2))`
 *
 * - `cyxbs://path?key=%5B1%2C2%2C3%5D`
 *   -> `RepeatedListRoute(key = listOf(1, 2, 3))`
 *
 * - `cyxbs://path?item=%7B%22age%22%3A1%7D&item=%7B%22age%22%3A2%7D`
 *   -> `NestedListRoute(item = listOf(What(age = 1), What(age = 2)))`
 *
 * - `cyxbs://path?name=null`
 *   -> `NullableRoute(name = null)`
 *
 * - `cyxbs://path?type=Detail`
 *   -> `EnumRoute(type = RouteType.Detail)`
 *
 * - `cyxbs://path?type=Home&type=Detail`
 *   -> `EnumListRoute(type = listOf(RouteType.Home, RouteType.Detail))`
 *
 * ## 注意事项
 * - 不支持 opaque URI，例如 `cyxbs:path?key=value`；未编码 JSON 中的 `&`、`#` 会被 Uri 当作 URL 结构字符处理。
 */
@OptIn(ExperimentalSerializationApi::class)
internal class UrlDecoder(
  uri: Uri,
  private val json: Json,
  override val serializersModule: SerializersModule = json.serializersModule
) : AbstractDecoder() {
  private val parameters = parseQuery(uri)
  private var nextElementIndex = 0
  private var hasCurrentValue = false
  private var currentValues: List<String>? = null
  private var currentValue: String? = null

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    nextElementIndex = 0
    return this
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    while (nextElementIndex < descriptor.elementsCount) {
      val index = nextElementIndex++
      val name = descriptor.getElementName(index)
      val values = parameters[name]
      if (values != null) {
        currentValues = values
        // 重复 key 默认让非 List 字段使用最后一个值，和常见 query 覆盖语义保持一致。
        currentValue = values.lastOrNull()
        hasCurrentValue = true
        return index
      }
    }
    clearCurrentValue()
    return CompositeDecoder.DECODE_DONE
  }

  override fun decodeNotNullMark(): Boolean = currentValue != null && currentValue != "null"

  override fun decodeValue(): Any = currentValue ?: throw SerializationException("Current URL parameter is null")

  override fun decodeBoolean(): Boolean = decodeString().toBooleanStrict()

  override fun decodeByte(): Byte = decodeString().toByte()

  override fun decodeShort(): Short = decodeString().toShort()

  override fun decodeInt(): Int = decodeString().toInt()

  override fun decodeLong(): Long = decodeString().toLong()

  override fun decodeFloat(): Float = decodeString().toFloat()

  override fun decodeDouble(): Double = decodeString().toDouble()

  override fun decodeChar(): Char = decodeString().single()

  override fun decodeString(): String = currentValue ?: throw SerializationException("Current URL parameter is null")

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    val value = decodeString()
    val index = enumDescriptor.getElementIndex(value)
    if (index == CompositeDecoder.UNKNOWN_NAME) {
      throw SerializationException("Enum ${enumDescriptor.serialName} does not contain $value")
    }
    return index
  }

  override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
    if (!hasCurrentValue) return deserializer.deserialize(this)
    val values = currentValues.orEmpty()
    val value = currentValue
    if (value == null || value == "null") return deserializer.deserialize(this)
    return if (deserializer.descriptor.kind == StructureKind.LIST && values.size > 1) {
      // key=1&key=2 这种重复 key 形式会被视为 List；单个 key=[1,2] 仍交给 JSON array 解析。
      deserializer.deserialize(UrlListDecoder(values, json, serializersModule))
    } else if (deserializer.descriptor.kind.isPrimitiveLike()) {
      deserializer.deserialize(this)
    } else {
      // 复杂结构必须是 JSON 字符串；URL 编码会由 Uri.getQueryParameters 先解开。
      json.decodeFromString(deserializer, value)
    }
  }

  override fun endStructure(descriptor: SerialDescriptor) {
    clearCurrentValue()
  }

  private fun clearCurrentValue() {
    hasCurrentValue = false
    currentValues = null
    currentValue = null
  }
}

@OptIn(ExperimentalSerializationApi::class)
private class UrlListDecoder(
  private val values: List<String>,
  private val json: Json,
  override val serializersModule: SerializersModule
) : AbstractDecoder() {
  // 只处理重复 key 产生的列表；嵌套列表或单个 JSON array 由 Json decoder 处理。
  private var nextElementIndex = 0
  private var currentValue: String? = null

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    nextElementIndex = 0
    return this
  }

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    if (nextElementIndex >= values.size) return CompositeDecoder.DECODE_DONE
    currentValue = values[nextElementIndex]
    return nextElementIndex++
  }

  override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = values.size

  override fun decodeNotNullMark(): Boolean = currentValue != null && currentValue != "null"

  override fun decodeValue(): Any = currentValue ?: throw SerializationException("Current URL list element is null")

  override fun decodeBoolean(): Boolean = decodeString().toBooleanStrict()

  override fun decodeByte(): Byte = decodeString().toByte()

  override fun decodeShort(): Short = decodeString().toShort()

  override fun decodeInt(): Int = decodeString().toInt()

  override fun decodeLong(): Long = decodeString().toLong()

  override fun decodeFloat(): Float = decodeString().toFloat()

  override fun decodeDouble(): Double = decodeString().toDouble()

  override fun decodeChar(): Char = decodeString().single()

  override fun decodeString(): String = currentValue ?: throw SerializationException("Current URL list element is null")

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
    val value = decodeString()
    val index = enumDescriptor.getElementIndex(value)
    if (index == CompositeDecoder.UNKNOWN_NAME) {
      throw SerializationException("Enum ${enumDescriptor.serialName} does not contain $value")
    }
    return index
  }

  override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
    val value = currentValue ?: return deserializer.deserialize(this)
    return if (deserializer.descriptor.kind.isPrimitiveLike()) {
      deserializer.deserialize(this)
    } else {
      json.decodeFromString(deserializer, value)
    }
  }
}

/**
 * 解码为对应的对象
 *
 * 对于 cyxbs:// 协议应统一使用 [com.cyxbs.components.navigation.AppNavArgument.Companion.decodeFromUrl]
 * 或 [com.cyxbs.components.navigation.AppNavArgument.Companion.decodeFromRoute]，
 * 它们会按 route 自动选择 collector 与 serializer 后再调用本方法。
 */
internal fun <T> Uri.decodeFromUrl(deserializer: DeserializationStrategy<T>, json: Json): T {
  return UrlDecoder(this, json).decodeSerializableValue(deserializer)
}

@OptIn(ExperimentalSerializationApi::class)
internal fun SerialKind.isPrimitiveLike(): Boolean = this is PrimitiveKind || this == SerialKind.ENUM

private fun parseQuery(uri: Uri): Map<String, List<String>> {
  // Uri 的 query 参数 API 只支持 hierarchical URI，例如 cyxbs://path?key=value。
  require(!uri.isOpaque) { "URL must be hierarchical: $uri" }
  return uri.getQueryParameterNames().associateWith { name ->
    uri.getQueryParameters(name)
  }
}