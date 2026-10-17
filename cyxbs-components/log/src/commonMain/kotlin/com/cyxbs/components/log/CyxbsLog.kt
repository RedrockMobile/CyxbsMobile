package com.cyxbs.components.log

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import com.cyxbs.components.log.storage.FileLogEvent
import com.cyxbs.components.log.storage.PlatformFileLog
import com.cyxbs.components.log.storage.PreparedLogContext
import com.cyxbs.components.log.storage.sanitizeForFileLog
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/**
 * 掌邮日志入口，当前通过 Kermit 同步输出到平台控制台。
 *
 * 每次调用使用同一份配置快照，先过滤再求值一次消息；不采集调用栈。
 * [configure] 可重复调用，原子替换配置，不修改 Kermit 全局 Logger。
 * 消息构造或平台 writer 失败会被日志边界吞掉，不影响业务控制流。
 */
object CyxbsLog {
  private class State(val config: LogConfig) {
    val logger = Logger(
      config = loggerConfigInit(platformLogWriter(), minSeverity = config.minLevel.toSeverity()),
      tag = config.defaultTag,
    )
  }

  private val state = atomic(State(LogConfig()))
  private val fileStorageCollectionEnabled = atomic(true)
  private val collectionGate = SynchronizedObject()
  private var collectionEpoch = 0L

  /**
   * 设置当前进程的日志策略，建议在应用启动时、业务初始化之前调用。
   * 可重复调用以原子替换配置；正在输出的日志仍使用其开始时取得的配置快照。
   * 未调用时使用 [LogConfig] 的默认配置；本方法不会自动判断 Debug/Release。
   */
  fun configure(config: LogConfig) {
    state.value = State(config)
  }

  /**
   * 查询当前配置是否允许输出 [level]，用于在日志调用前跳过额外的昂贵诊断工作。
   * 普通消息拼接直接放进日志 lambda 即可，无需先查询；查询结果不保证后续配置不变。
   */
  fun isEnabled(level: LogLevel): Boolean {
    val config = state.value.config
    val fileAccepted = fileStorageCollectionEnabled.value && PlatformFileLog.accepts(level)
    return config.enabled && (level >= config.minLevel || fileAccepted)
  }

  /**
   * 按指定 [level] 输出日志，通常优先使用对应等级的快捷方法。
   *
   * TAG 约定：
   * - 定位单个文件时，推荐使用不带 `.kt` 的文件名，如 `ScheduleRepository`。
   * - 追踪跨文件业务链路时，统一使用语义明确的 `XXX_TAG` 常量，
   *   如 `SCHEDULE_SYNC_TAG = "ScheduleSync"`，尤其适合用 [v]/[d] 串联各阶段。
   * - 同一链路的不同等级复用同一 TAG；TAG 保持稳定，不拼接用户信息或请求 ID。
   *   区分并发请求时可在消息中加入非敏感的关联 ID，TAG 本身不提供自动链路追踪。
   *
   * @param tag 文件名或约定的链路 TAG；null 时使用配置的 defaultTag，不会自动推断文件名。
   * @param throwable 需要记录堆栈的原始异常，可用于任意等级；无需在消息中重复拼接堆栈。
   * @param message 通过等级过滤后同步求值一次；仅构造诊断文本，避免业务副作用，
   * 不记录 token、密码及未脱敏的个人数据。构造异常会被隔离且本条日志被丢弃。
   */
  fun log(
    level: LogLevel,
    tag: String? = null,
    throwable: Throwable? = null,
    message: () -> String,
  ) {
    logInternal(level, tag, throwable, context = null, message)
  }

  /**
   * 输出带固定白名单结构化上下文的日志。旧文本 API 保持不变；仅在确有诊断价值时填写 [context]。
   */
  fun log(
    level: LogLevel,
    tag: String? = null,
    throwable: Throwable? = null,
    context: LogContext,
    message: () -> String,
  ) {
    logInternal(level, tag, throwable, context, message)
  }

