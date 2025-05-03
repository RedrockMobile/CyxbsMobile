package com.cyxbs.components.utils.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlin.reflect.KProperty

/**
 * .
 *
 * @author 985892345
 * 2024/4/10 19:03
 */

// 一个简单的包装类，用于 compose 中保存数据
@Stable
data class Wrapper<T>(var value: T)
@Stable
data class IntWrapper(var value: Int)
@Stable
data class LongWrapper(var value: Long)
@Stable
data class FloatWrapper(var value: Float)
@Stable
data class DoubleWrapper(var value: Double)
@Stable
data class BooleanWrapper(var value: Boolean)

@Composable
fun <T> rememberWrapper(value: T): Wrapper<T> = remember { Wrapper(value) }
@Composable
fun <T> rememberUpdatedWrapper(value: T): Wrapper<T> = remember { Wrapper(value) }.also { it.value = value }
operator fun <T> Wrapper<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

operator fun <T> Wrapper<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
  this.value = value
}

@Composable
fun rememberIntWrapper(value: Int): IntWrapper = remember { IntWrapper(value) }
@Composable
fun rememberUpdatedIntWrapper(value: Int): IntWrapper = remember { IntWrapper(value) }.also { it.value = value }
operator fun IntWrapper.getValue(thisRef: Any?, property: KProperty<*>): Int = value
operator fun IntWrapper.setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
  this.value = value
}

@Composable
fun rememberLongWrapper(value: Long): LongWrapper = remember { LongWrapper(value) }
@Composable
fun rememberUpdatedLongWrapper(value: Long): LongWrapper = remember { LongWrapper(value) }.also { it.value = value }
operator fun LongWrapper.getValue(thisRef: Any?, property: KProperty<*>): Long = value
operator fun LongWrapper.setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
  this.value = value
}

@Composable
fun rememberFloatWrapper(value: Float): FloatWrapper = remember { FloatWrapper(value) }
@Composable
fun rememberUpdatedFloatWrapper(value: Float): FloatWrapper = remember { FloatWrapper(value) }.also { it.value = value }
operator fun FloatWrapper.getValue(thisRef: Any?, property: KProperty<*>): Float = value
operator fun FloatWrapper.setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
  this.value = value
}

@Composable
fun rememberDoubleWrapper(value: Double): DoubleWrapper = remember { DoubleWrapper(value) }
@Composable
fun rememberUpdatedDoubleWrapper(value: Double): DoubleWrapper = remember { DoubleWrapper(value) }.also { it.value = value }
operator fun DoubleWrapper.getValue(thisRef: Any?, property: KProperty<*>): Double = value
operator fun DoubleWrapper.setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
  this.value = value
}

@Composable
fun rememberBooleanWrapper(value: Boolean): BooleanWrapper = remember { BooleanWrapper(value) }
@Composable
fun rememberUpdatedBooleanWrapper(value: Boolean): BooleanWrapper = remember { BooleanWrapper(value) }.also { it.value = value }
operator fun BooleanWrapper.getValue(thisRef: Any?, property: KProperty<*>): Boolean = value
operator fun BooleanWrapper.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
  this.value = value
}
