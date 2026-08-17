package com.cyxbs.functions.code.language.java.compiler.builtin

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType

/**
 * 精选 Java 类库成员的可观察行为兼容等级。
 *
 * [RESULT_COMPATIBLE] 表示 allowlist 输入上的返回值、副作用与异常目标是 Java 8 一致；
 * [RESTRICTED_COMPATIBLE] 表示签名或输入范围为教学场景收敛后的 Java 8 子集；
 * [COMPILE_TIME_UNSUPPORTED] 预留给需要被 catalog 明确识别、但当前不得进入运行时的 API。
 */
internal enum class JavaBuiltinCompatibility {
  RESULT_COMPATIBLE,
  RESTRICTED_COMPATIBLE,
  COMPILE_TIME_UNSUPPORTED,
}

/** builtin 类型的稳定运行时角色；符号编号每次编译可变，后端不得按限定名猜测。 */
internal enum class JavaBuiltinTypeRole(val boxedPrimitive: JavaAstPrimitiveType? = null) {
  OBJECT,
  STRING,
  SYSTEM,
  MATH,
  PRINT_STREAM,
  INPUT_STREAM,
  NUMBER,
  BOOLEAN(JavaAstPrimitiveType.BOOLEAN),
  BYTE(JavaAstPrimitiveType.BYTE),
  SHORT(JavaAstPrimitiveType.SHORT),
  CHARACTER(JavaAstPrimitiveType.CHAR),
  INTEGER(JavaAstPrimitiveType.INT),
  STRING_BUILDER,
  LIST,
  ARRAY_LIST,
  SET,
  HASH_SET,
  MAP,
  HASH_MAP,
  ITERATOR,
  SCANNER,
  AUTO_CLOSEABLE,
  THROWABLE,
  ERROR,
  EXCEPTION,
  RUNTIME_EXCEPTION,
  ILLEGAL_ARGUMENT_EXCEPTION,
  ILLEGAL_STATE_EXCEPTION,
  NULL_POINTER_EXCEPTION,
  ARITHMETIC_EXCEPTION,
  INDEX_OUT_OF_BOUNDS_EXCEPTION,
  ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION,
  STRING_INDEX_OUT_OF_BOUNDS_EXCEPTION,
  CLASS_CAST_EXCEPTION,
  UNSUPPORTED_OPERATION_EXCEPTION,
  NEGATIVE_ARRAY_SIZE_EXCEPTION,
  ARRAY_STORE_EXCEPTION,
  NO_SUCH_ELEMENT_EXCEPTION,
  INPUT_MISMATCH_EXCEPTION,
}

/**
 * lowering 与后端共同识别的稳定内建操作。
 *
 * operation 按 Java 可观察语义而不是 JavaScript 实现命名，避免后端根据方法名称、参数数量或
 * 生成代码形态重新猜测行为。重载在运行行为不同处使用独立 operation。
 */
internal enum class JavaBuiltinOperation {
  OBJECT_EQUALS,
  OBJECT_HASH_CODE,
  OBJECT_TO_STRING,

  SYSTEM_OUT,
  SYSTEM_ERR,

  PRINTSTREAM_PRINT_BOOLEAN,
  PRINTSTREAM_PRINT_CHAR,
  PRINTSTREAM_PRINT_CHAR_ARRAY,
  PRINTSTREAM_PRINT_INT,
  PRINTSTREAM_PRINT_STRING,
  PRINTSTREAM_PRINT_OBJECT,
  PRINTSTREAM_PRINTLN,
  PRINTSTREAM_PRINTLN_BOOLEAN,
  PRINTSTREAM_PRINTLN_CHAR,
  PRINTSTREAM_PRINTLN_CHAR_ARRAY,
  PRINTSTREAM_PRINTLN_INT,
  PRINTSTREAM_PRINTLN_STRING,
  PRINTSTREAM_PRINTLN_OBJECT,

  STRING_LENGTH,
  STRING_IS_EMPTY,
  STRING_CHAR_AT,
  STRING_EQUALS,
  STRING_SUBSTRING_FROM,
  STRING_SUBSTRING_RANGE,
  STRING_INDEX_OF_CHAR,
  STRING_INDEX_OF_STRING,
  STRING_CONTAINS,
  STRING_STARTS_WITH,
  STRING_ENDS_WITH,

  MATH_ABS_INT,
  MATH_MIN_INT,
  MATH_MAX_INT,

  BOOLEAN_VALUE_OF, BOOLEAN_BOOLEAN_VALUE, BOOLEAN_EQUALS, BOOLEAN_HASH_CODE, BOOLEAN_TO_STRING,
  BYTE_VALUE_OF, BYTE_BYTE_VALUE, BYTE_EQUALS, BYTE_HASH_CODE, BYTE_TO_STRING,
  SHORT_VALUE_OF, SHORT_SHORT_VALUE, SHORT_EQUALS, SHORT_HASH_CODE, SHORT_TO_STRING,
  CHARACTER_VALUE_OF, CHARACTER_CHAR_VALUE, CHARACTER_EQUALS, CHARACTER_HASH_CODE, CHARACTER_TO_STRING,
  INTEGER_VALUE_OF, INTEGER_INT_VALUE, INTEGER_EQUALS, INTEGER_HASH_CODE, INTEGER_TO_STRING,
  NUMBER_INT_VALUE,

