package com.cyxbs.functions.code.language.java.compiler.frontend

import com.cyxbs.functions.code.language.java.parser
import com.cyxbs.functions.code.language.java.compiler.JavaAstFrontend
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstAssignmentOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstAnnotation
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstArrayDimension
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstArrayInitializer
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstArrayInitializerElement
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstCompilationUnit
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstConstructorInvocationKind
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstExpression
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstForInitializer
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstImport
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstLiteralKind
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstMemberDeclaration
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstModifier
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstParameter
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstStatement
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstTypeDeclaration
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstTypeDeclarationKind
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstTypeParameter
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstTypeReference
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstUnaryOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstVariableDeclarator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstWorkspace
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerDiagnostic
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticSeverity
import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeIdSequence
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import com.cyxbs.functions.code.language.lezer.LezerSyntaxNode

/**
 * 从 @lezer/java 的 CST 严格构建 Stage1 AST。
 *
 * Lezer 可为编辑器恢复不完整源码；编译器先拒绝 error 节点，再只接受明确映射的 Java 8 子集，
 * 因而不会把恢复树或半成品 AST 交给后续语义阶段。
 */
internal object JavaLezerAstFrontend : JavaAstFrontend {
  /** 解析整个工作区，任何 ERROR 都阻止返回 AST。 */
  override fun parse(workspace: JavaSourceWorkspace): JavaCompilerPhaseResult<JavaAstWorkspace> {
    val diagnostics = mutableListOf<JavaCompilerDiagnostic>()
    val units = workspace.files.mapNotNull { file ->
      val tree = parser.parse(file.source)
      val recovered = tree.topNode.firstRecoveryNode()
      if (recovered != null) {
        diagnostics += error(file, recovered, "java.syntax.recovery", "Java 源码包含语法错误恢复节点，不能编译。")
        null
      } else {
        try {
          JavaLezerFileAdapter(file).build(tree.topNode)
        } catch (issue: JavaFrontendIssue) {
          diagnostics += error(file, issue.node, issue.code, issue.message)
          null
        }
      }
    }
    return if (diagnostics.any { it.severity == JavaDiagnosticSeverity.ERROR }) {
      JavaCompilerPhaseResult.failure(diagnostics)
    } else {
      JavaCompilerPhaseResult.success(JavaAstWorkspace(units))
    }
  }

  /** 将局部 CST 错误转换为稳定的公共诊断。 */
  private fun error(file: JavaSourceFile, node: LezerSyntaxNode, code: String, message: String): JavaCompilerDiagnostic {
    return JavaCompilerDiagnostic(code, message, JavaDiagnosticSeverity.ERROR, JavaSourceSpan(file.id, node.from, node.to))
  }
}

/** 单文件 CST adapter；表达式只按节点层级和 token 节点建模，不通过源码扫描重建。 */
private class JavaLezerFileAdapter(private val file: JavaSourceFile) {
  private val ids = JavaNodeIdSequence(file.id)

  /** 构建 package、imports 与顶层 class。 */
  fun build(root: LezerSyntaxNode): JavaAstCompilationUnit {
    val packageNode = root.children().firstOrNull { it.name == "PackageDeclaration" }
    val types = root.children().filter { it.name in TYPE_NODES }
    if (types.isEmpty()) unsupported(root, "Stage1 至少需要一个顶层 class 或 interface。")
    if (root.children().any { it.name !in TYPE_NODES + setOf("PackageDeclaration", "ImportDeclaration", ";") && !it.trivia() }) {
      unsupported(root, "Stage1 不支持该顶层语法。")
    }
    return JavaAstCompilationUnit(
      ids.next(), span(root), file, packageNode?.qualifiedName(),
      root.children().filter { it.name == "ImportDeclaration" }.map(::import),
      types.map(::type),
    )
  }

  /** 构建 import，不让 classpath 或 wildcard 解析泄漏进前端。 */
  private fun import(node: LezerSyntaxNode): JavaAstImport {
    // ScopedIdentifier 内部还包含多个 Identifier，必须取 declaration 的直接名称节点，
    // 否则 import b.Helper 会被截断成 Helper。
    val name = node.children().firstOrNull { it.name in NAME_NODES }
      ?: unsupported(node, "import 缺少限定名。")
    return JavaAstImport(ids.next(), span(node), text(name),
      node.children().any { text(it) == "static" }, node.descendants().any { text(it) == "*" })
  }

  /**
   * 构建阶段 1 的 class 或 interface。
   *
   * 继承和泛型必须从 CST 的专用 clause 读取，不能仅保留 class 名称后把这些结构静默降级；
   * interface 虽可构建 AST，但其执行语义仍由后续阶段决定是否开放。
   */
  private fun type(node: LezerSyntaxNode): JavaAstTypeDeclaration {
    val kind = when (node.name) {
      "ClassDeclaration" -> JavaAstTypeDeclarationKind.CLASS
      "InterfaceDeclaration" -> JavaAstTypeDeclarationKind.INTERFACE
      else -> unsupported(node, "阶段 1 仅支持 class 和 interface。")
    }
    val definition = node.children().firstOrNull { it.name == "Definition" } ?: unsupported(node, "class 缺少名称。")
    val body = node.children().firstOrNull {
      it.name == "ClassBody" || it.name == "InterfaceBody"
    } ?: unsupported(node, "类型缺少主体。")
    val modifiers = node.modifiersBefore(definition, TYPE_MODIFIERS, "类型")
    rejectAnnotationsBefore(node, definition, "类型")
    node.descendants().firstOrNull { it.name in UNSUPPORTED_NODES }?.let {
      unsupported(it, "阶段 1 不支持 Java 8 之外的语法。")
    }
    val members = body.children().mapNotNull { child ->
      when {
        child.name in MEMBER_NODES -> member(child, kind)
        child.name == "{" || child.name == "}" || child.name == ";" || child.trivia() -> null
        else -> unsupported(child, "阶段 1 不支持嵌套类型、初始化块或该 class 成员。")
      }
    }
    return JavaAstTypeDeclaration(
      ids.next(),
      span(node),
      kind,
      modifiers,
      text(definition),
      node.children().firstOrNull { it.name == "TypeParameters" }?.let(::typeParameters).orEmpty(),
      node.children().firstOrNull { it.name == "Superclass" }?.onlyTypeReference(),
      node.children().firstOrNull { it.name in INTERFACE_CLAUSES }?.typeReferences().orEmpty(),
      members,
    )
  }

