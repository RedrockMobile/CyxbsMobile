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
import com.cyxbs.functions.code.editor.highlight.JavaScriptCodeEditor
import com.cyxbs.functions.code.editor.highlight.rememberJavaScriptCodeEditorState
import com.cyxbs.functions.code.js.diagnostic.toJsDiagnostic
import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.teaching.JsTeachingCodeResult
import com.cyxbs.functions.code.js.teaching.JsTeachingCodeRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** 无参数的代码编辑器手动测试页面路由。 */
@Serializable
data object CodeEditorTestNavArgument : AppNavArgument

/**
 * KodeMirror JavaScript 编辑和本地 QuickJS 运行的手动测试页面。
 *
 * 该页面只编译进 `noWebMain`，用于 Android、iOS 与 Desktop 的功能体验，不作为正式教学 UI。
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
    val coroutineScope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("点击运行查看控制台输出") }

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

  private companion object {
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
