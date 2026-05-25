package com.cyxbs.components.navigation

import com.cyxbs.components.navigation.utils.decodeFromUrl
import com.cyxbs.components.navigation.utils.encodeToUrl
import com.eygraber.uri.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * .
 *
 * @author 985892345
 * @date 2026/5/25
 */
/**
 * AppNavEntry 对应参数的 NavArgument 接口
 *
 * 如果子类在其他模块使用时报错父接口依赖问题，请给对应模块加上 useNavigation()
 */
interface AppNavArgument {

  // 快捷跳转，子类可自定义
  // 优先使用该方式而非操控 appNavBackStack
  fun navigate() {
    appNavBackStack.push(this)
  }

  // 快捷弹出栈，子类可自定义
  // 优先使用该方式而非操控 appNavBackStack
  fun popBackStack() {
    val index = appNavBackStack.indexOfLast { it == this } // 从末尾开始查找
    if (index >= 0) {
      appNavBackStack.removeAt(index)
    }
  }

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
      return runCatching { uri.decodeFromUrl(serializer, NavJson) }.getOrNull()
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
  return serializer.encodeToUrl(this, NavJson)
    .scheme(AppScheme.SCHEME)
    .authority(segments.first()) // 第一段为 authority
    .apply {
      // 其余端为 path
      segments.drop(1).forEach { appendPath(it) }
    }
    .build()
}


// 因为依赖关系，不能反向依赖 config 模块，所以 copy一份
private val NavJson = Json {
  explicitNulls = true // 显示编码 null 值，否则编码与解码将发生歧义，详细看注释
  encodeDefaults = false // 不需要编码默认值
  ignoreUnknownKeys = true // 忽略未知键
  isLenient = true // 宽松模式，允许键和字符串值不带引号
  allowTrailingComma = true // 允许尾随逗号
}