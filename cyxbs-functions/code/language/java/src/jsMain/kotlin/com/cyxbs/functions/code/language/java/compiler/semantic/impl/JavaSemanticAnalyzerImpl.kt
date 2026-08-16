package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.JavaSemanticAnalyzer
import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.diagnostic.*
import com.cyxbs.functions.code.language.java.compiler.semantic.*
import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * 阶段 0 的严格 Java 语义分析器。
 *
 * 固定按“全部类型 → 全部 static 方法签名 → 全部方法体”执行，源码文件和声明顺序不会影响绑定。
 * Error 只用于失败过程中的恢复；成功结果绝不会把 Error 或猜测出的重载交给 lowering。
 */
internal object JavaSemanticAnalyzerImpl : JavaSemanticAnalyzer {
  /** 构建不可变语义 side table；任意错误都会丢弃半成品模型。 */
  override fun analyze(ast: JavaAstWorkspace): JavaCompilerPhaseResult<JavaSemanticModel> {
    val analysis = Analysis(ast)
    analysis.declareTypes()
    analysis.declareMethods()
    analysis.analyzeBodies()
    return analysis.result()
  }
}

/** 单次编译持有的可变分析状态，完成后统一冻结。 */
private class Analysis(private val ast: JavaAstWorkspace) {
  private val diagnostics = mutableListOf<JavaCompilerDiagnostic>()
  private var nextSymbol = 0
  private val symbols = linkedMapOf<JavaSymbolId, JavaSemanticSymbol>()
  private val declarations = linkedMapOf<JavaNodeId, JavaSymbolId>()
  private val resolved = linkedMapOf<JavaNodeId, JavaSymbolId>()
  private val expressionTypes = linkedMapOf<JavaNodeId, JavaSemanticType>()
  private val conversions = linkedMapOf<JavaNodeId, JavaSemanticConversion>()
  private val calls = linkedMapOf<JavaNodeId, JavaCallableBinding>()
  private val constants = linkedMapOf<JavaNodeId, JavaConstantValue>()

  private val typesByQualifiedName = linkedMapOf<String, TypeInfo>()
  private val typesByNode = linkedMapOf<JavaNodeId, TypeInfo>()
  private val methods = mutableListOf<MethodInfo>()
  private val methodsByOwnerAndName =
    linkedMapOf<Pair<JavaSymbolId, String>, MutableList<MethodInfo>>()
  private var stringSymbol: JavaSymbolId? = null
  private var scope: Scope? = null
  private var currentMethod: MethodInfo? = null
  private val definitelyAssigned = mutableSetOf<JavaSymbolId>()
  private val finalSymbols = mutableSetOf<JavaSymbolId>()

  /** 第一遍登记 package 限定类型；不会用全局 simple-name 后缀猜测类型。 */
  fun declareTypes() {
    val host = ast.units.firstOrNull()
    if (host == null) {
      error(null, "java.semantic.empty_workspace", "Java 工作区不包含可分析的编译单元。")
      return
    }
    stringSymbol = symbol(
      JavaSymbolKind.TYPE,
      "String",
      null,
      host.nodeId,
      host.span,
      null,
      registerDeclaration = false,
    )
    ast.units.forEach { unit ->
      unit.imports.filter { it.isStatic }.forEach {
        error(it.span, "java.semantic.static_import_unsupported", "阶段 0 不支持 static import。")
      }
      unit.types.forEach { declaration ->
        if (declaration.kind != JavaAstTypeDeclarationKind.CLASS) {
          error(declaration.span, "java.semantic.type_kind_unsupported", "阶段 0 仅支持 class。")
        }
        if (declaration.modifiers.any {
            it == JavaAstModifier.PRIVATE ||
              it == JavaAstModifier.PROTECTED ||
              it == JavaAstModifier.STATIC
          }
        ) {
          error(
            declaration.span,
            "java.semantic.invalid_top_level_modifier",
            "顶层 class 不能声明为 private、protected 或 static。",
          )
        }
        if (declaration.typeParameters.isNotEmpty()) {
          error(declaration.span, "java.semantic.generic_type_unsupported", "阶段 0 尚不支持泛型类型。")
        }
        if (declaration.superClass != null || declaration.interfaces.isNotEmpty()) {
          error(declaration.span, "java.semantic.inheritance_unsupported", "阶段 0 尚不支持继承。")
        }
        if (JavaAstModifier.PUBLIC in declaration.modifiers &&
          javaFileName(unit.sourceFile.path) != declaration.name
        ) {
          error(
            declaration.span,
            "java.semantic.public_type_file_mismatch",
            "public 顶层类型 " + declaration.name + " 必须位于同名 Java 文件中。",
          )
        }
        val qualifiedName = qualify(unit.packageName, declaration.name)
        val previous = typesByQualifiedName[qualifiedName]
        if (previous != null) {
          error(
            declaration.span,
            "java.semantic.duplicate_type",
            "限定类型名 " + qualifiedName + " 已在工作区声明。",
            listOf(JavaDiagnosticNote("首次声明位于此处。", previous.declaration.span)),
          )
        } else {
          val id = symbol(
            JavaSymbolKind.TYPE,
            declaration.name,
            null,
            declaration.nodeId,
            declaration.span,
            null,
          )
          TypeInfo(unit, declaration, qualifiedName, id).also {
            typesByQualifiedName[qualifiedName] = it
            typesByNode[declaration.nodeId] = it
          }
        }
      }
    }
    validateExplicitImports()
  }

  /**
   * 类型第一遍完成后统一校验显式 import，即使源码尚未引用该类型也不能让非法 import 假通过。
   *
   * wildcard import 需要完整 classpath/package 索引，阶段 0 暂不在此处验证。
   */
  private fun validateExplicitImports() {
    ast.units.forEach { unit ->
      val imports = unit.imports.filter { !it.isStatic && !it.isWildcard }
      imports.groupBy { it.qualifiedName.substringAfterLast('.') }
        .filterValues { candidates -> candidates.map { it.qualifiedName }.distinct().size > 1 }
        .forEach { (simpleName, candidates) ->
          error(
            candidates.last().span,
            "java.semantic.ambiguous_import",
            "类型名 " + simpleName + " 被多个显式 import 引入。",
            candidates.dropLast(1).map { JavaDiagnosticNote("冲突的 import。", it.span) },
          )
        }
      imports.forEach { declaration ->
        if (declaration.qualifiedName == "java.lang.String") return@forEach
        val type = typesByQualifiedName[declaration.qualifiedName]
        if (type == null) {
          error(
            declaration.span,
            "java.semantic.unknown_import",
            "显式 import 的类型 " + declaration.qualifiedName + " 不在当前编译工作区中。",
          )
        } else {
          accessibleType(type, unit, declaration.span)
        }
      }
    }
  }

