package com.cyxbs.functions.code.language.java.compiler.ast

import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * 规范化 Java AST 的公共节点契约。
 *
 * AST 不保存 Lezer 节点，也不携带最终符号或类型；[nodeId] 用于关联语义 side table。
 */
internal sealed interface JavaAstNode {
  val nodeId: JavaNodeId
  val span: JavaSourceSpan
}

/** 一次工作区解析产生的不可变 AST。 */
internal data class JavaAstWorkspace(
  val units: List<JavaAstCompilationUnit>,
)

/** 单个 Java 源文件的规范化结构。 */
internal data class JavaAstCompilationUnit(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val sourceFile: JavaSourceFile,
  val packageName: String?,
  val imports: List<JavaAstImport>,
  val types: List<JavaAstTypeDeclaration>,
) : JavaAstNode

/** Java import 声明，名称不包含末尾通配符。 */
internal data class JavaAstImport(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val qualifiedName: String,
  val isStatic: Boolean,
  val isWildcard: Boolean,
) : JavaAstNode

/** Java 声明上与阶段 0 语义有关的修饰符。 */
internal enum class JavaAstModifier {
  PUBLIC,
  PROTECTED,
  PRIVATE,
  ABSTRACT,
  STATIC,
  FINAL,
}

/**
 * 阶段 1 保留的声明注解。
 *
 * 当前只由前端接受精确的 [qualifiedName] `Override`，保留该节点可避免把注解静默擦除，
 * 也为后续语义阶段校验 override 关系提供稳定的源码区间。
 */
internal data class JavaAstAnnotation(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val qualifiedName: String,
) : JavaAstNode

/** 类型声明种类；阶段 0 只执行 CLASS，其余种类由后续阶段逐步开放。 */
internal enum class JavaAstTypeDeclarationKind {
  CLASS,
  INTERFACE,
  ENUM,
}

/** 类、接口或枚举声明的统一规范化结构。 */
internal data class JavaAstTypeDeclaration(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val kind: JavaAstTypeDeclarationKind,
  val modifiers: Set<JavaAstModifier>,
  val name: String,
  val typeParameters: List<JavaAstTypeParameter>,
  val superClass: JavaAstTypeReference?,
  val interfaces: List<JavaAstTypeReference>,
  val members: List<JavaAstMemberDeclaration>,
) : JavaAstNode

/** 泛型类型参数及其显式上界。 */
internal data class JavaAstTypeParameter(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val name: String,
  val upperBounds: List<JavaAstTypeReference>,
) : JavaAstNode

/** Java 源码中尚未完成名称解析的类型引用。 */
internal sealed interface JavaAstTypeReference : JavaAstNode {
  /** primitive 类型。 */
  data class Primitive(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val kind: JavaAstPrimitiveType,
  ) : JavaAstTypeReference

  /** 可能带限定名和泛型实参的声明类型。 */
  data class Named(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val qualifiedName: String,
    val arguments: List<JavaAstTypeReference>,
    /** `new Box<>()` 等菱形写法；空 [arguments] 本身表示未参数化类型。 */
    val usesDiamond: Boolean = false,
  ) : JavaAstTypeReference

  /** 固定维度的数组类型。 */
  data class Array(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val componentType: JavaAstTypeReference,
    val dimensions: Int,
  ) : JavaAstTypeReference {
    init {
      require(dimensions > 0) { "Java AST array dimensions must be positive." }
    }
  }

  /** 泛型通配符；两个边界不能同时存在。 */
  data class Wildcard(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val upperBound: JavaAstTypeReference? = null,
    val lowerBound: JavaAstTypeReference? = null,
  ) : JavaAstTypeReference {
    init {
      require(upperBound == null || lowerBound == null) {
        "A Java wildcard cannot declare upper and lower bounds together."
      }
    }
  }

  /** 方法返回位置允许的 void。 */
  data class Void(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
  ) : JavaAstTypeReference
}

/** Java 8 primitive 类型集合。 */
internal enum class JavaAstPrimitiveType {
  BOOLEAN,
  BYTE,
  SHORT,
  INT,
  LONG,
  CHAR,
  FLOAT,
  DOUBLE,
}

/** 类型体中的成员声明。 */
internal sealed interface JavaAstMemberDeclaration : JavaAstNode {
  /** 字段声明；同一声明中的多个变量已拆成独立 declarator。 */
  data class Field(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val modifiers: Set<JavaAstModifier>,
    val type: JavaAstTypeReference,
    val declarators: List<JavaAstVariableDeclarator>,
  ) : JavaAstMemberDeclaration

  /** 普通方法声明；抽象方法的 [body] 为空。 */
  data class Method(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val modifiers: Set<JavaAstModifier>,
    val typeParameters: List<JavaAstTypeParameter>,
    val returnType: JavaAstTypeReference,
    val name: String,
    val parameters: List<JavaAstParameter>,
    val body: JavaAstStatement.Block?,
    /** 仅保留阶段 1 明确允许的 `@Override`，默认值保持既有构造调用兼容。 */
    val annotations: List<JavaAstAnnotation> = emptyList(),
  ) : JavaAstMemberDeclaration

