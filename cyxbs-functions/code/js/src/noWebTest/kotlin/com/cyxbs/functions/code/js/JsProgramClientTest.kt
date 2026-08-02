package com.cyxbs.functions.code.js

import com.cyxbs.functions.code.js.bundle.JsAsyncFunctionCapability
import com.cyxbs.functions.code.js.bundle.JsRuntimeBundle
import com.cyxbs.functions.code.js.bundle.JsSyncFunctionCapability
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleLevel
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleMessage
import com.cyxbs.functions.code.js.bundle.teaching.JsTeachingConsoleCapability
import com.cyxbs.functions.code.js.bundle.teaching.JsTeachingInputCapability
import com.cyxbs.functions.code.js.program.JsExecutionEnvironment
import com.cyxbs.functions.code.js.program.JsExecutionPolicy
import com.cyxbs.functions.code.js.program.JsPolicyViolationException
import com.cyxbs.functions.code.js.program.JsProgramClient
import com.cyxbs.functions.code.js.program.JsProgramMode
import com.cyxbs.functions.code.js.program.JsSourcePackage
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.storage.InMemoryJsProgramStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** JavaScript Program 的源码、场景隔离与公共 Runtime 编排测试。 */
class JsProgramClientTest {

  /** 验证多个程序复用 Bundle 时，每次执行仍拥有隔离的全局状态。 */
  @Test
  fun reuseInternalCapabilityBundleWithIsolatedRuntime() = runTest {
    val client = createClient()
    val bundle = JsRuntimeBundle(
      id = "internal-common",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsSyncFunctionCapability("internal.answer", "internalAnswer") { 42 },
      ),
    )
    var verificationCount = 0
    val environment = JsExecutionEnvironment.forInternal(
      bundle = bundle,
      sourceVerifier = { verificationCount++ },
    )
    val first = sourcePackage(
      packageId = "internal.first",
      code = "globalThis.localState = 1; internalAnswer()",
      requiredCapabilities = setOf("internal.answer"),
    )
    val second = sourcePackage(
      packageId = "internal.second",
      code = "typeof localState === 'undefined' ? internalAnswer() + 1 : -1",
      requiredCapabilities = setOf("internal.answer"),
    )

    assertEquals(42, client.installAndExecute<Int>(first, environment).value)
    assertEquals(43, client.installAndExecute<Int>(second, environment).value)
    assertEquals(4, verificationCount)
  }

  /** 验证教学策略拒绝未加入白名单的宿主能力。 */
  @Test
  fun rejectForbiddenTeachingCapability() = runTest {
    val client = createClient()
    val bundle = JsRuntimeBundle(
      id = "teaching-with-network",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsAsyncFunctionCapability("network.request", "request") { "not-called" },
      ),
    )
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = bundle,
      policy = JsExecutionPolicy.teaching(),
    )

    assertFailsWith<JsPolicyViolationException> {
      client.install(sourcePackage("teaching.forbidden", "1"), environment)
    }
  }

  /** 验证教学控制台和异步输入通过公共 Runtime 能力正常工作。 */
  @Test
  fun executeWithTeachingConsoleAndLineInput() = runTest {
    val client = createClient()
    val messages = mutableListOf<JsConsoleMessage>()
    var requestedPrompt: String? = null
    val bundle = JsRuntimeBundle(
      id = "teaching-io",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsTeachingConsoleCapability(messages::add),
        JsTeachingInputCapability(
          input = { prompt ->
            requestedPrompt = prompt
            "Cyxbs"
          },
        ),
      ),
    )
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = bundle,
      policy = JsExecutionPolicy.teaching(
        allowedCapabilityIds = setOf(
          JsTeachingConsoleCapability.ID,
          JsTeachingInputCapability.ID,
        ),
      ),
    )
    val sourcePackage = JsSourcePackage.create(
      packageId = "teaching.io",
      version = "1",
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = mapOf(
        "main.js" to """
          const name = await readLine("Your name?");
          console.log("Hello", name, 40 + 2);
        """.trimIndent(),
      ),
      requiredCapabilities = setOf(
        JsTeachingConsoleCapability.ID,
        JsTeachingInputCapability.ID,
      ),
    )

    client.installAndExecute<Any?>(sourcePackage, environment)

    assertEquals("Your name?", requestedPrompt)
    assertEquals(
      listOf(JsConsoleMessage(JsConsoleLevel.LOG, listOf("Hello", "Cyxbs", "42"))),
      messages,
    )
  }

  /** 验证取消 Program 执行协程会终止已经进入的无限循环。 */
  @Test
  fun cancelRunningTeachingProgram() = runTest {
    val client = createClient()
    val started = CompletableDeferred<Unit>()
    val capabilityId = "teaching.execution-started"
    val bundle = JsRuntimeBundle(
      id = "teaching-cancellation",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsSyncFunctionCapability(capabilityId, "notifyExecutionStarted") {
          started.complete(Unit)
          null
        },
      ),
    )
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = bundle,
      policy = JsExecutionPolicy.teaching(allowedCapabilityIds = setOf(capabilityId)),
    )
    val sourcePackage = sourcePackage(
      packageId = "teaching.cancellation",
      code = "notifyExecutionStarted(); while (true) {}",
      requiredCapabilities = setOf(capabilityId),
    )
    client.install(sourcePackage, environment)

    val execution = backgroundScope.launch {
      client.execute<Unit>(sourcePackage.reference, environment)
    }
    withContext(Dispatchers.Default) {
      withTimeout(5.seconds) {
        started.await()
        execution.cancelAndJoin()
      }
    }
    assertTrue(execution.isCancelled)
  }

  /** 验证源码包 Module 与 Bundle Module 共同通过公共 Module Loader 执行。 */
  @Test
  fun executeModulesFromPackageAndBundle() = runTest {
    val client = createClient()
    var captured: Int? = null
    val bundle = JsRuntimeBundle(
      id = "teaching-module",
      version = 1,
      hostApiVersion = 1,
      modules = mapOf("shared" to "export const base = 40;"),
      capabilities = listOf(
        JsSyncFunctionCapability("teaching.capture", "capture") { args ->
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

    client.installAndExecute<Any?>(sourcePackage, environment)
    assertEquals(42, captured)
  }

  /** 验证源码 Map 的插入顺序不会改变内容哈希。 */
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

  private fun createClient(): JsProgramClient {
    return JsProgramClient(
      sourceStore = InMemoryJsProgramStorage(),
      runtimeFactory = QuickJsRuntimeFactory,
      allowBytecodeCache = false,
    )
  }

  private fun sourcePackage(
    packageId: String,
    code: String,
    requiredCapabilities: Set<String> = emptySet(),
  ): JsSourcePackage {
    return JsSourcePackage.create(
      packageId = packageId,
      version = "1",
      files = mapOf(JsRuntime.DEFAULT_FILENAME to code),
      requiredCapabilities = requiredCapabilities,
    )
  }
}
