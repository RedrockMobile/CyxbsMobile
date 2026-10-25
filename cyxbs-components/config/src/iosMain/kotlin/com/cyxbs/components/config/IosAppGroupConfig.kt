package com.cyxbs.components.config

/**
 * 获取构建时从主 App entitlement 读取的共享容器标识符。
 *
 * 仅表示主 App 的配置；与 Widget Extension 的一致性由 widget 模块构建时校验。
 */
fun getIosMainAppGroupId(): String = IosMainBuildConfig.APP_GROUP_ID
