package com.cyxbs.functions.code.js.teaching

import com.cyxbs.functions.code.js.JsExecutionEnvironment
import com.cyxbs.functions.code.js.JsExecutionPolicy
import com.cyxbs.functions.code.js.JsPolicyViolationException
import com.cyxbs.functions.code.js.JsProgramClient
import com.cyxbs.functions.code.js.JsProgramMode
import com.cyxbs.functions.code.js.JsSourcePackage
import com.cyxbs.functions.code.js.JsSourceVerificationException
import com.cyxbs.functions.code.js.JsTeachingCodeResult
import com.cyxbs.functions.code.js.JsTeachingCodeRunner
import com.cyxbs.functions.code.js.bundle.JsRuntimeBundle
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleMessage
import com.cyxbs.functions.code.js.bundle.teaching.JsTeachingConsoleCapability
import com.cyxbs.functions.code.js.storage.InMemoryJsProgramStorage
import com.dokar.quickjs.QuickJsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 基于现有 [JsProgramClient] 的教学 JavaScript Runner。
 *
 * 源码和字节码仅保存在当前对象的内存中，适合编辑器即时运行和预览；进程退出后不会持久化。
 * 同一个 Runner 的并发执行会串行化，避免固定教学源码引用在安装与读取之间被另一轮运行覆盖。
 */
internal class DefaultJsTeachingCodeRunner : JsTeachingCodeRunner {
  private val storage = InMemoryJsProgramStorage()
  private val client = JsProgramClient(sourceStore = storage, bytecodeCache = storage)
  private val executionMutex = Mutex()

  /**
   * 在互斥区内组装最小教学 Bundle，并复用同一 Client 完成源码安装、编译缓存和执行。
   */
  @Throws(
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    QuickJsException::class,
    CancellationException::class,
  )
  override suspend fun execute(code: String): JsTeachingCodeResult {
    return executionMutex.withLock {
      val consoleMessages = mutableListOf<JsConsoleMessage>()
      val consoleCapability = JsTeachingConsoleCapability(consoleMessages::add)
      val bundle = JsRuntimeBundle(
        id = TEACHING_BUNDLE_ID,
        version = TEACHING_BUNDLE_VERSION,
        hostApiVersion = HOST_API_VERSION,
        capabilities = listOf(consoleCapability),
      )
      val environment = JsExecutionEnvironment.forTeaching(
        bundle = bundle,
        policy = JsExecutionPolicy.teaching(
          allowedCapabilityIds = setOf(JsTeachingConsoleCapability.ID),
        ),
      )
      val sourcePackage = JsSourcePackage.create(
        packageId = TEACHING_PACKAGE_ID,
        version = TEACHING_PACKAGE_VERSION,
        entry = ENTRY_FILE_NAME,
        mode = JsProgramMode.SCRIPT,
        files = mapOf(ENTRY_FILE_NAME to code),
        requiredHostApiVersion = HOST_API_VERSION,
        requiredCapabilities = setOf(JsTeachingConsoleCapability.ID),
      )

      val result = client.installAndExecute<Any?>(
        sourcePackage = sourcePackage,
        environment = environment,
      )
      JsTeachingCodeResult(
        value = result.value,
        consoleMessages = consoleMessages.toList(),
        executableOrigin = result.origin,
      )
    }
  }

  private companion object {
    const val TEACHING_PACKAGE_ID = "teaching.editor"
    const val TEACHING_PACKAGE_VERSION = "local"
    const val TEACHING_BUNDLE_ID = "teaching.editor"
    const val TEACHING_BUNDLE_VERSION = 1
    const val HOST_API_VERSION = 1
    const val ENTRY_FILE_NAME = "main.js"
  }
}
