package com.cyxbs.functions.code.npm.js.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * npm JavaScript Service 与 Host Bridge 统一使用的结果类型。
 *
 * 协议接口不直接暴露 Kotlin [Result]，避免语言内建类型的特殊表示渗透到 KSP 生成代码和跨
 * Runtime 协议。调用方可通过 [toResult] 转为普通业务结果，也可使用 [Result.toNpmJsResult]
 * 把已有实现接入动态协议。
 *
 * 本类型本身不会直接序列化；跨 Runtime 时由 [NpmJsResultEnvelope] 只传输成功值或受限失败
 * 消息。失败在本地进程内保留原 [Throwable]，跨 Runtime 后则由调用方向的稳定异常重新构造。
 */
class NpmJsResult<out T> private constructor(
  private val content: Any?,
) {

  /** 当前结果是否成功；成功值允许为 `null`。 */
  val isSuccess: Boolean
    get() = content !is Failure

  /** 当前结果是否失败。 */
  val isFailure: Boolean
    get() = content is Failure

  /** 成功时返回值，失败时返回 `null`。 */
  @Suppress("UNCHECKED_CAST")
  fun getOrNull(): T? = if (content is Failure) null else content as T

  /** 失败时返回异常，成功时返回 `null`。 */
  fun exceptionOrNull(): Throwable? = (content as? Failure)?.throwable

  /** 成功时返回值，失败时抛出保存的异常。 */
  @Suppress("UNCHECKED_CAST")
  fun getOrThrow(): T {
    val failure = content as? Failure
    if (failure != null) throw failure.throwable
    return content as T
  }

  /** 成功时转换值；失败保持原异常。 */
  fun <R> map(transform: (T) -> R): NpmJsResult<R> = if (isSuccess) {
    success(transform(getOrThrow()))
  } else {
    @Suppress("UNCHECKED_CAST")
    this as NpmJsResult<R>
  }

  /**
   * 成功时转换值并捕获转换异常。
   *
   * [CancellationException] 始终继续抛出，普通异常转换为失败。
   */
  fun <R> mapCatching(transform: (T) -> R): NpmJsResult<R> = if (isFailure) {
    @Suppress("UNCHECKED_CAST")
    this as NpmJsResult<R>
  } else {
    try {
      success(transform(getOrThrow()))
    } catch (exception: CancellationException) {
      throw exception
    } catch (throwable: Throwable) {
      failure(throwable)
    }
  }

  /** 分别处理成功值或失败异常。 */
  fun <R> fold(
    onSuccess: (T) -> R,
    onFailure: (Throwable) -> R,
  ): R {
    val failure = exceptionOrNull()
    return if (failure == null) {
      onSuccess(getOrThrow())
    } else {
      // 不能使用 Elvis 合并两条分支，否则失败回调合法返回 null 时会误入成功分支。
      onFailure(failure)
    }
  }

  /** 成功时执行 [action]，并返回当前结果以便链式调用。 */
  fun onSuccess(action: (T) -> Unit): NpmJsResult<T> {
    if (isSuccess) action(getOrThrow())
    return this
  }

  /** 失败时执行 [action]，并返回当前结果以便链式调用。 */
  fun onFailure(action: (Throwable) -> Unit): NpmJsResult<T> {
    exceptionOrNull()?.let(action)
    return this
  }

  /** 转为 Kotlin [Result]，供不需要感知 npm 协议的业务层使用。 */
  fun toResult(): Result<T> = if (isSuccess) {
    Result.success(getOrThrow())
  } else {
    Result.failure(requireNotNull(exceptionOrNull()))
  }

  override fun toString(): String = if (isSuccess) {
    "NpmJsResult.success(${getOrNull()})"
  } else {
    "NpmJsResult.failure(${exceptionOrNull()})"
  }

  companion object {

    /** 创建成功结果。 */
    fun <T> success(value: T): NpmJsResult<T> = NpmJsResult(value)

    /**
     * 创建失败结果。
     *
     * 取消不能成为协议业务失败，因此传入 [CancellationException] 时会直接重新抛出。
     */
    fun <T> failure(throwable: Throwable): NpmJsResult<T> {
      if (throwable is CancellationException) throw throwable
      return NpmJsResult(Failure(throwable))
    }

    /** 从 Kotlin [Result] 创建协议结果。 */
    fun <T> fromResult(result: Result<T>): NpmJsResult<T> = result.fold(
      onSuccess = ::success,
      onFailure = ::failure,
    )
  }

  /** 与成功 `null` 区分的内部失败标记。 */
  private class Failure(val throwable: Throwable)
}

/** 把 Kotlin [Result] 转为 npm JavaScript 协议结果。 */
fun <T> Result<T>.toNpmJsResult(): NpmJsResult<T> = NpmJsResult.fromResult(this)

/**
 * Service 与 Host Bridge 共用的跨 Runtime 结果信封。
 *
 * [NpmJsResult] 和任意 [Throwable] 都不会直接序列化，因此边界上只传输成功值 JSON 或失败
 * 消息。本协议不兼容旧版直接返回业务 JSON 的格式，宿主与动态包必须使用同一套结果信封。
 * [resultJson] 忽略未知字段只用于同一协议基线下的字段扩展，不承担旧协议兼容。
 */
