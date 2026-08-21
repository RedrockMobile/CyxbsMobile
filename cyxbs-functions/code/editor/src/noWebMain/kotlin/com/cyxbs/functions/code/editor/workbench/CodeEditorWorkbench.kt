package com.cyxbs.functions.code.editor.workbench

import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.cyxbs.functions.code.editor.theme.CodeEditorConnectedCornerRadius
import com.cyxbs.functions.code.editor.workbench.internal.CodeEditorSystemBarsEffect
import kotlinx.coroutines.flow.drop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * 跨业务代码编辑工作台。
 *
 * 工作台只负责布局、面板选择和 Tool Window 交互，源码、运行器、语言服务及课程状态全部由调用方
 * 注入。侧边能力使用 [CodeEditorSidePanel] 扩展，因此普通代码场景可以只传文件与搜索，教学场景
 * 再额外传课程面板。
 *
 * ```text
 * 紧凑窗口                         宽窗口
 * ┌──────── 编辑器 ────────┐      ┌ 活动栏 ┬ 支持面板 ┬── 编辑器 ──┐
 * │ 侧栏以覆盖层显示         │      │        │          │            │
 * │                         │      │        │          ├ ToolWindow │
 * ├ ToolWindow（向上展开）──┤      ├────────┴──────────┴────────────┤
 * └ Run / Problems / Perf ──┘      └ Run / Problems / Perf ────────┘
 * ```
 *
 * 底部按钮条始终固定在最下方，工具内容只从其上方向上展开。布局由 [layoutPolicy] 根据每次重组时的
 * 实际宽高决定，支持旋转、分屏、折叠屏展开及 Desktop 拖拽窗口后即时切换。
 *
 * @param title 工作台标题。
 * @param activeDocumentLabel 当前文件或文档的展示名。
 * @param subtitle 标题下方的场景说明；为空时仅显示标题。
 * @param openDocumentLabels 已打开文档的稳定标识或路径，用于渲染编辑器标签栏。
 * @param breadcrumbs 当前文档的路径导航；为空时根据 [activeDocumentLabel] 生成。
 * @param onDocumentSelected 点击标签后的切换回调；为空时标签仅用于展示。
 * @param documentIcon 文档标签图标槽位；调用方可按文件路径提供语言图标，为空时使用通用圆点。
 * @param sidePanels 可用侧边能力，顺序决定活动栏顺序。
 * @param toolWindows 可用底部工具窗口，顺序决定底部按钮顺序。
 * @param onRun 顶部运行按钮回调；传入 null 时隐藏运行按钮。
 * @param runButtonModifier 顶部运行按钮的布局修饰；教学场景可用它注册引导锚点。
 * @param runPopupContent 锚定在顶部运行按钮上的弹出内容；为空时只展示普通运行按钮。
 * @param onUndo 撤销当前编辑器操作；传入 null 时隐藏撤销按钮。
 * @param canUndo 当前是否存在可撤销操作；为 false 时保留灰色按钮但禁止点击。
 * @param onRedo 重做当前编辑器操作；传入 null 时隐藏重做按钮。
 * @param canRedo 当前是否存在可重做操作；为 false 时保留灰色按钮但禁止点击。
 * @param onSearch 打开当前编辑器的文内搜索；传入 null 时隐藏搜索按钮。
 * @param onBack 关闭或返回工作台；传入后会在活动栏底部提供显式入口。
 * @param editorGutterWidth 编辑器左侧 gutter 的实时宽度，用于对齐底部按钮和展开面板。
 * @param editor 编辑器主体。
 */
