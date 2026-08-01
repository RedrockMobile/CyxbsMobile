package com.cyxbs.functions.code.js.internal

import com.cyxbs.functions.code.js.JsBytecodeCacheErrorHandler
import com.cyxbs.functions.code.js.JsExecutableOrigin
import com.cyxbs.functions.code.js.JsExecutionEnvironment
import com.cyxbs.functions.code.js.JsExecutionResult
import com.cyxbs.functions.code.js.JsProgramMode
import com.cyxbs.functions.code.js.JsSourcePackage
import com.cyxbs.functions.code.js.runtime.QuickJsRuntime
import com.cyxbs.functions.code.js.storage.JsBytecodeCache
import com.cyxbs.functions.code.js.storage.JsBytecodeCacheKey
import com.dokar.quickjs.QuickJsException
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.ByteString.Companion.toByteString

/**
 * 负责单次 JavaScript 程序的字节码准备、依赖解析、执行与缓存持久化。
 *
 * 该类位于内部包，业务统一通过 JsProgramClient 调用。每次执行都会创建独立 QuickJS Runtime；
 * 静态依赖可在执行顶层代码前安全回退，动态依赖失败时只清理缓存而不会自动重复业务副作用。
 *
 * @param bytecodeCache 可删除并由源码重建的逐 Module 字节码缓存。
 * @param cacheErrorHandler 非致命缓存错误监听。
 */