  STRING_BUILDER_CONSTRUCT_EMPTY,
  STRING_BUILDER_CONSTRUCT_STRING,
  STRING_BUILDER_APPEND_BOOLEAN,
  STRING_BUILDER_APPEND_CHAR,
  STRING_BUILDER_APPEND_CHAR_ARRAY,
  STRING_BUILDER_APPEND_INT,
  STRING_BUILDER_APPEND_STRING,
  STRING_BUILDER_APPEND_OBJECT,
  STRING_BUILDER_LENGTH,
  STRING_BUILDER_CHAR_AT,
  STRING_BUILDER_SET_CHAR_AT,
  STRING_BUILDER_REVERSE,
  STRING_BUILDER_SUBSTRING_FROM,
  STRING_BUILDER_SUBSTRING_RANGE,
  STRING_BUILDER_TO_STRING,

  ARRAY_LIST_CONSTRUCT,
  HASH_SET_CONSTRUCT,
  HASH_MAP_CONSTRUCT,
  LIST_SIZE, LIST_IS_EMPTY, LIST_ADD, LIST_GET, LIST_SET,
  LIST_REMOVE_INDEX, LIST_REMOVE_OBJECT, LIST_CONTAINS, LIST_INDEX_OF, LIST_CLEAR, LIST_ITERATOR,
  ITERATOR_HAS_NEXT, ITERATOR_NEXT,
  SET_ADD, SET_CONTAINS, SET_REMOVE, SET_SIZE, SET_IS_EMPTY, SET_CLEAR, SET_ITERATOR,
  MAP_PUT, MAP_GET, MAP_GET_OR_DEFAULT, MAP_CONTAINS_KEY, MAP_REMOVE,
  MAP_SIZE, MAP_IS_EMPTY, MAP_CLEAR, MAP_KEY_SET,

  SYSTEM_IN,
  SCANNER_CONSTRUCT_INPUT_STREAM,
  SCANNER_HAS_NEXT,
  SCANNER_NEXT,
  SCANNER_HAS_NEXT_INT,
  SCANNER_NEXT_INT,
  SCANNER_HAS_NEXT_LINE,
  SCANNER_NEXT_LINE,
  SCANNER_CLOSE,

  THROWABLE_GET_MESSAGE,
  THROWABLE_GET_CAUSE,
  THROWABLE_TO_STRING,
  AUTO_CLOSEABLE_CLOSE,

  /** 异常构造器共享行为，实际 Java 类型由 ConstructBuiltin 的 result role 决定。 */
  EXCEPTION_CONSTRUCT_EMPTY,
  EXCEPTION_CONSTRUCT_STRING,
  EXCEPTION_CONSTRUCT_STRING_CAUSE,
}

/** catalog 中与一次编译 symbol 编号无关的类型引用。 */
internal sealed interface JavaBuiltinTypeReference {
  data class Primitive(val kind: JavaAstPrimitiveType) : JavaBuiltinTypeReference
  data class Array(val componentType: JavaBuiltinTypeReference) : JavaBuiltinTypeReference
  data class Declared(
    val qualifiedName: String,
    val arguments: List<JavaBuiltinTypeReference> = emptyList(),
  ) : JavaBuiltinTypeReference
  /** 只在所属 builtin type 的成员或父类签名中解析。 */
  data class TypeParameter(val name: String) : JavaBuiltinTypeReference
  data object Void : JavaBuiltinTypeReference
}

/**
 * 一个没有用户源码声明、但可参与 Java 类型检查的内建类型。
 *
 * [hasDefaultConstructor] 仅保留既有 Object/String 行为；System、Math 和 PrintStream 不会因
 * catalog 登记而意外获得可调用构造器。
 */
internal data class JavaBuiltinTypeDescriptor(
  val qualifiedName: String,
  val directSuperQualifiedName: String?,
  val isFinal: Boolean,
  val role: JavaBuiltinTypeRole,
  val hasDefaultConstructor: Boolean = false,
  val typeParameters: List<String> = emptyList(),
  val directSuperArguments: List<JavaBuiltinTypeReference> = emptyList(),
  /** facade 只参与声明/继承成员代换，不允许 new，也不向用户开放 extends/implements。 */
  val isInterfaceFacade: Boolean = false,
  /** AutoCloseable 等受控接口允许用户类型实现，但仍不允许直接构造。 */
  val allowsUserImplementation: Boolean = false,
)

/**
 * 精选类库中的字段或 callable 描述。
 *
 * 每条描述必须带有唯一可执行语义 [operation] 和兼容等级 [compatibility]。语义分析会为描述
 * 分配本次编译内 symbol，并把 symbol 到本描述的映射冻结进 semantic model。
 */
internal sealed interface JavaBuiltinMemberDescriptor {
  val ownerQualifiedName: String
  val name: String
  val operation: JavaBuiltinOperation
  val compatibility: JavaBuiltinCompatibility

  /** 只读 static 字段；首批仅用于 System.out/err。 */
  data class Field(
    override val ownerQualifiedName: String,
    override val name: String,
    val type: JavaBuiltinTypeReference,
    val isStatic: Boolean,
    val isFinal: Boolean,
    override val operation: JavaBuiltinOperation,
    override val compatibility: JavaBuiltinCompatibility,
  ) : JavaBuiltinMemberDescriptor

