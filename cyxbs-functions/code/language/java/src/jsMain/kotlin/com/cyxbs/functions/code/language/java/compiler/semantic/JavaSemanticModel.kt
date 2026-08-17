package com.cyxbs.functions.code.language.java.compiler.semantic

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstWorkspace
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinMemberDescriptor
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinTypeRole
import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/** 一次编译中的语义符号编号。 */
internal data class JavaSymbolId(val value: Int)

/** 符号类别，用于校验绑定位置并决定 lowering 行为。 */
internal enum class JavaSymbolKind {
  PACKAGE,
  TYPE,
  TYPE_PARAMETER,
  FIELD,
  METHOD,
  CONSTRUCTOR,
  PARAMETER,
  LOCAL_VARIABLE,
}

/** Java 编译器内部的严格语义类型。 */
internal sealed interface JavaSemanticType {
  /** primitive 类型。 */
  data class Primitive(val kind: JavaAstPrimitiveType) : JavaSemanticType

  /** 已解析到具体类型符号的声明类型。 */
  data class Declared(
    val symbol: JavaSymbolId,
    val arguments: List<JavaSemanticType>,
  ) : JavaSemanticType

  /** 已解析到声明位置的类型变量。 */
  data class TypeVariable(val symbol: JavaSymbolId) : JavaSemanticType

  /** Java 数组类型；多维数组通过嵌套表示。 */
  data class Array(val componentType: JavaSemanticType) : JavaSemanticType

  /** 泛型通配符。 */
  data class Wildcard(
    val upperBound: JavaSemanticType? = null,
    val lowerBound: JavaSemanticType? = null,
  ) : JavaSemanticType

  /** null 字面量的专用类型。 */
  data object Null : JavaSemanticType

  /** 合法的方法无返回值类型。 */
  data object Void : JavaSemanticType

  /**
   * 发生诊断后的恢复类型。
   *
   * ERROR 只能帮助同一阶段减少级联诊断；含 ERROR 的工作区不得进入 typed IR。
   */
  data object Error : JavaSemanticType
}

/** 编译期间登记的声明符号。 */
internal data class JavaSemanticSymbol(
  val id: JavaSymbolId,
  val kind: JavaSymbolKind,
  val name: String,
  val owner: JavaSymbolId?,
  val declarationNode: JavaNodeId,
  val declarationSpan: JavaSourceSpan,
  val type: JavaSemanticType?,
)

/** Java 声明的源码可见性；默认可见性使用独立枚举值，避免用 null 表达。 */
internal enum class JavaVisibility {
  PUBLIC,
  PROTECTED,
  PACKAGE_PRIVATE,
  PRIVATE,
}

/** 已进入语义模型的类型种类；[BUILTIN] 用于没有用户源码声明的 Object、String 等根类型。 */
internal enum class JavaSemanticTypeDeclarationKind {
  CLASS,
  INTERFACE,
  ENUM,
  BUILTIN,
}

/**
 * 已解析的类型声明。
 *
 * [membersInSourceOrder] 只保存源码显式成员的 symbol，供字段初始化顺序和确定性代码生成使用；
 * 隐式 default constructor 等合成成员由对应声明表保存，不伪装成源码成员。
 */
internal data class JavaSemanticTypeDeclaration(
  val symbol: JavaSymbolId,
  val qualifiedName: String,
  val kind: JavaSemanticTypeDeclarationKind,
  val visibility: JavaVisibility,
  val isFinal: Boolean,
  val typeParameters: List<JavaSymbolId>,
  val directSuperClass: JavaSemanticType.Declared?,
  val membersInSourceOrder: List<JavaSymbolId>,
  /** class 的 implements 与 interface 的 extends 均冻结为显式直接接口边。 */
  val directInterfaces: List<JavaSemanticType.Declared> = emptyList(),
) {
  init {
    require(qualifiedName.isNotEmpty()) { "A semantic type must have a qualified name." }
    require(typeParameters.distinct().size == typeParameters.size) {
      "A semantic type must not repeat a type parameter symbol."
    }
    require(membersInSourceOrder.distinct().size == membersInSourceOrder.size) {
      "A semantic type must not repeat a source member symbol."
    }
    require(directInterfaces.map { it.symbol }.distinct().size == directInterfaces.size) {
      "A semantic type must not repeat a direct interface symbol."
    }
  }
}

