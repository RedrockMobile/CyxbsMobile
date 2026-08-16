package com.cyxbs.functions.code.language.java.compiler.lowering

import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.ir.*
import com.cyxbs.functions.code.language.java.compiler.semantic.*
import com.cyxbs.functions.code.language.java.compiler.source.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 覆盖阶段 0 lowering 的控制流、赋值和拒绝边界。 */
class JavaAstToIrLowererTest {
  /** sum 方法必须保留局部变量、while 和 int descriptor。 */
  @Test
  fun lowersStaticSumMethod() {
    val result = JavaAstToIrLowerer.lower(fixture().model)

    val method = assertNotNull(result.value).classes.single().methods.single()
    assertEquals("(I)I", method.descriptor)
    assertEquals(JavaIrMethodId(2), method.id)
    assertEquals(JavaIrLocalId(4), method.locals.single().id)
    assertTrue(method.body!!.statements.any { it is JavaIrStatement.While })
  }

  /** 复合赋值应成为读取旧 local 的 binary 后再 SetLocal。 */
  @Test
  fun lowersCompoundAssignment() {
    val fixture = fixture()
    val assignment = fixture.assignment
    val result = JavaAstToIrLowerer.lower(fixture.model)
    val method = assertNotNull(result.value).classes.single().methods.single()
    val expression = (method.body!!.statements[1] as JavaIrStatement.While).body
      .let { it as JavaIrStatement.Block }.statements.single() as JavaIrStatement.Expression
    val write = expression.expression as JavaIrExpression.SetLocal

    assertEquals(JavaIrLocalId(4), write.local)
    assertEquals(JavaIrBinaryOperator.ADD, (write.value as JavaIrExpression.Binary).operator)
    assertEquals(assignment.span, write.span)
  }

  /** return 中的 postfix 必须先保存旧值、再写回，最后返回合成 local。 */
  @Test
  fun preservesReturnPostfixValueBeforeWriteBack() {
    val result = JavaAstToIrLowerer.lower(fixture(returnPostfix = true).model)
    val statements = assertNotNull(result.value).classes.single().methods.single().body!!.statements

    assertTrue(statements[2] is JavaIrStatement.DeclareLocal)
    assertTrue(statements[3] is JavaIrStatement.Expression)
    assertTrue(statements[4] is JavaIrStatement.Return)
  }

  /** static 调用必须使用 semantic 已选择的 method ID，而不是由名称反查。 */
  @Test
  fun lowersSelectedStaticCall() {
    val result = JavaAstToIrLowerer.lower(fixture(returnStaticCall = true).model)
    val returned = assertNotNull(result.value).classes.single().methods.single().body!!.statements.last()
      as JavaIrStatement.Return

    assertEquals(JavaIrMethodId(5), (returned.expression as JavaIrExpression.InvokeStatic).method)
  }

  /** TypeName 接收者只用于限定 static 目标，不能被降低为运行时 receiver。 */
  @Test
  fun lowersTypeNameStaticReceiver() {
    val result = JavaAstToIrLowerer.lower(
      fixture(returnStaticCall = true, typeReceiverStaticCall = true).model,
    )
    val returned = assertNotNull(result.value).classes.single().methods.single().body!!.statements.last()
      as JavaIrStatement.Return

    assertEquals(JavaIrMethodId(5), (returned.expression as JavaIrExpression.InvokeStatic).method)
  }

  /** 合成 postfix local 必须避开所有真实 semantic symbol/local ID。 */
  @Test
  fun allocatesPostfixTempOutsideSemanticIds() {
    val method = assertNotNull(
      JavaAstToIrLowerer.lower(fixture(returnPostfix = true).model).value,
    ).classes.single().methods.single()

    assertEquals(JavaIrLocalId(6), method.locals.last().id)
    assertTrue(method.locals.map { it.id }.distinct().size == method.locals.size)
  }

