package com.cyxbs.functions.code.language.java.compiler.frontend

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstAssignmentOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstConstructorInvocationKind
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstExpression
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstForInitializer
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstMemberDeclaration
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstStatement
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstTypeDeclarationKind
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstTypeReference
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 验证 Lezer CST 到阶段 0 规范化 AST 的严格边界。 */
class JavaLezerAstFrontendTest {

  /** 阶段 0 样例必须保留 classic for、比较、后缀递增与复合赋值结构。 */
  @Test
  fun parsesStageZeroSumProgram() {
    val result = parse(
      """
      class Main {
        static int sum(int limit) {
          int result = 0;
          for (int i = 0; i < limit; i++) {
            result += i;
          }
          return result;
        }
      }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.joinToString())
    val method = assertNotNull(result.value).units.single().types.single().members.single()
    val staticMethod = assertIs<com.cyxbs.functions.code.language.java.compiler.ast.JavaAstMemberDeclaration.Method>(method)
    assertEquals(JavaAstPrimitiveType.INT, assertIs<JavaAstTypeReference.Primitive>(staticMethod.returnType).kind)
    val statements = assertNotNull(staticMethod.body).statements
    val loop = assertIs<JavaAstStatement.For>(statements[1])
    assertIs<JavaAstForInitializer.VariableDeclaration>(loop.initializer)
    assertIs<JavaAstExpression.Binary>(assertNotNull(loop.condition))
    assertIs<JavaAstExpression.Unary>(loop.updates.single())
    val update = assertIs<JavaAstStatement.Expression>(assertIs<JavaAstStatement.Block>(loop.body).statements.single())
    assertEquals(JavaAstAssignmentOperator.ADD_ASSIGN, assertIs<JavaAstExpression.Assignment>(update.expression).operator)
  }

  /** Lezer 恢复节点必须阻止产生可进入语义阶段的半成品 AST。 */
  @Test
  fun rejectsLezerRecoveryTree() {
    val result = parse("class Main { static int sum() { int value = ; } }")

    assertFalse(result.isSuccess)
    assertTrue(result.value == null)
    assertTrue(result.diagnostics.any { it.code == "java.syntax.recovery" })
  }

  /** AST span 必须仍是 UTF-16 偏移，不能因为 emoji 改用 code point。 */
  @Test
  fun preservesUtf16Spans() {
    val source = "// 😀\nclass Main { static int value() { return 1; } }"

    val result = parse(source)

    assertTrue(result.isSuccess, result.diagnostics.joinToString())
    val unit = assertNotNull(result.value).units.single()
    assertEquals(0, unit.span.from)
    assertEquals(source.length, unit.span.to)
    assertTrue(unit.types.single().span.from == source.indexOf("class"))
  }

  /** 未映射到阶段 1 的 Java 结构必须给出 stable unsupported diagnostic。 */
  @Test
  fun rejectsUnsupportedSyntax() {
    val result = parse("enum Main { VALUE }")

    assertFalse(result.isSuccess)
    assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" })
  }

  /** 阶段 1 继续拒绝数组和 vararg，不能把它们静默降成普通类型或参数。 */
  @Test
  fun rejectsUnsupportedTypeWrappers() {
    listOf(
      "class Main { static int[] value() { return null; } }",
      "class Main { static int value(int... items) { return 0; } }",
    ).forEach { source ->
      val result = parse(source)
      assertFalse(result.isSuccess, source)
      assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" }, source)
    }
  }

  /** 未开放语句必须整体拒绝，不能递归提取其内部 block 后改变控制流语义。 */
  @Test
  fun rejectsUnsupportedStatementWrappers() {
    listOf(
      "class Main { static int value() { try { return 1; } finally { return 2; } } }",
      "class Main { static int value() { synchronized (this) { return 1; } } }",
    ).forEach { source ->
      val result = parse(source)
      assertFalse(result.isSuccess, source)
      assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" }, source)
    }
  }

  /** 嵌套类型的成员不能被吸收到外层类型。 */
  @Test
  fun rejectsNestedTypeInsteadOfFlatteningMembers() {
    val result = parse(
      "class Main { static class Inner { static int value() { return 1; } } }",
    )

    assertFalse(result.isSuccess)
    assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" })
  }

  /**
   * 真实 @lezer/java CST 必须完整映射阶段 1 的类型参数、继承、字段、构造器、interface 与泛型调用。
   *
   * 该用例不构造手写 CST，而是经动态 npm 中的 parser.parse 进入 adapter，用于锁定 CST 节点契约。
   */
  @Test
  fun mapsStageOneDeclarationsFromRealLezerCst() {
    val result = parse(
      """
      class Child<T extends Base & Marker> extends Base implements Marker {
        Box<String> field = new Box<>();
        Box<? super Base> lower;
        Child(int value) { super(value); }
        @Override public <R extends Base> R convert(R value) { return value; }
        int inherited() { return super.value(); }
        static <T> T pick(T value) { return value; }
        static String run() { return Child.<String>pick("ok"); }
      }
      interface Marker<T> extends Parent<T> { T map(T value); }
      """.trimIndent(),
    )

    assertTrue(result.isSuccess, result.diagnostics.joinToString())
    val unit = assertNotNull(result.value).units.single()
    val child = unit.types.first()
    assertEquals(JavaAstTypeDeclarationKind.CLASS, child.kind)
    assertEquals("T", child.typeParameters.single().name)
    assertEquals(2, child.typeParameters.single().upperBounds.size)
    assertEquals("Base", assertIs<JavaAstTypeReference.Named>(assertNotNull(child.superClass)).qualifiedName)
    assertEquals("Marker", assertIs<JavaAstTypeReference.Named>(child.interfaces.single()).qualifiedName)
    val field = assertIs<JavaAstMemberDeclaration.Field>(child.members[0])
    val fieldType = assertIs<JavaAstTypeReference.Named>(field.type)
    assertEquals("Box", fieldType.qualifiedName)
    assertEquals("String", assertIs<JavaAstTypeReference.Named>(fieldType.arguments.single()).qualifiedName)
    assertTrue(assertIs<JavaAstExpression.NewObject>(assertNotNull(field.declarators.single().initializer)).type.let {
      assertIs<JavaAstTypeReference.Named>(it).usesDiamond
    })
    val lower = assertIs<JavaAstMemberDeclaration.Field>(child.members[1])
    val wildcard = assertIs<JavaAstTypeReference.Wildcard>(
      assertIs<JavaAstTypeReference.Named>(lower.type).arguments.single(),
    )
    assertEquals("Base", assertIs<JavaAstTypeReference.Named>(assertNotNull(wildcard.lowerBound)).qualifiedName)
    val constructor = child.members.filterIsInstance<JavaAstMemberDeclaration.Constructor>().single()
    assertEquals(JavaAstConstructorInvocationKind.SUPER, assertIs<JavaAstStatement.ConstructorInvocation>(constructor.body.statements.single()).kind)
    val methods = child.members.filterIsInstance<JavaAstMemberDeclaration.Method>()
    val override = methods.first { it.name == "convert" }
    assertEquals(listOf("Override"), override.annotations.map { it.qualifiedName })
    assertEquals("R", override.typeParameters.single().name)
    val inherited = methods.first { it.name == "inherited" }
    val inheritedCall = assertIs<JavaAstExpression.MethodInvocation>(
      assertIs<JavaAstStatement.Return>(assertNotNull(inherited.body).statements.single()).expression,
    )
    assertIs<JavaAstExpression.Super>(assertNotNull(inheritedCall.receiver))
    val run = methods.first { it.name == "run" }
    val invocation = assertIs<JavaAstExpression.MethodInvocation>(
      assertIs<JavaAstStatement.Return>(assertNotNull(run.body).statements.single()).expression,
    )
    assertEquals("String", assertIs<JavaAstTypeReference.Named>(invocation.typeArguments.single()).qualifiedName)
    val marker = unit.types.last()
    assertEquals(JavaAstTypeDeclarationKind.INTERFACE, marker.kind)
    assertEquals("Parent", assertIs<JavaAstTypeReference.Named>(marker.interfaces.single()).qualifiedName)
    assertTrue(assertIs<JavaAstMemberDeclaration.Method>(marker.members.single()).body == null)
  }

  /** 真实 CST 中的未开放 Java 8 或阶段 2A 结构必须被稳定拒绝，不能在 adapter 中消失。 */
  @Test
  fun rejectsStageOneExcludedSyntaxWithoutErasure() {
    listOf(
      "class Main { int value() throws Exception { return 1; } }",
      "class Main { @Deprecated int value() { return 1; } }",
      "class Main { strictfp int value() { return 1; } }",
      "public public class Main { }",
      "class Main { Object value = new Object() { }; }",
      "class Main { static { } }",
    ).forEach { source ->
      val result = parse(source)
      assertFalse(result.isSuccess, source)
      assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" }, source)
    }
  }

  /** 构造唯一文件编号和规范化工作区路径。 */
  private fun parse(source: String) = JavaLezerAstFrontend.parse(
    JavaSourceWorkspace(listOf(JavaSourceFile(JavaSourceFileId(0), "Main.java", source))),
  )
}
