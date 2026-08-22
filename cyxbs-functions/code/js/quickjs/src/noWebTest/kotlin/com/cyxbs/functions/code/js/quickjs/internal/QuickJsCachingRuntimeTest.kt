package com.cyxbs.functions.code.js.quickjs.internal

import com.cyxbs.functions.code.js.quickjs.QuickJsRuntime
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsModuleNormalizer
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import com.cyxbs.functions.code.js.runtime.JsSyncFunctionBridge
import com.cyxbs.functions.code.js.runtime.create
import com.cyxbs.functions.code.js.runtime.evaluate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** QuickJS 内部缓存、静态依赖预检和失败停止测试。 */
class QuickJsCachingRuntimeTest {

  /** 损坏入口会停止当前执行并失效缓存，只允许下一次业务执行从源码恢复。 */
  @Test
  fun corruptedEntryCacheStopsCurrentEvaluation() = runTest {
    val code = "capture(42);"
    val filename = "cache-recovery-entry.js"
    val key = entryKey(code, filename)
    val invalidBytecode = byteArrayOf(1, 2, 3, 4)
    QuickJsBytecodeCache.writeEntry(key, invalidBytecode)

    var captured: Int? = null
    val runtime = QuickJsRuntimeFactory.create(
      bridges = listOf(captureBridge { captured = it }),
    )
    try {
      assertFailsWith<JsRuntimeException> {
        runtime.evaluate<Unit>(code = code, filename = filename, asModule = true)
      }
    } finally {
      runtime.close()
    }

    assertNull(captured)
    assertEquals(42, evaluateCapturedEntry(code = code, filename = filename))
    assertNotNull(QuickJsBytecodeCache.readEntry(key))
  }

  /** 静态预解析会保存依赖清单和每个实际依赖的独立字节码。 */
  @Test
  fun staticResolutionPersistsDependencyManifest() = runTest {
    val code = "import { value } from 'cache-dependency'; globalThis.answer = value;"
    val filename = "cache-manifest-entry.js"
    val dependencySource = "export const value = 42;"
    val version = engineVersion()
    val entryKey = createQuickJsCacheKey("entry", filename, code, version)
    val moduleKey = createQuickJsCacheKey(
      kind = "module",
      name = "cache-dependency",
      source = dependencySource,
      engineVersion = version,
    )

    val runtime = QuickJsRuntimeFactory.create(
      moduleLoader = JsModuleLoader { name ->
        dependencySource.takeIf { name == "cache-dependency" }
      },
    )
    try {
      runtime.evaluate<Unit>(code = code, filename = filename, asModule = true)
      assertEquals(42, runtime.evaluate<Int>("globalThis.answer"))
    } finally {
      runtime.close()
    }

    assertEquals(setOf("cache-dependency"), QuickJsBytecodeCache.readManifest(entryKey))
    assertNotNull(QuickJsBytecodeCache.readModule(moduleKey))
  }

  /** Module 名称解析在启用或关闭字节码缓存时都必须透传到 QuickJS。 */
  @Test
  fun moduleNormalizerWorksWithAndWithoutBytecodeCache() = runTest {
    listOf(false, true).forEach { allowBytecodeCache ->
      val filename = "normalizer-$allowBytecodeCache-entry.js"
      val canonicalName = "packages/answer.js"
      val normalized = mutableListOf<Pair<String, String>>()
      val loader = object : JsModuleLoader {
        override val normalizer = JsModuleNormalizer { baseName, requestedName ->
          normalized += baseName to requestedName
          canonicalName
        }

        override fun load(name: String): String? {
          return "export const value = 42;".takeIf { name == canonicalName }
        }
      }
      var captured: Int? = null
      val runtime = QuickJsRuntimeFactory.create(
        JsRuntimeOptions(
          moduleLoader = loader,
          allowBytecodeCache = allowBytecodeCache,
          bridges = listOf(captureBridge { captured = it }),
        ),
      )
      try {
        runtime.evaluate<Unit>(
          code = "import { value } from 'answer-alias'; capture(value);",
          filename = filename,
          asModule = true,
        )
      } finally {
        runtime.close()
      }

      assertEquals(42, captured)
      assertEquals(setOf(filename to "answer-alias"), normalized.toSet())
    }
  }

  /** 依赖源码哈希变化后只允许使用新源码对应的缓存，不能继续执行旧 Module 字节码。 */
  @Test
  fun changedDependencySourceInvalidatesModuleBytecode() = runTest {
    val filename = "cache-changed-dependency-entry.js"
    val moduleName = "cache-changed-dependency"
    val code = "import { value } from '$moduleName'; capture(value);"

    assertEquals(
      41,
      evaluateCapturedModule(
        code = code,
        filename = filename,
        moduleName = moduleName,
        moduleSource = "export const value = 41;",
      ),
    )
    assertEquals(
      42,
      evaluateCapturedModule(
        code = code,
        filename = filename,
        moduleName = moduleName,
        moduleSource = "export const value = 42;",
      ),
    )
  }

