package com.cyxbs.functions.code.editor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.functions.code.editor.highlight.JavaScriptCodeEditor
import com.cyxbs.functions.code.editor.highlight.rememberJavaScriptCodeEditorState
import com.cyxbs.functions.code.js.diagnostic.toJsDiagnostic
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.teaching.JsTeachingCodeResult
import com.cyxbs.functions.code.js.teaching.JsTeachingCodeRunner
import com.cyxbs.functions.code.language.DynamicLanguageInfo
import com.cyxbs.functions.code.language.DynamicLanguageManager
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.TimeSource

/** 无参数的代码编辑器手动测试页面路由。 */
@Serializable
data object CodeEditorTestNavArgument : AppNavArgument

/**
 * KodeMirror JavaScript 编辑、本地 QuickJS 运行和动态语言服务的手动测试页面。
 *
 * 动态语言验证被拆成加载和调用两步，使测试者可以先观察完整服务加载耗时，再独立检查高亮与
 * 补全结果。该页面只编译进 `noWebMain`，用于 Android、iOS 与 Desktop 的功能体验，不作为
 * 正式教学 UI。
 */
@AppNav(route = "code/editor-test")
class CodeEditorTestNavEntry : AppNavEntry<CodeEditorTestNavArgument>() {

  override fun isNeedLogin(argument: CodeEditorTestNavArgument): Boolean = false

