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

/** 使用手工完整 SemanticModel 覆盖阶段 1 lowering 与原阶段 0 控制流。 */
class JavaAstToIrLowererTest {
  private fun JavaIrProgram.onlyMethod(): JavaIrMethod =
    classes.single().methods.single { it.kind == JavaIrMethodKind.METHOD }

  /** sum 方法必须保留局部变量、while 和 int descriptor。 */
  @Test
  fun lowersStaticSumMethod() {
    val result = JavaAstToIrLowerer.lower(fixture().model)

    val method = assertNotNull(result.value).onlyMethod()
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
    val method = assertNotNull(result.value).onlyMethod()
    val expression = (method.body!!.statements[1] as JavaIrStatement.While).body
      .let { it as JavaIrStatement.Block }.statements.single() as JavaIrStatement.Expression
    val write = expression.expression as JavaIrExpression.SetLocal

    assertEquals(JavaIrLocalId(4), write.local)
    assertEquals(JavaIrBinaryOperator.ADD, (write.value as JavaIrExpression.Binary).operator)
    assertEquals(assignment.span, write.span)
  }

  /** String `+=` 必须直接消费 semantic 转换类别，不能由 lowering 回推 JavaScript `+`。 */
  @Test
  fun lowersStringCompoundAssignmentFromSemanticBinding() {
    val fixture = fixture()
    val model = fixture.model.copy(
      stringConcatenations = mapOf(
        fixture.assignment.nodeId to JavaStringConcatenationBinding(
          JavaStringConversionKind.INT_LIKE,
          JavaStringConversionKind.INT_LIKE,
        ),
      ),
    )

    val method = assertNotNull(JavaAstToIrLowerer.lower(model).value).onlyMethod()
    val expression = (method.body!!.statements[1] as JavaIrStatement.While).body
      .let { it as JavaIrStatement.Block }.statements.single() as JavaIrStatement.Expression
    assertTrue((expression.expression as JavaIrExpression.SetLocal).value is JavaIrExpression.StringConcat)
  }

  /** return 中的 postfix 必须先保存旧值、再写回，最后返回合成 local。 */
  @Test
  fun preservesReturnPostfixValueBeforeWriteBack() {
    val result = JavaAstToIrLowerer.lower(fixture(returnPostfix = true).model)
    val statements = assertNotNull(result.value).onlyMethod().body!!.statements

    assertTrue(statements[2] is JavaIrStatement.DeclareLocal)
    assertTrue(statements[3] is JavaIrStatement.Expression)
    assertTrue(statements[4] is JavaIrStatement.Return)
  }

  /** static 调用必须使用 semantic 已选择的 method ID，而不是由名称反查。 */
  @Test
  fun lowersSelectedStaticCall() {
    val result = JavaAstToIrLowerer.lower(fixture(returnStaticCall = true).model)
    val returned = assertNotNull(result.value).onlyMethod().body!!.statements.last()
      as JavaIrStatement.Return

    assertEquals(JavaIrMethodId(2), (returned.expression as JavaIrExpression.InvokeStatic).method)
  }

  /** TypeName 接收者只用于限定 static 目标，不能被降低为运行时 receiver。 */
  @Test
  fun lowersTypeNameStaticReceiver() {
    val result = JavaAstToIrLowerer.lower(
      fixture(returnStaticCall = true, typeReceiverStaticCall = true).model,
    )
    val returned = assertNotNull(result.value).onlyMethod().body!!.statements.last()
      as JavaIrStatement.Return

    assertEquals(JavaIrMethodId(2), (returned.expression as JavaIrExpression.InvokeStatic).method)
  }

  /** 合成 postfix local 必须避开所有真实 semantic symbol/local ID。 */
  @Test
  fun allocatesPostfixTempOutsideSemanticIds() {
    val method = assertNotNull(
      JavaAstToIrLowerer.lower(fixture(returnPostfix = true).model).value,
    ).onlyMethod()

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
    val loopBlock = assertNotNull(result.value).onlyMethod().body!!.statements[1]
      as JavaIrStatement.Block

    assertTrue(loopBlock.statements.single() is JavaIrStatement.While)
  }

  /** 字段声明按 semantic declarationOrder 降低并保留 initializer。 */
  @Test
  fun lowersFieldInitializer() {
    val fixture = fixture(withField = true)
    val result = JavaAstToIrLowerer.lower(fixture.model)

    val fields = assertNotNull(result.value).classes.single().fields
    assertEquals(listOf(JavaIrFieldId(6), JavaIrFieldId(7)), fields.map { it.id })
    assertEquals(listOf(false, true), fields.map { it.isStatic })
    assertTrue(fields.all { it.initializer is JavaIrExpression.Constant })
    assertNull(result.value.classes.single().instanceInitializer)
  }

