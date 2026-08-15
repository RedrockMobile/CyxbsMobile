package com.cyxbs.functions.code.language.js.bridge

import kotlinx.serialization.Serializable

/**
 * 动态语言包提供的跨平台文件图标。
 *
 * 图标只描述原点为 `(0, 0)` 的 SVG viewport 和已展开变换的填充路径，不包含平台资源、完整
 * SVG XML、脚本、外链、文本、滤镜或渐变。客户端可以直接使用 Compose commonMain 的
 * `PathParser` 和 `ImageVector` 渲染，避免为 Android、iOS 与 Desktop 分别保存图片资源。
 *
 * @param viewportWidth SVG viewport 宽度，必须为有限正数。
 * @param viewportHeight SVG viewport 高度，必须为有限正数。
 * @param paths 按绘制顺序排列的路径；多路径可以组成 Java、JavaScript 等多色图标。
 */
@Serializable
data class DynamicLanguageIcon(
  val viewportWidth: Float,
  val viewportHeight: Float,
  val paths: List<DynamicLanguageIconPath>,
)

/**
 * 动态语言图标中的一条 SVG 填充路径。
 *
 * 语言包发布前应将 stroke、group transform 和文字转换为最终填充轮廓，使客户端只需解析标准
 * SVG pathData。这样可以保持协议稳定，也能限制远端图标可使用的绘制能力。
 *
 * @param pathData 标准 SVG pathData，支持 Compose `PathParser` 能解析的命令。
 * @param fillColor `#RRGGBB` 或 `#AARRGGBB`；为 null 时使用客户端当前内容颜色。
 * @param fillAlpha 路径透明度，取值范围为 0 到 1。
 * @param fillType 路径内部区域的填充规则。
 */
@Serializable
data class DynamicLanguageIconPath(
  val pathData: String,
  val fillColor: String? = null,
  val fillAlpha: Float = 1F,
  val fillType: DynamicLanguageIconFillType = DynamicLanguageIconFillType.NON_ZERO,
)

/** SVG 路径的填充规则，与 Compose `PathFillType` 一一对应。 */
@Serializable
enum class DynamicLanguageIconFillType {
  /** 使用非零环绕规则判断路径内部区域。 */
  NON_ZERO,

  /** 使用奇偶规则判断路径内部区域。 */
  EVEN_ODD,
}
