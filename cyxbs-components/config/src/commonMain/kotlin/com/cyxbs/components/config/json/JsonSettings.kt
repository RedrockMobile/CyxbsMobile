package com.cyxbs.components.config.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**  
 * description: Json本地化存储工具
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/9/27 13:56
 */
expect class JsonSettings internal constructor(
  name: String
) {

  /**
   * 把键值对通过json保存下来
   */
  suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>): Boolean

  /**
   * 获取对应键的值
   */
  suspend fun <T> getOrNull(key: String, serializer: KSerializer<T>): T?

  /**
   * 移除对应的键
   */
  suspend fun remove(key: String): Boolean

  /**
   * 清理
   */
  suspend fun clear(): Boolean

}

/**
 * 这里采用inline + reified的形式封装了一层，避免了泛型擦除
 * 实现了无须传入serializer解析器
 */
suspend inline fun <reified T> JsonSettings.put(
  key: String,
  value: T
): Boolean {
  return put(key, value, serializer())
}

suspend inline fun <reified T> JsonSettings.getOrNull(
  key: String
): T? {
  return getOrNull(key, serializer())
}

suspend inline fun <reified T> JsonSettings.get(
  key: String,
  defaultValue: T
): T {
  return getOrNull(key, serializer()) ?: defaultValue
}