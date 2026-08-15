package com.cyxbs.functions.code.editor.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIconFillType

/**
 * 按稳定语言 ID 保存动态语言文件图标的页面级缓存。
 *
 * 每种语言只在 [update] 时解析一次 SVG pathData，标签栏和文件树随后共享同一个不可变
 * [ImageVector]。更新一种语言不会覆盖其他语言；尚未加载或图标损坏的语言返回 null，由 UI
 * 显示通用文件标记。
 *
 * 缓存跟随编辑器页面生命周期，不持有 JavaScript Runtime，也不会触发 npm 下载或跨 JS 调用。
 * App 重启后的恢复由语言层 `DynamicLanguageIconCache` 负责，本类只保存当前页面已转换完成的
 * Compose 矢量对象。
 */
@Stable
class DynamicLanguageFileIconCache {
  private val vectors = mutableStateMapOf<String, ImageVector?>()
  private val vectorSources = mutableMapOf<String, DynamicLanguageFileIconSource>()

  /**
   * 解析并保存一种语言的图标；相同语言再次更新时只替换该语言的缓存。
   *
   * @param languageId Catalog 中经过校验的稳定语言 ID。
   * @param icon 对应语言 Service 返回的跨平台矢量模型。
   * @param fallbackColor 路径未指定固定颜色时使用的编辑器主题颜色。
   */
  fun update(
    languageId: String,
    icon: DynamicLanguageIcon,
    fallbackColor: Color = EditorWorkbenchColors.PrimaryText,
  ) {
    val normalizedLanguageId = languageId.normalizedLanguageId()
    val source = DynamicLanguageFileIconSource(icon, fallbackColor)
    if (vectorSources[normalizedLanguageId] == source) return
    vectorSources[normalizedLanguageId] = source
    vectors[normalizedLanguageId] = icon.toImageVectorOrNull(fallbackColor)
  }

  /** 批量恢复持久缓存中的协议图标；每种语言仍只转换一次。 */
  fun updateAll(
    icons: Map<DynamicLanguageInfo, DynamicLanguageIcon>,
    fallbackColor: Color = EditorWorkbenchColors.PrimaryText,
  ) {
    icons.forEach { (language, icon) -> update(language.languageId, icon, fallbackColor) }
  }

  /** 返回对应语言共享的矢量图；语言尚未加载或图标损坏时返回 null。 */
  operator fun get(languageId: String?): ImageVector? {
    return languageId?.let { vectors[it.normalizedLanguageId()] }
  }

  /** 清理当前页面内的全部矢量引用，不影响持久图标缓存、npm 包池及语言 Runtime。 */
  fun clear() {
    vectorSources.clear()
    vectors.clear()
  }
}

/** 创建跟随当前编辑器页面生命周期的 Compose 矢量图缓存。 */
@Composable
fun rememberDynamicLanguageFileIconCache(): DynamicLanguageFileIconCache = remember {
  DynamicLanguageFileIconCache()
}

/** 记录矢量图的完整输入，避免不同页面恢复同一协议图标时重复解析 SVG pathData。 */
private data class DynamicLanguageFileIconSource(
  val icon: DynamicLanguageIcon,
  val fallbackColor: Color,
)

/**
 * 根据文件最后一级扩展名查找 Catalog 中对应的稳定语言 ID。
 *
 * 路径没有扩展名、扩展名未知或 Catalog 尚未加载时返回 null，调用方应显示兜底图标。匹配忽略
 * 文件扩展名大小写，Catalog 中的扩展名仍由协议保证为不含前导点的小写值。
 */
internal fun resolveDynamicLanguageIdForFile(
  filePath: String,
  languages: List<DynamicLanguageInfo>,
): String? {
  val fileName = filePath.replace('\\', '/').substringAfterLast('/')
  val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
    .lowercase()
    .takeIf(String::isNotEmpty)
    ?: return null
  return languages.firstOrNull { language -> extension in language.fileExtensions }?.languageId
}

/** 统一缓存键格式，避免调用方大小写或首尾空白差异产生重复条目。 */
private fun String.normalizedLanguageId(): String {
  require(isNotBlank()) { "Dynamic language ID must not be blank." }
  return trim().lowercase()
}