  /** 已完成 allowlist 收敛的方法签名；当前首批不包含泛型 callable。 */
  data class Callable(
    override val ownerQualifiedName: String,
    override val name: String,
    val parameterTypes: List<JavaBuiltinTypeReference>,
    val returnType: JavaBuiltinTypeReference,
    val isStatic: Boolean,
    val isFinal: Boolean,
    /** true 时作为 builtin 构造器参与 new overload，不伪造源码构造器。 */
    val isConstructor: Boolean = false,
    /** true 时作为 Object 虚方法族的根参与 override 与动态分派。 */
    val isVirtualRoot: Boolean = false,
    /** builtin 抽象契约没有 JS 函数体，只能经用户 override 或受控实现分派。 */
    val isAbstract: Boolean = false,
    /** 声明可能传播的异常类型，供 checked exception 校验使用。 */
    val thrownTypes: List<JavaBuiltinTypeReference> = emptyList(),
    override val operation: JavaBuiltinOperation,
    override val compatibility: JavaBuiltinCompatibility,
  ) : JavaBuiltinMemberDescriptor
}

/**
 * Java 8 教学运行时的唯一 builtin allowlist。
 *
 * 类型检查、补全适配和运行实现应共享这些稳定描述；这里不伪造 JDK 源码，也不开放未列出的
 * 类或成员。未知 API 因而继续由普通名称/成员解析在 Java 源码位置报告。
 */
internal object JavaBuiltinLibrary {
  private val booleanType = JavaBuiltinTypeReference.Primitive(JavaAstPrimitiveType.BOOLEAN)
  private val charType = JavaBuiltinTypeReference.Primitive(JavaAstPrimitiveType.CHAR)
  private val charArrayType = JavaBuiltinTypeReference.Array(charType)
  private val intType = JavaBuiltinTypeReference.Primitive(JavaAstPrimitiveType.INT)
  private val objectType = JavaBuiltinTypeReference.Declared("java.lang.Object")
  private val stringType = JavaBuiltinTypeReference.Declared("java.lang.String")
  private val printStreamType = JavaBuiltinTypeReference.Declared("java.io.PrintStream")
  private val inputStreamType = JavaBuiltinTypeReference.Declared("java.io.InputStream")
  private val throwableType = JavaBuiltinTypeReference.Declared("java.lang.Throwable")
  private val booleanBoxType = JavaBuiltinTypeReference.Declared("java.lang.Boolean")
  private val byteBoxType = JavaBuiltinTypeReference.Declared("java.lang.Byte")
  private val shortBoxType = JavaBuiltinTypeReference.Declared("java.lang.Short")
  private val characterBoxType = JavaBuiltinTypeReference.Declared("java.lang.Character")
  private val integerBoxType = JavaBuiltinTypeReference.Declared("java.lang.Integer")
  private val stringBuilderType = JavaBuiltinTypeReference.Declared("java.lang.StringBuilder")
  private val eType = JavaBuiltinTypeReference.TypeParameter("E")
  private val kType = JavaBuiltinTypeReference.TypeParameter("K")
  private val vType = JavaBuiltinTypeReference.TypeParameter("V")
  private val iteratorOfE = JavaBuiltinTypeReference.Declared("java.util.Iterator", listOf(eType))
  private val setOfK = JavaBuiltinTypeReference.Declared("java.util.Set", listOf(kType))

