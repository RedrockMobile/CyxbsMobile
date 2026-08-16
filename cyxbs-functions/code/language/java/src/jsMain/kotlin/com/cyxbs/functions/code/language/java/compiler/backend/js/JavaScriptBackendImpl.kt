package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.JavaCompilerEntryPoint
import com.cyxbs.functions.code.language.java.compiler.JavaScriptBackend
import com.cyxbs.functions.code.language.java.compiler.JavaScriptModuleArtifact
import com.cyxbs.functions.code.language.java.compiler.JavaScriptProgramArtifact
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerDiagnostic
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticSeverity
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClass
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClassId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstant
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConversion
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrDispatchKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrExpression
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrField
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrFieldId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocal
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocalId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethod
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrProgram
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStatement
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrType
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrUnaryOperator
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * 阶段 0 的 typed IR 到 ES Module 后端。
 *
 * 本实现只观察 [JavaIrProgram]；所有名称解析、类型推断和 CST/源码读取必须在 lowering 前结束。
 * 生成前会完整校验支持范围，因此失败时绝不返回可能执行错误的半成品 JavaScript。
 */
internal object JavaScriptBackendImpl : JavaScriptBackend {
  override fun generate(
    program: JavaIrProgram,
    entryPoint: JavaCompilerEntryPoint,
  ): JavaCompilerPhaseResult<JavaScriptProgramArtifact> {
    val model = JavaIrIndex(program)
    val diagnostics = JavaScriptBackendValidator(model, entryPoint).validate()
    if (diagnostics.any { diagnostic -> diagnostic.severity == JavaDiagnosticSeverity.ERROR }) {
      return JavaCompilerPhaseResult.failure(diagnostics)
    }
    return JavaCompilerPhaseResult.success(
      JavaScriptEmitter(model, entryPoint).emit(),
      diagnostics,
    )
  }
}

/** 将 IR 编号建立为只读索引，后端不再根据 Java 源码名称猜测任何绑定。 */
private class JavaIrIndex(
  val program: JavaIrProgram,
) {
  val classes: Map<JavaIrClassId, JavaIrClass> = program.classes.associateBy(JavaIrClass::id)
  val methods: Map<JavaIrMethodId, JavaIrMethod> = program.classes
    .flatMap(JavaIrClass::methods)
    .associateBy(JavaIrMethod::id)
  val fields: Map<JavaIrFieldId, JavaIrField> = program.classes
    .flatMap(JavaIrClass::fields)
    .associateBy(JavaIrField::id)
  val locals: Map<JavaIrLocalId, JavaIrLocal> = program.classes
    .flatMap(JavaIrClass::methods)
    .flatMap { method -> method.parameters + method.locals }
    .associateBy(JavaIrLocal::id)
}