  /** 第二遍登记全部方法签名，支持前向调用、互相调用和跨文件静态调用。 */
  fun declareMethods() {
    ast.units.forEach { unit ->
      unit.types.forEach typeLoop@{ declaration ->
        val owner = typesByNode[declaration.nodeId] ?: return@typeLoop
        declaration.members.forEach { member ->
          when (member) {
            is JavaAstMemberDeclaration.Field -> error(
              member.span,
              "java.semantic.field_unsupported",
              "阶段 0 尚不支持字段。",
            )
            is JavaAstMemberDeclaration.Constructor -> error(
              member.span,
              "java.semantic.constructor_unsupported",
              "阶段 0 尚不支持构造器和对象实例。",
            )
            is JavaAstMemberDeclaration.Method -> declareMethod(unit, owner, member)
          }
        }
      }
    }
  }

  /** 第三遍分析方法体；此时所有候选方法已经完整可见。 */
  fun analyzeBodies() {
    methods.forEach(::analyzeBody)
  }

  /** 成功时冻结模型，失败时不暴露包含恢复类型的 side table。 */
  fun result(): JavaCompilerPhaseResult<JavaSemanticModel> {
    if (diagnostics.none { it.severity == JavaDiagnosticSeverity.ERROR } &&
      (expressionTypes.values.any { it == JavaSemanticType.Error } ||
        symbols.values.any { it.type == JavaSemanticType.Error })
    ) {
      error(null, "java.semantic.incomplete_model", "语义分析产生了未诊断的恢复类型。")
    }
    if (diagnostics.any { it.severity == JavaDiagnosticSeverity.ERROR }) {
      return JavaCompilerPhaseResult.failure(diagnostics)
    }
    return JavaCompilerPhaseResult.success(
      JavaSemanticModel(
        ast,
        symbols.toMap(),
        declarations.toMap(),
        resolved.toMap(),
        expressionTypes.toMap(),
        conversions.toMap(),
        calls.toMap(),
        constants.toMap(),
      ),
      diagnostics,
    )
  }

  /** 解析并登记一个方法 descriptor；同名不同参数合法，相同参数列表重复。 */
  private fun declareMethod(
    unit: JavaAstCompilationUnit,
    owner: TypeInfo,
    declaration: JavaAstMemberDeclaration.Method,
  ) {
    if (JavaAstModifier.STATIC !in declaration.modifiers) {
      error(declaration.span, "java.semantic.instance_method_unsupported", "阶段 0 仅支持 static 方法。")
    }
    if (declaration.typeParameters.isNotEmpty()) {
      error(declaration.span, "java.semantic.generic_method_unsupported", "阶段 0 尚不支持泛型方法。")
    }
    if (declaration.body == null) {
      error(declaration.span, "java.semantic.method_body_required", "阶段 0 方法必须提供方法体。")
    }
    val returnType = resolveType(declaration.returnType, unit, allowVoid = true)
    val parameterTypes = declaration.parameters.map { parameter ->
      if (parameter.isVararg) {
        error(parameter.span, "java.semantic.vararg_unsupported", "阶段 0 尚不支持可变参数。")
      }
      resolveType(parameter.type, unit, allowVoid = false)
    }
    val key = owner.symbol to declaration.name
    val previous = methodsByOwnerAndName[key].orEmpty().firstOrNull {
      it.parameterTypes == parameterTypes
    }
    if (previous != null && parameterTypes.none { it == JavaSemanticType.Error }) {
      error(
        declaration.span,
        "java.semantic.duplicate_method_descriptor",
        "方法 " + declaration.name + " 的参数 descriptor 重复。",
        listOf(JavaDiagnosticNote("冲突的方法声明位于此处。", previous.declaration.span)),
      )
    }
    val id = symbol(
      JavaSymbolKind.METHOD,
      declaration.name,
      owner.symbol,
      declaration.nodeId,
      declaration.span,
      returnType,
    )
    val method = MethodInfo(unit, owner, declaration, id, parameterTypes, returnType)
    methods += method
    methodsByOwnerAndName.getOrPut(key) { mutableListOf() } += method
  }

  /** 参数与方法顶层 block 共用冲突范围；嵌套 block 和 for 创建独立生命周期。 */
  private fun analyzeBody(method: MethodInfo) {
    val body = method.declaration.body ?: return
    val previousScope = scope
    val previousMethod = currentMethod
    val previousAssigned = definitelyAssigned.toSet()
    scope = Scope(null)
    currentMethod = method
    definitelyAssigned.clear()
    method.declaration.parameters.zip(method.parameterTypes).forEach { (parameter, type) ->
      val parameterSymbol = declareLocal(
        JavaSymbolKind.PARAMETER,
        parameter.name,
        method.symbol,
        parameter.nodeId,
        parameter.span,
        type,
      )
      definitelyAssigned += parameterSymbol
      if (JavaAstModifier.FINAL in parameter.modifiers) finalSymbols += parameterSymbol
    }
    analyzeStatements(body.statements)
    if (method.returnType != JavaSemanticType.Void &&
      method.returnType != JavaSemanticType.Error &&
      canCompleteNormally(body)
    ) {
      error(
        body.span,
        "java.semantic.missing_return",
        "非 void 方法存在可以正常结束而不返回值的路径。",
      )
    }
    scope = previousScope
    currentMethod = previousMethod
    replaceAssigned(previousAssigned)
  }

