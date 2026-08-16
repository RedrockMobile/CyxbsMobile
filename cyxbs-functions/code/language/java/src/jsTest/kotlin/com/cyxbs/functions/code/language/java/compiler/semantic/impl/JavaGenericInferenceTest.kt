package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.semantic.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 阶段 1 泛型方法实参推断的保守边界测试。 */
class JavaGenericInferenceTest {
  /** <T> T id(T) 从唯一实参得到 T=String。 */
  @Test
  fun infersIdentityMethodType() {
    val fixture = fixture()
    assertEquals(
      mapOf(OBJECT_TYPE_PARAMETER to fixture.stringType),
      fixture.inference.infer(
        listOf(OBJECT_TYPE_PARAMETER),
        listOf(JavaSemanticType.TypeVariable(OBJECT_TYPE_PARAMETER)),
        listOf(fixture.stringType),
      ),
    )
  }

  /** 显式类型实参仍必须满足 T extends Base。 */
  @Test
  fun validatesExplicitTypeArgumentBound() {
    val fixture = fixture()
    assertEquals(
      mapOf(BASE_TYPE_PARAMETER to fixture.childType),
      fixture.inference.infer(
        listOf(BASE_TYPE_PARAMETER),
        listOf(JavaSemanticType.TypeVariable(BASE_TYPE_PARAMETER)),
        listOf(fixture.childType),
        listOf(fixture.childType),
      ),
    )
    assertNull(
      fixture.inference.infer(
        listOf(BASE_TYPE_PARAMETER),
        listOf(JavaSemanticType.TypeVariable(BASE_TYPE_PARAMETER)),
        listOf(fixture.otherType),
        listOf(fixture.otherType),
      ),
    )
  }

  /** callable 上界中的 owner 类型变量必须先应用 receiver 已确定的固定代换。 */
  @Test
  fun substitutesOwnerTypeInsideCallableBound() {
    val fixture = fixture()
    val formal = listOf(JavaSemanticType.TypeVariable(DEPENDENT_TYPE_PARAMETER))

    assertEquals(
      mapOf(DEPENDENT_TYPE_PARAMETER to fixture.stringType),
      fixture.inference.infer(
        listOf(DEPENDENT_TYPE_PARAMETER),
        formal,
        listOf(fixture.stringType),
        fixedSubstitutions = mapOf(OWNER_TYPE_PARAMETER to fixture.objectType),
      ),
    )
    assertEquals(
      mapOf(DEPENDENT_TYPE_PARAMETER to fixture.stringType),
      fixture.inference.infer(
        listOf(DEPENDENT_TYPE_PARAMETER),
        formal,
        listOf(fixture.stringType),
        explicitTypeArguments = listOf(fixture.stringType),
        fixedSubstitutions = mapOf(OWNER_TYPE_PARAMETER to fixture.objectType),
      ),
    )
    assertNull(
      fixture.inference.infer(
        listOf(DEPENDENT_TYPE_PARAMETER),
        formal,
        listOf(fixture.otherType),
        explicitTypeArguments = listOf(fixture.otherType),
        fixedSubstitutions = mapOf(OWNER_TYPE_PARAMETER to fixture.childType),
      ),
    )
  }

  /** 只出现在返回类型中的参数没有实参约束，本阶段拒绝 target-only inference。 */
  @Test
  fun rejectsTargetOnlyInference() {
    val fixture = fixture()
    assertNull(
      fixture.inference.infer(
        listOf(OBJECT_TYPE_PARAMETER),
        emptyList(),
        emptyList(),
      ),
    )
  }

  /** 创建带 Base/Child/Other 层级的最小推断环境。 */
  private fun fixture(): Fixture {
    val declarations = mapOf(
      OBJECT to declaration(OBJECT, "java.lang.Object"),
      STRING to declaration(
        STRING,
        "java.lang.String",
        JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
      BASE to declaration(
        BASE,
        "demo.Base",
        JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
      CHILD to declaration(
        CHILD,
        "demo.Child",
        JavaSemanticType.Declared(BASE, emptyList()),
      ),
      OTHER to declaration(
        OTHER,
        "demo.Other",
        JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
    )
    val typeParameters = mapOf(
      OBJECT_TYPE_PARAMETER to JavaSemanticTypeParameterDeclaration(
        OBJECT_TYPE_PARAMETER,
        METHOD_OWNER,
        listOf(JavaSemanticType.Declared(OBJECT, emptyList())),
      ),
      BASE_TYPE_PARAMETER to JavaSemanticTypeParameterDeclaration(
        BASE_TYPE_PARAMETER,
        METHOD_OWNER,
        listOf(JavaSemanticType.Declared(BASE, emptyList())),
      ),
      OWNER_TYPE_PARAMETER to JavaSemanticTypeParameterDeclaration(
        OWNER_TYPE_PARAMETER,
        CLASS_OWNER,
        listOf(JavaSemanticType.Declared(OBJECT, emptyList())),
      ),
      DEPENDENT_TYPE_PARAMETER to JavaSemanticTypeParameterDeclaration(
        DEPENDENT_TYPE_PARAMETER,
        METHOD_OWNER,
        listOf(JavaSemanticType.TypeVariable(OWNER_TYPE_PARAMETER)),
      ),
    )
    val relations = JavaTypeRelations(declarations, typeParameters, OBJECT)
    return Fixture(
      JavaGenericInference(relations, typeParameters),
      JavaSemanticType.Declared(OBJECT, emptyList()),
      JavaSemanticType.Declared(STRING, emptyList()),
      JavaSemanticType.Declared(CHILD, emptyList()),
      JavaSemanticType.Declared(OTHER, emptyList()),
    )
  }

  /** 创建非泛型 class 声明。 */
  private fun declaration(
    symbol: JavaSymbolId,
    name: String,
    parent: JavaSemanticType.Declared? = null,
  ) = JavaSemanticTypeDeclaration(
    symbol,
    name,
    JavaSemanticTypeDeclarationKind.CLASS,
    JavaVisibility.PUBLIC,
    false,
    emptyList(),
    parent,
    emptyList(),
  )

  /** 测试使用的推断器及常用声明类型。 */
  private data class Fixture(
    val inference: JavaGenericInference,
    val objectType: JavaSemanticType.Declared,
    val stringType: JavaSemanticType.Declared,
    val childType: JavaSemanticType.Declared,
    val otherType: JavaSemanticType.Declared,
  )

  private companion object {
    val OBJECT = JavaSymbolId(1)
    val STRING = JavaSymbolId(2)
    val BASE = JavaSymbolId(3)
    val CHILD = JavaSymbolId(4)
    val OTHER = JavaSymbolId(5)
    val METHOD_OWNER = JavaSymbolId(6)
    val CLASS_OWNER = JavaSymbolId(20)
    val OBJECT_TYPE_PARAMETER = JavaSymbolId(7)
    val BASE_TYPE_PARAMETER = JavaSymbolId(8)
    val OWNER_TYPE_PARAMETER = JavaSymbolId(9)
    val DEPENDENT_TYPE_PARAMETER = JavaSymbolId(10)
  }
}
