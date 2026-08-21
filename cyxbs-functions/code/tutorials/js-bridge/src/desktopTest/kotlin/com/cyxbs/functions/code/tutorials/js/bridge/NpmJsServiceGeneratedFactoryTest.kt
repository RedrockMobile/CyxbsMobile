package com.cyxbs.functions.code.tutorials.js.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证教程协议已由 KSP 注册到通用 npm Service Loader。 */
class NpmJsServiceGeneratedFactoryTest {
  @Test
  fun generatedFactoryCarriesServiceIdentity() {
    val factory = _DynamicTutorialServiceNpmJsFactory()

    assertEquals(DynamicTutorialService::class, factory.serviceClass)
    assertEquals(
      "com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService",
      factory.serviceId,
    )
  }
}
