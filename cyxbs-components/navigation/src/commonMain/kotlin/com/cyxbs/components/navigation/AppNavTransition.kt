package com.cyxbs.components.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.scene.Scene

/**
 * 进入时的动画
 */
internal expect fun <T : Any> appNavTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform

/**
 * 返回时的动画
 */
internal expect fun <T : Any> appNavPopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform

/**
 * 预测性返回的动画
 */
internal expect fun <T : Any> appNavPredictivePopTransitionSpec():
    AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform
