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
  /** Object 虚方法 operation 到槽位的映射，供运行时动态分派且不按源码名称猜测。 */
  val builtinVirtualSlots: Map<JavaBuiltinOperation, Int> = mapOf(
    JavaBuiltinOperation.OBJECT_EQUALS to 0,
    JavaBuiltinOperation.OBJECT_HASH_CODE to 1,
    JavaBuiltinOperation.OBJECT_TO_STRING to 2,
  ),
)

/** 运行时仍需区分可实例化 class 与只承载静态成员/default method 的 interface。 */
internal enum class JavaIrTypeDeclarationKind {
  CLASS,
  ENUM,
  INTERFACE,
}

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
  val kind: JavaIrTypeDeclarationKind = JavaIrTypeDeclarationKind.CLASS,
  /** class 最终继承的接口默认实现，key 为虚槽，value 为已验证的接口方法。 */
  val interfaceDefaultMethods: Map<Int, JavaIrMethodId> = emptyMap(),
  /** 非空时表示该源码 class 是异常类型，值为直接异常父类型的限定名。 */
  val exceptionSuperQualifiedName: String? = null,
  /** enum 常量按源码顺序保存，供 values/valueOf 与初始化校验使用。 */
  val enumConstants: List<JavaIrEnumConstant> = emptyList(),
)

/** enum 常量的稳定字段、名称和 ordinal；常量对象本身仍存储在对应 static field。 */
internal data class JavaIrEnumConstant(
  val field: JavaIrFieldId,
  val name: String,
  val ordinal: Int,
)

