package com.cyxbs.functions.code.language.java.compiler

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstWorkspace
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerDiagnostic
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticSeverity
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrDispatchKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrProgram
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticModel
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace

/**
 * 调用方指定的 Java 静态入口。
 *
 * [descriptor] 使用擦除后的 JVM descriptor 表达参数和返回类型，避免重载入口依赖名称猜测。
 */
internal data class JavaCompilerEntryPoint(
  val qualifiedClassName: String,
  val methodName: String,
  val descriptor: String,
)

/** 一次 Java 编译请求。 */
internal data class JavaCompilerRequest(
  val workspace: JavaSourceWorkspace,
  val entryPoint: JavaCompilerEntryPoint,
)

/**
 * 编辑器从源码位置选择的 Java 入口。
 *
 * [position] 为空时要求入口文件中恰好存在一个可执行 static 方法；非空时选择包含该 UTF-16
 * 位置的最内层 static 方法。JVM descriptor 仍由完成语义分析后的 typed IR 生成。
 */
internal data class JavaCompilerSourceEntryPoint(
  val filePath: String,
  val position: Int?,
)

/** CST adapter：只负责严格 Java 8 方言校验和规范化 AST 构建。 */
internal fun interface JavaAstFrontend {
  fun parse(workspace: JavaSourceWorkspace): JavaCompilerPhaseResult<JavaAstWorkspace>
}

/** 语义分析器：完成符号、类型、转换、重载和泛型绑定，不生成运行时代码。 */
internal fun interface JavaSemanticAnalyzer {
  fun analyze(ast: JavaAstWorkspace): JavaCompilerPhaseResult<JavaSemanticModel>
}

/** lowering：只消费 AST 与完整语义模型，产出不含未解析名称的 typed IR。 */
internal fun interface JavaIrLowerer {
  fun lower(semanticModel: JavaSemanticModel): JavaCompilerPhaseResult<JavaIrProgram>
}

/** JavaScript 后端：只能读取 typed IR，并生成可交给独立 QuickJS Runtime 的纯 ES Module。 */
internal fun interface JavaScriptBackend {
  fun generate(
    program: JavaIrProgram,
    entryPoint: JavaCompilerEntryPoint,
  ): JavaCompilerPhaseResult<JavaScriptProgramArtifact>
}

/** 生成代码位置与原始 Java 源码区间的映射。 */
internal data class JavaScriptSourceMapping(
  val generatedLine: Int,
  val generatedColumn: Int,
  val sourceSpan: JavaSourceSpan,
) {
  init {
    require(generatedLine >= 1) { "Generated JavaScript line must be one-based." }
    require(generatedColumn >= 0) { "Generated JavaScript column must be non-negative." }
  }
}

/** 单个生成的 ES Module。 */
internal data class JavaScriptModuleArtifact(
  val moduleName: String,
  val source: String,
  val sourceMappings: List<JavaScriptSourceMapping>,
)

/**
 * Java 编译器生成的完整模块集合。
 *
 * [entryExportName] 是入口模块导出的稳定函数名；宿主入口模块负责调用并通过受控 bridge 收集结果。
 */
internal data class JavaScriptProgramArtifact(
  val entryModuleName: String,
  val entryExportName: String,
  val modules: List<JavaScriptModuleArtifact>,
) {
  init {
    require(modules.map(JavaScriptModuleArtifact::moduleName).distinct().size == modules.size) {
      "Generated JavaScript module names must be unique."
    }
    require(modules.any { module -> module.moduleName == entryModuleName }) {
      "Generated JavaScript program must contain its entry module."
    }
  }
}

/**
 * 正式 Java 编译流水线。
 *
 * 任一阶段产生 ERROR 后立即停止，不把恢复 AST、ERROR 类型或半成品 IR 交给后续阶段。
 */