  /**
   * 保守判断语句能否正常结束，用于阻止非 void 方法生成隐式 JavaScript `undefined`。
   *
   * 阶段 0 尚不支持 break，因此常量 true 循环可视为不能正常结束；未知条件必须保守视为可能结束。
   */
  private fun canCompleteNormally(statement: JavaAstStatement): Boolean = when (statement) {
    is JavaAstStatement.Block -> statement.statements.fold(true) { reachable, child ->
      reachable && canCompleteNormally(child)
    }
    is JavaAstStatement.If -> statement.elseBranch == null ||
      canCompleteNormally(statement.thenBranch) ||
      canCompleteNormally(statement.elseBranch)
    is JavaAstStatement.While -> !isConstantTrue(statement.condition)
    is JavaAstStatement.For -> statement.condition?.let(::isConstantTrue) != true
    is JavaAstStatement.Return -> false
    else -> true
  }

  /** 识别不依赖语义 side table 的 `true` 与括号包装，供控制流结束性判断。 */
  private fun isConstantTrue(expression: JavaAstExpression): Boolean = when (expression) {
    is JavaAstExpression.Literal ->
      expression.kind == JavaAstLiteralKind.BOOLEAN && expression.tokenText == "true"
    is JavaAstExpression.Parenthesized -> isConstantTrue(expression.expression)
    else -> false
  }

  /** 顺序分析语句，保证同一声明的后一个 declarator 能看到前一个。 */
  private fun analyzeStatements(statements: List<JavaAstStatement>) {
    statements.forEach(::analyzeStatement)
  }

  /** 分析 block、for、return 和基础控制流，并维护词法作用域。 */
  private fun analyzeStatement(statement: JavaAstStatement) {
    when (statement) {
      is JavaAstStatement.Block -> withScope { analyzeStatements(statement.statements) }
      is JavaAstStatement.VariableDeclaration ->
        analyzeVariables(statement.modifiers, statement.type, statement.declarators)
      is JavaAstStatement.Expression -> analyzeExpression(statement.expression)
      is JavaAstStatement.If -> {
        requireType(statement.condition, booleanType(), "if 条件必须是 boolean。")
        val branchEntry = definitelyAssigned.toSet()
        analyzeStatement(statement.thenBranch)
        val thenExit = definitelyAssigned.toSet()
        replaceAssigned(branchEntry)
        statement.elseBranch?.let(::analyzeStatement)
        val elseExit = definitelyAssigned.toSet()
        val completingExits = buildList {
          if (canCompleteNormally(statement.thenBranch)) add(thenExit)
          if (statement.elseBranch == null || canCompleteNormally(statement.elseBranch)) add(elseExit)
        }
        replaceAssigned(
          completingExits.reduceOrNull { common, exit -> common.intersect(exit) }
            ?: branchEntry,
        )
      }
      is JavaAstStatement.While -> {
        requireType(statement.condition, booleanType(), "while 条件必须是 boolean。")
        val afterCondition = definitelyAssigned.toSet()
        analyzeStatement(statement.body)
        // 循环体可能一次也不执行，只有首次 condition 求值后的状态能传播到循环外。
        replaceAssigned(afterCondition)
      }
      is JavaAstStatement.For -> withScope {
        when (val initializer = statement.initializer) {
          is JavaAstForInitializer.VariableDeclaration ->
            analyzeVariables(initializer.modifiers, initializer.type, initializer.declarators)
          is JavaAstForInitializer.Expressions ->
            initializer.expressions.forEach(::analyzeExpression)
          null -> Unit
        }
        statement.condition?.let {
          requireType(it, booleanType(), "for 条件必须是 boolean。")
        }
        val afterCondition = definitelyAssigned.toSet()
        analyzeStatement(statement.body)
        statement.updates.forEach(::analyzeExpression)
        // body/update 可能一次也不执行，不能把其中的赋值错误传播为循环后的 definite assignment。
        replaceAssigned(afterCondition)
      }
      is JavaAstStatement.Return -> analyzeReturn(statement)
      is JavaAstStatement.Empty -> Unit
    }
  }

  /** 初始化值先分析、名称后入作用域，避免错误接受局部变量自引用。 */
  private fun analyzeVariables(
    modifiers: Set<JavaAstModifier>,
    reference: JavaAstTypeReference,
    declarators: List<JavaAstVariableDeclarator>,
  ) {
    val method = method()
    val declaredType = resolveType(reference, method.unit, allowVoid = false)
    declarators.forEach { declarator ->
      declarator.initializer?.let {
        val actual = analyzeExpression(it)
        assign(it.nodeId, actual, declaredType, it.span)
      }
      val symbol = declareLocal(
        JavaSymbolKind.LOCAL_VARIABLE,
        declarator.name,
        method.symbol,
        declarator.nodeId,
        declarator.span,
        declaredType,
      )
      if (declarator.initializer != null) definitelyAssigned += symbol
      if (JavaAstModifier.FINAL in modifiers) {
        finalSymbols += symbol
        if (declarator.initializer == null) {
          error(
            declarator.span,
            "java.semantic.blank_final_unsupported",
            "阶段 0 尚不支持稍后赋值的空白 final 局部变量。",
          )
        }
      }
    }
  }

  /** 校验 return 的有值/无值形态和当前方法返回类型。 */
  private fun analyzeReturn(statement: JavaAstStatement.Return) {
    val expected = method().returnType
    val value = statement.expression
    if (value == null) {
      if (expected != JavaSemanticType.Void && expected != JavaSemanticType.Error) {
        error(
          statement.span,
          "java.semantic.return_type_mismatch",
          "非 void 方法必须返回一个兼容值。",
        )
      }
      return
    }
    val actual = analyzeExpression(value)
    if (expected == JavaSemanticType.Void) {
      error(value.span, "java.semantic.return_type_mismatch", "void 方法不能返回值。")
    } else {
      assign(
        value.nodeId,
        actual,
        expected,
        value.span,
        "java.semantic.return_type_mismatch",
      )
    }
  }

  /** 分析表达式，并保证访问过的每个表达式都写入类型 side table。 */
  private fun analyzeExpression(expression: JavaAstExpression): JavaSemanticType {
    val type = when (expression) {
      is JavaAstExpression.Literal -> literal(expression)
      is JavaAstExpression.Name -> name(expression)
      is JavaAstExpression.Parenthesized -> analyzeExpression(expression.expression)
      is JavaAstExpression.Binary -> binary(expression)
      is JavaAstExpression.Unary -> unary(expression)
      is JavaAstExpression.Assignment -> assignment(expression)
      is JavaAstExpression.MethodInvocation -> invocation(expression)
      is JavaAstExpression.This ->
        unsupported(expression.span, "阶段 0 的 static 方法中不支持 this。")
      is JavaAstExpression.NewObject -> {
        resolveType(expression.type, method().unit, allowVoid = false)
        expression.arguments.forEach(::analyzeExpression)
        unsupported(expression.span, "阶段 0 尚不支持对象创建。")
      }
      is JavaAstExpression.FieldAccess -> {
        analyzeExpression(expression.receiver)
        unsupported(expression.span, "阶段 0 尚不支持字段访问。")
      }
    }
    expressionTypes[expression.nodeId] = type
    return type
  }