internal class JsProgramExecutor(
  private val bytecodeCache: JsBytecodeCache,
  private val cacheErrorHandler: JsBytecodeCacheErrorHandler,
) {

  /**
   * 执行已经完成来源、签名和策略校验的源码包。
   *
   * @param sourcePackage 当前安装版本的完整源码主数据。
   * @param environment 当前场景策略、Bundle 与宿主能力。
   * @param evaluator 保留调用点结果类型信息的入口字节码执行函数。
   */
  suspend fun <T> execute(
    sourcePackage: JsSourcePackage,
    environment: JsExecutionEnvironment,
    evaluator: suspend (runtime: QuickJsRuntime, entryBytecode: ByteArray) -> T,
  ): JsExecutionResult<T> {
    val prepared = prepareExecution(
      sourcePackage = sourcePackage,
      environment = environment,
      allowCachedBytecode = true,
    )
    val cachedDependenciesBeforeEvaluation = prepared.moduleState.usedCachedModuleNames()
    try {
      return JsExecutionResult(
        value = evaluator(prepared.runtime, prepared.entryBytecode),
        origin = prepared.origin,
        engineVersion = prepared.runtime.engineVersion,
        reference = sourcePackage.reference,
        compiledModules = prepared.compiledModuleNames(),
        cachedModules = prepared.cachedModuleNames(),
      )
    } catch (throwable: Throwable) {
      if (throwable is QuickJsException) {
        val dynamicCacheNames = prepared.moduleState.usedCachedModuleNames() -
          cachedDependenciesBeforeEvaluation
        dynamicCacheNames.forEach { name ->
          prepared.dependencyCacheKeys[name]?.let { cacheKey ->
            removeBytecodeSafely(cacheKey)
          }
        }
      }
      throw throwable
    } finally {
      prepared.runtime.close()
      persistCompiledBytecodes(prepared)
    }
  }

  /**
   * 创建已完成入口编译和静态依赖解析的 Runtime。
   *
   * Module Loader 是同步接口，因此在引擎开始解析前预读取所有已知缓存，但只有实际到达的
   * Module 才会被 QuickJS 加载。若静态准备阶段使用缓存后出现 QuickJS 错误，会删除本次实际
   * 使用过的可疑缓存，并在全新 Runtime 中只用源码重试一次；此时尚未执行顶层代码，不会重复
   * 业务副作用。
   */
  private suspend fun prepareExecution(
    sourcePackage: JsSourcePackage,
    environment: JsExecutionEnvironment,
    allowCachedBytecode: Boolean,
  ): PreparedJsExecution {
    val dependencyDefinitions = createDependencyDefinitions(
      sourcePackage = sourcePackage,
      environment = environment,
    )
    val moduleState = JsModuleCacheState(dependencyDefinitions)
    val runtime = QuickJsRuntime(
      config = environment.policy.runtimeConfig,
      moduleLoader = moduleState,
    )
    val entryDefinition = JsModuleDefinition(
      name = sourcePackage.entry,
      source = sourcePackage.entrySource(),
      ownerId = packageOwnerId(sourcePackage.packageId),
    )
    val entryCacheKey = createModuleCacheKey(
      definition = entryDefinition,
      mode = sourcePackage.mode,
      environment = environment,
      engineVersion = runtime.engineVersion,
    )
    val dependencyCacheKeys = dependencyDefinitions.mapValues { (_, definition) ->
      createModuleCacheKey(
        definition = definition,
        mode = JsProgramMode.MODULE,
        environment = environment,
        engineVersion = runtime.engineVersion,
      )
    }
    var entryUsedCache = false
    var compiledEntryBytecode: ByteArray? = null

    try {
      environment.bundle.install(runtime)

      val cachedEntryBytecode = if (allowCachedBytecode) {
        readBytecodeSafely(entryCacheKey)
      } else {
        null
      }
      if (allowCachedBytecode) {
        dependencyCacheKeys.forEach { (name, cacheKey) ->
          readBytecodeSafely(cacheKey)?.let { bytecode ->
            moduleState.seedCachedBytecode(name = name, bytecode = bytecode)
          }
        }
      }

      val entryBytecode = if (cachedEntryBytecode != null) {
        entryUsedCache = true
        cachedEntryBytecode
      } else {
        runtime.compile(
          code = entryDefinition.source,
          filename = entryDefinition.name,
          asModule = sourcePackage.mode == JsProgramMode.MODULE,
        ).also { bytecode ->
          compiledEntryBytecode = bytecode
        }
      }

      if (sourcePackage.mode == JsProgramMode.MODULE) {
        // 这里只解析、编译静态 import，不执行 Module 顶层代码，使损坏缓存能在产生业务副作用前安全回退源码；
        // onCompiled 会立即写入内存，正式 evaluate 时会复用而不是重复编译。
        runtime.resolveModuleGraph(entryBytecode)
      }

      return PreparedJsExecution(
        runtime = runtime,
        entryBytecode = entryBytecode,
        entryName = entryDefinition.name,
        entryCacheKey = entryCacheKey,
        entryUsedCache = entryUsedCache,
        compiledEntryBytecode = compiledEntryBytecode,
        dependencyCacheKeys = dependencyCacheKeys,
        moduleState = moduleState,
      )
    } catch (throwable: Throwable) {
      val usedCacheKeys = buildList {
        if (entryUsedCache) add(entryCacheKey)
        moduleState.usedCachedModuleNames().mapNotNullTo(this) { dependencyCacheKeys[it] }
      }
      runtime.close()
      persistCompiledBytecodes(
        entryCacheKey = entryCacheKey,
        compiledEntryBytecode = compiledEntryBytecode,
        dependencyCacheKeys = dependencyCacheKeys,
        compiledDependencies = moduleState.compiledBytecodes(),
      )

      if (allowCachedBytecode && throwable is QuickJsException && usedCacheKeys.isNotEmpty()) {
        usedCacheKeys.forEach { cacheKey ->
          removeBytecodeSafely(cacheKey)
        }
        return prepareExecution(
          sourcePackage = sourcePackage,
          environment = environment,
          allowCachedBytecode = false,
        )
      }
      throw throwable
    }
  }

  /**
   * 缓存读取失败时退化为未命中，但协程取消必须继续向上传播。
   */
  private suspend fun readBytecodeSafely(key: JsBytecodeCacheKey): ByteArray? {
    return try {
      bytecodeCache.readBytecode(key)
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      cacheErrorHandler.onError(key, throwable)
      null
    }
  }

  /**
   * 缓存写入失败不影响本次执行，但协程取消必须继续向上传播。
   */
  private suspend fun writeBytecodeSafely(
    key: JsBytecodeCacheKey,
    bytecode: ByteArray,
  ) {
    try {
      bytecodeCache.writeBytecode(key = key, bytecode = bytecode)
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      cacheErrorHandler.onError(key, throwable)
    }
  }

  /**
   * 删除准备阶段确认可疑的缓存；删除失败只上报，不阻止源码 Runtime 继续重建。
   */
  private suspend fun removeBytecodeSafely(key: JsBytecodeCacheKey) {
    try {
      bytecodeCache.removeBytecode(key)
    } catch (throwable: Throwable) {
      if (throwable is CancellationException) throw throwable
      cacheErrorHandler.onError(key, throwable)
    }
  }

  /**
   * 在 QuickJS 操作结束后持久化入口和依赖 Module 的新字节码。
   *
   * 该方法不在 Module Loader 回调中执行，因此磁盘或数据库写入不会占用 QuickJS 引擎锁。
   */
  private suspend fun persistCompiledBytecodes(prepared: PreparedJsExecution) {
    persistCompiledBytecodes(
      entryCacheKey = prepared.entryCacheKey,
      compiledEntryBytecode = prepared.compiledEntryBytecode,
      dependencyCacheKeys = prepared.dependencyCacheKeys,
      compiledDependencies = prepared.moduleState.compiledBytecodes(),
    )
  }

  /** 持久化一次准备或执行批次已经成功产出的字节码。 */
  private suspend fun persistCompiledBytecodes(
    entryCacheKey: JsBytecodeCacheKey,
    compiledEntryBytecode: ByteArray?,
    dependencyCacheKeys: Map<String, JsBytecodeCacheKey>,
    compiledDependencies: Map<String, ByteArray>,
  ) {
    compiledEntryBytecode?.let { bytecode ->
      writeBytecodeSafely(key = entryCacheKey, bytecode = bytecode)
    }
    compiledDependencies.forEach { (name, bytecode) ->
      dependencyCacheKeys[name]?.let { cacheKey ->
        writeBytecodeSafely(key = cacheKey, bytecode = bytecode)
      }
    }
  }

  companion object {
    private const val BYTECODE_CACHE_FORMAT_VERSION = 1
    private const val RUNTIME_ADAPTER_VERSION = 2

    /**
     * 组合源码包依赖与共享 Bundle Module，并记录各自稳定所有者。
     *
     * 策略校验已在调用前保证二者不存在同名依赖；这里仍让业务源码最后写入，避免未来校验规则
     * 调整时静默改变既有覆盖顺序。
     */
    private fun createDependencyDefinitions(
      sourcePackage: JsSourcePackage,
      environment: JsExecutionEnvironment,
    ): Map<String, JsModuleDefinition> {
      val definitions = linkedMapOf<String, JsModuleDefinition>()
      environment.bundle.modules.forEach { (name, source) ->
        definitions[name] = JsModuleDefinition(
          name = name,
          source = source,
          ownerId = bundleOwnerId(environment.bundle.id),
        )
      }
      sourcePackage.files.forEach { (name, source) ->
        if (name != sourcePackage.entry) {
          definitions[name] = JsModuleDefinition(
            name = name,
            source = source,
            ownerId = packageOwnerId(sourcePackage.packageId),
          )
        }
      }
      return definitions
    }

    /**
     * 根据单个 Module 源码、名称、所有者、引擎和宿主环境生成缓存键。
     *
     * 源码包业务版本不参与缓存键，因此同一 packageId 的新版本可以复用源码未变化的 Module。
     * Bundle 版本和策略仍参与隔离，宿主环境升级时不会误用旧环境产生的缓存。
     */
    private fun createModuleCacheKey(
      definition: JsModuleDefinition,
      mode: JsProgramMode,
      environment: JsExecutionEnvironment,
      engineVersion: String,
    ): JsBytecodeCacheKey {
      val buffer = Buffer()
      buffer.writeInt(BYTECODE_CACHE_FORMAT_VERSION)
      buffer.writeInt(RUNTIME_ADAPTER_VERSION)
      buffer.writeStableString(definition.ownerId)
      buffer.writeStableString(definition.name)
      buffer.writeStableString(mode.name)
      buffer.writeStableString(
        definition.source.encodeToByteArray().toByteString().sha256().hex(),
      )
      buffer.writeStableString(engineVersion)
      buffer.writeStableString(environment.policy.id)
      buffer.writeStableString(environment.policy.scene.name)
      buffer.writeStableString(environment.bundle.id)
      buffer.writeInt(environment.bundle.version)
      buffer.writeInt(environment.bundle.hostApiVersion)
      val digest = buffer.readByteArray().toByteString().sha256().hex()
      return JsBytecodeCacheKey(digest)
    }

    private fun packageOwnerId(packageId: String): String = "package:$packageId"

    private fun bundleOwnerId(bundleId: String): String = "bundle:$bundleId"

    /** 向缓存键缓冲区写入带长度的 UTF-8 字符串。 */
    private fun Buffer.writeStableString(value: String) {
      val bytes = value.encodeToByteArray()
      writeInt(bytes.size)
      write(bytes)
    }
  }
}