  /** 构造器声明；显式 this/super 调用保留为方法体首条表达式。 */
  data class Constructor(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val modifiers: Set<JavaAstModifier>,
    val typeParameters: List<JavaAstTypeParameter>,
    val name: String,
    val parameters: List<JavaAstParameter>,
    val body: JavaAstStatement.Block,
  ) : JavaAstMemberDeclaration
}

/** 字段或局部变量声明中的单个名称与初始化表达式。 */
internal data class JavaAstVariableDeclarator(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val name: String,
  val initializer: JavaAstExpression?,
) : JavaAstNode

/** 方法或构造器参数。 */
internal data class JavaAstParameter(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val modifiers: Set<JavaAstModifier>,
  val type: JavaAstTypeReference,
  val name: String,
  val isVararg: Boolean,
) : JavaAstNode

/** 阶段 0 所需的结构化 Java 语句。 */
internal sealed interface JavaAstStatement : JavaAstNode {
  /** 词法作用域块。 */
  data class Block(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val statements: List<JavaAstStatement>,
  ) : JavaAstStatement

  /** 局部变量声明语句。 */
  data class VariableDeclaration(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val modifiers: Set<JavaAstModifier>,
    val type: JavaAstTypeReference,
    val declarators: List<JavaAstVariableDeclarator>,
  ) : JavaAstStatement

  /**
   * 构造器的显式 `this(...)` 或 `super(...)` 调用。
   *
   * 它不是普通方法调用：Java 要求其只能位于构造器 body 的第一条语句，前端保留专用节点让
   * 后续语义与 lowering 可以据此执行该约束和初始化顺序。
   */
  data class ConstructorInvocation(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val kind: JavaAstConstructorInvocationKind,
    val arguments: List<JavaAstExpression>,
  ) : JavaAstStatement

  /** 仅为副作用求值的表达式语句。 */
  data class Expression(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val expression: JavaAstExpression,
  ) : JavaAstStatement

  /** if/else 语句。 */
  data class If(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val condition: JavaAstExpression,
    val thenBranch: JavaAstStatement,
    val elseBranch: JavaAstStatement?,
  ) : JavaAstStatement

  /** while 语句。 */
  data class While(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val condition: JavaAstExpression,
    val body: JavaAstStatement,
  ) : JavaAstStatement

  /** 仍保留源码结构、等待 lowering 的经典 for 语句。 */
  data class For(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val initializer: JavaAstForInitializer?,
    val condition: JavaAstExpression?,
    val updates: List<JavaAstExpression>,
    val body: JavaAstStatement,
  ) : JavaAstStatement

  /** 方法返回语句。 */
  data class Return(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val expression: JavaAstExpression?,
  ) : JavaAstStatement

  /** 空语句。 */
  data class Empty(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
  ) : JavaAstStatement
}

/** 显式构造器调用的目标。 */
internal enum class JavaAstConstructorInvocationKind {
  THIS,
  SUPER,
}

/** 经典 for 初始化部分的两种合法形态。 */
internal sealed interface JavaAstForInitializer : JavaAstNode {
  /** for 初始化中的局部变量声明。 */
  data class VariableDeclaration(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val modifiers: Set<JavaAstModifier>,
    val type: JavaAstTypeReference,
    val declarators: List<JavaAstVariableDeclarator>,
  ) : JavaAstForInitializer

  /** for 初始化中的逗号分隔表达式。 */
  data class Expressions(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val expressions: List<JavaAstExpression>,
  ) : JavaAstForInitializer
}

/** 阶段 0 所需的 Java 表达式，最终类型由语义 side table 提供。 */
internal sealed interface JavaAstExpression : JavaAstNode {
  /** 保留原 token 的字面量，避免在 CST adapter 中提前应用 Java 数值规则。 */
  data class Literal(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val kind: JavaAstLiteralKind,
    val tokenText: String,
  ) : JavaAstExpression

  /** 简单或限定名称表达式。 */
  data class Name(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val qualifiedName: String,
  ) : JavaAstExpression

  /** this 表达式。 */
  data class This(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
  ) : JavaAstExpression

  /** `super` 表达式，阶段 1 用于父类实例成员访问和调用。 */
  data class Super(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
  ) : JavaAstExpression

  /** 二元运算。 */
  data class Binary(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val left: JavaAstExpression,
    val operator: JavaAstBinaryOperator,
    val right: JavaAstExpression,
  ) : JavaAstExpression

  /** 前缀或后缀一元运算。 */
  data class Unary(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val operator: JavaAstUnaryOperator,
    val operand: JavaAstExpression,
  ) : JavaAstExpression

