package com.cyxbs.functions.code.language.java.compiler.semantic

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstWorkspace
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

/** 已完成泛型代换的方法或构造器绑定。 */
internal data class JavaCallableBinding(
  val symbol: JavaSymbolId,
  val dispatch: JavaDispatchKind,
  val parameterTypes: List<JavaSemanticType>,
  val returnType: JavaSemanticType,
  val substitutions: Map<JavaSymbolId, JavaSemanticType>,
)

/** 方法调用在运行时需要采用的分派方式。 */
internal enum class JavaDispatchKind {
  STATIC,
  SPECIAL,
  VIRTUAL,
  INTERFACE,
}

/** Java 隐式或显式转换，lowering 必须把它变成明确 IR。 */
internal sealed interface JavaSemanticConversion {
  data object Identity : JavaSemanticConversion

  data class PrimitiveWidening(
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
}

/** 编译期常量；阶段 0 仅登记保证能无损进入 IR 的类型。 */
internal sealed interface JavaConstantValue {
  data class BooleanValue(val value: Boolean) : JavaConstantValue
  data class IntValue(val value: Int) : JavaConstantValue
  data class LongValue(val value: Long) : JavaConstantValue
  data class StringValue(val value: String) : JavaConstantValue
  data object NullValue : JavaConstantValue
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
