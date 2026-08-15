package com.cyxbs.functions.code.editor.workbench

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 通用编辑工作台的自适应策略与状态测试。 */
class CodeEditorWorkbenchModelTest {

  /** 竖屏手机应使用覆盖侧栏，高频命令留在顶部，设置随活动栏进入抽屉。 */
  @Test
  fun portraitWindowUsesCompactPortraitLayout() {
    val layout = DefaultCodeEditorWorkbenchLayoutPolicy.resolve(
      maxWidth = 412.dp,
      maxHeight = 915.dp,
    )

    assertEquals(CodeEditorWorkbenchLayoutMode.CompactPortrait, layout.mode)
    assertTrue(layout.usesOverlaySidePanel)
    assertEquals(CodeEditorCommandPlacement.TopBar, layout.runPlacement)
    assertEquals(CodeEditorCommandPlacement.ActivityBar, layout.settingsPlacement)
  }

  /** 横屏手机宽度不足展开阈值时仍使用覆盖侧栏，但保留独立模式供后续调整按钮位置。 */
  @Test
  fun landscapePhoneUsesCompactLandscapeLayout() {
    val layout = DefaultCodeEditorWorkbenchLayoutPolicy.resolve(
      maxWidth = 780.dp,
      maxHeight = 412.dp,
    )

    assertEquals(CodeEditorWorkbenchLayoutMode.CompactLandscape, layout.mode)
    assertTrue(layout.usesOverlaySidePanel)
    assertEquals(CodeEditorCommandPlacement.ActivityBar, layout.settingsPlacement)
  }

  /** 平板、桌面和足够宽的横屏窗口应常驻活动栏与支持面板。 */
  @Test
  fun wideWindowUsesExpandedLayout() {
    val layout = DefaultCodeEditorWorkbenchLayoutPolicy.resolve(
      maxWidth = 1024.dp,
      maxHeight = 768.dp,
    )

    assertEquals(CodeEditorWorkbenchLayoutMode.Expanded, layout.mode)
    assertFalse(layout.usesOverlaySidePanel)
  }

  /** 侧栏状态不绑定窗口模式，容器切换时应持续保留面板选择与可见性。 */
  @Test
  fun sidePanelStateIsIndependentFromWindowMode() {
    val state = CodeEditorWorkbenchState(
      initialSidePanelId = "files",
      initialToolWindowId = null,
    )

    state.selectSidePanel("search")
    assertEquals("search", state.selectedSidePanelId)
    assertTrue(state.isSidePanelVisible)

    // 工作台只替换抽屉与常驻面板容器，不再通知状态对象当前窗口模式。
    assertEquals("search", state.selectedSidePanelId)
    assertTrue(state.isSidePanelVisible)

    state.closeSidePanel()
    assertEquals("search", state.selectedSidePanelId)
    assertFalse(state.isSidePanelVisible)
  }

  /** 底部工具窗口互斥展开，再次点击当前按钮时收起。 */
  @Test
  fun toolWindowButtonsToggleSingleSelection() {
    val state = CodeEditorWorkbenchState(
      initialSidePanelId = null,
      initialToolWindowId = null,
    )

    state.toggleToolWindow("run")
    assertEquals("run", state.selectedToolWindowId)
    state.toggleToolWindow("performance")
    assertEquals("performance", state.selectedToolWindowId)
    state.toggleToolWindow("performance")
    assertEquals(null, state.selectedToolWindowId)
  }
}