@Composable
fun CodeEditorWorkbench(
  title: String,
  activeDocumentLabel: String,
  sidePanels: List<CodeEditorSidePanel>,
  toolWindows: List<CodeEditorToolWindow>,
  state: CodeEditorWorkbenchState,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  openDocumentLabels: List<String> = listOf(activeDocumentLabel),
  breadcrumbs: List<String> = emptyList(),
  onDocumentSelected: ((String) -> Unit)? = null,
  documentIcon: (@Composable (document: String, modifier: Modifier) -> Unit)? = null,
  layoutPolicy: CodeEditorWorkbenchLayoutPolicy = DefaultCodeEditorWorkbenchLayoutPolicy,
  isRunning: Boolean = false,
  onBack: (() -> Unit)? = null,
  onRun: (() -> Unit)? = null,
  runButtonModifier: Modifier = Modifier,
  runPopupContent: (@Composable () -> Unit)? = null,
  onUndo: (() -> Unit)? = null,
  canUndo: Boolean = onUndo != null,
  onRedo: (() -> Unit)? = null,
  canRedo: Boolean = onRedo != null,
  onSearch: (() -> Unit)? = null,
  editorGutterWidth: Dp = DefaultEditorGutterWidth,
  editor: @Composable () -> Unit,
) {
  CodeEditorSystemBarsEffect()
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      // 系统缺口区域只绘制安全底色；工作台背景从 safeDrawing 内部开始，避免横屏活动栏
      // 与摄像头区域连成一体，看起来像抽屉伸到了显示缺口下面。
      .background(EditorWorkbenchColors.SystemInsetBackground)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .background(EditorWorkbenchColors.EditorBackground),
  ) {
    val layout = layoutPolicy.resolve(maxWidth = maxWidth, maxHeight = maxHeight)
    val selectedPanel = sidePanels.firstOrNull { it.id == state.selectedSidePanelId }
    val firstPrimarySidePanelId = sidePanels.firstOrNull {
      it.group == CodeEditorSidePanelGroup.Primary
    }?.id
    val selectedPanelConnectsToTopEdge = selectedPanel?.id == firstPrimarySidePanelId
    val overlaySidePanelProgress = remember {
      mutableFloatStateOf(
        if (state.isSidePanelVisible && selectedPanel != null) 1F else 0F,
      )
    }
    val selectedToolWindow = toolWindows.firstOrNull { it.id == state.selectedToolWindowId }
    val closeWorkbenchAction = onBack
    val settingsPanel = sidePanels.firstOrNull { it.group == CodeEditorSidePanelGroup.Bottom }
    val bottomSettingsAction = if (layout.settingsPlacement == CodeEditorCommandPlacement.BottomBar) {
      settingsPanel?.let { panel -> { state.selectSidePanel(panel.id) } }
    } else {
      null
    }
    // 面板不再提供统一关闭按钮，重复点击当前活动栏入口负责关闭；选择其他入口则直接切换内容。
    val onActivitySidePanelSelected: (CodeEditorSidePanel) -> Unit = { panel ->
      if (state.isSidePanelVisible && state.selectedSidePanelId == panel.id) {
        state.closeSidePanel()
      } else {
        state.selectSidePanel(panel.id)
      }
    }
    val minimumExpandedSidePanelRegionWidth = MinimumExpandedSidePanelRegionWidth
    val maximumExpandedSidePanelRegionWidth = (maxWidth - MinimumExpandedEditorWidth)
      .coerceIn(
        minimumValue = minimumExpandedSidePanelRegionWidth,
        maximumValue = MaximumExpandedSidePanelRegionWidth,
      )
    // 窗口缩小时只约束当前展示值，不回写用户偏好；重新变宽后仍可恢复此前拖出的宽度。
    val expandedSidePanelRegionWidth = state.sidePanelRegionWidthDp.dp.coerceIn(
      minimumValue = minimumExpandedSidePanelRegionWidth,
      maximumValue = maximumExpandedSidePanelRegionWidth,
    )

    // 外部动态移除当前面板或工具窗口时立即清理选择，避免留下不可见但仍占布局空间的状态。
    LaunchedEffect(sidePanels, state.selectedSidePanelId) {
      if (state.selectedSidePanelId != null && selectedPanel == null) {
        state.clearSidePanelSelection()
      }
    }
    LaunchedEffect(toolWindows, state.selectedToolWindowId) {
      if (state.selectedToolWindowId != null && selectedToolWindow == null) {
        state.closeToolWindow()
      }
    }
    // 从宽屏切入紧凑布局时，抽屉尚未产生 offset，先使用业务落点避免首帧图标状态错误。
    LaunchedEffect(layout.usesOverlaySidePanel) {
      if (layout.usesOverlaySidePanel) {
        overlaySidePanelProgress.floatValue =
          if (state.isSidePanelVisible && selectedPanel != null) 1F else 0F
      }
    }

    val workbenchBody: @Composable () -> Unit = {
      // 左侧活动栏与支持面板占满工作区；工具按钮条只属于右侧编辑器列。
      Row(Modifier.fillMaxSize()) {
        if (layout.mode == CodeEditorWorkbenchLayoutMode.Expanded) {
          EditorActivityBar(
            sidePanels = sidePanels,
            selectedPanelId = selectedPanel?.id.takeIf { state.isSidePanelVisible },
            onSelect = onActivitySidePanelSelected,
            isRunning = isRunning,
            onRun = onRun.takeIf { layout.runPlacement == CodeEditorCommandPlacement.ActivityBar },
            showBottomPanels = layout.settingsPlacement == CodeEditorCommandPlacement.ActivityBar,
            onCloseWorkbench = closeWorkbenchAction,
          )
          if (state.isSidePanelVisible && selectedPanel != null) {
            ResizableExpandedSidePanel(
              panel = selectedPanel,
              layoutMode = layout.mode,
              connectsToTopEdge = selectedPanelConnectsToTopEdge,
              onClose = state::closeSidePanel,
              regionWidth = expandedSidePanelRegionWidth,
              minimumRegionWidth = minimumExpandedSidePanelRegionWidth,
              maximumRegionWidth = maximumExpandedSidePanelRegionWidth,
              state = state,
            )
          }
        }

        EditorAndToolWindow(
          activeDocumentLabel = activeDocumentLabel,
          openDocumentLabels = openDocumentLabels,
          breadcrumbs = breadcrumbs,
          onDocumentSelected = onDocumentSelected,
          documentIcon = documentIcon,
          toolWindows = toolWindows,
          selectedToolWindow = selectedToolWindow,
          isRunning = isRunning,
          onRun = onRun.takeIf { layout.runPlacement == CodeEditorCommandPlacement.BottomBar },
          onOpenSettings = bottomSettingsAction,
          editorGutterWidth = editorGutterWidth,
          state = state,
          editor = editor,
          modifier = Modifier.weight(1F),
        )
      }
    }

    Column(Modifier.fillMaxSize()) {
      // 标题栏位于抽屉外部，因此打开侧栏后不会移动、变暗或失去交互。
      EditorWorkbenchTopBar(
        title = title,
        subtitle = subtitle,
        layout = layout,
        isRunning = isRunning,
        onBack = onBack,
        sidePanelToOpen = selectedPanel ?: sidePanels.firstOrNull(),
        sidePanelNavigationProgress = if (layout.usesOverlaySidePanel) {
          overlaySidePanelProgress.floatValue
        } else {
          0F
        },
        onOpenSidePanel = { panel -> state.selectSidePanel(panel.id) },
        onCloseSidePanel = state::closeSidePanel,
        onRun = onRun,
        runButtonModifier = runButtonModifier,
        runPopupContent = runPopupContent,
        onUndo = onUndo,
        canUndo = canUndo,
        onRedo = onRedo,
        canRedo = canRedo,
        onSearch = onSearch,
      )

      if (layout.usesOverlaySidePanel) {
        CompactSidePanelDrawer(
          sidePanels = sidePanels,
          selectedPanel = selectedPanel,
          layoutMode = layout.mode,
          state = state,
          isRunning = isRunning,
          onRun = onRun.takeIf { layout.runPlacement == CodeEditorCommandPlacement.ActivityBar },
          showBottomPanels = layout.settingsPlacement == CodeEditorCommandPlacement.ActivityBar,
          onCloseWorkbench = closeWorkbenchAction,
          onProgressChanged = { progress -> overlaySidePanelProgress.floatValue = progress },
          modifier = Modifier.fillMaxWidth().weight(1F),
          content = workbenchBody,
        )
      } else {
        Box(Modifier.fillMaxWidth().weight(1F)) {
          workbenchBody()
        }
      }
    }
  }
}

/** 编辑区与向上展开的 Tool Window；面板占用编辑区空间而非拦截其余编辑区域。 */
@Composable
private fun EditorAndToolWindow(
  activeDocumentLabel: String,
  openDocumentLabels: List<String>,
  breadcrumbs: List<String>,
  onDocumentSelected: ((String) -> Unit)?,
  documentIcon: (@Composable (document: String, modifier: Modifier) -> Unit)?,
  toolWindows: List<CodeEditorToolWindow>,
  selectedToolWindow: CodeEditorToolWindow?,
  isRunning: Boolean,
  onRun: (() -> Unit)?,
  onOpenSettings: (() -> Unit)?,
  editorGutterWidth: Dp,
  state: CodeEditorWorkbenchState,
  editor: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier.fillMaxHeight()) {
    val availableHeight = maxHeight
    val maximumToolHeight = availableHeight * MaximumToolWindowHeightFraction
    val minimumToolHeight = MinimumToolWindowHeight.coerceAtMost(maximumToolHeight)
    // 拖动边界由当前窗口实际可用高度推导，松手后保留连续位置，不再吸附到预设档位。
    val minimumToolHeightFraction = if (availableHeight.value > 0F) {
      minimumToolHeight.value / availableHeight.value
    } else {
      0F
    }
    val maximumToolHeightFraction = if (availableHeight.value > 0F) {
      maximumToolHeight.value / availableHeight.value
    } else {
      0F
    }
    val toolHeight = (availableHeight * state.toolWindowHeightFraction)
      .coerceIn(minimumToolHeight, maximumToolHeight)

    Column(
      Modifier
        .fillMaxSize()
        // 外层淡黑轮廓收住代码区底部；顶部继续与文件标签栏保持直线衔接。
        .clip(CodeAreaOuterShape)
        .background(EditorWorkbenchColors.ToolWindowBarBackground),
    ) {
      EditorDocumentBar(
        activeDocumentLabel = activeDocumentLabel,
        openDocumentLabels = openDocumentLabels,
        breadcrumbs = breadcrumbs,
        onDocumentSelected = onDocumentSelected,
        documentIcon = documentIcon,
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1F)
          // 代码内容直接延伸到右边界，右侧不再额外绘制包边或圆角。
          .background(EditorWorkbenchColors.EditorBackground),
      ) {
        editor()
      }
      if (selectedToolWindow != null) {
        // 与侧栏分隔槽保持相同尺寸；整条间距负责拖动，不额外绘制短横把手。
        Spacer(
          Modifier
            .fillMaxWidth()
            .height(PanelResizeGutterSize)
            .semantics { contentDescription = "调整工具窗口高度" }
            .pointerInput(availableHeight) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                val fractionDelta = -dragAmount.y / availableHeight.toPx()
                state.toolWindowHeightFraction = (state.toolWindowHeightFraction + fractionDelta)
                  .coerceIn(minimumToolHeightFraction, maximumToolHeightFraction)
              }
            },
        )
        EditorToolWindowContent(
          toolWindow = selectedToolWindow,
          height = toolHeight,
          editorGutterWidth = editorGutterWidth,
          connectsToLeadingButton = onRun == null &&
            toolWindows.firstOrNull()?.id == selectedToolWindow.id,
          state = state,
        )
      }
      EditorToolWindowBar(
        toolWindows = toolWindows,
        selectedToolWindowId = selectedToolWindow?.id,
        onToggle = state::toggleToolWindow,
        isRunning = isRunning,
        onRun = onRun,
        onOpenSettings = onOpenSettings,
        editorGutterWidth = editorGutterWidth,
      )
    }
  }
}