  private fun logInternal(
    level: LogLevel,
    tag: String?,
    throwable: Throwable?,
    context: LogContext?,
    message: () -> String,
    throwableRendering: ThrowableRendering = ThrowableRendering.Full,
  ) {
    val snapshot = state.value
    if (!snapshot.config.enabled) return

    val consoleAccepted = level >= snapshot.config.minLevel
    val (epoch, admission) = synchronized(collectionGate) {
      collectionEpoch to if (fileStorageCollectionEnabled.value) PlatformFileLog.admission(level) else null
    }
    if (!consoleAccepted && admission == null) return

    val resolvedTag = tag ?: snapshot.config.defaultTag
    val renderedMessage = runCatching(message).getOrNull() ?: return
    val payload = runCatching {
      prepareLogPayload(resolvedTag, renderedMessage, throwable, throwableRendering)
    }.getOrNull() ?: return
    val preparedContext = context?.prepareForLog()
    val observedTimestampMillis = Clock.System.now().toEpochMilliseconds()

    if (admission != null) {
      val event = FileLogEvent(
        eventTimestampMillis = context?.eventTimestampMillis
          ?.takeIf { it > 0 }
          ?: observedTimestampMillis,
        observedTimestampMillis = observedTimestampMillis,
        level = level,
        tag = payload.tag,
        message = payload.message,
        stackTrace = payload.stackTrace,
        context = preparedContext?.value,
        snapshotTruncated = payload.truncated || preparedContext?.truncated == true,
      )
      synchronized(collectionGate) {
        if (fileStorageCollectionEnabled.value && collectionEpoch == epoch) admission.offer(event)
      }
    }
    if (consoleAccepted) {
      val consoleMessage = buildConsoleMessage(payload.message, payload.stackTrace, preparedContext?.value)
      runCatching {
        when (level) {
          LogLevel.Verbose -> snapshot.logger.v(consoleMessage, null, payload.tag)
          LogLevel.Debug -> snapshot.logger.d(consoleMessage, null, payload.tag)
          LogLevel.Info -> snapshot.logger.i(consoleMessage, null, payload.tag)
          LogLevel.Warn -> snapshot.logger.w(consoleMessage, null, payload.tag)
          LogLevel.Error -> snapshot.logger.e(consoleMessage, null, payload.tag)
          LogLevel.Assert -> snapshot.logger.a(consoleMessage, null, payload.tag)
        }
      }
    }
  }

  /**
   * 同步控制后续日志是否允许进入文件层，不影响控制台，也不会自行启动或关闭 worker。
   * 宿主在隐私撤回和用户关闭采集时应先设为 false，再异步调用 [closeFileStorage] 排空旧记录。
   */
  fun setFileStorageCollectionEnabled(enabled: Boolean) {
    synchronized(collectionGate) {
      if (fileStorageCollectionEnabled.value != enabled) collectionEpoch += 1
      fileStorageCollectionEnabled.value = enabled
    }
  }

  /**
   * 启动或替换当前进程的文件日志引擎。
   *
   * [FileLogConfig.directory] 必须由当前进程独占。替换已有引擎时会先完成其 close；
   * 返回 Success 后新引擎已经完成目录检查并可接收日志。
   */
  suspend fun startFileStorage(config: FileLogConfig): LogStorageResult =
    PlatformFileLog.start(config)

  /**
   * 等待调用前已经成功入队的记录写入并刷新到操作系统。
   * 这不是断电持久化保证，也不包含此前因队列或预算被丢弃的记录。
   */
  suspend fun flushFileStorage(
    mode: LogFlushMode = LogFlushMode.Buffered,
  ): LogFlushResult = PlatformFileLog.flush(mode)

  /**
   * 有序清理本模块管理的日志。清理期间的新记录会被拒绝并计入 droppedClosed。
   */
  suspend fun clearFileStorage(): LogStorageResult = PlatformFileLog.clear()

  /**
   * 刷新并封存当前文件，然后在启动配置的受控导出目录生成可独立读取的 JSONL 副本。
   * 应用层负责将返回文件交给系统分享或文件保存界面。
   */
  suspend fun exportFileStorage(): LogExportResult = PlatformFileLog.export()

  /** 处理已接收记录、刷新并释放 worker；重复调用是安全的。 */
  suspend fun closeFileStorage(): LogStorageResult = PlatformFileLog.close()

  /** 返回无阻塞的进程内统计快照。 */
  fun fileStorageHealth(): FileLogHealth = PlatformFileLog.health()

  /**
   * Verbose：细粒度执行轨迹，如状态流变化、循环中的处理步骤；排查细节时开启，控制调用频率。
   * 跨文件追踪时使用统一的 `XXX_TAG` 常量，并在消息中注明当前阶段。
   * 参数及 TAG 命名遵循 [log] 的约定。
   */
  fun v(tag: String? = null, throwable: Throwable? = null, message: () -> String) =
    log(LogLevel.Verbose, tag, throwable, message)

  /**
   * Debug：开发排错信息，如缓存命中、分支选择、请求开始与结束。
   * 单文件定位推荐以文件名为 TAG；追踪业务链路时复用该链路的 `XXX_TAG` 常量。
   * 参数及 TAG 命名遵循 [log] 的约定。
   */
  fun d(tag: String? = null, throwable: Throwable? = null, message: () -> String) =
    log(LogLevel.Debug, tag, throwable, message)

  /**
   * Info：正常且有意义的业务或系统事件，如课表同步完成、初始化阶段完成。
   * 优先记录关键结果，避免将频繁执行的细节全部记为 Info。
   * 参数及 TAG 命名遵循 [log] 的约定。
   */
  fun i(tag: String? = null, throwable: Throwable? = null, message: () -> String) =
    log(LogLevel.Info, tag, throwable, message)