  /** 值名称优先绑定局部作用域；未命中时才进入独立的类型命名空间。 */
  private fun name(
    expression: JavaAstExpression.Name,
    requireAssigned: Boolean = true,
  ): JavaSemanticType {
    if ('.' !in expression.qualifiedName) {
      scope?.resolve(expression.qualifiedName)?.let { id ->
        resolved[expression.nodeId] = id
        val type = symbols.getValue(id).type ?: JavaSemanticType.Error
        if (requireAssigned && id !in definitelyAssigned) {
          error(
            expression.span,
            "java.semantic.variable_not_initialized",
            "局部变量 " + expression.qualifiedName + " 可能尚未初始化。",
          )
          return JavaSemanticType.Error
        }
        return type
      }
    }
    val diagnosticsBefore = diagnostics.size
    val info = visibleTypeOrNull(expression.qualifiedName, method().unit, expression.span)
    if (info == null && diagnostics.size == diagnosticsBefore) {
      error(
        expression.span,
        "java.semantic.undefined_name",
        "名称 " + expression.qualifiedName + " 在当前值或类型作用域中未定义。",
      )
    }
    if (info == null) {
      return JavaSemanticType.Error
    }
    resolved[expression.nodeId] = info.symbol
    return JavaSemanticType.Declared(info.symbol, emptyList())
  }

  /** 二元运算不采用 JavaScript 的隐式真值或字符串转换。 */
  private fun binary(expression: JavaAstExpression.Binary): JavaSemanticType {
    val left = analyzeExpression(expression.left)
    val right = analyzeExpression(expression.right)
    if (left == JavaSemanticType.Error || right == JavaSemanticType.Error) {
      return JavaSemanticType.Error
    }
    return when (expression.operator) {
      JavaAstBinaryOperator.MULTIPLY,
      JavaAstBinaryOperator.DIVIDE,
      JavaAstBinaryOperator.REMAINDER,
      JavaAstBinaryOperator.ADD,
      JavaAstBinaryOperator.SUBTRACT,
      JavaAstBinaryOperator.SHIFT_LEFT,
      JavaAstBinaryOperator.SHIFT_RIGHT,
      JavaAstBinaryOperator.UNSIGNED_SHIFT_RIGHT,
      -> binaryRequired(
        expression.span,
        left,
        right,
        intType(),
        intType(),
        "算术和位移操作数必须是 int。",
      )
      JavaAstBinaryOperator.LESS_THAN,
      JavaAstBinaryOperator.LESS_THAN_OR_EQUAL,
      JavaAstBinaryOperator.GREATER_THAN,
      JavaAstBinaryOperator.GREATER_THAN_OR_EQUAL,
      -> binaryRequired(
        expression.span,
        left,
        right,
        intType(),
        booleanType(),
        "关系比较操作数必须是 int。",
      )
      JavaAstBinaryOperator.EQUAL,
      JavaAstBinaryOperator.NOT_EQUAL,
      -> if (equalityCompatible(left, right)) {
        booleanType()
      } else {
        error(
          expression.span,
          "java.semantic.invalid_binary_operands",
          "等值比较的两个操作数类型不兼容。",
        )
        JavaSemanticType.Error
      }
      JavaAstBinaryOperator.BITWISE_AND,
      JavaAstBinaryOperator.BITWISE_XOR,
      JavaAstBinaryOperator.BITWISE_OR,
      -> when {
        left == intType() && right == intType() -> intType()
        left == booleanType() && right == booleanType() -> booleanType()
        else -> {
          error(
            expression.span,
            "java.semantic.invalid_binary_operands",
            "位运算要求两个 int 或两个 boolean 操作数。",
          )
          JavaSemanticType.Error
        }
      }
      JavaAstBinaryOperator.LOGICAL_AND,
      JavaAstBinaryOperator.LOGICAL_OR,
      -> binaryRequired(
        expression.span,
        left,
        right,
        booleanType(),
        booleanType(),
        "逻辑操作数必须是 boolean。",
      )
    }
  }

  /** 一元运算只开放 int 数值/位运算、boolean 取反和可写 int 的递增递减。 */
  private fun unary(expression: JavaAstExpression.Unary): JavaSemanticType {
    val operand = analyzeExpression(expression.operand)
    if (operand == JavaSemanticType.Error) return JavaSemanticType.Error
    return when (expression.operator) {
      JavaAstUnaryOperator.POSITIVE,
      JavaAstUnaryOperator.NEGATIVE,
      JavaAstUnaryOperator.BITWISE_NOT,
      -> unaryRequired(expression.span, operand, intType(), "该一元操作数必须是 int。")
      JavaAstUnaryOperator.LOGICAL_NOT ->
        unaryRequired(expression.span, operand, booleanType(), "逻辑取反操作数必须是 boolean。")
      JavaAstUnaryOperator.PRE_INCREMENT,
      JavaAstUnaryOperator.PRE_DECREMENT,
      JavaAstUnaryOperator.POST_INCREMENT,
      JavaAstUnaryOperator.POST_DECREMENT,
      -> if (isFinal(expression.operand)) {
        error(
          expression.span,
          "java.semantic.final_assignment",
          "final 参数或局部变量不能递增或递减。",
        )
        JavaSemanticType.Error
      } else if (writable(expression.operand) && operand == intType()) {
        intType()
      } else {
        error(
          expression.span,
          "java.semantic.invalid_update_target",
          "递增递减目标必须是可写的 int 参数或局部变量。",
        )
        JavaSemanticType.Error
      }
    }
  }

