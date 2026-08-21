package com.cyxbs.functions.code.tutorials

/** 教程 Catalog 或教程包返回了端上无法安全解释的协议数据。 */
class DynamicTutorialProtocolException(
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
