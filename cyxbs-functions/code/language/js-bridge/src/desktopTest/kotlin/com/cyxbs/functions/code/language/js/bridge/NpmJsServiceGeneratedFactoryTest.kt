package com.cyxbs.functions.code.language.js.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证生成工厂以 KClass 而非类名字符串关联业务接口。 */
class NpmJsServiceGeneratedFactoryTest {

  @Test
  fun generatedFactoryCarriesServiceClassIdentity() {
    val factory = _DynamicLanguageServiceNpmJsFactory()

    assertEquals(DynamicLanguageService::class, factory.serviceClass)
    assertEquals(
      "com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService",
      factory.serviceId,
    )
  }
}