  /** 简单赋值使用保守兼容性，复合赋值当前只开放 int。 */
  private fun assignment(expression: JavaAstExpression.Assignment): JavaSemanticType {
    val target = if (expression.target is JavaAstExpression.Name) {
      name(
        expression.target,
        requireAssigned = expression.operator != JavaAstAssignmentOperator.ASSIGN,
      ).also { expressionTypes[expression.target.nodeId] = it }
    } else {
      analyzeExpression(expression.target)
    }
    val value = analyzeExpression(expression.value)
    if (!writable(expression.target)) {
      error(
        expression.target.span,
        "java.semantic.invalid_assignment_target",
        "赋值目标必须是参数或局部变量。",
      )
      return JavaSemanticType.Error
    }
    if (isFinal(expression.target)) {
      error(
        expression.target.span,
        "java.semantic.final_assignment",
        "final 参数或局部变量不能再次赋值。",
      )
      return JavaSemanticType.Error
    }
    if (target == JavaSemanticType.Error || value == JavaSemanticType.Error) {
      return JavaSemanticType.Error
    }
    val result = if (expression.operator == JavaAstAssignmentOperator.ASSIGN) {
      if (assign(expression.value.nodeId, value, target, expression.value.span)) {
        target
      } else {
        JavaSemanticType.Error
      }
    } else if (target == intType() && value == intType()) {
      intType()
    } else {
      error(
        expression.span,
        "java.semantic.invalid_compound_assignment",
        "阶段 0 复合赋值两侧必须都是 int。",
      )
      JavaSemanticType.Error
    }
    if (result != JavaSemanticType.Error) {
      resolved[expression.target.nodeId]?.let(definitelyAssigned::add)
    }
    return result
  }

  /**
   * 静态调用无 receiver 时只查当前 class，TypeName receiver 精确指定 class。
   * 值 receiver 显式失败，避免把未来实例调用误绑定到同名 static 方法。
   */
  private fun invocation(expression: JavaAstExpression.MethodInvocation): JavaSemanticType {
    if (expression.typeArguments.isNotEmpty()) {
      error(
        expression.span,
        "java.semantic.explicit_type_arguments_unsupported",
        "阶段 0 尚不支持显式方法类型实参。",
      )
    }
    val owner = invocationOwner(expression.receiver)
    val argumentTypes = expression.arguments.map(::analyzeExpression)
    if (owner == null || argumentTypes.any { it == JavaSemanticType.Error }) {
      return JavaSemanticType.Error
    }
    val namedCandidates = methodsByOwnerAndName[owner.symbol to expression.methodName].orEmpty()
      .filter { JavaAstModifier.STATIC in it.declaration.modifiers }
    val candidates = namedCandidates.filter(::isMethodAccessible)
    if (namedCandidates.isNotEmpty() && candidates.isEmpty()) {
      error(
        expression.span,
        "java.semantic.inaccessible_method",
        "static 方法 " + expression.methodName + " 在当前 compilation unit 中不可访问。",
        namedCandidates.map { JavaDiagnosticNote("不可访问的方法声明。", it.declaration.span) },
      )
      return JavaSemanticType.Error
    }
    val applicable = candidates.filter { candidate ->
      candidate.parameterTypes.size == argumentTypes.size &&
        argumentTypes.zip(candidate.parameterTypes).all { (actual, expected) ->
          compatibility(actual, expected) != null
        }
    }
    if (applicable.isEmpty()) {
      error(
        expression.span,
        "java.semantic.no_applicable_overload",
        "找不到参数数量和类型均兼容的 static 方法 " + expression.methodName + "。",
        candidates.map { JavaDiagnosticNote("候选方法声明。", it.declaration.span) },
      )
      return JavaSemanticType.Error
    }
    if (applicable.size > 1) {
      error(
        expression.span,
        "java.semantic.ambiguous_overload",
        "static 方法 " + expression.methodName + " 存在多个同等兼容的重载。",
        applicable.map { JavaDiagnosticNote("可适用的候选方法。", it.declaration.span) },
      )
      return JavaSemanticType.Error
    }
    val selected = applicable.single()
    expression.arguments.zip(selected.parameterTypes).forEach { (argument, expected) ->
      conversions[argument.nodeId] =
        checkNotNull(compatibility(expressionTypes.getValue(argument.nodeId), expected))
    }
    resolved[expression.nodeId] = selected.symbol
    calls[expression.nodeId] = JavaCallableBinding(
      selected.symbol,
      JavaDispatchKind.STATIC,
      selected.parameterTypes,
      selected.returnType,
      emptyMap(),
    )
    return selected.returnType
  }

  /** 类型名 receiver 单独解析，局部同名值优先并被报告为不支持的实例 receiver。 */
  private fun invocationOwner(receiver: JavaAstExpression?): TypeInfo? {
    if (receiver == null) return method().owner
    if (receiver is JavaAstExpression.Name) {
      if ('.' !in receiver.qualifiedName && scope?.resolve(receiver.qualifiedName) != null) {
        analyzeExpression(receiver)
        error(
          receiver.span,
          "java.semantic.value_receiver_unsupported",
          "阶段 0 不支持通过对象值调用方法。",
        )
        return null
      }
      val info = visibleType(receiver.qualifiedName, method().unit, receiver.span)
      expressionTypes[receiver.nodeId] =
        info?.let { JavaSemanticType.Declared(it.symbol, emptyList()) } ?: JavaSemanticType.Error
      if (info != null) resolved[receiver.nodeId] = info.symbol
      return info
    }
    analyzeExpression(receiver)
    error(
      receiver.span,
      "java.semantic.value_receiver_unsupported",
      "阶段 0 的调用 receiver 必须是类型名。",
    )
    return null
  }

  /** 登记 boolean、int、String 和 null 字面量的真实常量值。 */
  private fun literal(expression: JavaAstExpression.Literal): JavaSemanticType =
    when (expression.kind) {
      JavaAstLiteralKind.BOOLEAN -> booleanType().also {
        constants[expression.nodeId] =
          JavaConstantValue.BooleanValue(expression.tokenText == "true")
      }
      JavaAstLiteralKind.INTEGER -> integerLiteral(expression)
      JavaAstLiteralKind.STRING -> stringLiteral(expression)
      JavaAstLiteralKind.NULL -> JavaSemanticType.Null.also {
        constants[expression.nodeId] = JavaConstantValue.NullValue
      }
      JavaAstLiteralKind.FLOATING_POINT,
      JavaAstLiteralKind.CHARACTER,
      -> unsupported(
        expression.span,
        "阶段 0 仅支持 boolean、int、String 和 null 字面量。",
      )
    }