  @Composable
  override fun Content(argument: CodeEditorTestNavArgument) {
    val editorState = rememberJavaScriptCodeEditorState(initialCode = DEFAULT_CODE)
    val runner = remember {
      JsTeachingCodeRunner.create(QuickJsRuntimeFactory)
    }
    val dynamicLanguageManager = remember { DynamicLanguageManager() }
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var isLoadingLanguage by remember { mutableStateOf(false) }
    var isAnalyzingLanguage by remember { mutableStateOf(false) }
    var loadedLanguage by remember { mutableStateOf<DynamicLanguageInfo?>(null) }
    var dynamicLanguageService by remember { mutableStateOf<DynamicLanguageService?>(null) }
    var output by remember { mutableStateOf("点击运行查看控制台输出") }
    var autoHighlightReport by remember { mutableStateOf("加载动态服务后显示实时高亮耗时") }

    // Service 与页面编辑会话同生命周期；即使页面协程被取消，也要完成 Runtime 释放。
    LaunchedEffect(dynamicLanguageService) {
      val service = dynamicLanguageService ?: return@LaunchedEffect
      try {
        awaitCancellation()
      } finally {
        withContext(NonCancellable) {
          service.close()
        }
      }
    }

    // 输入停止后自动刷新动态高亮；collectLatest 取消等待中的旧请求，源码校验阻止迟到结果覆盖新文档。
    LaunchedEffect(dynamicLanguageService, editorState) {
      val service = dynamicLanguageService ?: return@LaunchedEffect
      snapshotFlow { editorState.code }.collectLatest { source ->
        delay(AUTO_HIGHLIGHT_DELAY_MILLIS)
        try {
          val roundTripMark = TimeSource.Monotonic.markNow()
          val result = service.highlight(source)
          val roundTripDuration = roundTripMark.elapsedNow()
          if (editorState.code == source) {
            val applyMark = TimeSource.Monotonic.markNow()
            editorState.applyHighlights(result.spans)
            autoHighlightReport = buildAutoHighlightText(
              metrics = result.metrics,
              highlightCount = result.spans.size,
              roundTripDuration = roundTripDuration,
              applyDuration = applyMark.elapsedNow(),
            )
          }
        } catch (throwable: Throwable) {
          if (throwable is CancellationException) throw throwable
          output = throwable.toFailureText("动态高亮自动刷新失败")
        }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(onClick = argument::popBackStack) {
          Text("返回")
        }
        Button(
          enabled = !isRunning,
          onClick = {
            coroutineScope.launch {
              isRunning = true
              output = "运行中…"
              try {
                output = runner.execute(editorState.code).toDisplayText()
              } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                val diagnostic = throwable.toJsDiagnostic()
                output = buildString {
                  append(diagnostic.kind)
                  append(": ")
                  append(diagnostic.message)
                  diagnostic.lineNumber?.let { line -> append("\n位置：第 ").append(line).append(" 行") }
                  diagnostic.columnNumber?.let { column -> append("，第 ").append(column).append(" 列") }
                }
              } finally {
                isRunning = false
              }
            }
          },
        ) {
          Text(if (isRunning) "运行中" else "运行")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Button(
          enabled = !isLoadingLanguage && dynamicLanguageService == null,
          onClick = {
            coroutineScope.launch {
              isLoadingLanguage = true
              output = "正在加载动态语言目录与 JavaScript 服务…"
              var newService: DynamicLanguageService? = null
              try {
                val startMark = TimeSource.Monotonic.markNow()
                val catalogMark = TimeSource.Monotonic.markNow()
                val languages = dynamicLanguageManager.supportedLanguages()
                val catalogDuration = catalogMark.elapsedNow()
                val javaScript = languages.firstOrNull { it.languageId == JAVASCRIPT_LANGUAGE_ID }
                  ?: error("动态语言目录中未声明 JavaScript。")
                val serviceMark = TimeSource.Monotonic.markNow()
                newService = dynamicLanguageManager.load(javaScript.languageId)
                val serviceDuration = serviceMark.elapsedNow()
                dynamicLanguageService = newService
                newService = null
                loadedLanguage = javaScript
                output = buildLanguageLoadedText(
                  languages = languages,
                  loadedLanguage = javaScript,
                  totalDuration = startMark.elapsedNow(),
                  catalogDuration = catalogDuration,
                  serviceDuration = serviceDuration,
                )
              } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                output = throwable.toFailureText("动态语言服务加载失败")
              } finally {
                withContext(NonCancellable) {
                  newService?.close()
                }
                isLoadingLanguage = false
              }
            }
          },
        ) {
          Text(if (isLoadingLanguage) "加载中" else "1. 加载动态服务")
        }
        Button(
          enabled = dynamicLanguageService != null && !isAnalyzingLanguage,
          onClick = {
            val service = dynamicLanguageService ?: return@Button
            coroutineScope.launch {
              isAnalyzingLanguage = true
              output = "正在验证高亮与补全能力…"
              try {
                val source = editorState.code
                val startMark = TimeSource.Monotonic.markNow()
                val highlightMark = TimeSource.Monotonic.markNow()
                val highlightResult = service.highlight(source)
                val highlightRoundTripDuration = highlightMark.elapsedNow()
                // 第二阶段不仅核对协议结果，也将动态区间写回同一个可编辑视图供人工检查。
                val applyMark = TimeSource.Monotonic.markNow()
                editorState.applyHighlights(highlightResult.spans)
                val applyDuration = applyMark.elapsedNow()
                val completionMark = TimeSource.Monotonic.markNow()
                val completion = service.complete(
                  source = source,
                  position = source.length,
                  explicit = true,
                )
                val completionDuration = completionMark.elapsedNow()
                output = buildLanguageAnalysisText(
                  language = loadedLanguage,
                  highlightSummary = highlightResult.spans.take(DISPLAY_RESULT_LIMIT).joinToString("\n") { span ->
                    "${span.from}..${span.to}  ${span.styleIds.joinToString()}"
                  },
                  highlightCount = highlightResult.spans.size,
                  highlightMetrics = highlightResult.metrics,
                  highlightRoundTripDuration = highlightRoundTripDuration,
                  applyDuration = applyDuration,
                  completionRange = completion?.let { "${it.from}..${it.to}" },
                  completionLabels = completion?.options
                    ?.take(DISPLAY_RESULT_LIMIT)
                    ?.joinToString { it.displayLabel ?: it.label },
                  completionCount = completion?.options?.size ?: 0,
                  completionDuration = completionDuration,
                  totalDuration = startMark.elapsedNow(),
                )
              } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                output = throwable.toFailureText("动态语言功能验证失败")
              } finally {
                isAnalyzingLanguage = false
              }
            }
          },
        ) {
          Text(if (isAnalyzingLanguage) "验证中" else "2. 验证语言功能")
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1F)
          .background(Color(0xFFF7F7F7)),
      ) {
        JavaScriptCodeEditor(
          state = editorState,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Text("实时高亮性能")
      Text(
        text = autoHighlightReport,
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFFEEEEEE))
          .padding(8.dp),
      )