/**
 * 类型或 callable 声明的泛型参数。
 *
 * [upperBounds] 已完成名称解析和外层类型参数代换；没有显式上界时必须写入内建 Object，
 * 避免后续 overload 与擦除逻辑再次猜测隐式边界。
 */
internal data class JavaSemanticTypeParameterDeclaration(
  val symbol: JavaSymbolId,
  val owner: JavaSymbolId,
  val upperBounds: List<JavaSemanticType>,
) {
  init {
    require(upperBounds.isNotEmpty()) {
      "A semantic type parameter must include its explicit or implicit upper bound."
    }
  }
}

/**
 * 已解析的字段声明。
 *
 * [declarationOrder] 是字段在所属类型全部源码字段中的零基顺序，不因 static/instance 分组而重排。
 */
internal data class JavaSemanticFieldDeclaration(
  val symbol: JavaSymbolId,
  val owner: JavaSymbolId,
  val type: JavaSemanticType,
  val visibility: JavaVisibility,
  val isStatic: Boolean,
  val isFinal: Boolean,
  val declarationOrder: Int,
) {
  init {
    require(declarationOrder >= 0) { "A semantic field declaration order must be non-negative." }
  }
}

/** callable 声明种类；构造器与普通方法共用参数、泛型和 overload 基础设施。 */
internal enum class JavaSemanticCallableKind {
  METHOD,
  CONSTRUCTOR,
}

/**
 * 已解析的方法或构造器声明。
 *
 * [parameters] 与 [parameterTypes] 按源码位置一一对应。[returnType] 对普通方法是声明返回类型；
 * 对构造器是所属类型的声明类型，便于 new 表达式和构造器 binding 共享同一契约。
 * [erasedDescriptor] 是 override、名称改编和运行时分派使用的稳定 Java 擦除 descriptor。
 */
internal data class JavaSemanticCallableDeclaration(
  val symbol: JavaSymbolId,
  val owner: JavaSymbolId,
  val kind: JavaSemanticCallableKind,
  val typeParameters: List<JavaSymbolId>,
  val parameters: List<JavaSymbolId>,
  val parameterTypes: List<JavaSemanticType>,
  val returnType: JavaSemanticType,
  val visibility: JavaVisibility,
  val isStatic: Boolean,
  val isFinal: Boolean,
  val isAbstract: Boolean,
  val erasedDescriptor: String,
  /** callable 可能向调用方传播的受检或非受检异常，已完成类型名称解析。 */
  val thrownTypes: List<JavaSemanticType.Declared> = emptyList(),
) {
  init {
    require(typeParameters.distinct().size == typeParameters.size) {
      "A semantic callable must not repeat a type parameter symbol."
    }
    require(parameters.distinct().size == parameters.size) {
      "A semantic callable must not repeat a parameter symbol."
    }
    require(parameters.size == parameterTypes.size) {
      "Semantic callable parameters and parameter types must have equal sizes."
    }
    require(erasedDescriptor.isNotEmpty()) {
      "A semantic callable must provide its erased descriptor."
    }
    require(
      kind != JavaSemanticCallableKind.CONSTRUCTOR ||
        !isStatic && !isFinal && !isAbstract && returnType is JavaSemanticType.Declared,
    ) {
      "A constructor must be an instance callable returning its owner declaration type."
    }
  }
}

/**
 * try-with-resources 中单个资源的确定关闭协议。
 *
 * [localSymbol] 是资源声明生成的 final 局部变量；[closeCallable] 已完成动态分派与虚槽选择，
 * lowering 不得再次按 `close` 名称查找成员。
 */
internal data class JavaResourceCloseBinding(
  val localSymbol: JavaSymbolId,
  val closeCallable: JavaCallableBinding,
)