  /** 赋值及复合赋值。 */
  data class Assignment(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val target: JavaAstExpression,
    val operator: JavaAstAssignmentOperator,
    val value: JavaAstExpression,
  ) : JavaAstExpression

  /** 方法调用；[receiver] 为空表示当前类型中的非限定调用。 */
  data class MethodInvocation(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val receiver: JavaAstExpression?,
    val methodName: String,
    val typeArguments: List<JavaAstTypeReference>,
    val arguments: List<JavaAstExpression>,
  ) : JavaAstExpression

  /** 对象创建表达式。 */
  data class NewObject(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val type: JavaAstTypeReference,
    val arguments: List<JavaAstExpression>,
  ) : JavaAstExpression

  /**
   * 数组创建表达式。
   *
   * [componentType] 不包含维度；每个 [dimensions] 依源码顺序保留，未填写大小的维度以
   * [JavaAstArrayDimension.size] 为 `null` 表示。直接花括号初始化器也统一规范化为该节点，
   * 以便后续语义阶段在同一入口校验维度与元素类型。
   */
  data class NewArray(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val componentType: JavaAstTypeReference,
    val dimensions: List<JavaAstArrayDimension>,
    val initializer: JavaAstArrayInitializer?,
  ) : JavaAstExpression

  /** 数组下标访问；赋值目标合法性和元素类型由语义阶段决定。 */
  data class ArrayAccess(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val array: JavaAstExpression,
    val index: JavaAstExpression,
  ) : JavaAstExpression

  /** 实例或静态字段访问；具体分派由语义阶段确定。 */
  data class FieldAccess(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val receiver: JavaAstExpression,
    val fieldName: String,
  ) : JavaAstExpression

  /** 显式括号表达式，便于保持诊断区间；lowering 时可消除。 */
  data class Parenthesized(
    override val nodeId: JavaNodeId,
    override val span: JavaSourceSpan,
    val expression: JavaAstExpression,
  ) : JavaAstExpression
}

/** 数组创建中单个 `[...]` 维度；[size] 为 null 表示由初始化器或后续维度决定长度。 */
internal data class JavaAstArrayDimension(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val size: JavaAstExpression?,
) : JavaAstNode

/**
 * 数组花括号初始化器。
 *
 * 花括号不是可独立出现的 Java expression，故使用专用节点保留递归结构，避免错误地允许
 * `return { ... };` 一类语法。
 */
internal data class JavaAstArrayInitializer(
  override val nodeId: JavaNodeId,
  override val span: JavaSourceSpan,
  val elements: List<JavaAstArrayInitializerElement>,
) : JavaAstNode

/** 数组初始化器元素：普通表达式或嵌套花括号初始化器。 */
internal sealed interface JavaAstArrayInitializerElement {
  /** 普通数组元素表达式。 */
  data class Expression(val expression: JavaAstExpression) : JavaAstArrayInitializerElement

  /** 多维数组的嵌套花括号初始化器。 */
  data class Nested(val initializer: JavaAstArrayInitializer) : JavaAstArrayInitializerElement
}

/** Java 字面量分类。 */
internal enum class JavaAstLiteralKind {
  BOOLEAN,
  INTEGER,
  FLOATING_POINT,
  CHARACTER,
  STRING,
  NULL,
}

/** Java 二元操作符。 */
internal enum class JavaAstBinaryOperator {
  MULTIPLY,
  DIVIDE,
  REMAINDER,
  ADD,
  SUBTRACT,
  SHIFT_LEFT,
  SHIFT_RIGHT,
  UNSIGNED_SHIFT_RIGHT,
  LESS_THAN,
  LESS_THAN_OR_EQUAL,
  GREATER_THAN,
  GREATER_THAN_OR_EQUAL,
  EQUAL,
  NOT_EQUAL,
  BITWISE_AND,
  BITWISE_XOR,
  BITWISE_OR,
  LOGICAL_AND,
  LOGICAL_OR,
}

/** Java 一元操作符，递增递减显式区分前缀与后缀语义。 */
internal enum class JavaAstUnaryOperator {
  POSITIVE,
  NEGATIVE,
  LOGICAL_NOT,
  BITWISE_NOT,
  PRE_INCREMENT,
  PRE_DECREMENT,
  POST_INCREMENT,
  POST_DECREMENT,
}

/** Java 赋值操作符。 */
internal enum class JavaAstAssignmentOperator {
  ASSIGN,
  MULTIPLY_ASSIGN,
  DIVIDE_ASSIGN,
  REMAINDER_ASSIGN,
  ADD_ASSIGN,
  SUBTRACT_ASSIGN,
  SHIFT_LEFT_ASSIGN,
  SHIFT_RIGHT_ASSIGN,
  UNSIGNED_SHIFT_RIGHT_ASSIGN,
  AND_ASSIGN,
  XOR_ASSIGN,
  OR_ASSIGN,
}