@Serializable
data class NpmJsResultEnvelope(
  val isSuccess: Boolean,
  val value: JsonElement? = null,
  val failureMessage: String? = null,
)

/**
 * 捕获一次普通跨 Runtime 调用。
 *
 * [CancellationException] 始终继续抛出以保持结构化并发语义，其他异常转为 [NpmJsResult.failure]。
 */
suspend fun <T> npmJsCatching(block: suspend () -> T): NpmJsResult<T> {
  return try {
    NpmJsResult.success(block())
  } catch (exception: CancellationException) {
    throw exception
  } catch (throwable: Throwable) {
    NpmJsResult.failure(throwable)
  }
}

/**
 * 捕获一次本身已经返回 [NpmJsResult] 的跨 Runtime 调用，避免产生嵌套结果。
 *
 * 实现错误抛出的异常和显式返回的失败都会收敛为同一个 [NpmJsResult]；取消仍然继续传播。
 * [exceptionMapper] 只转换从 [block] 抛出的边界异常，不会改变 [block] 显式返回的业务失败。
 */
suspend fun <T> npmJsResultCatching(
  exceptionMapper: (Throwable) -> Throwable = { it },
  block: suspend () -> NpmJsResult<T>,
): NpmJsResult<T> {
  return try {
    block()
  } catch (exception: CancellationException) {
    throw exception
  } catch (throwable: Throwable) {
    val mapped = try {
      exceptionMapper(throwable)
    } catch (exception: CancellationException) {
      throw exception
    } catch (mapperFailure: Throwable) {
      mapperFailure
    }
    NpmJsResult.failure(mapped)
  }
}

/**
 * 执行动态协议实现并把调用、参数解码和成功值编码异常统一收敛到失败信封。
 *
 * [block] 可以显式返回失败；也可以抛出普通异常。成功值编码失败时会改为编码失败信封，避免
 * 序列化异常越过 Runtime 边界。只有 [CancellationException] 会继续抛出。
 */
suspend fun <T> encodeNpmJsResultCatching(
  encodeValue: (T) -> JsonElement,
  block: suspend () -> NpmJsResult<T>,
): String {
  val result = npmJsResultCatching(block = block)
  return try {
    encodeNpmJsResult(result, encodeValue)
  } catch (exception: CancellationException) {
    throw exception
  } catch (throwable: Throwable) {
    encodeNpmJsResult(NpmJsResult.failure(throwable), encodeValue)
  }
}

/**
 * 将协议结果编码为稳定信封。
 *
 * 失败消息最多保留 [MAX_FAILURE_MESSAGE_LENGTH] 个字符，避免动态实现把超大异常文本带过桥。
 */
fun <T> encodeNpmJsResult(
  result: NpmJsResult<T>,
  encodeValue: (T) -> JsonElement,
): String {
  val throwable = result.exceptionOrNull()
  if (throwable is CancellationException) throw throwable
  val envelope = if (throwable == null) {
    NpmJsResultEnvelope(
      isSuccess = true,
      value = encodeValue(result.getOrThrow()),
    )
  } else {
    NpmJsResultEnvelope(
      isSuccess = false,
      failureMessage = throwable.message
        ?.take(MAX_FAILURE_MESSAGE_LENGTH)
        ?: DEFAULT_FAILURE_MESSAGE,
    )
  }
  return resultJson.encodeToString(envelope)
}

/**
 * 解码稳定信封并还原为本方向的 [NpmJsResult]。
 *
 * @param failureFactory 根据远端失败消息创建 Service 或 Host Bridge 的稳定异常。
 * @throws IllegalArgumentException 信封同时缺失成功值和失败信息，或字段组合不符合协议。
 */
fun <T> decodeNpmJsResult(
  envelopeJson: String,
  decodeValue: (JsonElement) -> T,
  failureFactory: (String) -> Throwable,
): NpmJsResult<T> {
  val envelope = resultJson.decodeFromString<NpmJsResultEnvelope>(envelopeJson)
  return if (envelope.isSuccess) {
    require(envelope.failureMessage == null) {
      "Successful npm JavaScript Result must not contain a failure message."
    }
    val value = requireNotNull(envelope.value) {
      "Successful npm JavaScript Result must contain a value."
    }
    NpmJsResult.success(decodeValue(value))
  } else {
    require(envelope.value == null) {
      "Failed npm JavaScript Result must not contain a value."
    }
    val failureMessage = requireNotNull(envelope.failureMessage) {
      "Failed npm JavaScript Result must contain a failure message."
    }
    NpmJsResult.failure(
      failureFactory(failureMessage),
    )
  }
}

/** 结果信封自身使用的前向兼容 JSON 配置。 */
@OptIn(ExperimentalSerializationApi::class)
private val resultJson = Json {
  ignoreUnknownKeys = true
  encodeDefaults = false
  explicitNulls = true
}

private const val MAX_FAILURE_MESSAGE_LENGTH = 4_096
private const val DEFAULT_FAILURE_MESSAGE = "Remote npm JavaScript call failed."
