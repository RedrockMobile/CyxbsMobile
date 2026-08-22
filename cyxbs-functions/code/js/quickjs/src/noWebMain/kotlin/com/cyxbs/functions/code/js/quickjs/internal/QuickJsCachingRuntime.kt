package com.cyxbs.functions.code.js.quickjs.internal

import com.cyxbs.functions.code.js.quickjs.QuickJsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.ByteString.Companion.toByteString

/**
 * 为 QuickJS Runtime 增加透明的 Module 字节码缓存与静态依赖预检。
 *
 * Program 仍然只看到 [JsRuntime]。静态预检一旦失败，本次执行会立即停止；实现只会使本次实际
 * 使用的可疑缓存失效，等待调用方下一次创建 Runtime 时自然从源码重新编译。
 */
internal class QuickJsCachingRuntime(
  private val options: JsRuntimeOptions,
) : JsRuntime {
  private val moduleState = JsModuleCacheState(options.moduleLoader)
  private val runtime = createRuntime(moduleState)
  private var closed = false

  override val isClosed: Boolean
    get() = closed || runtime.isClosed

  /**
   * Module 入口使用内部字节码缓存和静态预解析；普通 Script 直接交给 QuickJS 源码执行。
   */
  override suspend fun evaluateValue(
    code: String,
    filename: String,
    asModule: Boolean,
  ): Any? {
    check(!closed) { "JavaScript Runtime is already closed." }
    if (!asModule) {
      return runtime.evaluateValue(code = code, filename = filename, asModule = false)
    }
    return evaluateModule(code = code, filename = filename)
  }

  override fun interruptEvaluation() {
    runtime.interruptEvaluation()
  }

  override fun close() {
    if (closed) return
    closed = true
    runtime.close()
  }

  /**
   * 预读缓存、编译入口并解析静态依赖，确认安全后才执行 Module 顶层代码。
   */
  private suspend fun evaluateModule(
    code: String,
    filename: String,
  ): Any? {
    val entryKey = createQuickJsCacheKey(
      kind = "entry",
      name = filename,
      source = code,
      engineVersion = runtime.engineVersion,
    )
    val previousManifest = cacheRead { QuickJsBytecodeCache.readManifest(entryKey) }.orEmpty()
    val moduleKeys = linkedMapOf<String, QuickJsCacheKey>()
    previousManifest.forEach { name ->
      val source = options.moduleLoader?.load(name) ?: return@forEach
      val key = createQuickJsCacheKey(
        kind = "module",
        name = name,
        source = source,
        engineVersion = runtime.engineVersion,
      )
      moduleKeys[name] = key
      cacheRead { QuickJsBytecodeCache.readModule(key) }?.let { bytecode ->
        moduleState.seedCachedBytecode(name, bytecode)
      }
    }

    var entryUsedCache = false
    var compiledEntry: ByteArray? = null
    val entryBytecode = cacheRead { QuickJsBytecodeCache.readEntry(entryKey) }?.also {
      entryUsedCache = true
    } ?: runtime.compile(code = code, filename = filename, asModule = true).also {
      compiledEntry = it
    }

    val resolvedNames = try {
      // 这里只解析、编译静态 import，不执行 Module 顶层代码；失败时必须立即结束本次执行。
      runtime.resolveModuleGraph(entryBytecode)
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      val failedModuleNames = moduleState.takeFailedModuleNames()
      if (failedModuleNames.isEmpty() && entryUsedCache) {
        cacheWrite { QuickJsBytecodeCache.removeEntry(entryKey) }
      }
      invalidateFailedModuleCaches(
        failedModuleNames = failedModuleNames,
        cachedModuleNames = moduleState.usedCachedModuleNames(),
        moduleKeys = moduleKeys,
      )
      // 只为下一次独立执行清除可疑缓存，当前调用不重建 Runtime，也不自动回退源码。
      throw throwable
    }

    val cachedBeforeEvaluation = moduleState.usedCachedModuleNames()
    try {
      val result = runtime.evaluateBytecodeValue(entryBytecode)
      invalidateFailedModuleCaches(
        failedModuleNames = moduleState.takeFailedModuleNames(),
        cachedModuleNames = moduleState.usedCachedModuleNames() - cachedBeforeEvaluation,
        moduleKeys = moduleKeys,
      )
      return result
    } catch (throwable: Throwable) {
      // 动态 import 可能在入口已产生副作用后才失败，因此只删除本次新触达的缓存，绝不自动重跑。
      invalidateFailedModuleCaches(
        failedModuleNames = moduleState.takeFailedModuleNames(),
        cachedModuleNames = moduleState.usedCachedModuleNames() - cachedBeforeEvaluation,
        moduleKeys = moduleKeys,
      )
      throw throwable
    } finally {
      persistCompiled(
        entryKey = entryKey,
        entryName = filename,
        compiledEntry = compiledEntry,
        previousManifest = previousManifest,
        resolvedNames = resolvedNames,
        moduleKeys = moduleKeys,
      )
    }
  }

  /** 在 QuickJS 调用结束后保存入口、依赖字节码和已观察到的 Module 清单。 */
  private suspend fun persistCompiled(
    entryKey: QuickJsCacheKey,
    entryName: String,
    compiledEntry: ByteArray?,
    previousManifest: Set<String>,
    resolvedNames: Set<String>,
    moduleKeys: MutableMap<String, QuickJsCacheKey>,
  ) {
    compiledEntry?.let { bytecode ->
      cacheWrite { QuickJsBytecodeCache.writeEntry(entryKey, bytecode) }
    }
    moduleState.compiledBytecodes().forEach { (name, bytecode) ->
      val source = options.moduleLoader?.load(name) ?: return@forEach
      val key = moduleKeys.getOrPut(name) {
        createQuickJsCacheKey(
          kind = "module",
          name = name,
          source = source,
          engineVersion = runtime.engineVersion,
        )
      }
      cacheWrite { QuickJsBytecodeCache.writeModule(key, bytecode) }
    }
    val observedNames = buildSet {
      addAll(previousManifest)
      addAll(resolvedNames)
      addAll(moduleState.loadedModuleNames())
      remove(entryName)
    }
    cacheWrite { QuickJsBytecodeCache.writeManifest(entryKey, observedNames) }
  }

  /**
   * 只删除引擎明确指出且本次确实从持久化缓存读取的失败 Module。
   *
   * 源码 Module 加载失败不需要清缓存；缺少名称时也不能猜测并连带删除其他有效依赖。
   */
  private suspend fun invalidateFailedModuleCaches(
    failedModuleNames: Set<String>,
    cachedModuleNames: Set<String>,
    moduleKeys: Map<String, QuickJsCacheKey>,
  ) {
    failedModuleNames.forEach { name ->
      if (name !in cachedModuleNames) return@forEach
      val key = moduleKeys[name] ?: return@forEach
      cacheWrite { QuickJsBytecodeCache.removeModule(key) }
    }
  }

  private fun createRuntime(moduleState: JsModuleCacheState): QuickJsRuntime {
    return QuickJsRuntime(
      options = options.copy(moduleLoader = null),
      internalModuleLoader = moduleState,
    )
  }

  private suspend fun <T> cacheRead(block: suspend () -> T): T? {
    return try {
      block()
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      null
    }
  }

  private suspend fun cacheWrite(block: suspend () -> Unit) {
    try {
      block()
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
    }
  }

}

/** 根据源码和当前 QuickJS 版本生成实现内部缓存键。 */
internal fun createQuickJsCacheKey(
  kind: String,
  name: String,
  source: String,
  engineVersion: String,
): QuickJsCacheKey {
  val buffer = Buffer()
  buffer.writeInt(1)
  buffer.writeStableString(kind)
  buffer.writeStableString(name)
  buffer.writeStableString(source.encodeToByteArray().toByteString().sha256().hex())
  buffer.writeStableString("quickjs")
  buffer.writeStableString(engineVersion)
  return QuickJsCacheKey(buffer.readByteArray().toByteString().sha256().hex())
}

private fun Buffer.writeStableString(value: String) {
  val bytes = value.encodeToByteArray()
  writeInt(bytes.size)
  write(bytes)
}