  /** postfix 的语义类型缺失时应返回结构化 failure，不能让 lowering 抛异常。 */
  @Test
  fun failsStructurallyForMissingPostfixType() {
    val result = JavaAstToIrLowerer.lower(
      fixture(returnPostfix = true, omitPostfixType = true).model,
    )

    assertNull(result.value)
    assertTrue(result.diagnostics.any { it.code == "JAVA_LOWERING_INVALID_SEMANTIC_MODEL" })
  }

  /** classic for 必须被规范化为外层 block 内的 while，供后端统一处理。 */
  @Test
  fun normalizesClassicForToWhile() {
    val result = JavaAstToIrLowerer.lower(fixture(withFor = true).model)
    val loopBlock = assertNotNull(result.value).classes.single().methods.single().body!!.statements[1]
      as JavaIrStatement.Block

    assertTrue(loopBlock.statements.single() is JavaIrStatement.While)
  }

  /** 字段声明必须在 lowering 阶段给出结构化错误，不能返回半成品 IR。 */
  @Test
  fun rejectsFieldsWithoutPartialIr() {
    val fixture = fixture(withField = true)
    val result = JavaAstToIrLowerer.lower(fixture.model)

    assertNull(result.value)
    assertTrue(result.diagnostics.any { it.code == "JAVA_LOWERING_UNSUPPORTED" })
  }

