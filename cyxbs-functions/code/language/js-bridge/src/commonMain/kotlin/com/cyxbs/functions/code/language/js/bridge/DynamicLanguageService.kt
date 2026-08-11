package com.cyxbs.functions.code.language.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceMethodNotImplementedException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 动态语法分析返回的一段高亮区间。
 *
 * [styleIds] 使用动态语言包给出的稳定样式标识，区间采用 UTF-16 偏移，与 Kotlin 和
 * JavaScript 字符串位置保持一致。
 */
@Serializable
data class DynamicHighlightSpan(
  val from: Int,
  val to: Int,
  val styleIds: List<String>,
)

/** 动态语言包返回的单个补全候选。 */
@Serializable
data class DynamicCompletionItem(
  val label: String,
  val displayLabel: String? = null,
  val detail: String? = null,
  val info: String? = null,
  val type: String? = null,
  val boost: Int = 0,
  val apply: String? = null,
)

/**
 * 一次补全查询的结果。
 *
 * [from] 与 [to] 表示应用候选时要替换的源码区间；无可用补全时返回 `null`。
 */
@Serializable
data class DynamicCompletionResult(
  val from: Int,
  val to: Int,
  val options: List<DynamicCompletionItem>,
)

/**
 * 由 npm JavaScript 包实现的动态语言能力。
 *
 * 业务将 `DynamicLanguageService::class`、npm 包名和版本传给 `NpmJsServiceLoader.load` 获取端上
 * 代理，不直接访问生成类或 JavaScript Runtime。Kotlin/JS 发布模块只需提供一个实现本接口的
 * object，KSP 会生成分发器。
 */
@NpmJsService
interface DynamicLanguageService : NpmJsServiceInstance {

  /**
   * 分析完整源码并返回按 UTF-16 偏移排序的高亮区间。
   *
   * @throws NpmJsServiceMethodNotImplementedException 旧语言包尚未实现本方法。
   * @throws NpmJsServiceInvocationException JavaScript 执行失败。
   * @throws SerializationException 参数编码或返回值解码不符合接口协议。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmJsServiceMethodNotImplementedException::class,
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
  )
  suspend fun highlight(source: String): List<DynamicHighlightSpan>

  /**
   * 查询指定光标位置的补全候选。
   *
   * @param source 当前完整源码。
   * @param position 光标 UTF-16 偏移。
   * @param explicit 是否由用户主动触发补全。
   * @throws NpmJsServiceMethodNotImplementedException 旧语言包尚未实现本方法。
   * @throws NpmJsServiceInvocationException JavaScript 执行失败。
   * @throws SerializationException 参数编码或返回值解码不符合接口协议。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmJsServiceMethodNotImplementedException::class,
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
  )
  suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult?
}
