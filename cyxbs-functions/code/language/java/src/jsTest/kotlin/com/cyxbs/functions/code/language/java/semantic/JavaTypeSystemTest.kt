package com.cyxbs.functions.code.language.java.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证 Java 泛型、通配符、隐式转换和重载选择的轻量类型内核。 */
class JavaTypeSystemTest {

  /** 类型解析应保留嵌套泛型、通配符和数组结构。 */
  @Test
  fun parsesNestedGenericWildcardArray() {
    val type = parseJavaType("Map<String, List<? extends Number>>[]")

    assertEquals("Map<String,List<? extends Number>>[]", type.render())
  }

  /** 类型形参解析应保留多个上界。 */
  @Test
  fun parsesTypeParameterBounds() {
    val parameters = parseJavaTypeParameters("<T extends Number & Comparable<T>, R>")

    assertEquals(listOf("T", "R"), parameters.map(JavaTypeParameter::name))
    assertEquals(listOf("Number", "Comparable<T>"), parameters.first().upperBounds.map(JavaType::render))
  }

  /** extends 通配符允许协变读取，普通泛型仍保持不变。 */
  @Test
  fun wildcardExtendsAcceptsSubtypeArgument() {
    val relations = JavaTypeRelations()
    val integers = parseJavaType("List<Integer>")

    assertTrue(relations.isAssignable(integers, parseJavaType("Collection<? extends Number>")))
    assertFalse(relations.isAssignable(integers, parseJavaType("List<Number>")))
  }

  /** super 通配符允许向 Object 容器写入 String。 */
  @Test
  fun wildcardSuperAcceptsSupertypeArgument() {
    val relations = JavaTypeRelations()

    assertTrue(
      relations.isAssignable(
        parseJavaType("List<Object>"),
        parseJavaType("List<? super String>"),
      ),
    )
  }

  /** 泛型方法应从容器实参推断 T 并应用到返回类型。 */
  @Test
  fun infersGenericMethodReturnType() {
    val candidate = JavaCallableCandidate(
      value = "first",
      typeParameters = parseJavaTypeParameters("<T extends Number>"),
      parameterTypes = listOf(parseJavaType("List<T>")),
      returnType = parseJavaType("T"),
    )

    val match = resolveJavaOverload(
      candidates = listOf(candidate),
      argumentTypes = listOf(parseJavaType("List<Integer>")),
      relations = JavaTypeRelations(),
    )

    assertEquals("Integer", assertNotNull(match).returnType?.render())
  }

  /** 重载应按实参类型选择，而不是按声明顺序选择。 */
  @Test
  fun resolvesOverloadByArgumentType() {
    val candidates = listOf(
      callable("int", "int"),
      callable("String", "String"),
    )

    val match = resolveJavaOverload(candidates, listOf(parseJavaType("String")), JavaTypeRelations())

    assertEquals("String", assertNotNull(match).value)
  }

  /** null 实参应选择继承层级中更具体的引用类型。 */
  @Test
  fun nullSelectsMoreSpecificReferenceOverload() {
    val candidates = listOf(
      callable("Object", "Object"),
      callable("String", "String"),
    )

    val match = resolveJavaOverload(candidates, listOf(JavaType.Null), JavaTypeRelations())

    assertEquals("String", assertNotNull(match).value)
  }

  /** 数值基本类型应遵守 Java 拓宽优先级。 */
  @Test
  fun primitiveWideningSelectsNearestTarget() {
    val candidates = listOf(
      callable("long", "long"),
      callable("double", "double"),
    )

    val match = resolveJavaOverload(candidates, listOf(parseJavaType("int")), JavaTypeRelations())

    assertEquals("long", assertNotNull(match).value)
  }

  /** 基本类型实参在没有拓宽候选时应支持常见装箱重载。 */
  @Test
  fun boxingSelectsWrapperOverObject() {
    val candidates = listOf(
      callable("Object", "Object"),
      callable("Integer", "Integer"),
    )

    val match = resolveJavaOverload(candidates, listOf(parseJavaType("int")), JavaTypeRelations())

    assertEquals("Integer", assertNotNull(match).value)
  }

  /** 用测试标签构造单参数方法签名。 */
  private fun callable(label: String, parameterType: String): JavaCallableCandidate<String> {
    return JavaCallableCandidate(
      value = label,
      typeParameters = emptyList(),
      parameterTypes = listOf(parseJavaType(parameterType)),
      returnType = parseJavaType("void"),
    )
  }
}