/** 预检 IR，拒绝尚未具备 Java 语义实现的节点、类型或不一致引用。 */
private class JavaScriptBackendValidator(
  private val index: JavaIrIndex,
  private val entryPoint: JavaCompilerEntryPoint,
) {
  private val diagnostics = mutableListOf<JavaCompilerDiagnostic>()

  /** 返回全部错误；调用方在诊断存在时不会创建 emitter。 */
  fun validate(): List<JavaCompilerDiagnostic> {
    if (index.classes.size != index.program.classes.size) {
      invalid("Java IR contains duplicate class ids.", null)
    }
    if (index.methods.size != index.program.classes.sumOf { it.methods.size }) {
      invalid("Java IR contains duplicate method ids.", null)
    }
    if (index.fields.size != index.program.classes.sumOf { it.fields.size }) {
      invalid("Java IR contains duplicate field ids.", null)
    }
    index.program.classes.sortedBy { it.id.value }.forEach(::validateClass)
    validateEntryPoint()
    return diagnostics.toList()
  }

  /** 阶段 0 只保留无字段、无初始化器的静态方法容器。 */
  private fun validateClass(clazz: JavaIrClass) {
    if (clazz.superClass != null || clazz.interfaces.isNotEmpty()) {
      unsupported("Class inheritance and interfaces are not available in the stage 0 JavaScript backend.", clazz.span)
    }
    if (clazz.fields.isNotEmpty()) {
      unsupported("Java fields are not available in the stage 0 JavaScript backend.", clazz.span)
    }
    if (clazz.staticInitializer != null) {
      unsupported("Java class initializers are not available in the stage 0 JavaScript backend.", clazz.span)
    }
    clazz.methods.forEach(::validateMethod)
  }

  /** 校验方法签名、局部变量引用和结构化语句。 */
  private fun validateMethod(method: JavaIrMethod) {
    if (method.dispatch != JavaIrDispatchKind.STATIC) {
      unsupported("Only static Java methods are available in the stage 0 JavaScript backend.", method.span)
    }
    if (method.body == null) {
      unsupported("Abstract or native Java methods cannot be emitted as JavaScript.", method.span)
    }
    validateType(method.returnType, method.span)
    val locals = method.parameters + method.locals
    if (locals.map(JavaIrLocal::id).distinct().size != locals.size) {
      invalid("Java IR method contains duplicate local ids.", method.span)
    }
    if (method.parameters.any { parameter -> !parameter.isParameter } ||
      method.locals.any { local -> local.isParameter }
    ) {
      invalid("Java IR method parameters and locals have inconsistent flags.", method.span)
    }
    locals.forEach { local -> validateType(local.type, local.span) }
    method.body?.let(::validateStatement)
  }

  /** 递归校验在阶段 0 可直接翻译的控制流。 */
  private fun validateStatement(statement: JavaIrStatement) {
    when (statement) {
      is JavaIrStatement.Block -> statement.statements.forEach(::validateStatement)
      is JavaIrStatement.DeclareLocal -> {
        requireLocal(statement.local, statement.span)
        statement.initializer?.let(::validateExpression)
      }
      is JavaIrStatement.Expression -> validateExpression(statement.expression)
      is JavaIrStatement.If -> {
        validateExpression(statement.condition)
        validateStatement(statement.thenBranch)
        statement.elseBranch?.let(::validateStatement)
      }
      is JavaIrStatement.While -> {
        validateExpression(statement.condition)
        validateStatement(statement.body)
      }
      is JavaIrStatement.Return -> statement.expression?.let(::validateExpression)
      is JavaIrStatement.Throw -> unsupported(
        "Java throw statements are not available in the stage 0 JavaScript backend.",
        statement.span,
      )
    }
  }

  /** 递归校验表达式以及其引用到的已完成绑定。 */
  private fun validateExpression(expression: JavaIrExpression) {
    validateType(expression.type, expression.span)
    when (expression) {
      is JavaIrExpression.Constant -> if (expression.value is JavaIrConstant.LongValue) {
        unsupported("Java long constants require the later BigInt backend.", expression.span)
      }
      is JavaIrExpression.GetLocal -> requireLocal(expression.local, expression.span)
      is JavaIrExpression.SetLocal -> {
        requireLocal(expression.local, expression.span)
        validateExpression(expression.value)
      }
      is JavaIrExpression.GetField,
      is JavaIrExpression.SetField,
      is JavaIrExpression.GetStaticField,
      is JavaIrExpression.SetStaticField -> unsupported(
        "Java field access is not available in the stage 0 JavaScript backend.",
        expression.span,
      )
      is JavaIrExpression.Binary -> {
        validateExpression(expression.left)
        validateExpression(expression.right)
      }
      is JavaIrExpression.Unary -> validateExpression(expression.operand)
      is JavaIrExpression.Convert -> {
        validateExpression(expression.expression)
        validateConversion(expression.conversion, expression.span)
      }
      is JavaIrExpression.InvokeStatic -> {
        requireStaticMethod(expression.method, expression.span)
        expression.arguments.forEach(::validateExpression)
      }
      is JavaIrExpression.InvokeSpecial,
      is JavaIrExpression.InvokeVirtual,
      is JavaIrExpression.NewObject -> unsupported(
        "Instance construction and dispatch are not available in the stage 0 JavaScript backend.",
        expression.span,
      )
    }
  }

  /** 阶段 0 支持 identity、引用拓宽与不涉及 long/浮点的整数 widening。 */
  private fun validateConversion(conversion: JavaIrConversion, span: JavaSourceSpan) {
    when (conversion) {
      JavaIrConversion.Identity,
      is JavaIrConversion.ReferenceWidening -> Unit
      is JavaIrConversion.PrimitiveWidening -> {
        if (!conversion.from.name.isStage0IntegralPrimitiveName() ||
          !conversion.to.name.isStage0IntegralPrimitiveName()
        ) {
          unsupported("Only integral primitive widening is available in the stage 0 JavaScript backend.", span)
        }
      }
      is JavaIrConversion.Boxing,
      is JavaIrConversion.Unboxing -> unsupported(
        "Boxing and unboxing are not available in the stage 0 JavaScript backend.",
        span,
      )
    }
  }

  /** 引用类型仅以不透明值参与阶段 0 的 null、相等性和字符串字面量场景。 */
  private fun validateType(type: JavaIrType, span: JavaSourceSpan) {
    when (type) {
      is JavaIrType.Primitive -> when (type.kind.name) {
        "LONG" -> unsupported("Java long requires the later BigInt backend.", span)
        "FLOAT",
        "DOUBLE" -> unsupported(
          "Java floating-point values are not available in the stage 0 JavaScript backend.",
          span,
        )
        else -> Unit
      }
      is JavaIrType.Array -> unsupported(
        "Java arrays are not available in the stage 0 JavaScript backend.",
        span,
      )
      is JavaIrType.Reference,
      JavaIrType.Null,
      JavaIrType.Void -> Unit
    }
  }

  /** 入口必须唯一地指向完整、静态且已编译的方法。 */
  private fun validateEntryPoint() {
    val owner = index.program.classes.singleOrNull { clazz ->
      clazz.qualifiedName == entryPoint.qualifiedClassName
    }
    if (owner == null) {
      invalid("Java entry class '${entryPoint.qualifiedClassName}' is missing or ambiguous.", null)
      return
    }
    val target = owner.methods.singleOrNull { method ->
      method.name == entryPoint.methodName &&
        method.descriptor == entryPoint.descriptor &&
        method.dispatch == JavaIrDispatchKind.STATIC
    }
    if (target == null) {
      invalid(
        "Java entry method '${entryPoint.methodName}${entryPoint.descriptor}' is missing or ambiguous.",
        owner.span,
      )
    } else if (target.body == null) {
      unsupported("Java entry method has no executable body.", target.span)
    }
  }

  /** 检查局部变量编号在整个 lowered program 中可解析。 */
  private fun requireLocal(localId: JavaIrLocalId, span: JavaSourceSpan) {
    if (index.locals[localId] == null) {
      invalid("Java IR references an unknown local id ${localId.value}.", span)
    }
  }

  /** 检查调用 target 存在且被 lowering 标记为 static。 */
  private fun requireStaticMethod(methodId: JavaIrMethodId, span: JavaSourceSpan) {
    val method = index.methods[methodId]
    if (method == null) {
      invalid("Java IR references an unknown method id ${methodId.value}.", span)
    } else if (method.dispatch != JavaIrDispatchKind.STATIC || method.body == null) {
      unsupported("Java static calls may only target executable static methods.", span)
    }
  }

  /** 记录 typed IR 已知但当前后端尚未实现的稳定诊断。 */
  private fun unsupported(message: String, span: JavaSourceSpan?) {
    diagnostics += JavaCompilerDiagnostic(
      code = "JAVA_BACKEND_UNSUPPORTED",
      message = message,
      severity = JavaDiagnosticSeverity.ERROR,
      span = span,
    )
  }

  /** 记录违反 lowered IR 完整性约束的稳定诊断。 */
  private fun invalid(message: String, span: JavaSourceSpan?) {
    diagnostics += JavaCompilerDiagnostic(
      code = "JAVA_BACKEND_INVALID_IR",
      message = message,
      severity = JavaDiagnosticSeverity.ERROR,
      span = span,
    )
  }
}

