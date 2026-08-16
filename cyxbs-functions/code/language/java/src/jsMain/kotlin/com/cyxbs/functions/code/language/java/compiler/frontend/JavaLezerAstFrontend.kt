package com.cyxbs.functions.code.language.java.compiler.frontend

import com.cyxbs.functions.code.language.java.parser
import com.cyxbs.functions.code.language.java.compiler.JavaAstFrontend
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstAssignmentOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstCompilationUnit
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
 * 从 @lezer/java 的 CST 严格构建阶段 0 AST。
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
    if (types.isEmpty()) unsupported(root, "阶段 0 至少需要一个顶层 class。")
    if (root.children().any { it.name !in TYPE_NODES + setOf("PackageDeclaration", "ImportDeclaration", ";") && !it.trivia() }) {
      unsupported(root, "阶段 0 不支持该顶层语法。")
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

  /** 阶段 0 只开放 class 和其中的 static 方法、字段。 */
  private fun type(node: LezerSyntaxNode): JavaAstTypeDeclaration {
    if (node.name != "ClassDeclaration") unsupported(node, "阶段 0 仅支持 class。")
    val definition = node.children().firstOrNull { it.name == "Definition" } ?: unsupported(node, "class 缺少名称。")
    val body = node.children().firstOrNull { it.name == "ClassBody" } ?: unsupported(node, "class 缺少主体。")
    node.children().firstOrNull { it.name in UNSUPPORTED_CLASS_CLAUSES }?.let {
      unsupported(it, "阶段 0 尚不支持泛型 class、继承或接口实现。")
    }
    node.descendants().firstOrNull { it.name in UNSUPPORTED_NODES }?.let {
      unsupported(it, "阶段 0 不支持 Java 8 之外的语法。")
    }
    val members = body.children().mapNotNull { child ->
      when {
        child.name in MEMBER_NODES -> member(child)
        child.name == "{" || child.name == "}" || child.name == ";" || child.trivia() -> null
        else -> unsupported(child, "阶段 0 不支持嵌套类型、初始化块或该 class 成员。")
      }
    }
    return JavaAstTypeDeclaration(ids.next(), span(node), JavaAstTypeDeclarationKind.CLASS,
      node.modifiersBefore(definition), text(definition), emptyList(), null, emptyList(),
      members)
  }

  /** 构建字段或静态方法。 */
  private fun member(node: LezerSyntaxNode): JavaAstMemberDeclaration = when (node.name) {
    "FieldDeclaration" -> field(node)
    "MethodDeclaration" -> method(node)
    else -> unsupported(node, "阶段 0 不支持构造器或嵌套类型。")
  }

  /** 字段按单个 declarator 保留其初始化表达式。 */
  private fun field(node: LezerSyntaxNode): JavaAstMemberDeclaration.Field {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "字段缺少名称。")
    return JavaAstMemberDeclaration.Field(ids.next(), span(node), node.modifiersBefore(definition),
      node.typeBefore(definition), node.descendants().filter { it.name == "VariableDeclarator" }.map(::declarator).toList())
  }

  /** 阶段 0 方法必须是带 block body 的 static 非泛型方法。 */
  private fun method(node: LezerSyntaxNode): JavaAstMemberDeclaration.Method {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "方法缺少名称。")
    val modifiers = node.modifiersBefore(definition)
    if (JavaAstModifier.STATIC !in modifiers) unsupported(node, "阶段 0 仅支持 static 方法。")
    if (node.descendants().any { it.name == "TypeParameters" }) unsupported(node, "阶段 0 不支持泛型方法。")
    node.descendants().firstOrNull { it.name == "SpreadParameter" }?.let {
      unsupported(it, "阶段 0 尚不支持可变参数。")
    }
    val body = node.children().firstOrNull { it.name == "Block" } ?: node.descendants().firstOrNull { it.name == "Block" }
      ?: unsupported(node, "阶段 0 方法必须有 block 方法体。")
    val parameters = node.descendants().filter { it.name == "FormalParameter" }
      .filter { it.nearest("MethodDeclaration") === node }.map(::parameter)
    return JavaAstMemberDeclaration.Method(ids.next(), span(node), modifiers, emptyList(), node.typeBefore(definition),
      text(definition), parameters.toList(), block(body))
  }

  /** 构建普通参数，vararg 由后续阶段开放。 */
  private fun parameter(node: LezerSyntaxNode): JavaAstParameter {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "参数缺少名称。")
    return JavaAstParameter(ids.next(), span(node), node.modifiersBefore(definition), node.typeBefore(definition),
      text(definition), false)
  }

  /** 构建 block 内的语句，命中语句后不继续穿透。 */
  private fun block(node: LezerSyntaxNode): JavaAstStatement.Block {
    val statements = node.children().mapNotNull { child ->
      when {
        child.name in STATEMENT_NODES -> statement(child)
        child.name == "{" || child.name == "}" || child.trivia() -> null
        else -> unsupported(child, "阶段 0 不支持该语句，不能忽略其外层控制流语义。")
      }
    }
    return JavaAstStatement.Block(ids.next(), span(node), statements)
  }

  /** 构建阶段 0 语句。 */
  private fun statement(node: LezerSyntaxNode): JavaAstStatement = when (node.name) {
    "Block" -> block(node)
    "LocalVariableDeclaration" -> local(node)
    "ExpressionStatement" -> JavaAstStatement.Expression(ids.next(), span(node), expression(node.onlyExpression()))
    "ReturnStatement" -> JavaAstStatement.Return(ids.next(), span(node), node.expressions().singleOrNull()?.let(::expression))
    "IfStatement" -> ifStatement(node)
    "WhileStatement" -> whileStatement(node)
    "ForStatement" -> forStatement(node)
    "EmptyStatement" -> JavaAstStatement.Empty(ids.next(), span(node))
    else -> unsupported(node, "阶段 0 不支持该语句。")
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
      ?: unsupported(node, "阶段 0 只支持经典 for。")
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
      return JavaAstForInitializer.VariableDeclaration(ids.next(), span(declaration), declaration.modifiersBefore(definition),
        declaration.typeBefore(definition), declaration.descendants().filter { it.name == "VariableDeclarator" }.map(::declarator).toList())
    }
    val expressions = nodes.expressionNodes().map(::expression)
    if (expressions.isEmpty()) unsupported(nodes.first(), "for 初始化不包含表达式。")
    return JavaAstForInitializer.Expressions(ids.next(), span(nodes.first(), nodes.last()), expressions)
  }

  /** 构建局部变量。 */
  private fun local(node: LezerSyntaxNode): JavaAstStatement.VariableDeclaration {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "局部变量缺少名称。")
    return JavaAstStatement.VariableDeclaration(ids.next(), span(node), node.modifiersBefore(definition), node.typeBefore(definition),
      node.descendants().filter { it.name == "VariableDeclarator" }.map(::declarator).toList())
  }

  /** declarator 的初始化值由 VariableInitializer 的唯一 expression child 提供。 */
  private fun declarator(node: LezerSyntaxNode): JavaAstVariableDeclarator {
    val definition = node.descendants().firstOrNull { it.name == "Definition" } ?: unsupported(node, "变量缺少名称。")
    val initializerNode = node.children().firstOrNull { it.name == "VariableInitializer" }?.onlyExpression()
      ?: node.expressions().singleOrNull()
    val initializer = initializerNode?.let(::expression)
    return JavaAstVariableDeclarator(ids.next(), span(node), text(definition), initializer)
  }

  /** 结构化映射阶段 0 所需的表达式节点。 */
  private fun expression(original: LezerSyntaxNode): JavaAstExpression {
    val node = original.unwrap()
    return when (node.name) {
      "Identifier", "ScopedIdentifier" -> JavaAstExpression.Name(ids.next(), span(node), text(node))
      "This" -> JavaAstExpression.This(ids.next(), span(node))
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
      "FieldAccess" -> fieldAccess(node)
      else -> unsupported(node, "阶段 0 不支持该表达式。")
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
    // 限定调用的 receiver 也可能包含 Identifier，必须优先取专用 MethodName，
    // 否则 Helper.twice() 会被错误解析为调用 Helper()。
    val name = node.descendants().firstOrNull { it.name == "MethodName" }
      ?: node.descendants().firstOrNull { it.name == "Identifier" }
      ?: unsupported(node, "调用缺少方法名。")
    val arguments = node.descendants().firstOrNull { it.name == "ArgumentList" }?.expressions()?.map(::expression).orEmpty()
    val receiver = node.expressions().firstOrNull { it.to <= name.from }?.let(::expression)
    return JavaAstExpression.MethodInvocation(ids.next(), span(node), receiver, text(name), emptyList(), arguments)
  }

  /** 阶段 0 仅支持普通对象创建，不支持数组创建与匿名类型。 */
  private fun newObject(node: LezerSyntaxNode): JavaAstExpression.NewObject {
    val type = node.descendants().firstOrNull { it.name in TYPE_REFERENCE_NODES } ?: unsupported(node, "对象创建缺少类型。")
    val arguments = node.descendants().firstOrNull { it.name == "ArgumentList" }?.expressions()?.map(::expression).orEmpty()
    return JavaAstExpression.NewObject(ids.next(), span(node), typeReference(type), arguments)
  }

  /** 字段访问保留 receiver 与字段名称的 CST 关系。 */
  private fun fieldAccess(node: LezerSyntaxNode): JavaAstExpression.FieldAccess {
    val receiver = node.expressions().firstOrNull() ?: unsupported(node, "字段访问缺少 receiver。")
    val name = node.descendants().lastOrNull { it.name == "Identifier" || it.name == "FieldName" } ?: unsupported(node, "字段访问缺少名称。")
    return JavaAstExpression.FieldAccess(ids.next(), span(node), expression(receiver), text(name))
  }

  /** 保留 literal token 原文，数值范围与转义交由语义阶段。 */
  private fun literal(node: LezerSyntaxNode, kind: JavaAstLiteralKind) = JavaAstExpression.Literal(ids.next(), span(node), kind, text(node))

  /** 阶段 0 类型限于 primitive、void、简单命名类型。 */
  private fun typeReference(node: LezerSyntaxNode): JavaAstTypeReference = when (node.name) {
    "PrimitiveType" -> JavaAstTypeReference.Primitive(ids.next(), span(node), primitive(text(node)))
    "TypeName", "ScopedTypeName" -> JavaAstTypeReference.Named(ids.next(), span(node), text(node), emptyList())
    "void" -> JavaAstTypeReference.Void(ids.next(), span(node))
    else -> unsupported(node, "阶段 0 不支持该类型。")
  }

  /** 从 declaration 的类型 CST 子节点构建类型。 */
  private fun LezerSyntaxNode.typeBefore(definition: LezerSyntaxNode): JavaAstTypeReference {
    descendants().firstOrNull {
      it.name in UNSUPPORTED_TYPE_NODES || it.name == "Dimension"
    }?.let { unsupported(it, "阶段 0 尚不支持数组、参数化类型或通配符类型。") }
    val type = descendants().filter { it.to <= definition.from && it.name in TYPE_REFERENCE_NODES }
      .filterNot { it.hasAncestor("Annotation") }.minWithOrNull(compareBy<LezerSyntaxNode> { it.from }.thenByDescending { it.to })
      ?: unsupported(this, "声明缺少支持的类型。")
    return typeReference(type)
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

  /** definition 前的 modifier token。 */
  private fun LezerSyntaxNode.modifiersBefore(definition: LezerSyntaxNode): Set<JavaAstModifier> =
    descendants().filter { it.to <= definition.from }.map(::text).mapNotNull(MODIFIERS::get).toSet()

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
private val TYPE_REFERENCE_NODES = setOf("PrimitiveType", "TypeName", "ScopedTypeName", "void")
private val UNSUPPORTED_TYPE_NODES = setOf("ArrayType", "GenericType", "Wildcard", "WildcardType")
private val UNSUPPORTED_CLASS_CLAUSES = setOf("TypeParameters", "Superclass", "Interfaces", "SuperInterfaces")
private val NAME_NODES = setOf("QualifiedName", "ScopedIdentifier", "TypeName", "Identifier")
private val EXPRESSION_NODES = setOf("Expression", "BinaryExpression", "AssignmentExpression", "UnaryExpression", "PostfixExpression", "UpdateExpression", "MethodInvocation", "ObjectCreationExpression", "FieldAccess", "ParenthesizedExpression", "Identifier", "ScopedIdentifier", "This", "IntegerLiteral", "FloatingPointLiteral", "StringLiteral", "CharacterLiteral", "BooleanLiteral", "null")
private val WRAPPERS = setOf("Expression", "ConditionalExpression", "ConditionalOrExpression", "ConditionalAndExpression")
private val UNSUPPORTED_NODES = setOf("RecordDeclaration", "ModuleDeclaration", "TextBlock", "SwitchExpression", "YieldStatement")
private val MODIFIERS = mapOf("public" to JavaAstModifier.PUBLIC, "protected" to JavaAstModifier.PROTECTED, "private" to JavaAstModifier.PRIVATE, "abstract" to JavaAstModifier.ABSTRACT, "static" to JavaAstModifier.STATIC, "final" to JavaAstModifier.FINAL)
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