  /** Object 不进入输出类列表，语义合成的 default constructor 仍成为 SPECIAL 构造器。 */
  @Test
  fun lowersRuntimeRootAndDefaultConstructor() {
    val clazz = assertNotNull(JavaAstToIrLowerer.lower(fixture().model).value).classes.single()

    assertNull(clazz.superClass)
    val constructor = clazz.methods.single { it.kind == JavaIrMethodKind.CONSTRUCTOR }
    assertEquals(JavaIrDispatchKind.SPECIAL, constructor.dispatch)
    assertEquals("()V", constructor.descriptor)
    assertTrue(constructor.body!!.statements.isEmpty())
  }

  /** callable descriptor 直接采用语义阶段给出的泛型擦除签名。 */
  @Test
  fun usesSemanticGenericErasureDescriptor() {
    val fixture = fixture()
    val typeParameter = JavaSymbolId(8)
    val objectType = JavaSemanticType.Declared(JavaSymbolId(0), emptyList())
    val callable = fixture.model.callableDeclarations.getValue(JavaSymbolId(2))
    val model = fixture.model.copy(
      symbols = fixture.model.symbols + (
        typeParameter to symbol(8, JavaSymbolKind.TYPE_PARAMETER, callable.parameters.let {
          fixture.model.symbols.getValue(JavaSymbolId(2)).declarationNode
        }, null)
        ),
      callableDeclarations = fixture.model.callableDeclarations + (
        JavaSymbolId(2) to callable.copy(
          typeParameters = listOf(typeParameter),
          parameterTypes = listOf(JavaSemanticType.TypeVariable(typeParameter)),
          returnType = JavaSemanticType.TypeVariable(typeParameter),
          erasedDescriptor = "(Ljava/lang/Object;)Ljava/lang/Object;",
        )
        ),
      typeParameterDeclarations = mapOf(
        typeParameter to JavaSemanticTypeParameterDeclaration(
          typeParameter, JavaSymbolId(2), listOf(objectType),
        ),
      ),
    )

    assertEquals(
      "(Ljava/lang/Object;)Ljava/lang/Object;",
      assertNotNull(JavaAstToIrLowerer.lower(model).value).onlyMethod().descriptor,
    )
  }

  /** virtual slot 和 receiver 形态完全由 selectedCallables/virtualSlots 决定。 */
  @Test
  fun lowersVirtualCallWithSemanticSlot() {
    val fixture = fixture(returnStaticCall = true)
    val declaration = fixture.model.callableDeclarations.getValue(JavaSymbolId(2))
    val callNode = fixture.model.selectedCallables.keys.single()
    val model = fixture.model.copy(
      callableDeclarations = fixture.model.callableDeclarations + (
        JavaSymbolId(2) to declaration.copy(isStatic = false)
        ),
      selectedCallables = mapOf(
        callNode to fixture.model.selectedCallables.getValue(callNode).copy(
          dispatch = JavaDispatchKind.VIRTUAL,
          receiverKind = JavaReceiverKind.IMPLICIT_THIS,
          virtualSlot = JavaVirtualSlotId(7),
        ),
      ),
      virtualSlots = mapOf(JavaSymbolId(2) to JavaVirtualSlotId(7)),
      overriddenMethods = mapOf(JavaSymbolId(2) to emptyList()),
    )

    val returned = assertNotNull(JavaAstToIrLowerer.lower(model).value)
      .onlyMethod().body!!.statements.last() as JavaIrStatement.Return
    assertEquals(7, (returned.expression as JavaIrExpression.InvokeVirtual).virtualSlot)
  }

  /** SPECIAL 普通调用使用 semantic receiverKind，不按源码方法名或 modifier 重选目标。 */
  @Test
  fun lowersSelectedSpecialCall() {
    val fixture = fixture(returnStaticCall = true)
    val declaration = fixture.model.callableDeclarations.getValue(JavaSymbolId(2))
    val callNode = fixture.model.selectedCallables.keys.single()
    val model = fixture.model.copy(
      callableDeclarations = fixture.model.callableDeclarations + (
        JavaSymbolId(2) to declaration.copy(isStatic = false)
        ),
      selectedCallables = mapOf(
        callNode to fixture.model.selectedCallables.getValue(callNode).copy(
          dispatch = JavaDispatchKind.SPECIAL,
          receiverKind = JavaReceiverKind.IMPLICIT_THIS,
        ),
      ),
    )

    val returned = assertNotNull(JavaAstToIrLowerer.lower(model).value)
      .onlyMethod().body!!.statements.last() as JavaIrStatement.Return
    assertTrue(returned.expression is JavaIrExpression.InvokeSpecial)
  }

