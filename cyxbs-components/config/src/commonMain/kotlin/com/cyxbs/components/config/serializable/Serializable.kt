package com.cyxbs.components.config.serializable

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * .
 *
 * @author 985892345
 * 2024/3/11 17:19
 */

class TextUnitSerializable : KSerializer<TextUnit> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.ui.unit.TextUnit", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): TextUnit {
    val decodeString = decoder.decodeString()
    val unit = when (decodeString.substringAfterLast(".")) {
      "sp" -> TextUnitType.Sp
      "em" -> TextUnitType.Em
      else -> TextUnitType.Unspecified
    }
    val value = decodeString.substringBeforeLast(".").toFloat()
    return TextUnit(value, unit)
  }

  override fun serialize(encoder: Encoder, value: TextUnit) {
    encoder.encodeString(value.toString())
  }
}

// 此 ColorSerializable 用于保存原始的 Color，因为其内部包含不同的颜色模式，所以解析出来的值并不可读，
// 但大部分情况下使用 aRGB 模式。可以使用 ColorArgbSerializable 来进行序列化
class ColorSerializable : KSerializer<Color> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.ui.graphics.Color", PrimitiveKind.LONG)

  override fun deserialize(decoder: Decoder): Color {
    return Color(decoder.decodeLong().toULong())
  }

  override fun serialize(encoder: Encoder, value: Color) {
    encoder.encodeLong(value.value.toLong())
  }
}

class ColorArgbSerializable : KSerializer<Color> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.ui.graphics.ColorArgb", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): Color {
    return Color(decoder.decodeString().toLong(16))
  }

  override fun serialize(encoder: Encoder, value: Color) {
    encoder.encodeString((value.value.toLong() ushr 32).toString(16))
  }

  companion object {
    fun colorToArgbStr(color: Color): String {
      return (color.value.toLong() ushr 32).toString(16).uppercase()
    }

    fun argbStrToColor(argbStr: String): Color {
      return Color(argbStr.toLong(16))
    }
  }
}

class StringStateSerializable: KSerializer<MutableState<String>> {

  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.runtime.MutableState<String>", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): MutableState<String> {
    return mutableStateOf(decoder.decodeString())
  }

  override fun serialize(encoder: Encoder, value: MutableState<String>) {
    encoder.encodeString(value.value)
  }
}

class FloatStateSerializable : KSerializer<MutableFloatState> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.runtime.MutableFloatState", PrimitiveKind.FLOAT)

  override fun deserialize(decoder: Decoder): MutableFloatState {
    return mutableFloatStateOf(decoder.decodeFloat())
  }

  override fun serialize(encoder: Encoder, value: MutableFloatState) {
    encoder.encodeFloat(value.value)
  }
}

class IntStateSerializable : KSerializer<MutableIntState> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.runtime.MutableIntState", PrimitiveKind.INT)

  override fun deserialize(decoder: Decoder): MutableIntState {
    return mutableIntStateOf(decoder.decodeInt())
  }

  override fun serialize(encoder: Encoder, value: MutableIntState) {
    encoder.encodeInt(value.value)
  }
}

class LongStateSerializable : KSerializer<MutableLongState> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.runtime.MutableLongState", PrimitiveKind.LONG)

  override fun deserialize(decoder: Decoder): MutableLongState {
    return mutableLongStateOf(decoder.decodeLong())
  }

  override fun serialize(encoder: Encoder, value: MutableLongState) {
    encoder.encodeLong(value.value)
  }
}

class BooleanStateSerializable : KSerializer<MutableState<Boolean>> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("androidx.compose.runtime.MutableState<Boolean>", PrimitiveKind.BOOLEAN)

  override fun deserialize(decoder: Decoder): MutableState<Boolean> {
    return mutableStateOf(decoder.decodeBoolean())
  }

  override fun serialize(encoder: Encoder, value: MutableState<Boolean>) {
    encoder.encodeBoolean(value.value)
  }
}