package com.cyxbs.functions.code.language.java.compiler.lowering

import com.cyxbs.functions.code.language.java.compiler.JavaIrLowerer
import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinMemberDescriptor
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinOperation
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
    JavaTypeRelations(
      model.typeDeclarations,
      model.typeParameterDeclarations,
      it,
      model.wrapperPrimitiveTypes,
    )
  }
  private var nextSyntheticLocalId = model.symbols.keys.maxOfOrNull { it.value } ?: 0

  /** 合成 local 使用全工作区单调 ID，避免不同方法及真实 symbol 互相冲突。 */
  fun allocateSyntheticLocalId(): JavaIrLocalId = JavaIrLocalId(++nextSyntheticLocalId)

  /** 先建立反向源码索引，再按 compilation-unit 顺序生成用户类。 */
  fun lower(): JavaIrProgram? {
    indexSources()
    val classes = model.ast.units.flatMap { it.types.mapNotNull(::lowerClass) }
    return if (failed) null else JavaIrProgram(
      classes,
      model.builtinTypeRoles.mapKeys { (symbol, _) -> JavaIrClassId(symbol.value) },
      model.builtinMembers.mapNotNull { (symbol, descriptor) ->
        val callable = descriptor as? JavaBuiltinMemberDescriptor.Callable
        if (callable?.isVirtualRoot != true) return@mapNotNull null
        val slot = model.virtualSlots[symbol]?.value
          ?: return invalid("Builtin virtual root is missing its slot.", null)
        callable.operation to slot
      }.toMap(),
    )
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

  /** class/interface 头和成员均来自 semantic declarations，接口边不从 AST 名称重新解析。 */
  private fun lowerClass(source: JavaAstTypeDeclaration): JavaIrClass? {
    val symbol = declaration(source.nodeId, source.span, JavaSymbolKind.TYPE) ?: return null
    val declaration = model.typeDeclarations[symbol.id]
      ?: return invalid("Missing semantic type declaration.", source.span)
    val expectedKind = when (source.kind) {
      JavaAstTypeDeclarationKind.CLASS -> JavaSemanticTypeDeclarationKind.CLASS
      JavaAstTypeDeclarationKind.INTERFACE -> JavaSemanticTypeDeclarationKind.INTERFACE
      JavaAstTypeDeclarationKind.ENUM -> JavaSemanticTypeDeclarationKind.ENUM
    }
    if (declaration.symbol != symbol.id || declaration.kind != expectedKind ||
      declaration.kind !in setOf(
        JavaSemanticTypeDeclarationKind.CLASS,
        JavaSemanticTypeDeclarationKind.INTERFACE,
      )
    ) {
      unsupported("Only semantic class/interface declarations are executable in stage 2A.", source.span)
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
    val interfaces = declaration.directInterfaces.mapNotNull { inherited ->
      val erased = erase(inherited, source.span) as? JavaSemanticType.Declared ?: return@mapNotNull null
      classIds[erased.symbol] ?: run {
        invalid("Missing direct interface class id.", source.span)
        null
      }
    }
    val defaults = model.interfaceDefaultMethods[declaration.symbol].orEmpty().mapNotNull { (slot, method) ->
      if (model.callableDeclarations[method]?.isAbstract != false) {
        invalid("Interface default binding must target an executable method.", source.span)
        null
      } else {
        slot.value to JavaIrMethodId(method.value)
      }
    }.toMap()
    return JavaIrClass(
      owner,
      declaration.qualifiedName,
      lowerSuper(declaration, source.span),
      interfaces,
      fields,
      methods,
      null,
      source.span,
      null,
      if (declaration.kind == JavaSemanticTypeDeclarationKind.INTERFACE) {
        JavaIrTypeDeclarationKind.INTERFACE
      } else {
        JavaIrTypeDeclarationKind.CLASS
      },
      defaults,
      exceptionSuperQualifiedName(declaration, source.span),
    )
  }

  /** 源码异常类保留直接异常父类名称，供 JS catch 匹配与自定义异常层级注册。 */
  private fun exceptionSuperQualifiedName(
    declaration: JavaSemanticTypeDeclaration,
    span: JavaSourceSpan,
  ): String? {
    val throwable = model.typeDeclarations.values.singleOrNull {
      it.kind == JavaSemanticTypeDeclarationKind.BUILTIN && it.qualifiedName == "java.lang.Throwable"
    } ?: return null
    val self = JavaSemanticType.Declared(declaration.symbol, emptyList())
    if (relations?.isSubtype(
        self,
        JavaSemanticType.Declared(throwable.symbol, emptyList()),
      ) != true
    ) return null
    val parent = declaration.directSuperClass
      ?: return invalid("A source exception must have a direct superclass.", span)
    return model.typeDeclarations[parent.symbol]?.qualifiedName
      ?: invalid("A source exception parent declaration is missing.", span)
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
      // builtin Throwable 家族由异常 runtime 承担，不生成普通 class prototype 父边。
      val parent = model.typeDeclarations[erased.symbol]
      if (parent?.kind == JavaSemanticTypeDeclarationKind.BUILTIN &&
        parent.qualifiedName in setOf("java.lang.Throwable", "java.lang.Exception", "java.lang.Error")
      ) return null
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
      null
    } else bodyLowering.lowerBlock(sourceBody)
    val slot = model.virtualSlots[declaration.symbol]?.value
    val dispatch = when {
      declaration.kind == JavaSemanticCallableKind.CONSTRUCTOR -> JavaIrDispatchKind.SPECIAL
      declaration.isStatic -> JavaIrDispatchKind.STATIC
      ownerDeclaration.kind == JavaSemanticTypeDeclarationKind.INTERFACE -> JavaIrDispatchKind.INTERFACE
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
          val builtin = lowering.model.builtinMembers[delegation.targetConstructor]
          if (builtin is JavaBuiltinMemberDescriptor.Callable) {
            if (delegation.kind != JavaConstructorDelegationKind.SUPER) {
              lowering.invalid("A builtin exception constructor can only be invoked through super.", span)
            } else {
              val expectedCount = when (builtin.operation) {
                JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY -> 0
                JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING -> 1
                JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE -> 2
                else -> -1
              }
              if (expectedCount < 0 || arguments.size != expectedCount) {
                lowering.invalid("Unsupported builtin superclass constructor delegation.", span)
              } else {
                statements += JavaIrStatement.InitializeException(
                  arguments.getOrNull(0),
                  arguments.getOrNull(1),
                  sourceInvocation?.span ?: span,
                )
              }
            }
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
      is JavaAstStatement.DoWhile -> collectLocals(statement.body)
      is JavaAstStatement.For -> {
        (statement.initializer as? JavaAstForInitializer.VariableDeclaration)
          ?.declarators?.forEach(::registerLocal)
        collectLocals(statement.body)
      }
      is JavaAstStatement.EnhancedFor -> {
        registerLocal(statement.variable)
        collectLocals(statement.body)
      }
      is JavaAstStatement.Switch -> statement.entries.forEach { entry ->
        entry.statements.forEach(::collectLocals)
      }
      is JavaAstStatement.Try -> {
        statement.resources.forEach { resource -> registerResourceLocal(resource) }
        collectLocals(statement.body)
        statement.catches.forEach { clause ->
          registerCatchLocal(clause)
          collectLocals(clause.body)
        }
        statement.finallyBlock?.let(::collectLocals)
      }
      else -> Unit
    }
  }

  /** resource 声明和普通 local 使用同一套稳定 symbol/local id。 */
  private fun registerResourceLocal(source: JavaAstResource) {
    val symbol = lowering.declaration(source.nodeId, source.span, JavaSymbolKind.LOCAL_VARIABLE) ?: return
    if (localBySymbol[symbol.id] == null) {
      createLocal(symbol.id, symbol.name, symbol.type, false, source.span)
    }
  }

  private fun registerLocal(source: JavaAstVariableDeclarator) {
    val symbol = lowering.declaration(source.nodeId, source.span, JavaSymbolKind.LOCAL_VARIABLE) ?: return
    if (localBySymbol[symbol.id] == null) {
      createLocal(symbol.id, symbol.name, symbol.type, false, source.span)
    }
  }

  /** catch 参数在语义上属于 parameter，但在 IR 中是由 catch 入口赋值的普通 local。 */
  private fun registerCatchLocal(source: JavaAstCatchClause) {
    val symbol = lowering.declaration(source.nodeId, source.parameterSpan, JavaSymbolKind.PARAMETER)
      ?: return
    if (localBySymbol[symbol.id] == null) {
      createLocal(symbol.id, symbol.name, symbol.type, false, source.parameterSpan)
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

  /** 结构化降低语句；constructor invocation 只能由构造专用入口消费。 */
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
    is JavaAstStatement.DoWhile -> expression(statement.condition)?.let {
      listOf(JavaIrStatement.DoWhile(branch(statement.body), it, statement.span))
    } ?: emptyList()
    is JavaAstStatement.For -> lowerFor(statement)
    is JavaAstStatement.EnhancedFor -> lowerEnhancedFor(statement)
    is JavaAstStatement.Switch -> lowerSwitch(statement)
    is JavaAstStatement.Throw -> expression(statement.expression)?.let { value ->
      listOf(JavaIrStatement.Throw(value, statement.span))
    } ?: emptyList()
    is JavaAstStatement.Try -> lowerTry(statement)
    is JavaAstStatement.Break -> listOf(JavaIrStatement.Break(statement.span))
    is JavaAstStatement.Continue -> listOf(JavaIrStatement.Continue(statement.span))
    is JavaAstStatement.Return -> lowerReturn(statement)
    is JavaAstStatement.Empty -> emptyList()
  }

  /** try/catch/finally 与资源关闭协议只消费语义 side table，不重新做类型或成员查找。 */
  private fun lowerTry(statement: JavaAstStatement.Try): List<JavaIrStatement> {
    val catches = statement.catches.map { clause ->
      val symbol = lowering.declaration(
        clause.nodeId,
        clause.parameterSpan,
        JavaSymbolKind.PARAMETER,
      ) ?: return emptyList()
      val local = localBySymbol[symbol.id]
        ?: return invalid("Catch parameter is missing its IR local.", clause.parameterSpan).let { emptyList() }
      val semanticTypes = lowering.model.catchTypes[clause.nodeId]
        ?: return invalid("Catch clause is missing its resolved exception types.", clause.span).let { emptyList() }
      val types = semanticTypes.mapNotNull { type ->
        lowering.typeOf(type, clause.type.span) as? JavaIrType.Reference
      }
      if (types.size != semanticTypes.size || types.isEmpty()) {
        return invalid("Catch alternatives must lower to reference types.", clause.type.span).let { emptyList() }
      }
      JavaIrCatchClause(types.first(), local.id, lowerBlock(clause.body), clause.span, types.drop(1))
    }
    val resources = statement.resources.map { resource ->
      val binding = lowering.model.resourceCloseBindings[resource.nodeId]
        ?: return invalid("Resource is missing its close binding.", resource.span).let { emptyList() }
      val local = localBySymbol[binding.localSymbol]
        ?: return invalid("Resource is missing its IR local.", resource.span).let { emptyList() }
      val initializer = expression(resource.initializer) ?: return emptyList()
      val receiver = JavaIrExpression.GetLocal(local.id, local.type, resource.span)
      val closeExpression = invocation(binding.closeCallable, receiver, emptyList(), resource.span)
        ?: return emptyList()
      JavaIrResource(local.id, initializer, closeExpression, resource.span)
    }
    return listOf(
      JavaIrStatement.Try(
        lowerBlock(statement.body),
        catches,
        statement.finallyBlock?.let(::lowerBlock),
        statement.span,
        resources,
      ),
    )
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
        JavaIrStatement.Expression(
          increment(prepared.target, initializer, initializer.operator, initializer.span),
          initializer.span,
        ),
      )
    } else listOf(JavaIrStatement.DeclareLocal(local.id, initializer?.let(::expression), span))
  }

  /** initializer 放入外层 block，condition/update/body 保留为 for IR 以维持 continue 语义。 */
  private fun lowerFor(statement: JavaAstStatement.For): List<JavaIrStatement> {
    val init = when (val source = statement.initializer) {
      null -> emptyList()
      is JavaAstForInitializer.VariableDeclaration -> lowerDeclarations(source.declarators, source.span)
      is JavaAstForInitializer.Expressions -> source.expressions.flatMap { expressionStatement(it, it.span) }
    }
    val condition = statement.condition?.let(::expression) ?: JavaIrExpression.Constant(
      JavaIrConstant.BooleanValue(true), JavaIrType.Primitive(JavaAstPrimitiveType.BOOLEAN), statement.span,
    )
    val updates = statement.updates.mapNotNull { update ->
      val lowered = expressionStatement(update, update.span)
      if (lowered.size != 1 || lowered.single() !is JavaIrStatement.Expression) {
        lowering.unsupported("for 更新表达式暂不支持需要临时语句的后缀结果。", update.span)
        null
      } else (lowered.single() as JavaIrStatement.Expression).expression
    }
    if (updates.size != statement.updates.size) return emptyList()
    return listOf(JavaIrStatement.Block(
      init + JavaIrStatement.For(
        condition, updates, branch(statement.body), statement.span,
      ),
      statement.span,
    ))
  }

  /** 增强 for 只消费 semantic binding，集合类别与元素转换均不从源码名称推断。 */
  private fun lowerEnhancedFor(statement: JavaAstStatement.EnhancedFor): List<JavaIrStatement> {
    val binding = lowering.model.enhancedForBindings[statement.nodeId]
      ?: return invalid("Enhanced for is missing its semantic binding.", statement.span).let { emptyList() }
    val symbol = lowering.declaration(
      statement.variable.nodeId, statement.variable.span, JavaSymbolKind.LOCAL_VARIABLE,
    ) ?: return emptyList()
    val local = localBySymbol[symbol.id]
      ?: return invalid("Enhanced for variable is missing its IR local.", statement.variable.span).let { emptyList() }
    val iterable = expression(statement.iterable) ?: return emptyList()
    val elementType = lowering.typeOf(binding.elementType, statement.variable.span) ?: return emptyList()
    val conversions = irConversions(binding.conversion, statement.variable.span) ?: return emptyList()
    val kind = when (binding.kind) {
      JavaEnhancedForKind.ARRAY -> JavaIrEnhancedForKind.ARRAY
      JavaEnhancedForKind.LIST -> JavaIrEnhancedForKind.LIST
      JavaEnhancedForKind.SET -> JavaIrEnhancedForKind.SET
    }
    return listOf(JavaIrStatement.EnhancedFor(
      kind, local.id, iterable, elementType, conversions, branch(statement.body), statement.span,
    ))
  }

  /** switch label 必须已经由语义阶段证明为常量；entry 顺序原样保留 fallthrough。 */
  private fun lowerSwitch(statement: JavaAstStatement.Switch): List<JavaIrStatement> {
    val selector = expression(statement.selector) ?: return emptyList()
    val entries = statement.entries.map { entry ->
      val label = entry.label?.let { source ->
        expression(source) as? JavaIrExpression.Constant
          ?: return invalid("Switch label is not a lowered constant.", source.span).let { emptyList() }
      }
      JavaIrSwitchEntry(label, entry.statements.flatMap(::lowerStatement), entry.span)
    }
    return listOf(JavaIrStatement.Switch(selector, entries, statement.span))
  }

  /** 将可能为 Sequence 的元素赋值转换展平成后端可顺序应用的 IR conversion。 */
  private fun irConversions(
    conversion: JavaSemanticConversion,
    span: JavaSourceSpan,
  ): List<JavaIrConversion>? = when (conversion) {
    JavaSemanticConversion.Identity -> listOf(JavaIrConversion.Identity)
    is JavaSemanticConversion.Sequence -> conversion.steps.flatMap { step ->
      irConversions(step, span) ?: return null
    }
    is JavaSemanticConversion.PrimitiveWidening -> listOf(
      JavaIrConversion.PrimitiveWidening(conversion.from, conversion.to),
    )
    is JavaSemanticConversion.PrimitiveNarrowing -> listOf(
      JavaIrConversion.PrimitiveNarrowing(conversion.from, conversion.to),
    )
    is JavaSemanticConversion.ReferenceWidening -> listOf(JavaIrConversion.ReferenceWidening(
      lowering.typeOf(conversion.from, span) ?: return null,
      lowering.typeOf(conversion.to, span) ?: return null,
    ))
    is JavaSemanticConversion.Boxing -> listOf(JavaIrConversion.Boxing(
      conversion.primitive, JavaIrClassId(conversion.boxedType.value),
    ))
    is JavaSemanticConversion.Unboxing -> listOf(JavaIrConversion.Unboxing(
      JavaIrClassId(conversion.boxedType.value), conversion.primitive,
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
        JavaIrStatement.Expression(increment(prepared.target, value, value.operator, value.span), value.span),
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
        increment(prepared.target, source, source.operator, source.span), span,
      )
    }
    if (source is JavaAstExpression.Assignment &&
      source.operator != JavaAstAssignmentOperator.ASSIGN
    ) {
      val prepared = prepareTarget(source.target) ?: return emptyList()
      val right = expression(source.value) ?: return emptyList()
      val left = boundConversion(source.target, read(prepared.target, source.target.span))
        ?: return emptyList()
      val value = compoundValue(
        source.nodeId, left, source.operator.toIr(), right,
        updateComputationType(source.nodeId, left.type), source.span,
      )
      val writeValue = updateWriteConversion(source.nodeId, value, source.span) ?: return emptyList()
      return prepared.prefix + JavaIrStatement.Expression(
        write(prepared.target, writeValue, prepared.target.type, source.span), span,
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
      is JavaAstExpression.Lambda -> lambda(source, type)
  } ?: return null
    return conversion(source, raw)
  }

  /** 将 semantic SAM binding 降为保留词法捕获的 typed lambda IR。 */
  private fun lambda(
    source: JavaAstExpression.Lambda,
    type: JavaIrType,
  ): JavaIrExpression? {
    val referenceType = type as? JavaIrType.Reference
      ?: return invalid("Lambda target must lower to a reference type.", source.span)
    val binding = lowering.model.lambdaBindings[source.nodeId]
      ?: return invalid("Lambda expression is missing its semantic binding.", source.span)
    if (referenceType.classId != JavaIrClassId(binding.targetType.symbol.value)) {
      return invalid("Lambda target type is inconsistent with semantic binding.", source.span)
    }
    val slot = binding.functionalMethod.virtualSlot?.value
      ?: return invalid("Lambda SAM binding is missing its virtual slot.", source.span)
    if (binding.parameterSymbols.size != source.parameters.size ||
      binding.functionalMethod.parameterTypes.size != source.parameters.size
    ) {
      return invalid("Lambda parameter binding count is inconsistent.", source.span)
    }
    val parameterLocals = source.parameters.mapIndexed { index, parameter ->
      val symbol = lowering.declaration(parameter.nodeId, parameter.span, JavaSymbolKind.PARAMETER)
        ?: return null
      if (symbol.id != binding.parameterSymbols[index]) {
        return invalid("Lambda parameter order is inconsistent.", parameter.span)
      }
      // isParameter 只表示顶层 JavaIrMethod 形参；lambda 形参由 Lambda 节点自身绑定。
      createLocal(symbol.id, symbol.name, binding.functionalMethod.parameterTypes[index], false, parameter.span)
        ?: return null
    }
    val body = when (val lambdaBody = source.body) {
      is JavaAstLambdaBody.Expression -> {
        val lowered = expression(lambdaBody.expression) ?: return null
        val statement = if (binding.functionalMethod.returnType == JavaSemanticType.Void) {
          JavaIrStatement.Expression(lowered, lambdaBody.expression.span)
        } else {
          JavaIrStatement.Return(lowered, lambdaBody.expression.span)
        }
        JavaIrStatement.Block(listOf(statement), lambdaBody.expression.span)
      }
      is JavaAstLambdaBody.Block -> lowerBlock(lambdaBody.block)
    }
    val captures = binding.captures.map { symbol ->
      localBySymbol[symbol]?.id
        ?: return invalid("Lambda capture references an unknown enclosing local.", source.span)
    }
    return JavaIrExpression.Lambda(
      referenceType.classId,
      slot,
      parameterLocals.map(JavaIrLocal::id),
      captures,
      body,
      referenceType,
      source.span,
    )
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
        // catch 参数在 semantic 中仍是 PARAMETER，但 IR 必须把它放进 locals，交给 catch 入口赋值；
        // 因此这里只校验“是否为 callable 形参”，不能把所有 PARAMETER binding 都当作方法形参。
        val isCallableParameter = binding.symbol in callable?.parameters.orEmpty()
        if (local.isParameter != isCallableParameter ||
          (binding.kind == JavaValueAccessKind.LOCAL && isCallableParameter)
        ) {
          return invalid("Value access kind does not match local declaration kind.", source.span)
        }
        JavaIrExpression.GetLocal(local.id, type, source.span)
      }
      JavaValueAccessKind.STATIC_FIELD -> {
        val field = field(binding, true, source.span) ?: return null
        val owner = lowering.model.typeDeclarations[field.owner]
          ?: return invalid("Field owner declaration is missing.", source.span)
        if (owner.kind == JavaSemanticTypeDeclarationKind.BUILTIN) {
          val builtin = lowering.model.builtinMembers[binding.symbol]
            ?: return invalid("Builtin field is missing its operation binding.", source.span)
          if (builtin !is JavaBuiltinMemberDescriptor.Field ||
            !builtin.isStatic ||
            builtin.ownerQualifiedName != owner.qualifiedName ||
            binding.receiverKind !in setOf(JavaReceiverKind.NONE, JavaReceiverKind.TYPE_QUALIFIED)
          ) {
            return invalid("Builtin field binding is inconsistent.", source.span)
          }
          return JavaIrExpression.BuiltinValue(builtin.operation, type, source.span)
        }
        if (binding.symbol in lowering.model.builtinMembers) {
          return invalid("Source field unexpectedly carries a builtin operation.", source.span)
        }
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
      increment(target, source, source.operator, source.span)
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
      val left = boundConversion(source.target, read(target, source.target.span)) ?: return null
      compoundValue(
        source.nodeId, left, source.operator.toIr(), right,
        updateComputationType(source.nodeId, left.type), source.span,
      )
    }
    val writeValue = updateWriteConversion(source.nodeId, value, source.span) ?: return null
    return write(target, writeValue, type, source.span)
  }

  /** selectedCallables 与 receiverKind 决定 static/special/virtual/interface 分派。 */
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
    val owner = lowering.model.typeDeclarations[declaration.owner]
      ?: return invalid("Selected method owner declaration is missing.", source.span)
    if (owner.kind == JavaSemanticTypeDeclarationKind.BUILTIN) {
      val builtin = lowering.model.builtinMembers[binding.symbol]
        ?: return invalid("Builtin callable is missing its operation binding.", source.span)
      if (builtin !is JavaBuiltinMemberDescriptor.Callable ||
        builtin.isStatic != declaration.isStatic ||
        builtin.ownerQualifiedName != owner.qualifiedName
      ) {
        return invalid("Builtin callable binding is inconsistent.", source.span)
      }
      return builtinInvocation(source, type, binding, builtin)
    }
    if (binding.symbol in lowering.model.builtinMembers) {
      return invalid("Source callable unexpectedly carries a builtin operation.", source.span)
    }
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
      JavaDispatchKind.INTERFACE -> {
        val slot = binding.virtualSlot ?: lowering.model.virtualSlots[binding.symbol]
          ?: return invalid("Interface call is missing its slot.", source.span)
        JavaIrExpression.InvokeVirtual(
          receiver(binding.receiverKind, source.receiver, source.span) ?: return null,
          method, slot.value, arguments, type, source.span,
        )
      }
    }
  }

  /**
   * 内建调用先 lower receiver、再按源码顺序 lower 参数，确保后端可保持 Java 的单次求值顺序。
   * 参数仍统一经过 [expression]，因此 widening 等语义 conversion 不会在 builtin 路径丢失。
   */
  private fun builtinInvocation(
    source: JavaAstExpression.MethodInvocation,
    type: JavaIrType,
    binding: JavaCallableBinding,
    builtin: JavaBuiltinMemberDescriptor.Callable,
  ): JavaIrExpression? {
    val runtimeReceiver = if (builtin.isStatic) {
      if (binding.dispatch != JavaDispatchKind.STATIC ||
        binding.receiverKind !in setOf(JavaReceiverKind.NONE, JavaReceiverKind.TYPE_QUALIFIED)
      ) {
        return invalid("Static builtin call has an invalid receiver or dispatch.", source.span)
      }
      null
    } else {
      val expectedDispatch = if (builtin.isVirtualRoot) {
        JavaDispatchKind.VIRTUAL
      } else {
        JavaDispatchKind.SPECIAL
      }
      if (binding.dispatch != expectedDispatch ||
        (builtin.isVirtualRoot && binding.virtualSlot?.value !=
          lowering.model.virtualSlots[binding.symbol]?.value)
      ) {
        return invalid("Instance builtin call has an invalid dispatch or virtual slot.", source.span)
      }
      receiver(binding.receiverKind, source.receiver, source.span) ?: return null
    }
    val arguments = source.arguments.mapNotNull(::expression)
    if (arguments.size != source.arguments.size ||
      arguments.size != binding.parameterTypes.size ||
      arguments.size != builtin.parameterTypes.size
    ) {
      return invalid("Builtin call argument count is inconsistent.", source.span)
    }
    return if (builtin.isVirtualRoot) {
      JavaIrExpression.InvokeVirtualSlot(
        builtin.operation,
        checkNotNull(runtimeReceiver),
        binding.virtualSlot?.value ?: return invalid("Builtin virtual call is missing its slot.", source.span),
        arguments,
        type,
        source.span,
      )
    } else JavaIrExpression.InvokeBuiltin(
      builtin.operation, runtimeReceiver, arguments, type, source.span,
    )
  }

  /**
   * 为 try-with-resources 等合成调用降低已决议 callable。
   *
   * 这里没有对应的 MethodInvocation AST，因此 receiver 和参数由调用方显式提供；目标身份、
   * 分派种类与虚槽仍完全取自 semantic binding。
   */
  private fun invocation(
    binding: JavaCallableBinding,
    receiver: JavaIrExpression,
    arguments: List<JavaIrExpression>,
    span: JavaSourceSpan,
  ): JavaIrExpression? {
    val declaration = lowering.model.callableDeclarations[binding.symbol]
      ?: return invalid("Synthetic call is missing its declaration.", span)
    if (arguments.size != binding.parameterTypes.size ||
      declaration.erasedDescriptor != binding.erasedDescriptor
    ) return invalid("Synthetic call binding is inconsistent.", span)
    val type = lowering.typeOf(binding.returnType, span) ?: return null
    val builtin = lowering.model.builtinMembers[binding.symbol]
    if (builtin is JavaBuiltinMemberDescriptor.Callable) {
      if (builtin.isStatic || binding.receiverKind !in setOf(
          JavaReceiverKind.EXPLICIT,
          JavaReceiverKind.IMPLICIT_THIS,
          JavaReceiverKind.SUPER,
        )
      ) return invalid("Synthetic builtin instance call has an invalid receiver.", span)
      return if (builtin.isVirtualRoot) {
        JavaIrExpression.InvokeVirtualSlot(
          builtin.operation,
          receiver,
          binding.virtualSlot?.value ?: return invalid("Synthetic virtual call is missing its slot.", span),
          arguments,
          type,
          span,
        )
      } else JavaIrExpression.InvokeBuiltin(builtin.operation, receiver, arguments, type, span)
    }
    val method = JavaIrMethodId(binding.symbol.value)
    return when (binding.dispatch) {
      JavaDispatchKind.SPECIAL -> JavaIrExpression.InvokeSpecial(receiver, method, arguments, type, span)
      JavaDispatchKind.VIRTUAL,
      JavaDispatchKind.INTERFACE,
      -> JavaIrExpression.InvokeVirtual(
        receiver,
        method,
        binding.virtualSlot?.value ?: return invalid("Synthetic virtual call is missing its slot.", span),
        arguments,
        type,
        span,
      )
      JavaDispatchKind.STATIC -> invalid("Resource close cannot be static.", span)
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
    val builtin = lowering.model.builtinMembers[binding.symbol]
    if (builtin != null) {
      if (builtin !is JavaBuiltinMemberDescriptor.Callable || !builtin.isConstructor ||
        builtin.ownerQualifiedName != lowering.model.typeDeclarations[declaration.owner]?.qualifiedName
      ) return invalid("Builtin constructor binding is inconsistent.", source.span)
      return JavaIrExpression.ConstructBuiltin(builtin.operation, arguments, reference, source.span)
    }
    return JavaIrExpression.NewObject(
      reference.classId, JavaIrMethodId(binding.symbol.value), arguments, type, source.span,
    )
  }

  /** semantic conversion 原样落到 IR，包含 boxing/unboxing 的稳定 boxed class id。 */
  private fun conversion(
    source: JavaAstExpression,
    expression: JavaIrExpression,
  ): JavaIrExpression? {
    val semantic = lowering.model.conversions[source.nodeId] ?: return expression
    return applyConversion(semantic, expression, source.span)
  }

  /** 把 semantic Sequence 展开为逐步 typed Convert，确保每一层 result type 都是该步目标类型。 */
  private fun applyConversion(
    semantic: JavaSemanticConversion,
    expression: JavaIrExpression,
    span: JavaSourceSpan,
  ): JavaIrExpression? = when (semantic) {
    JavaSemanticConversion.Identity -> expression
    is JavaSemanticConversion.Sequence -> semantic.steps.fold(expression as JavaIrExpression?) { current, step ->
      current?.let { applyConversion(step, it, span) }
    }
    is JavaSemanticConversion.PrimitiveWidening -> JavaIrExpression.Convert(
      JavaIrConversion.PrimitiveWidening(semantic.from, semantic.to), expression,
      JavaIrType.Primitive(semantic.to), span,
    )
    is JavaSemanticConversion.PrimitiveNarrowing -> JavaIrExpression.Convert(
      JavaIrConversion.PrimitiveNarrowing(semantic.from, semantic.to), expression,
      JavaIrType.Primitive(semantic.to), span,
    )
    is JavaSemanticConversion.ReferenceWidening -> JavaIrExpression.Convert(
      JavaIrConversion.ReferenceWidening(
        lowering.typeOf(semantic.from, span) ?: return null,
        lowering.typeOf(semantic.to, span) ?: return null,
      ),
      expression,
      lowering.typeOf(semantic.to, span) ?: return null,
      span,
    )
    is JavaSemanticConversion.Boxing -> JavaIrExpression.Convert(
      JavaIrConversion.Boxing(semantic.primitive, JavaIrClassId(semantic.boxedType.value)),
      expression, JavaIrType.Reference(JavaIrClassId(semantic.boxedType.value)), span,
    )
    is JavaSemanticConversion.Unboxing -> JavaIrExpression.Convert(
      JavaIrConversion.Unboxing(JavaIrClassId(semantic.boxedType.value), semantic.primitive),
      expression, JavaIrType.Primitive(semantic.primitive), span,
    )
  }

  private fun boundConversion(
    source: JavaAstExpression,
    expression: JavaIrExpression,
  ): JavaIrExpression? = lowering.model.conversions[source.nodeId]
    ?.let { applyConversion(it, expression, source.span) } ?: expression

  private fun updateWriteConversion(
    nodeId: JavaNodeId,
    expression: JavaIrExpression,
    span: JavaSourceSpan,
  ): JavaIrExpression? = lowering.model.updateWriteConversions[nodeId]
    ?.let { applyConversion(it, expression, span) } ?: expression

  /** 从回写序列首步恢复 compound 的真实 primitive 计算类型，禁止把 byte wrapper 结果伪装成 byte 算术。 */
  private fun updateComputationType(nodeId: JavaNodeId, fallback: JavaIrType): JavaIrType {
    val conversion = lowering.model.updateWriteConversions[nodeId] ?: return fallback
    val first = if (conversion is JavaSemanticConversion.Sequence) conversion.steps.first() else conversion
    val primitive = when (first) {
      is JavaSemanticConversion.PrimitiveNarrowing -> first.from
      is JavaSemanticConversion.Boxing -> first.primitive
      else -> return fallback
    }
    return JavaIrType.Primitive(primitive)
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
        val isCallableParameter = binding.symbol in callable?.parameters.orEmpty()
        if (local.isParameter != isCallableParameter) {
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
    source: JavaAstExpression.Unary,
    operator: JavaAstUnaryOperator,
    span: JavaSourceSpan,
  ): JavaIrExpression {
    val binary = if (operator == JavaAstUnaryOperator.PRE_DECREMENT ||
      operator == JavaAstUnaryOperator.POST_DECREMENT
    ) JavaIrBinaryOperator.SUBTRACT else JavaIrBinaryOperator.ADD
    val read = checkNotNull(boundConversion(source.operand, read(target, span)))
    val one = JavaIrExpression.Constant(
      JavaIrConstant.IntValue(1),
      JavaIrType.Primitive(JavaAstPrimitiveType.INT),
      span,
    )
    val computedType = if (read.type == JavaIrType.Primitive(JavaAstPrimitiveType.LONG)) {
      read.type
    } else {
      JavaIrType.Primitive(JavaAstPrimitiveType.INT)
    }
    val computed = JavaIrExpression.Binary(read, binary, one, computedType, span)
    val writeValue = checkNotNull(updateWriteConversion(source.nodeId, computed, span))
    return write(
      target,
      writeValue,
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
