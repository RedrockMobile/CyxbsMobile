package com.cyxbs.components.config.service

import com.g985892345.provider.api.init.wrapper.ImplProviderWrapper
import com.g985892345.provider.manager.KtProvider
import kotlin.reflect.KClass

/**
 * 服务提供收口处
 *
 * 以下文档分为：
 * - 1、SPI 类型服务提供
 * - 2、Fragment 注册使用
 * - 3、Activity 注册使用
 *
 * # 1、SPI 类型服务提供
 * 比如 InitialService、IAccountService 等
 * ```
 * // api 模块：
 * interface IAccountService {}
 *
 * // 实现类：
 * @ImplProvider
 * object AccountService : IAccountService {}
 *
 * // 使用方式：
 * val accountService = IAccountService::class.impl()
 * ```
 * ## 注意事项
 * - 实现类推荐使用 object 单例，若使用 class 则每次 impl() 都会生成一个新的实例
 * - 实现类是 IAccountService 的默认实现时，@ImplProvider 可不提供 clazz 和 name 参数（但要求只能有一个父类或接口）
 *
 *
 *
 * # 2、Fragment 注册使用
 * ```
 * @ImplProvider(clazz = Fragment::class, name = NAV_FRAGMENT_页面)
 * class MyFragment : BaseFragment() {}
 *
 * // 使用时：
 * val fragment = Fragment::class.impl(NAV_FRAGMENT_页面)
 * ```
 * ## 注意事项
 * - @ImplProvider clazz 参数统一写成 Fragment::class
 * - @ImplProvider name  参数统一放在 config 模块 /navigation/NavigationTable.kt 中定义
 *
 *
 *
 * # 3、Activity 注册使用
 * ```
 * @KClassProvider(clazz = Activity::class, name = NAV_页面)
 * class MyActivity : BaseActivity() {}
 *
 * // 使用时：
 * startActivity(NAV_页面) {
 *   // 虽然这里可以拿到 Intent 传递参数，但是更推荐使用 api 模块提供方法来约束参数传递
 * }
 *
 * // 直接拿到 KClass<MyActivity>
 * val clazz = MyActivity::class.implClass(NAV_页面)
 * ```
 * ## 注意事项
 * - @KClassProvider clazz 参数统一写成 Activity::class
 * - @KClassProvider name  参数统一放在 config 模块 /navigation/NavigationTable.kt 中定义
 *
 */


/**
 * ```
 * // 得到实现类
 * @ImplProvider
 * object AccountService : IAccountService
 *
 * val isLogin = IAccountService::class.impl().isLogin()
 *
 * // 得到 Fragment
 * @ImplProvider(clazz = Fragment::class, name = NAV_FRAGMENT_页面)
 * class MyFragment : BaseFragment()
 *
 * val fragment = Fragment::class::impl(NAV_FRAGMENT_页面)
 * ```
 */
fun <T : Any> KClass<T>.impl(name: String = ""): T {
  return KtProvider.impl(this, name)
}

fun <T : Any> KClass<T>.implOrNull(name: String = ""): T? {
  return KtProvider.implOrNull(this, name)
}

/**
 * ```
 * // 得到所有实现类
 * val allImpl = IAccountService::class.allImpl().map { it.get() }
 * ```
 */
fun <T : Any> KClass<out T>.allImpl(): Map<String, ImplProviderWrapper<T>> {
  return KtProvider.allImpl(this)
}

/**
 * 得到实现类 KClass，一般用于 Activity 中
 * ```
 * @KClassProvider(clazz = Activity::class, name = NAV_页面)
 * class MyActivity : BaseActivity() {}
 *
 * // 跳转便捷写法
 * startActivity(NAV_页面)
 *
 * // 获得 KClass<MyActivity>
 * val clazz = Activity::class.implClass(NAV_页面)
 * ```
 */
fun <T : Any> KClass<T>.implClass(name: String): KClass<out T> {
  return KtProvider.clazz(this, name)
}

