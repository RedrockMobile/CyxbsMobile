package com.cyxbs.functions.code.js.teaching

import com.cyxbs.functions.code.js.program.JsExecutionEnvironment
import com.cyxbs.functions.code.js.program.JsExecutionPolicy
import com.cyxbs.functions.code.js.program.JsPolicyViolationException
import com.cyxbs.functions.code.js.program.JsProgramClient
import com.cyxbs.functions.code.js.program.JsProgramMode
import com.cyxbs.functions.code.js.program.JsSourcePackage
import com.cyxbs.functions.code.js.program.JsSourceVerificationException
import com.cyxbs.functions.code.js.bundle.JsRuntimeBundle
import com.cyxbs.functions.code.js.bundle.teaching.JsConsoleMessage
import com.cyxbs.functions.code.js.bundle.teaching.JsTeachingConsoleCapability
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 基于现有 [JsProgramClient] 的教学 JavaScript Runner。
 *
 * 源码仅保存在当前对象的内存中，适合编辑器即时运行和预览；创建 Client 时固定禁用持久化缓存。
 * 同一个 Runner 的并发执行会串行化，避免固定教学源码引用在安装与读取之间被另一轮运行覆盖。
 */
internal class DefaultJsTeachingCodeRunner(
  private val client: JsProgramClient,
) : JsTeachingCodeRunner {
  private val executionMutex = Mutex()

  /**
   * 在互斥区内组装最小教学 Bundle，并复用同一 Client 完成源码安装和执行。
   */
  @Throws(
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    JsRuntimeException::class,
    CancellationException::class,
  )
  override suspend fun execute(code: String): JsTeachingCodeResult {
    return execute(
      files = mapOf(ENTRY_FILE_NAME to code),
      entryFile = ENTRY_FILE_NAME,
      mode = JsProgramMode.SCRIPT,
    )
  }

  /**
   * 将编辑器工作区按 Module 语义安装并执行，依赖解析交给 Runtime 的 Module Loader 完成。
   */
  @Throws(
    IllegalArgumentException::class,
    JsPolicyViolationException::class,
    JsSourceVerificationException::class,
    JsRuntimeException::class,
    CancellationException::class,
  )
  override suspend fun executeModule(
    files: Map<String, String>,
    entryFile: String,
  ): JsTeachingCodeResult {
    return execute(
      files = files,
      entryFile = entryFile,
      mode = JsProgramMode.MODULE,
    )
  }

  /**
   * 串行组装教学 Bundle 和源码包；[mode] 决定入口使用 Script 还是 Module 语义。
   */
  private suspend fun execute(
    files: Map<String, String>,
    entryFile: String,
    mode: JsProgramMode,
  ): JsTeachingCodeResult {
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
        entry = entryFile,
        mode = mode,
        files = files,
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
