package com.cyxbs.functions.code.language.java.compiler.lowering

import com.cyxbs.functions.code.language.java.compiler.JavaIrLowerer
import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.diagnostic.*
import com.cyxbs.functions.code.language.java.compiler.ir.*
import com.cyxbs.functions.code.language.java.compiler.semantic.*
import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * 阶段 1 的 AST + semantic model 到 typed IR lowering。
 *
 * 声明身份、值访问、调用分派和构造委托只读取语义 side table；源码名称仅作为调试提示。
 */
internal object JavaAstToIrLowerer : JavaIrLowerer {
  override fun lower(semanticModel: JavaSemanticModel): JavaCompilerPhaseResult<JavaIrProgram> {
    val lowering = JavaLowering(semanticModel)
    val program = lowering.lower()
    return if (program == null || lowering.failed) {
      JavaCompilerPhaseResult.failure(lowering.diagnostics)
    } else {
      JavaCompilerPhaseResult.success(program, lowering.diagnostics)
    }
  }
}

/** 管理一次 lowering 的全局语义索引、擦除关系、合成 ID 和结构化诊断。 */
private class JavaLowering(val model: JavaSemanticModel) {
  val diagnostics = mutableListOf<JavaCompilerDiagnostic>()
  val failed: Boolean get() = diagnostics.any { it.severity == JavaDiagnosticSeverity.ERROR }
  private val sourceTypes = mutableMapOf<JavaSymbolId, JavaAstTypeDeclaration>()
  private val sourceMembers = mutableMapOf<JavaSymbolId, JavaAstMemberDeclaration>()
  private val sourceFields = mutableMapOf<JavaSymbolId, JavaAstVariableDeclarator>()
  private val classIds = model.typeDeclarations.keys.associateWith { JavaIrClassId(it.value) }
  private val objectSymbol = model.typeDeclarations.values.singleOrNull {
    it.kind == JavaSemanticTypeDeclarationKind.BUILTIN && it.qualifiedName == "java.lang.Object"
  }?.symbol
  private val relations = objectSymbol?.let {
    JavaTypeRelations(model.typeDeclarations, model.typeParameterDeclarations, it)
  }
  private var nextSyntheticLocalId = model.symbols.keys.maxOfOrNull { it.value } ?: 0

  /** 合成 local 使用全工作区单调 ID，避免不同方法及真实 symbol 互相冲突。 */
  fun allocateSyntheticLocalId(): JavaIrLocalId = JavaIrLocalId(++nextSyntheticLocalId)

  /** 先建立反向源码索引，再按 compilation-unit 顺序生成用户类。 */
  fun lower(): JavaIrProgram? {
    indexSources()
    val classes = model.ast.units.flatMap { it.types.mapNotNull(::lowerClass) }
    return if (failed) null else JavaIrProgram(classes)
  }

  /** 反向索引完全依赖 declarations，后续禁止按成员名称查找。 */
  private fun indexSources() {
    model.ast.units.forEach { unit ->
      unit.types.forEach { type ->
        declaration(type.nodeId, type.span, JavaSymbolKind.TYPE)?.let {
          sourceTypes[it.id] = type
        }
        type.members.forEach { member ->
          when (member) {
            is JavaAstMemberDeclaration.Field -> member.declarators.forEach { declarator ->
              declaration(declarator.nodeId, declarator.span, JavaSymbolKind.FIELD)?.let {
                sourceFields[it.id] = declarator
              }
            }
            is JavaAstMemberDeclaration.Method ->
              declaration(member.nodeId, member.span, JavaSymbolKind.METHOD)?.let {
                sourceMembers[it.id] = member
              }
            is JavaAstMemberDeclaration.Constructor ->
              declaration(member.nodeId, member.span, JavaSymbolKind.CONSTRUCTOR)?.let {
                sourceMembers[it.id] = member
              }
          }
        }
      }
    }
  }

  /** 类头和成员均来自 semantic declarations；接口能力在本阶段显式延期。 */
  private fun lowerClass(source: JavaAstTypeDeclaration): JavaIrClass? {
    val symbol = declaration(source.nodeId, source.span, JavaSymbolKind.TYPE) ?: return null
    val declaration = model.typeDeclarations[symbol.id]
      ?: return invalid("Missing semantic type declaration.", source.span)
    if (declaration.symbol != symbol.id ||
      declaration.kind != JavaSemanticTypeDeclarationKind.CLASS ||
      source.kind != JavaAstTypeDeclarationKind.CLASS
    ) {
      unsupported("Only semantic class declarations are executable in stage 1.", source.span)
      return null
    }
    if (source.interfaces.isNotEmpty()) {
      unsupported("Interfaces and implements clauses are deferred beyond stage 1.", source.span)
      return null
    }
    val owner = classIds[symbol.id] ?: return invalid("Missing class id.", source.span)
    val fields = declaration.membersInSourceOrder.mapNotNull { id ->
      model.fieldDeclarations[id]?.let { lowerField(owner, it) }
    }.sortedBy { model.fieldDeclarations[JavaSymbolId(it.id.value)]?.declarationOrder }
    val sourceCallableIds = declaration.membersInSourceOrder.filter(model.callableDeclarations::containsKey)
    val syntheticConstructors = model.callableDeclarations.values.filter {
      it.owner == symbol.id && it.kind == JavaSemanticCallableKind.CONSTRUCTOR &&
        it.symbol !in sourceCallableIds
    }.sortedBy { it.symbol.value }
    val callables = sourceCallableIds.mapNotNull(model.callableDeclarations::get) + syntheticConstructors
    val methods = callables.mapNotNull { lowerCallable(owner, declaration, it, source.span) }
    declaration.membersInSourceOrder.forEach { id ->
      val field = model.fieldDeclarations[id]
      val callable = model.callableDeclarations[id]
      if ((field == null) == (callable == null) ||
        (field?.owner ?: callable?.owner) != declaration.symbol
      ) invalid("Invalid semantic source member declaration.", source.span)
    }
    return JavaIrClass(
      owner, declaration.qualifiedName, lowerSuper(declaration, source.span), emptyList(),
      fields, methods, null, source.span, null,
    )
  }

