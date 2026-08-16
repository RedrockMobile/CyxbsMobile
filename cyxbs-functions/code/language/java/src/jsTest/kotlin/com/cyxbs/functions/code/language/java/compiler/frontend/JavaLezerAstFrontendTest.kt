package com.cyxbs.functions.code.language.java.compiler.frontend

import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstAssignmentOperator
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstExpression
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstForInitializer
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstStatement
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

  /** 未映射到阶段 0 的 Java 结构必须给出 stable unsupported diagnostic。 */
  @Test
  fun rejectsUnsupportedSyntax() {
    val result = parse("interface Main {}")

    assertFalse(result.isSuccess)
    assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" })
  }

  /** 包裹基础类型的数组、泛型和 vararg 不能被静默擦除成阶段 0 类型。 */
  @Test
  fun rejectsUnsupportedTypeWrappers() {
    listOf(
      "class Main { static int[] value() { return null; } }",
      "class Main { static List<String> value() { return null; } }",
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

  /** 泛型、extends 与 implements 必须保留为显式 unsupported，不能被降成普通 class。 */
  @Test
  fun rejectsUnsupportedClassClauses() {
    listOf(
      "class Main<T> {}",
      "class Main extends Base {} class Base {}",
      "class Main implements Runnable {}",
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