  /** 用手工 AST 和 semantic side table 构建最小 static int sum 工作区。 */
  private fun fixture(
    withField: Boolean = false,
    returnPostfix: Boolean = false,
    returnStaticCall: Boolean = false,
    typeReceiverStaticCall: Boolean = false,
    omitPostfixType: Boolean = false,
    withFor: Boolean = false,
  ): Fixture {
    val ids = Ids()
    val intType = JavaSemanticType.Primitive(JavaAstPrimitiveType.INT)
    val boolType = JavaSemanticType.Primitive(JavaAstPrimitiveType.BOOLEAN)
    val limitName = JavaAstExpression.Name(ids.next(), span(), "limit")
    val resultTarget = JavaAstExpression.Name(ids.next(), span(), "result")
    val resultName = JavaAstExpression.Name(ids.next(), span(), "result")
    val postfix = JavaAstExpression.Unary(ids.next(), span(), JavaAstUnaryOperator.POST_INCREMENT, resultName)
    val typeReceiver = JavaAstExpression.Name(ids.next(), span(), "MathUtil")
    val staticCall = JavaAstExpression.MethodInvocation(
      ids.next(), span(), if (typeReceiverStaticCall) typeReceiver else null, "identity", emptyList(), listOf(resultName),
    )
    val zero = JavaAstExpression.Literal(ids.next(), span(), JavaAstLiteralKind.INTEGER, "0")
    val one = JavaAstExpression.Literal(ids.next(), span(), JavaAstLiteralKind.INTEGER, "1")
    val condition = JavaAstExpression.Binary(
      ids.next(), span(), limitName, JavaAstBinaryOperator.GREATER_THAN, zero,
    )
    val assignment = JavaAstExpression.Assignment(
      ids.next(), span(), resultTarget, JavaAstAssignmentOperator.ADD_ASSIGN, limitName,
    )
    val parameter = JavaAstParameter(ids.next(), span(), emptySet(), primitive(ids), "limit", false)
    val declarator = JavaAstVariableDeclarator(ids.next(), span(), "result", zero)
    val loopBody = JavaAstStatement.Block(
      ids.next(),
      span(),
      listOf(JavaAstStatement.Expression(ids.next(), span(), assignment)),
    )
    val loop = if (withFor) {
      JavaAstStatement.For(ids.next(), span(), null, condition, emptyList(), loopBody)
    } else {
      JavaAstStatement.While(ids.next(), span(), condition, loopBody)
    }
    val returnExpression = when {
      returnPostfix -> postfix
      returnStaticCall -> staticCall
      else -> resultName
    }
    val body = JavaAstStatement.Block(
      ids.next(),
      span(),
      listOf(
        JavaAstStatement.VariableDeclaration(ids.next(), span(), emptySet(), primitive(ids), listOf(declarator)),
        loop,
        JavaAstStatement.Return(ids.next(), span(), returnExpression),
      ),
    )
    val method = JavaAstMemberDeclaration.Method(
      ids.next(),
      span(),
      setOf(JavaAstModifier.STATIC),
      emptyList(),
      primitive(ids),
      "sum",
      listOf(parameter),
      body,
    )
    val field = JavaAstMemberDeclaration.Field(
      ids.next(), span(), emptySet(), primitive(ids),
      listOf(JavaAstVariableDeclarator(ids.next(), span(), "bad", one)),
    )
    val type = JavaAstTypeDeclaration(
      ids.next(), span(), JavaAstTypeDeclarationKind.CLASS, emptySet(), "Main",
      emptyList(), null, emptyList(), if (withField) listOf(field, method) else listOf(method),
    )
    val file = JavaSourceFile(JavaSourceFileId(0), "Main.java", "class Main {}")
    val unit = JavaAstCompilationUnit(ids.next(), span(), file, null, emptyList(), listOf(type))
    val symbols = mapOf(
      JavaSymbolId(1) to symbol(1, JavaSymbolKind.TYPE, type.nodeId, null),
      JavaSymbolId(2) to symbol(2, JavaSymbolKind.METHOD, method.nodeId, intType),
      JavaSymbolId(3) to symbol(3, JavaSymbolKind.PARAMETER, parameter.nodeId, intType),
      JavaSymbolId(4) to symbol(4, JavaSymbolKind.LOCAL_VARIABLE, declarator.nodeId, intType),
      JavaSymbolId(5) to symbol(5, JavaSymbolKind.METHOD, method.nodeId, intType),
    )
    val expressionTypes = mutableMapOf<JavaNodeId, JavaSemanticType>(
      limitName.nodeId to intType, resultTarget.nodeId to intType, resultName.nodeId to intType,
      staticCall.nodeId to intType, zero.nodeId to intType, one.nodeId to intType,
      condition.nodeId to boolType, assignment.nodeId to intType,
    ).also { if (!omitPostfixType) it[postfix.nodeId] = intType }
    return Fixture(
      assignment,
      JavaSemanticModel(
        JavaAstWorkspace(listOf(unit)),
        symbols,
        mapOf(type.nodeId to JavaSymbolId(1), method.nodeId to JavaSymbolId(2), parameter.nodeId to JavaSymbolId(3), declarator.nodeId to JavaSymbolId(4)),
        mapOf(
          limitName.nodeId to JavaSymbolId(3),
          resultTarget.nodeId to JavaSymbolId(4),
          resultName.nodeId to JavaSymbolId(4),
          typeReceiver.nodeId to JavaSymbolId(1),
        ),
        expressionTypes,
        emptyMap(),
        mapOf(
          staticCall.nodeId to JavaCallableBinding(
            JavaSymbolId(5),
            JavaDispatchKind.STATIC,
            listOf(intType),
            intType,
            emptyMap(),
          ),
        ),
        mapOf(zero.nodeId to JavaConstantValue.IntValue(0), one.nodeId to JavaConstantValue.IntValue(1)),
      ),
    )
  }

  private fun primitive(ids: Ids) = JavaAstTypeReference.Primitive(ids.next(), span(), JavaAstPrimitiveType.INT)
  private fun symbol(id: Int, kind: JavaSymbolKind, node: JavaNodeId, type: JavaSemanticType?) =
    JavaSemanticSymbol(JavaSymbolId(id), kind, "s$id", null, node, span(), type)
  private fun span() = JavaSourceSpan(JavaSourceFileId(0), 0, 1)

  private data class Fixture(val assignment: JavaAstExpression.Assignment, val model: JavaSemanticModel)
  private class Ids { private var value = 0; fun next() = JavaNodeId(JavaSourceFileId(0), value++) }
}
