package com.cyxbs.functions.code.editor.workbench

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 编辑工作台在当前窗口下采用的布局模式。 */
enum class CodeEditorWorkbenchLayoutMode {
  /** 竖向空间占主导的紧凑窗口，侧栏使用覆盖层。 */
  CompactPortrait,

  /** 横向空间占主导但宽度不足以常驻支持面板，侧栏仍使用覆盖层。 */
  CompactLandscape,

  /** 可同时容纳活动栏、支持面板和编辑器的宽窗口。 */
  Expanded,
}

/** 工作台命令在不同窗口模式中的推荐摆放位置。 */
enum class CodeEditorCommandPlacement {
  TopBar,
  ActivityBar,
  BottomBar,
  Hidden,
}

/**
 * 当前窗口的工作台布局结果。
 *
 * [runPlacement] 与 [settingsPlacement] 独立建模，后续调整横屏按钮位置时无需修改工作台结构。
 */
data class CodeEditorWorkbenchLayout(
  val mode: CodeEditorWorkbenchLayoutMode,
  val runPlacement: CodeEditorCommandPlacement,
  val settingsPlacement: CodeEditorCommandPlacement,
) {
  val usesOverlaySidePanel: Boolean
    get() = mode != CodeEditorWorkbenchLayoutMode.Expanded
}

/**
 * 根据实时可用宽高选择工作台布局。
 *
 * 实现必须是纯函数；[CodeEditorWorkbench] 会在窗口尺寸变化时重新调用，因此旋转、分屏和桌面窗口缩放
 * 都能立即切换布局，而不依赖 Activity 重建。
 */
fun interface CodeEditorWorkbenchLayoutPolicy {
  fun resolve(maxWidth: Dp, maxHeight: Dp): CodeEditorWorkbenchLayout
}

/**
 * 默认布局策略。
 *
 * 运行保留在顶部高频命令区；设置始终属于活动栏底部能力，紧凑窗口会随活动栏一起显示在抽屉内，
 * 宽屏则显示在常驻活动栏，避免顶部“更多”按钮与打开抽屉产生重复语义。
 */
object DefaultCodeEditorWorkbenchLayoutPolicy : CodeEditorWorkbenchLayoutPolicy {
  override fun resolve(maxWidth: Dp, maxHeight: Dp): CodeEditorWorkbenchLayout {
    val mode = when {
      maxWidth >= ExpandedMinimumWidth -> CodeEditorWorkbenchLayoutMode.Expanded
      maxWidth > maxHeight -> CodeEditorWorkbenchLayoutMode.CompactLandscape
      else -> CodeEditorWorkbenchLayoutMode.CompactPortrait
    }
    return CodeEditorWorkbenchLayout(
      mode = mode,
      runPlacement = CodeEditorCommandPlacement.TopBar,
      settingsPlacement = CodeEditorCommandPlacement.ActivityBar,
    )
  }

  private val ExpandedMinimumWidth = 840.dp
}

/** 侧边活动栏中的分组位置。 */
enum class CodeEditorSidePanelGroup {
  Primary,
  Bottom,
}

/** 侧边面板内容可使用的工作台操作。 */
@Stable
class CodeEditorSidePanelScope internal constructor(
  val layoutMode: CodeEditorWorkbenchLayoutMode,
  private val closePanelAction: () -> Unit,
) {
  /** 关闭手机覆盖侧栏；宽屏下仅收起当前支持面板。 */
  fun closePanel() = closePanelAction()
}

/**
 * 可注入工作台的侧边能力。
 *
 * 课程、文件、搜索和结构均使用同一模型。通用工作台不认识“课程”概念，教学业务只需额外传入一个
 * [CodeEditorSidePanel]，其他代码场景省略该项即可。
 */
data class CodeEditorSidePanel(
  val id: String,
  val title: String,
  val icon: ImageVector,
  val group: CodeEditorSidePanelGroup = CodeEditorSidePanelGroup.Primary,
  val content: @Composable CodeEditorSidePanelScope.() -> Unit,
)

/** 底部 Tool Window 内容可使用的工作台操作。 */
@Stable
class CodeEditorToolWindowScope internal constructor(
  private val closeAction: () -> Unit,
) {
  /** 收起当前工具窗口，底部按钮条仍保持可见。 */
  fun close() = closeAction()
}

/**
 * IDEA 风格底部工具窗口描述。
 *
 * 每个工具窗口只提供按钮和内容；高度、互斥切换、拖拽吸附和固定底栏由工作台统一管理。
 */
data class CodeEditorToolWindow(
  val id: String,
  val title: String,
  val icon: ImageVector? = null,
  val content: @Composable CodeEditorToolWindowScope.() -> Unit,
)

/**
 * 编辑工作台纯 UI 状态。
 *
 * 文件内容、语言服务和运行状态仍由业务持有；该状态只记录面板选择、宽屏侧栏宽度与工具窗口高度，
 * 窗口横竖切换时会复用同一份状态，避免布局切换导致用户上下文丢失。
 */
