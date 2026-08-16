package com.cyxbs.functions.code.language.java.compiler.lowering

import com.cyxbs.functions.code.language.java.compiler.JavaIrLowerer
import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.diagnostic.*
import com.cyxbs.functions.code.language.java.compiler.ir.*
import com.cyxbs.functions.code.language.java.compiler.semantic.*
import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * 阶段 0 的 AST 到 typed IR lowering。
 *
 * AST 名称只通过 semantic side table 解析一次；输出 IR 因此只含稳定 ID、类型和已选定调用目标，
 * JavaScript 后端不必回读 AST 或语义模型。
 */
internal object JavaAstToIrLowerer : JavaIrLowerer {
  override fun lower(semanticModel: JavaSemanticModel): JavaCompilerPhaseResult<JavaIrProgram> {
    val lowering = JavaLowering(semanticModel)
    val program = lowering.lower()
    return if (lowering.failed) {
      JavaCompilerPhaseResult.failure(lowering.diagnostics)
    } else {
      JavaCompilerPhaseResult.success(checkNotNull(program), lowering.diagnostics)
    }
  }
}

/** 管理一次 lowering 的稳定符号 ID 映射和诊断。 */
private class JavaLowering(
  val model: JavaSemanticModel,
) {
  val diagnostics = mutableListOf<JavaCompilerDiagnostic>()
  val failed: Boolean get() = diagnostics.any { it.severity == JavaDiagnosticSeverity.ERROR }
  private val classIds = mutableMapOf<JavaSymbolId, JavaIrClassId>()
  private val classQualifiedNames = mutableMapOf<JavaSymbolId, String>()
  private var nextSyntheticLocalId = model.symbols.keys.maxOfOrNull { it.value } ?: 0

  /**
   * 在整个 lowering 结果内分配合成 local ID。
   *
   * 从全部语义 symbol 的最大值之后递增，既不会覆盖真实 local，也不会让不同方法的临时变量相撞。
   */
  fun allocateSyntheticLocalId(): JavaIrLocalId = JavaIrLocalId(++nextSyntheticLocalId)

  /** 先登记全部类型 ID，再以源码单元顺序输出类，保证多文件结果确定。 */
  fun lower(): JavaIrProgram? {
    model.ast.units.forEach { unit ->
      unit.types.forEach { type ->
        declaration(type.nodeId, type.span, JavaSymbolKind.TYPE)?.let { symbol ->
          classIds[symbol.id] = JavaIrClassId(symbol.id.value)
          classQualifiedNames[symbol.id] = listOfNotNull(unit.packageName, type.name).joinToString(".")
        }
      }
    }
    val classes = model.ast.units.flatMap { unit ->
      unit.types.mapNotNull { type ->
        val qualifiedName = listOfNotNull(unit.packageName, type.name).joinToString(".")
        lowerClass(type, qualifiedName)
      }
    }
    return if (failed) null else JavaIrProgram(classes)
  }

  /** 阶段 0 class 只容纳无继承、无字段的 static 方法。 */
  private fun lowerClass(type: JavaAstTypeDeclaration, qualifiedName: String): JavaIrClass? {
    if (type.kind != JavaAstTypeDeclarationKind.CLASS) unsupported("Only class declarations are supported.", type.span)
    if (type.superClass != null || type.interfaces.isNotEmpty() || type.typeParameters.isNotEmpty()) {
      unsupported("Inheritance, interfaces and generic classes are not supported.", type.span)
    }
    val symbol = declaration(type.nodeId, type.span, JavaSymbolKind.TYPE) ?: return null
    val methods = type.members.mapNotNull { member ->
      when (member) {
        is JavaAstMemberDeclaration.Method -> {
          if (JavaAstModifier.STATIC !in member.modifiers) {
            unsupported("Only static methods are supported.", member.span)
            null
          } else if (member.body == null) {
            unsupported("Methods without a body are not executable.", member.span)
            null
          } else {
            MethodLowering(this, member, classIds.getValue(symbol.id), declaration(
              member.nodeId,
              member.span,
              JavaSymbolKind.METHOD,
            )).lower()
          }
        }
        is JavaAstMemberDeclaration.Field -> {
          unsupported("Fields are not supported by the stage 0 backend.", member.span); null
        }
        is JavaAstMemberDeclaration.Constructor -> {
          unsupported("Constructors are not supported by the stage 0 backend.", member.span); null
        }
      }
    }
    return JavaIrClass(
      id = classIds.getValue(symbol.id),
      qualifiedName = qualifiedName,
      superClass = null,
      interfaces = emptyList(),
      fields = emptyList(),
      methods = methods,
      staticInitializer = null,
      span = type.span,
    )
  }

  /** 读取并验证 declaration binding。 */
  fun declaration(nodeId: JavaNodeId, span: JavaSourceSpan, kind: JavaSymbolKind): JavaSemanticSymbol? {
    val symbol = model.declarations[nodeId]?.let(model.symbols::get)
    if (symbol == null || symbol.kind != kind) {
      invalid("Missing or invalid semantic declaration binding.", span)
      return null
    }
    return symbol
  }

  /** 读取并验证 resolved symbol binding。 */
  fun resolved(nodeId: JavaNodeId, span: JavaSourceSpan): JavaSemanticSymbol? {
    val symbol = model.resolvedSymbols[nodeId]?.let(model.symbols::get)
    if (symbol == null) invalid("Missing or invalid semantic symbol binding.", span)
    return symbol
  }

  /** 将语义类型擦除为运行时 IR 类型，同时拒绝后端暂不支持的表示。 */
  fun typeOf(type: JavaSemanticType?, span: JavaSourceSpan): JavaIrType? = when (type) {
    is JavaSemanticType.Primitive -> when (type.kind) {
      JavaAstPrimitiveType.LONG -> rejectType("Java long requires a BigInt runtime.", span)
      JavaAstPrimitiveType.FLOAT, JavaAstPrimitiveType.DOUBLE ->
        rejectType("Floating-point values are not supported in stage 0.", span)
      else -> JavaIrType.Primitive(type.kind)
    }
    is JavaSemanticType.Declared -> JavaIrType.Reference(classIds[type.symbol] ?: JavaIrClassId(type.symbol.value))
    JavaSemanticType.Null -> JavaIrType.Null
    JavaSemanticType.Void -> JavaIrType.Void
    is JavaSemanticType.Array -> rejectType("Arrays are not supported in stage 0.", span)
    is JavaSemanticType.TypeVariable, is JavaSemanticType.Wildcard ->
      rejectType("Generic runtime types are not supported in stage 0.", span)
    JavaSemanticType.Error, null -> {
      invalid("Missing or ERROR semantic type.", span); null
    }
  }

  /** 返回表达式的已决议 IR 类型。 */
  fun expressionType(nodeId: JavaNodeId, span: JavaSourceSpan): JavaIrType? =
    typeOf(model.expressionTypes[nodeId], span)

  /**
   * 从已解析语义类型生成 JVM descriptor，禁止使用 AST 中可能未限定的源码拼写。
   *
   * 例如 import 后的 `Helper` 必须稳定生成为 `Llibrary/Helper;`，否则入口和重载身份会漂移。
   */
  fun descriptor(type: JavaSemanticType?, span: JavaSourceSpan): String? {
    return when (type) {
      is JavaSemanticType.Primitive -> when (type.kind) {
        JavaAstPrimitiveType.BOOLEAN -> "Z"
        JavaAstPrimitiveType.BYTE -> "B"
        JavaAstPrimitiveType.SHORT -> "S"
        JavaAstPrimitiveType.INT -> "I"
        JavaAstPrimitiveType.LONG -> "J"
        JavaAstPrimitiveType.CHAR -> "C"
        JavaAstPrimitiveType.FLOAT -> "F"
        JavaAstPrimitiveType.DOUBLE -> "D"
      }
      is JavaSemanticType.Declared -> {
        val qualifiedName = classQualifiedNames[type.symbol]
          ?: model.symbols[type.symbol]?.takeIf {
            it.kind == JavaSymbolKind.TYPE && it.name == "String"
          }?.let { "java.lang.String" }
        if (qualifiedName == null) {
          invalid("Missing qualified name for declared descriptor type.", span)
          null
        } else {
          "L" + qualifiedName.replace('.', '/') + ";"
        }
      }
      is JavaSemanticType.Array -> {
        val component = descriptor(type.componentType, span) ?: return null
        "[" + component
      }
      JavaSemanticType.Void -> "V"
      JavaSemanticType.Null,
      is JavaSemanticType.TypeVariable,
      is JavaSemanticType.Wildcard,
      JavaSemanticType.Error,
      null,
      -> {
        invalid("Unsupported semantic type in method descriptor.", span)
        null
      }
    }
  }

  /** 返回语义已验证的常量。 */
  fun constant(nodeId: JavaNodeId, span: JavaSourceSpan): JavaConstantValue? =
    model.constants[nodeId] ?: run { invalid("Missing semantic constant value.", span); null }

  fun unsupported(message: String, span: JavaSourceSpan) {
    diagnostics += JavaCompilerDiagnostic("JAVA_LOWERING_UNSUPPORTED", message, JavaDiagnosticSeverity.ERROR, span)
  }

  fun invalid(message: String, span: JavaSourceSpan) {
    diagnostics += JavaCompilerDiagnostic(
      "JAVA_LOWERING_INVALID_SEMANTIC_MODEL",
      message,
      JavaDiagnosticSeverity.ERROR,
      span,
    )
  }

  private fun rejectType(message: String, span: JavaSourceSpan): Nothing? {
    unsupported(message, span)
    return null
  }
}

