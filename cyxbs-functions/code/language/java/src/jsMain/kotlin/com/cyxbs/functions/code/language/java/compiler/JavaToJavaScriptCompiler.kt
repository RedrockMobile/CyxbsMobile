package com.cyxbs.functions.code.language.java.compiler

import com.cyxbs.functions.code.language.java.compiler.backend.js.JavaScriptBackendImpl
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.frontend.JavaLezerAstFrontend
import com.cyxbs.functions.code.language.java.compiler.lowering.JavaAstToIrLowerer
import com.cyxbs.functions.code.language.java.compiler.semantic.impl.JavaSemanticAnalyzerImpl
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace

/**
 * Stage1 Java 源码到纯 ES Module 的默认编译入口。
 *
 * 该入口固定串联严格 CST adapter、多遍语义分析、typed IR lowering 和 JavaScript 后端。
 * 任一阶段失败都会立即返回结构化诊断，不会向后续阶段传递恢复树或半成品。
 */
internal object JavaToJavaScriptCompiler {
  private val pipeline = JavaCompilerPipeline(
    frontend = JavaLezerAstFrontend,
    semanticAnalyzer = JavaSemanticAnalyzerImpl,
    lowerer = JavaAstToIrLowerer,
    backend = JavaScriptBackendImpl,
  )

  /**
   * 编译一个多文件 Java 工作区。
   *
   * [request] 必须提供以 JVM descriptor 精确定位的 static 入口；成功时返回可交给 QuickJS
   * Module Loader 的模块集合，失败时只返回面向 Java 源码的诊断。
   */
  fun compile(
    request: JavaCompilerRequest,
  ): JavaCompilerPhaseResult<JavaScriptProgramArtifact> = pipeline.compile(request)

  /**
   * 使用编辑器文件和光标位置选择入口，不向公共动态语言协议暴露 JVM descriptor。
   *
   * @param workspace 参与编译的 Java 源码工作区。
   * @param entryPoint 入口文件和可选 UTF-16 光标位置。
   */
  fun compile(
    workspace: JavaSourceWorkspace,
    entryPoint: JavaCompilerSourceEntryPoint,
  ): JavaCompilerPhaseResult<JavaScriptProgramArtifact> = pipeline.compile(workspace, entryPoint)
}
