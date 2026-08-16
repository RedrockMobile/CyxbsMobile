package com.cyxbs.functions.code.language.java.compiler.semantic

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 阶段 1 泛型父类投影、通配符和赋值转换的纯模型测试。 */
class JavaTypeRelationsTest {
  /** Child<T> 到 Parent<T> 再到 Object 的投影必须保留实参。 */
  @Test
  fun projectsGenericSupertypeAcrossMultipleLevels() {
    val fixture = standardFixture()

    assertEquals(
      fixture.declared(PARENT, fixture.stringType),
      fixture.relations.asSupertype(
        fixture.declared(GRAND_CHILD, fixture.stringType),
        PARENT,
      ),
    )
    assertEquals(
      fixture.objectType,
      fixture.relations.asSupertype(
        fixture.declared(GRAND_CHILD, fixture.stringType),
        OBJECT,
      ),
    )
  }

  /** 代换需要递归进入声明实参、数组和通配符边界。 */
  @Test
  fun substitutesNestedGenericTypes() {
    val fixture = standardFixture()
    val source = JavaSemanticType.Array(
      fixture.declared(
        BOX,
        JavaSemanticType.Wildcard(
          upperBound = JavaSemanticType.TypeVariable(CHILD_T),
        ),
      ),
    )

    assertEquals(
      JavaSemanticType.Array(
        fixture.declared(
          BOX,
          JavaSemanticType.Wildcard(upperBound = fixture.stringType),
        ),
      ),
      fixture.relations.substitute(
        source,
        mapOf(CHILD_T to fixture.stringType),
      ),
    )
  }

  /** owner 自类型产生的 T -> T 是恒等代换，字段与方法成员类型必须继续保留 T。 */
  @Test
  fun keepsIdentityTypeVariableSubstitution() {
    val fixture = standardFixture()
    val variable = JavaSemanticType.TypeVariable(CHILD_T)

    assertEquals(
      variable,
      fixture.relations.substitute(variable, mapOf(CHILD_T to variable)),
    )
  }

  /** 跨两个类型变量回到起点是真实递归环，不能误当作恒等代换。 */
  @Test
  fun rejectsIndirectTypeVariableSubstitutionCycle() {
    val fixture = standardFixture()

    assertNull(
      fixture.relations.substitute(
        JavaSemanticType.TypeVariable(CHILD_T),
        mapOf(
          CHILD_T to JavaSemanticType.TypeVariable(GRAND_CHILD_T),
          GRAND_CHILD_T to JavaSemanticType.TypeVariable(CHILD_T),
        ),
      ),
    )
  }

  /** 普通参数化类型保持 invariant，不能把 Parent<String> 当成 Parent<Object>。 */
  @Test
  fun keepsParameterizedTypesInvariant() {
    val fixture = standardFixture()

    assertFalse(
      fixture.relations.isSubtype(
        fixture.declared(PARENT, fixture.stringType),
        fixture.declared(PARENT, fixture.objectType),
      ),
    )
    assertTrue(
      fixture.relations.isSubtype(
        fixture.declared(PARENT, fixture.stringType),
        fixture.declared(PARENT, JavaSemanticType.Wildcard()),
      ),
    )
  }

  /** 目标 extends/super 通配符分别检查上界和下界 containment。 */
  @Test
  fun acceptsBasicExtendsAndSuperWildcards() {
    val fixture = standardFixture()
    val boxOfInteger = fixture.declared(BOX, fixture.integerType)
    val boxOfNumber = fixture.declared(BOX, fixture.numberType)
    val boxOfString = fixture.declared(BOX, fixture.stringType)

    assertTrue(
      fixture.relations.isSubtype(
        boxOfInteger,
        fixture.declared(
          BOX,
          JavaSemanticType.Wildcard(upperBound = fixture.numberType),
        ),
      ),
    )
    assertTrue(
      fixture.relations.isSubtype(
        boxOfNumber,
        fixture.declared(
          BOX,
          JavaSemanticType.Wildcard(lowerBound = fixture.integerType),
        ),
      ),
    )
    assertFalse(
      fixture.relations.isSubtype(
        boxOfString,
        fixture.declared(
          BOX,
          JavaSemanticType.Wildcard(upperBound = fixture.numberType),
        ),
      ),
    )
  }

  /** null 只能拓宽到合法引用类型，不能赋给 primitive。 */
  @Test
  fun convertsNullOnlyToReferenceTypes() {
    val fixture = standardFixture()

    assertEquals(
      JavaSemanticConversion.ReferenceWidening(
        JavaSemanticType.Null,
        fixture.stringType,
      ),
      fixture.relations.assignmentConversion(
        JavaSemanticType.Null,
        fixture.stringType,
      ),
    )
    assertNull(
      fixture.relations.assignmentConversion(
        JavaSemanticType.Null,
        JavaSemanticType.Primitive(JavaAstPrimitiveType.INT),
      ),
    )
  }