  /** 解析 Java 常见四种进制 int，并区分格式错误、long 与范围错误。 */
  private fun integerLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val token = expression.tokenText
    if (token.endsWith('l', ignoreCase = true)) {
      error(
        expression.span,
        "java.semantic.integer_type_unsupported",
        "阶段 0 尚不支持 long 整数字面量。",
      )
      return JavaSemanticType.Error
    }
    return when (val parsed = parseJavaInt(token)) {
      is ParsedInt.Invalid -> {
        error(expression.span, parsed.code, parsed.message)
        JavaSemanticType.Error
      }
      is ParsedInt.Value -> intType().also {
        constants[expression.nodeId] = JavaConstantValue.IntValue(parsed.value)
      }
    }
  }

  /** 解码常用、Unicode 与八进制 Java String 转义，不保存带引号 token。 */
  private fun stringLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val value = decodeJavaString(expression.tokenText)
    if (value == null) {
      error(
        expression.span,
        "java.semantic.invalid_string_literal",
        "String 字面量包含无效或不完整的 Java 转义。",
      )
      return JavaSemanticType.Error
    }
    constants[expression.nodeId] = JavaConstantValue.StringValue(value)
    val id = stringSymbol
    if (id == null) {
      error(expression.span, "java.semantic.incomplete_model", "缺少 String 内建类型。")
      return JavaSemanticType.Error
    }
    return JavaSemanticType.Declared(id, emptyList())
  }

  /** 阶段 0 类型只开放 int、boolean、String、源码 class 与返回位置的 void。 */
  private fun resolveType(
    reference: JavaAstTypeReference,
    unit: JavaAstCompilationUnit,
    allowVoid: Boolean,
  ): JavaSemanticType = when (reference) {
    is JavaAstTypeReference.Primitive ->
      if (reference.kind == JavaAstPrimitiveType.INT ||
        reference.kind == JavaAstPrimitiveType.BOOLEAN
      ) {
        JavaSemanticType.Primitive(reference.kind)
      } else {
        error(
          reference.span,
          "java.semantic.primitive_type_unsupported",
          "阶段 0 仅支持 int 与 boolean primitive。",
        )
        JavaSemanticType.Error
      }
    is JavaAstTypeReference.Void ->
      if (allowVoid) {
        JavaSemanticType.Void
      } else {
        error(
          reference.span,
          "java.semantic.void_type_not_allowed",
          "该声明位置不能使用 void。",
        )
        JavaSemanticType.Error
      }
    is JavaAstTypeReference.Named -> {
      if (reference.arguments.isNotEmpty()) {
        error(
          reference.span,
          "java.semantic.parameterized_type_unsupported",
          "阶段 0 尚不支持参数化类型。",
        )
        JavaSemanticType.Error
      } else {
        declaredType(reference.qualifiedName, unit, reference.span)?.also {
          if (it is JavaSemanticType.Declared) resolved[reference.nodeId] = it.symbol
        } ?: JavaSemanticType.Error
      }
    }
    is JavaAstTypeReference.Array -> {
      error(reference.span, "java.semantic.array_type_unsupported", "阶段 0 尚不支持数组。")
      JavaSemanticType.Error
    }
    is JavaAstTypeReference.Wildcard -> {
      error(
        reference.span,
        "java.semantic.wildcard_type_unsupported",
        "阶段 0 尚不支持通配符。",
      )
      JavaSemanticType.Error
    }
  }

  /** 解析声明类型；同 package 类型优先于 java.lang.String。 */
  private fun declaredType(
    name: String,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): JavaSemanticType? {
    if (name == "java.lang.String") {
      return stringSymbol?.let { JavaSemanticType.Declared(it, emptyList()) }
    }
    typesByQualifiedName[qualify(unit.packageName, name)]?.let {
      return JavaSemanticType.Declared(it.symbol, emptyList())
    }
    val diagnosticsBefore = diagnostics.size
    val visible = visibleTypeOrNull(name, unit, span)
    if (visible != null) return JavaSemanticType.Declared(visible.symbol, emptyList())
    if (name == "String") {
      return stringSymbol?.let { JavaSemanticType.Declared(it, emptyList()) }
    }
    if (diagnostics.size == diagnosticsBefore) {
      error(span, "java.semantic.unknown_type", "当前 compilation unit 无法解析类型名 " + name + "。")
    }
    return null
  }

  /** 表达式中的类型名解析需要直接产生 unknown_type 诊断。 */
  private fun visibleType(
    name: String,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): TypeInfo? {
    val diagnosticsBefore = diagnostics.size
    val result = visibleTypeOrNull(name, unit, span)
    if (result == null && diagnostics.size == diagnosticsBefore) {
      error(span, "java.semantic.unknown_type", "当前 compilation unit 无法解析类型名 " + name + "。")
    }
    return result
  }

  /** 按限定名、同 package、显式 import、通配 import 解析，绝不跨 package 猜后缀。 */
  private fun visibleTypeOrNull(
    name: String,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): TypeInfo? {
    if ('.' in name) return typesByQualifiedName[name]?.let { accessibleType(it, unit, span) }
    typesByQualifiedName[qualify(unit.packageName, name)]?.let { return it }
    val explicit = unit.imports
      .filter {
        !it.isStatic && !it.isWildcard && it.qualifiedName.substringAfterLast('.') == name
      }
      .mapNotNull { typesByQualifiedName[it.qualifiedName] }
      .distinctBy { it.symbol }
    if (explicit.size == 1) return accessibleType(explicit.single(), unit, span)
    if (explicit.size > 1) {
      error(span, "java.semantic.ambiguous_type", "类型名 " + name + " 被多个显式 import 引入。")
      return null
    }
    val wildcard = unit.imports
      .filter { !it.isStatic && it.isWildcard }
      .mapNotNull { typesByQualifiedName[qualify(it.qualifiedName, name)] }
      .distinctBy { it.symbol }
    if (wildcard.size == 1) return accessibleType(wildcard.single(), unit, span)
    if (wildcard.size > 1) {
      error(span, "java.semantic.ambiguous_type", "类型名 " + name + " 在多个通配 import 中可见。")
    }
    return null
  }

  /** 跨 package 只允许访问 public 顶层类型；同 package 保留 Java 默认可见性。 */
  private fun accessibleType(
    type: TypeInfo,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): TypeInfo? {
    if (type.unit.packageName == unit.packageName ||
      JavaAstModifier.PUBLIC in type.declaration.modifiers
    ) {
      return type
    }
    error(
      span,
      "java.semantic.inaccessible_type",
      "类型 " + type.qualifiedName + " 在当前 package 中不可访问。",
      listOf(JavaDiagnosticNote("不可访问的类型声明。", type.declaration.span)),
    )
    return null
  }

  /** 阶段 0 无继承：同 class 全部可见，同 package 接受默认/protected，跨 package 仅 public。 */
  private fun isMethodAccessible(candidate: MethodInfo): Boolean {
    val current = method()
    if (candidate.owner.symbol == current.owner.symbol) return true
    if (JavaAstModifier.PRIVATE in candidate.declaration.modifiers) return false
    if (candidate.unit.packageName == current.unit.packageName) return true
    return JavaAstModifier.PUBLIC in candidate.declaration.modifiers
  }

  /** 要求表达式具备目标类型，并登记 identity/null 引用转换。 */
  private fun requireType(
    expression: JavaAstExpression,
    expected: JavaSemanticType,
    message: String,
  ): JavaSemanticType {
    val actual = analyzeExpression(expression)
    return if (assign(expression.nodeId, actual, expected, expression.span, message = message)) {
      expected
    } else {
      JavaSemanticType.Error
    }
  }

  /** 阶段 0 只允许 identity 和 null 到声明类型的赋值兼容。 */
  private fun assign(
    nodeId: JavaNodeId,
    actual: JavaSemanticType,
    expected: JavaSemanticType,
    span: JavaSourceSpan,
    code: String = "java.semantic.type_mismatch",
    message: String = "表达式类型与目标类型不兼容。",
  ): Boolean {
    if (actual == JavaSemanticType.Error || expected == JavaSemanticType.Error) return false
    val conversion = compatibility(actual, expected)
    if (conversion == null) {
      error(span, code, message)
      return false
    }
    conversions[nodeId] = conversion
    return true
  }

  /** 重载筛选与赋值共用同一保守兼容规则。 */
  private fun compatibility(
    actual: JavaSemanticType,
    expected: JavaSemanticType,
  ): JavaSemanticConversion? = when {
    actual == expected -> JavaSemanticConversion.Identity
    actual == JavaSemanticType.Null && expected is JavaSemanticType.Declared ->
      JavaSemanticConversion.ReferenceWidening(actual, expected)
    else -> null
  }

  /** 等值比较接受同类型，或 null 与声明类型。 */
  private fun equalityCompatible(
    left: JavaSemanticType,
    right: JavaSemanticType,
  ): Boolean =
    left == right && left != JavaSemanticType.Void ||
      left == JavaSemanticType.Null && right is JavaSemanticType.Declared ||
      right == JavaSemanticType.Null && left is JavaSemanticType.Declared

  /** 校验同类型二元操作并返回指定结果类型。 */
  private fun binaryRequired(
    span: JavaSourceSpan,
    left: JavaSemanticType,
    right: JavaSemanticType,
    required: JavaSemanticType,
    result: JavaSemanticType,
    message: String,
  ): JavaSemanticType {
    if (left == required && right == required) return result
    error(span, "java.semantic.invalid_binary_operands", message)
    return JavaSemanticType.Error
  }

  /** 校验一元操作类型。 */
  private fun unaryRequired(
    span: JavaSourceSpan,
    actual: JavaSemanticType,
    required: JavaSemanticType,
    message: String,
  ): JavaSemanticType {
    if (actual == required) return required
    error(span, "java.semantic.invalid_unary_operand", message)
    return JavaSemanticType.Error
  }

  /** 参数和局部变量名称是阶段 0 唯一可写左值。 */
  private fun writable(expression: JavaAstExpression): Boolean {
    if (expression !is JavaAstExpression.Name) return false
    val declaration = resolved[expression.nodeId]?.let(symbols::get) ?: return false
    return declaration.kind == JavaSymbolKind.PARAMETER ||
      declaration.kind == JavaSymbolKind.LOCAL_VARIABLE
  }

  /** 判断已经解析的名称是否指向 final 参数或局部变量。 */
  private fun isFinal(expression: JavaAstExpression): Boolean =
    expression is JavaAstExpression.Name && resolved[expression.nodeId] in finalSymbols

  /** 登记词法值；Java 不允许遮蔽仍处于作用域内的参数或局部变量。 */
  private fun declareLocal(
    kind: JavaSymbolKind,
    name: String,
    owner: JavaSymbolId,
    nodeId: JavaNodeId,
    span: JavaSourceSpan,
    type: JavaSemanticType,
  ): JavaSymbolId {
    val current = checkNotNull(scope)
    val previous = current.resolve(name)
    if (previous != null) {
      error(
        span,
        "java.semantic.duplicate_local_declaration",
        "名称 " + name + " 与仍处于作用域内的局部声明重复。",
        listOf(JavaDiagnosticNote("原声明位于此处。", symbols.getValue(previous).declarationSpan)),
      )
    }
    val id = symbol(kind, name, owner, nodeId, span, type)
    if (name !in current.values) current.values[name] = id
    return id
  }

  /** 创建符号；内建 String 不登记为用户源码 declaration。 */
  private fun symbol(
    kind: JavaSymbolKind,
    name: String,
    owner: JavaSymbolId?,
    nodeId: JavaNodeId,
    span: JavaSourceSpan,
    type: JavaSemanticType?,
    registerDeclaration: Boolean = true,
  ): JavaSymbolId {
    val id = JavaSymbolId(nextSymbol++)
    symbols[id] = JavaSemanticSymbol(id, kind, name, owner, nodeId, span, type)
    if (registerDeclaration) declarations[nodeId] = id
    return id
  }

  /** 创建并在退出后恢复 block/for 作用域。 */
  private inline fun withScope(block: () -> Unit) {
    val previous = scope
    scope = Scope(previous)
    try {
      block()
    } finally {
      scope = previous
    }
  }

  /** 用分支交集或循环入口状态替换当前 definite-assignment 集合。 */
  private fun replaceAssigned(symbols: Set<JavaSymbolId>) {
    definitelyAssigned.clear()
    definitelyAssigned += symbols
  }

  /** 获取当前方法上下文；只有分析器内部调用顺序错误时才抛出。 */
  private fun method(): MethodInfo =
    checkNotNull(currentMethod) { "Semantic expression analysis outside a method body." }

  /** 添加稳定 ERROR 诊断。 */
  private fun error(
    span: JavaSourceSpan?,
    code: String,
    message: String,
    notes: List<JavaDiagnosticNote> = emptyList(),
  ) {
    diagnostics += JavaCompilerDiagnostic(
      code,
      message,
      JavaDiagnosticSeverity.ERROR,
      span,
      notes,
    )
  }

  /** 报告未开放表达式并返回仅供恢复的 Error。 */
  private fun unsupported(span: JavaSourceSpan, message: String): JavaSemanticType {
    error(span, "java.semantic.expression_unsupported", message)
    return JavaSemanticType.Error
  }

  private fun intType() = JavaSemanticType.Primitive(JavaAstPrimitiveType.INT)
  private fun booleanType() = JavaSemanticType.Primitive(JavaAstPrimitiveType.BOOLEAN)
}