  val types: List<JavaBuiltinTypeDescriptor> = listOf(
    JavaBuiltinTypeDescriptor(
      qualifiedName = "java.lang.Object",
      directSuperQualifiedName = null,
      isFinal = false,
      role = JavaBuiltinTypeRole.OBJECT,
      hasDefaultConstructor = true,
    ),
    JavaBuiltinTypeDescriptor(
      qualifiedName = "java.lang.String",
      directSuperQualifiedName = "java.lang.Object",
      isFinal = true,
      role = JavaBuiltinTypeRole.STRING,
      hasDefaultConstructor = true,
    ),
    JavaBuiltinTypeDescriptor(
      qualifiedName = "java.lang.System",
      directSuperQualifiedName = "java.lang.Object",
      isFinal = true,
      role = JavaBuiltinTypeRole.SYSTEM,
    ),
    JavaBuiltinTypeDescriptor(
      qualifiedName = "java.lang.Math",
      directSuperQualifiedName = "java.lang.Object",
      isFinal = true,
      role = JavaBuiltinTypeRole.MATH,
    ),
    JavaBuiltinTypeDescriptor(
      qualifiedName = "java.io.PrintStream",
      directSuperQualifiedName = "java.lang.Object",
      isFinal = false,
      role = JavaBuiltinTypeRole.PRINT_STREAM,
    ),
    JavaBuiltinTypeDescriptor("java.lang.Number", "java.lang.Object", false, JavaBuiltinTypeRole.NUMBER),
    JavaBuiltinTypeDescriptor("java.lang.Boolean", "java.lang.Object", true, JavaBuiltinTypeRole.BOOLEAN),
    JavaBuiltinTypeDescriptor("java.lang.Byte", "java.lang.Number", true, JavaBuiltinTypeRole.BYTE),
    JavaBuiltinTypeDescriptor("java.lang.Short", "java.lang.Number", true, JavaBuiltinTypeRole.SHORT),
    JavaBuiltinTypeDescriptor("java.lang.Character", "java.lang.Object", true, JavaBuiltinTypeRole.CHARACTER),
    JavaBuiltinTypeDescriptor("java.lang.Integer", "java.lang.Number", true, JavaBuiltinTypeRole.INTEGER),
    JavaBuiltinTypeDescriptor("java.lang.StringBuilder", "java.lang.Object", true, JavaBuiltinTypeRole.STRING_BUILDER),
    JavaBuiltinTypeDescriptor(
      "java.util.List", "java.lang.Object", isFinal = false,
      role = JavaBuiltinTypeRole.LIST,
      typeParameters = listOf("E"), isInterfaceFacade = true,
    ),
    JavaBuiltinTypeDescriptor(
      "java.util.ArrayList", "java.util.List", isFinal = false,
      role = JavaBuiltinTypeRole.ARRAY_LIST,
      typeParameters = listOf("E"), directSuperArguments = listOf(eType),
    ),
    JavaBuiltinTypeDescriptor(
      "java.util.Set", "java.lang.Object", isFinal = false,
      role = JavaBuiltinTypeRole.SET,
      typeParameters = listOf("E"), isInterfaceFacade = true,
    ),
    JavaBuiltinTypeDescriptor(
      "java.util.HashSet", "java.util.Set", isFinal = false,
      role = JavaBuiltinTypeRole.HASH_SET,
      typeParameters = listOf("E"), directSuperArguments = listOf(eType),
    ),
    JavaBuiltinTypeDescriptor(
      "java.util.Map", "java.lang.Object", isFinal = false,
      role = JavaBuiltinTypeRole.MAP,
      typeParameters = listOf("K", "V"), isInterfaceFacade = true,
    ),
    JavaBuiltinTypeDescriptor(
      "java.util.HashMap", "java.util.Map", isFinal = false,
      role = JavaBuiltinTypeRole.HASH_MAP,
      typeParameters = listOf("K", "V"), directSuperArguments = listOf(kType, vType),
    ),
    JavaBuiltinTypeDescriptor(
      "java.util.Iterator", "java.lang.Object", isFinal = false,
      role = JavaBuiltinTypeRole.ITERATOR,
      typeParameters = listOf("E"), isInterfaceFacade = true,
    ),
    JavaBuiltinTypeDescriptor("java.io.InputStream", "java.lang.Object", false, JavaBuiltinTypeRole.INPUT_STREAM),
    JavaBuiltinTypeDescriptor(
      "java.lang.AutoCloseable",
      "java.lang.Object",
      isFinal = false,
      role = JavaBuiltinTypeRole.AUTO_CLOSEABLE,
      isInterfaceFacade = true,
      allowsUserImplementation = true,
    ),
    JavaBuiltinTypeDescriptor("java.util.Scanner", "java.lang.AutoCloseable", true, JavaBuiltinTypeRole.SCANNER),
    JavaBuiltinTypeDescriptor("java.lang.Throwable", "java.lang.Object", false, JavaBuiltinTypeRole.THROWABLE),
    JavaBuiltinTypeDescriptor("java.lang.Error", "java.lang.Throwable", false, JavaBuiltinTypeRole.ERROR),
    JavaBuiltinTypeDescriptor("java.lang.Exception", "java.lang.Throwable", false, JavaBuiltinTypeRole.EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.RuntimeException", "java.lang.Exception", false, JavaBuiltinTypeRole.RUNTIME_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.IllegalArgumentException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.ILLEGAL_ARGUMENT_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.IllegalStateException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.ILLEGAL_STATE_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.NullPointerException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.NULL_POINTER_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.ArithmeticException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.ARITHMETIC_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.IndexOutOfBoundsException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.INDEX_OUT_OF_BOUNDS_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.ArrayIndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException", false, JavaBuiltinTypeRole.ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.StringIndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException", false, JavaBuiltinTypeRole.STRING_INDEX_OUT_OF_BOUNDS_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.ClassCastException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.CLASS_CAST_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.UnsupportedOperationException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.UNSUPPORTED_OPERATION_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.NegativeArraySizeException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.NEGATIVE_ARRAY_SIZE_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.lang.ArrayStoreException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.ARRAY_STORE_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.util.NoSuchElementException", "java.lang.RuntimeException", false, JavaBuiltinTypeRole.NO_SUCH_ELEMENT_EXCEPTION),
    JavaBuiltinTypeDescriptor("java.util.InputMismatchException", "java.util.NoSuchElementException", false, JavaBuiltinTypeRole.INPUT_MISMATCH_EXCEPTION),
  )

  /**
   * 由同一 catalog 生成的 builtin 直接父类型角色。
   *
   * 后端用它验证擦除后的引用拓宽，避免另写一份类名或继承表；null 值仅表示 Object 根类型。
   */
  val directSuperRoles: Map<JavaBuiltinTypeRole, JavaBuiltinTypeRole?> = run {
    val rolesByName = types.associate { descriptor -> descriptor.qualifiedName to descriptor.role }
    types.associate { descriptor ->
      descriptor.role to descriptor.directSuperQualifiedName?.let(rolesByName::getValue)
    }
  }