  /** primitive widening 遵循 Java 8 表，不接受 narrowing 或 boolean/numeric 混用。 */
  @Test
  fun appliesPrimitiveWideningTable() {
    val fixture = standardFixture()
    val intType = JavaSemanticType.Primitive(JavaAstPrimitiveType.INT)
    val longType = JavaSemanticType.Primitive(JavaAstPrimitiveType.LONG)
    val booleanType = JavaSemanticType.Primitive(JavaAstPrimitiveType.BOOLEAN)

    assertEquals(
      JavaSemanticConversion.PrimitiveWidening(
        JavaAstPrimitiveType.INT,
        JavaAstPrimitiveType.LONG,
      ),
      fixture.relations.assignmentConversion(intType, longType),
    )
    assertNull(fixture.relations.assignmentConversion(longType, intType))
    assertNull(fixture.relations.assignmentConversion(booleanType, intType))
  }

  /** 引用数组保持 Java 协变，primitive 数组只接受完全相同的 component 类型。 */
  @Test
  fun appliesArraySubtypeAndAssignmentRules() {
    val fixture = standardFixture()
    val stringArray = JavaSemanticType.Array(fixture.stringType)
    val objectArray = JavaSemanticType.Array(fixture.objectType)
    val intArray = JavaSemanticType.Array(
      JavaSemanticType.Primitive(JavaAstPrimitiveType.INT),
    )
    val longArray = JavaSemanticType.Array(
      JavaSemanticType.Primitive(JavaAstPrimitiveType.LONG),
    )

    assertTrue(fixture.relations.isSubtype(stringArray, objectArray))
    assertEquals(
      JavaSemanticConversion.ReferenceWidening(stringArray, objectArray),
      fixture.relations.assignmentConversion(stringArray, objectArray),
    )
    assertFalse(fixture.relations.isSubtype(intArray, longArray))
    assertNull(fixture.relations.assignmentConversion(intArray, longArray))
  }

  /** 数组是引用值：可拓宽到 Object，也可接收 null，而无需 boxing。 */
  @Test
  fun convertsArraysAsReferenceValues() {
    val fixture = standardFixture()
    val intArray = JavaSemanticType.Array(
      JavaSemanticType.Primitive(JavaAstPrimitiveType.INT),
    )

    assertEquals(
      JavaSemanticConversion.ReferenceWidening(intArray, fixture.objectType),
      fixture.relations.assignmentConversion(intArray, fixture.objectType),
    )
    assertEquals(
      JavaSemanticConversion.ReferenceWidening(JavaSemanticType.Null, intArray),
      fixture.relations.assignmentConversion(JavaSemanticType.Null, intArray),
    )
  }

  /** 类型变量擦除使用首上界，并去除声明类型的泛型实参。 */
  @Test
  fun erasesTypeVariableToItsFirstUpperBound() {
    val fixture = standardFixture()

    assertEquals(
      JavaSemanticType.Declared(NUMBER, emptyList()),
      fixture.relations.erasure(JavaSemanticType.TypeVariable(NUMBER_T)),
    )
  }

  /** 泛型声明的空实参是 raw type，不能参与成员类型关系或赋值转换。 */
  @Test
  fun rejectsRawTypes() {
    val fixture = standardFixture()
    val rawParent = JavaSemanticType.Declared(PARENT, emptyList())

    assertNull(fixture.relations.asSupertype(rawParent, OBJECT))
    assertFalse(fixture.relations.isSubtype(rawParent, rawParent))
    assertNull(fixture.relations.assignmentConversion(rawParent, rawParent))
  }

