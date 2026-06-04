package com.cyxbs.components.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec

internal actual fun <T : Any> appNavTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = defaultTransitionSpec()

internal actual fun <T : Any> appNavPopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = defaultPopTransitionSpec()

internal actual fun <T : Any> appNavPredictivePopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = defaultPredictivePopTransitionSpec()
