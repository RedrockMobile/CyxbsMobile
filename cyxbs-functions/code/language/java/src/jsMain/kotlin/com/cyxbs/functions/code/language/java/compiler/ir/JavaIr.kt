package com.cyxbs.functions.code.language.java.compiler.ir

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/** typed IR 中的类编号。 */
internal data class JavaIrClassId(val value: Int)

/** typed IR 中的方法编号。 */
internal data class JavaIrMethodId(val value: Int)

/** typed IR 中的字段编号。 */
internal data class JavaIrFieldId(val value: Int)

/** typed IR 中的局部变量编号。 */
internal data class JavaIrLocalId(val value: Int)

/**
 * Java 语义完成擦除后交给运行时的类型。
 *
 * 泛型实参不会进入运行时 IR；数组和必要的 cast/instanceof 仍保留组件类型。
 */
internal sealed interface JavaIrType {
  data class Primitive(val kind: JavaAstPrimitiveType) : JavaIrType
  data class Reference(val classId: JavaIrClassId) : JavaIrType
  data class Array(val componentType: JavaIrType) : JavaIrType
  data object Null : JavaIrType
  data object Void : JavaIrType
}

/** 完整工作区 lowering 后的 typed IR。 */
internal data class JavaIrProgram(
  val classes: List<JavaIrClass>,
)

/** 一个已经解析继承关系的 Java 类型。 */
internal data class JavaIrClass(
  val id: JavaIrClassId,
  val qualifiedName: String,
  val superClass: JavaIrClassId?,
  val interfaces: List<JavaIrClassId>,
  val fields: List<JavaIrField>,
  val methods: List<JavaIrMethod>,
  val staticInitializer: JavaIrStatement.Block?,
  val span: JavaSourceSpan,
)

/** 已完成静态/实例分类的字段。 */
internal data class JavaIrField(
  val id: JavaIrFieldId,
  val owner: JavaIrClassId,
  val name: String,
  val type: JavaIrType,
  val isStatic: Boolean,
  val initializer: JavaIrExpression?,
  val span: JavaSourceSpan,
)

/**
 * 已完成重载选择、泛型擦除和分派分类的方法。
 *
 * [descriptor] 是稳定缓存和名称 mangling 使用的 Java 擦除签名；虚方法的 [virtualSlot] 必须非空。
 */
internal data class JavaIrMethod(
  val id: JavaIrMethodId,
  val owner: JavaIrClassId,
  val name: String,
  val descriptor: String,
  val dispatch: JavaIrDispatchKind,
  val virtualSlot: Int?,
  val returnType: JavaIrType,
  val parameters: List<JavaIrLocal>,
  val locals: List<JavaIrLocal>,
  val body: JavaIrStatement.Block?,
  val span: JavaSourceSpan,
) {
  init {
    val requiresVirtualSlot = dispatch == JavaIrDispatchKind.VIRTUAL ||
      dispatch == JavaIrDispatchKind.INTERFACE
    require(requiresVirtualSlot == (virtualSlot != null)) {
      "Only virtual and interface Java IR methods may declare a virtual slot."
    }
  }
}

/** IR 方法参数和局部变量。 */
internal data class JavaIrLocal(
  val id: JavaIrLocalId,
  val nameHint: String,
  val type: JavaIrType,
  val isParameter: Boolean,
  val span: JavaSourceSpan,
)

/** Java 方法在运行时采用的调用方式。 */
internal enum class JavaIrDispatchKind {
  STATIC,
  SPECIAL,
  VIRTUAL,
  INTERFACE,
}

/**
 * 已消除 for、复合赋值等语法糖的结构化 IR 语句。
 *
 * 保留结构化控制流比提前转换成 SSA 更适合 JavaScript 后端和 Java 源码栈映射。
 */
internal sealed interface JavaIrStatement {
  val span: JavaSourceSpan

  data class Block(
    val statements: List<JavaIrStatement>,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class DeclareLocal(
    val local: JavaIrLocalId,
    val initializer: JavaIrExpression?,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class Expression(
    val expression: JavaIrExpression,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class If(
    val condition: JavaIrExpression,
    val thenBranch: JavaIrStatement,
    val elseBranch: JavaIrStatement?,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class While(
    val condition: JavaIrExpression,
    val body: JavaIrStatement,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class Return(
    val expression: JavaIrExpression?,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class Throw(
    val expression: JavaIrExpression,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement
}

/** 每个表达式都携带已经确定的运行时类型，JS 后端不得再次推断。 */
internal sealed interface JavaIrExpression {
  val type: JavaIrType
  val span: JavaSourceSpan

  data class Constant(
    val value: JavaIrConstant,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class GetLocal(
    val local: JavaIrLocalId,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /** Java 赋值表达式会返回写入后的值，因此写入仍作为表达式存在。 */
  data class SetLocal(
    val local: JavaIrLocalId,
    val value: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class GetField(
    val receiver: JavaIrExpression,
    val field: JavaIrFieldId,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class SetField(
    val receiver: JavaIrExpression,
    val field: JavaIrFieldId,
    val value: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class GetStaticField(
    val field: JavaIrFieldId,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class SetStaticField(
    val field: JavaIrFieldId,
    val value: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class Binary(
    val left: JavaIrExpression,
    val operator: JavaIrBinaryOperator,
    val right: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class Unary(
    val operator: JavaIrUnaryOperator,
    val operand: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class Convert(
    val conversion: JavaIrConversion,
    val expression: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class InvokeStatic(
    val method: JavaIrMethodId,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class InvokeSpecial(
    val receiver: JavaIrExpression,
    val method: JavaIrMethodId,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class InvokeVirtual(
    val receiver: JavaIrExpression,
    val method: JavaIrMethodId,
    val virtualSlot: Int,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  data class NewObject(
    val classId: JavaIrClassId,
    val constructor: JavaIrMethodId,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression
}

/** 精确且不依赖 JavaScript Number 表示范围的 IR 常量。 */
internal sealed interface JavaIrConstant {
  data class BooleanValue(val value: Boolean) : JavaIrConstant
  data class IntValue(val value: Int) : JavaIrConstant

  /**
   * 十进制 long 文本。
   *
   * 使用字符串避免 Kotlin/JS 与 JSON 经过 Number 时损失 64 位精度，JS 后端再生成 BigInt。
   */
  data class LongValue(val canonicalText: String) : JavaIrConstant

  data class StringValue(val value: String) : JavaIrConstant
  data object NullValue : JavaIrConstant
}

/** IR 中不再区分源码拼写，只保留后端需要实现的运算语义。 */
internal enum class JavaIrBinaryOperator {
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

/** 已消除前后缀递增副作用后的纯一元运算。 */
internal enum class JavaIrUnaryOperator {
  NEGATE,
  LOGICAL_NOT,
  BITWISE_NOT,
}

/** lowering 明确登记、由 JS 后端实现的转换。 */
internal sealed interface JavaIrConversion {
  data object Identity : JavaIrConversion

  data class PrimitiveWidening(
    val from: JavaAstPrimitiveType,
    val to: JavaAstPrimitiveType,
  ) : JavaIrConversion

  data class ReferenceWidening(
    val from: JavaIrType,
    val to: JavaIrType,
  ) : JavaIrConversion

  data class Boxing(
    val primitive: JavaAstPrimitiveType,
    val boxedClass: JavaIrClassId,
  ) : JavaIrConversion

  data class Unboxing(
    val boxedClass: JavaIrClassId,
    val primitive: JavaAstPrimitiveType,
  ) : JavaIrConversion
}