  /** new 只使用 selected constructor 和 semantic result type，不从源码类型名反查。 */
  @Test
  fun lowersSelectedObjectCreation() {
    val fixture = fixture()
    val unit = fixture.model.ast.units.single()
    val type = unit.types.single()
    val method = type.members.filterIsInstance<JavaAstMemberDeclaration.Method>().single()
    val newNode = JavaNodeId(JavaSourceFileId(0), 999)
    val created = JavaAstExpression.NewObject(
      newNode, span(),
      JavaAstTypeReference.Named(JavaNodeId(JavaSourceFileId(0), 998), span(), "WrongName", emptyList()),
      emptyList(),
    )
    val body = method.body!!
    val changedMethod = method.copy(
      body = body.copy(
        statements = body.statements.dropLast(1) +
          JavaAstStatement.Expression(
            JavaNodeId(JavaSourceFileId(0), 997), span(), created,
          ) +
          body.statements.last(),
      ),
    )
    val changedType = type.copy(members = type.members.map { if (it === method) changedMethod else it })
    val model = fixture.model.copy(
      ast = JavaAstWorkspace(listOf(unit.copy(types = listOf(changedType)))),
      expressionTypes = fixture.model.expressionTypes + (
        newNode to JavaSemanticType.Declared(JavaSymbolId(1), emptyList())
        ),
      selectedCallables = fixture.model.selectedCallables + (
        newNode to JavaCallableBinding(
          JavaSymbolId(5), JavaDispatchKind.SPECIAL, emptyList(),
          JavaSemanticType.Declared(JavaSymbolId(1), emptyList()), emptyMap(),
          receiverKind = JavaReceiverKind.NONE, erasedDescriptor = "()V",
        )
        ),
    )

    val statements = assertNotNull(JavaAstToIrLowerer.lower(model).value).onlyMethod().body!!.statements
    assertTrue((statements[2] as JavaIrStatement.Expression).expression is JavaIrExpression.NewObject)
  }

  /** 显式 this(...) 的首句只按 constructorDelegations 生成，目标是稳定 constructor id。 */
  @Test
  fun lowersExplicitThisConstructorDelegation() {
    val fixture = fixture()
    val unit = fixture.model.ast.units.single()
    val type = unit.types.single()
    val invocationNode = JavaNodeId(JavaSourceFileId(0), 990)
    val constructorNode = JavaNodeId(JavaSourceFileId(0), 991)
    val constructor = JavaAstMemberDeclaration.Constructor(
      constructorNode, span(), emptySet(), emptyList(), "MisleadingName", emptyList(),
      JavaAstStatement.Block(
        JavaNodeId(JavaSourceFileId(0), 992), span(),
        listOf(JavaAstStatement.ConstructorInvocation(
          invocationNode, span(), JavaAstConstructorInvocationKind.SUPER, emptyList(),
        )),
      ),
    )
    val changedType = type.copy(members = type.members + constructor)
    val mainType = JavaSemanticType.Declared(JavaSymbolId(1), emptyList())
    val explicitId = JavaSymbolId(9)
    val typeDeclaration = fixture.model.typeDeclarations.getValue(JavaSymbolId(1))
    val model = fixture.model.copy(
      ast = JavaAstWorkspace(listOf(unit.copy(types = listOf(changedType)))),
      symbols = fixture.model.symbols + (
        explicitId to JavaSemanticSymbol(
          explicitId, JavaSymbolKind.CONSTRUCTOR, "ignored", JavaSymbolId(1),
          constructorNode, span(), mainType,
        )
        ),
      declarations = fixture.model.declarations + (constructorNode to explicitId),
      typeDeclarations = fixture.model.typeDeclarations + (
        JavaSymbolId(1) to typeDeclaration.copy(
          membersInSourceOrder = typeDeclaration.membersInSourceOrder + explicitId,
        )
        ),
      callableDeclarations = fixture.model.callableDeclarations + (
        explicitId to JavaSemanticCallableDeclaration(
          explicitId, JavaSymbolId(1), JavaSemanticCallableKind.CONSTRUCTOR,
          emptyList(), emptyList(), emptyList(), mainType, JavaVisibility.PACKAGE_PRIVATE,
          false, false, false, "()V",
        )
        ),
      constructorDelegations = mapOf(
        explicitId to JavaConstructorDelegation(
          explicitId, JavaSymbolId(5), JavaConstructorDelegationKind.THIS,
          false, invocationNode,
        ),
      ),
    )

    val lowered = assertNotNull(JavaAstToIrLowerer.lower(model).value)
      .classes.single().methods.single { it.id == JavaIrMethodId(9) }
    val delegation = lowered.body!!.statements.single() as JavaIrStatement.ConstructorInvocation
    assertEquals(JavaIrConstructorInvocationKind.THIS, delegation.kind)
    assertEquals(JavaIrMethodId(5), delegation.constructor)
  }

