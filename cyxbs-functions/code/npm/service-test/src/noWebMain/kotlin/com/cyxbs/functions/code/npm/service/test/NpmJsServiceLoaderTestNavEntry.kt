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
              var service: NpmJsServiceLoaderTestService? = null
              try {
                service = NpmJsServiceLoader().load(
                  serviceClass = NpmJsServiceLoaderTestService::class,
                  packageName = PACKAGE_NAME,
                  version = PACKAGE_VERSION,
                  refreshPolicy = NpmRefreshPolicy.FORCE,
                )
                val result = service.execute(TEST_INPUT, TEST_VALUE)
                output = buildString {
                  appendLine("调用成功")
                  appendLine("bundleMarker：${result.bundleMarker}")
                  appendLine("input：${result.input}")
                  appendLine("inputLength：${result.inputLength}")
                  append("multipliedValue：${result.multipliedValue}")
                }
              } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                output = throwable.toDisplayText()
              } finally {
                withContext(NonCancellable) {
                  try {
                    service?.close()
                  } catch (closeFailure: Throwable) {
                    output += "\n关闭 Service 失败：${closeFailure.message}"
                  }
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

  private companion object {
    const val PACKAGE_NAME = "@cyxbs-mobile/npm-service-test"
    const val PACKAGE_VERSION = "latest"
    const val TEST_INPUT = "Cyxbs"
    const val TEST_VALUE = 6
  }
}
