package com.cyxbs.functions.code.js

import com.cyxbs.functions.code.js.bundle.JsAsyncFunctionCapability
import com.cyxbs.functions.code.js.bundle.JsRuntimeBundle
import com.cyxbs.functions.code.js.bundle.JsSyncFunctionCapability
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleLevel
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleMessage
import com.cyxbs.functions.code.js.bundle.teaching.JsTeachingConsoleCapability
import com.cyxbs.functions.code.js.bundle.teaching.JsTeachingInputCapability
import com.cyxbs.functions.code.js.runtime.QuickJsRuntime
import com.cyxbs.functions.code.js.storage.JsBytecodeCache
import com.cyxbs.functions.code.js.storage.JsBytecodeCacheKey
import com.cyxbs.functions.code.js.storage.InMemoryJsProgramStorage
import com.dokar.quickjs.QuickJsException
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
   * 验证教学 Bundle 可以提供标准控制台输出和可挂起的行输入，且两项能力必须显式进入白名单。
   */
  @Test
  fun executeWithTeachingConsoleAndLineInput() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
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
          console.log({
            course: "KMP\nJS",
            score: 42,
            passed: true,
            tags: ["json", null],
            nested: { quote: '"' },
          });
          console.warn("Check input");
          print("Done");
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
      listOf(
        JsConsoleMessage(JsConsoleLevel.LOG, listOf("Hello", "Cyxbs", "42")),
        JsConsoleMessage(
          JsConsoleLevel.LOG,
          listOf(
            "{\"course\":\"KMP\\nJS\",\"score\":42,\"passed\":true," +
              "\"tags\":[\"json\",null],\"nested\":{\"quote\":\"\\\"\"}}",
          ),
        ),
        JsConsoleMessage(JsConsoleLevel.WARN, listOf("Check input")),
        JsConsoleMessage(JsConsoleLevel.LOG, listOf("Done")),
      ),
      messages,
    )
  }

  /**
   * 验证业务取消执行协程时，Client 会借助 QuickJS 原生 interrupt 停止已经进入的无限循环。
   */
  @Test
  fun cancelRunningTeachingProgram() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    val started = CompletableDeferred<Unit>()
    val capabilityId = "teaching.execution-started"
    val bundle = JsRuntimeBundle(
      id = "teaching-cancellation",
      version = 1,
      hostApiVersion = 1,
      capabilities = listOf(
        JsSyncFunctionCapability(
          id = capabilityId,
          functionName = "notifyExecutionStarted",
        ) {
          started.complete(Unit)
          null
        },
      ),
    )
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = bundle,
      policy = JsExecutionPolicy.teaching(
        allowedCapabilityIds = setOf(capabilityId),
      ),
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

  /**
   * 验证源码包、业务模块和共享 Bundle 模块可以共同组成 ES Module 执行图。
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

    val first = client.installAndExecute<Any?>(
      sourcePackage = sourcePackage,
      environment = environment,
    )
    assertEquals(42, captured)
    captured = null
    val second = client.execute<Any?>(
      reference = sourcePackage.reference,
      environment = environment,
    )

    assertEquals(42, captured)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, first.origin)
    assertEquals(setOf("main.js", "shared", "increment"), first.compiledModules)
    assertTrue(first.cachedModules.isEmpty())
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, second.origin)
    assertTrue(second.compiledModules.isEmpty())
    assertEquals(setOf("main.js", "shared", "increment"), second.cachedModules)
  }

  /**
   * 验证同一 packageId 发布新版本时，只重新编译源码发生变化的静态依赖 Module。
   */
  @Test
  fun recompileOnlyChangedStaticModuleAcrossVersions() = runTest {
    val storage = InMemoryJsProgramStorage()
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
    val environment = JsExecutionEnvironment.forTeaching(
      bundle = JsRuntimeBundle(
        id = "teaching-incremental",
        version = 1,
        hostApiVersion = 1,
        capabilities = listOf(
          JsSyncFunctionCapability(
            id = "teaching.capture",
            functionName = "capture",
          ) { null },
        ),
      ),
      policy = JsExecutionPolicy.teaching(
        allowedCapabilityIds = setOf("teaching.capture"),
      ),
    )
    val firstPackage = staticModulePackage(
      version = "1",
      middleSource = """
        import { value } from "leaf";
        export const result = value + 1;
      """.trimIndent(),
    )
    val secondPackage = staticModulePackage(
      version = "2",
      middleSource = """
        import { value } from "leaf";
        export const result = value + 2;
      """.trimIndent(),
    )

    val first = client.installAndExecute<Any?>(firstPackage, environment)
    val second = client.installAndExecute<Any?>(secondPackage, environment)

    assertEquals(setOf("main.js", "middle", "leaf"), first.compiledModules)
    assertTrue(first.cachedModules.isEmpty())
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, second.origin)
    assertEquals(setOf("middle"), second.compiledModules)
    assertEquals(setOf("main.js", "leaf"), second.cachedModules)
  }

  /**
   * 验证动态 import 只在执行到对应语句时加载，并在源码变化后仅重编译动态依赖。
   */
  @Test
  fun cacheDynamicImportAndRecompileChangedModule() = runTest {
    val storage = InMemoryJsProgramStorage()
    val corruptingCache = CorruptingBytecodeCache(storage)
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = corruptingCache)
    var captured: Int? = null
    val bundle = JsRuntimeBundle(
      id = "teaching-dynamic",
      version = 1,
      hostApiVersion = 1,
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
    val firstPackage = dynamicModulePackage(version = "1", value = 41)
    val secondPackage = dynamicModulePackage(version = "2", value = 42)

    val first = client.installAndExecute<Any?>(firstPackage, environment)
    assertEquals(41, captured)
    assertEquals(setOf("main.js", "dynamic"), first.compiledModules)
    assertTrue(first.cachedModules.isEmpty())

    captured = null
    val cached = client.execute<Any?>(firstPackage.reference, environment)
    assertEquals(41, captured)
    assertTrue(cached.compiledModules.isEmpty())
    assertEquals(setOf("main.js", "dynamic"), cached.cachedModules)

    corruptingCache.resetReadCount()
    corruptingCache.corruptReadNumber = 2
    assertFailsWith<QuickJsException> {
      client.execute<Any?>(firstPackage.reference, environment)
    }

    captured = null
    corruptingCache.resetReadCount()
    corruptingCache.corruptReadNumber = null
    val repaired = client.execute<Any?>(firstPackage.reference, environment)
    assertEquals(41, captured)
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, repaired.origin)
    assertEquals(setOf("dynamic"), repaired.compiledModules)
    assertEquals(setOf("main.js"), repaired.cachedModules)

    captured = null
    val changed = client.installAndExecute<Any?>(secondPackage, environment)
    assertEquals(42, captured)
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, changed.origin)
    assertEquals(setOf("dynamic"), changed.compiledModules)
    assertEquals(setOf("main.js"), changed.cachedModules)
  }

  /**
   * 验证静态准备阶段遇到不可解析缓存时，会在新 Runtime 中回退源码并修复缓存。
   */
  @Test
  fun fallbackToSourceWhenStaticBytecodeIsInvalid() = runTest {
    val storage = InMemoryJsProgramStorage()
    val corruptingCache = CorruptingBytecodeCache(storage)
    val client = JsProgramClient(sourceStore = storage, bytecodeCache = corruptingCache)
    val environment = JsExecutionEnvironment.forTeaching()
    val sourcePackage = JsSourcePackage.create(
      packageId = "teaching.invalid-cache",
      version = "1",
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = mapOf(
        "main.js" to "import { value } from 'answer'; globalThis.result = value;",
        "answer" to "export const value = 42;",
      ),
    )

    val first = client.installAndExecute<Any?>(sourcePackage, environment)
    assertEquals(setOf("main.js", "answer"), first.compiledModules)

    corruptingCache.corruptReads = true
    val recovered = client.execute<Any?>(sourcePackage.reference, environment)
    assertEquals(JsExecutableOrigin.COMPILED_SOURCE, recovered.origin)
    assertEquals(setOf("main.js", "answer"), recovered.compiledModules)
    assertTrue(recovered.cachedModules.isEmpty())

    corruptingCache.corruptReads = false
    val cached = client.execute<Any?>(sourcePackage.reference, environment)
    assertEquals(JsExecutableOrigin.BYTECODE_CACHE, cached.origin)
    assertTrue(cached.compiledModules.isEmpty())
    assertEquals(setOf("main.js", "answer"), cached.cachedModules)
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

  /** 创建包含两级静态依赖的增量缓存测试包。 */
  private fun staticModulePackage(
    version: String,
    middleSource: String,
  ): JsSourcePackage {
    return JsSourcePackage.create(
      packageId = "teaching.incremental",
      version = version,
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = mapOf(
        "main.js" to """
          import { result } from "middle";
          capture(result);
        """.trimIndent(),
        "middle" to middleSource,
        "leaf" to "export const value = 40;",
      ),
      requiredCapabilities = setOf("teaching.capture"),
    )
  }

  /** 创建仅包含一个动态依赖的增量缓存测试包。 */
  private fun dynamicModulePackage(
    version: String,
    value: Int,
  ): JsSourcePackage {
    return JsSourcePackage.create(
      packageId = "teaching.dynamic",
      version = version,
      entry = "main.js",
      mode = JsProgramMode.MODULE,
      files = mapOf(
        "main.js" to """
          const loaded = await import("dynamic");
          capture(loaded.value);
        """.trimIndent(),
        "dynamic" to "export const value = $value;",
      ),
      requiredCapabilities = setOf("teaching.capture"),
    )
  }

  /**
   * 在读取命中时返回不可解析内容，用于模拟通过缓存层读取后 QuickJS 仍无法接受的缓存。
   */
  private class CorruptingBytecodeCache(
    private val delegate: JsBytecodeCache,
  ) : JsBytecodeCache by delegate {

    var corruptReads: Boolean = false
    var corruptReadNumber: Int? = null
    private var readCount: Int = 0

    fun resetReadCount() {
      readCount = 0
    }

    override suspend fun readBytecode(key: JsBytecodeCacheKey): ByteArray? {
      val bytecode = delegate.readBytecode(key) ?: return null
      readCount++
      return if (corruptReads || readCount == corruptReadNumber) byteArrayOf(0) else bytecode
    }
  }
}