/**
 * 编辑器顶部命令栏。
 *
 * 紧凑窗口左侧提供抽屉入口，并随抽屉状态在菜单与返回形态间切换；宽屏已常驻活动栏，
 * 不再展示重复的侧栏或返回入口。
 * 编辑命令固定按运行、撤销、重做、搜索排列，搜索始终位于最右侧。设置属于全局侧边能力，
 * 统一放到活动栏底部，避免与抽屉入口产生歧义。
 */
@Composable
private fun EditorWorkbenchTopBar(
  title: String,
  subtitle: String?,
  layout: CodeEditorWorkbenchLayout,
  isRunning: Boolean,
  onBack: (() -> Unit)?,
  sidePanelToOpen: CodeEditorSidePanel?,
  sidePanelNavigationProgress: Float,
  onOpenSidePanel: (CodeEditorSidePanel) -> Unit,
  onCloseSidePanel: () -> Unit,
  onRun: (() -> Unit)?,
  runButtonModifier: Modifier,
  runPopupContent: (@Composable () -> Unit)?,
  onUndo: (() -> Unit)?,
  canUndo: Boolean,
  onRedo: (() -> Unit)?,
  canRedo: Boolean,
  onSearch: (() -> Unit)?,
) {
  Surface(color = EditorWorkbenchColors.ToolbarBackground) {
    Row(
      modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (layout.mode != CodeEditorWorkbenchLayoutMode.Expanded) {
        if (sidePanelToOpen != null) {
          val shouldCloseSidePanel = sidePanelNavigationProgress >= 0.5F
          AnimatedSidePanelNavigationAction(
            progress = sidePanelNavigationProgress,
            onClick = {
              if (shouldCloseSidePanel) onCloseSidePanel() else onOpenSidePanel(sidePanelToOpen)
            },
          )
        } else if (onBack != null) {
          EditorTopBarAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
          )
        }
      }

      Column(modifier = Modifier.weight(1F).padding(horizontal = 10.dp)) {
        Text(
          text = title,
          color = EditorWorkbenchColors.PrimaryText,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            color = EditorWorkbenchColors.SecondaryText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (onRun != null && layout.runPlacement == CodeEditorCommandPlacement.TopBar) {
        // Box 同时作为 DropdownMenu 的定位锚点，使多入口选择紧贴运行按钮而不是居中遮挡编辑器。
        Box(modifier = runButtonModifier) {
          EditorTopBarAction(
            icon = Icons.Default.PlayArrow,
            contentDescription = if (isRunning) "运行中" else "运行",
            enabled = !isRunning,
            contentColor = if (isRunning) EditorWorkbenchColors.SecondaryText else Color.White,
            onClick = onRun,
          )
          runPopupContent?.invoke()
        }
      }
      if (onUndo != null) {
        EditorTopBarAction(
          icon = Icons.AutoMirrored.Filled.Undo,
          contentDescription = "撤销",
          enabled = canUndo,
          contentColor = if (canUndo) Color.White else EditorWorkbenchColors.SecondaryText,
          onClick = onUndo,
        )
      }
      if (onRedo != null) {
        EditorTopBarAction(
          icon = Icons.AutoMirrored.Filled.Redo,
          contentDescription = "重做",
          enabled = canRedo,
          contentColor = if (canRedo) Color.White else EditorWorkbenchColors.SecondaryText,
          onClick = onRedo,
        )
      }
      if (onSearch != null) {
        EditorTopBarAction(
          icon = Icons.Default.Search,
          contentDescription = "搜索当前文件",
          contentColor = Color.White,
          onClick = onSearch,
        )
      }
    }
  }
}

/**
 * 紧凑窗口的侧栏导航按钮，将三条横线连续形变为返回箭头。
 *
 * Android View 的 `DrawerArrowDrawable` 没有 Compose Multiplatform 对应实现；这里在一个 Canvas
 * 内让三根线以自身中心为基准连续平移、旋转并缩短到 [Icons.AutoMirrored.Filled.ArrowBack] 的
 * 24×24 边界。
 * 三根线由同一个 Path 一次描边，并在箭头尖端使用圆头重叠，避免独立面片抗锯齿产生接缝。
 * 形变进度直接取自抽屉 offset，不创建独立动画，因此手势拖动、松手回弹和按钮开关可以在
 * 任意进度无缝衔接。
 *
 * @param progress 抽屉从完全关闭到完全打开的归一化进度，超出 0～1 的值会被限制到有效范围。
 */
@Composable
private fun AnimatedSidePanelNavigationAction(
  progress: Float,
  onClick: () -> Unit,
) {
  val coercedProgress = progress.coerceIn(0F, 1F)
  val navigationContentDescription = if (coercedProgress >= 0.5F) "关闭侧栏" else "打开侧栏"

  Box(
    modifier = Modifier
      .width(36.dp)
      .height(48.dp)
      .clickable(onClick = onClick)
      .semantics { contentDescription = navigationContentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(20.dp)) {
      fun viewportOffset(x: Float, y: Float): Offset {
        val directionalX = if (layoutDirection == LayoutDirection.Ltr) x else 24F - x
        return Offset(
          x = size.width * directionalX / 24F,
          y = size.height * y / 24F,
        )
      }

      fun interpolate(start: Offset, end: Offset): Offset {
        return Offset(
          x = start.x + (end.x - start.x) * coercedProgress,
          y = start.y + (end.y - start.y) * coercedProgress,
        )
      }

      val path = Path().apply {
        fun addTransformingLine(
          menuCenter: Offset,
          arrowCenter: Offset,
          arrowAngleRadians: Float,
          arrowLength: Float,
        ) {
          val center = interpolate(menuCenter, arrowCenter)
          val length = 16F + (arrowLength - 16F) * coercedProgress
          val angle = arrowAngleRadians * coercedProgress
          val halfWidth = cos(angle) * length / 2F
          val halfHeight = sin(angle) * length / 2F
          val start = viewportOffset(center.x - halfWidth, center.y - halfHeight)
          val end = viewportOffset(center.x + halfWidth, center.y + halfHeight)
          moveTo(start.x, start.y)
          lineTo(end.x, end.y)
        }

        // 上、下横线分别绕自身中心旋转，并在平移过程中缩短为 45° 箭头斜边。
        addTransformingLine(
          menuCenter = Offset(12F, 7F),
          arrowCenter = Offset(8F, 8F),
          arrowAngleRadians = (-PI / 4F).toFloat(),
          arrowLength = 8F * sqrt(2F),
        )
        addTransformingLine(
          menuCenter = Offset(12F, 12F),
          arrowCenter = Offset(12F, 12F),
          arrowAngleRadians = 0F,
          arrowLength = 16F,
        )
        addTransformingLine(
          menuCenter = Offset(12F, 17F),
          arrowCenter = Offset(8F, 16F),
          arrowAngleRadians = (PI / 4F).toFloat(),
          arrowLength = 8F * sqrt(2F),
        )
      }
      drawPath(
        path = path,
        color = EditorWorkbenchColors.PrimaryText,
        style = Stroke(
          width = size.minDimension * 2F / 24F,
          cap = StrokeCap.Round,
        ),
      )
    }
  }
}

/**
 * 无背景的紧凑顶部操作。
 *
 * 横向占位压缩为 36dp，纵向仍保留 48dp 触摸高度；纯图标避免多个相邻圆角容器造成视觉拥挤。
 */
@Composable
private fun EditorTopBarAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  enabled: Boolean = true,
  contentColor: Color = EditorWorkbenchColors.PrimaryText,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier.width(36.dp).height(48.dp).clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(20.dp))
  }
}