  /**
   * Warn：出现异常情况，但仍能恢复或降级，如网络失败后使用缓存、跳过不支持的数据。
   * 消息说明异常及采取的恢复措施；需要定位原因时通过 throwable 传入原始异常。
   * 参数及 TAG 命名遵循 [log] 的约定。
   */
  fun w(tag: String? = null, throwable: Throwable? = null, message: () -> String) =
    log(LogLevel.Warn, tag, throwable, message)

  /**
   * Error：操作最终失败，需要定位原因，如解析失败、文件写入失败、提交失败。
   * 消息说明失败的操作及必要上下文，原始异常通过 throwable 传入；本方法只记录，不负责恢复。
   * 参数及 TAG 命名遵循 [log] 的约定。
   */
  fun e(tag: String? = null, throwable: Throwable? = null, message: () -> String) =
    log(LogLevel.Error, tag, throwable, message)

  /**
   * Error 紧急路径：仍接收原始 [throwable]，但只记录有界类型与消息摘要，不遍历堆栈、cause 或 suppressed。
   * 仅用于未捕获异常等必须限制同步处理成本的场景；普通错误继续使用 [e] 保留堆栈。
   */
  fun eBounded(tag: String? = null, throwable: Throwable, message: () -> String) =
    logInternal(
      level = LogLevel.Error,
      tag = tag,
      throwable = throwable,
      context = null,
      message = message,
      throwableRendering = ThrowableRendering.Summary,
    )

  /**
   * Assert：内部不变量被破坏、按设计不应发生的状态；不用于普通网络或业务失败。
   * 此方法仅输出 Assert 等级日志，不主动抛异常或终止程序；需要阻止继续执行时使用 check/require。
   * 参数及 TAG 命名遵循 [log] 的约定。
   */
  fun a(tag: String? = null, throwable: Throwable? = null, message: () -> String) =
    log(LogLevel.Assert, tag, throwable, message)
}

private const val MAX_RAW_FILE_SNAPSHOT_BYTES = 64 * 1_024
private const val MAX_FILE_SNAPSHOT_CHARS = 16 * 1_024
private const val MAX_CONTEXT_TEXT_CHARS = 128
private const val MAX_ROUTE_CHARS = 256
private const val MAX_CORRELATION_ID_CHARS = 64
private val safeCorrelationId = Regex("^[A-Za-z0-9._:-]{1,64}$")

internal data class FileTextSnapshot(val value: String, val truncated: Boolean)

/**
 * 在任何正则脱敏前按 UTF-8 字节数截取有界前缀，不编码完整原文，也不会拆开代理对。
 */
internal fun String.takeRawFileSnapshot(): FileTextSnapshot {
  var index = 0
  var bytes = 0
  while (index < length) {
    val current = this[index]
    val charBytes: Int
    val charCount: Int
    when {
      current.code <= 0x7F -> {
        charBytes = 1
        charCount = 1
      }
      current.code <= 0x7FF -> {
        charBytes = 2
        charCount = 1
      }
      current.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate() -> {
        charBytes = 4
        charCount = 2
      }
      else -> {
        charBytes = 3
        charCount = 1
      }
    }
    if (bytes + charBytes > MAX_RAW_FILE_SNAPSHOT_BYTES) break
    bytes += charBytes
    index += charCount
  }
  return if (index == length) {
    FileTextSnapshot(this, truncated = false)
  } else {
    FileTextSnapshot(substring(0, index), truncated = true)
  }
}

private fun String.takeFileSnapshot(): String {
  if (length <= MAX_FILE_SNAPSHOT_CHARS) return this
  return substring(0, safeSnapshotEnd(MAX_FILE_SNAPSHOT_CHARS))
}

private fun String.safeSnapshotEnd(limit: Int): Int =
  if (this[limit - 1].isHighSurrogate()) limit - 1 else limit

internal enum class ThrowableRendering {
  Full,
  Summary,
}

internal data class PreparedLogPayload(
  val tag: String,
  val message: String,
  val stackTrace: String?,
  val truncated: Boolean,
)