/**
 * lambda 表达式已经完成 target typing 后的唯一执行契约。
 *
 * [functionalMethod] 保存完成泛型代换后的 SAM 参数、返回类型和虚槽；[parameterSymbols] 与源码
 * 参数一一对应；[captures] 只包含外围方法的局部变量和参数，并且语义阶段已验证 effectively-final。
 */
internal data class JavaLambdaBinding(
  val targetType: JavaSemanticType.Declared,
  val functionalMethod: JavaCallableBinding,
  val parameterSymbols: List<JavaSymbolId>,
  val captures: List<JavaSymbolId>,
) {
  init {
    require(parameterSymbols.distinct().size == parameterSymbols.size) {
      "A Java lambda must not repeat a parameter symbol."
    }
    require(captures.distinct().size == captures.size) {
      "A Java lambda must not repeat a captured symbol."
    }
  }
}

/** 方法引用在创建函数对象时采用的 receiver/构造形态。 */
internal enum class JavaMethodReferenceKind {
  STATIC,
  BOUND_INSTANCE,
  UNBOUND_INSTANCE,
  CONSTRUCTOR,
}

/**
 * 方法引用完成目标类型与 overload 选择后的执行契约。
 *
 * [argumentConversions] 对应被引用 callable 的显式参数；未绑定实例引用额外使用
 * [receiverConversion] 转换 SAM 第一个参数。[returnConversion] 为空表示 SAM 返回 void，调用结果丢弃。
 */
internal data class JavaMethodReferenceBinding(
  val targetType: JavaSemanticType.Declared,
  val functionalMethod: JavaCallableBinding,
  val referencedCallable: JavaCallableBinding,
  val kind: JavaMethodReferenceKind,
  val argumentConversions: List<JavaSemanticConversion>,
  val receiverConversion: JavaSemanticConversion? = null,
  val returnConversion: JavaSemanticConversion? = null,
) {
  init {
    require(argumentConversions.size == referencedCallable.parameterTypes.size) {
      "A Java method reference must convert every referenced callable parameter."
    }
    require(kind == JavaMethodReferenceKind.UNBOUND_INSTANCE || receiverConversion == null) {
      "Only an unbound instance method reference may convert a SAM receiver parameter."
    }
  }
}

/** 一次编译内稳定的虚方法槽编号；编号不应跨编译请求持久化。 */
internal data class JavaVirtualSlotId(val value: Int) {
  init {
    require(value >= 0) { "A Java virtual slot id must be non-negative." }
  }
}

/**
 * 名称或字段访问最终采用的值读取/写入方式。
 *
 * 类型名仍通过 [JavaSemanticModel.resolvedSymbols] 解析，不进入本枚举；这里仅描述能产生 Java 值
 * 的局部声明和字段，避免 lowering 根据 symbol modifier 或 JavaScript 形态重新猜测。
 */
internal enum class JavaValueAccessKind {
  LOCAL,
  PARAMETER,
  INSTANCE_FIELD,
  STATIC_FIELD,
}

/**
 * 成员访问或调用的 receiver 形态。
 *
 * [NONE] 用于局部值、参数、无运行时 receiver 的非限定 static 调用以及 new；
 * [TYPE_QUALIFIED] 也是编译期限定符，不应在运行时求值。
 */
internal enum class JavaReceiverKind {
  NONE,
  IMPLICIT_THIS,
  EXPLICIT,
  SUPER,
  TYPE_QUALIFIED,
}

/** AST 值表达式到最终声明和 receiver 规则的绑定。 */
internal data class JavaValueAccessBinding(
  val symbol: JavaSymbolId,
  val kind: JavaValueAccessKind,
  val receiverKind: JavaReceiverKind,
) {
  init {
    require(
      kind != JavaValueAccessKind.LOCAL && kind != JavaValueAccessKind.PARAMETER ||
        receiverKind == JavaReceiverKind.NONE,
    ) {
      "Local and parameter accesses must not carry a receiver."
    }
  }
}