/** 文件标签与路径导航；均属于编辑器列，因此宽屏时不会挤入支持面板。 */
@Composable
private fun EditorDocumentBar(
  activeDocumentLabel: String,
  openDocumentLabels: List<String>,
  breadcrumbs: List<String>,
  onDocumentSelected: ((String) -> Unit)?,
  documentIcon: (@Composable (document: String, modifier: Modifier) -> Unit)?,
) {
  val documents = openDocumentLabels.distinct().ifEmpty { listOf(activeDocumentLabel) }
  val selectedDocumentIndex = documents.indexOf(activeDocumentLabel).coerceAtLeast(0)
  val density = LocalDensity.current
  val connectedCornerRadiusPx = with(density) { CodeEditorConnectedCornerRadius.toPx() }
  val tabSpacingPx = with(density) {
    (DocumentTabSpacing - CodeEditorConnectedCornerRadius).toPx()
  }
  val tabScrollState = rememberScrollState()
  val tabWidthsPx = remember(documents) { mutableStateMapOf<String, Int>() }
  val leadingConnectionProgress by remember(
    documents,
    selectedDocumentIndex,
    connectedCornerRadiusPx,
    tabSpacingPx,
  ) {
    derivedStateOf {
      val hasMeasuredLeadingTabs = documents.take(selectedDocumentIndex).all(tabWidthsPx::containsKey)
      if (!hasMeasuredLeadingTabs) {
        if (selectedDocumentIndex == 0) 1F else 0F
      } else {
        val selectedTabContentStartPx = documents.take(selectedDocumentIndex)
          .sumOf { document -> tabWidthsPx.getValue(document) } +
          tabSpacingPx * selectedDocumentIndex
        val selectedTabVisibleStartPx = selectedTabContentStartPx - tabScrollState.value
        // 直接读取逐帧变化的滚动值，确保标签与路径栏在最后一个圆角半径内同步收平。
        ((connectedCornerRadiusPx - selectedTabVisibleStartPx) / connectedCornerRadiusPx)
          .coerceIn(0F, 1F)
      }
    }
  }
  Column(Modifier.fillMaxWidth().background(EditorWorkbenchColors.DocumentBarBackground)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(DocumentTabBarHeight)
        .horizontalScroll(tabScrollState)
        // 标签与侧边面板共同从标题栏下缘开始；右侧保留滚动结束留白，避免最后一个标签贴边。
        .padding(end = 8.dp),
      // 反圆角自身会占据 7dp 肩部；相邻标签轻微重叠后只保留 3dp 可见间距，不压缩标签内容。
      horizontalArrangement = Arrangement.spacedBy(DocumentTabSpacing - CodeEditorConnectedCornerRadius),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      documents.forEach { document ->
        val selected = document == activeDocumentLabel
        val bringIntoViewRequester = remember(document) { BringIntoViewRequester() }
        // 文件切换可能来自侧栏、定义跳转或标签点击，统一确保新标签完整进入横向可视区域。
        LaunchedEffect(selected) {
          if (selected) bringIntoViewRequester.bringIntoView()
        }
        Row(
          modifier = Modifier
            .height(DocumentTabBarHeight)
            .onSizeChanged { size ->
              if (tabWidthsPx[document] != size.width) tabWidthsPx[document] = size.width
            }
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(
              if (selected) {
                Modifier.background(
                  color = EditorWorkbenchColors.ActiveDocumentBackground,
                  shape = raisedDocumentTabShape(
                    leadingConnectionProgress = leadingConnectionProgress,
                  ),
                )
              } else {
                Modifier
              },
            )
            .clickable(enabled = onDocumentSelected != null) { onDocumentSelected?.invoke(document) }
            .padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (documentIcon == null) {
            Box(
              Modifier
                .size(8.dp)
                .background(EditorWorkbenchColors.FileIndicator, CircleShape),
            )
          } else {
            documentIcon(document, Modifier.size(14.dp))
          }
          Text(
            text = document.substringAfterLast('/'),
            color = if (selected) EditorWorkbenchColors.PrimaryText else EditorWorkbenchColors.SecondaryText,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = if (documentIcon == null) 8.dp else 6.dp),
          )
        }
      }
    }
    val path = breadcrumbs.ifEmpty { activeDocumentLabel.split('/').filter(String::isNotBlank) }
    if (path.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(DocumentBreadcrumbHeight)
          // 与标签复用连接进度，使目录栏左上角在滑动贴边时同步由圆角收成直线。
          .background(
            color = EditorWorkbenchColors.ActiveDocumentBackground,
            shape = RoundedCornerShape(
              topStart = CodeEditorConnectedCornerRadius * (1F - leadingConnectionProgress),
            ),
          )
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        path.forEachIndexed { index, part ->
          if (index > 0) {
            Text("›", color = EditorWorkbenchColors.MutedText, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp))
          }
          Text(
            text = part,
            color = if (index == path.lastIndex) EditorWorkbenchColors.PrimaryText else EditorWorkbenchColors.MutedText,
            fontSize = 10.sp,
            fontWeight = if (index == path.lastIndex) FontWeight.Medium else FontWeight.Normal,
          )
        }
      }
    }
  }
}

/**
 * 根据标签贴近可视区域左边缘的进度生成轮廓。
 *
 * [leadingConnectionProgress] 为 0 时保留完整反圆角，为 1 时左侧收成连接路径栏的竖直边；
 * 中间值用于手势滚动过程中的连续过渡。标签顶部保留内收圆角，底部反向曲线仍限制在自身边界内，
 * 不会覆盖相邻标签的点击区域。
 */
private fun raisedDocumentTabShape(leadingConnectionProgress: Float): Shape = object : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val flare = with(density) { CodeEditorConnectedCornerRadius.toPx() }
      .coerceAtMost(size.width / 4F)
      .coerceAtMost(size.height / 2F)
    val leadingInset = flare * (1F - leadingConnectionProgress.coerceIn(0F, 1F))
    val topRadius = with(density) { CodeEditorConnectedCornerRadius.toPx() }
      .coerceAtMost((size.width - leadingInset - flare) / 2F)
      .coerceAtMost(size.height - flare)
    return Outline.Generic(
      Path().apply {
        moveTo(0F, size.height)
        cubicTo(
          leadingInset * 0.55F,
          size.height,
          leadingInset,
          size.height - leadingInset * 0.45F,
          leadingInset,
          size.height - leadingInset,
        )
        lineTo(leadingInset, topRadius)
        quadraticTo(leadingInset, 0F, leadingInset + topRadius, 0F)
        lineTo(size.width - flare - topRadius, 0F)
        quadraticTo(size.width - flare, 0F, size.width - flare, topRadius)
        lineTo(size.width - flare, size.height - flare)
        cubicTo(
          size.width - flare,
          size.height - flare * 0.45F,
          size.width - flare * 0.55F,
          size.height,
          size.width,
          size.height,
        )
        close()
      },
    )
  }
}