@Stable
class CodeEditorWorkbenchState internal constructor(
  initialSidePanelId: String?,
  initialToolWindowId: String?,
) {
  var selectedSidePanelId: String? by mutableStateOf(initialSidePanelId)
    private set

  /**
   * 侧边面板是否可见。
   *
   * 该状态不绑定具体窗口形态：紧凑窗口将其展示为有界抽屉，宽窗口将其展示为常驻支持面板。
   * 因此旋转、分屏或拖拽窗口时只替换容器，不会丢失用户正在查看的面板。
   */
  var isSidePanelVisible: Boolean by mutableStateOf(false)
    private set

  var selectedToolWindowId: String? by mutableStateOf(initialToolWindowId)
    private set

  internal var toolWindowHeightFraction: Float by mutableFloatStateOf(DefaultToolWindowHeightFraction)

  /**
   * 宽屏侧栏区域的用户偏好宽度，单位为 dp，并包含活动栏与拖拽分隔槽。
   *
   * 窗口临时缩小时，工作台只会钳制本次展示宽度而不会覆盖该值；窗口重新变宽后可恢复用户选择。
   */
  internal var sidePanelRegionWidthDp: Float by mutableFloatStateOf(DefaultSidePanelRegionWidthDp)

  /** 选择并显示侧边能力；具体使用抽屉还是常驻面板由工作台当前布局决定。 */
  fun selectSidePanel(id: String) {
    selectedSidePanelId = id
    isSidePanelVisible = true
  }

  /** 隐藏侧边面板但保留最后选择，便于再次打开或切换窗口形态时恢复用户上下文。 */
  fun closeSidePanel() {
    isSidePanelVisible = false
  }

  /**
   * 同步抽屉的手势状态。
   *
   * 仅由工作台在抽屉完成打开或关闭状态转换时调用，业务仍通过 [selectSidePanel] 与
   * [closeSidePanel] 控制面板。
   */
  internal fun updateSidePanelVisibility(visible: Boolean) {
    isSidePanelVisible = visible
  }

  /** 外部移除当前面板时同时清理选择与可见状态，避免保留无法恢复的无效标识。 */
  internal fun clearSidePanelSelection() {
    selectedSidePanelId = null
    isSidePanelVisible = false
  }

  /** 保存宽屏侧栏拖拽后的用户偏好；可展示范围仍由当前窗口尺寸在布局层约束。 */
  internal fun updateSidePanelRegionWidth(widthDp: Float) {
    sidePanelRegionWidthDp = widthDp
  }

  /** 点击底部按钮时切换对应工具窗口，同一时间只展开一个。 */
  fun toggleToolWindow(id: String) {
    selectedToolWindowId = if (selectedToolWindowId == id) null else id
  }

  /** 展开指定工具窗口，适用于运行按钮需要同时触发执行与展示输出的场景。 */
  fun showToolWindow(id: String) {
    selectedToolWindowId = id
  }

  /** 收起当前工具窗口。 */
  fun closeToolWindow() {
    selectedToolWindowId = null
  }

  internal companion object {
    const val DefaultToolWindowHeightFraction = 0.42F
    const val DefaultSidePanelRegionWidthDp = 360F

    /** 保存面板、宽度和工具窗口选择，确保宿主发生配置重建后仍恢复编辑上下文。 */
    val Saver = listSaver<CodeEditorWorkbenchState, Any>(
      save = { state ->
        listOf(
          state.selectedSidePanelId.orEmpty(),
          state.isSidePanelVisible,
          state.selectedToolWindowId.orEmpty(),
          state.toolWindowHeightFraction,
          state.sidePanelRegionWidthDp,
        )
      },
      restore = { values ->
        CodeEditorWorkbenchState(
          initialSidePanelId = (values[0] as String).ifEmpty { null },
          initialToolWindowId = (values[2] as String).ifEmpty { null },
        ).also { state ->
          state.isSidePanelVisible = values[1] as Boolean
          state.toolWindowHeightFraction = values[3] as Float
          // 兼容尚未保存侧栏宽度的旧状态；新增字段只影响 UI 偏好，不改变原有选择语义。
          state.sidePanelRegionWidthDp =
            (values.getOrNull(4) as? Float) ?: DefaultSidePanelRegionWidthDp
        }
      },
    )
  }
}

/** 创建并记住工作台 UI 状态。 */
@Composable
fun rememberCodeEditorWorkbenchState(
  initialSidePanelId: String? = null,
  initialToolWindowId: String? = null,
): CodeEditorWorkbenchState {
  return rememberSaveable(saver = CodeEditorWorkbenchState.Saver) {
    CodeEditorWorkbenchState(
      initialSidePanelId = initialSidePanelId,
      initialToolWindowId = initialToolWindowId,
    )
  }
}