  /** 损坏模型中的继承环必须稳定终止而不是无限遍历。 */
  @Test
  fun rejectsInheritanceCycle() {
    val a = JavaSymbolId(100)
    val b = JavaSymbolId(101)
    val declarations = mapOf(
      OBJECT to typeDeclaration(OBJECT, "java.lang.Object"),
      a to typeDeclaration(
        a,
        "cycle.A",
        directSuperClass = JavaSemanticType.Declared(b, emptyList()),
      ),
      b to typeDeclaration(
        b,
        "cycle.B",
        directSuperClass = JavaSemanticType.Declared(a, emptyList()),
      ),
    )
    val relations = JavaTypeRelations(declarations, emptyMap(), OBJECT)

    assertNull(relations.asSupertype(JavaSemanticType.Declared(a, emptyList()), OBJECT))
    assertFalse(
      relations.isSubtype(
        JavaSemanticType.Declared(a, emptyList()),
        JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
    )
  }

  /** 指向不存在声明的父类边必须返回失败，不能用限定名或 symbol 数值猜测。 */
  @Test
  fun rejectsMissingSupertypeDeclaration() {
    val child = JavaSymbolId(110)
    val missing = JavaSymbolId(111)
    val declarations = mapOf(
      OBJECT to typeDeclaration(OBJECT, "java.lang.Object"),
      child to typeDeclaration(
        child,
        "broken.Child",
        directSuperClass = JavaSemanticType.Declared(missing, emptyList()),
      ),
    )
    val relations = JavaTypeRelations(declarations, emptyMap(), OBJECT)

    assertNull(relations.asSupertype(JavaSemanticType.Declared(child, emptyList()), OBJECT))
  }

  /** 构造一组含泛型父类链和常用引用层级的手工语义声明。 */
  private fun standardFixture(): Fixture {
    val declarations = mapOf(
      OBJECT to typeDeclaration(OBJECT, "java.lang.Object"),
      STRING to typeDeclaration(
        STRING,
        "java.lang.String",
        directSuperClass = JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
      NUMBER to typeDeclaration(
        NUMBER,
        "java.lang.Number",
        directSuperClass = JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
      INTEGER to typeDeclaration(
        INTEGER,
        "java.lang.Integer",
        directSuperClass = JavaSemanticType.Declared(NUMBER, emptyList()),
      ),
      PARENT to typeDeclaration(
        PARENT,
        "demo.Parent",
        typeParameters = listOf(PARENT_T),
        directSuperClass = JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
      CHILD to typeDeclaration(
        CHILD,
        "demo.Child",
        typeParameters = listOf(CHILD_T),
        directSuperClass = JavaSemanticType.Declared(
          PARENT,
          listOf(JavaSemanticType.TypeVariable(CHILD_T)),
        ),
      ),
      GRAND_CHILD to typeDeclaration(
        GRAND_CHILD,
        "demo.GrandChild",
        typeParameters = listOf(GRAND_CHILD_T),
        directSuperClass = JavaSemanticType.Declared(
          CHILD,
          listOf(JavaSemanticType.TypeVariable(GRAND_CHILD_T)),
        ),
      ),
      BOX to typeDeclaration(
        BOX,
        "demo.Box",
        typeParameters = listOf(BOX_T),
        directSuperClass = JavaSemanticType.Declared(OBJECT, emptyList()),
      ),
    )
    val typeParameters = mapOf(
      PARENT_T to typeParameter(PARENT_T, PARENT),
      CHILD_T to typeParameter(CHILD_T, CHILD),
      GRAND_CHILD_T to typeParameter(GRAND_CHILD_T, GRAND_CHILD),
      BOX_T to typeParameter(BOX_T, BOX),
      NUMBER_T to JavaSemanticTypeParameterDeclaration(
        symbol = NUMBER_T,
        owner = JavaSymbolId(999),
        upperBounds = listOf(JavaSemanticType.Declared(NUMBER, emptyList())),
      ),
    )
    return Fixture(
      relations = JavaTypeRelations(declarations, typeParameters, OBJECT),
    )
  }

  /** 创建无源码成员的最小类型声明。 */
  private fun typeDeclaration(
    symbol: JavaSymbolId,
    qualifiedName: String,
    typeParameters: List<JavaSymbolId> = emptyList(),
    directSuperClass: JavaSemanticType.Declared? = null,
  ): JavaSemanticTypeDeclaration {
    return JavaSemanticTypeDeclaration(
      symbol = symbol,
      qualifiedName = qualifiedName,
      kind = JavaSemanticTypeDeclarationKind.CLASS,
      visibility = JavaVisibility.PUBLIC,
      isFinal = false,
      typeParameters = typeParameters,
      directSuperClass = directSuperClass,
      membersInSourceOrder = emptyList(),
    )
  }

  /** 创建带隐式 Object 上界的类型参数声明。 */
  private fun typeParameter(
    symbol: JavaSymbolId,
    owner: JavaSymbolId,
  ): JavaSemanticTypeParameterDeclaration {
    return JavaSemanticTypeParameterDeclaration(
      symbol = symbol,
      owner = owner,
      upperBounds = listOf(JavaSemanticType.Declared(OBJECT, emptyList())),
    )
  }

  /** 测试中复用的类型关系及常用语义类型。 */
  private data class Fixture(
    val relations: JavaTypeRelations,
  ) {
    val objectType = JavaSemanticType.Declared(OBJECT, emptyList())
    val stringType = JavaSemanticType.Declared(STRING, emptyList())
    val numberType = JavaSemanticType.Declared(NUMBER, emptyList())
    val integerType = JavaSemanticType.Declared(INTEGER, emptyList())

    /** 按实参创建测试声明类型，避免各断言重复构造列表。 */
    fun declared(
      symbol: JavaSymbolId,
      vararg arguments: JavaSemanticType,
    ): JavaSemanticType.Declared {
      return JavaSemanticType.Declared(symbol, arguments.toList())
    }
  }

  private companion object {
    val OBJECT = JavaSymbolId(1)
    val STRING = JavaSymbolId(2)
    val NUMBER = JavaSymbolId(3)
    val INTEGER = JavaSymbolId(4)
    val PARENT = JavaSymbolId(10)
    val PARENT_T = JavaSymbolId(11)
    val CHILD = JavaSymbolId(20)
    val CHILD_T = JavaSymbolId(21)
    val GRAND_CHILD = JavaSymbolId(30)
    val GRAND_CHILD_T = JavaSymbolId(31)
    val BOX = JavaSymbolId(40)
    val BOX_T = JavaSymbolId(41)
    val NUMBER_T = JavaSymbolId(51)
  }
}