/** compilation unit 中完成限定名登记的源码类型。 */
private data class TypeInfo(
  val unit: JavaAstCompilationUnit,
  val declaration: JavaAstTypeDeclaration,
  val qualifiedName: String,
  val symbol: JavaSymbolId,
)

/** 第二遍冻结的方法签名，第三遍不会重复解析类型。 */
private data class MethodInfo(
  val unit: JavaAstCompilationUnit,
  val owner: TypeInfo,
  val declaration: JavaAstMemberDeclaration.Method,
  val symbol: JavaSymbolId,
  val parameterTypes: List<JavaSemanticType>,
  val returnType: JavaSemanticType,
)

/** 方法体局部值命名空间；类型命名空间独立。 */
private class Scope(val parent: Scope?) {
  val values = linkedMapOf<String, JavaSymbolId>()
  fun resolve(name: String): JavaSymbolId? = values[name] ?: parent?.resolve(name)
}

/** int 字面量解析结果，错误携带稳定诊断 code。 */
private sealed interface ParsedInt {
  data class Value(val value: Int) : ParsedInt
  data class Invalid(val code: String, val message: String) : ParsedInt
}

/** 解析 Java 常用 int 格式；阶段 0 不接受依赖二补数解释的极端无符号写法。 */
private fun parseJavaInt(token: String): ParsedInt {
  if (token.isEmpty() || token.first() == '_' || token.last() == '_' || "__" in token) {
    return ParsedInt.Invalid(
      "java.semantic.invalid_integer_literal",
      "int 字面量的下划线位置无效。",
    )
  }
  val compact = token.replace("_", "")
  val radix: Int
  val digits: String
  when {
    compact.startsWith("0x", ignoreCase = true) -> {
      radix = 16
      digits = compact.substring(2)
    }
    compact.startsWith("0b", ignoreCase = true) -> {
      radix = 2
      digits = compact.substring(2)
    }
    compact.length > 1 && compact.startsWith('0') -> {
      radix = 8
      digits = compact.substring(1)
    }
    else -> {
      radix = 10
      digits = compact
    }
  }
  if (digits.isEmpty() || digits.any { digitValue(it) !in 0 until radix }) {
    return ParsedInt.Invalid(
      "java.semantic.invalid_integer_literal",
      "int 字面量不符合对应进制格式。",
    )
  }
  val value = digits.toLongOrNull(radix)
    ?: return ParsedInt.Invalid(
      "java.semantic.integer_out_of_range",
      "int 字面量超出阶段 0 可表示范围。",
    )
  if (value > Int.MAX_VALUE) {
    return ParsedInt.Invalid(
      "java.semantic.integer_out_of_range",
      "int 字面量超出 Int.MAX_VALUE。",
    )
  }
  return ParsedInt.Value(value.toInt())
}

