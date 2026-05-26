package com.cyxbs.components.navigation

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.toMutableStateList
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * 供全局使用的应用栈
 *
 * 💡跳转和弹栈优先使用 [AppNavArgument.navigate] 和 [AppNavArgument.popBackStack]
 */
lateinit var appNavBackStack: AppNavBackStack
  internal set


/**
 * 主导航栈
 *
 * 参考官方 NavBackStack 实现，但改为不可修改的 List，如果外界需要修改则添加对应方法，方便后续排查问题
 */
@Serializable(with = AppNavBackStackSerializer::class)
class AppNavBackStack(internal val base: SnapshotStateList<AppNavArgument>) :
  List<AppNavArgument> by base, StateObject by base, RandomAccess by base {

  constructor(elements: List<AppNavArgument>) : this(base = elements.toMutableStateList())

  /*
  * 如果外界需要修改集合，一般情况下可使用 AppNavArgument.navigate() 和 popBackStack() 进行代替
  * */

  // 使用 AppNavArgument.navigate() 代替，以方便子类重载
  internal fun push(argument: AppNavArgument): Boolean {
    if (lastOrNull() == argument) {
      return false // 正常来说不应该多次添加完全相等的页面
    }
    base.add(argument)
    return true
  }

  // 使用 appNavBackStack[index].popBackStack() 代替，以方便子类重载
  internal fun removeAt(index: Int) {
    base.removeAt(index)
  }

  // 仅特殊场景下使用，如进入登录页提供清空所有栈的选择
  fun clear() {
    base.clear()
  }

  override fun toString(): String {
    return Snapshot.withoutReadObservation { base.toString() }
  }

}

class AppNavBackStackSerializer : KSerializer<AppNavBackStack> {

  private val delegate = SnapshotStateListSerializer(PolymorphicSerializer(AppNavArgument::class))

  @OptIn(ExperimentalSerializationApi::class)
  override val descriptor: SerialDescriptor =
    SerialDescriptor("com.cyxbs.components.navigation.AppNavBackStack", delegate.descriptor)

  override fun serialize(encoder: Encoder, value: AppNavBackStack) {
    encoder.encodeSerializableValue(delegate, value.base)
  }

  override fun deserialize(decoder: Decoder): AppNavBackStack {
    return AppNavBackStack(base = decoder.decodeSerializableValue(delegate))
  }
}