/**
 * 已完成泛型代换的方法或构造器绑定。
 *
 * [erasedDescriptor] 在阶段 0 兼容期允许为空；阶段 1 成功模型必须填写。
 * [virtualSlot] 仅在 VIRTUAL/INTERFACE 分派时存在，静态和构造器调用不得依赖虚槽。
 */
internal data class JavaCallableBinding(
  val symbol: JavaSymbolId,
  val dispatch: JavaDispatchKind,
  val parameterTypes: List<JavaSemanticType>,
  val returnType: JavaSemanticType,
  val substitutions: Map<JavaSymbolId, JavaSemanticType>,
  val receiverKind: JavaReceiverKind = JavaReceiverKind.NONE,
  val erasedDescriptor: String? = null,
  val virtualSlot: JavaVirtualSlotId? = null,
)

/** 方法调用在运行时需要采用的分派方式。 */
internal enum class JavaDispatchKind {
  STATIC,
  SPECIAL,
  VIRTUAL,
  INTERFACE,
}

/** 构造器首条委托调用的种类。 */
internal enum class JavaConstructorDelegationKind {
  THIS,
  SUPER,
}

/**
 * 一个构造器到同类或父类构造器的确定委托边。
 *
 * 没有显式 this(...) 或 super(...) 时，[isImplicit] 为 true 且 [invocationNode] 为空；
 * 语义阶段必须在生成成功模型前完成目标构造器 overload 选择和委托环检查。
 */
internal data class JavaConstructorDelegation(
  val sourceConstructor: JavaSymbolId,
  val targetConstructor: JavaSymbolId,
  val kind: JavaConstructorDelegationKind,
  val isImplicit: Boolean,
  val invocationNode: JavaNodeId?,
) {
  init {
    require(!isImplicit || kind == JavaConstructorDelegationKind.SUPER) {
      "Only an implicit super constructor delegation is valid."
    }
    require(isImplicit == (invocationNode == null)) {
      "Implicit constructor delegation must not reference a source invocation node."
    }
  }
}

/** Java 隐式或显式转换，lowering 必须把它变成明确 IR。 */
internal sealed interface JavaSemanticConversion {
  data object Identity : JavaSemanticConversion

  data class PrimitiveWidening(
    val from: JavaAstPrimitiveType,
    val to: JavaAstPrimitiveType,
  ) : JavaSemanticConversion

  /** 仅用于 ++/-- 与复合赋值隐式回写；普通赋值和方法调用绝不开放 primitive narrowing。 */
  data class PrimitiveNarrowing(
    val from: JavaAstPrimitiveType,
    val to: JavaAstPrimitiveType,
  ) : JavaSemanticConversion

  data class ReferenceWidening(
    val from: JavaSemanticType,
    val to: JavaSemanticType,
  ) : JavaSemanticConversion

  data class Boxing(
    val primitive: JavaAstPrimitiveType,
    val boxedType: JavaSymbolId,
  ) : JavaSemanticConversion

  data class Unboxing(
    val boxedType: JavaSymbolId,
    val primitive: JavaAstPrimitiveType,
  ) : JavaSemanticConversion

  /** 多步合法转换按执行顺序冻结，禁止 lowering/backend 再从端点类型猜测中间步骤。 */
  data class Sequence(val steps: List<JavaSemanticConversion>) : JavaSemanticConversion {
    init {
      require(steps.isNotEmpty()) { "A semantic conversion sequence must not be empty." }
      require(steps.none { it is Sequence || it == Identity }) {
        "A semantic conversion sequence must be flat and must not contain identity steps."
      }
    }
  }
}

/**
 * String 拼接单个操作数的 Java 源语言转换类别。
 *
 * 该分类刻意不包含任意引用与数组：primitive 数值和受控 wrapper 已由数值 runtime 冻结格式，
 * 其他对象仍需后续 Object.toString 虚分派契约，不能借用 JavaScript 动态 `+`。
 */