  /** 构建字段、构造器或实例/static 方法；成员类别由 class body 的直接 child 决定。 */
  private fun member(
    node: LezerSyntaxNode,
    ownerKind: JavaAstTypeDeclarationKind,
  ): JavaAstMemberDeclaration = when (node.name) {
    "FieldDeclaration" -> field(node)
    "MethodDeclaration" -> method(node, ownerKind)
    "ConstructorDeclaration" -> constructor(node)
    else -> unsupported(node, "阶段 1 不支持嵌套类型或该 class 成员。")
  }

  /** 字段按单个 declarator 保留其初始化表达式。 */
  private fun field(node: LezerSyntaxNode): JavaAstMemberDeclaration.Field {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "字段缺少名称。")
    rejectAnnotationsBefore(node, definition, "字段")
    val type = node.typeBefore(definition)
    return JavaAstMemberDeclaration.Field(ids.next(), span(node), node.modifiersBefore(definition, FIELD_MODIFIERS, "字段"),
      type, node.descendants().filter { it.name == "VariableDeclarator" }.map { declarator(it, type) }.toList())
  }

  /** 构建 instance/static 方法和其受限泛型声明。 */
  private fun method(
    node: LezerSyntaxNode,
    ownerKind: JavaAstTypeDeclarationKind,
  ): JavaAstMemberDeclaration.Method {
    // 泛型方法的 TypeParameters 内也包含 Definition，方法名必须只从声明的直接子节点读取。
    val definition = node.children().firstOrNull { it.name == "Definition" }
      ?: unsupported(node, "方法缺少名称。")
    val modifiers = node.modifiersBefore(definition, METHOD_MODIFIERS, "方法")
    val annotations = methodAnnotations(node, definition)
    rejectReturnDimensions(node)
    node.descendants().firstOrNull { it.name in THROWS_CLAUSES }?.let {
      unsupported(it, "阶段 1 尚不支持 throws 或受检异常。")
    }
    node.descendants().firstOrNull { it.name == "SpreadParameter" }?.let {
      unsupported(it, "阶段 1 尚不支持可变参数。")
    }
    val bodyNode = node.children().firstOrNull { it.name == "Block" }
    if (bodyNode == null && ownerKind == JavaAstTypeDeclarationKind.CLASS &&
      JavaAstModifier.ABSTRACT !in modifiers
    ) {
      unsupported(node, "class 中的非 abstract 方法必须有 block 方法体。")
    }
    if (bodyNode != null && JavaAstModifier.ABSTRACT in modifiers) {
      unsupported(bodyNode, "abstract 方法不能提供 block 方法体。")
    }
    val parameters = node.descendants().filter { it.name == "FormalParameter" }
      .filter { it.nearest("MethodDeclaration") === node }.map(::parameter)
    return JavaAstMemberDeclaration.Method(
      ids.next(),
      span(node),
      modifiers,
      node.children().firstOrNull { it.name == "TypeParameters" }?.let(::typeParameters).orEmpty(),
      node.typeBefore(definition),
      text(definition),
      parameters.toList(),
      bodyNode?.let { block(it) },
      annotations,
    )
  }

  /** 构建构造器，并让其显式 this/super 调用保留为首条专用语句。 */
  private fun constructor(node: LezerSyntaxNode): JavaAstMemberDeclaration.Constructor {
    // 构造器类型参数同样可能包含 Definition，不能让它覆盖构造器自身名称。
    val definition = node.children().firstOrNull { it.name == "Definition" }
      ?: unsupported(node, "构造器缺少名称。")
    rejectAnnotationsBefore(node, definition, "构造器")
    node.descendants().firstOrNull { it.name in THROWS_CLAUSES }?.let {
      unsupported(it, "阶段 1 尚不支持 throws 或受检异常。")
    }
    node.descendants().firstOrNull { it.name == "SpreadParameter" }?.let {
      unsupported(it, "阶段 1 尚不支持可变参数。")
    }
    val body = node.children().firstOrNull { it.name == "ConstructorBody" }
      ?: unsupported(node, "构造器必须有 block 方法体。")
    val parameters = node.descendants().filter { it.name == "FormalParameter" }
      .filter { it.nearest("ConstructorDeclaration") === node }.map(::parameter).toList()
    return JavaAstMemberDeclaration.Constructor(
      ids.next(),
      span(node),
      node.modifiersBefore(definition, CONSTRUCTOR_MODIFIERS, "构造器"),
      node.children().firstOrNull { it.name == "TypeParameters" }?.let(::typeParameters).orEmpty(),
      text(definition),
      parameters,
      block(body, allowConstructorInvocation = true),
    )
  }

  /** 构建普通参数，vararg 由后续阶段开放。 */
  private fun parameter(node: LezerSyntaxNode): JavaAstParameter {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "参数缺少名称。")
    rejectAnnotationsBefore(node, definition, "参数")
    rejectPostNameDimensions(definition, "参数")
    return JavaAstParameter(ids.next(), span(node), node.modifiersBefore(definition, PARAMETER_MODIFIERS, "参数"), node.typeBefore(definition),
      text(definition), false)
  }

  /**
   * 构建 block 内的直接语句。
   *
   * [allowConstructorInvocation] 只在构造器 body 开启，且显式 this/super 调用必须成为首条非 trivia
   * 语句，避免错误地降成普通方法调用后破坏初始化顺序。
   */
  private fun block(
    node: LezerSyntaxNode,
    allowConstructorInvocation: Boolean = false,
  ): JavaAstStatement.Block {
    val statements = mutableListOf<JavaAstStatement>()
    node.children().forEach { child ->
      when {
        child.name in STATEMENT_NODES -> statements += statement(child)
        child.name in CONSTRUCTOR_INVOCATION_NODES -> {
          if (!allowConstructorInvocation || statements.isNotEmpty()) {
            unsupported(child, "显式 this/super 调用只能作为构造器的第一条语句。")
          }
          statements += constructorInvocation(child)
        }
        child.name == "{" || child.name == "}" || child.trivia() -> Unit
        else -> unsupported(child, "阶段 1 不支持该语句，不能忽略其外层控制流语义。")
      }
    }
    return JavaAstStatement.Block(ids.next(), span(node), statements)
  }

  /** 构建阶段 1 基础语句。 */
  private fun statement(node: LezerSyntaxNode): JavaAstStatement = when (node.name) {
    "Block" -> block(node)
    "LocalVariableDeclaration" -> local(node)
    "ExpressionStatement" -> JavaAstStatement.Expression(ids.next(), span(node), expression(node.onlyExpression()))
    "ReturnStatement" -> JavaAstStatement.Return(ids.next(), span(node), node.returnExpression())
    "IfStatement" -> ifStatement(node)
    "WhileStatement" -> whileStatement(node)
    "ForStatement" -> forStatement(node)
    "EmptyStatement" -> JavaAstStatement.Empty(ids.next(), span(node))
    else -> unsupported(node, "阶段 1 不支持该语句。")
  }

  /** 将真实 CST 的 ExplicitConstructorInvocation 映射为初始化顺序专用节点。 */
  private fun constructorInvocation(node: LezerSyntaxNode): JavaAstStatement.ConstructorInvocation {
    node.children().firstOrNull { it.name == "TypeArguments" }?.let {
      unsupported(it, "阶段 1 尚不支持构造器调用的显式类型实参。")
    }
    val target = node.children().firstOrNull { text(it) == "this" || text(it) == "super" }
      ?: unsupported(node, "显式构造器调用缺少 this 或 super。")
    val kind = when (text(target)) {
      "this" -> JavaAstConstructorInvocationKind.THIS
      "super" -> JavaAstConstructorInvocationKind.SUPER
      else -> unsupported(target, "不支持的构造器调用目标。")
    }
    val arguments = node.children().firstOrNull { it.name == "ArgumentList" }
      ?.expressions()
      ?.map(::expression)
      .orEmpty()
    return JavaAstStatement.ConstructorInvocation(ids.next(), span(node), kind, arguments)
  }

  /** 构建 if/else，并仅把直接语句子节点视为两个分支。 */
  private fun ifStatement(node: LezerSyntaxNode): JavaAstStatement.If {
    val condition = node.children().firstOrNull { it.name == "ParenthesizedExpression" }
      ?: unsupported(node, "if 缺少条件。")
    val branches = node.children().filter { it.name in STATEMENT_NODES }
    if (branches.isEmpty() || branches.size > 2) unsupported(node, "if 分支结构无效。")
    return JavaAstStatement.If(
      ids.next(),
      span(node),
      expression(condition),
      statement(branches.first()),
      branches.getOrNull(1)?.let(::statement),
    )
  }

  /** 构建 while；条件保留 ParenthesizedExpression，供后续语义阶段严格校验 boolean。 */
  private fun whileStatement(node: LezerSyntaxNode): JavaAstStatement.While {
    val condition = node.children().firstOrNull { it.name == "ParenthesizedExpression" }
      ?: unsupported(node, "while 缺少条件。")
    val body = node.children().lastOrNull { it.name in STATEMENT_NODES }
      ?: unsupported(node, "while 缺少循环体。")
    return JavaAstStatement.While(ids.next(), span(node), expression(condition), statement(body))
  }

  /** 经典 for 使用 ForSpec 的直接分号 token 分段，绝不通过源码切分。 */
  private fun forStatement(node: LezerSyntaxNode): JavaAstStatement.For {
    val spec = node.children().firstOrNull { it.name == "ForSpec" } ?: node.descendants().firstOrNull { it.name == "ForSpec" }
      ?: unsupported(node, "阶段 1 只支持经典 for。")
    val parts = spec.split(";")
    if (parts.size != 3) unsupported(spec, "经典 for 必须包含三个分段。")
    val body = node.children().lastOrNull { it.name in STATEMENT_NODES } ?: unsupported(node, "for 缺少循环体。")
    return JavaAstStatement.For(ids.next(), span(node), forInitializer(parts[0]),
      parts[1].expressionNodes().singleOrNull()?.let(::expression),
      parts[2].expressionNodes().map(::expression), statement(body))
  }

  /** for 初始化支持局部变量或表达式列表。 */
  private fun forInitializer(nodes: List<LezerSyntaxNode>): JavaAstForInitializer? {
    if (nodes.isEmpty()) return null
    val declaration = nodes.firstOrNull { it.name == "LocalVariableDeclaration" }
    if (declaration != null) {
      val definition = declaration.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(declaration, "for 变量缺少名称。")
      rejectAnnotationsBefore(declaration, definition, "for 局部变量")
      declaration.descendants().firstOrNull { text(it) == "var" }?.let {
        unsupported(it, "阶段 1 仅支持 Java 8，不能使用 var。")
      }
      val type = declaration.typeBefore(definition)
      return JavaAstForInitializer.VariableDeclaration(ids.next(), span(declaration), declaration.modifiersBefore(definition, LOCAL_MODIFIERS, "for 局部变量"),
        type, declaration.descendants().filter { it.name == "VariableDeclarator" }.map { declarator(it, type) }.toList())
    }
    val expressions = nodes.expressionNodes().map(::expression)
    if (expressions.isEmpty()) unsupported(nodes.first(), "for 初始化不包含表达式。")
    return JavaAstForInitializer.Expressions(ids.next(), span(nodes.first(), nodes.last()), expressions)
  }

  /** 构建局部变量。 */
  private fun local(node: LezerSyntaxNode): JavaAstStatement.VariableDeclaration {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "局部变量缺少名称。")
    rejectAnnotationsBefore(node, definition, "局部变量")
    node.descendants().firstOrNull { text(it) == "var" }?.let {
      unsupported(it, "阶段 1 仅支持 Java 8，不能使用 var。")
    }
    val type = node.typeBefore(definition)
    return JavaAstStatement.VariableDeclaration(ids.next(), span(node), node.modifiersBefore(definition, LOCAL_MODIFIERS, "局部变量"), type,
      node.descendants().filter { it.name == "VariableDeclarator" }.map { declarator(it, type) }.toList())
  }

  /**
   * 单个 declarator 的初始化值由 VariableInitializer 或直接 ArrayInitializer 提供。
   *
   * 同一 field/local/for 声明可含多个 declarator，因此后置维度必须在这里按各自 Definition
   * 校验，不能只检查声明中的第一个名称后漏掉 `int first, second[];`。
   */
  private fun declarator(
    node: LezerSyntaxNode,
    declaredType: JavaAstTypeReference,
  ): JavaAstVariableDeclarator {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "变量缺少名称。")
    rejectPostNameDimensions(definition, "变量")
    val initializerNode = node.children().firstOrNull {
      it.name == "VariableInitializer" || it.name == "ArrayInitializer"
    }
    val initializer = initializerNode?.let { variableInitializer(it, declaredType) }
      ?: node.expressions().singleOrNull()
        ?.let(::expression)
    return JavaAstVariableDeclarator(ids.next(), span(node), text(definition), initializer)
  }

  /** 结构化映射阶段 0 所需的表达式节点。 */
  private fun expression(original: LezerSyntaxNode): JavaAstExpression {
    val node = original.unwrap()
    return when (node.name) {
      "Identifier", "ScopedIdentifier" -> JavaAstExpression.Name(ids.next(), span(node), text(node))
      "this" -> JavaAstExpression.This(ids.next(), span(node))
      "super" -> JavaAstExpression.Super(ids.next(), span(node))
      "IntegerLiteral" -> literal(node, JavaAstLiteralKind.INTEGER)
      "FloatingPointLiteral" -> literal(node, JavaAstLiteralKind.FLOATING_POINT)
      "StringLiteral" -> literal(node, JavaAstLiteralKind.STRING)
      "CharacterLiteral" -> literal(node, JavaAstLiteralKind.CHARACTER)
      "BooleanLiteral" -> literal(node, JavaAstLiteralKind.BOOLEAN)
      "null" -> literal(node, JavaAstLiteralKind.NULL)
      "ParenthesizedExpression" -> JavaAstExpression.Parenthesized(ids.next(), span(node), expression(node.onlyExpression()))
      "BinaryExpression" -> binary(node)
      "AssignmentExpression" -> assignment(node)
      "UnaryExpression", "PostfixExpression", "UpdateExpression" -> unary(node)
      "MethodInvocation" -> invocation(node)
      "ObjectCreationExpression" -> newObject(node)
      "ArrayCreationExpression" -> newArray(node)
      "ArrayAccess" -> arrayAccess(node)
      "FieldAccess" -> fieldAccess(node)
      else -> unsupported(node, "Stage1 不支持该表达式。")
    }
  }

  /** 二元表达式完全由两个 expression child 和直接 token 组成。 */
  private fun binary(node: LezerSyntaxNode): JavaAstExpression.Binary {
    val operands = node.expressions()
    val token = node.children().map(::text).firstOrNull { it in BINARY } ?: unsupported(node, "二元操作符不受支持。")
    if (operands.size != 2) unsupported(node, "二元表达式操作数数量错误。")
    return JavaAstExpression.Binary(ids.next(), span(node), expression(operands[0]), BINARY.getValue(token), expression(operands[1]))
  }

  /** 赋值表达式支持 = 与阶段 0 循环所需的复合赋值。 */
  private fun assignment(node: LezerSyntaxNode): JavaAstExpression.Assignment {
    val operands = node.expressions()
    val token = node.children().map(::text).firstOrNull { it in ASSIGNMENT } ?: unsupported(node, "赋值操作符不受支持。")
    if (operands.size != 2) unsupported(node, "赋值表达式操作数数量错误。")
    return JavaAstExpression.Assignment(ids.next(), span(node), expression(operands[0]), ASSIGNMENT.getValue(token), expression(operands[1]))
  }

  /** 一元节点的 token 形态决定目前的前后缀递增语义。 */
  private fun unary(node: LezerSyntaxNode): JavaAstExpression.Unary {
    val operand = node.expressions().singleOrNull() ?: unsupported(node, "一元表达式操作数数量错误。")
    val tokenNode = node.children().firstOrNull { text(it) in UNARY } ?: unsupported(node, "一元操作符不受支持。")
    val token = text(tokenNode)
    val operator = when (token) {
      "++" -> if (tokenNode.from < operand.from) JavaAstUnaryOperator.PRE_INCREMENT else JavaAstUnaryOperator.POST_INCREMENT
      "--" -> if (tokenNode.from < operand.from) JavaAstUnaryOperator.PRE_DECREMENT else JavaAstUnaryOperator.POST_DECREMENT
      else -> UNARY.getValue(token)
    }
    return JavaAstExpression.Unary(ids.next(), span(node), operator, expression(operand))
  }

  /** 构建方法调用和其 CST ArgumentList。 */
  private fun invocation(node: LezerSyntaxNode): JavaAstExpression.MethodInvocation {
    val children = node.children()
    // 链式调用的 receiver 本身也是 MethodInvocation；只读取当前节点直接成员，不能误取内层名称和参数。
    val name = children.firstOrNull { it.name == "MethodName" }
      ?: children.lastOrNull { it.name == "Identifier" }
      ?: unsupported(node, "调用缺少方法名。")
    val arguments = children.firstOrNull { it.name == "ArgumentList" }
      ?.expressions()
      ?.map(::expression)
      .orEmpty()
    val receiver = node.expressions().firstOrNull { it.to <= name.from }?.let(::expression)
    val typeArguments = children.firstOrNull { it.name == "TypeArguments" }?.typeArguments().orEmpty()
    return JavaAstExpression.MethodInvocation(ids.next(), span(node), receiver, text(name), typeArguments, arguments)
  }

  /** 阶段 0 仅支持普通对象创建，不支持数组创建与匿名类型。 */
  private fun newObject(node: LezerSyntaxNode): JavaAstExpression.NewObject {
    node.children().firstOrNull { it.name == "ClassBody" }?.let {
      unsupported(it, "阶段 1 尚不支持匿名类。")
    }
    val type = node.children().firstOrNull { it.name in TYPE_REFERENCE_NODES + setOf("GenericType") }
      ?: unsupported(node, "对象创建缺少类型。")
    val arguments = node.descendants().firstOrNull { it.name == "ArgumentList" }?.expressions()?.map(::expression).orEmpty()
    return JavaAstExpression.NewObject(ids.next(), span(node), typeReference(type), arguments)
  }

  /**
   * 将真实 CST 的数组创建节点映射为组件类型、逐维大小和可选花括号初始化器。
   *
   * `Dimension` 的大小是否为空必须保留，不能将 `new int[2][]` 误写成两个同类已定长维度；
   * 一维执行限制由后续语义阶段统一校验。
   */
  private fun newArray(node: LezerSyntaxNode): JavaAstExpression.NewArray {
    val component = node.children().firstOrNull {
      it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS - setOf("ArrayType")
    } ?: unsupported(node, "数组创建缺少组件类型。")
    val dimensions = node.children().filter { it.name == "Dimension" }.map(::arrayDimension)
    if (dimensions.isEmpty()) unsupported(node, "数组创建缺少维度。")
    val initializer = node.children().firstOrNull { it.name == "ArrayInitializer" }?.let(::arrayInitializer)
    return JavaAstExpression.NewArray(ids.next(), span(node), typeReference(component), dimensions, initializer)
  }

  /** 将数组访问节点的两个直接 expression child（数组和下标）完整保留。 */
  private fun arrayAccess(node: LezerSyntaxNode): JavaAstExpression.ArrayAccess {
    val operands = node.expressions()
    if (operands.size != 2) unsupported(node, "数组访问必须包含数组与下标两个表达式。")
    return JavaAstExpression.ArrayAccess(ids.next(), span(node), expression(operands[0]), expression(operands[1]))
  }

  /** 映射数组创建中的单个 `Dimension`，空方括号用 null size 表示。 */
  private fun arrayDimension(node: LezerSyntaxNode): JavaAstArrayDimension {
    val sizes = node.expressions()
    if (sizes.size > 1) unsupported(node, "数组维度包含多个大小表达式。")
    return JavaAstArrayDimension(ids.next(), span(node), sizes.singleOrNull()?.let(::expression))
  }

  /**
   * 递归保留花括号初始化器，元素只允许普通 expression 或嵌套 ArrayInitializer。
   *
   * 该节点不会进入通用 [expression]，从而维持 Java 中花括号仅能作为数组初始化器的边界。
   */
  private fun arrayInitializer(node: LezerSyntaxNode): JavaAstArrayInitializer {
    val elements = node.children().mapNotNull { child ->
      when {
        child.name == "ArrayInitializer" -> JavaAstArrayInitializerElement.Nested(arrayInitializer(child))
        child.name in EXPRESSION_NODES -> JavaAstArrayInitializerElement.Expression(expression(child))
        child.trivia() || text(child) in ARRAY_INITIALIZER_TOKENS -> null
        else -> unsupported(child, "数组初始化器包含不支持的元素。")
      }
    }
    return JavaAstArrayInitializer(ids.next(), span(node), elements)
  }

  /**
   * 读取变量初始化器；`int[] values = { ... }` 没有 CST 的 ArrayCreationExpression，故以
   * 声明数组类型规范化为虚拟 NewArray，后续阶段可与显式 `new int[] { ... }` 走同一规则。
   */
  private fun variableInitializer(
    node: LezerSyntaxNode,
    declaredType: JavaAstTypeReference,
  ): JavaAstExpression {
    // @lezer/java 1.1.3 会将 `{...}` 直接挂在 VariableDeclarator 下，不能假定存在 wrapper。
    val arrayInitializer = if (node.name == "ArrayInitializer") node else {
      node.children().firstOrNull { it.name == "ArrayInitializer" }
    }
    if (arrayInitializer == null) return expression(node.onlyExpression())
    val type = declaredType as? JavaAstTypeReference.Array
      ?: unsupported(arrayInitializer, "花括号初始化器只能用于数组声明。")
    val dimensions = List(type.dimensions) {
      JavaAstArrayDimension(ids.next(), span(arrayInitializer), null)
    }
    return JavaAstExpression.NewArray(
      ids.next(),
      span(arrayInitializer),
      type.componentType,
      dimensions,
      arrayInitializer(arrayInitializer),
    )
  }

  /** 字段访问保留 receiver 与字段名称的 CST 关系。 */
  private fun fieldAccess(node: LezerSyntaxNode): JavaAstExpression.FieldAccess {
    val receiver = node.expressions().firstOrNull() ?: unsupported(node, "字段访问缺少 receiver。")
    val name = node.descendants().lastOrNull { it.name == "Identifier" || it.name == "FieldName" } ?: unsupported(node, "字段访问缺少名称。")
    return JavaAstExpression.FieldAccess(ids.next(), span(node), expression(receiver), text(name))
  }

  /** 保留 literal token 原文，数值范围与转义交由语义阶段。 */
  private fun literal(node: LezerSyntaxNode, kind: JavaAstLiteralKind) = JavaAstExpression.Literal(ids.next(), span(node), kind, text(node))

  /**
   * 映射阶段 1 的 Java 8 类型引用。
   *
   * 参数化类型、通配符、菱形和数组都必须保留在 AST，不能退化为裸类型。
   */
  private fun typeReference(node: LezerSyntaxNode): JavaAstTypeReference = when (node.name) {
    "PrimitiveType" -> JavaAstTypeReference.Primitive(ids.next(), span(node), primitive(text(node)))
    "TypeName", "ScopedTypeName" -> JavaAstTypeReference.Named(ids.next(), span(node), text(node), emptyList())
    "GenericType" -> {
      val name = node.children().firstOrNull { it.name == "TypeName" || it.name == "ScopedTypeName" }
        ?: unsupported(node, "参数化类型缺少原始类型名。")
      val arguments = node.children().firstOrNull { it.name == "TypeArguments" }
        ?: unsupported(node, "参数化类型缺少类型实参。")
      JavaAstTypeReference.Named(
        ids.next(),
        span(node),
        text(name),
        arguments.typeArguments(),
        arguments.isDiamond(),
      )
    }
    "Wildcard" -> wildcard(node)
    "ArrayType" -> arrayType(node)
    "void" -> JavaAstTypeReference.Void(ids.next(), span(node))
    else -> unsupported(node, "阶段 1 不支持该类型。")
  }

  /** 将前置方括号的 ArrayType 映射为已有的组件类型加固定维度数。 */
  private fun arrayType(node: LezerSyntaxNode): JavaAstTypeReference.Array {
    val component = node.children().firstOrNull {
      it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS && it.name != "ArrayType"
    } ?: unsupported(node, "数组类型缺少组件类型。")
    val dimensions = node.children().count { it.name == "Dimension" }
    if (dimensions == 0) unsupported(node, "数组类型缺少维度。")
    return JavaAstTypeReference.Array(ids.next(), span(node), typeReference(component), dimensions)
  }

  /** 从 declaration 的类型 CST 子节点构建类型，并排除泛型参数与注解内部类型。 */
  private fun LezerSyntaxNode.typeBefore(definition: LezerSyntaxNode): JavaAstTypeReference {
    val type = descendants()
      .filter { it.to <= definition.from && it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS }
      .filterNot { it.hasAncestor("Annotation") || it.hasAncestor("MarkerAnnotation") || it.hasAncestor("TypeParameters") }
      .filterNot(::isNestedTypeReference)
      .minWithOrNull(compareBy<LezerSyntaxNode> { it.from }.thenByDescending { it.to })
      ?: unsupported(this, "声明缺少支持的类型。")
    return typeReference(type)
  }

  /**
   * 拒绝变量名后的 `[]`，避免 `int value[]` 被现有共享声明类型静默降成 int。
   *
   * 只检查 Definition 所在声明节点的直接后继，且不深入 initializer：数组创建、索引表达式中的
   * Dimension 也在名称之后，若递归扫描会把合法的数组创建初始化器误判为后置维度。
   * LezerSyntaxNode 是 cursor wrapper，不能依赖 wrapper 引用相同；这里以节点名称和源码区间
   * 在直接 child 中定位 Definition。
   * 前置 `int[] value` 的 Dimension 位于 ArrayType 内，仍由 [arrayType] 正常保留。
   */
  private fun rejectPostNameDimensions(definition: LezerSyntaxNode, declarationName: String) {
    val declaration = definition.parent ?: unsupported(definition, "${declarationName}缺少声明容器。")
    val suffixDimension = declaration.children()
      .dropWhile { it.name != definition.name || it.from != definition.from || it.to != definition.to }
      .drop(1)
      .takeWhile { child ->
        text(child) != "=" && child.name != "AssignOp" && child.name != "VariableInitializer" &&
          child.name !in EXPRESSION_NODES
      }
      .firstOrNull { it.name == "Dimension" }
    suffixDimension?.let {
      unsupported(it, "阶段 2A 仅支持前置数组维度，${declarationName}不能在名称后声明 []。")
    }
  }

  /** 拒绝方法 FormalParameters 后的返回类型后置维度 `int value()[]`。 */
  private fun rejectReturnDimensions(node: LezerSyntaxNode) {
    node.children().firstOrNull { it.name == "Dimension" }?.let {
      unsupported(it, "阶段 2A 仅支持前置数组维度，方法返回类型不能在参数列表后声明 []。")
    }
  }

  /** 读取 GenericType 的 TypeArguments，直接 child 只保留一个类型实参层级。 */
  private fun LezerSyntaxNode.typeArguments(): List<JavaAstTypeReference> = children()
    .filter { it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS + setOf("Wildcard") }
    .map(::typeReference)

  /** `<>` 没有任何类型 child；它与未参数化类型都使用空列表，故必须额外保留标记。 */
  private fun LezerSyntaxNode.isDiamond(): Boolean =
    children().none { it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS + setOf("Wildcard") }

  /** 映射 `?`、`? extends T` 与 `? super T`，并保持边界方向。 */
  private fun wildcard(node: LezerSyntaxNode): JavaAstTypeReference.Wildcard {
    // @lezer/java 1.1.3 将 super/extends 与边界类型直接放在 Wildcard 下，不存在额外 wrapper。
    val children = node.children()
    val type = children.firstOrNull { it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS }
      ?: return JavaAstTypeReference.Wildcard(ids.next(), span(node))
    return when {
      children.any { text(it) == "extends" } ->
        JavaAstTypeReference.Wildcard(ids.next(), span(node), upperBound = typeReference(type))
      children.any { text(it) == "super" } ->
        JavaAstTypeReference.Wildcard(ids.next(), span(node), lowerBound = typeReference(type))
      else -> unsupported(node, "通配符边界缺少 extends 或 super。")
    }
  }

  /** 读取一个 clause 的唯一类型，避免把 extends/implements 意外映射成空继承关系。 */
  private fun LezerSyntaxNode.onlyTypeReference(): JavaAstTypeReference {
    val types = typeReferences()
    if (types.size != 1) unsupported(this, "继承 clause 必须包含唯一父类型。")
    return types.single()
  }

  /** 从 Superclass、SuperInterfaces 或 ExtendsInterfaces 的 InterfaceTypeList 中读取直接类型。 */
  private fun LezerSyntaxNode.typeReferences(): List<JavaAstTypeReference> {
    val list = children().firstOrNull { it.name == "InterfaceTypeList" } ?: this
    return list.children()
      .filter { it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS }
      .map(::typeReference)
  }

  /** 泛型外壳内的 TypeName/TypeArguments 不应与外层 GenericType 重复建模。 */
  private fun isNestedTypeReference(node: LezerSyntaxNode): Boolean {
    var current = node.parent
    while (current != null) {
      if (current.name in TYPE_REFERENCE_WRAPPERS) return true
      current = current.parent
    }
    return false
  }

  /** 构建 `<T extends A & B>` 形式的类型参数；type bound 只能包含阶段 1 已开放类型。 */
  private fun typeParameters(node: LezerSyntaxNode): List<JavaAstTypeParameter> {
    return node.children().filter { it.name == "TypeParameter" }.map { parameter ->
      rejectAnnotationsBefore(parameter, parameter.definition(), "类型参数")
      val definition = parameter.definition()
      val bounds = parameter.children().firstOrNull { it.name == "TypeBound" }
        ?.children()
        ?.filter { it.name in TYPE_REFERENCE_NODES + TYPE_REFERENCE_WRAPPERS }
        ?.map(::typeReference)
        .orEmpty()
      JavaAstTypeParameter(
        ids.next(),
        span(parameter),
        text(definition),
        bounds,
      )
    }
  }

  /** TypeParameter 与其他声明共享 Definition 节点，集中处理缺失诊断。 */
  private fun LezerSyntaxNode.definition(): LezerSyntaxNode =
    children().firstOrNull { it.name == "Definition" }
      ?: unsupported(this, "声明缺少名称。")

  /**
   * 收集并校验声明修饰符。
   *
   * 不能用 Set 静默抹掉重复 token，也不能让未映射的 Java 8 modifier 在语义阶段之前消失；
   * 允许集合由声明位置传入，组合合法性随后统一检查。
   */
  private fun LezerSyntaxNode.modifiersBefore(
    definition: LezerSyntaxNode,
    allowed: Set<JavaAstModifier>,
    declarationName: String,
  ): Set<JavaAstModifier> {
    // Lezer 的 Modifiers 容器文本与内部单个 modifier token 相同；只读取叶子 token，避免同一
    // 个 static/public/final 被容器和 child 重复计数。
    val tokens = descendants()
      .filter { it.firstChild == null && it.to <= definition.from && text(it) in JAVA_MODIFIER_TOKENS }
      .map(::text)
      .toList()
    val unsupported = tokens.firstOrNull { it !in MODIFIERS }
    if (unsupported != null) unsupported(definition, declarationName + " 不支持修饰符 " + unsupported + "。")
    val mapped = tokens.map(MODIFIERS::getValue)
    val duplicate = mapped.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
    if (duplicate != null) unsupported(definition, declarationName + " 不能重复修饰符 " + duplicate.key.name.lowercase() + "。")
    val disallowed = mapped.firstOrNull { it !in allowed }
    if (disallowed != null) unsupported(definition, declarationName + " 不允许修饰符 " + disallowed.name.lowercase() + "。")
    if (JavaAstModifier.PUBLIC in mapped && JavaAstModifier.PRIVATE in mapped ||
      JavaAstModifier.PUBLIC in mapped && JavaAstModifier.PROTECTED in mapped ||
      JavaAstModifier.PRIVATE in mapped && JavaAstModifier.PROTECTED in mapped ||
      JavaAstModifier.ABSTRACT in mapped && JavaAstModifier.FINAL in mapped
    ) {
      unsupported(definition, declarationName + " 包含互斥修饰符。")
    }
    return mapped.toSet()
  }

  /** 非方法声明目前不接受注解，防止未知 metadata 被静默忽略。 */
  private fun rejectAnnotationsBefore(
    node: LezerSyntaxNode,
    definition: LezerSyntaxNode,
    declarationName: String,
  ) {
    node.descendants().firstOrNull {
      it.to <= definition.from && it.name in ANNOTATION_NODES
    }?.let { unsupported(it, "阶段 1 的 " + declarationName + " 不支持注解。") }
  }

  /** 仅接受无参数的精确 `@Override`，其他 Java annotation 仍属于后续阶段。 */
  private fun methodAnnotations(
    node: LezerSyntaxNode,
    definition: LezerSyntaxNode,
  ): List<JavaAstAnnotation> {
    return node.descendants().filter {
      it.to <= definition.from && it.name in ANNOTATION_NODES
    }.map { annotation ->
      if (annotation.name != "MarkerAnnotation" || text(annotation) != "@Override") {
        unsupported(annotation, "阶段 1 的方法只支持无参数的 @Override。")
      }
      JavaAstAnnotation(ids.next(), span(annotation), "Override")
    }.toList()
  }

  /** primitive token 到公共 AST enum 的映射。 */
  private fun primitive(token: String) = when (token) {
    "boolean" -> JavaAstPrimitiveType.BOOLEAN; "byte" -> JavaAstPrimitiveType.BYTE; "short" -> JavaAstPrimitiveType.SHORT
    "int" -> JavaAstPrimitiveType.INT; "long" -> JavaAstPrimitiveType.LONG; "char" -> JavaAstPrimitiveType.CHAR
    "float" -> JavaAstPrimitiveType.FLOAT; "double" -> JavaAstPrimitiveType.DOUBLE
    else -> error("Unexpected primitive token")
  }

  /**
   * 依据 ForSpec 的 CST 分号节点分段，绝不扫描源码字符。
   *
   * Lezer 会把声明式 initializer 的第一个分号收进 LocalVariableDeclaration，而表达式 initializer
   * 的分号是 ForSpec 的直接 child，因此两种结构需要统一转换为三个分段。
   */
  private fun LezerSyntaxNode.split(token: String): List<List<LezerSyntaxNode>> {
    val result = mutableListOf(mutableListOf<LezerSyntaxNode>())
    children().forEach { child ->
      result.last().add(child)
      if (text(child) == token || child.name == "LocalVariableDeclaration" && child.children().any { text(it) == token }) {
        if (text(child) == token) result.last().removeAt(result.last().lastIndex)
        result.add(mutableListOf())
      }
    }
    return result
  }

  /** 当前节点下的直接表达式 child。 */
  private fun LezerSyntaxNode.expressions(): List<LezerSyntaxNode> = children().flatMap { child ->
    if (child.name in EXPRESSION_NODES) listOf(child) else if (child.name == "Expression") child.children().filter { it.name in EXPRESSION_NODES } else emptyList()
  }

  /** 同时兼容“节点自身是表达式”和“容器节点直接包含表达式”两种 CST 位置。 */
  private fun List<LezerSyntaxNode>.expressionNodes(): List<LezerSyntaxNode> = flatMap { node ->
    if (node.name in EXPRESSION_NODES) listOf(node) else node.expressions()
  }

  /** 取得唯一 expression child，否则明确报 unsupported。 */
  private fun LezerSyntaxNode.onlyExpression() = expressions().singleOrNull() ?: unsupported(this, "无法唯一确定表达式。")

  /**
   * 返回语句只有没有非 token child 时才表示 `return;`。
   *
   * 这样未映射的新 expression 不会因 expressions() 过滤而被静默擦除成无返回值语句。
   */
  private fun LezerSyntaxNode.returnExpression(): JavaAstExpression? {
    val valueNodes = children().filterNot { child ->
      child.trivia() || text(child) == "return" || text(child) == ";"
    }
    if (valueNodes.isEmpty()) return null
    if (valueNodes.size != 1) unsupported(this, "return 包含多个表达式。")
    return expression(valueNodes.single())
  }

  /** 仅解开不承载语义的单 child expression wrapper。 */
  private fun LezerSyntaxNode.unwrap(): LezerSyntaxNode {
    var current = this
    while (current.name in WRAPPERS) {
      val child = current.expressions().singleOrNull() ?: break
      current = child
    }
    return current
  }

  /** 枚举所有直接 CST child。 */
  private fun LezerSyntaxNode.children(): List<LezerSyntaxNode> {
    val result = mutableListOf<LezerSyntaxNode>(); var child = firstChild
    while (child != null) { result += child; child = child.nextSibling }
    return result
  }

  /** 深度优先枚举 CST descendants。 */
  private fun LezerSyntaxNode.descendants(): Sequence<LezerSyntaxNode> = sequence {
    children().forEach { child -> yield(child); yieldAll(child.descendants()) }
  }

  /** 检查祖先，避免 annotation 被误作声明类型。 */
  private fun LezerSyntaxNode.hasAncestor(name: String): Boolean {
    var current = parent; while (current != null) { if (current.name == name) return true; current = current.parent }; return false
  }

  /** 返回最近祖先，确保参数属于当前方法。 */
  private fun LezerSyntaxNode.nearest(name: String): LezerSyntaxNode? {
    var current = parent; while (current != null) { if (current.name == name) return current; current = current.parent }; return null
  }

  /** 读取 package declaration 的完整限定名。 */
  private fun LezerSyntaxNode.qualifiedName(): String {
    // package a.b 的直接 child 已经携带完整 a.b；深入 descendants 反而只会得到末级 b。
    val name = children().firstOrNull { it.name in NAME_NODES }
      ?: unsupported(this, "限定名缺失。")
    return text(name)
  }

  /** 忽略 root 的注释节点。 */
  private fun LezerSyntaxNode.trivia() = name == "LineComment" || name == "BlockComment"
  private fun span(node: LezerSyntaxNode) = JavaSourceSpan(file.id, node.from, node.to)
  private fun span(first: LezerSyntaxNode, last: LezerSyntaxNode) = JavaSourceSpan(file.id, first.from, last.to)
  private fun text(node: LezerSyntaxNode) = file.source.substring(node.from, node.to)
  private fun unsupported(node: LezerSyntaxNode, message: String): Nothing = throw JavaFrontendIssue(node, "java.frontend.unsupported", message)
}