  /** 显式 super(...) 由 semantic edge 指向父类构造器，并保留已擦除父类 id。 */
  @Test
  fun lowersExplicitSuperConstructorDelegation() {
    val fixture = fixture()
    val unit = fixture.model.ast.units.single()
    val invocationNode = JavaNodeId(JavaSourceFileId(0), 960)
    val constructorNode = JavaNodeId(JavaSourceFileId(0), 961)
    val childTypeNode = JavaNodeId(JavaSourceFileId(0), 962)
    val childConstructor = JavaAstMemberDeclaration.Constructor(
      constructorNode, span(), emptySet(), emptyList(), "Child", emptyList(),
      JavaAstStatement.Block(
        JavaNodeId(JavaSourceFileId(0), 963), span(),
        listOf(JavaAstStatement.ConstructorInvocation(
          invocationNode, span(), JavaAstConstructorInvocationKind.THIS, emptyList(),
        )),
      ),
    )
    val childType = JavaAstTypeDeclaration(
      childTypeNode, span(), JavaAstTypeDeclarationKind.CLASS, emptySet(), "Child",
      emptyList(),
      JavaAstTypeReference.Named(
        JavaNodeId(JavaSourceFileId(0), 964), span(), "IgnoredParentName", emptyList(),
      ),
      emptyList(), listOf(childConstructor),
    )
    val childTypeId = JavaSymbolId(10)
    val childConstructorId = JavaSymbolId(11)
    val childSemanticType = JavaSemanticType.Declared(childTypeId, emptyList())
    val model = fixture.model.copy(
      ast = JavaAstWorkspace(listOf(unit.copy(types = unit.types + childType))),
      symbols = fixture.model.symbols + mapOf(
        childTypeId to JavaSemanticSymbol(
          childTypeId, JavaSymbolKind.TYPE, "Child", null, childTypeNode, span(), null,
        ),
        childConstructorId to JavaSemanticSymbol(
          childConstructorId, JavaSymbolKind.CONSTRUCTOR, "Child", childTypeId,
          constructorNode, span(), childSemanticType,
        ),
      ),
      declarations = fixture.model.declarations + mapOf(
        childTypeNode to childTypeId,
        constructorNode to childConstructorId,
      ),
      typeDeclarations = fixture.model.typeDeclarations + (
        childTypeId to JavaSemanticTypeDeclaration(
          childTypeId, "example.Child", JavaSemanticTypeDeclarationKind.CLASS,
          JavaVisibility.PACKAGE_PRIVATE, false, emptyList(),
          JavaSemanticType.Declared(JavaSymbolId(1), emptyList()),
          listOf(childConstructorId),
        )
        ),
      callableDeclarations = fixture.model.callableDeclarations + (
        childConstructorId to JavaSemanticCallableDeclaration(
          childConstructorId, childTypeId, JavaSemanticCallableKind.CONSTRUCTOR,
          emptyList(), emptyList(), emptyList(), childSemanticType,
          JavaVisibility.PACKAGE_PRIVATE, false, false, false, "()V",
        )
        ),
      constructorDelegations = mapOf(
        childConstructorId to JavaConstructorDelegation(
          childConstructorId, JavaSymbolId(5), JavaConstructorDelegationKind.SUPER,
          false, invocationNode,
        ),
      ),
    )

    val child = assertNotNull(JavaAstToIrLowerer.lower(model).value)
      .classes.single { it.id == JavaIrClassId(10) }
    assertEquals(JavaIrClassId(1), child.superClass)
    val delegation = child.methods.single().body!!.statements.single()
      as JavaIrStatement.ConstructorInvocation
    assertEquals(JavaIrConstructorInvocationKind.SUPER, delegation.kind)
    assertEquals(JavaIrMethodId(5), delegation.constructor)
  }

