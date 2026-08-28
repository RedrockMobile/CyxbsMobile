package com.cyxbs.pages.schedule.ui.category

import androidx.compose.ui.graphics.Color
import com.cyxbs.pages.schedule.api.ScheduleDefaultOccurrenceColor
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Category.color 中保存的结构化课表配色。
 *
 * 服务端只把该 JSON 当作不透明字符串保存；客户端记录浅色背景、浅色字体和深色背景。深色字体统一
 * 使用固定颜色，不写入分组数据。颜色统一使用 `#AARRGGBB`。
 */
@Serializable
internal data class ScheduleCategoryColorValue(
  val background: String,
  val content: String,
  val darkBackground: String,
)

/** 分组管理页提供的固定配色候选；[label] 只用于 UI，不写入服务端。 */
internal data class ScheduleCategoryColorPreset(
  val label: String,
  val value: ScheduleCategoryColorValue,
)

private val ScheduleDefaultGrayCategoryColorValue =
  ScheduleDefaultOccurrenceColor.toCategoryColorValue()

private val ScheduleCategoryColorJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/**
 * 18 组经过背景/文字对比配对的课表颜色。
 *
 * 候选不会直接复用课程已有的早课橙、午间红和晚间蓝色值，减少日程与课程仅靠细微色差区分的情况。
 * 展示顺序按中性、黄橙、红粉、紫、蓝青、绿、棕的色相过渡排列，柔和与鲜艳配色穿插展示。
 * 分组只保存选中的一组 JSON；候选数量不会扩大 wire 字段。深色模式统一使用固定浅色文字，事务会在
 * 对应背景色上额外绘制斜纹。
 */
internal val ScheduleCategoryColorPresets: List<ScheduleCategoryColorPreset> = listOf(
  ScheduleCategoryColorPreset("默认灰", ScheduleDefaultGrayCategoryColorValue),
  preset("柠檬", "#FFFFE38A", "#FF5B4800", "#BF584C10"),
  preset("亮橙", "#FFFFC98F", "#FF6B3700", "#BF5C3514"),
  preset("珊瑚", "#FFFFB0A6", "#FF7A2B24", "#BF6A342D"),
  preset("莓红", "#FFF29AB2", "#FF7A2439", "#BF65303D"),
  preset("桃粉", "#FFFFB3D8", "#FF7A2451", "#BF653151"),
  preset("藤紫", "#FFE3C6EA", "#FF61346F", "#CC403046"),
  preset("葡萄", "#FFD1BED9", "#FF452850", "#CC3D3041"),
  preset("电紫", "#FFBDAAFF", "#FF432B88", "#BF42356F"),
  preset("天蓝", "#FFA3C5FF", "#FF173E78", "#BF1F426E"),
  preset("湖蓝", "#FF86DBE9", "#FF005262", "#BF16505A"),
  preset("松石", "#FFAEDCD5", "#FF0B504B", "#CC1F4140"),
  preset("薄荷", "#FF8BE4C1", "#FF00563D", "#BF145341"),
  preset("翡翠", "#FFB3DCC4", "#FF0F5038", "#CC1F4032"),
  preset("青提", "#FFC5EE8A", "#FF315200", "#BF3B571B"),
  preset("草绿", "#FFD6ECC6", "#FF285D30", "#CC28422D"),
  preset("苔绿", "#FFC5DCAD", "#FF2B4D12", "#CC344225"),
  preset("棕褐", "#FFD8C6B5", "#FF493629", "#CC40362F"),
)

/**
 * 清单与分组的默认颜色，始终取分组预设第一项。
 *
 * 新建分组、旧数据兜底和课表默认颜色因此不会各自维护另一份默认值。
 */
internal val ScheduleDefaultCategoryColorValue: ScheduleCategoryColorValue
  get() = ScheduleCategoryColorPresets.first().value

/** 编码当前选中的完整颜色组；属性顺序由 serializer 固定，便于列表稳定匹配预设。 */
internal fun ScheduleCategoryColorValue.encodeScheduleCategoryColor(): String =
  ScheduleCategoryColorJson.encodeToString(this)

/**
 * 解析服务端透传的 Category.color。
 *
 * 旧版任意字符串、缺少字段或非法色值都返回 null，课表回退到既有默认配色，不让单条脏配置阻断快照。
 */
internal fun decodeScheduleCategoryColor(value: String?): ScheduleCategoryColorValue? {
  if (value.isNullOrBlank()) return null
  return runCatching {
    ScheduleCategoryColorJson.decodeFromString<ScheduleCategoryColorValue>(value)
  }.getOrNull()?.takeIf { color ->
    listOf(color.background, color.content, color.darkBackground)
      .all(::isArgbColor)
  }
}

/** 将持久化颜色转换为不泄漏 JSON 和 Compose 类型的 Schedule API 值。 */
internal fun ScheduleCategoryColorValue.toOccurrenceColor(): ScheduleOccurrenceColor =
  ScheduleOccurrenceColor(
    lightBackgroundArgb = background.toArgbLong(),
    lightContentArgb = content.toArgbLong(),
    darkBackgroundArgb = darkBackground.toArgbLong(),
  )

/** 返回当前主题下用于分组管理页预览的背景色。 */
internal fun ScheduleCategoryColorValue.backgroundColor(isLight: Boolean): Color =
  Color((if (isLight) background else darkBackground).toArgbLong().toInt())

/** 返回当前主题下用于分组管理页预览的字体色。 */
internal fun ScheduleCategoryColorValue.contentColor(isLight: Boolean): Color =
  if (isLight) Color(content.toArgbLong().toInt()) else ScheduleDarkContentColor

private fun preset(
  label: String,
  background: String,
  content: String,
  darkBackground: String,
): ScheduleCategoryColorPreset = ScheduleCategoryColorPreset(
  label = label,
  value = ScheduleCategoryColorValue(background, content, darkBackground),
)

private fun isArgbColor(value: String): Boolean =
  value.length == 9 && value[0] == '#' && value.drop(1).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

private fun String.toArgbLong(): Long = drop(1).toLong(radix = 16)

/** 将 API 的共享默认色转换为 Category.color 使用的 `#AARRGGBB` 字符串结构。 */
private fun ScheduleOccurrenceColor.toCategoryColorValue(): ScheduleCategoryColorValue =
  ScheduleCategoryColorValue(
    background = lightBackgroundArgb.toArgbString(),
    content = lightContentArgb.toArgbString(),
    darkBackground = darkBackgroundArgb.toArgbString(),
  )

private fun Long.toArgbString(): String = "#${toString(radix = 16).uppercase().padStart(8, '0')}"

/** 分组预览在深色模式下统一使用的文字颜色，不写入 Category.color JSON。 */
private val ScheduleDarkContentColor = Color(0xFFF0F0F2)