/** adapter 私有异常，外层必须转为 JavaCompilerDiagnostic。 */
private class JavaFrontendIssue(val node: LezerSyntaxNode, val code: String, override val message: String) : RuntimeException(message)

private val TYPE_NODES = setOf("ClassDeclaration", "InterfaceDeclaration", "EnumDeclaration")
private val MEMBER_NODES = setOf("FieldDeclaration", "MethodDeclaration", "ConstructorDeclaration")
private val STATEMENT_NODES = setOf("Block", "LocalVariableDeclaration", "ExpressionStatement", "ReturnStatement", "IfStatement", "WhileStatement", "ForStatement", "EmptyStatement")
private val TYPE_REFERENCE_NODES = setOf("PrimitiveType", "TypeName", "ScopedTypeName", "void", "ArrayType")
private val TYPE_REFERENCE_WRAPPERS = setOf("GenericType")
private val INTERFACE_CLAUSES = setOf("SuperInterfaces", "ExtendsInterfaces")
private val CONSTRUCTOR_INVOCATION_NODES = setOf("ExplicitConstructorInvocation")
private val THROWS_CLAUSES = setOf("Throws")
private val ANNOTATION_NODES = setOf("MarkerAnnotation", "Annotation")
private val NAME_NODES = setOf("QualifiedName", "ScopedIdentifier", "TypeName", "Identifier")
private val EXPRESSION_NODES = setOf("Expression", "BinaryExpression", "AssignmentExpression", "UnaryExpression", "PostfixExpression", "UpdateExpression", "MethodInvocation", "ObjectCreationExpression", "ArrayCreationExpression", "ArrayAccess", "FieldAccess", "ParenthesizedExpression", "Identifier", "ScopedIdentifier", "this", "super", "IntegerLiteral", "FloatingPointLiteral", "StringLiteral", "CharacterLiteral", "BooleanLiteral", "null")
private val ARRAY_INITIALIZER_TOKENS = setOf("{", "}", ",")
private val WRAPPERS = setOf("Expression", "ConditionalExpression", "ConditionalOrExpression", "ConditionalAndExpression")
private val UNSUPPORTED_NODES = setOf("RecordDeclaration", "ModuleDeclaration", "TextBlock", "SwitchExpression", "YieldStatement")
private val MODIFIERS = mapOf("public" to JavaAstModifier.PUBLIC, "protected" to JavaAstModifier.PROTECTED, "private" to JavaAstModifier.PRIVATE, "abstract" to JavaAstModifier.ABSTRACT, "static" to JavaAstModifier.STATIC, "final" to JavaAstModifier.FINAL)
private val JAVA_MODIFIER_TOKENS = MODIFIERS.keys + setOf("strictfp", "default", "synchronized", "native", "transient", "volatile")
private val TYPE_MODIFIERS = setOf(JavaAstModifier.PUBLIC, JavaAstModifier.ABSTRACT, JavaAstModifier.FINAL)
private val FIELD_MODIFIERS = setOf(JavaAstModifier.PUBLIC, JavaAstModifier.PROTECTED, JavaAstModifier.PRIVATE, JavaAstModifier.STATIC, JavaAstModifier.FINAL)
private val METHOD_MODIFIERS = setOf(JavaAstModifier.PUBLIC, JavaAstModifier.PROTECTED, JavaAstModifier.PRIVATE, JavaAstModifier.STATIC, JavaAstModifier.FINAL, JavaAstModifier.ABSTRACT)
private val CONSTRUCTOR_MODIFIERS = setOf(JavaAstModifier.PUBLIC, JavaAstModifier.PROTECTED, JavaAstModifier.PRIVATE)
private val PARAMETER_MODIFIERS = setOf(JavaAstModifier.FINAL)
private val LOCAL_MODIFIERS = setOf(JavaAstModifier.FINAL)
private val BINARY = mapOf("*" to JavaAstBinaryOperator.MULTIPLY, "/" to JavaAstBinaryOperator.DIVIDE, "%" to JavaAstBinaryOperator.REMAINDER, "+" to JavaAstBinaryOperator.ADD, "-" to JavaAstBinaryOperator.SUBTRACT, "<" to JavaAstBinaryOperator.LESS_THAN, "<=" to JavaAstBinaryOperator.LESS_THAN_OR_EQUAL, ">" to JavaAstBinaryOperator.GREATER_THAN, ">=" to JavaAstBinaryOperator.GREATER_THAN_OR_EQUAL, "==" to JavaAstBinaryOperator.EQUAL, "!=" to JavaAstBinaryOperator.NOT_EQUAL, "&&" to JavaAstBinaryOperator.LOGICAL_AND, "||" to JavaAstBinaryOperator.LOGICAL_OR)
private val ASSIGNMENT = mapOf("=" to JavaAstAssignmentOperator.ASSIGN, "+=" to JavaAstAssignmentOperator.ADD_ASSIGN, "-=" to JavaAstAssignmentOperator.SUBTRACT_ASSIGN, "*=" to JavaAstAssignmentOperator.MULTIPLY_ASSIGN, "/=" to JavaAstAssignmentOperator.DIVIDE_ASSIGN, "%=" to JavaAstAssignmentOperator.REMAINDER_ASSIGN)
private val UNARY = mapOf("+" to JavaAstUnaryOperator.POSITIVE, "-" to JavaAstUnaryOperator.NEGATIVE, "!" to JavaAstUnaryOperator.LOGICAL_NOT, "~" to JavaAstUnaryOperator.BITWISE_NOT, "++" to JavaAstUnaryOperator.POST_INCREMENT, "--" to JavaAstUnaryOperator.POST_DECREMENT)

/** 在进入 adapter 前查找 Lezer 错误恢复节点；skip rule 节点（例如注释）不能视为错误。 */
private fun LezerSyntaxNode.firstRecoveryNode(): LezerSyntaxNode? {
  if (type.isError) return this
  var child = firstChild
  while (child != null) {
    child.firstRecoveryNode()?.let { return it }
    child = child.nextSibling
  }
  return null
}
