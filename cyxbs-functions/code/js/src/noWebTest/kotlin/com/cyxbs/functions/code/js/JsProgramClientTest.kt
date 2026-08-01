package com.cyxbs.functions.code.js

import com.cyxbs.functions.code.js.bundle.JsAsyncFunctionCapability
import com.cyxbs.functions.code.js.bundle.JsRuntimeBundle
import com.cyxbs.functions.code.js.bundle.JsSyncFunctionCapability
import com.cyxbs.functions.code.js.runtime.QuickJsRuntime
import com.cyxbs.functions.code.js.storage.InMemoryJsProgramStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * JavaScript 业务加载、场景隔离与字节码缓存测试。
 */
class JsProgramClientTest {

  /**
   * 验证首次从源码编译，后续执行命中本地字节码缓存。
   */
  @Test
  fun compileSourceThenUseBytecodeCache() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    val environment = JsExecutionEnvironment.forTeaching()
    val sourcePackage = sourcePackage(
      packageId = "teaching.basic",
      code = "40 + 2",
    )

    val first = client.installAndExecute<Int>(
      sourcePackage = sourcePackage,
      environment = environment,
    )
    val second = client.execute<Int>(
      reference = sourcePackage.reference,
      environment = environment,
    )
    client.clearBytecodeCache()
    val afterClear = client.execute<Int>(
      reference = sourcePackage.reference,
      environment = environment,
    )

    assertEquals(42, first.value)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, first.origin)
    assertEquals(42, second.value)
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, second.origin)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, afterClear.origin)
  }

  /**
   * 验证 Bundle 版本变化会生成新缓存键，避免执行旧宿主环境编译的字节码。
   */
  @Test
  fun invalidateBytecodeWhenBundleChanges() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    val sourcePackage = sourcePackage(
      packageId = "teaching.bundle-version",
      code = "21 * 2",
    )
    val firstEnvironment = JsExecutionEnvironment.forTeaching(
      bundle = JsRuntimeBundle(
        id = "teaching-standard",
        version = 1,
        hostApiVersion = 1,
      ),
    )
    val upgradedEnvironment = JsExecutionEnvironment.forTeaching(
      bundle = JsRuntimeBundle(
        id = "teaching-standard",
        version = 2,
        hostApiVersion = 1,
      ),
    )

    val first = client.installAndExecute<Int>(sourcePackage, firstEnvironment)
    val afterUpgrade = client.execute<Int>(sourcePackage.reference, upgradedEnvironment)

    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, first.origin)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, afterUpgrade.origin)
    assertEquals(42, afterUpgrade.value)
  }

  /**
   * 验证多个内部脚本可复用相同的不可变能力 Bundle，但执行结果不会共享 JS 全局状态。
   */
  @Test
  fun reuseInternalCapabilityBundle() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    val sharedBundle = JsRuntimeBundle(
      id = "internal-common",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsSyncFunctionCapability(
          id = "internal.answer",
          functionName = "internalAnswer",
        ) {
          42
        },
      ),
    )
    var verificationCount = 0
    val environment = JsExecutionEnvironment.forInternal(
      bundle = sharedBundle,
      sourceVerifier = {
        verificationCount++
      },
    )
    val firstPackage = sourcePackage(
      packageId = "internal.first",
      code = "globalThis.localState = 1; internalAnswer()",
      requiredCapabilities = setOf("internal.answer"),
    )
    val secondPackage = sourcePackage(
      packageId = "internal.second",
      code = "typeof globalThis.localState === 'undefined' ? internalAnswer() + 1 : -1",
      requiredCapabilities = setOf("internal.answer"),
    )

    val first = client.installAndExecute<Int>(firstPackage, environment)
    val second = client.installAndExecute<Int>(secondPackage, environment)

    assertEquals(42, first.value)
    assertEquals(43, second.value)
    assertEquals(4, verificationCount)
  }

  /**
   * 验证教学场景拒绝 Bundle 中未显式加入白名单的宿主能力。
   */
  @Test
  fun rejectForbiddenTeachingCapability() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    val networkBundle = JsRuntimeBundle(
      id = "teaching-with-network",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsAsyncFunctionCapability(
          id = "network.request",
          functionName = "request",
        ) {
          "not-called"
        },
      ),
    )
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = networkBundle,
      policy = JsExecutionPolicy.teaching(),
    )

    assertFailsWith<JsPolicyViolationException> {
      client.install(
        sourcePackage = sourcePackage(
          packageId = "teaching.forbidden-network",
          code = "1",
        ),
        environment = environment,
      )
    }
  }

  /**
   * 验证源码包、业务模块和共享 Bundle 模块可以共同组成 ES Module 执行图。
   *
   * Client 的持久化层目前仍以单个程序缓存键保存一个字节码，因此这里同时验证尚未接入
   * 按 Module 缓存的业务流程会保持源码直执行。
   */
  @Test
  fun executeModulesFromPackageAndBundle() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    var captured: Int? = null
    val bundle = JsRuntimeBundle(
      id = "teaching-module",
      version = 1,
      hostApiVersion = 1,
      modules = mapOf(
        "shared" to "export const base = 40;",
      ),
      capabilities = listOf(
        JsSyncFunctionCapability(
          id = "teaching.capture",
          functionName = "capture",
        ) { args ->
          captured = (args.single() as Number).toInt()
          null
        },
      ),
    )
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = bundle,
      policy = JsExecutionPolicy.teaching(
        allowedCapabilityIds = setOf("teaching.capture"),
      ),
    )
    val sourcePackage = JsSourcePackage.create(
      packageId = "teaching.modules",
      version = "1",
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = mapOf(
        "main.js" to """
          import { base } from "shared";
          import { increment } from "increment";
          capture(increment(base) + 1);
        """.trimIndent(),
        "increment" to "export const increment = value => value + 1;",
      ),
      requiredCapabilities = setOf("teaching.capture"),
    )

    val result = client.installAndExecute<Any?>(
      sourcePackage = sourcePackage,
      environment = environment,
    )

    assertEquals(42, captured)
    assertEquals(JsExecutableOrigin.SOURCE_DIRECT, result.origin)
  }

  /**
   * 验证源码 Map 的插入顺序不会改变内容哈希。
   */
  @Test
  fun calculateStableHashAcrossFileOrder() {
    val first = JsSourcePackage.create(
      packageId = "teaching.hash",
      version = "1",
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = linkedMapOf(
        "main.js" to "import { value } from 'value'; value",
        "value" to "export const value = 42;",
      ),
    )
    val second = JsSourcePackage.create(
      packageId = "teaching.hash",
      version = "1",
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = linkedMapOf(
        "value" to "export const value = 42;",
        "main.js" to "import { value } from 'value'; value",
      ),
    )

    assertEquals(first.contentHash, second.contentHash)
    assertTrue(first.contentHash.matches(Regex("[0-9a-f]{64}")))
  }

  /**
   * 创建单文件测试源码包。
   */
  private fun sourcePackage(
    packageId: String,
    code: String,
    requiredCapabilities: Set<String> = emptySet(),
  ): JsSourcePackage {
    return JsSourcePackage.create(
      packageId = packageId,
      version = "1",
      files = mapOf(QuickJsRuntime.DEFAULT_FILENAME to code),
      requiredCapabilities = requiredCapabilities,
    )
  }
}
