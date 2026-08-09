package com.cyxbs.functions.code.npm.service.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.functions.code.npm.NpmJsServiceLoader
import com.cyxbs.functions.code.npm.model.NpmRefreshPolicy
import com.cyxbs.functions.code.npm.service.test.js.bridge.NpmJsServiceLoaderTestService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.TimeSource

/** 无参数的 NpmJsServiceLoader 手动测试页面路由。 */
@Serializable
data object NpmJsServiceLoaderTestNavArgument : AppNavArgument

/**
 * 验证本模块 npm bundle 从本地 debug 源进入包池并通过 QuickJS Service 代理执行的页面。
 *
 * 每次点击都使用 [NpmRefreshPolicy.FORCE]，确保在创建 Runtime 前重新经过 metadata、依赖下载与
 * SRI 校验链路；调用结束后立即关闭 Service，释放 Runtime 和包池租约。
 */
@AppNav(route = "code/npm-service-test")
class NpmJsServiceLoaderTestNavEntry : AppNavEntry<NpmJsServiceLoaderTestNavArgument>() {

  override fun isNeedLogin(argument: NpmJsServiceLoaderTestNavArgument): Boolean = false

  @Composable
  override fun Content(argument: NpmJsServiceLoaderTestNavArgument) {
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var output by remember {
      mutableStateOf("点击“加载并调用”验证 NpmJsServiceLoader。")
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
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
              output = "正在刷新 metadata、解析依赖并创建 Service…"
              val totalStartedAt = TimeSource.Monotonic.markNow()
              val stageTimings = mutableListOf<StageTiming>()
              var service: NpmJsServiceLoaderTestService? = null
              var resultText: String? = null
              var failureText: String? = null
              try {
                val loader = stageTimings.measureStage("构造 NpmJsServiceLoader") {
                  NpmJsServiceLoader()
                }
                val loadedService = stageTimings.measureStage(
                  "加载 Service（刷新、下载、校验、建图、Runtime 初始化）",
                ) {
                  loader.load(
                    serviceClass = NpmJsServiceLoaderTestService::class,
                    packageName = PACKAGE_NAME,
                    version = PACKAGE_VERSION,
                    refreshPolicy = NpmRefreshPolicy.FORCE,
                  )
                }
                service = loadedService
                val result = stageTimings.measureStage("首次 JS Service 调用") {
                  loadedService.execute(TEST_INPUT, TEST_VALUE)
                }
                resultText = buildString {
                  appendLine("调用成功")
                  appendLine("bundleMarker：${result.bundleMarker}")
                  appendLine("input：${result.input}")
                  appendLine("inputLength：${result.inputLength}")
                  append("multipliedValue：${result.multipliedValue}")
                }
              } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                failureText = throwable.toDisplayText()
              } finally {
                withContext(NonCancellable) {
                  val serviceToClose = service
                  try {
                    if (serviceToClose != null) {
                      stageTimings.measureStage("关闭 Service") {
                        serviceToClose.close()
                      }
                    }
                  } catch (closeFailure: Throwable) {
                    failureText = buildString {
                      failureText?.let(::appendLine)
                      append("关闭 Service 失败：${closeFailure.message}")
                    }
                  }
                }
                val totalDuration = totalStartedAt.elapsedNow()
                output = buildString {
                  resultText?.let(::append)
                  if (resultText != null && failureText != null) appendLine()
                  append(failureText ?: if (resultText == null) "执行已结束，但没有返回结果。" else "")
                  appendLine()
                  appendLine()
                  appendLine("耗时统计：")
                  stageTimings.forEach { timing ->
                    appendLine("- ${timing.name}：${timing.duration.toDisplayMilliseconds()}")
                  }
                  append("- 总耗时（包含清理）：${totalDuration.toDisplayMilliseconds()}")
                }
                isRunning = false
              }
            }
          },
        ) {
          Text(if (isRunning) "运行中" else "加载并调用")
        }
      }

      Text("包坐标：$PACKAGE_NAME@$PACKAGE_VERSION")
      Text("预期结果：$TEST_INPUT 长度 5，$TEST_VALUE × 7 = 42")
      Text(
        text = output,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1F)
          .background(Color(0xFFEEEEEE))
          .padding(12.dp)
          .verticalScroll(rememberScrollState()),
      )
    }
  }

  /** 将稳定业务异常和底层 cause 整理成手动测试可直接阅读的文本。 */
  private fun Throwable.toDisplayText(): String = buildString {
    append(this@toDisplayText::class.simpleName ?: "Error")
    append(": ")
    append(this@toDisplayText.message ?: "未知错误")
    this@toDisplayText.cause?.message?.let { causeMessage ->
      appendLine()
      append("cause: ")
      append(causeMessage)
    }
  }

  /** 将单阶段耗时保留到微秒后按毫秒展示，避免快速阶段全部显示为 0 ms。 */
  private fun Duration.toDisplayMilliseconds(): String {
    val microseconds = inWholeMicroseconds
    val milliseconds = microseconds / MICROSECONDS_PER_MILLISECOND
    val fraction = (microseconds % MICROSECONDS_PER_MILLISECOND).toString().padStart(3, '0')
    return "$milliseconds.$fraction ms"
  }

  /** 执行并记录一个阶段；即使阶段抛出异常，也保留截至失败时的耗时。 */
  private suspend fun <T> MutableList<StageTiming>.measureStage(
    name: String,
    block: suspend () -> T,
  ): T {
    val startedAt = TimeSource.Monotonic.markNow()
    try {
      return block()
    } finally {
      this += StageTiming(name, startedAt.elapsedNow())
    }
  }

  /** 页面侧可观察阶段的单次耗时，不改变正式 npm 加载链路。 */
  private data class StageTiming(
    val name: String,
    val duration: Duration,
  )

  private companion object {
    const val MICROSECONDS_PER_MILLISECOND = 1_000L
    const val PACKAGE_NAME = "@cyxbs-mobile/cyxbs-functions-code-npm-service-test-js-impl"
    const val PACKAGE_VERSION = "latest"
    const val TEST_INPUT = "Cyxbs"
    const val TEST_VALUE = 6
  }
}