/** 降低一个 static 方法，并为 return postfix 结果分配稳定的合成局部变量。 */
private class MethodLowering(
  private val lowering: JavaLowering,
  private val method: JavaAstMemberDeclaration.Method,
  private val owner: JavaIrClassId,
  private val methodSymbol: JavaSemanticSymbol?,
) {
  private val locals = linkedMapOf<JavaSymbolId, JavaIrLocal>()
  private val declared = mutableListOf<JavaIrLocal>()

  /** 完成参数、源码局部变量登记及结构化语句 lowering。 */
  fun lower(): JavaIrMethod? {
    val symbol = methodSymbol ?: return null
    val parameters = method.parameters.mapNotNull { parameter ->
      val parameterSymbol = lowering.declaration(parameter.nodeId, parameter.span, JavaSymbolKind.PARAMETER)
        ?: return@mapNotNull null
      createLocal(parameterSymbol, parameter.name, parameter.span, true)
    }
    collectLocals(checkNotNull(method.body))
    val body = lowerBlock(checkNotNull(method.body))
    return JavaIrMethod(
      id = JavaIrMethodId(symbol.id.value),
      owner = owner,
      name = method.name,
      descriptor = descriptor() ?: return null,
      dispatch = JavaIrDispatchKind.STATIC,
      virtualSlot = null,
      returnType = lowering.typeOf(symbol.type, method.span) ?: return null,
      parameters = parameters,
      locals = declared,
      body = body,
      span = method.span,
    )
  }

  /** 预先登记嵌套 block/if/while/for 中的源码局部变量，Name lowering 因此无遍历顺序依赖。 */
  private fun collectLocals(statement: JavaAstStatement) {
    when (statement) {
      is JavaAstStatement.Block -> statement.statements.forEach(::collectLocals)
      is JavaAstStatement.VariableDeclaration -> statement.declarators.forEach(::register)
      is JavaAstStatement.If -> { collectLocals(statement.thenBranch); statement.elseBranch?.let(::collectLocals) }
      is JavaAstStatement.While -> collectLocals(statement.body)
      is JavaAstStatement.For -> {
        (statement.initializer as? JavaAstForInitializer.VariableDeclaration)
          ?.declarators?.forEach(::register)
        collectLocals(statement.body)
      }
      else -> Unit
    }
  }

  /** 使用 semantic symbol id 作为 local id，禁止根据 AST 遍历次序分配。 */
  private fun register(declarator: JavaAstVariableDeclarator) {
    val symbol = lowering.declaration(declarator.nodeId, declarator.span, JavaSymbolKind.LOCAL_VARIABLE) ?: return
    if (!locals.containsKey(symbol.id)) createLocal(symbol, declarator.name, declarator.span, false)
  }

  private fun createLocal(
    symbol: JavaSemanticSymbol,
    name: String,
    span: JavaSourceSpan,
    parameter: Boolean,
  ): JavaIrLocal? {
    val local = JavaIrLocal(
      id = JavaIrLocalId(symbol.id.value),
      nameHint = name,
      type = lowering.typeOf(symbol.type, span) ?: return null,
      isParameter = parameter,
      span = span,
    )
    locals[symbol.id] = local
    if (!parameter) declared += local
    return local
  }

  private fun lowerBlock(block: JavaAstStatement.Block): JavaIrStatement.Block =
    JavaIrStatement.Block(block.statements.flatMap(::lowerStatement), block.span)

  /** classic for 统一规范化为 initializer block、while 和尾部 updates。 */
  private fun lowerStatement(statement: JavaAstStatement): List<JavaIrStatement> = when (statement) {
    is JavaAstStatement.Block -> listOf(lowerBlock(statement))
    is JavaAstStatement.VariableDeclaration -> lowerDeclarations(statement.declarators, statement.span)
    is JavaAstStatement.Expression -> expressionStatement(statement.expression, statement.span)
    is JavaAstStatement.If -> expression(statement.condition)?.let {
      listOf(JavaIrStatement.If(it, branch(statement.thenBranch), statement.elseBranch?.let(::branch), statement.span))
    } ?: emptyList()
    is JavaAstStatement.While -> expression(statement.condition)?.let {
      listOf(JavaIrStatement.While(it, branch(statement.body), statement.span))
    } ?: emptyList()
    is JavaAstStatement.For -> lowerFor(statement)
    is JavaAstStatement.Return -> lowerReturn(statement)
    is JavaAstStatement.Empty -> emptyList()
  }

  private fun branch(statement: JavaAstStatement): JavaIrStatement =
    if (statement is JavaAstStatement.Block) lowerBlock(statement)
    else JavaIrStatement.Block(lowerStatement(statement), statement.span)

  /** 处理局部声明；postfix initializer 拆为 old-value declaration 再 write-back。 */
  private fun lowerDeclarations(
    declarators: List<JavaAstVariableDeclarator>,
    span: JavaSourceSpan,
  ): List<JavaIrStatement> = declarators.flatMap { declarator ->
    val local = lowering.declaration(declarator.nodeId, declarator.span, JavaSymbolKind.LOCAL_VARIABLE)
      ?.let { symbol -> locals[symbol.id] } ?: return@flatMap emptyList<JavaIrStatement>()
    val initializer = declarator.initializer
    if (initializer is JavaAstExpression.Unary && initializer.operator.isPostfix()) {
      val target = localTarget(initializer.operand) ?: return@flatMap emptyList<JavaIrStatement>()
      listOf(
        JavaIrStatement.DeclareLocal(local.id, read(target, initializer.operand.span), span),
        JavaIrStatement.Expression(writeIncrement(target, initializer.operator, initializer.span), initializer.span),
      )
    } else {
      listOf(JavaIrStatement.DeclareLocal(local.id, initializer?.let(::expression), span))
    }
  }

  private fun lowerFor(statement: JavaAstStatement.For): List<JavaIrStatement> {
    val init = when (val initializer = statement.initializer) {
      null -> emptyList()
      is JavaAstForInitializer.VariableDeclaration -> lowerDeclarations(initializer.declarators, initializer.span)
      is JavaAstForInitializer.Expressions -> initializer.expressions.flatMap { expressionStatement(it, it.span) }
    }
    val condition = statement.condition?.let(::expression) ?: JavaIrExpression.Constant(
      JavaIrConstant.BooleanValue(true),
      JavaIrType.Primitive(JavaAstPrimitiveType.BOOLEAN),
      statement.span,
    )
    val body = when (val sourceBody = statement.body) {
      is JavaAstStatement.Block -> lowerBlock(sourceBody).statements.toMutableList()
      else -> lowerStatement(sourceBody).toMutableList()
    }
    statement.updates.forEach { update -> body += expressionStatement(update, update.span) }
    return listOf(
      JavaIrStatement.Block(
        init + JavaIrStatement.While(condition, JavaIrStatement.Block(body, statement.body.span), statement.span),
        statement.span,
      ),
    )
  }

  /** return postfix 使用合成 temp，保证先保存旧值、完成写回、最后返回旧值。 */
  private fun lowerReturn(statement: JavaAstStatement.Return): List<JavaIrStatement> {
    val value = statement.expression ?: return listOf(JavaIrStatement.Return(null, statement.span))
    if (value is JavaAstExpression.Unary && value.operator.isPostfix()) {
      val target = localTarget(value.operand) ?: return emptyList()
      val temporary = temporary(lowering.expressionType(value.nodeId, value.span) ?: return emptyList(), value.span)
      return listOf(
        JavaIrStatement.DeclareLocal(temporary.id, read(target, value.operand.span), value.span),
        JavaIrStatement.Expression(writeIncrement(target, value.operator, value.span), value.span),
        JavaIrStatement.Return(read(temporary, value.span), statement.span),
      )
    }
    return expression(value)?.let { listOf(JavaIrStatement.Return(it, statement.span)) } ?: emptyList()
  }

  /** 独立 postfix 结果被丢弃，可直接写回；其他嵌套 postfix 均拒绝以免打乱左到右求值顺序。 */
  private fun expressionStatement(expression: JavaAstExpression, span: JavaSourceSpan): List<JavaIrStatement> {
    if (expression is JavaAstExpression.Unary && expression.operator.isPostfix()) {
      val target = localTarget(expression.operand) ?: return emptyList()
      return listOf(JavaIrStatement.Expression(writeIncrement(target, expression.operator, expression.span), span))
    }
    return expression(expression)?.let { listOf(JavaIrStatement.Expression(it, span)) } ?: emptyList()
  }

  /** 转换 AST expression，并显式写入 semantic conversion。 */
  private fun expression(source: JavaAstExpression): JavaIrExpression? {
    val type = lowering.expressionType(source.nodeId, source.span) ?: return null
    val raw = when (source) {
      is JavaAstExpression.Literal -> literal(source, type)
      is JavaAstExpression.Name -> name(source, type)
      is JavaAstExpression.Binary -> binary(source, type)
      is JavaAstExpression.Unary -> unary(source, type)
      is JavaAstExpression.Assignment -> assignment(source, type)
      is JavaAstExpression.MethodInvocation -> invocation(source, type)
      is JavaAstExpression.Parenthesized -> expression(source.expression)
      is JavaAstExpression.This -> reject("this is not supported.", source.span)
      is JavaAstExpression.FieldAccess -> reject("Field access is not supported.", source.span)
      is JavaAstExpression.NewObject -> reject("Object creation is not supported.", source.span)
    } ?: return null
    return conversion(source, raw, type)
  }

  /** 常量只接受 semantic table 的值，避免重复实现 Java 字面量解析。 */
  private fun literal(source: JavaAstExpression.Literal, type: JavaIrType): JavaIrExpression? {
    val value = when (val constant = lowering.constant(source.nodeId, source.span) ?: return null) {
      is JavaConstantValue.BooleanValue -> JavaIrConstant.BooleanValue(constant.value)
      is JavaConstantValue.IntValue -> JavaIrConstant.IntValue(constant.value)
      is JavaConstantValue.StringValue -> JavaIrConstant.StringValue(constant.value)
      JavaConstantValue.NullValue -> JavaIrConstant.NullValue
      is JavaConstantValue.LongValue -> return reject("Java long is not supported.", source.span)
    }
    return JavaIrExpression.Constant(value, type, source.span)
  }

  private fun name(source: JavaAstExpression.Name, type: JavaIrType): JavaIrExpression? {
    val symbol = lowering.resolved(source.nodeId, source.span) ?: return null
    val local = locals[symbol.id] ?: return reject("Only local-variable names are supported.", source.span)
    return JavaIrExpression.GetLocal(local.id, type, source.span)
  }

  private fun binary(source: JavaAstExpression.Binary, type: JavaIrType): JavaIrExpression? =
    JavaIrExpression.Binary(
      expression(source.left) ?: return null,
      source.operator.toIr(),
      expression(source.right) ?: return null,
      type,
      source.span,
    )

  /** prefix 更新返回 SetLocal 的新值；postfix 只在 statement/declaration/return 中安全拆分。 */
  private fun unary(source: JavaAstExpression.Unary, type: JavaIrType): JavaIrExpression? = when (source.operator) {
    JavaAstUnaryOperator.POSITIVE -> expression(source.operand)
    JavaAstUnaryOperator.NEGATIVE -> JavaIrExpression.Unary(JavaIrUnaryOperator.NEGATE, expression(source.operand) ?: return null, type, source.span)
    JavaAstUnaryOperator.LOGICAL_NOT -> JavaIrExpression.Unary(JavaIrUnaryOperator.LOGICAL_NOT, expression(source.operand) ?: return null, type, source.span)
    JavaAstUnaryOperator.BITWISE_NOT -> JavaIrExpression.Unary(JavaIrUnaryOperator.BITWISE_NOT, expression(source.operand) ?: return null, type, source.span)
    JavaAstUnaryOperator.PRE_INCREMENT, JavaAstUnaryOperator.PRE_DECREMENT ->
      writeIncrement(localTarget(source.operand) ?: return null, source.operator, source.span)
    JavaAstUnaryOperator.POST_INCREMENT, JavaAstUnaryOperator.POST_DECREMENT ->
      reject("Nested postfix ++/-- is not supported in stage 0.", source.span)
  }

  /** 复合赋值读取 target 的旧值、计算 binary 结果再 SetLocal，保持 Java assignment expression 返回值。 */
  private fun assignment(source: JavaAstExpression.Assignment, type: JavaIrType): JavaIrExpression? {
    val target = localTarget(source.target) ?: return null
    val right = expression(source.value) ?: return null
    val value = if (source.operator == JavaAstAssignmentOperator.ASSIGN) right else {
      JavaIrExpression.Binary(read(target, source.target.span), source.operator.toIr(), right, target.type, source.span)
    }
    return JavaIrExpression.SetLocal(target.id, value, type, source.span)
  }

  /**
   * 调用只使用 semantic selected callable，并只允许 static dispatch。
   *
   * TypeName receiver 是编译期限定符，不求值也不进入 IR；其他 receiver 即使语义模型被伪造也会拒绝。
   */
  private fun invocation(source: JavaAstExpression.MethodInvocation, type: JavaIrType): JavaIrExpression? {
    source.receiver?.let { receiver ->
      if (receiver !is JavaAstExpression.Name) return reject("Only TypeName static receivers are supported.", receiver.span)
      val receiverSymbol = lowering.resolved(receiver.nodeId, receiver.span) ?: return null
      if (receiverSymbol.kind != JavaSymbolKind.TYPE) {
        return reject("Static call receiver must resolve to a type.", receiver.span)
      }
    }
    val binding = lowering.model.selectedCallables[source.nodeId]
      ?: return invalid("Missing selected callable binding.", source.span)
    if (binding.dispatch != JavaDispatchKind.STATIC) return reject("Only static calls are supported.", source.span)
    val method = lowering.model.symbols[binding.symbol]
    if (method?.kind != JavaSymbolKind.METHOD) return invalid("Selected callable is not a method.", source.span)
    val arguments = source.arguments.mapNotNull(::expression)
    if (arguments.size != source.arguments.size) return null
    return JavaIrExpression.InvokeStatic(JavaIrMethodId(binding.symbol.value), arguments, type, source.span)
  }

  /** 把 semantic conversion 原样变为 IR conversion，boxing 仍在 lowering 阶段明确拒绝。 */
  private fun conversion(
    source: JavaAstExpression,
    expression: JavaIrExpression,
    type: JavaIrType,
  ): JavaIrExpression? {
    val semanticConversion = lowering.model.conversions[source.nodeId] ?: return expression
    val irConversion = when (semanticConversion) {
      JavaSemanticConversion.Identity -> JavaIrConversion.Identity
      is JavaSemanticConversion.PrimitiveWidening ->
        JavaIrConversion.PrimitiveWidening(semanticConversion.from, semanticConversion.to)
      is JavaSemanticConversion.ReferenceWidening -> JavaIrConversion.ReferenceWidening(
        lowering.typeOf(semanticConversion.from, source.span) ?: return null,
        lowering.typeOf(semanticConversion.to, source.span) ?: return null,
      )
      is JavaSemanticConversion.Boxing, is JavaSemanticConversion.Unboxing ->
        return reject("Boxing and unboxing are not supported.", source.span)
    }
    return JavaIrExpression.Convert(irConversion, expression, type, source.span)
  }

  private fun localTarget(source: JavaAstExpression): JavaIrLocal? {
    if (source !is JavaAstExpression.Name) return reject("Only simple local assignment targets are supported.", source.span)
    val symbol = lowering.resolved(source.nodeId, source.span) ?: return null
    return locals[symbol.id] ?: reject("Assignment target is not a local variable.", source.span)
  }

  private fun read(local: JavaIrLocal, span: JavaSourceSpan): JavaIrExpression.GetLocal =
    JavaIrExpression.GetLocal(local.id, local.type, span)

  /** 根据 ++/-- 生成 write-back；postfix 的旧值处理由上层 statement lowering 负责。 */
  private fun writeIncrement(local: JavaIrLocal, operator: JavaAstUnaryOperator, span: JavaSourceSpan): JavaIrExpression.SetLocal {
    val binary = if (operator == JavaAstUnaryOperator.PRE_DECREMENT || operator == JavaAstUnaryOperator.POST_DECREMENT) {
      JavaIrBinaryOperator.SUBTRACT
    } else {
      JavaIrBinaryOperator.ADD
    }
    val one = JavaIrExpression.Constant(JavaIrConstant.IntValue(1), local.type, span)
    return JavaIrExpression.SetLocal(
      local.id,
      JavaIrExpression.Binary(read(local, span), binary, one, local.type, span),
      local.type,
      span,
    )
  }

  /** 合成 temp 仅用于 return postfix；ID 来自全局分配器，避免与真实 symbol ID 冲突。 */
  private fun temporary(type: JavaIrType, span: JavaSourceSpan): JavaIrLocal =
    JavaIrLocal(lowering.allocateSyntheticLocalId(), "postfixResult", type, false, span)
      .also(declared::add)

  /** 方法身份由语义已解析的参数与返回类型生成，源码 simple name 不参与 descriptor。 */
  private fun descriptor(): String? {
    val parameterDescriptors = method.parameters.map { parameter ->
      val symbol = lowering.declaration(parameter.nodeId, parameter.span, JavaSymbolKind.PARAMETER)
        ?: return null
      lowering.descriptor(symbol.type, parameter.span) ?: return null
    }
    val returnDescriptor = lowering.descriptor(methodSymbol?.type, method.span) ?: return null
    return "(" + parameterDescriptors.joinToString("") + ")" + returnDescriptor
  }

  private fun JavaAstUnaryOperator.isPostfix(): Boolean =
    this == JavaAstUnaryOperator.POST_INCREMENT || this == JavaAstUnaryOperator.POST_DECREMENT

  private fun JavaAstBinaryOperator.toIr(): JavaIrBinaryOperator = JavaIrBinaryOperator.valueOf(name)

  private fun JavaAstAssignmentOperator.toIr(): JavaIrBinaryOperator = when (this) {
    JavaAstAssignmentOperator.MULTIPLY_ASSIGN -> JavaIrBinaryOperator.MULTIPLY
    JavaAstAssignmentOperator.DIVIDE_ASSIGN -> JavaIrBinaryOperator.DIVIDE
    JavaAstAssignmentOperator.REMAINDER_ASSIGN -> JavaIrBinaryOperator.REMAINDER
    JavaAstAssignmentOperator.ADD_ASSIGN -> JavaIrBinaryOperator.ADD
    JavaAstAssignmentOperator.SUBTRACT_ASSIGN -> JavaIrBinaryOperator.SUBTRACT
    JavaAstAssignmentOperator.SHIFT_LEFT_ASSIGN -> JavaIrBinaryOperator.SHIFT_LEFT
    JavaAstAssignmentOperator.SHIFT_RIGHT_ASSIGN -> JavaIrBinaryOperator.SHIFT_RIGHT
    JavaAstAssignmentOperator.UNSIGNED_SHIFT_RIGHT_ASSIGN -> JavaIrBinaryOperator.UNSIGNED_SHIFT_RIGHT
    JavaAstAssignmentOperator.AND_ASSIGN -> JavaIrBinaryOperator.BITWISE_AND
    JavaAstAssignmentOperator.XOR_ASSIGN -> JavaIrBinaryOperator.BITWISE_XOR
    JavaAstAssignmentOperator.OR_ASSIGN -> JavaIrBinaryOperator.BITWISE_OR
    JavaAstAssignmentOperator.ASSIGN -> error("Simple assignment has no binary operator.")
  }

  private fun reject(message: String, span: JavaSourceSpan): Nothing? { lowering.unsupported(message, span); return null }
  private fun invalid(message: String, span: JavaSourceSpan): Nothing? { lowering.invalid(message, span); return null }
}