  /** Object 不进入用户类列表并作为运行时根；泛型父类先擦除。 */
  private fun lowerSuper(
    declaration: JavaSemanticTypeDeclaration,
    span: JavaSourceSpan,
  ): JavaIrClassId? {
    val source = declaration.directSuperClass ?: return null
    val erased = erase(source, span) as? JavaSemanticType.Declared ?: return null
    if (erased.symbol == objectSymbol) return null
    if (sourceTypes[erased.symbol] == null) {
      invalid("Superclass is not an emitted source class.", span)
      return null
    }
    return classIds[erased.symbol] ?: run { invalid("Missing superclass id.", span); null }
  }

  /** 字段 initializer 只保存在 field；当前 AST 没有独立 initializer-block，避免重复副作用。 */
  private fun lowerField(
    owner: JavaIrClassId,
    declaration: JavaSemanticFieldDeclaration,
  ): JavaIrField? {
    val symbol = symbol(declaration.symbol, JavaSymbolKind.FIELD, null) ?: return null
    if (declaration.owner.value != owner.value) {
      return invalid("Field owner does not match class.", symbol.declarationSpan)
    }
    val source = sourceFields[declaration.symbol]
      ?: return invalid("Missing source field declarator.", symbol.declarationSpan)
    val type = typeOf(declaration.type, source.span) ?: return null
    val initializer = source.initializer?.let {
      BodyLowering(this, owner, null, !declaration.isStatic).expression(it)
    }
    return JavaIrField(
      JavaIrFieldId(declaration.symbol.value), owner, symbol.name, type,
      declaration.isStatic, initializer, source.span,
    )
  }

  /** 普通方法、显式构造器和语义合成 default constructor 共用同一入口。 */
  private fun lowerCallable(
    owner: JavaIrClassId,
    ownerDeclaration: JavaSemanticTypeDeclaration,
    declaration: JavaSemanticCallableDeclaration,
    fallbackSpan: JavaSourceSpan,
  ): JavaIrMethod? {
    val symbolKind = if (declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR) {
      JavaSymbolKind.CONSTRUCTOR
    } else JavaSymbolKind.METHOD
    val symbol = symbol(declaration.symbol, symbolKind, fallbackSpan) ?: return null
    if (declaration.owner != ownerDeclaration.symbol) {
      return invalid("Callable owner does not match class.", symbol.declarationSpan)
    }
    val source = sourceMembers[declaration.symbol]
    val parameters = when (source) {
      is JavaAstMemberDeclaration.Method -> source.parameters
      is JavaAstMemberDeclaration.Constructor -> source.parameters
      null -> emptyList()
      else -> return invalid("Invalid callable source.", symbol.declarationSpan)
    }
    val bodyLowering = BodyLowering(this, owner, declaration, !declaration.isStatic)
    val irParameters = bodyLowering.registerParameters(parameters) ?: return null
    val sourceBody = when (source) {
      is JavaAstMemberDeclaration.Method -> source.body
      is JavaAstMemberDeclaration.Constructor -> source.body
      else -> null
    }
    val body = if (declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR) {
      bodyLowering.lowerConstructor(sourceBody, symbol.declarationSpan)
    } else if (sourceBody == null || declaration.isAbstract) {
      unsupported("Methods without executable bodies are deferred beyond stage 1.", symbol.declarationSpan)
      null
    } else bodyLowering.lowerBlock(sourceBody)
    val slot = model.virtualSlots[declaration.symbol]?.value
    val dispatch = when {
      declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR -> JavaIrDispatchKind.SPECIAL
      declaration.isStatic -> JavaIrDispatchKind.STATIC
      slot != null -> JavaIrDispatchKind.VIRTUAL
      else -> JavaIrDispatchKind.SPECIAL
    }
    val returnType = if (declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR) {
      JavaIrType.Void
    } else typeOf(declaration.returnType, symbol.declarationSpan) ?: return null
    return JavaIrMethod(
      JavaIrMethodId(declaration.symbol.value), owner,
      if (declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR) "<init>" else symbol.name,
      declaration.erasedDescriptor, dispatch, slot, returnType, irParameters,
      bodyLowering.locals, body, symbol.declarationSpan,
      if (declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR) {
        JavaIrMethodKind.CONSTRUCTOR
      } else JavaIrMethodKind.METHOD,
    )
  }

  /** 验证 AST declaration binding 的 symbol 种类与 declarationNode。 */
  fun declaration(
    nodeId: JavaNodeId,
    span: JavaSourceSpan,
    kind: JavaSymbolKind,
  ): JavaSemanticSymbol? {
    val symbol = model.declarations[nodeId]?.let(model.symbols::get)
    if (symbol == null || symbol.kind != kind || symbol.declarationNode != nodeId) {
      invalid("Missing or invalid semantic declaration binding.", span)
      return null
    }
    return symbol
  }

  fun symbol(
    id: JavaSymbolId,
    kind: JavaSymbolKind,
    span: JavaSourceSpan?,
  ): JavaSemanticSymbol? {
    val symbol = model.symbols[id]
    if (symbol == null || symbol.kind != kind) {
      invalid("Missing or invalid semantic symbol.", span)
      return null
    }
    return symbol
  }

  /** primitive/reference/array/type-variable 统一先擦除再生成运行时 IR 类型。 */
  fun typeOf(type: JavaSemanticType?, span: JavaSourceSpan): JavaIrType? {
    if (type == null || type == JavaSemanticType.Error) {
      return invalid("Missing or ERROR semantic type.", span)
    }
    // null 没有 JVM 擦除类型，但 IR 必须保留它，供引用赋值、比较和重载结果继续降级。
    if (type == JavaSemanticType.Null) return JavaIrType.Null
    return irTypeOfErased(erase(type, span) ?: return null, span)
  }

