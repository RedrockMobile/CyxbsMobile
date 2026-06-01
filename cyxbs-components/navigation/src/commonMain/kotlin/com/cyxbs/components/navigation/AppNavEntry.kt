package com.cyxbs.components.navigation

import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SceneStrategy
import com.cyxbs.components.navigation.utils.UrlDecoder
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

/**
 * 主导航页面
 *
 * 建议先看该文章学习 Navigation3 的特性：
 * https://juejin.cn/post/7568873939033260082?searchId=2026051823320440955048A6F1243E556E
 * - 理解基本概念，学会如何压栈、弹栈
 * - 理解如何在宽屏下使用 [ListDetailSceneStrategy] 显示多个 NavEntry 页面
 *
 * ## 使用方式
 * ```kotlin
 * @Serializable                // 路由参数必须使用 `@Serializable` 标注
 * data class HomeNavArgument(  // 类名需以 NavArgument 结尾
 *   val page: String = "discover"
 * ): AppNavArgument            // 需实现 AppNavArgument 接口
 *
 * @AppNav(route = NAV_HOME)    // 使用该注解，route 统一放至 NavigationTable.kt
 * object HomeNavEntry : AppNavEntry<HomeNavArgument>()
 *
 * // 跳转：
 * HomeNavArgument().navigate() // 统一使用该方法压栈，方便子类进行重写
 *
 * // 从返回栈中弹出：
 * argument.popBackStack()      // 统一使用该方法弹栈，方便子类进行重写
 * ```
 *
 * 支持 dialog 作为 NavEntry，但需要配置 [DialogSceneStrategy]
 * ```
 * @AppNav(route = NAV_DIALOG_NOTICE)      // route 统一为 NAV_DIALOG_ 开头
 * class NoticeDialogNavEntry : AppNavEntry<NoticeNavArgument>() {
 *   override fun buildMetadata(argument: NoticeNavArgument): Map<String, Any> {
 *     return DialogSceneStrategy.dialog() // 重写 buildMetadata，返回 DialogSceneStrategy.dialog()
 *   }
 * }
 * ```
 *
 * ## deeplink 支持
 * 会根据 route 自动生成对应的 deeplink
 * - 默认以 cyxbs:// 开头，比如：cyxbs://home?page=discover
 * - 当 route 含 `/` 时（如 `dialog/update`），首段作为 URI authority，其余作为 path segment，最终拼为 cyxbs://dialog/update
 * - 第一层字段采取 key=value 进行解析，后续层字段采取 json 进行解析，解析规则详细可看 [UrlDecoder]
 *
 * ```
 * // deeplink 跳转优先使用 AppScheme，支持 http、cyxbs 协议跳转
 *
 * // 跳转至统一的 webView
 * AppScheme.jump("https://...")
 *
 * // 跳转至对应页面
 * AppScheme.jump("cyxbs://...")   // cyxbs 协议可看 UrlDecoder 了解编码规则
 * ```
 *
 * 💡编译打包时会输出对应的 deeplink 规则，最后会汇总到 :cyxbs-applications:pro 或者 test 模块下
 */
abstract class AppNavEntry<T : AppNavArgument> {

  /**
   * 页面是否需要登录，如果是的话，则在未登录时将先跳转登录页
   */
  abstract fun isNeedLogin(argument: T): Boolean

  /**
   * 唯一且稳定的 ID [NavEntry.contentKey]
   *
   * 需要准守如下规则：
   * - 应在多个页面中唯一，等价于 ListAdapter 比较的唯一 id
   * - 如果是 Home、Login 这类设计成单例的页面，应该返回固定的值，不与 AppNavArgument 中的参数关联
   * - 一般情况下都不为单例页面，直接返回 argument.toString() 即可
   * - 支持栈中存在多个相同 contentKey 的页面，但代价是页面状态被复用，包括 remember、ViewModel 等
   * - 多次调用应返回相同值
   */
  open fun getContentKey(argument: T): String {
    return argument.toString()
  }

  /**
   * 页面显示的附加信息 [NavEntry.metadata]
   *
   * 有以下作用：
   * - 提供给 sceneStrategies 实现宽屏多页面分屏展示 [ListDetailSceneStrategy]
   *    - 比如 [ListDetailSceneStrategy.listPane] 作为列表页，[ListDetailSceneStrategy.detailPane] 作为详细页
   * - dialog entry 配置
   *    - 返回 [DialogSceneStrategy.dialog]
   */
  open fun buildMetadata(argument: T): Map<String, Any> {
    return emptyMap()
  }

  /**
   * 自定义注入的Strategy
   */
  open fun getSceneStrategy(): SceneStrategy<Any>? {
    return null
  }

  /**
   * 页面的具体内容
   *
   * 登录态由外层统一处理，方法内部无需再做登录检查。
   */
  @Composable
  abstract fun Content(argument: T)
}



/**
 * 主导航页面注册注解
 *
 * 在 [AppNavEntry] 的实现类上标注该注解，KSP 会在编译期为其生成对应的 [AppNavCollector] 实现，
 * 并通过 `::class.allImpl()` 的服务发现机制让 [AppNavDisplay] 在启动时收集所有已注册页面。
 *
 * @param route 该页面在主导航中的路由标识，用于 deeplink 与跨模块跳转，需要在工程内全局唯一。
 *   命名规则（由 KSP 编译期强校验）：
 *   - 不能为空，不能以 `/` 开头或结尾，不能包含连续 `/`
 *   - 每个 segment 仅允许字符 `[a-zA-Z0-9_-]`
 *   - 含 `/` 时按首段作 URI authority、其余作 URI path segment 拆解，例如
 *     `dialog/update` -> `cyxbs://dialog/update`
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class AppNav(val route: String)

/**
 * [AppNavEntry] 的收集器
 *
 * 由 KSP 根据 [AppNav] 注解自动生成实现类，是 KSP 生成代码与运行时之间的桥梁。
 *
 * [AppNavDisplay] 在启动时通过 `AppNavCollector::class.allImpl()` 拿到所有实现，并据此：
 * - 注册 [argumentSerializer] 到 SerializersModule，实现栈状态的多态序列化
 * - 通过 [argumentClazz] 将路由参数与 [navEntry] 绑定到 entryProvider
 *
 * 业务侧无需也不应该手动实现该接口。
 *
 * @param T 路由参数类型，与 [AppNavEntry] 的泛型保持一致
 */
interface AppNavCollector<T : AppNavArgument> {

  /** 由 KSP 实例化并持有的 [AppNavEntry] */
  val navEntry: AppNavEntry<T>

  /** 路由参数的运行时类型，用于在 entryProvider 中按类型分发页面 */
  val argumentClazz: KClass<T>

  /** 路由参数的 [KSerializer]，由 KSP 通过 `T.serializer()` 获取，用于多态序列化注册 */
  val argumentSerializer: KSerializer<T>
}