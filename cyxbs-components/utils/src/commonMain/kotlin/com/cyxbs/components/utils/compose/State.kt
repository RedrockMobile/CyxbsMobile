package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.StateFactoryMarker
import androidx.compose.runtime.structuralEqualityPolicy

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/20 13:11
 */

@StateFactoryMarker
fun <T> derivedStateOfStructure(
  calculation: () -> T,
): State<T> = derivedStateOf(structuralEqualityPolicy(), calculation)

@Composable
inline fun <T> rememberDerivedStateOfStructure(
  key: Any? = null,
  crossinline calculation: () -> T,
): State<T> = remember(key) {
  derivedStateOf(structuralEqualityPolicy()) { calculation.invoke() }
}

@Composable
inline fun <T> rememberDerivedStateOfStructure(
  key1: Any? = null,
  key2: Any? = null,
  crossinline calculation: () -> T,
): State<T> = remember(key1, key2) {
  derivedStateOf(structuralEqualityPolicy()) { calculation.invoke() }
}

@Composable
inline fun <T> rememberDerivedStateOfStructure(
  key1: Any? = null,
  key2: Any? = null,
  key3: Any? = null,
  crossinline calculation: () -> T,
): State<T> = remember(key1, key2, key3) {
  derivedStateOf(structuralEqualityPolicy()) { calculation.invoke() }
}