/** 不依赖源码方法体的四个 enum 标准操作。 */
internal enum class JavaIrEnumOperation {
  NAME,
  ORDINAL,
  VALUES,
  VALUE_OF,
}

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
 * 已消除复合赋值等表达式语法糖的结构化 IR 语句；for 保留 update 区域以维持 continue 语义。
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

  /** do-while 保留尾部条件，避免 lowering 复制循环体或改变 continue 目标。 */
  data class DoWhile(
    val body: JavaIrStatement,
    val condition: JavaIrExpression,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  /**
   * 已完成 initializer lowering 的经典 for。
   *
   * initializer 由外层 block 承载；保留 [updates] 是为了让 continue 仍按 Java 规则先执行更新。
   */
  data class For(
    val condition: JavaIrExpression,
    val updates: List<JavaIrExpression>,
    val body: JavaIrStatement,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  /** 增强 for 的运行协议已经由语义阶段冻结。 */
  data class EnhancedFor(
    val kind: JavaIrEnhancedForKind,
    val local: JavaIrLocalId,
    val iterable: JavaIrExpression,
    val elementType: JavaIrType,
    val elementConversions: List<JavaIrConversion>,
    val body: JavaIrStatement,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  /** switch 保留顺序与 fallthrough；default 的 label 为 null。 */
  data class Switch(
    val selector: JavaIrExpression,
    val entries: List<JavaIrSwitchEntry>,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  /** 退出最近循环或 switch。 */
  data class Break(override val span: JavaSourceSpan) : JavaIrStatement

  /** 继续最近循环；for 的更新表达式由后端原生 for 结构保证执行。 */
  data class Continue(override val span: JavaSourceSpan) : JavaIrStatement

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

  /**
   * 源码自定义异常构造器委托到 builtin Throwable 家族时的初始化动作。
   *
   * [message] 与 [cause] 已按目标构造器签名完成求值；后端只负责把元数据写入当前 `this`。
   */
  data class InitializeException(
    val message: JavaIrExpression?,
    val cause: JavaIrExpression?,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  data class Return(
    val expression: JavaIrExpression?,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  /** Java throw；后端必须在抛出前执行 null 检查并把 null 转为 NullPointerException。 */
  data class Throw(
    val expression: JavaIrExpression,
    override val span: JavaSourceSpan,
  ) : JavaIrStatement

  /**
   * 保留原生 try/catch/finally 结构，确保 finally 对所有 abrupt completion 的覆盖顺序与 Java 一致。
   */
  data class Try(
    val body: Block,
    val catches: List<JavaIrCatchClause>,
    val finallyBlock: Block?,
    override val span: JavaSourceSpan,
    val resources: List<JavaIrResource> = emptyList(),
  ) : JavaIrStatement
}

/** 已完成类型解析与局部绑定的 catch 分支。 */
internal data class JavaIrCatchClause(
  val exceptionType: JavaIrType.Reference,
  val local: JavaIrLocalId,
  val body: JavaIrStatement.Block,
  val span: JavaSourceSpan,
  val additionalExceptionTypes: List<JavaIrType.Reference> = emptyList(),
) {
  val exceptionTypes: List<JavaIrType.Reference> get() = listOf(exceptionType) + additionalExceptionTypes
}

/** try-with-resources 已完成 initializer 与 close() 分派的单个资源。 */
internal data class JavaIrResource(
  val local: JavaIrLocalId,
  val initializer: JavaIrExpression,
  val closeExpression: JavaIrExpression,
  val span: JavaSourceSpan,
)

/** 增强 for 后端协议；集合种类显式区分以选择稳定 iterator operation。 */
internal enum class JavaIrEnhancedForKind { ARRAY, LIST, SET }

/** typed IR switch case/default。 */
internal data class JavaIrSwitchEntry(
  val label: JavaIrExpression.Constant?,
  val statements: List<JavaIrStatement>,
  val span: JavaSourceSpan,
)

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

  /** 惰性条件表达式；后端只能执行被选中的一个结果分支。 */
  data class Conditional(
    val condition: JavaIrExpression,
    val whenTrue: JavaIrExpression,
    val whenFalse: JavaIrExpression,
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

  /**
   * 调用没有源码方法体、但已经由 builtin catalog 冻结槽位的虚方法根。
   *
   * 该节点让 AutoCloseable.close、Throwable API 等接口/根类方法可以动态分派到用户实现，
   * 同时避免伪造一个不存在于 [JavaIrProgram.classes] 中的 builtin 方法编号。
   */
  data class InvokeVirtualSlot(
    val operation: JavaBuiltinOperation,
    val receiver: JavaIrExpression,
    val virtualSlot: Int,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /**
   * 已完成 SAM 适配的 Java lambda。
   *
   * [parameters] 是由 lambda 自身绑定的局部编号，[captures] 只列出外围方法局部/参数；body 内
   * 的所有 GetLocal 均使用全局稳定 local id。后端必须用箭头函数保存外围 Java `this` 语义。
   */
  data class Lambda(
    val interfaceClass: JavaIrClassId,
    val virtualSlot: Int,
    val parameters: List<JavaIrLocalId>,
    val captures: List<JavaIrLocalId>,
    val body: JavaIrStatement.Block,
    override val type: JavaIrType.Reference,
    override val span: JavaSourceSpan,
    /** 方法引用创建时按源码顺序求值并固定的 receiver；普通 lambda 为空。 */
    val boundValues: List<JavaIrBoundValue> = emptyList(),
  ) : JavaIrExpression

  data class NewObject(
    val classId: JavaIrClassId,
    val constructor: JavaIrMethodId,
    val arguments: List<JavaIrExpression>,
    override val type: JavaIrType,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /**
   * enum 声明内部的常量分配。
   *
   * 与普通 [NewObject] 分离后，后端可在构造器执行前写入 Java 保证可见的 name/ordinal，且用户
   * 源码无法伪造 enum 实例。
   */
  data class NewEnumConstant(
    val classId: JavaIrClassId,
    val constructor: JavaIrMethodId,
    val arguments: List<JavaIrExpression>,
    val name: String,
    val ordinal: Int,
    override val type: JavaIrType.Reference,
    override val span: JavaSourceSpan,
  ) : JavaIrExpression

  /** enum 的 name/ordinal/values/valueOf 已在语义阶段绑定，不按方法名称猜测。 */
  data class InvokeEnum(
    val operation: JavaIrEnumOperation,
    val enumClass: JavaIrClassId,
    val receiver: JavaIrExpression?,
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

  /**
   * 多维数组分配；[lengths] 只包含源码中实际给出长度的连续前缀。
   *
   * 完整数组 rank 由 [type] 决定，因此 `new int[a][]` 会保存一个长度和二维结果类型。后端必须
   * 先按列表顺序各求值一次，再递归分配已给出长度的层级，不能在外层循环中重复求值内层长度。
   */
  data class NewMultiArray(
    val lengths: List<JavaIrExpression>,
    override val type: JavaIrType.Array,
    override val span: JavaSourceSpan,
    /** 最内层引用 component 的运行时分类；primitive 多维数组为 null。 */
    val leafReferenceComponentKind: JavaIrArrayReferenceComponentKind? = null,
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

/**
 * Lambda/方法引用创建时立即求值的闭包值。
 *
 * [local] 仅在函数对象 body 内可见；[requireNonNull] 用于 Java 绑定方法引用的创建时 NPE 语义。
 */
internal data class JavaIrBoundValue(
  val local: JavaIrLocalId,
  val expression: JavaIrExpression,
  val requireNonNull: Boolean,
)

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
  LONG,
  FLOAT,
  DOUBLE,
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

  /** 已验证为有限 Java 浮点 literal 的数值；表达式类型决定是否应用 Math.fround。 */
  data class FloatingValue(val value: Double) : JavaIrConstant

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