/**
 * 已完成入口编译和静态依赖准备、等待执行顶层代码的 Runtime。
 */
private class PreparedJsExecution(
  val runtime: QuickJsRuntime,
  val entryBytecode: ByteArray,
  val entryName: String,
  val entryCacheKey: JsBytecodeCacheKey,
  val entryUsedCache: Boolean,
  val compiledEntryBytecode: ByteArray?,
  val dependencyCacheKeys: Map<String, JsBytecodeCacheKey>,
  val moduleState: JsModuleCacheState,
) {

  val origin: JsExecutableOrigin
    get() = if (entryUsedCache) {
      JsExecutableOrigin.BYTECODE_CACHE
    } else {
      JsExecutableOrigin.COMPILED_SOURCE
    }

  /** 返回本次从源码新编译的入口与实际依赖 Module。 */
  fun compiledModuleNames(): Set<String> = buildSet {
    if (compiledEntryBytecode != null) add(entryName)
    addAll(moduleState.compiledBytecodes().keys)
  }

  /** 返回本次实际交给 QuickJS 使用的持久化缓存入口与依赖 Module。 */
  fun cachedModuleNames(): Set<String> = buildSet {
    if (entryUsedCache) add(entryName)
    addAll(moduleState.usedCachedModuleNames())
  }
}