/**
 * 编辑器活动栏；首要能力在顶部，设置等全局能力固定在底部。
 *
 * [onCloseWorkbench] 会显示在全部底部面板之后，为紧凑抽屉与宽屏常驻活动栏提供一致的显式
 * 退出入口；调用方不提供回调时不会占用底部空间。
 */
@Composable
private fun EditorActivityBar(
  sidePanels: List<CodeEditorSidePanel>,
  selectedPanelId: String?,
  onSelect: (CodeEditorSidePanel) -> Unit,
  isRunning: Boolean,
  onRun: (() -> Unit)?,
  showBottomPanels: Boolean,
  onCloseWorkbench: (() -> Unit)?,
) {
  val primaryPanels = sidePanels.filter { it.group == CodeEditorSidePanelGroup.Primary }
  Column(
    modifier = Modifier
      .width(ActivityBarWidth)
      .fillMaxHeight()
      .background(EditorWorkbenchColors.ActivityBarBackground),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    primaryPanels.forEachIndexed { index, panel ->
      ActivityBarButton(
        panel = panel,
        selected = selectedPanelId == panel.id,
        connectsToTopEdge = index == 0,
        onClick = { onSelect(panel) },
      )
    }
    if (onRun != null) {
      ActivityBarCommandButton(
        icon = Icons.Default.PlayArrow,
        contentDescription = if (isRunning) "运行中" else "运行",
        enabled = !isRunning,
        onClick = onRun,
      )
    }
    Spacer(Modifier.weight(1F))
    if (showBottomPanels) {
      sidePanels.filter { it.group == CodeEditorSidePanelGroup.Bottom }.forEach { panel ->
        ActivityBarButton(
          panel = panel,
          selected = selectedPanelId == panel.id,
          connectsToTopEdge = false,
          onClick = { onSelect(panel) },
        )
      }
    }
    if (onCloseWorkbench != null) {
      ActivityBarCommandButton(
        icon = Icons.AutoMirrored.Filled.ExitToApp,
        contentDescription = "退出工作区",
        enabled = true,
        contentColor = EditorWorkbenchColors.SecondaryText,
        onClick = onCloseWorkbench,
      )
    }
  }
}

/**
 * 无支持面板的活动栏命令，供布局策略把运行、关闭工作区等操作移动到侧边。
 *
 * 使用与支持面板入口一致的整栏点击区域，避免窄活动栏内独立圆形涟漪产生范围和中心偏差。
 *
 * @param contentColor 可用状态下的图标颜色；禁用状态统一使用次要文本色。
 */
@Composable
private fun ActivityBarCommandButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  enabled: Boolean,
  contentColor: Color = EditorWorkbenchColors.Accent,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(ActivityBarItemHeight)
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (enabled) contentColor else EditorWorkbenchColors.SecondaryText,
      modifier = Modifier.size(22.dp),
    )
  }
}

/**
 * 单个活动栏按钮。
 *
 * 选中项与右侧面板使用同一背景色。普通位置通过左侧外圆角和右侧内圆弧形成连续的
 * S 形连接；当 [connectsToTopEdge] 为 true 时仍保留左上外圆角，但不绘制右上方连接弧，
 * 使首项右侧与面板顶边保持一条直线。
 */
@Composable
private fun ActivityBarButton(
  panel: CodeEditorSidePanel,
  selected: Boolean,
  connectsToTopEdge: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(ActivityBarItemHeight)
      .drawBehind {
        if (selected) {
          val radius = CodeEditorConnectedCornerRadius.toPx()
          val centerX = size.width - radius
          if (!connectsToTopEdge) {
            // 先补齐面板色方块，再擦回圆内的活动栏底色；保留下来的圆外区域才是内凹连接弧。
            clipRect(
              left = centerX,
              top = -radius,
              right = size.width,
              bottom = 0F,
            ) {
              drawRect(
                color = EditorWorkbenchColors.PanelBackground,
                topLeft = Offset(centerX, -radius),
                size = Size(radius, radius),
              )
              drawCircle(
                color = EditorWorkbenchColors.ActivityBarBackground,
                radius = radius,
                center = Offset(centerX, -radius),
              )
            }
          }
          // 下方镜像同一补集，使面板边界从选中块底边平滑回到默认竖直位置。
          clipRect(
            left = centerX,
            top = size.height,
            right = size.width,
            bottom = size.height + radius,
          ) {
            drawRect(
              color = EditorWorkbenchColors.PanelBackground,
              topLeft = Offset(centerX, size.height),
              size = Size(radius, radius),
            )
            drawCircle(
              color = EditorWorkbenchColors.ActivityBarBackground,
              radius = radius,
              center = Offset(centerX, size.height + radius),
            )
          }
        }
      }
      .background(
        color = if (selected) EditorWorkbenchColors.PanelBackground else Color.Transparent,
        shape = if (connectsToTopEdge) {
          ActivityBarTopConnectedItemShape
        } else {
          ActivityBarSelectedItemShape
        },
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = panel.icon,
      contentDescription = panel.title,
      tint = if (selected) EditorWorkbenchColors.AccentLight else EditorWorkbenchColors.SecondaryText,
      modifier = Modifier.size(22.dp),
    )
  }
}

/**
 * 宽屏常驻支持面板及其 IDEA 风格拖拽间距。
 *
 * [regionWidth] 包含左侧活动栏、支持面板与分隔槽；活动栏由上层 Row 绘制，因此这里只扣除活动栏后
 * 分配内容宽度。分隔槽保留完整命中间距，但不绘制分隔线或把手，避免侧栏与行号栏之间出现多余
 * 竖线；用户仍可在该间距内拖动调整宽度。
 */
@Composable
private fun ResizableExpandedSidePanel(
  panel: CodeEditorSidePanel,
  layoutMode: CodeEditorWorkbenchLayoutMode,
  connectsToTopEdge: Boolean,
  onClose: () -> Unit,
  regionWidth: Dp,
  minimumRegionWidth: Dp,
  maximumRegionWidth: Dp,
  state: CodeEditorWorkbenchState,
) {
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val currentRegionWidth = rememberUpdatedState(regionWidth)
  val panelWidth = regionWidth - ActivityBarWidth - PanelResizeGutterSize

  EditorSidePanelContent(
    panel = panel,
    layoutMode = layoutMode,
    connectsToTopEdge = connectsToTopEdge,
    onClose = onClose,
    modifier = Modifier.width(panelWidth),
  )
  Spacer(
    modifier = Modifier
      .width(PanelResizeGutterSize)
      .fillMaxHeight()
      .background(EditorWorkbenchColors.EditorBackground)
      .semantics { contentDescription = "调整侧边栏宽度" }
      .pointerInput(density, layoutDirection, minimumRegionWidth, maximumRegionWidth) {
        var dragRegionWidth = currentRegionWidth.value
        detectDragGestures(
          onDragStart = { dragRegionWidth = currentRegionWidth.value },
        ) { change, dragAmount ->
          change.consume()
          val directionDelta = if (layoutDirection == LayoutDirection.Ltr) {
            dragAmount.x
          } else {
            -dragAmount.x
          }
          dragRegionWidth = (dragRegionWidth + with(density) { directionDelta.toDp() })
            .coerceIn(minimumRegionWidth, maximumRegionWidth)
          state.updateSidePanelRegionWidth(dragRegionWidth.value)
        }
      },
  )
}