  val members: List<JavaBuiltinMemberDescriptor> = buildList {
    callable(
      owner = "java.lang.Object",
      name = "equals",
      parameters = listOf(objectType),
      operation = JavaBuiltinOperation.OBJECT_EQUALS,
      returnType = booleanType,
      isFinal = false,
      isVirtualRoot = true,
    )
    callable(
      owner = "java.lang.Object",
      name = "hashCode",
      operation = JavaBuiltinOperation.OBJECT_HASH_CODE,
      returnType = intType,
      isFinal = false,
      isVirtualRoot = true,
    )
    callable(
      owner = "java.lang.Object",
      name = "toString",
      operation = JavaBuiltinOperation.OBJECT_TO_STRING,
      returnType = stringType,
      isFinal = false,
      isVirtualRoot = true,
    )

    field(
      owner = "java.lang.System",
      name = "out",
      type = printStreamType,
      operation = JavaBuiltinOperation.SYSTEM_OUT,
    )
    field(
      owner = "java.lang.System",
      name = "err",
      type = printStreamType,
      operation = JavaBuiltinOperation.SYSTEM_ERR,
    )
    field(
      owner = "java.lang.System",
      name = "in",
      type = inputStreamType,
      operation = JavaBuiltinOperation.SYSTEM_IN,
    )

    callable(
      owner = "java.io.PrintStream",
      name = "print",
      parameters = listOf(booleanType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINT_BOOLEAN,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "print",
      parameters = listOf(charType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "print",
      parameters = listOf(charArrayType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR_ARRAY,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "print",
      parameters = listOf(intType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINT_INT,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "print",
      parameters = listOf(stringType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINT_STRING,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "print",
      parameters = listOf(objectType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINT_OBJECT,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = emptyList(),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = listOf(booleanType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN_BOOLEAN,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = listOf(charType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = listOf(charArrayType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR_ARRAY,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = listOf(intType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN_INT,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = listOf(stringType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN_STRING,
    )
    callable(
      owner = "java.io.PrintStream",
      name = "println",
      parameters = listOf(objectType),
      operation = JavaBuiltinOperation.PRINTSTREAM_PRINTLN_OBJECT,
    )

    callable("java.lang.String", "length", operation = JavaBuiltinOperation.STRING_LENGTH, returnType = intType)
    callable(
      "java.lang.String",
      "isEmpty",
      operation = JavaBuiltinOperation.STRING_IS_EMPTY,
      returnType = booleanType,
    )
    callable(
      "java.lang.String",
      "charAt",
      parameters = listOf(intType),
      operation = JavaBuiltinOperation.STRING_CHAR_AT,
      returnType = charType,
    )
    callable(
      "java.lang.String",
      "equals",
      parameters = listOf(objectType),
      operation = JavaBuiltinOperation.STRING_EQUALS,
      returnType = booleanType,
    )
    callable(
      "java.lang.String",
      "substring",
      parameters = listOf(intType),
      operation = JavaBuiltinOperation.STRING_SUBSTRING_FROM,
      returnType = stringType,
    )
    callable(
      "java.lang.String",
      "substring",
      parameters = listOf(intType, intType),
      operation = JavaBuiltinOperation.STRING_SUBSTRING_RANGE,
      returnType = stringType,
    )
    callable(
      "java.lang.String",
      "indexOf",
      parameters = listOf(intType),
      operation = JavaBuiltinOperation.STRING_INDEX_OF_CHAR,
      returnType = intType,
    )
    callable(
      "java.lang.String",
      "indexOf",
      parameters = listOf(stringType),
      operation = JavaBuiltinOperation.STRING_INDEX_OF_STRING,
      returnType = intType,
    )
    callable(
      "java.lang.String",
      "contains",
      parameters = listOf(stringType),
      operation = JavaBuiltinOperation.STRING_CONTAINS,
      returnType = booleanType,
      compatibility = JavaBuiltinCompatibility.RESTRICTED_COMPATIBLE,
    )
    callable(
      "java.lang.String",
      "startsWith",
      parameters = listOf(stringType),
      operation = JavaBuiltinOperation.STRING_STARTS_WITH,
      returnType = booleanType,
    )
    callable(
      "java.lang.String",
      "endsWith",
      parameters = listOf(stringType),
      operation = JavaBuiltinOperation.STRING_ENDS_WITH,
      returnType = booleanType,
    )

    callable(
      owner = "java.lang.Math",
      name = "abs",
      parameters = listOf(intType),
      operation = JavaBuiltinOperation.MATH_ABS_INT,
      returnType = intType,
      isStatic = true,
    )
    callable(
      owner = "java.lang.Math",
      name = "min",
      parameters = listOf(intType, intType),
      operation = JavaBuiltinOperation.MATH_MIN_INT,
      returnType = intType,
      isStatic = true,
    )
    callable(
      owner = "java.lang.Math",
      name = "max",
      parameters = listOf(intType, intType),
      operation = JavaBuiltinOperation.MATH_MAX_INT,
      returnType = intType,
      isStatic = true,
    )

    wrapper(
      owner = "java.lang.Boolean", primitive = booleanType, boxed = booleanBoxType,
      valueOf = JavaBuiltinOperation.BOOLEAN_VALUE_OF,
      primitiveMethod = "booleanValue", primitiveOperation = JavaBuiltinOperation.BOOLEAN_BOOLEAN_VALUE,
      equals = JavaBuiltinOperation.BOOLEAN_EQUALS, hash = JavaBuiltinOperation.BOOLEAN_HASH_CODE,
      toString = JavaBuiltinOperation.BOOLEAN_TO_STRING,
    )
    wrapper(
      owner = "java.lang.Byte", primitive = JavaBuiltinTypeReference.Primitive(JavaAstPrimitiveType.BYTE), boxed = byteBoxType,
      valueOf = JavaBuiltinOperation.BYTE_VALUE_OF,
      primitiveMethod = "byteValue", primitiveOperation = JavaBuiltinOperation.BYTE_BYTE_VALUE,
      equals = JavaBuiltinOperation.BYTE_EQUALS, hash = JavaBuiltinOperation.BYTE_HASH_CODE,
      toString = JavaBuiltinOperation.BYTE_TO_STRING,
    )
    wrapper(
      owner = "java.lang.Short", primitive = JavaBuiltinTypeReference.Primitive(JavaAstPrimitiveType.SHORT), boxed = shortBoxType,
      valueOf = JavaBuiltinOperation.SHORT_VALUE_OF,
      primitiveMethod = "shortValue", primitiveOperation = JavaBuiltinOperation.SHORT_SHORT_VALUE,
      equals = JavaBuiltinOperation.SHORT_EQUALS, hash = JavaBuiltinOperation.SHORT_HASH_CODE,
      toString = JavaBuiltinOperation.SHORT_TO_STRING,
    )
    wrapper(
      owner = "java.lang.Character", primitive = charType, boxed = characterBoxType,
      valueOf = JavaBuiltinOperation.CHARACTER_VALUE_OF,
      primitiveMethod = "charValue", primitiveOperation = JavaBuiltinOperation.CHARACTER_CHAR_VALUE,
      equals = JavaBuiltinOperation.CHARACTER_EQUALS, hash = JavaBuiltinOperation.CHARACTER_HASH_CODE,
      toString = JavaBuiltinOperation.CHARACTER_TO_STRING,
    )
    wrapper(
      owner = "java.lang.Integer", primitive = intType, boxed = integerBoxType,
      valueOf = JavaBuiltinOperation.INTEGER_VALUE_OF,
      primitiveMethod = "intValue", primitiveOperation = JavaBuiltinOperation.INTEGER_INT_VALUE,
      equals = JavaBuiltinOperation.INTEGER_EQUALS, hash = JavaBuiltinOperation.INTEGER_HASH_CODE,
      toString = JavaBuiltinOperation.INTEGER_TO_STRING,
    )
    callable("java.lang.Number", "intValue", operation = JavaBuiltinOperation.NUMBER_INT_VALUE, returnType = intType)

    constructor("java.lang.StringBuilder", emptyList(), JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_EMPTY, stringBuilderType)
    constructor("java.lang.StringBuilder", listOf(stringType), JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING, stringBuilderType)
    callable("java.lang.StringBuilder", "append", listOf(booleanType), JavaBuiltinOperation.STRING_BUILDER_APPEND_BOOLEAN, stringBuilderType)
    callable("java.lang.StringBuilder", "append", listOf(charType), JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR, stringBuilderType)
    callable("java.lang.StringBuilder", "append", listOf(charArrayType), JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY, stringBuilderType)
    callable("java.lang.StringBuilder", "append", listOf(intType), JavaBuiltinOperation.STRING_BUILDER_APPEND_INT, stringBuilderType)
    callable("java.lang.StringBuilder", "append", listOf(stringType), JavaBuiltinOperation.STRING_BUILDER_APPEND_STRING, stringBuilderType)
    callable("java.lang.StringBuilder", "append", listOf(objectType), JavaBuiltinOperation.STRING_BUILDER_APPEND_OBJECT, stringBuilderType)
    callable("java.lang.StringBuilder", "length", operation = JavaBuiltinOperation.STRING_BUILDER_LENGTH, returnType = intType)
    callable("java.lang.StringBuilder", "charAt", listOf(intType), JavaBuiltinOperation.STRING_BUILDER_CHAR_AT, charType)
    callable("java.lang.StringBuilder", "setCharAt", listOf(intType, charType), JavaBuiltinOperation.STRING_BUILDER_SET_CHAR_AT)
    callable("java.lang.StringBuilder", "reverse", operation = JavaBuiltinOperation.STRING_BUILDER_REVERSE, returnType = stringBuilderType)
    callable("java.lang.StringBuilder", "substring", listOf(intType), JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_FROM, stringType)
    callable("java.lang.StringBuilder", "substring", listOf(intType, intType), JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_RANGE, stringType)
    callable("java.lang.StringBuilder", "toString", operation = JavaBuiltinOperation.STRING_BUILDER_TO_STRING, returnType = stringType)

    constructor(
      "java.util.ArrayList", emptyList(), JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT,
      JavaBuiltinTypeReference.Declared("java.util.ArrayList", listOf(eType)),
    )
    constructor(
      "java.util.HashSet", emptyList(), JavaBuiltinOperation.HASH_SET_CONSTRUCT,
      JavaBuiltinTypeReference.Declared("java.util.HashSet", listOf(eType)),
    )
    constructor(
      "java.util.HashMap", emptyList(), JavaBuiltinOperation.HASH_MAP_CONSTRUCT,
      JavaBuiltinTypeReference.Declared("java.util.HashMap", listOf(kType, vType)),
    )

    callable("java.util.List", "size", operation = JavaBuiltinOperation.LIST_SIZE, returnType = intType)
    callable("java.util.List", "isEmpty", operation = JavaBuiltinOperation.LIST_IS_EMPTY, returnType = booleanType)
    callable("java.util.List", "add", listOf(eType), JavaBuiltinOperation.LIST_ADD, booleanType)
    callable("java.util.List", "get", listOf(intType), JavaBuiltinOperation.LIST_GET, eType)
    callable("java.util.List", "set", listOf(intType, eType), JavaBuiltinOperation.LIST_SET, eType)
    callable("java.util.List", "remove", listOf(intType), JavaBuiltinOperation.LIST_REMOVE_INDEX, eType)
    callable("java.util.List", "remove", listOf(objectType), JavaBuiltinOperation.LIST_REMOVE_OBJECT, booleanType)
    callable("java.util.List", "contains", listOf(objectType), JavaBuiltinOperation.LIST_CONTAINS, booleanType)
    callable("java.util.List", "indexOf", listOf(objectType), JavaBuiltinOperation.LIST_INDEX_OF, intType)
    callable("java.util.List", "clear", operation = JavaBuiltinOperation.LIST_CLEAR)
    callable("java.util.List", "iterator", operation = JavaBuiltinOperation.LIST_ITERATOR, returnType = iteratorOfE)

    callable("java.util.Iterator", "hasNext", operation = JavaBuiltinOperation.ITERATOR_HAS_NEXT, returnType = booleanType)
    callable("java.util.Iterator", "next", operation = JavaBuiltinOperation.ITERATOR_NEXT, returnType = eType)

    callable("java.util.Set", "add", listOf(eType), JavaBuiltinOperation.SET_ADD, booleanType)
    callable("java.util.Set", "contains", listOf(objectType), JavaBuiltinOperation.SET_CONTAINS, booleanType)
    callable("java.util.Set", "remove", listOf(objectType), JavaBuiltinOperation.SET_REMOVE, booleanType)
    callable("java.util.Set", "size", operation = JavaBuiltinOperation.SET_SIZE, returnType = intType)
    callable("java.util.Set", "isEmpty", operation = JavaBuiltinOperation.SET_IS_EMPTY, returnType = booleanType)
    callable("java.util.Set", "clear", operation = JavaBuiltinOperation.SET_CLEAR)
    callable("java.util.Set", "iterator", operation = JavaBuiltinOperation.SET_ITERATOR, returnType = iteratorOfE)

    callable("java.util.Map", "put", listOf(kType, vType), JavaBuiltinOperation.MAP_PUT, vType)
    callable("java.util.Map", "get", listOf(objectType), JavaBuiltinOperation.MAP_GET, vType)
    callable("java.util.Map", "getOrDefault", listOf(objectType, vType), JavaBuiltinOperation.MAP_GET_OR_DEFAULT, vType)
    callable("java.util.Map", "containsKey", listOf(objectType), JavaBuiltinOperation.MAP_CONTAINS_KEY, booleanType)
    callable("java.util.Map", "remove", listOf(objectType), JavaBuiltinOperation.MAP_REMOVE, vType)
    callable("java.util.Map", "size", operation = JavaBuiltinOperation.MAP_SIZE, returnType = intType)
    callable("java.util.Map", "isEmpty", operation = JavaBuiltinOperation.MAP_IS_EMPTY, returnType = booleanType)
    callable("java.util.Map", "clear", operation = JavaBuiltinOperation.MAP_CLEAR)
    callable("java.util.Map", "keySet", operation = JavaBuiltinOperation.MAP_KEY_SET, returnType = setOfK)

    constructor(
      "java.util.Scanner", listOf(inputStreamType),
      JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM,
      JavaBuiltinTypeReference.Declared("java.util.Scanner"),
    )
    callable("java.util.Scanner", "hasNext", operation = JavaBuiltinOperation.SCANNER_HAS_NEXT, returnType = booleanType)
    callable("java.util.Scanner", "next", operation = JavaBuiltinOperation.SCANNER_NEXT, returnType = stringType)
    callable("java.util.Scanner", "hasNextInt", operation = JavaBuiltinOperation.SCANNER_HAS_NEXT_INT, returnType = booleanType)
    callable("java.util.Scanner", "nextInt", operation = JavaBuiltinOperation.SCANNER_NEXT_INT, returnType = intType)
    callable("java.util.Scanner", "hasNextLine", operation = JavaBuiltinOperation.SCANNER_HAS_NEXT_LINE, returnType = booleanType)
    callable("java.util.Scanner", "nextLine", operation = JavaBuiltinOperation.SCANNER_NEXT_LINE, returnType = stringType)
    callable("java.util.Scanner", "close", operation = JavaBuiltinOperation.SCANNER_CLOSE)

    callable(
      owner = "java.lang.AutoCloseable",
      name = "close",
      operation = JavaBuiltinOperation.AUTO_CLOSEABLE_CLOSE,
      isFinal = false,
      isVirtualRoot = true,
      isAbstract = true,
      thrownTypes = listOf(JavaBuiltinTypeReference.Declared("java.lang.Exception")),
    )
    callable(
      "java.lang.Throwable",
      "getMessage",
      operation = JavaBuiltinOperation.THROWABLE_GET_MESSAGE,
      returnType = stringType,
      isFinal = false,
      isVirtualRoot = true,
    )
    callable(
      "java.lang.Throwable",
      "getCause",
      operation = JavaBuiltinOperation.THROWABLE_GET_CAUSE,
      returnType = throwableType,
      isFinal = false,
      isVirtualRoot = true,
    )
    callable(
      "java.lang.Throwable",
      "toString",
      operation = JavaBuiltinOperation.THROWABLE_TO_STRING,
      returnType = stringType,
      isFinal = false,
      isVirtualRoot = true,
    )

    // 阶段 2 为异常家族开放空、message 与 message+cause 构造器；共享 operation 由结果 role 区分类型。
    listOf(
      "java.lang.Throwable",
      "java.lang.Error",
      "java.lang.Exception",
      "java.lang.RuntimeException",
      "java.lang.IllegalArgumentException",
      "java.lang.IllegalStateException",
      "java.lang.NullPointerException",
      "java.lang.ArithmeticException",
      "java.lang.IndexOutOfBoundsException",
      "java.lang.ArrayIndexOutOfBoundsException",
      "java.lang.StringIndexOutOfBoundsException",
      "java.lang.ClassCastException",
      "java.lang.UnsupportedOperationException",
      "java.lang.NegativeArraySizeException",
      "java.lang.ArrayStoreException",
      "java.util.NoSuchElementException",
      "java.util.InputMismatchException",
    ).forEach { owner ->
      val exceptionType = JavaBuiltinTypeReference.Declared(owner)
      constructor(owner, emptyList(), JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY, exceptionType)
      constructor(owner, listOf(stringType), JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING, exceptionType)
      constructor(
        owner,
        listOf(stringType, throwableType),
        JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE,
        exceptionType,
      )
    }
  }

  init {
    require(types.map { it.qualifiedName }.distinct().size == types.size) {
      "Java builtin type qualified names must be unique."
    }
    val sharedConstructorOperations = setOf(
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY,
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING,
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE,
    )
    require(
      members.filter { it.operation !in sharedConstructorOperations }
        .map { it.operation }.distinct().size ==
        members.count { it.operation !in sharedConstructorOperations },
    ) {
      "Every non-family Java builtin member overload must have a stable unique operation."
    }
    require(members.map(::signatureKey).distinct().size == members.size) {
      "Java builtin member signatures must be unique."
    }
    val owners = types.mapTo(mutableSetOf()) { it.qualifiedName }
    require(members.all { it.ownerQualifiedName in owners }) {
      "Every Java builtin member owner must be declared by the same catalog."
    }
  }

  /** 生成只用于 catalog 自校验的稳定签名键，不参与 Java overload 或运行时分派。 */
  private fun signatureKey(member: JavaBuiltinMemberDescriptor): String = when (member) {
    is JavaBuiltinMemberDescriptor.Field ->
      "${member.ownerQualifiedName}#${member.name}:field"
    is JavaBuiltinMemberDescriptor.Callable ->
      "${member.ownerQualifiedName}#${member.name}(${member.parameterTypes.joinToString()})"
  }

  private fun MutableList<JavaBuiltinMemberDescriptor>.field(
    owner: String,
    name: String,
    type: JavaBuiltinTypeReference,
    operation: JavaBuiltinOperation,
  ) {
    add(
      JavaBuiltinMemberDescriptor.Field(
        ownerQualifiedName = owner,
        name = name,
        type = type,
        isStatic = true,
        isFinal = true,
        operation = operation,
        compatibility = JavaBuiltinCompatibility.RESULT_COMPATIBLE,
      ),
    )
  }

  private fun MutableList<JavaBuiltinMemberDescriptor>.callable(
    owner: String,
    name: String,
    parameters: List<JavaBuiltinTypeReference> = emptyList(),
    operation: JavaBuiltinOperation,
    returnType: JavaBuiltinTypeReference = JavaBuiltinTypeReference.Void,
    isStatic: Boolean = false,
    compatibility: JavaBuiltinCompatibility = JavaBuiltinCompatibility.RESULT_COMPATIBLE,
    isConstructor: Boolean = false,
    isFinal: Boolean = true,
    isVirtualRoot: Boolean = false,
    isAbstract: Boolean = false,
    thrownTypes: List<JavaBuiltinTypeReference> = emptyList(),
  ) {
    add(
      JavaBuiltinMemberDescriptor.Callable(
        ownerQualifiedName = owner,
        name = name,
        parameterTypes = parameters,
        returnType = returnType,
        isStatic = isStatic,
        isFinal = isFinal,
        isConstructor = isConstructor,
        isVirtualRoot = isVirtualRoot,
        isAbstract = isAbstract,
        thrownTypes = thrownTypes,
        operation = operation,
        compatibility = if (owner.startsWith("java.util.")) {
          // java.util 只开放教学子集：集合不承诺完整哈希/fail-fast；Scanner 只读取预加载输入，
          // nextInt 仅接受 ASCII 十进制 +/-[0-9]，不会把其他 Unicode Nd 数字伪装成完整 Java 解析。
          JavaBuiltinCompatibility.RESTRICTED_COMPATIBLE
        } else {
          compatibility
        },
      ),
    )
  }

  /** 登记 wrapper 的 valueOf、拆箱与 Object 常用可观察方法。 */
  private fun MutableList<JavaBuiltinMemberDescriptor>.wrapper(
    owner: String,
    primitive: JavaBuiltinTypeReference,
    boxed: JavaBuiltinTypeReference,
    valueOf: JavaBuiltinOperation,
    primitiveMethod: String,
    primitiveOperation: JavaBuiltinOperation,
    equals: JavaBuiltinOperation,
    hash: JavaBuiltinOperation,
    toString: JavaBuiltinOperation,
  ) {
    callable(owner, "valueOf", listOf(primitive), valueOf, boxed, isStatic = true)
    callable(owner, primitiveMethod, operation = primitiveOperation, returnType = primitive)
    callable(owner, "equals", listOf(objectType), equals, booleanType)
    callable(owner, "hashCode", operation = hash, returnType = intType)
    callable(owner, "toString", operation = toString, returnType = stringType)
  }

  /** builtin 构造器只进入 new 的构造候选，运行时由 ConstructBuiltin 承载。 */
  private fun MutableList<JavaBuiltinMemberDescriptor>.constructor(
    owner: String,
    parameters: List<JavaBuiltinTypeReference>,
    operation: JavaBuiltinOperation,
    returnType: JavaBuiltinTypeReference,
  ) {
    callable(owner, "<init>", parameters, operation, returnType, isConstructor = true)
  }
}
