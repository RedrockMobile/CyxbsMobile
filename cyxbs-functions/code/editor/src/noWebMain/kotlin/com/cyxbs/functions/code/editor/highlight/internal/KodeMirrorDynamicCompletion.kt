package com.cyxbs.functions.code.editor.highlight.internal

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceMethodNotImplementedException
import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionConfig
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension

/**
 * 将按需加载的动态语言服务适配为 KodeMirror 异步补全扩展。
 *
 * [service] 在每次请求时读取，使编辑器可以先创建、再异步加载语言包。KodeMirror 会在请求挂起期间
 * 校验文档和光标是否仍匹配，迟到结果不会重新打开已经失效的补全弹窗。
 */
internal fun kodeMirrorDynamicCompletionExtension(
  service: () -> DynamicLanguageService?,
  workspace: () -> DynamicLanguageWorkspace,
  filePath: () -> String,
): Extension = autocompletion(
  CompletionConfig(
    asyncOverride = listOf completionSource@{ context ->
      val currentService = service() ?: return@completionSource null
      try {
        val currentFilePath = filePath()
        val currentWorkspace = workspace().withCurrentSource(
          filePath = currentFilePath,
          source = context.state.doc.toString(),
        )
        currentService.complete(
          workspace = currentWorkspace,
          filePath = currentFilePath,
          position = context.pos.value,
          explicit = context.explicit,
        )?.toKodeMirrorResult()
      } catch (_: NpmJsServiceMethodNotImplementedException) {
        // 旧动态包缺少 complete 时保留基础编辑能力，升级包后即可自动获得补全。
        null
      }
    },
  ),
)

/** 使用编辑器尚未保存的当前文档覆盖工作区快照。 */
private fun DynamicLanguageWorkspace.withCurrentSource(
  filePath: String,
  source: String,
): DynamicLanguageWorkspace {
  var replaced = false
  val updated = files.map { file ->
    if (file.path == filePath) {
      replaced = true
      DynamicSourceFile(filePath, source)
    } else {
      file
    }
  }
  require(replaced) { "Workspace does not contain '$filePath'." }
  return DynamicLanguageWorkspace(updated)
}

/** 将通用动态语言协议转换为 KodeMirror 的当前文档补全结果。 */
private fun DynamicCompletionResult.toKodeMirrorResult(): CompletionResult = CompletionResult(
  from = DocPos(from),
  to = DocPos(to),
  options = options.map(DynamicCompletionItem::toKodeMirrorCompletion),
  // 用户继续输入同一标识符时可由编辑器本地过滤，无需每个字符都跨 Runtime 请求。
  validFor = Regex("[\\p{L}\\p{N}_$]*"),
)

/** 保留动态包给出的显示信息、类型、权重和实际插入文本。 */
private fun DynamicCompletionItem.toKodeMirrorCompletion(): Completion = Completion(
  label = label,
  displayLabel = displayLabel,
  detail = detail,
  info = info,
  type = type,
  boost = boost,
  apply = apply,
)