/**
 * 紧凑窗口使用有界模态抽屉承载活动栏和支持面板。
 *
 * 抽屉容器只占用标题栏以下的工作区，因此侧栏、遮罩和手势都不会覆盖固定标题栏。宽度优先保留
 * [CompactDrawerEndSpacing] 的编辑区，并限制在 [DefaultSidePanelRegionWidth] 以内；锚点直接使用最终
 * 抽屉宽度，避免仅缩窄内容后仍按整窗距离滑动的问题。[CodeEditorWorkbenchState] 保存与布局无关的
 * 业务可见状态，抽屉状态负责动画、拖动和遮罩点击。状态转换完成时会同步回业务状态，使手势关闭
 * 与按钮关闭保持一致。抽屉 offset 会在每一帧归一化后通过 [onProgressChanged] 上报，顶部图标可
 * 直接跟随拖动和回弹。
 * Navigation3 的返回事件在抽屉可见时由这里优先消费，抽屉关闭后才会继续弹出页面返回栈。
 */
@Composable
private fun CompactSidePanelDrawer(
  sidePanels: List<CodeEditorSidePanel>,
  selectedPanel: CodeEditorSidePanel?,
  layoutMode: CodeEditorWorkbenchLayoutMode,
  state: CodeEditorWorkbenchState,
  isRunning: Boolean,
  onRun: (() -> Unit)?,
  showBottomPanels: Boolean,
  onCloseWorkbench: (() -> Unit)?,
  onProgressChanged: (Float) -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
  NavigationBackHandler(
    state = backEventState,
    isBackEnabled = state.isSidePanelVisible && selectedPanel != null,
    onBackCompleted = state::closeSidePanel,
  )

  // 抽屉关闭时会用负 offset 向起始侧平移；必须裁剪到 safeDrawing 后的工作区边界，
  // 否则动画内容会越过父布局绘制到横屏摄像头缺口区域。
  BoxWithConstraints(modifier.clipToBounds()) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val drawerWidth = (maxWidth - CompactDrawerEndSpacing)
      .coerceAtMost(DefaultSidePanelRegionWidth)
      .coerceAtLeast(ActivityBarWidth)
    val drawerTravelDistancePx = with(density) { drawerWidth.toPx() }
    val anchors = remember(drawerTravelDistancePx) {
      DraggableAnchors {
        DrawerValue.Closed at -drawerTravelDistancePx
        DrawerValue.Open at 0F
      }
    }
    val drawerState = remember(drawerTravelDistancePx) {
      AnchoredDraggableState(
        initialValue = if (state.isSidePanelVisible && selectedPanel != null) {
          DrawerValue.Open
        } else {
          DrawerValue.Closed
        },
        anchors = anchors,
      )
    }

    // settledValue 只在动画或手势完全落到锚点后变化，避免拖到一半就提前改变业务可见状态。
    LaunchedEffect(drawerState, state) {
      snapshotFlow { drawerState.settledValue }
        .drop(1)
        .collect { value -> state.updateSidePanelVisibility(value == DrawerValue.Open) }
    }

    // 业务按钮修改可见状态后驱动动画；面板被移除时必须关闭，避免展示空抽屉。
    LaunchedEffect(drawerState, state.isSidePanelVisible, selectedPanel?.id) {
      drawerState.animateTo(
        if (state.isSidePanelVisible && selectedPanel != null) {
          DrawerValue.Open
        } else {
          DrawerValue.Closed
        },
      )
    }

    // offset 在关闭态为 -抽屉宽度、打开态为 0；直接观察它才能覆盖手势中间态。
    LaunchedEffect(drawerState, drawerTravelDistancePx) {
      snapshotFlow { drawerState.offset }
        .collect { offset ->
          if (!offset.isNaN() && drawerTravelDistancePx > 0F) {
            onProgressChanged(
              ((offset + drawerTravelDistancePx) / drawerTravelDistancePx).coerceIn(0F, 1F),
            )
          }
        }
    }

    val drawerOffset = drawerState.offset.takeUnless(Float::isNaN) ?: -drawerTravelDistancePx
    val drawerProgress = if (drawerTravelDistancePx > 0F) {
      ((drawerOffset + drawerTravelDistancePx) / drawerTravelDistancePx).coerceIn(0F, 1F)
    } else {
      0F
    }
    val visualOffset = if (layoutDirection == LayoutDirection.Ltr) drawerOffset else -drawerOffset
    val drawerNestedScrollConnection = remember(drawerState, layoutDirection) {
      CompactDrawerNestedScrollConnection(
        drawerState = drawerState,
        layoutDirection = layoutDirection,
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .nestedScroll(drawerNestedScrollConnection)
        .anchoredDraggable(
          state = drawerState,
          orientation = Orientation.Horizontal,
          enabled = selectedPanel != null,
        ),
    ) {
      content()
      if (drawerProgress > 0F) {
        // 遮罩只在抽屉实际露出时参与命中，关闭态不会拦截编辑器的触摸与鼠标事件。
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              EditorWorkbenchColors.DrawerScrim.copy(
                alpha = EditorWorkbenchColors.DrawerScrim.alpha * drawerProgress,
              ),
            )
            .pointerInput(state) { detectTapGestures { state.closeSidePanel() } },
        )
      }
      if (selectedPanel != null) {
        Surface(
          modifier = Modifier
            .align(Alignment.TopStart)
            .width(drawerWidth)
            .fillMaxHeight()
            .offset { IntOffset(visualOffset.roundToInt(), 0) },
          shape = RectangleShape,
          color = EditorWorkbenchColors.ActivityBarBackground,
          contentColor = EditorWorkbenchColors.PrimaryText,
          elevation = 20.dp,
        ) {
          // 外层 Surface 只负责位移和阴影；右侧内容面板独立绘制四角圆角。
          Row(Modifier.fillMaxSize()) {
            EditorActivityBar(
              sidePanels = sidePanels,
              selectedPanelId = selectedPanel.id,
              onSelect = { panel ->
                if (state.isSidePanelVisible && state.selectedSidePanelId == panel.id) {
                  state.closeSidePanel()
                } else {
                  state.selectSidePanel(panel.id)
                }
              },
              isRunning = isRunning,
              onRun = onRun,
              showBottomPanels = showBottomPanels,
              onCloseWorkbench = onCloseWorkbench,
            )
            EditorSidePanelContent(
              panel = selectedPanel,
              layoutMode = layoutMode,
              connectsToTopEdge = selectedPanel.id == sidePanels.firstOrNull {
                it.group == CodeEditorSidePanelGroup.Primary
              }?.id,
              onClose = state::closeSidePanel,
              modifier = Modifier.weight(1F),
            )
          }
        }
      }
    }
  }
}

/**
 * 在抽屉与内部横向滚动内容之间传递未消费的水平手势。
 *
 * 文件标签、文件树等内容仍优先消费可用位移；内容到达滚动边界后，剩余位移才用于打开或关闭
 * 抽屉。抽屉一旦离开完全打开或完全关闭的位置，后续反向位移会优先驱动抽屉，避免手势在
 * 回弹途中重新被子组件抢走。
 * 手指松开时将抽屉吸附到最近锚点，保证业务状态最终只停留在完全打开或完全关闭。
 */