  /** QuickJS 无法解析依赖缓存时停止当前执行，下一次独立执行才从源码恢复。 */
  @Test
  fun corruptedDependencyCacheStopsCurrentEvaluation() = runTest {
    val filename = "cache-corrupted-dependency-entry.js"
    val moduleName = "cache-corrupted-dependency"
    val moduleSource = "export const value = 42;"
    val code = "import { value } from '$moduleName'; capture(value);"

    assertEquals(42, evaluateCapturedModule(code, filename, moduleName, moduleSource))
    val moduleKey = createQuickJsCacheKey(
      kind = "module",
      name = moduleName,
      source = moduleSource,
      engineVersion = engineVersion(),
    )
    QuickJsBytecodeCache.writeModule(moduleKey, byteArrayOf(1, 2, 3, 4))

    var captured: Int? = null
    val failedRuntime = QuickJsRuntimeFactory.create(
      moduleLoader = JsModuleLoader { name -> moduleSource.takeIf { name == moduleName } },
      bridges = listOf(captureBridge { captured = it }),
    )
    try {
      assertFailsWith<JsRuntimeException> {
        failedRuntime.evaluate<Unit>(code = code, filename = filename, asModule = true)
      }
    } finally {
      failedRuntime.close()
    }

    assertNull(captured)
    assertEquals(42, evaluateCapturedModule(code, filename, moduleName, moduleSource))
    val repaired = assertNotNull(QuickJsBytecodeCache.readModule(moduleKey))
    assertTrue(!repaired.contentEquals(byteArrayOf(1, 2, 3, 4)))
  }

  /** 损坏依赖只会失效自身缓存，不能连带删除有效入口和同图中的其他依赖。 */
  @Test
  fun corruptedDependencyCacheInvalidatesOnlyNamedModule() = runTest {
    val filename = "cache-targeted-invalidation-entry.js"
    val corruptedModuleName = "cache-targeted-corrupted"
    val validModuleName = "cache-targeted-valid"
    val code = """
      import { base } from '$corruptedModuleName';
      import { offset } from '$validModuleName';
      capture(base + offset);
    """.trimIndent()
    val moduleSources = mapOf(
      corruptedModuleName to "export const base = 40;",
      validModuleName to "export const offset = 2;",
    )
    val version = engineVersion()
    val entryKey = createQuickJsCacheKey("entry", filename, code, version)
    val corruptedModuleKey = createQuickJsCacheKey(
      kind = "module",
      name = corruptedModuleName,
      source = moduleSources.getValue(corruptedModuleName),
      engineVersion = version,
    )
    val validModuleKey = createQuickJsCacheKey(
      kind = "module",
      name = validModuleName,
      source = moduleSources.getValue(validModuleName),
      engineVersion = version,
    )

    assertEquals(42, evaluateCapturedModules(code, filename, moduleSources))
    val validModuleBytecode = assertNotNull(QuickJsBytecodeCache.readModule(validModuleKey))
    assertNotNull(QuickJsBytecodeCache.readEntry(entryKey))
    QuickJsBytecodeCache.writeModule(corruptedModuleKey, byteArrayOf(1, 2, 3, 4))

    val failedRuntime = QuickJsRuntimeFactory.create(
      moduleLoader = JsModuleLoader { name -> moduleSources[name] },
    )
    try {
      assertFailsWith<JsRuntimeException> {
        failedRuntime.evaluate<Unit>(code = code, filename = filename, asModule = true)
      }
    } finally {
      failedRuntime.close()
    }

    assertNull(QuickJsBytecodeCache.readModule(corruptedModuleKey))
    assertContentEquals(validModuleBytecode, QuickJsBytecodeCache.readModule(validModuleKey))
    assertNotNull(QuickJsBytecodeCache.readEntry(entryKey))
  }

  /** 被 JavaScript 捕获的动态 import 失败仍会精准失效对应的持久化缓存。 */
  @Test
  fun handledDynamicImportFailureInvalidatesNamedModuleCache() = runTest {
    val filename = "cache-handled-dynamic-import-entry.js"
    val moduleName = "cache-handled-dynamic-import"
    val moduleSource = "export const value = 42;"
    val code = "try { await import('$moduleName'); } catch (_) {}"
    val moduleKey = createQuickJsCacheKey(
      kind = "module",
      name = moduleName,
      source = moduleSource,
      engineVersion = engineVersion(),
    )

    val initialRuntime = QuickJsRuntimeFactory.create(
      moduleLoader = JsModuleLoader { name -> moduleSource.takeIf { name == moduleName } },
    )
    try {
      initialRuntime.evaluate<Unit>(code = code, filename = filename, asModule = true)
    } finally {
      initialRuntime.close()
    }
    assertNotNull(QuickJsBytecodeCache.readModule(moduleKey))
    QuickJsBytecodeCache.writeModule(moduleKey, byteArrayOf(1, 2, 3, 4))

    val failedRuntime = QuickJsRuntimeFactory.create(
      moduleLoader = JsModuleLoader { name -> moduleSource.takeIf { name == moduleName } },
    )
    try {
      failedRuntime.evaluate<Unit>(code = code, filename = filename, asModule = true)
    } finally {
      failedRuntime.close()
    }

    assertNull(QuickJsBytecodeCache.readModule(moduleKey))
  }