internal enum class JavaStringConversionKind {
  STRING,
  NULL,
  BOOLEAN,
  CHAR,
  INT_LIKE,
  /** long 使用 BigInt 保存，必须显式去掉 JavaScript 的 `n` 表示细节。 */
  LONG,
  /** float 每一步都已按 Java 单精度收敛，字符串化仍由运行时统一处理特殊值。 */
  FLOAT,
  /** double 使用 JavaScript Number，但不能直接泄漏 JavaScript 的 Infinity/NaN 拼写差异。 */
  DOUBLE,
  /** 首批 wrapper 引用按 Java String.valueOf(Object) 处理，null 不触发拆箱。 */
  BOXED,
}

/**
 * 一个 String `+` 或 `+=` 节点两侧已经确定的转换策略。
 *
 * lowering 必须消费该绑定生成显式字符串转换，不能仅根据结果类型重新猜测操作数语义。
 */
internal data class JavaStringConcatenationBinding(
  val leftKind: JavaStringConversionKind,
  val rightKind: JavaStringConversionKind,
)

/** 编译期常量；阶段 0 仅登记保证能无损进入 IR 的类型。 */
internal sealed interface JavaConstantValue {
  data class BooleanValue(val value: Boolean) : JavaConstantValue
  data class IntValue(val value: Int) : JavaConstantValue
  data class LongValue(val value: Long) : JavaConstantValue
  /** 浮点常量统一保存解析后的 double；实际 float 精度由 typed IR 类型和后端 fround 决定。 */
  data class FloatingValue(val value: Double) : JavaConstantValue
  data class StringValue(val value: String) : JavaConstantValue
  data object NullValue : JavaConstantValue
}

/** 增强 for 已决议的运行形态，后端无需从类型名猜测集合类别。 */
internal enum class JavaEnhancedForKind {
  ARRAY,
  LIST,
  SET,
}

/** 增强 for 的元素类型与赋值转换。 */
internal data class JavaEnhancedForBinding(
  val kind: JavaEnhancedForKind,
  val elementType: JavaSemanticType,
  val conversion: JavaSemanticConversion,
)

/** enum 常量字段的稳定 name/ordinal 元数据。 */
internal data class JavaEnumConstantBinding(
  val field: JavaSymbolId,
  val owner: JavaSymbolId,
  val name: String,
  val ordinal: Int,
)

/** 每个 enum 类型合成的四个标准成员；lowering 不按方法名称猜测。 */
internal enum class JavaEnumBuiltinOperation {
  NAME,
  ORDINAL,
  VALUES,
  VALUE_OF,
}

/**
 * AST 的不可变语义 side table。
 *
 * AST 保持源码结构，所有最终绑定和类型结果集中存放于此；JS 后端不得直接读取本模型。
 */