private class CompactDrawerNestedScrollConnection(
  private val drawerState: AnchoredDraggableState<DrawerValue>,
  private val layoutDirection: LayoutDirection,
) : NestedScrollConnection {

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    // 停在任一锚点时先让标签栏等子组件滚动；仅接管其到达边界后通过 onPostScroll 传回的余量。
    if (source != NestedScrollSource.UserInput || drawerState.isAtAnchor()) return Offset.Zero
    return dispatchHorizontalDelta(available.x)
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource,
  ): Offset {
    if (source != NestedScrollSource.UserInput) return Offset.Zero
    return dispatchHorizontalDelta(available.x)
  }

  override suspend fun onPreFling(available: Velocity): Velocity {
    if (drawerState.isAtAnchor()) return Velocity.Zero
    drawerState.settle(spring())
    return Velocity(x = available.x, y = 0F)
  }

  /** 将屏幕坐标位移转换成抽屉逻辑方向，并准确返回实际消费量。 */
  private fun dispatchHorizontalDelta(physicalDelta: Float): Offset {
    if (physicalDelta == 0F) return Offset.Zero
    val logicalDelta = if (layoutDirection == LayoutDirection.Ltr) physicalDelta else -physicalDelta
    val logicalConsumed = drawerState.dispatchRawDelta(logicalDelta)
    val physicalConsumed = if (layoutDirection == LayoutDirection.Ltr) {
      logicalConsumed
    } else {
      -logicalConsumed
    }
    return Offset(x = physicalConsumed, y = 0F)
  }

  private fun AnchoredDraggableState<DrawerValue>.isAtAnchor(): Boolean {
    val currentOffset = offset
    if (currentOffset.isNaN()) return true
    return DrawerValue.entries.any { value -> currentOffset == anchors.positionOf(value) }
  }
}

/**
 * 承载支持面板的业务内容。
 *
 * 工作台不再额外绘制标题或关闭按钮，避免压缩文件树、搜索等面板的可用空间；需要自定义关闭
 * 入口的面板仍可通过 [CodeEditorSidePanelScope.closePanel] 主动关闭。当首个活动栏入口被选中时，
 * [connectsToTopEdge] 会取消面板左上圆角，使选中块与面板顶边连续。
 */
@Composable
private fun EditorSidePanelContent(
  panel: CodeEditorSidePanel,
  layoutMode: CodeEditorWorkbenchLayoutMode,
  connectsToTopEdge: Boolean,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxHeight(),
    shape = if (connectsToTopEdge) SidePanelTopConnectedShape else SidePanelShape,
    color = EditorWorkbenchColors.PanelBackground,
  ) {
    val scope = remember(layoutMode, onClose) {
      CodeEditorSidePanelScope(layoutMode = layoutMode, closePanelAction = onClose)
    }
    Box(Modifier.fillMaxSize()) {
      panel.content(scope)
    }
  }
}

/**
 * 可拖拽的深黑 Tool Window 内容。
 *
 * 面板圆角与编辑器其他连接轮廓共用同一半径；与首个底部按钮连接时取消左下圆角，使两者
 * 共用一条竖直边界。面板不重复展示标题和关闭按钮，调用方通过底部按钮切换或关闭面板；
 * 面板上方的无把手分隔槽负责调整高度。
 */
@Composable
private fun EditorToolWindowContent(
  toolWindow: CodeEditorToolWindow,
  height: Dp,
  editorGutterWidth: Dp,
  connectsToLeadingButton: Boolean,
  state: CodeEditorWorkbenchState,
) {
  Surface(
    // 左侧与底栏内容起点对齐；右侧直接延伸到边界，不再保留包边和圆角。
    modifier = Modifier
      .fillMaxWidth()
      .height(height)
      .padding(start = editorGutterWidth),
    color = EditorWorkbenchColors.ToolWindowBackground,
    shape = RoundedCornerShape(
      topStart = CodeEditorConnectedCornerRadius,
      topEnd = 0.dp,
      bottomEnd = 0.dp,
      // 首个按钮从面板左边直接向下延伸，此处必须为直角才能形成连续竖线。
      bottomStart = if (connectsToLeadingButton) 0.dp else CodeEditorConnectedCornerRadius,
    ),
    elevation = 0.dp,
  ) {
    Column(Modifier.fillMaxSize()) {
      val scope = remember(state) { CodeEditorToolWindowScope(state::closeToolWindow) }
      Box(Modifier.fillMaxSize()) {
        toolWindow.content(scope)
      }
    }
  }
}

/**
 * 右侧编辑器列的固定工具栏。
 *
 * 未选中按钮浮在淡黑外壳上；选中按钮使用深黑连接形状向上接入 Tool Window，顶部反圆角与文件
 * 标签的底部反圆角使用同一曲率。内容展开后按钮仍保留在最底部，不随面板向上移动。
 */