  /** 无缓存工厂不会读取或覆盖同一源码对应的损坏持久化入口。 */
  @Test
  fun disabledCacheIgnoresPersistentBytecode() = runTest {
    val code = "globalThis.answer = 42;"
    val filename = "teaching-cache-disabled-entry.js"
    val key = entryKey(code, filename)
    val invalidBytecode = byteArrayOf(9, 8, 7)
    QuickJsBytecodeCache.writeEntry(key, invalidBytecode)

    val runtime = QuickJsRuntimeFactory.create(allowBytecodeCache = false)
    try {
      runtime.evaluate<Unit>(code = code, filename = filename, asModule = true)
      assertEquals(42, runtime.evaluate<Int>("globalThis.answer"))
    } finally {
      runtime.close()
    }

    assertContentEquals(invalidBytecode, QuickJsBytecodeCache.readEntry(key))
  }

  /** entry、Module 和依赖清单分别按自身最后使用时间执行 14 天 GC。 */
  @Test
  fun bytecodeArtifactsExpireIndependentlyAfterFourteenDays() = runTest {
    val entryKey = entryKey("gc-entry", "gc-entry.js")
    val moduleKey = entryKey("gc-module", "gc-module.js")
    QuickJsBytecodeCache.writeEntry(entryKey, byteArrayOf(1, 2, 3))
    QuickJsBytecodeCache.writeModule(moduleKey, byteArrayOf(4, 5, 6))
    QuickJsBytecodeCache.writeManifest(entryKey, setOf("gc-module"))

    QuickJsBytecodeCache.collectGarbage(
      Clock.System.now().toEpochMilliseconds() + 15.days.inWholeMilliseconds,
    )

    assertNull(QuickJsBytecodeCache.readEntry(entryKey))
    assertNull(QuickJsBytecodeCache.readModule(moduleKey))
    assertTrue(QuickJsBytecodeCache.readManifest(entryKey).isEmpty())
  }

  private fun entryKey(code: String, filename: String): QuickJsCacheKey {
    return createQuickJsCacheKey(
      kind = "entry",
      name = filename,
      source = code,
      engineVersion = engineVersion(),
    )
  }

  /** 创建下一次独立 Runtime 执行，用于验证失败缓存不会在当前调用内被自动重试。 */
  private suspend fun evaluateCapturedEntry(code: String, filename: String): Int? {
    var captured: Int? = null
    val runtime = QuickJsRuntimeFactory.create(
      bridges = listOf(captureBridge { captured = it }),
    )
    try {
      runtime.evaluate<Unit>(code = code, filename = filename, asModule = true)
    } finally {
      runtime.close()
    }
    return captured
  }

  /** 创建独立 Runtime 执行单依赖 Module，并通过宿主函数取回模块值。 */
  private suspend fun evaluateCapturedModule(
    code: String,
    filename: String,
    moduleName: String,
    moduleSource: String,
  ): Int? {
    return evaluateCapturedModules(
      code = code,
      filename = filename,
      moduleSources = mapOf(moduleName to moduleSource),
    )
  }

  /** 创建独立 Runtime 执行多依赖 Module，并通过宿主函数取回模块值。 */
  private suspend fun evaluateCapturedModules(
    code: String,
    filename: String,
    moduleSources: Map<String, String>,
  ): Int? {
    var captured: Int? = null
    val runtime = QuickJsRuntimeFactory.create(
      moduleLoader = JsModuleLoader { name -> moduleSources[name] },
      bridges = listOf(captureBridge { captured = it }),
    )
    try {
      runtime.evaluate<Unit>(code = code, filename = filename, asModule = true)
    } finally {
      runtime.close()
    }
    return captured
  }

  /** 创建测试专用 capture 桥，并在 Runtime 构造前冻结回调。 */
  private fun captureBridge(onCaptured: (Int) -> Unit): JsSyncFunctionBridge =
    JsSyncFunctionBridge("capture") { args ->
      onCaptured((args.single() as Number).toInt())
      null
    }

  private fun engineVersion(): String {
    val runtime = QuickJsRuntime()
    return try {
      runtime.engineVersion
    } finally {
      runtime.close()
    }
  }
}