  /** 已擦除类型递归转 IR 时不再次调用 erasure，避免参数化数组组件被误判为 raw type。 */
  private fun irTypeOfErased(erased: JavaSemanticType, span: JavaSourceSpan): JavaIrType? =
    when (erased) {
      is JavaSemanticType.Primitive -> JavaIrType.Primitive(erased.kind)
      is JavaSemanticType.Declared -> classIds[erased.symbol]?.let(JavaIrType::Reference)
        ?: invalid("Erased declared type is missing its semantic declaration.", span)
      is JavaSemanticType.Array ->
        irTypeOfErased(erased.componentType, span)?.let(JavaIrType::Array)
      JavaSemanticType.Null -> JavaIrType.Null
      JavaSemanticType.Void -> JavaIrType.Void
      is JavaSemanticType.TypeVariable,
      is JavaSemanticType.Wildcard,
      JavaSemanticType.Error,
      -> invalid("Semantic type did not erase to a runtime type.", span)
    }

  /** 无泛型的旧模型可直接通过；类型变量和参数化类型必须由 TypeRelations 擦除。 */
  private fun erase(type: JavaSemanticType, span: JavaSourceSpan): JavaSemanticType? {
    val result = relations?.erasure(type) ?: when (type) {
      is JavaSemanticType.Primitive, JavaSemanticType.Void -> type
      is JavaSemanticType.Declared -> if (type.arguments.isEmpty()) type else null
      is JavaSemanticType.Array -> erase(type.componentType, span)?.let(JavaSemanticType::Array)
      else -> null
    }
    if (result == null) invalid("Cannot erase semantic type.", span)
    return result
  }

  fun expressionType(nodeId: JavaNodeId, span: JavaSourceSpan): JavaIrType? =
    typeOf(model.expressionTypes[nodeId], span)

  fun constant(nodeId: JavaNodeId, span: JavaSourceSpan): JavaConstantValue? =
    model.constants[nodeId] ?: run { invalid("Missing semantic constant value.", span); null }

  fun unsupported(message: String, span: JavaSourceSpan?) {
    diagnostics += JavaCompilerDiagnostic(
      "JAVA_LOWERING_UNSUPPORTED", message, JavaDiagnosticSeverity.ERROR, span,
    )
  }

  fun invalid(message: String, span: JavaSourceSpan?): Nothing? {
    diagnostics += JavaCompilerDiagnostic(
      "JAVA_LOWERING_INVALID_SEMANTIC_MODEL", message, JavaDiagnosticSeverity.ERROR, span,
    )
    return null
  }
}

