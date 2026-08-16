package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.JavaSemanticAnalyzer
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstWorkspace
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticModel

/**
 * Java 语义分析器的稳定入口。
 *
 * Stage1 实现内部保留 Stage0 的 static、控制流、definite-assignment 与 literal 行为，并在其上增加
 * 继承、实例成员、构造器、重载和常用泛型；入口不再维护一份不可达的旧分析状态。
 */
internal object JavaSemanticAnalyzerImpl : JavaSemanticAnalyzer {
  /** 执行完整 Stage1 多遍分析；任意 ERROR 都只返回诊断，不暴露半成品模型。 */
  override fun analyze(ast: JavaAstWorkspace): JavaCompilerPhaseResult<JavaSemanticModel> =
    JavaStage1SemanticAnalysis(ast).analyze()
}
