package com.cyxbs.components.view.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForInspector
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cyxbs.components.view.R
import com.google.android.material.internal.EdgeToEdgeUtils
import java.util.UUID

/**
 * 窗口组件，类似于 Dialog，但全屏展示
 *
 * - 安卓基于自定义 Dialog 实现，Compose 自带的 Dialog 组件无法完全铺满屏幕
 *
 * @author 985892345
 * @date 2025/3/24
 */
@Composable
actual fun Window(
  dismissOnBackPress: (() -> Unit)?,
  content: @Composable () -> Unit,
) {
  val view = LocalView.current
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val composition = rememberCompositionContext()
  val currentContent by rememberUpdatedState(content)
  val dialogId = rememberSaveable { UUID.randomUUID() }
  val dialog = remember(view, density) {
    WindowDialog(
      dismissOnBackPress,
      view,
      layoutDirection,
      dialogId
    ).apply {
      setContent(composition) {
        // TODO(b/159900354): draw a scrim and add margins around the Compose Dialog, and
        //  consume clicks so they can't pass through to the underlying UI
        DialogLayout(
          Modifier.semantics { dialog() },
        ) {
          currentContent()
        }
      }
    }
  }

  DisposableEffect(dialog) {
    dialog.show()

    onDispose {
      dialog.dismiss()
      dialog.disposeComposition()
    }
  }

  SideEffect {
    dialog.updateParameters(
      dismissOnBackPress = dismissOnBackPress,
      layoutDirection = layoutDirection,
    )
  }
}

private class WindowDialog(
  private var dismissOnBackPress: (() -> Unit)?,
  private val composeView: View,
  layoutDirection: LayoutDirection,
  dialogId: UUID
) : ComponentDialog(
  composeView.context,
  R.style.viewFullScreenDialog,
), ViewRootForInspector {
  private val dialogLayout: DialogLayout

  override val subCompositionView: AbstractComposeView get() = dialogLayout

  private val onBackPressedCallback = onBackPressedDispatcher.addCallback(this) {
    dismissOnBackPress?.invoke()
  }

  init {
    val window = window ?: error("Dialog has no window")
    window.requestFeature(Window.FEATURE_NO_TITLE)
    window.setBackgroundDrawableResource(android.R.color.transparent)
    setCanceledOnTouchOutside(false)
    dialogLayout = DialogLayout(context, window).apply {
      // Set unique id for AbstractComposeView. This allows state restoration for the state
      // defined inside the Dialog via rememberSaveable()
      setTag(androidx.compose.ui.R.id.compose_view_saveable_id_tag, "Dialog:$dialogId")
    }

    setContentView(dialogLayout)
    dialogLayout.setViewTreeLifecycleOwner(composeView.findViewTreeLifecycleOwner())
    dialogLayout.setViewTreeViewModelStoreOwner(composeView.findViewTreeViewModelStoreOwner())
    dialogLayout.setViewTreeSavedStateRegistryOwner(
      composeView.findViewTreeSavedStateRegistryOwner()
    )

    // Initial setup
    updateParameters(dismissOnBackPress, layoutDirection)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val window = window!!
    WindowCompat.setDecorFitsSystemWindows(window, false) // 沉浸式状态栏
    window.statusBarColor = Color.TRANSPARENT
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  }

  private fun setLayoutDirection(layoutDirection: LayoutDirection) {
    dialogLayout.layoutDirection = when (layoutDirection) {
      LayoutDirection.Ltr -> android.util.LayoutDirection.LTR
      LayoutDirection.Rtl -> android.util.LayoutDirection.RTL
    }
  }

  fun setContent(parentComposition: CompositionContext, children: @Composable () -> Unit) {
    dialogLayout.setContent(parentComposition, children)
  }

  fun disposeComposition() {
    dialogLayout.disposeComposition()
  }

  fun updateParameters(
    dismissOnBackPress: (() -> Unit)?,
    layoutDirection: LayoutDirection
  ) {
    this.dismissOnBackPress = dismissOnBackPress
    onBackPressedCallback.isEnabled = dismissOnBackPress != null
    setLayoutDirection(layoutDirection)
  }

  override fun cancel() {
    // Prevents the dialog from dismissing itself
    return
  }
}

@SuppressLint("ViewConstructor")
private class DialogLayout(
  context: Context,
  override val window: Window
) : AbstractComposeView(context), DialogWindowProvider {

  private var content: @Composable () -> Unit by mutableStateOf({})

  override var shouldCreateCompositionOnAttachedToWindow: Boolean = false
    private set

  fun setContent(parent: CompositionContext, content: @Composable () -> Unit) {
    setParentCompositionContext(parent)
    this.content = content
    shouldCreateCompositionOnAttachedToWindow = true
    createComposition()
  }

  @Composable
  override fun Content() {
    content()
  }
}

@Composable
private fun DialogLayout(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  Layout(
    content = content,
    modifier = modifier
  ) { measurables, constraints ->
    val placeables = measurables.fastMap { it.measure(constraints) }
    val width = placeables.fastMaxBy { it.width }?.width ?: constraints.minWidth
    val height = placeables.fastMaxBy { it.height }?.height ?: constraints.minHeight
    layout(width, height) {
      placeables.fastForEach { it.placeRelative(0, 0) }
    }
  }
}