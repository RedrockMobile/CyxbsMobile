package com.cyxbs.pages.widget.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Widget 模块支持的一组桌面课表样式。 */
enum class CourseWidgetKind {
  CURRENT_COURSE,
  DAY_TIMELINE,
  WEEK_TIMETABLE,
}

/**
 * 小组件目录页所需的平台能力。
 *
 * Android 实现负责绘制可导出的示例预览并请求系统固定小组件；没有实现的平台只展示手动添加说明。
 */
interface CourseWidgetPagePlatform {

  /** 当前系统与桌面是否支持应用主动请求固定小组件。 */
  val isPinRequestSupported: Boolean

  /** 桌面虽接收固定请求、却可能另行拦截添加时，页面需要同时提示用户检查桌面权限。 */
  val needsPinPermissionGuide: Boolean

  /**
   * 请求系统把 [kind] 固定到桌面。
   *
   * @return 请求是否已成功交给系统；最终是否添加仍由用户在系统弹窗中确认。
   */
  fun requestPinWidget(kind: CourseWidgetKind): Boolean

  /** 返回本应用对应样式已固定的实例数，用于识别桌面静默拒绝请求。 */
  fun pinnedWidgetCount(kind: CourseWidgetKind): Int

  /** 打开当前应用的系统信息页，供用户授予桌面自行管理的特殊权限。 */
  fun openAppSettings(): Boolean

  /** 用确定性测试数据绘制 [kind] 的 Compose 示例，供目录页展示及图片导出。 */
  @Composable
  fun RenderPreview(kind: CourseWidgetKind, modifier: Modifier)
}