internal class JavaCompilerPipeline(
  private val frontend: JavaAstFrontend,
  private val semanticAnalyzer: JavaSemanticAnalyzer,
  private val lowerer: JavaIrLowerer,
  private val backend: JavaScriptBackend,
) {
  /** 编译工作区并累积每个成功阶段产生的非致命诊断。 */
  fun compile(request: JavaCompilerRequest): JavaCompilerPhaseResult<JavaScriptProgramArtifact> {
    val diagnostics = mutableListOf<JavaCompilerDiagnostic>()

    val astResult = frontend.parse(request.workspace)
    diagnostics += astResult.diagnostics
    val ast = astResult.value ?: return JavaCompilerPhaseResult.failure(diagnostics)

    val semanticResult = semanticAnalyzer.analyze(ast)
    diagnostics += semanticResult.diagnostics
    val semanticModel = semanticResult.value
      ?: return JavaCompilerPhaseResult.failure(diagnostics)

    val irResult = lowerer.lower(semanticModel)
    diagnostics += irResult.diagnostics
    val ir = irResult.value ?: return JavaCompilerPhaseResult.failure(diagnostics)

    val backendResult = backend.generate(ir, request.entryPoint)
    diagnostics += backendResult.diagnostics
    val artifact = backendResult.value
      ?: return JavaCompilerPhaseResult.failure(diagnostics)

    return JavaCompilerPhaseResult.success(artifact, diagnostics)
  }

  /**
   * 从编辑器入口位置编译工作区，避免公共动态语言协议暴露 Java descriptor。
   *
   * 入口选择发生在 typed IR 生成后，因此 package、import 和参数类型已经完成解析。选择失败会
   * 返回稳定源码诊断，不会猜测重载或执行任意一个方法。
   */
  fun compile(
    workspace: JavaSourceWorkspace,
    sourceEntryPoint: JavaCompilerSourceEntryPoint,
  ): JavaCompilerPhaseResult<JavaScriptProgramArtifact> {
    val diagnostics = mutableListOf<JavaCompilerDiagnostic>()

    val sourceFile = workspace.files.firstOrNull { file ->
      file.path == sourceEntryPoint.filePath
    } ?: return JavaCompilerPhaseResult.failure(
      listOf(
        JavaCompilerDiagnostic(
          code = "JAVA_ENTRY_FILE_NOT_FOUND",
          message = "Entry file '${sourceEntryPoint.filePath}' is not part of the workspace.",
          severity = JavaDiagnosticSeverity.ERROR,
        ),
      ),
    )

    val astResult = frontend.parse(workspace)
    diagnostics += astResult.diagnostics
    val ast = astResult.value ?: return JavaCompilerPhaseResult.failure(diagnostics)

    val semanticResult = semanticAnalyzer.analyze(ast)
    diagnostics += semanticResult.diagnostics
    val semanticModel = semanticResult.value
      ?: return JavaCompilerPhaseResult.failure(diagnostics)

    val irResult = lowerer.lower(semanticModel)
    diagnostics += irResult.diagnostics
    val ir = irResult.value ?: return JavaCompilerPhaseResult.failure(diagnostics)

    val candidates = ir.classes.flatMap { javaClass ->
      javaClass.methods.mapNotNull { method ->
        if (
          method.dispatch != JavaIrDispatchKind.STATIC ||
          method.body == null ||
          method.span.fileId != sourceFile.id
        ) {
          null
        } else {
          javaClass to method
        }
      }
    }.filter { (_, method) ->
      val position = sourceEntryPoint.position
      position == null || position >= method.span.from && position < method.span.to
    }.sortedBy { (_, method) -> method.span.to - method.span.from }

    val selected = when {
      candidates.isEmpty() -> null
      sourceEntryPoint.position != null -> candidates.first()
      candidates.size == 1 -> candidates.single()
      else -> null
    }
    if (selected == null) {
      val position = sourceEntryPoint.position ?: 0
      diagnostics += JavaCompilerDiagnostic(
        code = if (candidates.isEmpty()) {
          "JAVA_ENTRY_NOT_FOUND"
        } else {
          "JAVA_ENTRY_AMBIGUOUS"
        },
        message = if (candidates.isEmpty()) {
          "No executable static method was found at the selected entry position."
        } else {
          "The entry file contains multiple executable static methods; select a method before running."
        },
        severity = JavaDiagnosticSeverity.ERROR,
        span = JavaSourceSpan(
          sourceFile.id,
          position.coerceIn(0, sourceFile.source.length),
          position.coerceIn(0, sourceFile.source.length),
        ),
      )
      return JavaCompilerPhaseResult.failure(diagnostics)
    }

    val (entryClass, entryMethod) = selected
    val backendResult = backend.generate(
      program = ir,
      entryPoint = JavaCompilerEntryPoint(
        qualifiedClassName = entryClass.qualifiedName,
        methodName = entryMethod.name,
        descriptor = entryMethod.descriptor,
      ),
    )
    diagnostics += backendResult.diagnostics
    val artifact = backendResult.value ?: return JavaCompilerPhaseResult.failure(diagnostics)
    return JavaCompilerPhaseResult.success(artifact, diagnostics)
  }
}