/**
 * 绘制已经由页面级缓存转换完成的动态语言文件图标。
 *
 * @param imageVector 同一语言文件之间共享的矢量图；为 null 时绘制通用圆点作为降级结果。
 * @param modifier 图标布局修饰符，调用方负责指定最终显示尺寸。
 * @param contentDescription 无障碍描述；文件标签已经显示名称时可以传 null。
 */
@Composable
fun DynamicLanguageFileIcon(
  imageVector: ImageVector?,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
) {
  if (imageVector == null) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      Box(
        Modifier
          .size(8.dp)
          .background(EditorWorkbenchColors.FileIndicator, CircleShape),
      )
    }
  } else {
    Icon(
      imageVector = imageVector,
      contentDescription = contentDescription,
      modifier = modifier,
      tint = Color.Unspecified,
    )
  }
}

/**
 * 校验动态图标并转换成不可变 [ImageVector]；无效数据返回 null 供 UI 使用通用标记降级。
 *
 * 图标协议只允许有限 viewport、最多 64 条路径和 64 KiB pathData，防止损坏的动态包在文件
 * 列表中造成异常解析开销。每条路径只支持纯色填充，其他 SVG 能力应在语言包发布前展开。
 */
internal fun DynamicLanguageIcon.toImageVectorOrNull(fallbackColor: Color): ImageVector? {
  return try {
    require(
      viewportWidth.isFinite() && viewportWidth > 0F &&
        viewportWidth <= MaximumLanguageIconViewport,
    )
    require(
      viewportHeight.isFinite() && viewportHeight > 0F &&
        viewportHeight <= MaximumLanguageIconViewport,
    )
    require(paths.isNotEmpty() && paths.size <= MaximumLanguageIconPathCount)
    require(paths.sumOf { path -> path.pathData.length } <= MaximumLanguageIconPathDataLength)

    ImageVector.Builder(
      name = "DynamicLanguageFileIcon",
      defaultWidth = viewportWidth.dp,
      defaultHeight = viewportHeight.dp,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
    ).apply {
      paths.forEach { path ->
        require(path.pathData.isNotBlank())
        require(path.fillAlpha.isFinite() && path.fillAlpha in 0F..1F)
        val pathNodes = PathParser().parsePathString(path.pathData).toNodes()
        require(pathNodes.isNotEmpty())
        addPath(
          pathData = pathNodes,
          pathFillType = path.fillType.toComposeFillType(),
          fill = SolidColor(path.fillColor?.toComposeColor() ?: fallbackColor),
          fillAlpha = path.fillAlpha,
        )
      }
    }.build()
  } catch (_: Exception) {
    null
  }
}

/** 将跨语言协议枚举映射为 Compose 填充规则。 */
private fun DynamicLanguageIconFillType.toComposeFillType(): PathFillType = when (this) {
  DynamicLanguageIconFillType.NON_ZERO -> PathFillType.NonZero
  DynamicLanguageIconFillType.EVEN_ODD -> PathFillType.EvenOdd
}

/** 解析协议限定的 `#RRGGBB` 或 `#AARRGGBB` sRGB 颜色。 */
private fun String.toComposeColor(): Color {
  require(startsWith('#')) { "Dynamic language icon color must start with '#'." }
  val hexadecimal = substring(1)
  require(hexadecimal.length == 6 || hexadecimal.length == 8) {
    "Dynamic language icon color must use #RRGGBB or #AARRGGBB."
  }
  require(hexadecimal.all { character -> character.isDigit() || character.lowercaseChar() in 'a'..'f' }) {
    "Dynamic language icon color contains a non-hexadecimal character."
  }
  val alphaOffset = if (hexadecimal.length == 8) 2 else 0
  val alpha = if (alphaOffset == 0) 0xFF else hexadecimal.substring(0, 2).toInt(16)
  val red = hexadecimal.substring(alphaOffset, alphaOffset + 2).toInt(16)
  val green = hexadecimal.substring(alphaOffset + 2, alphaOffset + 4).toInt(16)
  val blue = hexadecimal.substring(alphaOffset + 4, alphaOffset + 6).toInt(16)
  return Color(red = red, green = green, blue = blue, alpha = alpha)
}

/** 单个语言图标允许的最大路径数量。 */
private const val MaximumLanguageIconPathCount = 64

/** 单个语言图标允许的最大 viewport 边长。 */
private const val MaximumLanguageIconViewport = 4096F

/** 单个语言图标允许的 pathData 总字符数。 */
private const val MaximumLanguageIconPathDataLength = 64 * 1024