@Composable
private fun EditorToolWindowBar(
  toolWindows: List<CodeEditorToolWindow>,
  selectedToolWindowId: String?,
  onToggle: (String) -> Unit,
  isRunning: Boolean,
  onRun: (() -> Unit)?,
  onOpenSettings: (() -> Unit)?,
  editorGutterWidth: Dp,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(ToolWindowBarHeight)
      .background(EditorWorkbenchColors.ToolWindowBarBackground)
      // 起点使用编辑器实时 gutter 宽度，使行号位数或字体缩放变化后仍与代码区保持对齐。
      // 普通按钮上下、相邻按钮之间及工具栏右侧均保留 3.dp；选中按钮仅向上延伸以连接内容面板。
      .padding(start = editorGutterWidth, end = 3.dp, bottom = 3.dp),
    verticalAlignment = Alignment.Bottom,
  ) {
    if (onRun != null) {
      IconButton(enabled = !isRunning, onClick = onRun, modifier = Modifier.size(30.dp)) {
        Icon(
          Icons.Default.PlayArrow,
          contentDescription = if (isRunning) "运行中" else "运行",
          tint = if (isRunning) EditorWorkbenchColors.SecondaryText else EditorWorkbenchColors.Accent,
          modifier = Modifier.size(16.dp),
        )
      }
    }
    val selectedToolWindowIndex = toolWindows.indexOfFirst { it.id == selectedToolWindowId }
    val selectedConnectsToLeadingEdge = selectedToolWindowIndex == 0 && onRun == null
    toolWindows.forEachIndexed { index, toolWindow ->
      val selected = selectedToolWindowId == toolWindow.id
      val connectsToLeadingEdge = selected && selectedConnectsToLeadingEdge
      val buttonShape = when {
        connectsToLeadingEdge -> LeadingAttachedToolWindowButtonShape
        selected -> AttachedToolWindowButtonShape
        else -> RoundedCornerShape(CodeEditorConnectedCornerRadius)
      }
      // 连接曲线属于按钮外围肩部，必须额外计入测量，不能从文字与图标的主体宽度中扣除。
      val connectionStartPadding = if (selected && !connectsToLeadingEdge) {
        CodeEditorConnectedCornerRadius
      } else {
        0.dp
      }
      val connectionEndPadding = if (selected) CodeEditorConnectedCornerRadius else 0.dp
      // 相邻按钮进入选中轮廓的透明肩部，留下的 3.dp Spacer 才是用户实际看到的间距。
      val connectionOverlap = when {
        selectedToolWindowIndex < 0 || index < selectedToolWindowIndex -> 0.dp
        index == selectedToolWindowIndex && !selectedConnectsToLeadingEdge -> {
          CodeEditorConnectedCornerRadius
        }
        index > selectedToolWindowIndex && selectedConnectsToLeadingEdge -> {
          CodeEditorConnectedCornerRadius
        }
        index > selectedToolWindowIndex -> CodeEditorConnectedCornerRadius * 2
        else -> 0.dp
      }
      Row(
        modifier = Modifier
          .height(if (selected) ToolWindowAttachedButtonHeight else ToolWindowButtonHeight)
          .offset(x = -connectionOverlap)
          // Material TextButton 会引入额外最小触控尺寸，改用精确布局保证连接轮廓不被裁掉。
          .clip(buttonShape)
          .background(
            color = if (selected) EditorWorkbenchColors.SelectedToolButton else EditorWorkbenchColors.ToolButton,
          )
          .clickable { onToggle(toolWindow.id) }
          .padding(
            start = 10.dp + connectionStartPadding,
            end = 10.dp + connectionEndPadding,
          ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        toolWindow.icon?.let { icon ->
          Icon(
            icon,
            contentDescription = null,
            tint = if (selected) EditorWorkbenchColors.Accent else EditorWorkbenchColors.SecondaryText,
            modifier = Modifier.size(14.dp),
          )
          Spacer(Modifier.width(3.dp))
        }
        Text(
          text = toolWindow.title.substringBefore(" · "),
          color = if (selected) EditorWorkbenchColors.Accent else EditorWorkbenchColors.SecondaryText,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
        )
      }
      Spacer(Modifier.width(3.dp))
    }
    Spacer(Modifier.weight(1F))
    if (onOpenSettings != null) {
      IconButton(onClick = onOpenSettings, modifier = Modifier.size(30.dp)) {
        Icon(
          Icons.Default.Settings,
          contentDescription = "打开设置",
          tint = EditorWorkbenchColors.SecondaryText,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

/**
 * 向上连接工具内容的底部按钮轮廓。
 *
 * 顶部两侧使用反圆角从完整的深黑工具内容收进按钮主体，底部使用普通外圆角；这与文件标签
 * “上方外圆角、下方反圆角”的方向相反，但曲率完全一致。
 */
private object AttachedToolWindowButtonShape : Shape by attachedToolWindowButtonShape(
  connectsToLeadingEdge = false,
)

/** 首个工具按钮与面板左边对齐时，左上直接连接面板，左下仍以外圆角收到底边。 */
private object LeadingAttachedToolWindowButtonShape : Shape by attachedToolWindowButtonShape(
  connectsToLeadingEdge = true,
)

/** 根据按钮是否贴住面板左边生成连接轮廓，确保两种状态复用完全相同的圆角参数。 */
private fun attachedToolWindowButtonShape(connectsToLeadingEdge: Boolean): Shape = object : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val radius = with(density) { CodeEditorConnectedCornerRadius.toPx() }
      .coerceAtMost(size.width / 4F)
      .coerceAtMost(size.height / 2F)
    val controlOffset = radius * ConnectedCornerBezierControl
    return Outline.Generic(
      Path().apply {
        moveTo(0F, 0F)
        if (connectsToLeadingEdge) {
          // 左上与面板共用竖直边界，只有抵达工具栏底部时才绘制普通外圆角。
          lineTo(0F, size.height - radius)
          cubicTo(
            0F,
            size.height - radius + controlOffset,
            radius - controlOffset,
            size.height,
            radius,
            size.height,
          )
        } else {
          cubicTo(
            controlOffset,
            0F,
            radius,
            radius - controlOffset,
            radius,
            radius,
          )
          lineTo(radius, size.height - radius)
          cubicTo(
            radius,
            size.height - radius + controlOffset,
            radius * 2F - controlOffset,
            size.height,
            radius * 2F,
            size.height,
          )
        }
        lineTo(size.width - radius * 2F, size.height)
        cubicTo(
          size.width - radius * 2F + controlOffset,
          size.height,
          size.width - radius,
          size.height - radius + controlOffset,
          size.width - radius,
          size.height - radius,
        )
        lineTo(size.width - radius, radius)
        cubicTo(
          size.width - radius,
          radius - controlOffset,
          size.width - controlOffset,
          0F,
          size.width,
          0F,
        )
        close()
      },
    )
  }
}

/** 工作台暗色主题色板；后续可以整体替换为编辑器主题模型。 */
internal object EditorWorkbenchColors {
  /** 代码及其外围工具栏使用的深黑底色。 */
  val DeepBackground = Color(0xFF0F131B)

  /** 状态栏与显示缺口沿用应用深黑底色；抽屉越界由布局裁剪负责。 */
  val SystemInsetBackground = DeepBackground

  /** 文件目录、支持面板以及代码区外围包边使用的淡黑底色。 */
  val LightBackground = Color(0xFF1C2330)

  val EditorBackground = DeepBackground
  val ToolbarBackground = DeepBackground
  val ActionBackground = LightBackground
  val DocumentBarBackground = DeepBackground
  val ActiveDocumentBackground = LightBackground
  val FileIndicator = Color(0xFFF3C969)
  // 抽屉活动栏使用深黑、右侧目录面板使用淡黑，选中项复用面板色以维持连续的连接造型。
  val ActivityBarBackground = DeepBackground
  val PanelBackground = LightBackground
  val ToolWindowBackground = DeepBackground
  val ToolWindowBarBackground = LightBackground
  val ToolButton = Color.Transparent
  val SelectedToolButton = DeepBackground
  val PrimaryText = Color(0xFFF4F6FB)
  val SecondaryText = Color(0xFFAAB4C8)
  val MutedText = Color(0xFF8C98AF)
  val Divider = Color(0xFF303A50)
  val Accent = Color(0xFF6F5CFF)
  val AccentLight = Color(0xFF8E7CFF)
  val DrawerScrim = Color(0xB3080A0F)
}

private val TopBarHeight = 54.dp
private val SidePanelShape = RoundedCornerShape(CodeEditorConnectedCornerRadius)
private val SidePanelTopConnectedShape = RoundedCornerShape(
  topStart = 0.dp,
  topEnd = CodeEditorConnectedCornerRadius,
  bottomEnd = CodeEditorConnectedCornerRadius,
  bottomStart = CodeEditorConnectedCornerRadius,
)
private val ActivityBarSelectedItemShape = RoundedCornerShape(
  topStart = CodeEditorConnectedCornerRadius,
  bottomStart = CodeEditorConnectedCornerRadius,
)
private val ActivityBarTopConnectedItemShape = RoundedCornerShape(
  topStart = CodeEditorConnectedCornerRadius,
  bottomStart = CodeEditorConnectedCornerRadius,
)
private val ActivityBarWidth = 52.dp
private val ActivityBarItemHeight = 48.dp
// 自动布局的侧栏总宽度包含活动栏；宽屏用户仍可通过分隔槽覆盖这一默认值。
private val DefaultSidePanelRegionWidth = CodeEditorWorkbenchState.DefaultSidePanelRegionWidthDp.dp
private val CompactDrawerEndSpacing = 56.dp
private val PanelResizeGutterSize = 8.dp
private val MinimumExpandedSidePanelRegionWidth = 280.dp
private val MaximumExpandedSidePanelRegionWidth = 560.dp
private val MinimumExpandedEditorWidth = 360.dp
// 标签栏与标签本体保持同高，避免额外居中或顶部 padding 造成宽屏侧栏与标签错位。
private val DocumentTabBarHeight = 30.dp
private val DocumentTabSpacing = 3.dp
private val DocumentBreadcrumbHeight = 28.dp
private val ToolWindowBarHeight = 36.dp
private val ToolWindowButtonHeight = 30.dp
private val ToolWindowAttachedButtonHeight = 33.dp
/** 未传入编辑器实时 gutter 宽度时使用的兼容默认值。 */
private val DefaultEditorGutterWidth = 30.dp
private val CodeAreaOuterShape = RoundedCornerShape(
  topStart = 0.dp,
  topEnd = 0.dp,
  bottomEnd = 0.dp,
  bottomStart = CodeEditorConnectedCornerRadius,
)
private val MinimumToolWindowHeight = 120.dp
private const val MaximumToolWindowHeightFraction = 0.72F
private const val ConnectedCornerBezierControl = 0.5522848F