/** 将已经验证的 typed IR 发射为单个 ES Module。 */
private class JavaScriptEmitter(
  private val index: JavaIrIndex,
  private val entryPoint: JavaCompilerEntryPoint,
) {
  private val writer = JsWriter()

  /** 输出 runtime prelude、全部静态方法和稳定入口导出。 */
  fun emit(): JavaScriptProgramArtifact {
    JavaRuntimePrelude.source.lines().forEach(writer::line)
    writer.line()
    index.program.classes
      .flatMap(JavaIrClass::methods)
      .sortedBy { method -> method.id.value }
      .forEach(::emitMethod)
    emitEntryExport(resolveEntryMethod())

    return JavaScriptProgramArtifact(
      entryModuleName = JavaModuleLayout.ENTRY_MODULE_NAME,
      entryExportName = JavaModuleLayout.ENTRY_EXPORT_NAME,
      modules = listOf(
        JavaScriptModuleArtifact(
          moduleName = JavaModuleLayout.ENTRY_MODULE_NAME,
          source = writer.source,
          sourceMappings = writer.mappings,
        ),
      ),
    )
  }

  /** 生成一个实现 Java static 方法、参数按 stable id 命名的普通 JS function。 */
  private fun emitMethod(method: JavaIrMethod) {
    val parameters = method.parameters.joinToString(", ") { parameter ->
      JsNameMangler.local(parameter.id)
    }
    writer.line("function ${JsNameMangler.method(method.id)}(${parameters}) {")
    writer.indented {
      method.parameters
        .filter { parameter -> parameter.type.isStage0Integral() }
        .forEach { parameter ->
          val name = JsNameMangler.local(parameter.id)
          writer.line("$name = $name | 0;")
        }
      emitBlockContents(checkNotNull(method.body))
    }
    writer.line("}")
    writer.line()
  }

  /** 输出稳定入口；剩余调用参数会由目标 static 方法的 JS 形参自然忽略。 */
  private fun emitEntryExport(entry: JavaIrMethod) {
    writer.line("export function ${JavaModuleLayout.ENTRY_EXPORT_NAME}(...args) {")
    writer.indented {
      writer.line("return ${JsNameMangler.method(entry.id)}(...args);")
    }
    writer.line("}")
  }

  /** 不额外生成一层根花括号，保持 Java 方法体和 JS function body 对齐。 */
  private fun emitBlockContents(block: JavaIrStatement.Block) {
    block.statements.forEach(::emitStatement)
  }

  /** 生成结构化语句，并在表达式起点记录最小 Java source mapping。 */
  private fun emitStatement(statement: JavaIrStatement) {
    when (statement) {
      is JavaIrStatement.Block -> {
        writer.line("{")
        writer.indented { emitBlockContents(statement) }
        writer.line("}")
      }
      is JavaIrStatement.DeclareLocal -> {
        val local = index.locals.getValue(statement.local)
        val initializer = statement.initializer
        val source = if (initializer == null) {
          "let ${JsNameMangler.local(local.id)};"
        } else {
          "let ${JsNameMangler.local(local.id)} = ${coerceToType(renderExpression(initializer), local.type)};"
        }
        writer.writeIndentation()
        writer.writeMapped(source, statement.span)
        writer.write("\n")
      }
      is JavaIrStatement.Expression -> {
        writer.writeIndentation()
        writer.writeMapped(renderExpression(statement.expression), statement.expression.span)
        writer.write(";\n")
      }
      is JavaIrStatement.If -> {
        writer.writeIndentation()
        writer.write("if (")
        writer.writeMapped(renderExpression(statement.condition), statement.condition.span)
        writer.write(") ")
        emitBranch(statement.thenBranch)
        statement.elseBranch?.let { elseBranch ->
          writer.writeIndentation()
          writer.write("else ")
          emitBranch(elseBranch)
        }
      }
      is JavaIrStatement.While -> {
        writer.writeIndentation()
        writer.write("while (")
        writer.writeMapped(renderExpression(statement.condition), statement.condition.span)
        writer.write(") ")
        emitBranch(statement.body)
      }
      is JavaIrStatement.Return -> {
        val expression = statement.expression
        if (expression == null) {
          writer.line("return;")
        } else {
          writer.writeIndentation()
          writer.write("return ")
          writer.writeMapped(renderExpression(expression), expression.span)
          writer.write(";\n")
        }
      }
      is JavaIrStatement.Throw -> error("Validated Java IR cannot contain throw statements.")
    }
  }

  /** 对非 block 分支补齐花括号，避免 if/while 生成时产生悬挂 else。 */
  private fun emitBranch(statement: JavaIrStatement) {
    if (statement is JavaIrStatement.Block) {
      writer.write("{\n")
      writer.indented { emitBlockContents(statement) }
      writer.line("}")
    } else {
      writer.write("{\n")
      writer.indented { emitStatement(statement) }
      writer.line("}")
    }
  }

  /** 将单个 typed IR 表达式渲染为带充分括号的 JavaScript 文本。 */
  private fun renderExpression(expression: JavaIrExpression): String {
    return when (expression) {
      is JavaIrExpression.Constant -> renderConstant(expression.value)
      is JavaIrExpression.GetLocal -> JsNameMangler.local(expression.local)
      is JavaIrExpression.SetLocal -> {
        val local = index.locals.getValue(expression.local)
        "(${JsNameMangler.local(expression.local)} = " +
          coerceToType(renderExpression(expression.value), local.type) + ")"
      }
      is JavaIrExpression.Binary -> renderBinary(expression)
      is JavaIrExpression.Unary -> renderUnary(expression)
      is JavaIrExpression.Convert -> renderConversion(expression)
      is JavaIrExpression.InvokeStatic -> {
        val arguments = expression.arguments.joinToString(", ") { argument -> renderExpression(argument) }
        "${JsNameMangler.method(expression.method)}(${arguments})"
      }
      is JavaIrExpression.GetField,
      is JavaIrExpression.SetField,
      is JavaIrExpression.GetStaticField,
      is JavaIrExpression.SetStaticField,
      is JavaIrExpression.InvokeSpecial,
      is JavaIrExpression.InvokeVirtual,
      is JavaIrExpression.NewObject -> error("Validated Java IR cannot contain unsupported expressions.")
    }
  }

  /** 根据表达式 result type 选择 Java int helper 或 JavaScript 布尔/引用运算。 */
  private fun renderBinary(expression: JavaIrExpression.Binary): String {
    val left = renderExpression(expression.left)
    val right = renderExpression(expression.right)
    return when (expression.operator) {
      JavaIrBinaryOperator.MULTIPLY -> if (expression.type.isStage0Integral()) {
        "Math.imul($left, $right)"
      } else {
        "($left * $right)"
      }
      JavaIrBinaryOperator.DIVIDE -> if (expression.type.isStage0Integral()) {
        "\$__j_int_div($left, $right)"
      } else {
        "($left / $right)"
      }
      JavaIrBinaryOperator.REMAINDER -> if (expression.type.isStage0Integral()) {
        "\$__j_int_rem($left, $right)"
      } else {
        "($left % $right)"
      }
      JavaIrBinaryOperator.ADD -> if (expression.type.isStage0Integral()) {
        "(($left + $right) | 0)"
      } else {
        "($left + $right)"
      }
      JavaIrBinaryOperator.SUBTRACT -> "(($left - $right) | 0)"
      JavaIrBinaryOperator.SHIFT_LEFT -> "(($left << $right) | 0)"
      JavaIrBinaryOperator.SHIFT_RIGHT -> "(($left >> $right) | 0)"
      JavaIrBinaryOperator.UNSIGNED_SHIFT_RIGHT -> "(($left >>> $right) | 0)"
      JavaIrBinaryOperator.LESS_THAN -> "($left < $right)"
      JavaIrBinaryOperator.LESS_THAN_OR_EQUAL -> "($left <= $right)"
      JavaIrBinaryOperator.GREATER_THAN -> "($left > $right)"
      JavaIrBinaryOperator.GREATER_THAN_OR_EQUAL -> "($left >= $right)"
      JavaIrBinaryOperator.EQUAL -> "($left === $right)"
      JavaIrBinaryOperator.NOT_EQUAL -> "($left !== $right)"
      JavaIrBinaryOperator.BITWISE_AND -> "(($left & $right) | 0)"
      JavaIrBinaryOperator.BITWISE_XOR -> "(($left ^ $right) | 0)"
      JavaIrBinaryOperator.BITWISE_OR -> "(($left | $right) | 0)"
      JavaIrBinaryOperator.LOGICAL_AND -> "($left && $right)"
      JavaIrBinaryOperator.LOGICAL_OR -> "($left || $right)"
    }
  }

  /** 生成已消除前后缀副作用的一元表达式。 */
  private fun renderUnary(expression: JavaIrExpression.Unary): String {
    val operand = renderExpression(expression.operand)
    return when (expression.operator) {
      JavaIrUnaryOperator.NEGATE -> "(-$operand | 0)"
      JavaIrUnaryOperator.LOGICAL_NOT -> "(!$operand)"
      JavaIrUnaryOperator.BITWISE_NOT -> "(~$operand | 0)"
    }
  }

  /** 将 lowering 明确给出的转换渲染为 JavaScript 中对应的安全表示。 */
  private fun renderConversion(expression: JavaIrExpression.Convert): String {
    val code = renderExpression(expression.expression)
    return when (expression.conversion) {
      JavaIrConversion.Identity,
      is JavaIrConversion.ReferenceWidening -> code
      is JavaIrConversion.PrimitiveWidening -> if (expression.type.isStage0Integral()) {
        "($code | 0)"
      } else {
        code
      }
      is JavaIrConversion.Boxing,
      is JavaIrConversion.Unboxing -> error("Validated Java IR cannot contain boxing conversions.")
    }
  }

  /** 常量使用 JSON 兼容转义，整数文本保持不经过 JavaScript Number 重新格式化。 */
  private fun renderConstant(constant: JavaIrConstant): String = when (constant) {
    is JavaIrConstant.BooleanValue -> constant.value.toString()
    is JavaIrConstant.IntValue -> constant.value.toString()
    is JavaIrConstant.StringValue -> writer.stringLiteral(constant.value)
    JavaIrConstant.NullValue -> "null"
    is JavaIrConstant.LongValue -> error("Validated Java IR cannot contain long constants.")
  }

  /** 对 int-like 本地变量或参数写入统一加上 Java 32 位截断。 */
  private fun coerceToType(code: String, type: JavaIrType): String {
    return if (type.isStage0Integral()) "($code | 0)" else code
  }

  /** 入口绑定已由 validator 验证唯一性，这里只执行确定性索引读取。 */
  private fun resolveEntryMethod(): JavaIrMethod {
    val owner = index.program.classes.single { clazz ->
      clazz.qualifiedName == entryPoint.qualifiedClassName
    }
    return owner.methods.single { method ->
      method.name == entryPoint.methodName &&
        method.descriptor == entryPoint.descriptor &&
        method.dispatch == JavaIrDispatchKind.STATIC
    }
  }
}

/** 阶段 0 使用 JavaScript 的 signed int32 承载 byte、short、char 和 int 的运行时值。 */
private fun JavaIrType.isStage0Integral(): Boolean {
  return this is JavaIrType.Primitive && kind.name.isStage0IntegralPrimitiveName()
}

/** long、float、double 需要不同 runtime representation，不得静默按 Number 降级。 */
private fun String.isStage0IntegralPrimitiveName(): Boolean {
  return this == "BYTE" || this == "SHORT" || this == "CHAR" || this == "INT"
}