      Text("输出")
      Text(
        text = output,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 96.dp, max = 180.dp)
          .background(Color(0xFFEEEEEE))
          .padding(12.dp)
          .verticalScroll(rememberScrollState()),
      )
    }
  }

  /** 将一次教学执行结果整理成测试页面可直接阅读的文本。 */
  private fun JsTeachingCodeResult.toDisplayText(): String = buildString {
    consoleMessages.forEach { message ->
      append('[').append(message.level).append("] ").appendLine(message.text)
    }
    append("返回值：").append(value)
  }

  /** 将目录发现和 Service 加载结果整理为第一阶段的测试摘要。 */
  private fun buildLanguageLoadedText(
    languages: List<DynamicLanguageInfo>,
    loadedLanguage: DynamicLanguageInfo,
    totalDuration: Duration,
    catalogDuration: Duration,
    serviceDuration: Duration,
  ): String = buildString {
    append("动态服务加载成功：")
      .append(loadedLanguage.displayName)
      .append("（")
      .append(loadedLanguage.npmPackageName)
      .appendLine("）")
    append("目录发现：").appendLine(catalogDuration.toDisplayMilliseconds())
    append("Service 下载、解析与 Runtime 创建：").appendLine(serviceDuration.toDisplayMilliseconds())
    append("整体加载：").appendLine(totalDuration.toDisplayMilliseconds())
    append("目录语言（").append(languages.size).appendLine("）：")
    languages.forEach { language ->
      append("- ").append(language.displayName).append(" / ").appendLine(language.languageId)
    }
  }

  /** 将第二阶段高亮与补全调用结果整理为便于人工核对的摘要。 */
  private fun buildLanguageAnalysisText(
    language: DynamicLanguageInfo?,
    highlightSummary: String,
    highlightCount: Int,
    highlightMetrics: DynamicHighlightMetrics,
    highlightRoundTripDuration: Duration,
    applyDuration: Duration,
    completionRange: String?,
    completionLabels: String?,
    completionCount: Int,
    completionDuration: Duration,
    totalDuration: Duration,
  ): String = buildString {
    append("语言功能验证成功：").appendLine(language?.displayName ?: "未知语言")
    append("缓存路径：").appendLine(highlightMetrics.cacheMode.name)
    append("Lezer 解析：").appendLine(highlightMetrics.parseMicroseconds.toDisplayMilliseconds())
    append("高亮区间收集：").appendLine(highlightMetrics.collectMicroseconds.toDisplayMilliseconds())
    append("桥接与序列化（估算）：").appendLine(
      highlightMetrics.bridgeOverheadMicroseconds(highlightRoundTripDuration).toDisplayMilliseconds(),
    )
    append("高亮 Service 往返：").appendLine(highlightRoundTripDuration.toDisplayMilliseconds())
    append("编辑器装饰应用：").appendLine(applyDuration.toDisplayMilliseconds())
    append("补全 Service 往返：").appendLine(completionDuration.toDisplayMilliseconds())
    append("功能调用合计：").appendLine(totalDuration.toDisplayMilliseconds())
    append("高亮区间：").append(highlightCount).appendLine(" 个")
    if (highlightSummary.isNotEmpty()) appendLine(highlightSummary)
    append("文末补全：").append(completionCount).append(" 个")
    completionRange?.let { append("，替换区间 ").append(it) }
    appendLine()
    if (!completionLabels.isNullOrEmpty()) append(completionLabels)
  }

  /** 将自动高亮一次请求的语言包内部、桥接及编辑器应用耗时整理为实时摘要。 */
  private fun buildAutoHighlightText(
    metrics: DynamicHighlightMetrics,
    highlightCount: Int,
    roundTripDuration: Duration,
    applyDuration: Duration,
  ): String = buildString {
    append("缓存：").append(metrics.cacheMode.name)
      .append("，复用片段 ").append(metrics.reusableFragmentCount)
      .append("，区间 ").append(highlightCount).appendLine(" 个")
    metrics.changedRange?.let { range ->
      append("变更：旧 [").append(range.fromBefore).append(", ").append(range.toBefore)
        .append(") → 新 [").append(range.fromAfter).append(", ").append(range.toAfter).appendLine(")")
    }
    append("解析 ").append(metrics.parseMicroseconds.toDisplayMilliseconds())
      .append(" ｜ 收集 ").append(metrics.collectMicroseconds.toDisplayMilliseconds())
      .append(" ｜ 桥接约 ")
      .append(metrics.bridgeOverheadMicroseconds(roundTripDuration).toDisplayMilliseconds())
      .append(" ｜ 往返 ").append(roundTripDuration.toDisplayMilliseconds())
      .append(" ｜ 应用 ").append(applyDuration.toDisplayMilliseconds())
  }

  /** 从端上测得的往返时间中扣除语言包内部耗时，近似观察 JSON 与 QuickJS 桥接成本。 */
  private fun DynamicHighlightMetrics.bridgeOverheadMicroseconds(roundTripDuration: Duration): Long {
    return (
      roundTripDuration.inWholeMicroseconds - parseMicroseconds - collectMicroseconds
      ).coerceAtLeast(0)
  }

  /** 将动态服务异常保留类型和根因，便于测试页直接定位协议或下载失败。 */
  private fun Throwable.toFailureText(title: String): String = buildString {
    append(title).append("：").appendLine(this@toFailureText::class.simpleName)
    append(message ?: "未提供错误信息")
    cause?.takeIf { it !== this@toFailureText }?.let { cause ->
      append("\n根因：").append(cause::class.simpleName).append(": ").append(cause.message)
    }
  }

  /** 将单次手动测试耗时保留到微秒后按毫秒展示，确保 Native 平台也使用相同格式。 */
  private fun Duration.toDisplayMilliseconds(): String {
    return inWholeMicroseconds.toDisplayMilliseconds()
  }

  /** 将微秒值按三位小数格式化为毫秒，供跨 JS 桥指标直接展示。 */
  private fun Long.toDisplayMilliseconds(): String {
    val microseconds = this
    val milliseconds = microseconds / MICROSECONDS_PER_MILLISECOND
    val fraction = (microseconds % MICROSECONDS_PER_MILLISECOND).toString().padStart(3, '0')
    return "$milliseconds.$fraction ms"
  }

  private companion object {
    const val JAVASCRIPT_LANGUAGE_ID = "javascript"
    const val DISPLAY_RESULT_LIMIT = 12
    const val MICROSECONDS_PER_MILLISECOND = 1_000
    const val AUTO_HIGHLIGHT_DELAY_MILLIS = 200L

    val DEFAULT_CODE = """
      class Student {
        constructor(name, scores) {
          this.name = name;
          this.scores = scores;
        }

        average() {
          return this.scores.reduce((sum, score) => sum + score, 0) / this.scores.length;
        }
      }

      const student = new Student("小邮", [88, 92, 95]);
      console.log(student.name, "平均分", student.average());
      student.average();
    """.trimIndent()
  }
}