/** 在进入任意 sink 前完成 UTF-8 有界快照与脱敏。 */
internal fun prepareLogPayload(
  tag: String,
  message: String,
  throwable: Throwable?,
  throwableRendering: ThrowableRendering = ThrowableRendering.Full,
): PreparedLogPayload {
  val rawMessage = message.takeRawFileSnapshot()
  val sanitizedMessage = rawMessage.value.sanitizeForFileLog()
  val boundedMessage = sanitizedMessage.takeFileSnapshot()
  val rawStackTrace = throwable?.toLogSnapshot(throwableRendering)
  val sanitizedStackTrace = rawStackTrace?.value?.sanitizeForFileLog()
  val boundedStackTrace = sanitizedStackTrace?.takeFileSnapshot()
  val rawTag = tag.takeRawFileSnapshot()
  val sanitizedTag = rawTag.value.sanitizeForFileLog()
  val boundedTag = sanitizedTag.takeFileSnapshot()
  return PreparedLogPayload(
    tag = boundedTag,
    message = boundedMessage,
    stackTrace = boundedStackTrace,
    truncated = rawMessage.truncated || rawStackTrace?.truncated == true || rawTag.truncated ||
      boundedMessage.length != sanitizedMessage.length ||
      boundedStackTrace?.length != sanitizedStackTrace?.length ||
      boundedTag.length != sanitizedTag.length,
  )
}

private fun Throwable.toLogSnapshot(rendering: ThrowableRendering): FileTextSnapshot = when (rendering) {
  ThrowableRendering.Full -> boundedThrowableSnapshot()
  ThrowableRendering.Summary -> {
    val type = this::class.simpleName ?: "Throwable"
    val detail = runCatching { message }.getOrNull()?.takeRawFileSnapshot()
    val summary = if (detail == null || detail.value.isBlank()) type else "$type: ${detail.value}"
    val bounded = summary.takeRawFileSnapshot()
    FileTextSnapshot(
      value = bounded.value,
      truncated = detail?.truncated == true || bounded.truncated,
    )
  }
}

private data class PreparedContextSnapshot(
  val value: PreparedLogContext,
  val truncated: Boolean,
)

private fun LogContext.prepareForLog(): PreparedContextSnapshot {
  var truncated = false
  fun bounded(value: String?, maxChars: Int): String? {
    if (value == null) return null
    val raw = value.takeRawFileSnapshot()
    val sanitized = raw.value.sanitizeForFileLog()
    val result = if (sanitized.length <= maxChars) sanitized else sanitized.substring(
      0,
      if (sanitized[maxChars - 1].isHighSurrogate()) maxChars - 1 else maxChars,
    )
    truncated = truncated || raw.truncated || result.length != sanitized.length
    return result.takeIf { it.isNotBlank() }
  }

  val routeSnapshot = route?.takeRawFileSnapshot()
  truncated = truncated || routeSnapshot?.truncated == true
  val normalizedRoute = routeSnapshot
    ?.value
    ?.substringBefore('?')
    ?.substringBefore('#')
    ?.let { bounded(it, MAX_ROUTE_CHARS) }
  val safeId = correlationId
    ?.takeIf(safeCorrelationId::matches)
    ?.let { bounded(it, MAX_CORRELATION_ID_CHARS) }
    ?.takeIf(safeCorrelationId::matches)
  return PreparedContextSnapshot(
    value = PreparedLogContext(
      operation = bounded(operation, MAX_CONTEXT_TEXT_CHARS),
      route = normalizedRoute,
      method = method?.name,
      statusCode = statusCode?.takeIf { it in 100..599 },
      durationMillis = durationMillis?.takeIf { it >= 0 },
      result = result?.name,
      retryCount = retryCount?.takeIf { it in 0..1_000 },
      fromState = bounded(fromState, MAX_CONTEXT_TEXT_CHARS),
      toState = bounded(toState, MAX_CONTEXT_TEXT_CHARS),
      correlationId = safeId,
    ),
    truncated = truncated,
  )
}

private fun buildConsoleMessage(
  message: String,
  stackTrace: String?,
  context: PreparedLogContext?,
): String = buildString {
  append(message)
  context?.toConsoleSuffix()?.takeIf { it.isNotEmpty() }?.let { suffix ->
    append(" | ")
    append(suffix)
  }
  if (!stackTrace.isNullOrEmpty()) {
    append('\n')
    append(stackTrace)
  }
}

private fun PreparedLogContext.toConsoleSuffix(): String = buildList {
  operation?.let { add("operation=$it") }
  route?.let { add("route=$it") }
  method?.let { add("method=$it") }
  statusCode?.let { add("status=$it") }
  durationMillis?.let { add("durationMs=$it") }
  result?.let { add("result=$it") }
  retryCount?.let { add("retry=$it") }
  fromState?.let { add("from=$it") }
  toState?.let { add("to=$it") }
  correlationId?.let { add("correlationId=$it") }
}.joinToString(" ")

private fun LogLevel.toSeverity(): Severity = when (this) {
  LogLevel.Verbose -> Severity.Verbose
  LogLevel.Debug -> Severity.Debug
  LogLevel.Info -> Severity.Info
  LogLevel.Warn -> Severity.Warn
  LogLevel.Error -> Severity.Error
  LogLevel.Assert -> Severity.Assert
}
