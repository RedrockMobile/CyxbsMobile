package com.cyxbs.components.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation3.scene.Scene

private const val TRANSITION_DURATION = 700

internal actual fun <T : Any> appNavTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    ContentTransform(
        fadeIn(animationSpec = tween(TRANSITION_DURATION)),
        fadeOut(animationSpec = tween(TRANSITION_DURATION)),
    )
}

internal actual fun <T : Any> appNavPopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    ContentTransform(
        fadeIn(animationSpec = tween(TRANSITION_DURATION)),
        fadeOut(animationSpec = tween(TRANSITION_DURATION)),
    )
}

internal actual fun <T : Any> appNavPredictivePopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = {
    ContentTransform(
        fadeIn(animationSpec = tween(TRANSITION_DURATION)),
        fadeOut(animationSpec = tween(TRANSITION_DURATION)),
    )
}
