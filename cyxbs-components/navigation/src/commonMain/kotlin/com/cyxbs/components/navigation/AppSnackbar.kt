package com.cyxbs.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private val AppSnackbarExitDurationMillis = 180.milliseconds
private val AppSnackbarBackgroundColor = Color(0xFF34495E)

/** 应用级 Snackbar 的默认视觉参数。 */
object AppSnackbarDefaults {
  val actionColor = Color(0xFF27D3A2)
}

private data class AppSnackbarData(
  val id: Long,
  val durationMillis: Long,
  val bottomPadding: Dp,
  val messageContent: @Composable () -> Unit,
  val actionTextContent: @Composable () -> Unit,
  val actionIconContent: @Composable () -> Unit,
  val onClick: () -> Unit,
  val onTimeout: () -> Unit,
)

private var appSnackbarSequence = 0L
private var appSnackbarVisible by mutableStateOf(false)
private var appSnackbarData by mutableStateOf<AppSnackbarData?>(null)

/**
 * 使用普通文本和 [actionIconPainter] 展示应用级 Snackbar 的便捷重载。
 *
 * 需要动画或特殊排版时应改用 Composable 插槽重载，由业务自行实现图标状态。
 */
fun showAppSnackbar(
  message: String,
  actionLabel: String,
  actionIconPainter: Painter,
  durationMillis: Long,
  bottomPadding: Dp = 16.dp,
  onClick: () -> Unit,
  onTimeout: () -> Unit,
) {
  showAppSnackbar(
    durationMillis = durationMillis,
    bottomPadding = bottomPadding,
    messageContent = {
      Text(text = message, color = Color.White, fontSize = 14.sp)
    },
    actionTextContent = {
      Text(text = actionLabel, color = AppSnackbarDefaults.actionColor, fontSize = 14.sp)
    },
    actionIconContent = {
      Icon(
        painter = actionIconPainter,
        contentDescription = actionLabel,
        tint = AppSnackbarDefaults.actionColor,
        modifier = Modifier.size(15.dp),
      )
    },
    onClick = onClick,
    onTimeout = onTimeout,
  )
}

/**
 * 在应用最顶层展示一条带操作按钮的 Snackbar，正文、操作文字与操作图标均由调用方组合。
 *
 * 新请求会替换当前请求并重新开始倒计时；[onClick] 与 [onTimeout] 只会有一个被调用。
 * [bottomPadding] 用于避让业务页面自己的底部悬浮层，系统导航栏由宿主自动处理。
 */
fun showAppSnackbar(
  durationMillis: Long,
  bottomPadding: Dp = 16.dp,
  messageContent: @Composable () -> Unit,
  actionTextContent: @Composable () -> Unit,
  actionIconContent: @Composable () -> Unit,
  onClick: () -> Unit,
  onTimeout: () -> Unit,
) {
  require(durationMillis > 0L) { "Snackbar duration must be positive." }
  if (appSnackbarVisible) {
    // 新消息替换旧消息时视为旧消息超时，确保旧请求不会悬空。
    appSnackbarData?.onTimeout?.invoke()
  }
  appSnackbarData = AppSnackbarData(
    id = ++appSnackbarSequence,
    durationMillis = durationMillis,
    bottomPadding = bottomPadding,
    messageContent = messageContent,
    actionTextContent = actionTextContent,
    actionIconContent = actionIconContent,
    onClick = onClick,
    onTimeout = onTimeout,
  )
  appSnackbarVisible = true
}

/** 使用普通文本和 [actionIconPainter] 绘制 Snackbar 内容的便捷重载。 */
@Composable
fun AppSnackbarContent(
  message: String,
  actionLabel: String,
  actionIconPainter: Painter,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AppSnackbarContent(
    messageContent = {
      Text(text = message, color = Color.White, fontSize = 14.sp)
    },
    actionTextContent = {
      Text(text = actionLabel, color = AppSnackbarDefaults.actionColor, fontSize = 14.sp)
    },
    actionIconContent = {
      Icon(
        painter = actionIconPainter,
        contentDescription = actionLabel,
        tint = AppSnackbarDefaults.actionColor,
        modifier = Modifier.size(15.dp),
      )
    },
    onClick = onClick,
    modifier = modifier,
  )
}

/**
 * Snackbar 的通用内容布局。
 *
 * 三个插槽分别对应正文、操作文字与操作图标；组件只处理排列和点击区域，不约束图标动画。
 */
@Composable
fun AppSnackbarContent(
  messageContent: @Composable () -> Unit,
  actionTextContent: @Composable () -> Unit,
  actionIconContent: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = AppSnackbarBackgroundColor,
    contentColor = Color.White,
    shape = RoundedCornerShape(10.dp),
    modifier = modifier.fillMaxWidth().height(52.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      messageContent()
      Spacer(modifier = Modifier.weight(1f))
      Row(
        modifier = Modifier
          .height(52.dp)
          .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        actionTextContent()
        Spacer(modifier = Modifier.width(7.dp))
        actionIconContent()
      }
    }
  }
}

/**
 * 应用级 Snackbar 宿主。
 *
 * 必须像 PlatformToastCompose 一样挂在 AppNavDisplay 之后，使它脱离页面、Pager 和课表的局部层级。
 */
@Composable
fun AppSnackbarCompose() {
  val data = appSnackbarData
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.BottomCenter,
  ) {
    AnimatedVisibility(
      visible = appSnackbarVisible && data != null,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .navigationBarsPadding()
        .padding(bottom = data?.bottomPadding ?: 0.dp),
      enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
      exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
    ) {
      if (data != null) {
        // 每次请求创建独立组合生命周期，业务图标可安全使用 remember/LaunchedEffect 实现自己的动画。
        key(data.id) {
          AppSnackbarContent(
            messageContent = data.messageContent,
            actionTextContent = data.actionTextContent,
            actionIconContent = data.actionIconContent,
            onClick = {
              if (appSnackbarVisible && appSnackbarData?.id == data.id) {
                appSnackbarVisible = false
                data.onClick()
              }
            },
          )
        }
      }
    }
  }

  LaunchedEffect(data?.id, appSnackbarVisible) {
    if (data == null) return@LaunchedEffect
    if (appSnackbarVisible) {
      delay(data.durationMillis.milliseconds)
      if (appSnackbarVisible && appSnackbarData?.id == data.id) {
        data.onTimeout()
        appSnackbarVisible = false
      }
    } else {
      // 保留内容直到退出动画结束，避免 Surface 在 fadeOut/slideOut 前瞬间消失。
      delay(AppSnackbarExitDurationMillis)
      if (!appSnackbarVisible && appSnackbarData?.id == data.id) {
        appSnackbarData = null
      }
    }
  }
}
