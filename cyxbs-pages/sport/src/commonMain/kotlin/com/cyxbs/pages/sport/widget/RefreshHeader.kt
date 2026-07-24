package com.cyxbs.pages.sport.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cyxbsmobile.cyxbs_pages.sport.generated.resources.Res
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_refresh
import org.jetbrains.compose.resources.painterResource

@Composable
fun RefreshHeader(
    state: RefreshState,
    modifier: Modifier = Modifier,
) {
    val spinningRotation = remember { Animatable(0f) }

    // 下拉时由手势控制的当前角度
    val dragRotation = state.progress * 360f

    LaunchedEffect(state.isRefreshing) {
        if (!state.isRefreshing) return@LaunchedEffect

        // 从松手前实际显示的角度开始
        spinningRotation.snapTo(dragRotation)

        while (true) {
            spinningRotation.animateTo(
                targetValue = spinningRotation.value + 360f,
                animationSpec = tween(
                    durationMillis = 1500,
                    easing = LinearEasing,
                ),
            )
        }
    }

    Image(
        painter = painterResource(Res.drawable.sport_ic_refresh),
        contentDescription = null,
        modifier = modifier
            .padding(top = 20.dp)
            .size(20.dp)
            .graphicsLayer {
                rotationZ = if (state.isRefreshing) {
                    spinningRotation.value
                } else {
                    dragRotation
                }
            },
    )
}