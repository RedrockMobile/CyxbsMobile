package com.cyxbs.pages.schedule.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * todo 模块内部复用的底部弹窗包装。
 *
 * 复刻老端 [com.google.android.material.bottomsheet.BottomSheetDialog] 的底部弹出语义：
 * - [show] 为 true 时组合 [BottomSheetCompose] 并自动展开。
 * - 用户下滑到底 / 点击 scrim / 按返回键均会触发 [onDismiss]，外层把 [show] 置 false
 *   即可从组合中移除。
 *
 * @param onDismissRequest 关闭请求拦截：点击 scrim / 按返回键触发关闭前回调，返回 true 放行（执行
 *   收起动画并最终回调 [onDismiss]），返回 false 则拦截本次关闭（弹窗保持展开，由调用方自行处理，
 *   如弹出「未保存确认」）。默认 null 表示不拦截，任何关闭请求都直接放行。
 * @param content 弹窗内容。内部自动包裹一层主题背景的圆角 Column 并挂 [bottomSheetDraggable]。
 */
@Composable
internal fun ScheduleBottomSheet(
  show: Boolean,
  onDismiss: () -> Unit,
  scrimColor: Color? = null,
  onDismissRequest: (() -> Boolean)? = null,
  content: @Composable () -> Unit,
) {
  if (!show) return
  val colors = LocalAppColors.current
  // 深色主题的 onSurface 接近白色，作为蒙层会把弹窗上方页面洗灰；默认统一使用黑色蒙层。
  val resolvedScrimColor = scrimColor ?: Color.Black.copy(alpha = 0.4F)
  // 用 rememberUpdatedState 保证 BottomSheetState 内捕获的始终是最新一帧的拦截回调（避免闭包过期）。
  val dismissGate = rememberUpdatedState(onDismissRequest)
  val state = remember {
    BottomSheetState(onDismissRequest = {
      val gate = dismissGate.value
      if (gate == null || gate()) hideSuspend()
    })
  }

  // 等测量完成后展开（BottomSheetCompose 通过 onSizeChanged 设置 showMaxHeight）。
  LaunchedEffect(Unit) {
    delay(100)
    state.expandAsync()
  }

  // hide 动画结束后通知外层移除组合。
  LaunchedEffect(state) {
    state.stateFlow.first { it == BottomSheetValueState.Hide }
    onDismiss()
  }

  BottomSheetCompose(
    bottomSheetState = state,
    peekHeight = 0.dp,
    scrimColor = resolvedScrimColor,
    dismissOnBackPress = true,
    dismissOnClickOutside = true,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .then(bottomSheetDraggable())
        .background(
          color = colors.topBg,
          shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        )
        .navigationBarsPadding(),
    ) {
      content()
    }
  }
}
