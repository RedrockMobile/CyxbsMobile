package com.cyxbs.components.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.toMutableStateList
import androidx.savedstate.compose.serialization.serializers.SnapshotStateListSerializer
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.navigation.utils.decodeFromUrl
import com.cyxbs.components.navigation.utils.encodeToUrl
import com.eygraber.uri.Uri
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * AppNavEntry 对应参数的 NavArgument 接口
 */
interface AppNavArgument {

  companion object {
    /**
     * 通过 url 解码为 AppNavArgument 对象
     */
    fun decodeFromUrl(url: String): AppNavArgument? {
      if (!url.startsWith("${AppScheme.SCHEME}://")) return null
      val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
      // @AppNav(route = "dialog/update") 会生成 cyxbs://dialog/update，host="dialog"、path="/update"，拼接后即为 route key。
      val route = (uri.authority.orEmpty() + uri.path.orEmpty()).trim('/')
      val collector = appNavCollector[route] ?: return null
      @Suppress("UNCHECKED_CAST")
      val serializer = collector.argumentSerializer as KSerializer<AppNavArgument>
      return runCatching { uri.decodeFromUrl(serializer, defaultJson) }.getOrNull()
    }

    /**
     * 通过 route 转换为 AppNavArgument 对象
     *
     * route 统一放在 NavigationTable 中，如 [NAV_LOGIN]、[NAV_HOME]
     */
    fun decodeFromRoute(route: String): AppNavArgument? {
      return decodeFromUrl("${AppScheme.SCHEME}://$route")
    }
  }
}

/**
 * 编码成 Url
 *
 * 含 `/` 的 route 按首段作 authority、其余作 path segment 拆解，
 * 保证 toString 形如 `cyxbs://dialog/update?k=v`。
 */
fun AppNavArgument.encodeToUrl(): Uri {
  val argumentClass = this::class
  val entry = appNavCollector.firstNotNullOfOrNull {
    if (it.value.argumentClazz == argumentClass) it else null
  }
  check(entry != null) { "请检查该类是否有被 AppNavEntry 使用？同时添加了 @AppNav 注解？${argumentClass}" }
  @Suppress("UNCHECKED_CAST")
  val serializer = entry.value.argumentSerializer as KSerializer<AppNavArgument>
  val segments = entry.key.split('/')
  return serializer.encodeToUrl(this, defaultJson)
    .scheme(AppScheme.SCHEME)
    .authority(segments.first()) // 第一段为 authority
    .apply {
      // 其余端为 path
      segments.drop(1).forEach { appendPath(it) }
    }
    .build()
}



/**
 * 主导航栈
 *
 * 参考 androidx.navigation3.runtime.NavBackStack 实现
 */
@Serializable(with = AppNavBackStackSerializer::class)
class AppNavBackStack(internal val base: SnapshotStateList<AppNavArgument>) :
  MutableList<AppNavArgument> by base, StateObject by base, RandomAccess by base {

  constructor(elements: List<AppNavArgument>) : this(base = elements.toMutableStateList())

  fun push(argument: AppNavArgument): Boolean {
    if (lastOrNull() == argument) {
      return false
    }
    add(argument)
    return true
  }

}

class AppNavBackStackSerializer : KSerializer<AppNavBackStack> {

  private val delegate = SnapshotStateListSerializer(PolymorphicSerializer(AppNavArgument::class))

  @OptIn(ExperimentalSerializationApi::class)
  override val descriptor: SerialDescriptor =
    SerialDescriptor("com.cyxbs.components.init.AppNavBackStack", delegate.descriptor)

  override fun serialize(encoder: Encoder, value: AppNavBackStack) {
    encoder.encodeSerializableValue(delegate, value.base)
  }

  override fun deserialize(decoder: Decoder): AppNavBackStack {
    return AppNavBackStack(base = decoder.decodeSerializableValue(delegate))
  }
}