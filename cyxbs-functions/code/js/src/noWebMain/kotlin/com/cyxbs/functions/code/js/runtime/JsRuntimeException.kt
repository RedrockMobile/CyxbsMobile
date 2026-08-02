package com.cyxbs.functions.code.js.runtime

/** JavaScript Runtime 执行失败的稳定分类，不依赖当前使用的引擎实现。 */
enum class JsRuntimeErrorKind {
  /** JavaScript 源码不满足语法规则。 */
  SYNTAX_ERROR,

  /** 静态或动态 import 无法解析目标 Module。 */
  MODULE_RESOLUTION_ERROR,

  /** JavaScript 顶层代码或函数在运行时抛出错误。 */
  RUNTIME_ERROR,

  /** 执行达到超时限制或被调用方主动中断。 */
  INTERRUPTED,

  /** JavaScript 返回值无法转换成调用方请求的 Kotlin 类型。 */
  VALUE_CONVERSION_ERROR,
}

/**
 * JavaScript Runtime 初始化、编译或执行失败。
 *
 * 该异常是 `code/js` 模块对外的稳定错误边界。调用方可以依赖 [kind] 和源码位置进行展示，
 * 但不应读取 [cause] 的具体类型；它只用于日志保留底层引擎的原始错误链。
 *
 * @param kind 稳定错误分类。
 * @param message 已去除重复 JavaScript 堆栈的错误摘要。
 * @param fileName 引擎报告的逻辑文件名，无法定位时为空。
 * @param lineNumber 从 1 开始的源码行号，无法定位时为空。
 * @param columnNumber 从 1 开始的源码列号，无法定位时为空。
 * @param jsStack JavaScript 原始堆栈，引擎未提供时为空。
 * @param moduleName Module 加载失败时对应的逻辑名称；其他错误或引擎无法定位时为空。
 * @param cause 仅供日志排查的底层异常，不属于稳定 API。
 */
class JsRuntimeException(
  val kind: JsRuntimeErrorKind,
  message: String,
  val fileName: String? = null,
  val lineNumber: Int? = null,
  val columnNumber: Int? = null,
  val jsStack: String? = null,
  val moduleName: String? = null,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