/** 把 ASCII 数字转换为数值，不依赖平台 locale。 */
private fun digitValue(character: Char): Int = when (character) {
  in '0'..'9' -> character - '0'
  in 'a'..'f' -> character - 'a' + 10
  in 'A'..'F' -> character - 'A' + 10
  else -> -1
}

/** 解码 Java 8 String token；失败返回 null，由调用方转换为源码诊断。 */
private fun decodeJavaString(token: String): String? {
  if (token.length < 2 || token.first() != '"' || token.last() != '"') return null
  val result = StringBuilder()
  var index = 1
  val end = token.lastIndex
  while (index < end) {
    val current = token[index++]
    if (current != '\\') {
      result.append(current)
      continue
    }
    if (index >= end) return null
    when (val escaped = token[index++]) {
      'b' -> result.append('\b')
      't' -> result.append('\t')
      'n' -> result.append('\n')
      'f' -> result.append('\u000C')
      'r' -> result.append('\r')
      '"' -> result.append('"')
      '\'' -> result.append('\'')
      '\\' -> result.append('\\')
      'u' -> {
        while (index < end && token[index] == 'u') index++
        if (index + 4 > end) return null
        var value = 0
        repeat(4) {
          val digit = digitValue(token[index++])
          if (digit !in 0..15) return null
          value = value * 16 + digit
        }
        result.append(value.toChar())
      }
      in '0'..'7' -> {
        var value = escaped - '0'
        var remaining = if (escaped <= '3') 2 else 1
        while (remaining > 0 && index < end && token[index] in '0'..'7') {
          value = value * 8 + (token[index++] - '0')
          remaining--
        }
        result.append(value.toChar())
      }
      else -> return null
    }
  }
  return result.toString()
}

/** 默认包保持简单名，否则用点号连接 package 与类型名。 */
private fun qualify(packageName: String?, simpleName: String): String =
  if (packageName.isNullOrEmpty()) simpleName else packageName + "." + simpleName

/** 从规范或平台路径中取得不含 `.java` 后缀的文件名；其他扩展名不能冒充 Java 源文件。 */
private fun javaFileName(path: String): String? {
  val fileName = path.substringAfterLast('/').substringAfterLast('\\')
  return fileName.takeIf { it.endsWith(".java") }?.removeSuffix(".java")
}