internal data class JavaSemanticModel(
  val ast: JavaAstWorkspace,
  val symbols: Map<JavaSymbolId, JavaSemanticSymbol>,
  val declarations: Map<JavaNodeId, JavaSymbolId>,
  val resolvedSymbols: Map<JavaNodeId, JavaSymbolId>,
  val expressionTypes: Map<JavaNodeId, JavaSemanticType>,
  val conversions: Map<JavaNodeId, JavaSemanticConversion>,
  val selectedCallables: Map<JavaNodeId, JavaCallableBinding>,
  val constants: Map<JavaNodeId, JavaConstantValue>,
  val typeDeclarations: Map<JavaSymbolId, JavaSemanticTypeDeclaration> = emptyMap(),
  val typeParameterDeclarations:
    Map<JavaSymbolId, JavaSemanticTypeParameterDeclaration> = emptyMap(),
  val fieldDeclarations: Map<JavaSymbolId, JavaSemanticFieldDeclaration> = emptyMap(),
  val callableDeclarations: Map<JavaSymbolId, JavaSemanticCallableDeclaration> = emptyMap(),
  val valueAccesses: Map<JavaNodeId, JavaValueAccessBinding> = emptyMap(),
  val virtualSlots: Map<JavaSymbolId, JavaVirtualSlotId> = emptyMap(),
  val overriddenMethods: Map<JavaSymbolId, List<JavaSymbolId>> = emptyMap(),
  val constructorDelegations:
    Map<JavaSymbolId, JavaConstructorDelegation> = emptyMap(),
  val stringConcatenations:
    Map<JavaNodeId, JavaStringConcatenationBinding> = emptyMap(),
  val arrayLengthExpressions: Set<JavaNodeId> = emptySet(),
  /** ++/-- 与 wrapper 复合赋值在算术完成后写回变量所需的装箱转换。 */
  val updateWriteConversions: Map<JavaNodeId, JavaSemanticConversion> = emptyMap(),
  /**
   * 精选类库 symbol 到稳定 builtin 描述的映射。
   *
   * 普通源码声明不会进入此表；lowering 必须通过该表识别内建行为，不能根据限定名猜测。
   */
  val builtinMembers: Map<JavaSymbolId, JavaBuiltinMemberDescriptor> = emptyMap(),
  /** builtin 类型 symbol 的稳定角色，供 lowering/validator 在不读取名称时核对运行时身份。 */
  val builtinTypeRoles: Map<JavaSymbolId, JavaBuiltinTypeRole> = emptyMap(),
  /** 精选 wrapper symbol 到 primitive 的唯一 catalog 映射。 */
  val wrapperPrimitiveTypes: Map<JavaSymbolId, JavaAstPrimitiveType> = emptyMap(),
  /**
   * class/interface 在自身未声明实现时最终继承的 default method。
   *
   * 外层 key 是类型 symbol，内层 key 是虚槽；value 必须指向带可执行方法体的接口方法。
   */
  val interfaceDefaultMethods:
    Map<JavaSymbolId, Map<JavaVirtualSlotId, JavaSymbolId>> = emptyMap(),
  /** EnhancedFor statement node 到已验证迭代协议的绑定。 */
  val enhancedForBindings: Map<JavaNodeId, JavaEnhancedForBinding> = emptyMap(),
  /** multi-catch clause node 到按源码顺序解析后的全部异常类型。 */
  val catchTypes: Map<JavaNodeId, List<JavaSemanticType.Declared>> = emptyMap(),
  /** 资源 AST node 到已验证的 final 局部变量和 close() 分派。 */
  val resourceCloseBindings: Map<JavaNodeId, JavaResourceCloseBinding> = emptyMap(),
  /** lambda expression node 到已决议 SAM、参数与 effectively-final 捕获的绑定。 */
  val lambdaBindings: Map<JavaNodeId, JavaLambdaBinding> = emptyMap(),
  /** 方法引用 expression node 到已决议 SAM、callable 和参数/返回转换。 */
  val methodReferenceBindings: Map<JavaNodeId, JavaMethodReferenceBinding> = emptyMap(),
  /** enum 常量字段 symbol 到声明顺序和标准名称。 */
  val enumConstants: Map<JavaSymbolId, JavaEnumConstantBinding> = emptyMap(),
  /** 合成 enum 标准方法 symbol 到稳定 operation。 */
  val enumBuiltinCallables: Map<JavaSymbolId, JavaEnumBuiltinOperation> = emptyMap(),
  /** enum switch selector 节点；lowering 会读取 ordinal，而非比较对象身份。 */
  val enumSwitchSelectors: Set<JavaNodeId> = emptySet(),
  /** enum switch case 名称节点到已经解析的常量 ordinal。 */
  val enumSwitchLabels: Map<JavaNodeId, Int> = emptyMap(),
) {
  /** 返回表达式的确定类型；缺失结果表示语义阶段违反了完整性契约。 */
  fun requireExpressionType(nodeId: JavaNodeId): JavaSemanticType {
    return expressionTypes[nodeId]
      ?: error("Missing Java semantic type for AST node $nodeId.")
  }

  /** 返回名称或成员访问绑定的符号。 */
  fun requireResolvedSymbol(nodeId: JavaNodeId): JavaSemanticSymbol {
    val symbolId = resolvedSymbols[nodeId]
      ?: error("Missing Java symbol binding for AST node $nodeId.")
    return symbols[symbolId]
      ?: error("Java semantic model does not contain symbol $symbolId.")
  }
}