/** 方法、构造器或字段 initializer 内的局部环境与求值顺序 lowering。 */
private class BodyLowering(
  private val lowering: JavaLowering,
  private val owner: JavaIrClassId,
  private val callable: JavaSemanticCallableDeclaration?,
  private val allowThis: Boolean,
) {
  private val localBySymbol = linkedMapOf<JavaSymbolId, JavaIrLocal>()
  val locals = mutableListOf<JavaIrLocal>()

  /** AST 参数必须与 callable declaration 的稳定 symbol 顺序逐项一致。 */
  fun registerParameters(source: List<JavaAstParameter>): List<JavaIrLocal>? {
    val declaration = callable ?: return if (source.isEmpty()) emptyList() else null
    if (source.size != declaration.parameters.size) {
      lowering.invalid("Callable parameter count does not match semantic declaration.", source.firstOrNull()?.span)
      return null
    }
    return source.mapIndexed { index, parameter ->
      val symbol = lowering.declaration(parameter.nodeId, parameter.span, JavaSymbolKind.PARAMETER)
        ?: return null
      if (symbol.id != declaration.parameters[index]) {
        lowering.invalid("Parameter order does not match semantic declaration.", parameter.span)
        return null
      }
      createLocal(symbol.id, symbol.name, declaration.parameterTypes[index], true, parameter.span)
        ?: return null
    }
  }

  /** 构造器首句只读取 constructorDelegations，default constructor 使用合成空 body。 */
  fun lowerConstructor(
    sourceBody: JavaAstStatement.Block?,
    span: JavaSourceSpan,
  ): JavaIrStatement.Block? {
    val declaration = callable ?: return lowering.invalid("Missing constructor declaration.", span)
    collectLocals(sourceBody)
    val sourceInvocation =
      sourceBody?.statements?.firstOrNull() as? JavaAstStatement.ConstructorInvocation
    val delegation = lowering.model.constructorDelegations[declaration.symbol]
    val semanticOwner = lowering.model.typeDeclarations[declaration.owner]
    val rootSymbol = lowering.model.typeDeclarations.values.singleOrNull {
      it.kind == JavaSemanticTypeDeclarationKind.BUILTIN && it.qualifiedName == "java.lang.Object"
    }?.symbol
    val needsRuntimeSuper = semanticOwner?.directSuperClass?.symbol?.let { it != rootSymbol } == true
    val statements = mutableListOf<JavaIrStatement>()
    if (delegation == null) {
      if (needsRuntimeSuper || sourceInvocation != null) {
        lowering.invalid("Missing constructor delegation binding.", sourceInvocation?.span ?: span)
      }
    } else if (needsRuntimeSuper || delegation.kind == JavaConstructorDelegationKind.THIS) {
      if (delegation.sourceConstructor != declaration.symbol) {
        lowering.invalid("Constructor delegation source is inconsistent.", span)
      } else {
        val arguments = if (delegation.isImplicit) emptyList() else {
          if (sourceInvocation == null || sourceInvocation.nodeId != delegation.invocationNode) {
            lowering.invalid("Constructor delegation invocation does not match AST.", span)
            emptyList()
          } else sourceInvocation.arguments.mapNotNull(::expression).also {
            if (it.size != sourceInvocation.arguments.size) return null
          }
        }
        val target = lowering.model.callableDeclarations[delegation.targetConstructor]
        if (target?.kind != JavaSemanticCallableKind.CONSTRUCTOR) {
          lowering.invalid("Constructor delegation target is invalid.", span)
        } else {
          statements += JavaIrStatement.ConstructorInvocation(
            if (delegation.kind == JavaConstructorDelegationKind.THIS) {
              JavaIrConstructorInvocationKind.THIS
            } else JavaIrConstructorInvocationKind.SUPER,
            JavaIrMethodId(delegation.targetConstructor.value), arguments,
            sourceInvocation?.span ?: span,
          )
        }
      }
    }
    val ordinary = sourceBody?.statements
      ?.drop(if (sourceInvocation == null) 0 else 1).orEmpty()
    statements += ordinary.flatMap(::lowerStatement)
    return JavaIrStatement.Block(statements, sourceBody?.span ?: span)
  }

  /** 预登记所有嵌套 block/if/loop 的源码局部，读取不依赖遍历先后。 */
  private fun collectLocals(statement: JavaAstStatement?) {
    when (statement) {
      is JavaAstStatement.Block -> statement.statements.forEach(::collectLocals)
      is JavaAstStatement.VariableDeclaration -> statement.declarators.forEach(::registerLocal)
      is JavaAstStatement.If -> {
        collectLocals(statement.thenBranch)
        collectLocals(statement.elseBranch)
      }
      is JavaAstStatement.While -> collectLocals(statement.body)
      is JavaAstStatement.For -> {
        (statement.initializer as? JavaAstForInitializer.VariableDeclaration)
          ?.declarators?.forEach(::registerLocal)
        collectLocals(statement.body)
      }
      else -> Unit
    }
  }

  private fun registerLocal(source: JavaAstVariableDeclarator) {
    val symbol = lowering.declaration(source.nodeId, source.span, JavaSymbolKind.LOCAL_VARIABLE) ?: return
    if (localBySymbol[symbol.id] == null) {
      createLocal(symbol.id, symbol.name, symbol.type, false, source.span)
    }
  }

  private fun createLocal(
    id: JavaSymbolId,
    name: String,
    type: JavaSemanticType?,
    parameter: Boolean,
    span: JavaSourceSpan,
  ): JavaIrLocal? {
    val local = JavaIrLocal(
      JavaIrLocalId(id.value), name, lowering.typeOf(type, span) ?: return null, parameter, span,
    )
    localBySymbol[id] = local
    if (!parameter) locals += local
    return local
  }

  /** 降低 block；重复收集是幂等的，便于分支递归调用。 */
  fun lowerBlock(block: JavaAstStatement.Block): JavaIrStatement.Block {
    collectLocals(block)
    return JavaIrStatement.Block(block.statements.flatMap(::lowerStatement), block.span)
  }

  /** classic for 规范化为 block + while；constructor invocation 只能由构造专用入口消费。 */
  private fun lowerStatement(statement: JavaAstStatement): List<JavaIrStatement> = when (statement) {
    is JavaAstStatement.Block -> listOf(lowerBlock(statement))
    is JavaAstStatement.VariableDeclaration -> lowerDeclarations(statement.declarators, statement.span)
    is JavaAstStatement.ConstructorInvocation -> {
      lowering.invalid("Constructor invocation is not the bound first statement.", statement.span)
      emptyList()
    }
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

  /** postfix initializer 先保存旧值到声明 local，再执行一次写回。 */
  private fun lowerDeclarations(
    declarations: List<JavaAstVariableDeclarator>,
    span: JavaSourceSpan,
  ): List<JavaIrStatement> = declarations.flatMap { source ->
    val symbol = lowering.declaration(source.nodeId, source.span, JavaSymbolKind.LOCAL_VARIABLE)
      ?: return@flatMap emptyList()
    val local = localBySymbol[symbol.id] ?: return@flatMap emptyList()
    val initializer = source.initializer
    if (initializer is JavaAstExpression.Unary && initializer.operator.isPostfix()) {
      val prepared = prepareTarget(initializer.operand) ?: return@flatMap emptyList()
      prepared.prefix + listOf(
        JavaIrStatement.DeclareLocal(local.id, read(prepared.target, initializer.span), span),
        JavaIrStatement.Expression(increment(prepared.target, initializer.operator, initializer.span), initializer.span),
      )
    } else listOf(JavaIrStatement.DeclareLocal(local.id, initializer?.let(::expression), span))
  }

  private fun lowerFor(statement: JavaAstStatement.For): List<JavaIrStatement> {
    val init = when (val source = statement.initializer) {
      null -> emptyList()
      is JavaAstForInitializer.VariableDeclaration -> lowerDeclarations(source.declarators, source.span)
      is JavaAstForInitializer.Expressions -> source.expressions.flatMap { expressionStatement(it, it.span) }
    }
    val condition = statement.condition?.let(::expression) ?: JavaIrExpression.Constant(
      JavaIrConstant.BooleanValue(true), JavaIrType.Primitive(JavaAstPrimitiveType.BOOLEAN), statement.span,
    )
    val body = if (statement.body is JavaAstStatement.Block) {
      lowerBlock(statement.body).statements.toMutableList()
    } else lowerStatement(statement.body).toMutableList()
    statement.updates.forEach { body += expressionStatement(it, it.span) }
    return listOf(JavaIrStatement.Block(
      init + JavaIrStatement.While(
        condition, JavaIrStatement.Block(body, statement.body.span), statement.span,
      ),
      statement.span,
    ))
  }

  /** return postfix 为 receiver/旧值建临时 local，保证 receiver 只求值一次。 */
  private fun lowerReturn(statement: JavaAstStatement.Return): List<JavaIrStatement> {
    val value = statement.expression ?: return listOf(JavaIrStatement.Return(null, statement.span))
    if (value is JavaAstExpression.Unary && value.operator.isPostfix()) {
      val prepared = prepareTarget(value.operand) ?: return emptyList()
      val old = temporary(
        lowering.expressionType(value.nodeId, value.span) ?: return emptyList(),
        "postfixResult", value.span,
      )
      return prepared.prefix + listOf(
        JavaIrStatement.DeclareLocal(old.id, read(prepared.target, value.span), value.span),
        JavaIrStatement.Expression(increment(prepared.target, value.operator, value.span), value.span),
        JavaIrStatement.Return(JavaIrExpression.GetLocal(old.id, old.type, value.span), statement.span),
      )
    }
    return expression(value)?.let { listOf(JavaIrStatement.Return(it, statement.span)) } ?: emptyList()
  }

  /** 独立更新/compound 先稳定数组或字段 target，避免 read 与 write 重复求值。 */
  private fun expressionStatement(
    source: JavaAstExpression,
    span: JavaSourceSpan,
  ): List<JavaIrStatement> {
    if (source is JavaAstExpression.Unary && source.operator.isUpdate()) {
      val prepared = prepareTarget(source.operand) ?: return emptyList()
      return prepared.prefix + JavaIrStatement.Expression(
        increment(prepared.target, source.operator, source.span), span,
      )
    }
    if (source is JavaAstExpression.Assignment &&
      source.operator != JavaAstAssignmentOperator.ASSIGN
    ) {
      val prepared = prepareTarget(source.target) ?: return emptyList()
      val right = expression(source.value) ?: return emptyList()
      val value = compoundValue(
        source.nodeId, read(prepared.target, source.target.span), source.operator.toIr(), right,
        prepared.target.type, source.span,
      )
      return prepared.prefix + JavaIrStatement.Expression(
        write(prepared.target, value, prepared.target.type, source.span), span,
      )
    }
    return expression(source)?.let { listOf(JavaIrStatement.Expression(it, span)) } ?: emptyList()
  }

  /** 每个表达式先读取 semantic result type，最后显式落下 conversion。 */
  fun expression(source: JavaAstExpression): JavaIrExpression? {
    val type = lowering.expressionType(source.nodeId, source.span) ?: return null
    val raw = when (source) {
      is JavaAstExpression.Literal -> literal(source, type)
      is JavaAstExpression.Name -> access(source, null, type)
      is JavaAstExpression.FieldAccess -> if (source.nodeId in lowering.model.arrayLengthExpressions) {
        JavaIrExpression.ArrayLength(expression(source.receiver) ?: return null, type, source.span)
      } else access(source, source.receiver, type)
      is JavaAstExpression.This -> thisExpression(source.span)
      is JavaAstExpression.Super -> invalid("Standalone super cannot enter IR.", source.span)
      is JavaAstExpression.Binary -> binary(source, type)
      is JavaAstExpression.Unary -> unary(source, type)
      is JavaAstExpression.Assignment -> assignment(source, type)
      is JavaAstExpression.MethodInvocation -> invocation(source, type)
      is JavaAstExpression.NewObject -> newObject(source, type)
      is JavaAstExpression.NewArray -> newArray(source, type)
      is JavaAstExpression.ArrayAccess -> arrayAccess(source, type)
      is JavaAstExpression.Parenthesized -> expression(source.expression)
  } ?: return null
    return conversion(source, raw, type)
  }

  /** String + 只消费 semantic binding；普通二元运算仍保留既有 typed IR 形式。 */
  private fun binary(source: JavaAstExpression.Binary, type: JavaIrType): JavaIrExpression? {
    val left = expression(source.left) ?: return null
    val right = expression(source.right) ?: return null
    val stringBinding = lowering.model.stringConcatenations[source.nodeId]
    return if (stringBinding == null) {
      JavaIrExpression.Binary(left, source.operator.toIr(), right, type, source.span)
    } else {
      stringConcat(left, stringBinding.leftKind, right, stringBinding.rightKind, type, source.span)
    }
  }

  /** 首批只 lowering 一维创建和扁平 initializer；多维由 semantic 先稳定拒绝。 */
  private fun newArray(source: JavaAstExpression.NewArray, type: JavaIrType): JavaIrExpression? {
    val arrayType = type as? JavaIrType.Array ?: return invalid("Array creation result is not an array.", source.span)
    if (arrayType.componentType is JavaIrType.Array || source.dimensions.size != 1) {
      return unsupported("Multidimensional arrays are deferred.", source.span)
    }
    val initializer = source.initializer
    if (initializer != null) {
      if (source.dimensions.any { it.size != null }) return invalid("Array initializer cannot include a dimension size.", source.span)
      val elements = initializer.elements.map { element ->
        val expression = (element as? JavaAstArrayInitializerElement.Expression)?.expression
          ?: return unsupported("Nested array initializers are deferred.", initializer.span)
        expression(expression) ?: return null
      }
      return JavaIrExpression.ArrayInitializer(
        arrayType.componentType,
        elements,
        arrayType,
        source.span,
        arrayReferenceComponentKind(source.componentType, arrayType.componentType, source.span),
      )
    }
    val length = source.dimensions.single().size ?: return invalid("Array creation is missing its length.", source.span)
    return JavaIrExpression.NewArray(
      arrayType.componentType,
      expression(length) ?: return null,
      arrayType,
      source.span,
      arrayReferenceComponentKind(source.componentType, arrayType.componentType, source.span),
    )
  }

  /** 将 semantic 已解析的引用组件分类传给 runtime，避免后端按 JavaScript 值猜测 String/Object。 */
  private fun arrayReferenceComponentKind(
    source: JavaAstTypeReference,
    componentType: JavaIrType,
    span: JavaSourceSpan,
  ): JavaIrArrayReferenceComponentKind? {
    if (componentType !is JavaIrType.Reference) return null
    val symbol = lowering.model.resolvedSymbols[source.nodeId]
      ?: return invalid("Array component type is missing its semantic symbol.", span)
    val declaration = lowering.model.typeDeclarations[symbol]
      ?: return invalid("Array component type is missing its semantic declaration.", span)
    return when (declaration.qualifiedName) {
      "java.lang.Object" -> JavaIrArrayReferenceComponentKind.OBJECT
      "java.lang.String" -> JavaIrArrayReferenceComponentKind.STRING
      else -> JavaIrArrayReferenceComponentKind.USER_CLASS
    }
  }

  /** 数组下标读取保留 receiver/index 两个独立表达式，后端负责一次求值和检查时序。 */
  private fun arrayAccess(source: JavaAstExpression.ArrayAccess, type: JavaIrType): JavaIrExpression? =
    JavaIrExpression.GetArrayElement(
      expression(source.array) ?: return null,
      expression(source.index) ?: return null,
      type,
      source.span,
    )

  /** 统一构造显式 StringConcat part，禁止 backend 按 JavaScript 类型猜测转换。 */
  private fun stringConcat(
    left: JavaIrExpression,
    leftKind: JavaStringConversionKind,
    right: JavaIrExpression,
    rightKind: JavaStringConversionKind,
    type: JavaIrType,
    span: JavaSourceSpan,
  ): JavaIrExpression.StringConcat = JavaIrExpression.StringConcat(
    listOf(
      JavaIrStringConcatPart(left, JavaIrStringConversionKind.valueOf(leftKind.name)),
      JavaIrStringConcatPart(right, JavaIrStringConversionKind.valueOf(rightKind.name)),
    ),
    type,
    span,
  )

  /**
   * 复合赋值同样必须读取语义提供的 String 转换绑定。
   *
   * [left] 已由调用者按 target 的 Java 求值顺序读取，故此处不能重新 lower target。
   */
  private fun compoundValue(
    nodeId: JavaNodeId,
    left: JavaIrExpression,
    operator: JavaIrBinaryOperator,
    right: JavaIrExpression,
    type: JavaIrType,
    span: JavaSourceSpan,
  ): JavaIrExpression {
    val binding = lowering.model.stringConcatenations[nodeId]
    return if (binding == null) {
      JavaIrExpression.Binary(left, operator, right, type, span)
    } else {
      stringConcat(left, binding.leftKind, right, binding.rightKind, type, span)
    }
  }

  private fun literal(source: JavaAstExpression.Literal, type: JavaIrType): JavaIrExpression? {
    val value = when (val constant = lowering.constant(source.nodeId, source.span) ?: return null) {
      is JavaConstantValue.BooleanValue -> JavaIrConstant.BooleanValue(constant.value)
      is JavaConstantValue.IntValue -> JavaIrConstant.IntValue(constant.value)
      is JavaConstantValue.LongValue -> JavaIrConstant.LongValue(constant.value.toString())
      is JavaConstantValue.StringValue -> JavaIrConstant.StringValue(constant.value)
      JavaConstantValue.NullValue -> JavaIrConstant.NullValue
    }
    return JavaIrExpression.Constant(value, type, source.span)
  }

  /** Name/FieldAccess 均严格消费 valueAccesses，receiverKind 决定是否产生运行时 receiver。 */
  private fun access(
    source: JavaAstExpression,
    explicitReceiver: JavaAstExpression?,
    type: JavaIrType,
  ): JavaIrExpression? {
    val binding = lowering.model.valueAccesses[source.nodeId]
      ?: return invalid("Missing value access binding.", source.span)
    return when (binding.kind) {
      JavaValueAccessKind.LOCAL, JavaValueAccessKind.PARAMETER -> {
        if (explicitReceiver != null || binding.receiverKind != JavaReceiverKind.NONE) {
          return invalid("Local access has an invalid receiver.", source.span)
        }
        val local = localBySymbol[binding.symbol]
          ?: return invalid("Value access references an unknown local.", source.span)
        if (local.isParameter != (binding.kind == JavaValueAccessKind.PARAMETER)) {
          return invalid("Value access kind does not match local declaration kind.", source.span)
        }
        JavaIrExpression.GetLocal(local.id, type, source.span)
      }
      JavaValueAccessKind.STATIC_FIELD -> {
        val field = field(binding, true, source.span) ?: return null
        if (binding.receiverKind == JavaReceiverKind.EXPLICIT) {
          return unsupported("Expression-qualified static fields are deferred.", source.span)
        }
        JavaIrExpression.GetStaticField(JavaIrFieldId(field.symbol.value), type, source.span)
      }
      JavaValueAccessKind.INSTANCE_FIELD -> {
        val field = field(binding, false, source.span) ?: return null
        JavaIrExpression.GetField(
          receiver(binding.receiverKind, explicitReceiver, source.span) ?: return null,
          JavaIrFieldId(field.symbol.value), type, source.span,
        )
      }
    }
  }

  private fun field(
    binding: JavaValueAccessBinding,
    static: Boolean,
    span: JavaSourceSpan,
  ): JavaSemanticFieldDeclaration? {
    val declaration = lowering.model.fieldDeclarations[binding.symbol]
    if (declaration == null || declaration.isStatic != static) {
      lowering.invalid("Missing field declaration or invalid field dispatch.", span)
      return null
    }
    return declaration
  }

  private fun thisExpression(span: JavaSourceSpan): JavaIrExpression.This? {
    if (!allowThis) return invalid("this is unavailable in a static context.", span)
    return JavaIrExpression.This(JavaIrType.Reference(owner), span)
  }

  private fun receiver(
    kind: JavaReceiverKind,
    source: JavaAstExpression?,
    span: JavaSourceSpan,
  ): JavaIrExpression? = when (kind) {
    JavaReceiverKind.IMPLICIT_THIS, JavaReceiverKind.SUPER -> thisExpression(span)
    JavaReceiverKind.EXPLICIT -> source?.let(::expression)
      ?: invalid("Explicit receiver binding has no AST receiver.", span)
    JavaReceiverKind.NONE, JavaReceiverKind.TYPE_QUALIFIED ->
      invalid("Instance access has no runtime receiver.", span)
  }

  private fun unary(
    source: JavaAstExpression.Unary,
    type: JavaIrType,
  ): JavaIrExpression? = when (source.operator) {
    JavaAstUnaryOperator.POSITIVE -> expression(source.operand)
    JavaAstUnaryOperator.NEGATIVE -> JavaIrExpression.Unary(
      JavaIrUnaryOperator.NEGATE, expression(source.operand) ?: return null, type, source.span,
    )
    JavaAstUnaryOperator.LOGICAL_NOT -> JavaIrExpression.Unary(
      JavaIrUnaryOperator.LOGICAL_NOT, expression(source.operand) ?: return null, type, source.span,
    )
    JavaAstUnaryOperator.BITWISE_NOT -> JavaIrExpression.Unary(
      JavaIrUnaryOperator.BITWISE_NOT, expression(source.operand) ?: return null, type, source.span,
    )
    JavaAstUnaryOperator.PRE_INCREMENT, JavaAstUnaryOperator.PRE_DECREMENT -> {
      val target = target(source.operand) ?: return null
      if (!target.isStable()) {
        return unsupported("Nested update with an effectful target is deferred.", source.span)
      }
      increment(target, source.operator, source.span)
    }
    JavaAstUnaryOperator.POST_INCREMENT, JavaAstUnaryOperator.POST_DECREMENT ->
      unsupported("Nested postfix update is deferred.", source.span)
  }

  /** SetField/SetArrayElement 自身保持 target-before-value；嵌套 compound 要求 target 可稳定重读。 */
  private fun assignment(
    source: JavaAstExpression.Assignment,
    type: JavaIrType,
  ): JavaIrExpression? {
    val target = target(source.target) ?: return null
    if (source.operator != JavaAstAssignmentOperator.ASSIGN && !target.isStable()) {
      return unsupported("Nested compound assignment with an effectful receiver is deferred.", source.span)
    }
    val right = expression(source.value) ?: return null
    val value = if (source.operator == JavaAstAssignmentOperator.ASSIGN) right else {
      compoundValue(
        source.nodeId, read(target, source.target.span), source.operator.toIr(), right, target.type, source.span,
      )
    }
    return write(target, value, type, source.span)
  }

  /** selectedCallables 与 receiverKind 决定 static/special/virtual；接口分派稳定延期。 */
  private fun invocation(
    source: JavaAstExpression.MethodInvocation,
    type: JavaIrType,
  ): JavaIrExpression? {
    val binding = lowering.model.selectedCallables[source.nodeId]
      ?: return invalid("Missing selected callable binding.", source.span)
    val declaration = lowering.model.callableDeclarations[binding.symbol]
      ?: return invalid("Missing selected callable declaration.", source.span)
    if (declaration.kind != JavaSemanticCallableKind.METHOD ||
      binding.erasedDescriptor == null ||
      binding.erasedDescriptor != declaration.erasedDescriptor
    ) return invalid("Selected method binding is inconsistent.", source.span)
    val arguments = source.arguments.mapNotNull(::expression)
    if (arguments.size != source.arguments.size ||
      arguments.size != binding.parameterTypes.size
    ) return invalid("Selected method argument count is inconsistent.", source.span)
    val method = JavaIrMethodId(binding.symbol.value)
    return when (binding.dispatch) {
      JavaDispatchKind.STATIC -> {
        if (binding.receiverKind == JavaReceiverKind.EXPLICIT) {
          return unsupported("Expression-qualified static calls are deferred.", source.span)
        }
        JavaIrExpression.InvokeStatic(method, arguments, type, source.span)
      }
      JavaDispatchKind.SPECIAL -> JavaIrExpression.InvokeSpecial(
        receiver(binding.receiverKind, source.receiver, source.span) ?: return null,
        method, arguments, type, source.span,
      )
      JavaDispatchKind.VIRTUAL -> {
        val slot = binding.virtualSlot ?: lowering.model.virtualSlots[binding.symbol]
          ?: return invalid("Virtual call is missing its slot.", source.span)
        JavaIrExpression.InvokeVirtual(
          receiver(binding.receiverKind, source.receiver, source.span) ?: return null,
          method, slot.value, arguments, type, source.span,
        )
      }
      JavaDispatchKind.INTERFACE ->
        unsupported("Interface dispatch is deferred beyond stage 1.", source.span)
    }
  }

  /** new 的 owner 和 constructor id 都来自 semantic binding/result type。 */
  private fun newObject(
    source: JavaAstExpression.NewObject,
    type: JavaIrType,
  ): JavaIrExpression? {
    val binding = lowering.model.selectedCallables[source.nodeId]
      ?: return invalid("Missing selected constructor binding.", source.span)
    val declaration = lowering.model.callableDeclarations[binding.symbol]
    if (declaration?.kind != JavaSemanticCallableKind.CONSTRUCTOR ||
      binding.dispatch != JavaDispatchKind.SPECIAL ||
      binding.erasedDescriptor == null ||
      binding.erasedDescriptor != declaration.erasedDescriptor
    ) return invalid("Object creation does not select a constructor.", source.span)
    val reference = type as? JavaIrType.Reference
      ?: return invalid("Object creation result is not a reference.", source.span)
    if (reference.classId.value != declaration.owner.value) {
      return invalid("Object creation type and constructor owner differ.", source.span)
    }
    val arguments = source.arguments.mapNotNull(::expression)
    if (arguments.size != source.arguments.size ||
      arguments.size != binding.parameterTypes.size
    ) return invalid("Selected constructor argument count is inconsistent.", source.span)
    return JavaIrExpression.NewObject(
      reference.classId, JavaIrMethodId(binding.symbol.value), arguments, type, source.span,
    )
  }

  /** semantic conversion 原样落到 IR，包含 boxing/unboxing 的稳定 boxed class id。 */
  private fun conversion(
    source: JavaAstExpression,
    expression: JavaIrExpression,
    type: JavaIrType,
  ): JavaIrExpression? {
    val semantic = lowering.model.conversions[source.nodeId] ?: return expression
    val ir = when (semantic) {
      JavaSemanticConversion.Identity -> JavaIrConversion.Identity
      is JavaSemanticConversion.PrimitiveWidening ->
        JavaIrConversion.PrimitiveWidening(semantic.from, semantic.to)
      is JavaSemanticConversion.ReferenceWidening -> JavaIrConversion.ReferenceWidening(
        lowering.typeOf(semantic.from, source.span) ?: return null,
        lowering.typeOf(semantic.to, source.span) ?: return null,
      )
      is JavaSemanticConversion.Boxing ->
        JavaIrConversion.Boxing(semantic.primitive, JavaIrClassId(semantic.boxedType.value))
      is JavaSemanticConversion.Unboxing ->
        JavaIrConversion.Unboxing(JavaIrClassId(semantic.boxedType.value), semantic.primitive)
    }
    return JavaIrExpression.Convert(ir, expression, type, source.span)
  }

  /** assignment/update target 同样严格读取 valueAccesses。 */
  private fun target(source: JavaAstExpression): Target? {
    if (source is JavaAstExpression.ArrayAccess) {
      val type = lowering.expressionType(source.nodeId, source.span) ?: return null
      return Target.Array(
        expression(source.array) ?: return null,
        expression(source.index) ?: return null,
        type,
      )
    }
    val binding = lowering.model.valueAccesses[source.nodeId]
      ?: return invalid("Missing writable value access binding.", source.span)
    val type = lowering.expressionType(source.nodeId, source.span) ?: return null
    val explicit = (source as? JavaAstExpression.FieldAccess)?.receiver
    return when (binding.kind) {
      JavaValueAccessKind.LOCAL, JavaValueAccessKind.PARAMETER -> {
        val local = localBySymbol[binding.symbol]
          ?: return invalid("Writable access references an unknown local.", source.span)
        if (local.isParameter != (binding.kind == JavaValueAccessKind.PARAMETER)) {
          return invalid("Writable access kind does not match local declaration kind.", source.span)
        }
        Target.Local(local.id, local.type)
      }
      JavaValueAccessKind.STATIC_FIELD -> {
        val field = field(binding, true, source.span) ?: return null
        Target.Static(JavaIrFieldId(field.symbol.value), type)
      }
      JavaValueAccessKind.INSTANCE_FIELD -> {
        val field = field(binding, false, source.span) ?: return null
        Target.Instance(
          receiver(binding.receiverKind, explicit, source.span) ?: return null,
          JavaIrFieldId(field.symbol.value), type,
        )
      }
    }
  }

  /** effectful field/array target 先保存为 temp，使 postfix/compound 的 read 与 write 共享同一值。 */
  private fun prepareTarget(source: JavaAstExpression): PreparedTarget? {
    val target = target(source) ?: return null
    if (target.isStable()) return PreparedTarget(emptyList(), target)
    return when (target) {
      is Target.Instance -> {
        val receiver = temporary(target.receiver.type, "fieldReceiver", source.span)
        PreparedTarget(
          listOf(JavaIrStatement.DeclareLocal(receiver.id, target.receiver, source.span)),
          Target.Instance(JavaIrExpression.GetLocal(receiver.id, receiver.type, source.span), target.field, target.type),
        )
      }
      is Target.Array -> {
        // Java 先求 array 再求 index；两个临时 local 也必须沿这个顺序声明。
        val array = temporary(target.array.type, "arrayReceiver", source.span)
        val index = temporary(target.index.type, "arrayIndex", source.span)
        PreparedTarget(
          listOf(
            JavaIrStatement.DeclareLocal(array.id, target.array, source.span),
            JavaIrStatement.DeclareLocal(index.id, target.index, source.span),
          ),
          Target.Array(
            JavaIrExpression.GetLocal(array.id, array.type, source.span),
            JavaIrExpression.GetLocal(index.id, index.type, source.span),
            target.type,
          ),
        )
      }
      is Target.Local, is Target.Static -> PreparedTarget(emptyList(), target)
    }
  }

  private fun read(target: Target, span: JavaSourceSpan): JavaIrExpression = when (target) {
    is Target.Local -> JavaIrExpression.GetLocal(target.local, target.type, span)
    is Target.Instance -> JavaIrExpression.GetField(target.receiver, target.field, target.type, span)
    is Target.Static -> JavaIrExpression.GetStaticField(target.field, target.type, span)
    is Target.Array -> JavaIrExpression.GetArrayElement(target.array, target.index, target.type, span)
  }

  private fun write(
    target: Target,
    value: JavaIrExpression,
    type: JavaIrType,
    span: JavaSourceSpan,
  ): JavaIrExpression = when (target) {
    is Target.Local -> JavaIrExpression.SetLocal(target.local, value, type, span)
    is Target.Instance -> JavaIrExpression.SetField(target.receiver, target.field, value, type, span)
    is Target.Static -> JavaIrExpression.SetStaticField(target.field, value, type, span)
    is Target.Array -> JavaIrExpression.SetArrayElement(target.array, target.index, value, type, span)
  }

  private fun increment(
    target: Target,
    operator: JavaAstUnaryOperator,
    span: JavaSourceSpan,
  ): JavaIrExpression {
    val binary = if (operator == JavaAstUnaryOperator.PRE_DECREMENT ||
      operator == JavaAstUnaryOperator.POST_DECREMENT
    ) JavaIrBinaryOperator.SUBTRACT else JavaIrBinaryOperator.ADD
    val one = JavaIrExpression.Constant(JavaIrConstant.IntValue(1), target.type, span)
    return write(
      target,
      JavaIrExpression.Binary(read(target, span), binary, one, target.type, span),
      target.type, span,
    )
  }

  private fun temporary(
    type: JavaIrType,
    hint: String,
    span: JavaSourceSpan,
  ): JavaIrLocal = JavaIrLocal(
    lowering.allocateSyntheticLocalId(), hint, type, false, span,
  ).also(locals::add)

  private sealed interface Target {
    val type: JavaIrType
    data class Local(val local: JavaIrLocalId, override val type: JavaIrType) : Target
    data class Instance(
      val receiver: JavaIrExpression,
      val field: JavaIrFieldId,
      override val type: JavaIrType,
    ) : Target
    data class Static(val field: JavaIrFieldId, override val type: JavaIrType) : Target
    data class Array(
      val array: JavaIrExpression,
      val index: JavaIrExpression,
      override val type: JavaIrType,
    ) : Target
  }

  private data class PreparedTarget(
    val prefix: List<JavaIrStatement>,
    val target: Target,
  )

  /** This/GetLocal 可重复引用；数组 target 还要求 index 已是稳定 local。 */
  private fun Target.isStable(): Boolean = when (this) {
    is Target.Instance -> receiver is JavaIrExpression.This || receiver is JavaIrExpression.GetLocal
    is Target.Array -> (array is JavaIrExpression.This || array is JavaIrExpression.GetLocal) &&
      index is JavaIrExpression.GetLocal
    is Target.Local, is Target.Static -> true
  }

  /** 自增和自减无论前后缀，表达式语句均可安全拆成稳定 target 后的一次写回。 */
  private fun JavaAstUnaryOperator.isUpdate(): Boolean =
    this == JavaAstUnaryOperator.PRE_INCREMENT || this == JavaAstUnaryOperator.PRE_DECREMENT ||
      isPostfix()

  private fun JavaAstUnaryOperator.isPostfix(): Boolean =
    this == JavaAstUnaryOperator.POST_INCREMENT ||
      this == JavaAstUnaryOperator.POST_DECREMENT

  private fun JavaAstBinaryOperator.toIr(): JavaIrBinaryOperator =
    JavaIrBinaryOperator.valueOf(name)

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
    JavaAstAssignmentOperator.ASSIGN -> JavaIrBinaryOperator.ADD
  }

  private fun invalid(message: String, span: JavaSourceSpan): Nothing? =
    lowering.invalid(message, span)

  private fun unsupported(message: String, span: JavaSourceSpan): Nothing? {
    lowering.unsupported(message, span)
    return null
  }
}