  /** instance field postfix 先 GetField 保存旧值，再 SetField 写回，temp id 避开全部 symbol。 */
  @Test
  fun preservesPostfixFieldValueAndStableTempId() {
    val fixture = fixture(withField = true)
    val unit = fixture.model.ast.units.single()
    val type = unit.types.single()
    val method = type.members.filterIsInstance<JavaAstMemberDeclaration.Method>().single()
    val fieldNode = JavaNodeId(JavaSourceFileId(0), 980)
    val postfixNode = JavaNodeId(JavaSourceFileId(0), 981)
    val fieldName = JavaAstExpression.Name(fieldNode, span(), "notUsedForBinding")
    val postfix = JavaAstExpression.Unary(
      postfixNode, span(), JavaAstUnaryOperator.POST_INCREMENT, fieldName,
    )
    val body = method.body!!
    val changedMethod = method.copy(
      body = body.copy(
        statements = body.statements.dropLast(1) +
          JavaAstStatement.Return(JavaNodeId(JavaSourceFileId(0), 982), span(), postfix),
      ),
    )
    val changedType = type.copy(members = type.members.map { if (it === method) changedMethod else it })
    val methodDeclaration = fixture.model.callableDeclarations.getValue(JavaSymbolId(2))
    val intType = JavaSemanticType.Primitive(JavaAstPrimitiveType.INT)
    val model = fixture.model.copy(
      ast = JavaAstWorkspace(listOf(unit.copy(types = listOf(changedType)))),
      expressionTypes = fixture.model.expressionTypes + mapOf(
        fieldNode to intType, postfixNode to intType,
      ),
      callableDeclarations = fixture.model.callableDeclarations + (
        JavaSymbolId(2) to methodDeclaration.copy(isStatic = false)
        ),
      valueAccesses = fixture.model.valueAccesses + (
        fieldNode to JavaValueAccessBinding(
          JavaSymbolId(6), JavaValueAccessKind.INSTANCE_FIELD,
          JavaReceiverKind.IMPLICIT_THIS,
        )
        ),
    )

    val lowered = assertNotNull(JavaAstToIrLowerer.lower(model).value).onlyMethod()
    val tail = lowered.body!!.statements.takeLast(3)
    assertTrue((tail[0] as JavaIrStatement.DeclareLocal).initializer is JavaIrExpression.GetField)
    assertTrue((tail[1] as JavaIrStatement.Expression).expression is JavaIrExpression.SetField)
    assertEquals(JavaIrLocalId(8), lowered.locals.last().id)
  }

