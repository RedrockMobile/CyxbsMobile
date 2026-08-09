package com.cyxbs.functions.code.npm.service

import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import kotlin.reflect.KClass

/**
 * 仅供 `cyxbs-compiler:ksp-npm-js-service` 生成代码使用的端上代理工厂协议。
 *
 * 工厂不保存 Runtime 状态，只负责把本次加载创建的 [NpmJsServiceSession] 通过构造参数交给生成
 * 代理。普通业务不应实现或直接获取该接口，应使用 [com.cyxbs.functions.code.npm.NpmJsServiceLoader.load]。
 */
interface NpmJsServiceProxyFactory<out T : NpmJsServiceInstance> {
  val serviceClass: KClass<out T>
  val serviceId: String
  val schemaHash: String

  /** 使用已初始化的 Session 创建业务接口代理。 */
  fun create(session: NpmJsServiceSession): T
}
