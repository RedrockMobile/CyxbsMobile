package com.cyxbs.components.utils.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import kotlinx.coroutines.delay


actual fun toast(s: CharSequence?) {
  if (s != null) {
    PlatformToast(s, 2000L).show()
  }
}

actual fun toastLong(s: CharSequence?) {
  if (s != null) {
    PlatformToast(s, 3500L).show()
  }
}

private class PlatformToast(
  val msg: CharSequence,
  val duration: Long,
) {
  fun show() {
    if (AppToastState === Empty) {
      AppToastState = this
      AppToastVisible = true
    } else {
      if (AppToastState === this) return
      AppToastList.addFirst(this)
    }
  }

  companion object {
    val Empty = PlatformToast("", 0L)
  }
}

private val AppToastList = ArrayDeque<PlatformToast>()

private var AppToastVisible by mutableStateOf(false)

private var AppToastState: PlatformToast by mutableStateOf(PlatformToast.Empty)

@Composable
fun PlatformToastCompose() {
  AnimatedVisibility(
    modifier = Modifier.padding(top = 140.dp),
    visible = AppToastVisible,
    enter = fadeIn() + scaleIn(initialScale = 0.5F),
    exit = fadeOut() + scaleOut(targetScale = 0.5F),
  ) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Text(
        modifier = Modifier.background(
          color = LocalAppColors.current.tvLv4,
          shape = RoundedCornerShape(18.dp),
        ).padding(horizontal = 24.dp, vertical = 12.dp),
        text = AppToastState.msg.toString(),
        color = if (LocalAppDark.current) Color.Black else Color.White,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
      )
    }
    DisposableEffect(Unit) {
      onDispose {
        val last = AppToastList.removeLastOrNull()
        if (last == null) {
          AppToastState = PlatformToast.Empty
        } else {
          AppToastState = last
          AppToastVisible = true
        }
      }
    }
  }
  LaunchedEffect(AppToastVisible) {
    if (AppToastVisible) {
      delay(AppToastState.duration)
      AppToastVisible = false
    }
  }
}