  /** 缺 valueAccess binding 必须稳定失败且不返回半成品。 */
  @Test
  fun failsStructurallyForMissingValueBinding() {
    val fixture = fixture()
    val result = JavaAstToIrLowerer.lower(fixture.model.copy(valueAccesses = emptyMap()))

    assertNull(result.value)
    assertTrue(result.diagnostics.any { it.code == "JAVA_LOWERING_INVALID_SEMANTIC_MODEL" })
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
    val staticField = JavaAstMemberDeclaration.Field(
      ids.next(), span(), setOf(JavaAstModifier.STATIC), primitive(ids),
      listOf(JavaAstVariableDeclarator(ids.next(), span(), "shared", zero)),
    )
    val type = JavaAstTypeDeclaration(
      ids.next(), span(), JavaAstTypeDeclarationKind.CLASS, emptySet(), "Main",
      emptyList(), null, emptyList(),
      if (withField) listOf(field, staticField, method) else listOf(method),
    )
    val file = JavaSourceFile(JavaSourceFileId(0), "Main.java", "class Main {}")
    val unit = JavaAstCompilationUnit(ids.next(), span(), file, null, emptyList(), listOf(type))
    val objectType = JavaSemanticType.Declared(JavaSymbolId(0), emptyList())
    val mainType = JavaSemanticType.Declared(JavaSymbolId(1), emptyList())
    val symbols = mutableMapOf(
      JavaSymbolId(0) to symbol(0, JavaSymbolKind.TYPE, unit.nodeId, null),
      JavaSymbolId(1) to symbol(1, JavaSymbolKind.TYPE, type.nodeId, null),
      JavaSymbolId(2) to symbol(2, JavaSymbolKind.METHOD, method.nodeId, intType),
      JavaSymbolId(3) to symbol(3, JavaSymbolKind.PARAMETER, parameter.nodeId, intType),
      JavaSymbolId(4) to symbol(4, JavaSymbolKind.LOCAL_VARIABLE, declarator.nodeId, intType),
      JavaSymbolId(5) to symbol(5, JavaSymbolKind.CONSTRUCTOR, type.nodeId, mainType),
    )
    if (withField) {
      symbols[JavaSymbolId(6)] = symbol(
        6, JavaSymbolKind.FIELD, field.declarators.single().nodeId, intType,
      )
      symbols[JavaSymbolId(7)] = symbol(
        7, JavaSymbolKind.FIELD, staticField.declarators.single().nodeId, intType,
      )
    }
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
        buildMap {
          put(type.nodeId, JavaSymbolId(1))
          put(method.nodeId, JavaSymbolId(2))
          put(parameter.nodeId, JavaSymbolId(3))
          put(declarator.nodeId, JavaSymbolId(4))
          if (withField) put(field.declarators.single().nodeId, JavaSymbolId(6))
          if (withField) put(staticField.declarators.single().nodeId, JavaSymbolId(7))
        },
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
            JavaSymbolId(2),
            JavaDispatchKind.STATIC,
            listOf(intType),
            intType,
            emptyMap(),
            receiverKind = if (typeReceiverStaticCall) {
              JavaReceiverKind.TYPE_QUALIFIED
            } else {
              JavaReceiverKind.NONE
            },
            erasedDescriptor = "(I)I",
          ),
        ),
        mapOf(zero.nodeId to JavaConstantValue.IntValue(0), one.nodeId to JavaConstantValue.IntValue(1)),
        typeDeclarations = mapOf(
          JavaSymbolId(0) to JavaSemanticTypeDeclaration(
            JavaSymbolId(0), "java.lang.Object", JavaSemanticTypeDeclarationKind.BUILTIN,
            JavaVisibility.PUBLIC, false, emptyList(), null, emptyList(),
          ),
          JavaSymbolId(1) to JavaSemanticTypeDeclaration(
            JavaSymbolId(1), "Main", JavaSemanticTypeDeclarationKind.CLASS,
            JavaVisibility.PACKAGE_PRIVATE, false, emptyList(), objectType,
            if (withField) {
              listOf(JavaSymbolId(6), JavaSymbolId(7), JavaSymbolId(2))
            } else {
              listOf(JavaSymbolId(2))
            },
          ),
        ),
        fieldDeclarations = if (withField) mapOf(
          JavaSymbolId(6) to JavaSemanticFieldDeclaration(
            JavaSymbolId(6), JavaSymbolId(1), intType, JavaVisibility.PACKAGE_PRIVATE,
            false, false, 0,
          ),
          JavaSymbolId(7) to JavaSemanticFieldDeclaration(
            JavaSymbolId(7), JavaSymbolId(1), intType, JavaVisibility.PACKAGE_PRIVATE,
            true, false, 1,
          ),
        ) else emptyMap(),
        callableDeclarations = mapOf(
          JavaSymbolId(2) to JavaSemanticCallableDeclaration(
            JavaSymbolId(2), JavaSymbolId(1), JavaSemanticCallableKind.METHOD,
            emptyList(), listOf(JavaSymbolId(3)), listOf(intType), intType,
            JavaVisibility.PACKAGE_PRIVATE, true, false, false, "(I)I",
          ),
          JavaSymbolId(5) to JavaSemanticCallableDeclaration(
            JavaSymbolId(5), JavaSymbolId(1), JavaSemanticCallableKind.CONSTRUCTOR,
            emptyList(), emptyList(), emptyList(), mainType,
            JavaVisibility.PACKAGE_PRIVATE, false, false, false, "()V",
          ),
        ),
        valueAccesses = mapOf(
          limitName.nodeId to JavaValueAccessBinding(
            JavaSymbolId(3), JavaValueAccessKind.PARAMETER, JavaReceiverKind.NONE,
          ),
          resultTarget.nodeId to JavaValueAccessBinding(
            JavaSymbolId(4), JavaValueAccessKind.LOCAL, JavaReceiverKind.NONE,
          ),
          resultName.nodeId to JavaValueAccessBinding(
            JavaSymbolId(4), JavaValueAccessKind.LOCAL, JavaReceiverKind.NONE,
          ),
        ),
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
