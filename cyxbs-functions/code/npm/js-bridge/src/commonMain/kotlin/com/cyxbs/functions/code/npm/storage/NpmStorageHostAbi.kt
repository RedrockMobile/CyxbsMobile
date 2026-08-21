package com.cyxbs.functions.code.npm.storage

/**
 * npm Storage 在 JavaScript Runtime 与宿主之间使用的稳定 ABI。
 *
 * 该对象位于 commonMain，使 npm 包和非 Web 宿主共享同一组协议名称。业务代码不应直接调用
 * [INVOKE] 对应的全局函数，而应使用 jsMain 提供的公开 Storage API。
 */
object NpmStorageHostAbi {
  /** 接收单个 JSON 请求并异步返回 JSON 响应的宿主函数名。 */
  const val INVOKE: String = "__cyxbs_npm_storage_invoke"

  const val SCOPE_PACKAGE: String = "package"
  const val SCOPE_GLOBAL: String = "global"

  const val SETTINGS_KEYS: String = "settings.keys"
  const val SETTINGS_CONTAINS: String = "settings.contains"
  const val SETTINGS_GET_STRING: String = "settings.getString"
  const val SETTINGS_GET_BOOLEAN: String = "settings.getBoolean"
  const val SETTINGS_GET_INT: String = "settings.getInt"
  const val SETTINGS_GET_LONG: String = "settings.getLong"
  const val SETTINGS_GET_FLOAT: String = "settings.getFloat"
  const val SETTINGS_GET_DOUBLE: String = "settings.getDouble"
  const val SETTINGS_PUT_STRING: String = "settings.putString"
  const val SETTINGS_PUT_BOOLEAN: String = "settings.putBoolean"
  const val SETTINGS_PUT_INT: String = "settings.putInt"
  const val SETTINGS_PUT_LONG: String = "settings.putLong"
  const val SETTINGS_PUT_FLOAT: String = "settings.putFloat"
  const val SETTINGS_PUT_DOUBLE: String = "settings.putDouble"
  const val SETTINGS_REMOVE: String = "settings.remove"
  const val SETTINGS_CLEAR: String = "settings.clear"

  const val FILES_EXISTS: String = "files.exists"
  const val FILES_METADATA: String = "files.metadata"
  const val FILES_LIST: String = "files.list"
  const val FILES_READ_TEXT: String = "files.readText"
  const val FILES_WRITE_TEXT: String = "files.writeText"
  const val FILES_READ_BYTES: String = "files.readBytes"
  const val FILES_WRITE_BYTES: String = "files.writeBytes"
  const val FILES_DELETE: String = "files.delete"
  const val FILES_CLEAR: String = "files.clear"
}
