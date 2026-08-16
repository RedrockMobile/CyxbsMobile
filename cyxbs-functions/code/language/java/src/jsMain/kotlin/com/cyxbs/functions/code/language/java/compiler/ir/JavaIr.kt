package com.cyxbs.functions.code.language.java.compiler.ir

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinOperation
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinTypeRole
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
  /** builtin classId 到稳定角色的映射；不把内建类型伪造成可发射的用户 class。 */
  val builtinTypeRoles: Map<JavaIrClassId, JavaBuiltinTypeRole> = emptyMap(),
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
  /**
   * 非 static 字段初始化器组成的已排序语句块。
   *
   * 后端必须在成功完成 super 构造器调用后、当前构造器普通语句前执行一次，不能根据字段声明重新猜测。
   */
  val instanceInitializer: JavaIrStatement.Block? = null,
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
  /** 默认保留普通方法身份，兼容阶段 0 的既有位置构造调用。 */
  val kind: JavaIrMethodKind = JavaIrMethodKind.METHOD,
) {
  init {
    val requiresVirtualSlot = dispatch == JavaIrDispatchKind.VIRTUAL ||
      dispatch == JavaIrDispatchKind.INTERFACE
    require(requiresVirtualSlot == (virtualSlot != null)) {
      "Only virtual and interface Java IR methods may declare a virtual slot."
    }
    if (kind == JavaIrMethodKind.CONSTRUCTOR) {
      require(descriptor.endsWith(")V")) {
        "Java IR constructors must use a descriptor with a void return type."
      }
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
 * 方法声明的语义身份。
 *
 * [CONSTRUCTOR] 与普通 SPECIAL 方法分离，避免后端把私有方法或 super 普通调用误当成对象初始化。
 */
internal enum class JavaIrMethodKind {
  METHOD,
  CONSTRUCTOR,
}

/** 构造器首语句的已决议委托目标。 */
internal enum class JavaIrConstructorInvocationKind {
  THIS,
  SUPER,
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

  /**
   * 构造器的首条委托语句。
   *
   * [constructor] 已由语义阶段精确选定；lowering 必须确保它只出现在 CONSTRUCTOR 方法体的第一条语句，
   * 以便后端准确插入实例字段初始化。
   */
  data class ConstructorInvocation(
    val kind: JavaIrConstructorInvocationKind,
    val constructor: JavaIrMethodId,
    val arguments: List<JavaIrExpression>,
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

  /**
   * 当前实例 receiver。
   *
   * 仅实例方法和构造器可产生该节点；使用 [JavaIrType.Reference] 避免把 primitive 或 null 伪装为 this。
   */
  data class This(
    override val type: JavaIrType.Reference,
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

  /**
   * 由精选类库提供、且不对应用户字段存储的稳定值。
   *
   * 例如 `System.out` 与 `System.err` 必须保留为显式 operation，后端不得按字段名猜测宿主桥。
   */
  data class BuiltinValue(
    val operation: JavaBuiltinOperation,
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

  /**
   * 调用精选类库的确定 operation，不暴露不存在的 JDK 方法体或虚方法槽。
   *
   * [receiver] 为 null 表示 static 操作；实例操作必须保存 receiver。字段顺序明确要求后端先求值
   * receiver，再从左到右求值 [arguments]，并且每个表达式只求值一次。
   */
  data class InvokeBuiltin(
    val operation: JavaBuiltinOperation,
    val receiver: JavaIrExpression?,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /** builtin 构造不进入用户 class 分配/构造器调用链，operation 完整决定运行时对象形态。 */
  data class ConstructBuiltin(
    val operation: JavaBuiltinOperation,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType.Reference,
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

  /** 一维数组分配；length 在进入节点前已按 Java 从左到右规则求值一次。 */
  data class NewArray(
    val componentType: JavaIrType,
    val length: JavaIrExpression,
    override val type: JavaIrType.Array,
    override val span: JavaSourceSpan,
    /** 引用数组的运行时存储边界；primitive 数组由 [componentType] 决定窄化。 */
    val referenceComponentKind: JavaIrArrayReferenceComponentKind? = null,
  ) : JavaIrExpression

  /** 一维数组 initializer；elements 保持源码顺序，后端不得重排或合并副作用。 */
  data class ArrayInitializer(
    val componentType: JavaIrType,
    val elements: List<JavaIrExpression>,
    override val type: JavaIrType.Array,
    override val span: JavaSourceSpan,
    /** 与 [NewArray.referenceComponentKind] 保持一致，供 initializer 写回使用。 */
    val referenceComponentKind: JavaIrArrayReferenceComponentKind? = null,
  ) : JavaIrExpression

  /** Java 数组元素读取，数组与 index 均由运行时统一检查。 */
  data class GetArrayElement(
    val array: JavaIrExpression,
    val index: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /** Java 数组元素写入；赋值表达式的结果为写入后的 value。 */
  data class SetArrayElement(
    val array: JavaIrExpression,
    val index: JavaIrExpression,
    val value: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /** Java array.length 专用节点，避免把 length 当作普通字段或 JavaScript 属性。 */
  data class ArrayLength(
    val array: JavaIrExpression,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /** 显式字符串拼接，part 的转换种类由语义阶段决定，后端不再猜测 Java `+`。 */
  data class StringConcat(
    val parts: List<JavaIrStringConcatPart>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression
}

/** 引用数组写入检查不依赖 JavaScript 动态类型，明确区分 Object、String 与用户类。 */
internal enum class JavaIrArrayReferenceComponentKind {
  OBJECT,
  STRING,
  USER_CLASS,
}

/** String 拼接的单个 part；保留 Java 转换种类以隔离 JavaScript 的隐式 String 规则。 */
internal data class JavaIrStringConcatPart(
  val expression: JavaIrExpression,
  val conversion: JavaIrStringConversionKind,
)

/** 已由语义分析决议的 Java String 转换；首批不包含 boxing 或任意对象 toString。 */
internal enum class JavaIrStringConversionKind {
  STRING,
  NULL,
  BOOLEAN,
  CHAR,
  INT_LIKE,
  BOXED,
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

  /** Java update/compound assignment 独有的隐式 primitive 回写窄化。 */
  data class PrimitiveNarrowing(
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
