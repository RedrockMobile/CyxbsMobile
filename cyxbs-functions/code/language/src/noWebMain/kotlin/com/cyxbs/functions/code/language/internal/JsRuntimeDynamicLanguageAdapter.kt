package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.js.runtime.JsModuleLoader
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsRuntimeException
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.create
import com.cyxbs.functions.code.js.runtime.evaluate
import com.cyxbs.functions.code.language.DynamicCompletionItem
import com.cyxbs.functions.code.language.DynamicCompletionResult
import com.cyxbs.functions.code.language.DynamicHighlightSpan
import com.cyxbs.functions.code.language.DynamicLanguageAdapter
import com.cyxbs.functions.code.language.DynamicLanguageExecutionException
import com.cyxbs.functions.code.language.DynamicLanguageMetadata
import com.cyxbs.functions.code.language.DynamicLanguageModuleGraph
import com.cyxbs.functions.code.language.DynamicLanguageProtocolException
import com.cyxbs.functions.code.language.DynamicLanguageRuntimeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 基于长生命周期 JavaScript Runtime 的动态语言协议实现。 */
internal class JsRuntimeDynamicLanguageAdapter private constructor(
  private val runtime: JsRuntime,
  private val dispatcher: CoroutineDispatcher,
  override val metadata: DynamicLanguageMetadata,
) : DynamicLanguageAdapter {
  private val mutex = Mutex()
  private var isClosed = false

  /** 在同一 Runtime 中串行调用动态语言包并校验高亮边界。 */
  override suspend fun highlight(source: String): List<DynamicHighlightSpan> {
    return withRuntimeLock {
      val encodedSource = JSON.encodeToString(source)
      val resultJson = executeRuntimeOperation("highlight source") {
        runtime.evaluate<String>(
          code = "$BRIDGE_PATH.highlight($encodedSource)",
          filename = HIGHLIGHT_CALL_FILENAME,
        )
      }
      val spans = try {
        JSON.decodeFromString<List<DynamicHighlightSpanDto>>(resultJson)
      } catch (exception: SerializationException) {
        throw DynamicLanguageProtocolException(
          message = "Dynamic language package returned invalid highlight data.",
          cause = exception,
        )
      }
      validateHighlightSpans(source = source, spans = spans)
      spans.map { span ->
        DynamicHighlightSpan(
          from = span.from,
          to = span.to,
          styleIds = span.classes.splitToSequence(' ')
            .filter(String::isNotBlank)
            .toList(),
        )
      }
    }
  }

  /** 在同一 Runtime 中串行调用动态语言包并校验补全区间。 */
  override suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    require(position in 0..source.length) {
      "position must be within 0..source.length."
    }
    return withRuntimeLock {
      val encodedSource = JSON.encodeToString(source)
      val resultJson = executeRuntimeOperation("complete source") {
        runtime.evaluate<String>(
          code = "$BRIDGE_PATH.complete($encodedSource, $position, $explicit)",
          filename = COMPLETION_CALL_FILENAME,
        )
      }
      if (resultJson == NULL_JSON) {
        return@withRuntimeLock null
      }
      val result = try {
        JSON.decodeFromString<DynamicCompletionResultDto>(resultJson)
      } catch (exception: SerializationException) {
        throw DynamicLanguageProtocolException(
          message = "Dynamic language package returned invalid completion data.",
          cause = exception,
        )
      }
      validateCompletionResult(source = source, result = result)
      DynamicCompletionResult(
        from = result.from,
        to = result.to,
        options = result.options.map { option ->
          DynamicCompletionItem(
            label = option.label,
            displayLabel = option.displayLabel,
            detail = option.detail,
            info = option.info,
            type = option.type,
            boost = option.boost,
            apply = option.apply,
          )
        },
      )
    }
  }

  /** 等待当前分析批次结束后释放 Runtime，保证不会与 native 调用并发关闭。 */
  override suspend fun close() {
    withContext(dispatcher) {
      mutex.withLock {
        if (!isClosed) {
          executeRuntimeOperation("close runtime") { runtime.close() }
          isClosed = true
        }
      }
    }
  }

  /** 把所有 native 调用约束在同一调度器和互斥区内。 */
  private suspend fun <T> withRuntimeLock(block: suspend () -> T): T {
    return withContext(dispatcher) {
      mutex.withLock {
        check(!isClosed) { "DynamicLanguageAdapter is already closed." }
        block()
      }
    }
  }

  internal companion object {
    private const val BRIDGE_PATH = "globalThis.CyxbsDynamicLanguage"
    private const val METADATA_CALL_FILENAME = "dynamic-language.metadata.js"
    private const val HIGHLIGHT_CALL_FILENAME = "dynamic-language.highlight.js"
    private const val COMPLETION_CALL_FILENAME = "dynamic-language.complete.js"
    private const val NULL_JSON = "null"
    private const val MAX_COMPLETION_OPTIONS = 1_000
    private val LANGUAGE_ID_REGEX = Regex("[A-Za-z0-9._-]{1,64}")
    private val JSON = Json { ignoreUnknownKeys = true }

    /** 创建 Runtime、执行 ESM 入口并验证固定协议对象。 */
    suspend fun create(
      runtimeFactory: JsRuntimeFactory,
      moduleGraph: DynamicLanguageModuleGraph,
      config: DynamicLanguageRuntimeConfig,
      dispatcher: CoroutineDispatcher,
    ): DynamicLanguageAdapter {
      if (moduleGraph.sourceSizeBytes > config.maxSourceBytes) {
        throw DynamicLanguageProtocolException(
          "Dynamic language module graph uses ${moduleGraph.sourceSizeBytes} bytes, " +
            "limit is ${config.maxSourceBytes}.",
        )
      }
      return withContext(dispatcher) {
        // Native Runtime 的创建、首次执行和失败释放保持在同一调度器，避免依赖跨线程初始化行为。
        val runtime = executeRuntimeOperation("create runtime") {
          runtimeFactory.create(
            jobDispatcher = dispatcher,
            config = JsRuntimeConfig(
              memoryLimitBytes = config.memoryLimitBytes,
              maxStackSizeBytes = config.maxStackSizeBytes,
              evaluationTimeoutMillis = config.evaluationTimeoutMillis,
            ),
            // Loader 持锁期间只读取预解析的内存 Map，不触发磁盘或网络访问。
            moduleLoader = JsModuleLoader(moduleGraph.moduleSources::get),
            allowBytecodeCache = true,
          )
        }
        try {
          executeRuntimeOperation("initialize package") {
            runtime.evaluate<Any?>(
              code = moduleGraph.entrySource(),
              filename = moduleGraph.entryModule,
              asModule = true,
            )
          }
          val hasBridge = executeRuntimeOperation("verify package bridge") {
            runtime.evaluate<Boolean>(
              code = "typeof $BRIDGE_PATH === 'object'" +
                " && typeof $BRIDGE_PATH.highlight === 'function'" +
                " && typeof $BRIDGE_PATH.complete === 'function'",
              filename = METADATA_CALL_FILENAME,
            )
          }
          if (!hasBridge) {
            throw DynamicLanguageProtocolException(
              "Dynamic language package does not expose the required bridge object.",
            )
          }
          val metadataJson = executeRuntimeOperation("read package metadata") {
            runtime.evaluate<String>(
              code = "JSON.stringify($BRIDGE_PATH.metadata)",
              filename = METADATA_CALL_FILENAME,
            )
          }
          val metadataDto = try {
            JSON.decodeFromString<DynamicLanguageMetadataDto>(metadataJson)
          } catch (exception: SerializationException) {
            throw DynamicLanguageProtocolException(
              message = "Dynamic language package metadata is invalid.",
              cause = exception,
            )
          }
          val metadata = metadataDto.toValidatedMetadata()
          JsRuntimeDynamicLanguageAdapter(
            runtime = runtime,
            dispatcher = dispatcher,
            metadata = metadata,
          )
        } catch (throwable: Throwable) {
          try {
            executeRuntimeOperation("close failed runtime") { runtime.close() }
          } catch (closeFailure: Throwable) {
            // 初始化失败是调用方真正需要处理的原因；释放失败只补充诊断，不能遮蔽首次异常。
            if (closeFailure !== throwable) throwable.addSuppressed(closeFailure)
          }
          throw throwable
        }
      }
    }

    /** 把 JS 模块的稳定 Runtime 异常转换为编辑器领域异常。 */
    private suspend fun <T> executeRuntimeOperation(
      operation: String,
      block: suspend () -> T,
    ): T {
      return try {
        block()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: JsRuntimeException) {
        throw DynamicLanguageExecutionException(
          message = "Failed to $operation in the dynamic language engine.",
          cause = exception,
        )
      }
    }

    /** 校验协议版本和可用于缓存、展示的稳定元数据。 */
    private fun DynamicLanguageMetadataDto.toValidatedMetadata(): DynamicLanguageMetadata {
      if (protocolVersion != DynamicLanguageAdapter.PROTOCOL_VERSION) {
        throw DynamicLanguageProtocolException(
          "Unsupported dynamic language protocol version: $protocolVersion.",
        )
      }
      if (!LANGUAGE_ID_REGEX.matches(languageId)) {
        throw DynamicLanguageProtocolException("Dynamic language id is invalid.")
      }
      if (displayName.isBlank() || displayName.length > 128) {
        throw DynamicLanguageProtocolException("Dynamic language display name is invalid.")
      }
      return DynamicLanguageMetadata(
        languageId = languageId,
        displayName = displayName,
        protocolVersion = protocolVersion,
      )
    }

    /** 拒绝越界、重叠或无样式的高亮结果，避免污染编辑器 Decoration 状态。 */
    private fun validateHighlightSpans(
      source: String,
      spans: List<DynamicHighlightSpanDto>,
    ) {
      var previousEnd = 0
      spans.forEachIndexed { index, span ->
        if (span.from !in 0..source.length || span.to !in span.from..source.length) {
          throw DynamicLanguageProtocolException(
            "Highlight span at index $index is outside the source range.",
          )
        }
        if (span.from < previousEnd) {
          throw DynamicLanguageProtocolException(
            "Highlight span at index $index overlaps the previous span.",
          )
        }
        if (span.from == span.to || span.classes.isBlank()) {
          throw DynamicLanguageProtocolException(
            "Highlight span at index $index is empty or has no style.",
          )
        }
        previousEnd = span.to
      }
    }

    /** 校验补全结果的替换范围、数量和必要展示字段。 */
    private fun validateCompletionResult(
      source: String,
      result: DynamicCompletionResultDto,
    ) {
      if (result.from !in 0..source.length || result.to !in result.from..source.length) {
        throw DynamicLanguageProtocolException(
          "Completion replacement range is outside the source range.",
        )
      }
      if (result.options.size > MAX_COMPLETION_OPTIONS) {
        throw DynamicLanguageProtocolException(
          "Completion result contains too many options: ${result.options.size}.",
        )
      }
      result.options.forEachIndexed { index, option ->
        if (option.label.isBlank()) {
          throw DynamicLanguageProtocolException(
            "Completion option at index $index has a blank label.",
          )
        }
      }
    }
  }
}
