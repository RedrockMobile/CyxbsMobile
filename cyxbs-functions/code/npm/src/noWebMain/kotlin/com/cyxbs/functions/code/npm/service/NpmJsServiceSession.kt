package com.cyxbs.functions.code.npm.service

import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.npm.pool.NpmPreparedEntryLease
import com.cyxbs.functions.code.npm.api.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.api.bridge.NpmJsServiceProtocolException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive

/**
 * 供 KSP 生成代理使用的一次 npm JavaScript Service 会话。
 *
 * 会话串行化同一 Runtime 上的初始化、调用与关闭，业务不应直接创建或持有该类型。每次方法调用
 * 通过 JSON 字符串跨越 Kotlin/JavaScript 边界，避免公开具体 JavaScript 引擎对象。
 */
class NpmJsServiceSession internal constructor(
  private val runtime: JsRuntime,
  private val lease: NpmPreparedEntryLease,
  private val serviceId: String,
  private val schemaHash: String,
) {
  private val mutex = Mutex()
  private var initialized = false
  private var closed = false
  private var nextCallId = 0L

  /**
   * 执行生成代理指定的方法。
   *
   * @param method KSP 已校验且写入协议的方法名。
   * @param argumentsJson 按声明顺序编码的 JSON 数组。
   * @return JavaScript 分发器返回的 JSON 文本。
   * @throws NpmJsServiceProtocolException 会话尚未初始化或已经关闭。
   * @throws NpmJsServiceInvocationException JavaScript 方法执行或结果转换失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmJsServiceProtocolException::class,
    NpmJsServiceInvocationException::class,
    CancellationException::class,
  )
  suspend fun invoke(method: String, argumentsJson: String): String {
    return mutex.withLock {
      checkUsable()
      invokeLocked(method, argumentsJson)
    }
  }

  /** 执行 npm 入口并确认端上接口与下发实现使用相同的协议摘要。 */
  internal suspend fun initialize(entrySource: String, entryModuleName: String) {
    mutex.withLock {
      if (closed) {
        throw NpmJsServiceProtocolException(
          "Npm JavaScript Service '$serviceId' session is already closed.",
        )
      }
      if (initialized) return@withLock
      evaluate(
        code = entrySource,
        filename = entryModuleName,
        asModule = true,
        action = "initialize",
      )
      val actualSchema = evaluate(
        code = "globalThis.CyxbsNpmJsService?.describe?.(${jsString(serviceId)}) ?? null",
        filename = "__cyxbs_npm_service_describe__.js",
        asModule = false,
        action = "describe",
      ) as? String
      if (actualSchema != schemaHash) {
        throw NpmJsServiceProtocolException(
          "Npm JavaScript Service '$serviceId' schema mismatch: " +
              "expected '$schemaHash', actual '${actualSchema ?: "missing"}'.",
        )
      }
      initialized = true
    }
  }

  /**
   * 关闭远端 Service、Runtime 并释放入口租约。
   *
   * 三层清理都会尝试执行；首个异常作为主异常抛出，后续异常附加为 suppressed。重复调用安全。
   *
   * @throws NpmJsServiceInvocationException 远端关闭或 Runtime 关闭失败。
   * @throws CancellationException 远端关闭或释放租约时协程被取消；本方法仍会先尝试完成剩余清理。
   */
  @Throws(NpmJsServiceInvocationException::class, CancellationException::class)
  suspend fun close() {
    mutex.withLock {
      if (closed) return@withLock
      closed = true
      var failure: Throwable? = null
      if (initialized) {
        try {
          invokeLocked(CLOSE_METHOD, EMPTY_ARGUMENTS)
        } catch (throwable: Throwable) {
          failure = throwable
        }
      }
      try {
        runtime.close()
      } catch (throwable: Throwable) {
        failure = failure.append(throwable)
      }
      try {
        lease.release()
      } catch (throwable: Throwable) {
        failure = failure.append(throwable)
      }
      failure?.let { throw it }
    }
  }

  /** 必须在 [mutex] 内调用，使用顶层 await 等待 Kotlin/JS suspend 实现完成。 */
  private suspend fun invokeLocked(method: String, argumentsJson: String): String {
    val resultKey = "__cyxbs_npm_service_result_${nextCallId++}"
    val resultKeyLiteral = jsString(resultKey)
    return try {
      evaluate(
        code = "globalThis[$resultKeyLiteral] = await " +
          "globalThis.CyxbsNpmJsService.invoke(" +
          "${jsString(serviceId)}, ${jsString(method)}, ${jsString(argumentsJson)});",
        filename = "__cyxbs_npm_service_call__.mjs",
        asModule = true,
        action = "invoke '$method'",
      )
      evaluate(
        code = "globalThis[$resultKeyLiteral]",
        filename = "__cyxbs_npm_service_result__.js",
        asModule = false,
        action = "read '$method' result",
      ) as? String ?: throw NpmJsServiceInvocationException(
        "Npm JavaScript Service '$serviceId' method '$method' did not return JSON text.",
      )
    } finally {
      if (!runtime.isClosed) {
        try {
          runtime.evaluateValue(
            code = "delete globalThis[$resultKeyLiteral]",
            filename = "__cyxbs_npm_service_cleanup__.js",
            asModule = false,
          )
        } catch (_: Throwable) {
          // 清理临时全局变量失败不覆盖真实调用异常；Runtime 最终会在 Service 关闭时整体释放。
        }
      }
    }
  }

  /** 将引擎异常收敛为 npm Service 稳定异常，不向业务暴露具体实现。 */
  private suspend fun evaluate(
    code: String,
    filename: String,
    asModule: Boolean,
    action: String,
  ): Any? {
    return try {
      runtime.evaluateValue(code, filename, asModule)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: JsRuntimeException) {
      throw NpmJsServiceInvocationException(
        "Failed to $action npm JavaScript Service '$serviceId'.",
        exception,
      )
    }
  }

  /** 检查生成代理是否仍可安全调用当前 Runtime。 */
  private fun checkUsable() {
    if (!initialized) {
      throw NpmJsServiceProtocolException(
        "Npm JavaScript Service '$serviceId' session is not initialized.",
      )
    }
    if (closed || runtime.isClosed) {
      throw NpmJsServiceProtocolException(
        "Npm JavaScript Service '$serviceId' session is already closed.",
      )
    }
  }

  private companion object {
    const val CLOSE_METHOD = "\$close"
    const val EMPTY_ARGUMENTS = "[]"

    /** 使用 JSON 字符串规则生成可安全嵌入脚本的字面量。 */
    fun jsString(value: String): String = JsonPrimitive(value).toString()

    /** 聚合关闭阶段异常并保留发生顺序。 */
    fun Throwable?.append(next: Throwable): Throwable {
      if (this == null) return next
      addSuppressed(next)
      return this
    }
  }
}
