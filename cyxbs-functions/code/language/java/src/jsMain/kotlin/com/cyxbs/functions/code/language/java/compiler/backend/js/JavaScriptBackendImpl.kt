package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.JavaCompilerEntryPoint
import com.cyxbs.functions.code.language.java.compiler.JavaScriptBackend
import com.cyxbs.functions.code.language.java.compiler.JavaScriptModuleArtifact
import com.cyxbs.functions.code.language.java.compiler.JavaScriptProgramArtifact
import com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinLibrary
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinOperation
import com.cyxbs.functions.code.language.java.compiler.builtin.JavaBuiltinTypeRole
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerDiagnostic
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticSeverity
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClass
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClassId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstant
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConversion
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrDispatchKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrExpression
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrEnhancedForKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrField
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrFieldId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocal
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocalId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethod
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrProgram
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrResource
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStatement
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstructorInvocationKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrType
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrTypeDeclarationKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrArrayReferenceComponentKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrUnaryOperator
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi

/**
 * Stage1 typed IR 到 ES Module 的 JavaScript 后端。
 *
 * 本实现只观察 [JavaIrProgram]；所有名称解析、类型推断和 CST/源码读取必须在 lowering 前结束。
 * 生成前会完整校验支持范围，因此失败时绝不返回可能执行错误的半成品 JavaScript。
 */
internal object JavaScriptBackendImpl : JavaScriptBackend {
  override fun generate(
    program: JavaIrProgram,
    entryPoint: JavaCompilerEntryPoint,
  ): JavaCompilerPhaseResult<JavaScriptProgramArtifact> {
    val model = JavaIrIndex(program)
    val diagnostics = JavaScriptBackendValidator(model, entryPoint).validate()
    if (diagnostics.any { diagnostic -> diagnostic.severity == JavaDiagnosticSeverity.ERROR }) {
      return JavaCompilerPhaseResult.failure(diagnostics)
    }
    return JavaCompilerPhaseResult.success(
      JavaScriptEmitter(model, entryPoint).emit(),
      diagnostics,
    )
  }
}

/** 将 IR 编号建立为只读索引，后端不再根据 Java 源码名称猜测任何绑定。 */
private class JavaIrIndex(
  val program: JavaIrProgram,
) {
  val classes: Map<JavaIrClassId, JavaIrClass> = program.classes.associateBy(JavaIrClass::id)
  val methods: Map<JavaIrMethodId, JavaIrMethod> = program.classes
    .flatMap(JavaIrClass::methods)
    .associateBy(JavaIrMethod::id)
  val fields: Map<JavaIrFieldId, JavaIrField> = program.classes
    .flatMap(JavaIrClass::fields)
    .associateBy(JavaIrField::id)
  val locals: Map<JavaIrLocalId, JavaIrLocal> = program.classes
    .flatMap(JavaIrClass::methods)
    .flatMap { method -> method.parameters + method.locals }
    .associateBy(JavaIrLocal::id)

  /** 返回方法所属类；所有实例/静态操作均以此消除源码名称依赖。 */
  fun ownerOf(method: JavaIrMethodId): JavaIrClass? = methods[method]?.let { classes[it.owner] }
}

/** 预检 IR，拒绝尚未具备 Java 语义实现的节点、类型或不一致引用。 */
private class JavaScriptBackendValidator(
  private val index: JavaIrIndex,
  private val entryPoint: JavaCompilerEntryPoint,
) {
  private val diagnostics = mutableListOf<JavaCompilerDiagnostic>()
  private val virtualSlotRoots = mutableMapOf<Int, JavaIrMethod>()

  /** 返回全部错误；调用方在诊断存在时不会创建 emitter。 */
  fun validate(): List<JavaCompilerDiagnostic> {
    if (index.classes.size != index.program.classes.size) {
      invalid("Java IR contains duplicate class ids.", null)
    }
    if (index.methods.size != index.program.classes.sumOf { it.methods.size }) {
      invalid("Java IR contains duplicate method ids.", null)
    }
    if (index.fields.size != index.program.classes.sumOf { it.fields.size }) {
      invalid("Java IR contains duplicate field ids.", null)
    }
    validateBuiltinVirtualSlots()
    index.program.classes.sortedBy { it.id.value }.forEach(::validateClass)
    index.program.classes.forEach { clazz -> validateHierarchyAcyclic(clazz) }
    validateEntryPoint()
    return diagnostics.toList()
  }

  /** Object 三个基础虚槽必需；后续 builtin 虚方法根可增量加入，但仍须唯一且来自 catalog。 */
  private fun validateBuiltinVirtualSlots() {
    val required = setOf(
      JavaBuiltinOperation.OBJECT_EQUALS,
      JavaBuiltinOperation.OBJECT_HASH_CODE,
      JavaBuiltinOperation.OBJECT_TO_STRING,
    )
    val slots = index.program.builtinVirtualSlots
    if (!slots.keys.containsAll(required) || slots.keys.any { !it.isBuiltinVirtualOperation() } ||
      slots.values.any { it < 0 }
    ) {
      invalid("Java IR builtin virtual slot table is incomplete or inconsistent.", null)
    }
  }

  /** 校验 class/interface 关系、成员归属和已决议 default method 映射。 */
  private fun validateClass(clazz: JavaIrClass) {
    clazz.superClass?.let { parent -> if (index.classes[parent] == null) invalid("Java IR references an unknown superclass id ${parent.value}.", clazz.span) }
    clazz.interfaces.forEach { interfaceId ->
      val inherited = index.classes[interfaceId]
      // 只有 catalog 明确允许用户实现的 builtin interface facade 可以不发射源码 class shell。
      val isImplementableBuiltinInterface = index.program.builtinTypeRoles[interfaceId]
        ?.let(::isUserImplementableBuiltinInterface) == true
      if ((inherited == null && !isImplementableBuiltinInterface) ||
        (inherited != null && inherited.kind != JavaIrTypeDeclarationKind.INTERFACE)
      ) {
        invalid("Java IR direct interface must reference an emitted interface.", clazz.span)
      }
    }
    if (clazz.kind == JavaIrTypeDeclarationKind.INTERFACE) {
      if (clazz.superClass != null || clazz.fields.any { !it.isStatic } ||
        clazz.methods.any { it.kind == JavaIrMethodKind.CONSTRUCTOR } ||
        clazz.instanceInitializer != null
      ) {
        invalid("Java IR interface contains class-only runtime members.", clazz.span)
      }
    }
    clazz.fields.forEach { field ->
      if (field.owner != clazz.id) invalid("Java IR field owner does not match its class.", field.span)
      validateType(field.type, field.span)
      field.initializer?.let(::validateExpression)
    }
    clazz.staticInitializer?.let { validateStatement(it, loopDepth = 0, breakDepth = 0) }
    clazz.instanceInitializer?.let { validateStatement(it, loopDepth = 0, breakDepth = 0) }
    clazz.methods.forEach { method ->
      if (method.owner != clazz.id) invalid("Java IR method owner does not match its class.", method.span)
      validateMethod(method)
    }
    clazz.interfaceDefaultMethods.forEach { (slot, methodId) ->
      val method = index.methods[methodId]
      val owner = method?.owner?.let(index.classes::get)
      if (method == null || owner?.kind != JavaIrTypeDeclarationKind.INTERFACE ||
        method.body == null || method.virtualSlot != slot ||
        method.dispatch != JavaIrDispatchKind.INTERFACE
      ) {
        invalid("Java IR default method mapping is inconsistent.", clazz.span)
      }
    }
    validateVirtualSlots(clazz)
  }

  /** 父类和接口共享同一继承图，validator 必须拒绝任意方向的循环。 */
  private fun validateHierarchyAcyclic(root: JavaIrClass) {
    val visiting = mutableSetOf<JavaIrClassId>()
    val visited = mutableSetOf<JavaIrClassId>()
    fun visit(classId: JavaIrClassId) {
      if (classId in visited) return
      if (!visiting.add(classId)) {
        invalid("Java IR inheritance graph contains a cycle.", root.span)
        return
      }
      index.classes[classId]?.let { current ->
        listOfNotNull(current.superClass).plus(current.interfaces).forEach(::visit)
      }
      visiting.remove(classId)
      visited += classId
    }
    visit(root.id)
  }

  /** 校验方法签名、构造器首句和局部变量引用。 */
  private fun validateMethod(method: JavaIrMethod) {
    if (method.body == null && method.dispatch !in setOf(
        JavaIrDispatchKind.VIRTUAL,
        JavaIrDispatchKind.INTERFACE,
      )
    ) invalid("Only virtual/interface declarations may omit an executable body.", method.span)
    if (method.kind == JavaIrMethodKind.CONSTRUCTOR && method.dispatch != JavaIrDispatchKind.SPECIAL) {
      invalid("Java IR constructors must use SPECIAL dispatch.", method.span)
    }
    validateType(method.returnType, method.span)
    val locals = method.parameters + method.locals
    if (locals.map(JavaIrLocal::id).distinct().size != locals.size) {
      invalid("Java IR method contains duplicate local ids.", method.span)
    }
    if (method.parameters.any { parameter -> !parameter.isParameter } ||
      method.locals.any { local -> local.isParameter }
    ) {
      invalid("Java IR method parameters and locals have inconsistent flags.", method.span)
    }
    locals.forEach { local -> validateType(local.type, local.span) }
    method.body?.let { body ->
      validateConstructorInvocations(method, body)
      validateStatement(body, loopDepth = 0, breakDepth = 0)
    }
  }

  /** 构造器委托只能作为直接首句，并且 target 必须与 this/super 语义严格一致。 */
  private fun validateConstructorInvocations(method: JavaIrMethod, body: JavaIrStatement.Block) {
    val invocations = mutableListOf<JavaIrStatement.ConstructorInvocation>()
    fun collect(statement: JavaIrStatement) {
      when (statement) {
        is JavaIrStatement.Block -> statement.statements.forEach(::collect)
        is JavaIrStatement.If -> {
          collect(statement.thenBranch)
          statement.elseBranch?.let(::collect)
        }
        is JavaIrStatement.While -> collect(statement.body)
        is JavaIrStatement.DoWhile -> collect(statement.body)
        is JavaIrStatement.For -> collect(statement.body)
        is JavaIrStatement.EnhancedFor -> collect(statement.body)
        is JavaIrStatement.Switch -> statement.entries.forEach { entry ->
          entry.statements.forEach(::collect)
        }
        is JavaIrStatement.ConstructorInvocation -> invocations += statement
        else -> Unit
      }
    }
    collect(body)
    if (method.kind != JavaIrMethodKind.CONSTRUCTOR) {
      invocations.forEach { invalid("Java IR constructor invocation may only appear in a constructor.", it.span) }
      return
    }

    val first = body.statements.firstOrNull() as? JavaIrStatement.ConstructorInvocation
    if (invocations.any { it !== first }) {
      invocations.filter { it !== first }.forEach {
        invalid("Java IR constructor invocation must be the constructor body's direct first statement.", it.span)
      }
    }
    val owner = index.classes[method.owner]
    if (owner?.superClass != null && first == null) {
      invalid("Java IR constructor must start with a constructor invocation.", method.span)
    }
    first?.let { invocation ->
      val target = index.methods[invocation.constructor]
      when (invocation.kind) {
        JavaIrConstructorInvocationKind.THIS -> if (target?.owner != method.owner) {
          invalid("Java IR this constructor invocation must target a constructor of the same class.", invocation.span)
        }
        JavaIrConstructorInvocationKind.SUPER -> if (target?.owner != owner?.superClass) {
          invalid("Java IR super constructor invocation must target a constructor of the direct superclass.", invocation.span)
        }
      }
    }
  }

  /** 同类 slot 不能重用；覆盖关系必须保持虚分派身份和兼容的参数形态。 */
  private fun validateVirtualSlots(clazz: JavaIrClass) {
    val declared = mutableMapOf<Int, JavaIrMethod>()
    clazz.methods.filter { it.virtualSlot != null }.forEach { method ->
      val slot = checkNotNull(method.virtualSlot)
      if (method.dispatch !in setOf(JavaIrDispatchKind.VIRTUAL, JavaIrDispatchKind.INTERFACE) ||
        method.kind != JavaIrMethodKind.METHOD
      ) {
        invalid("Java IR virtual slot $slot must belong to an instance virtual method.", method.span)
      }
      val previous = declared.put(slot, method)
      if (previous != null) {
        invalid("Java IR class contains duplicate virtual slot $slot.", method.span)
      }
      var ancestor = clazz.superClass?.let(index.classes::get)
      var inherited: JavaIrMethod? = null
      val visited = mutableSetOf<JavaIrClassId>()
      while (ancestor != null && inherited == null && visited.add(ancestor.id)) {
        inherited = ancestor.methods.singleOrNull { it.virtualSlot == slot }
        ancestor = ancestor.superClass?.let(index.classes::get)
      }
      if (inherited != null) {
        if (!hasCompatibleVirtualParameters(inherited, method)) {
          invalid("Java IR virtual slot $slot override parameters do not match its inherited method.", method.span)
        }
      } else {
        val root = virtualSlotRoots[slot]
        if (root == null) {
          virtualSlotRoots[slot] = method
        } else if (!hasCompatibleVirtualParameters(root, method)) {
          invalid("Java IR virtual slot $slot is reused by an incompatible method signature.", method.span)
        }
      }
    }
  }

  /**
   * 协变返回不会改变 Java override 的虚槽身份；泛型父类代换也可能让 lowered descriptor
   * 的引用参数文本不同。因此优先要求参数 descriptor 一致；仅引用参数允许按相同 arity
   * 退化匹配，既兼容泛型代换，也不放过 int/boolean 等原始参数的错误重用。
   */
  private fun hasCompatibleVirtualParameters(parent: JavaIrMethod, child: JavaIrMethod): Boolean {
    val parentParameters = parent.descriptor.substringBefore(')') + ")"
    val childParameters = child.descriptor.substringBefore(')') + ")"
    return parentParameters == childParameters ||
      (parent.parameters.size == child.parameters.size &&
        parent.parameters.zip(child.parameters).all { (parentParameter, childParameter) ->
          parentParameter.type is JavaIrType.Reference && childParameter.type is JavaIrType.Reference
        })
  }

  /** 递归校验在阶段 0 可直接翻译的控制流。 */
  private fun validateStatement(statement: JavaIrStatement, loopDepth: Int, breakDepth: Int) {
    when (statement) {
      is JavaIrStatement.Block -> statement.statements.forEach {
        validateStatement(it, loopDepth, breakDepth)
      }
      is JavaIrStatement.DeclareLocal -> {
        requireLocal(statement.local, statement.span)
        statement.initializer?.let(::validateExpression)
      }
      is JavaIrStatement.Expression -> validateExpression(statement.expression)
      is JavaIrStatement.If -> {
        validateExpression(statement.condition)
        validateStatement(statement.thenBranch, loopDepth, breakDepth)
        statement.elseBranch?.let { validateStatement(it, loopDepth, breakDepth) }
      }
      is JavaIrStatement.While -> {
        validateExpression(statement.condition)
        validateStatement(statement.body, loopDepth + 1, breakDepth + 1)
      }
      is JavaIrStatement.DoWhile -> {
        validateStatement(statement.body, loopDepth + 1, breakDepth + 1)
        validateExpression(statement.condition)
      }
      is JavaIrStatement.For -> {
        validateExpression(statement.condition)
        statement.updates.forEach(::validateExpression)
        validateStatement(statement.body, loopDepth + 1, breakDepth + 1)
      }
      is JavaIrStatement.EnhancedFor -> {
        requireLocal(statement.local, statement.span)
        validateExpression(statement.iterable)
        validateType(statement.elementType, statement.span)
        var current = statement.elementType
        statement.elementConversions.forEach { conversion ->
          val result = conversionResultType(conversion, current)
          validateConversion(JavaIrExpression.Convert(
            conversion,
            JavaIrExpression.Constant(JavaIrConstant.NullValue, current, statement.span),
            result,
            statement.span,
          ))
          current = result
        }
        if (index.locals[statement.local]?.type != current || statement.elementConversions.isEmpty()) {
          invalid("Enhanced for conversions do not reach the loop local type.", statement.span)
        }
        when (statement.kind) {
          JavaIrEnhancedForKind.ARRAY -> {
            val array = statement.iterable.type as? JavaIrType.Array
            if (array == null || array.componentType != statement.elementType) {
              invalid("Array enhanced for element type does not match its iterable.", statement.span)
            }
          }
          JavaIrEnhancedForKind.LIST -> requireBuiltinRole(
            statement.iterable.type,
            setOf(JavaBuiltinTypeRole.LIST, JavaBuiltinTypeRole.ARRAY_LIST),
            statement.span,
          )
          JavaIrEnhancedForKind.SET -> requireBuiltinRole(
            statement.iterable.type,
            setOf(JavaBuiltinTypeRole.SET, JavaBuiltinTypeRole.HASH_SET),
            statement.span,
          )
        }
        validateStatement(statement.body, loopDepth + 1, breakDepth + 1)
      }
      is JavaIrStatement.Switch -> {
        validateExpression(statement.selector)
        val stringSelector = statement.selector.type.hasBuiltinRole(JavaBuiltinTypeRole.STRING)
        val integralSelector = statement.selector.type.isStage0Integral()
        if (!stringSelector && !integralSelector) {
          invalid("Java IR switch selector must be integral or String.", statement.span)
        }
        var defaultSeen = false
        val labels = mutableSetOf<String>()
        statement.entries.forEach { entry ->
          val label = entry.label
          if (label == null) {
            if (defaultSeen) invalid("Java IR switch contains duplicate default labels.", entry.span)
            defaultSeen = true
          } else {
            validateExpression(label)
            val compatible = if (stringSelector) {
              label.type.hasBuiltinRole(JavaBuiltinTypeRole.STRING) &&
                label.value is JavaIrConstant.StringValue
            } else {
              label.type.isStage0Integral() && label.value is JavaIrConstant.IntValue
            }
            if (!compatible) invalid("Java IR switch label type does not match its selector.", entry.span)
            if (!labels.add(label.value.toString())) {
              invalid("Java IR switch contains duplicate case labels.", entry.span)
            }
          }
          entry.statements.forEach { validateStatement(it, loopDepth, breakDepth + 1) }
        }
      }
      is JavaIrStatement.Break -> if (breakDepth == 0) {
        invalid("Java IR break statement must be nested in a loop or switch.", statement.span)
      }
      is JavaIrStatement.Continue -> if (loopDepth == 0) {
        invalid("Java IR continue statement must be nested in a loop.", statement.span)
      }
      is JavaIrStatement.ConstructorInvocation -> {
        val target = index.methods[statement.constructor]
        if (target == null || target.kind != JavaIrMethodKind.CONSTRUCTOR) {
          invalid("Java IR constructor invocation must target a constructor.", statement.span)
        }
        statement.arguments.forEach(::validateExpression)
      }
      is JavaIrStatement.InitializeException -> {
        statement.message?.let(::validateExpression)
        statement.cause?.let(::validateExpression)
        if (statement.message?.type?.let { !it.hasBuiltinRole(JavaBuiltinTypeRole.STRING) && it != JavaIrType.Null } == true ||
          statement.cause?.type?.let { it != JavaIrType.Null && !it.hasThrowableRole() } == true
        ) invalid("Java IR exception initialization has an invalid message or cause.", statement.span)
      }
      is JavaIrStatement.Return -> statement.expression?.let(::validateExpression)
      is JavaIrStatement.Throw -> {
        validateExpression(statement.expression)
        val type = statement.expression.type
        if (type != JavaIrType.Null && !type.hasThrowableRole()) {
          invalid("Java IR throw expression must be Throwable or null.", statement.span)
        }
      }
      is JavaIrStatement.Try -> {
        if (statement.catches.isEmpty() && statement.finallyBlock == null && statement.resources.isEmpty()) {
          invalid("Java IR try must contain resources, catch or finally.", statement.span)
        }
        statement.resources.forEach { resource ->
          val local = index.locals[resource.local]
          validateExpression(resource.initializer)
          validateExpression(resource.closeExpression)
          if (local == null || local.type != resource.initializer.type ||
            resource.closeExpression.type != JavaIrType.Void
          ) invalid("Java IR resource has an invalid local, initializer or close call.", resource.span)
        }
        validateStatement(statement.body, loopDepth, breakDepth)
        statement.catches.forEach { clause ->
          val local = index.locals[clause.local]
          if (local == null || local.type !is JavaIrType.Reference ||
            clause.exceptionTypes.any { !it.hasThrowableRole() }
          ) {
            invalid("Java IR catch must bind a Throwable local of the declared type.", clause.span)
          }
          validateStatement(clause.body, loopDepth, breakDepth)
        }
        statement.finallyBlock?.let { validateStatement(it, loopDepth, breakDepth) }
      }
    }
  }

  /** 递归校验表达式以及其引用到的已完成绑定。 */
  private fun validateExpression(expression: JavaIrExpression) {
    validateType(expression.type, expression.span)
    when (expression) {
      is JavaIrExpression.Constant -> if (expression.value is JavaIrConstant.LongValue) {
        unsupported("Java long constants require the later BigInt backend.", expression.span)
      }
      is JavaIrExpression.GetLocal -> requireLocal(expression.local, expression.span)
      is JavaIrExpression.This -> if (index.classes[expression.type.classId] == null) {
        invalid("Java IR this expression references an unknown class.", expression.span)
      }
      is JavaIrExpression.SetLocal -> {
        requireLocal(expression.local, expression.span)
        validateExpression(expression.value)
      }
      is JavaIrExpression.GetField -> { requireInstanceField(expression.field, expression.span); validateExpression(expression.receiver) }
      is JavaIrExpression.SetField -> { requireInstanceField(expression.field, expression.span); validateExpression(expression.receiver); validateExpression(expression.value) }
      is JavaIrExpression.GetStaticField -> requireStaticField(expression.field, expression.span)
      is JavaIrExpression.BuiltinValue -> validateBuiltinValue(expression)
      is JavaIrExpression.SetStaticField -> { requireStaticField(expression.field, expression.span); validateExpression(expression.value) }
      is JavaIrExpression.Binary -> {
        validateExpression(expression.left)
        validateExpression(expression.right)
      }
      is JavaIrExpression.Unary -> validateExpression(expression.operand)
      is JavaIrExpression.Convert -> {
        validateExpression(expression.expression)
        validateConversion(expression)
      }
      is JavaIrExpression.InvokeStatic -> {
        requireStaticMethod(expression.method, expression.span)
        expression.arguments.forEach(::validateExpression)
      }
      is JavaIrExpression.InvokeBuiltin -> validateBuiltinInvocation(expression)
      is JavaIrExpression.ConstructBuiltin -> validateBuiltinConstruction(expression)
      is JavaIrExpression.InvokeSpecial -> { requireInstanceMethod(expression.method, expression.span); validateExpression(expression.receiver); expression.arguments.forEach(::validateExpression) }
      is JavaIrExpression.InvokeVirtual -> {
        val target = index.methods[expression.method]
        if (target == null || target.dispatch !in setOf(
            JavaIrDispatchKind.VIRTUAL,
            JavaIrDispatchKind.INTERFACE,
          ) || target.virtualSlot != expression.virtualSlot
        ) invalid("Java IR virtual/interface call slot does not match its selected method.", expression.span)
        validateExpression(expression.receiver); expression.arguments.forEach(::validateExpression)
      }
      is JavaIrExpression.InvokeVirtualSlot -> {
        validateExpression(expression.receiver)
        expression.arguments.forEach(::validateExpression)
        val signature = builtinSignature(expression.operation)
        if (expression.receiver.type !is JavaIrType.Reference || signature == null || !signature.hasReceiver ||
          index.program.builtinVirtualSlots[expression.operation] != expression.virtualSlot ||
          expression.arguments.size != signature.parameters.size ||
          expression.arguments.zip(signature.parameters).any { (argument, expected) ->
            !matchesBuiltinArgument(argument, expected)
          } || !matchesBuiltinType(expression.type, signature.result)
        ) invalid("Java IR builtin virtual slot call has an invalid signature.", expression.span)
      }
      is JavaIrExpression.Lambda -> validateLambda(expression)
      is JavaIrExpression.NewObject -> {
        val target = index.methods[expression.constructor]
        if (target == null || target.kind != JavaIrMethodKind.CONSTRUCTOR || target.owner != expression.classId) invalid("Java IR object creation must target an owner constructor.", expression.span)
        expression.arguments.forEach(::validateExpression)
      }
      is JavaIrExpression.NewArray -> {
        validateArrayComponent(expression.componentType, expression.span)
        validateExpression(expression.length)
        if (!expression.length.type.isStage0Integral()) invalid("Java IR array length must be integral.", expression.length.span)
      }
      is JavaIrExpression.ArrayInitializer -> {
        validateArrayComponent(expression.componentType, expression.span)
        expression.elements.forEach(::validateExpression)
      }
      is JavaIrExpression.GetArrayElement -> {
        validateExpression(expression.array)
        validateExpression(expression.index)
        requireArray(expression.array.type, expression.array.span)
        if (!expression.index.type.isStage0Integral()) invalid("Java IR array index must be integral.", expression.index.span)
      }
      is JavaIrExpression.SetArrayElement -> {
        validateExpression(expression.array)
        validateExpression(expression.index)
        validateExpression(expression.value)
        requireArray(expression.array.type, expression.array.span)
        if (!expression.index.type.isStage0Integral()) invalid("Java IR array index must be integral.", expression.index.span)
      }
      is JavaIrExpression.ArrayLength -> {
        validateExpression(expression.array)
        requireArray(expression.array.type, expression.array.span)
        if (!expression.type.isStage0Integral()) invalid("Java IR array length result must be integral.", expression.span)
      }
      is JavaIrExpression.StringConcat -> expression.parts.forEach { part -> validateExpression(part.expression) }
    }
  }

  /** 校验 lambda 的 SAM 槽、词法参数/捕获以及结构化 body。 */
  private fun validateLambda(expression: JavaIrExpression.Lambda) {
    val sourceInterface = index.classes[expression.interfaceClass]
    val builtinRole = index.program.builtinTypeRoles[expression.interfaceClass]
    val validInterface = sourceInterface?.kind == JavaIrTypeDeclarationKind.INTERFACE ||
      builtinRole?.let(::isUserImplementableBuiltinInterface) == true
    val sourceSlot = sourceInterface?.methods?.any { method ->
      method.virtualSlot == expression.virtualSlot && method.dispatch == JavaIrDispatchKind.INTERFACE
    } == true
    val builtinSlot = builtinRole != null &&
      index.program.builtinVirtualSlots.values.any { it == expression.virtualSlot }
    if (!validInterface || (!sourceSlot && !builtinSlot) || expression.virtualSlot < 0) {
      invalid("Java IR lambda target or virtual slot is invalid.", expression.span)
    }
    if (expression.parameters.distinct().size != expression.parameters.size ||
      expression.captures.distinct().size != expression.captures.size ||
      expression.boundValues.map { it.local }.distinct().size != expression.boundValues.size ||
      expression.parameters.any { it in expression.captures } ||
      expression.boundValues.any { it.local in expression.parameters || it.local in expression.captures }
    ) {
      invalid("Java IR lambda has duplicate or overlapping local bindings.", expression.span)
    }
    expression.parameters.forEach { local ->
      val declaration = index.locals[local]
      if (declaration == null || declaration.isParameter) {
        invalid("Java IR lambda parameter is not a lambda-local declaration.", expression.span)
      }
    }
    expression.captures.forEach { requireLocal(it, expression.span) }
    expression.boundValues.forEach { bound ->
      validateExpression(bound.expression)
      val declaration = index.locals[bound.local]
      if (declaration == null || declaration.isParameter || declaration.type != bound.expression.type ||
        bound.requireNonNull && bound.expression.type !is JavaIrType.Reference
      ) {
        invalid("Java IR lambda bound value has an invalid local or type.", expression.span)
      }
    }
    validateStatement(expression.body, loopDepth = 0, breakDepth = 0)
  }

  /** System 标准流值只允许使用 catalog operation，并且必须保持引用类型。 */
  private fun validateBuiltinValue(expression: JavaIrExpression.BuiltinValue) {
    val role = when (expression.operation) {
      JavaBuiltinOperation.SYSTEM_OUT, JavaBuiltinOperation.SYSTEM_ERR -> JavaBuiltinTypeRole.PRINT_STREAM
      JavaBuiltinOperation.SYSTEM_IN -> JavaBuiltinTypeRole.INPUT_STREAM
      else -> null
    }
    if (role == null || !matchesBuiltinRole(expression.type, setOf(role))) {
      invalid("Java IR builtin value has an invalid operation or type.", expression.span)
    }
  }

  /** 校验 builtin operation 的 static/receiver、参数和结果形态，防止篡改 IR 进入 emitter。 */
  private fun validateBuiltinInvocation(expression: JavaIrExpression.InvokeBuiltin) {
    expression.receiver?.let(::validateExpression)
    expression.arguments.forEach(::validateExpression)
    val signature = builtinSignature(expression.operation)
    if (signature == null) {
      invalid("Java IR operation is not a callable builtin.", expression.span)
      return
    }
    val virtualSlotValid = if (expression.operation.isBuiltinVirtualOperation()) {
      index.program.builtinVirtualSlots[expression.operation] != null
    } else {
      true
    }
    if ((expression.receiver != null) != signature.hasReceiver ||
      !virtualSlotValid ||
      (signature.hasReceiver && expression.receiver?.type !is JavaIrType.Reference) ||
      !matchesBuiltinRole(expression.receiver?.type, builtinReceiverRoles(expression.operation)) ||
      expression.arguments.size != signature.parameters.size ||
      !matchesBuiltinType(expression.type, signature.result) ||
      !matchesBuiltinRole(expression.type, builtinResultRoles(expression.operation)) ||
      expression.arguments.zip(signature.parameters).any { (argument, expected) ->
        !matchesBuiltinArgument(argument, expected)
      } || expression.arguments.indices.any { index ->
        !matchesBuiltinRole(
          expression.arguments[index].type,
          builtinParameterRoles(expression.operation, index),
        )
      }
    ) {
      invalid("Java IR builtin invocation has an invalid receiver or signature.", expression.span)
    }
  }

  /** builtin 构造与普通 NewObject 分离，只接受 catalog 明确声明的构造形态。 */
  private fun validateBuiltinConstruction(expression: JavaIrExpression.ConstructBuiltin) {
    expression.arguments.forEach(::validateExpression)
    val parameters = when (expression.operation) {
      JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_EMPTY -> emptyList()
      JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING -> listOf(BuiltinType.REFERENCE_OR_NULL)
      JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT,
      JavaBuiltinOperation.HASH_SET_CONSTRUCT,
      JavaBuiltinOperation.HASH_MAP_CONSTRUCT -> emptyList()
      JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM -> listOf(BuiltinType.REFERENCE_OR_NULL)
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY -> emptyList()
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING -> listOf(BuiltinType.REFERENCE_OR_NULL)
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE ->
        listOf(BuiltinType.REFERENCE_OR_NULL, BuiltinType.REFERENCE_OR_NULL)
      else -> {
        invalid("Java IR operation is not a constructible builtin.", expression.span)
        return
      }
    }
    val resultRoles = if (expression.operation.isExceptionConstruction()) {
      JavaBuiltinTypeRole.entries.filterTo(mutableSetOf()) { it.isThrowableRole() }
    } else {
      setOfNotNull(builtinConstructRole(expression.operation))
    }
    if (expression.arguments.size != parameters.size ||
      !matchesBuiltinRole(expression.type, resultRoles) ||
      expression.arguments.zip(parameters).any { (argument, expected) ->
        !matchesBuiltinType(argument.type, expected)
      } || expression.arguments.indices.any { index ->
        !matchesBuiltinRole(
          expression.arguments[index].type,
          builtinParameterRoles(expression.operation, index),
        )
      }
    ) invalid("Java IR builtin construction has an invalid signature.", expression.span)
  }

  /** operation 到 Java IR 形态的唯一映射；不得从成员名称或 JavaScript 值反推。 */
  private fun builtinSignature(operation: JavaBuiltinOperation): BuiltinSignature? = when (operation) {
    JavaBuiltinOperation.SYSTEM_OUT,
    JavaBuiltinOperation.SYSTEM_ERR,
    JavaBuiltinOperation.SYSTEM_IN -> null
    JavaBuiltinOperation.OBJECT_EQUALS ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.OBJECT_HASH_CODE -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.OBJECT_TO_STRING -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.THROWABLE_GET_MESSAGE,
    JavaBuiltinOperation.THROWABLE_GET_CAUSE,
    JavaBuiltinOperation.THROWABLE_TO_STRING -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.AUTO_CLOSEABLE_CLOSE -> BuiltinSignature(true, emptyList(), BuiltinType.VOID)
    JavaBuiltinOperation.RUNNABLE_RUN -> BuiltinSignature(true, emptyList(), BuiltinType.VOID)
    JavaBuiltinOperation.CONSUMER_ACCEPT ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE), BuiltinType.VOID)
    JavaBuiltinOperation.FUNCTION_APPLY ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE), BuiltinType.REFERENCE)
    JavaBuiltinOperation.SUPPLIER_GET -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.PREDICATE_TEST ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.PRINTSTREAM_PRINT_BOOLEAN -> BuiltinSignature(true, listOf(BuiltinType.BOOLEAN), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR -> BuiltinSignature(true, listOf(BuiltinType.CHAR), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR_ARRAY -> BuiltinSignature(true, listOf(BuiltinType.CHAR_ARRAY), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINT_INT -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINT_STRING -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINT_OBJECT -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN -> BuiltinSignature(true, emptyList(), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_BOOLEAN -> BuiltinSignature(true, listOf(BuiltinType.BOOLEAN), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR -> BuiltinSignature(true, listOf(BuiltinType.CHAR), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR_ARRAY -> BuiltinSignature(true, listOf(BuiltinType.CHAR_ARRAY), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_INT -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_STRING -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.VOID)
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_OBJECT -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.VOID)
    JavaBuiltinOperation.STRING_LENGTH -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.STRING_IS_EMPTY -> BuiltinSignature(true, emptyList(), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.STRING_CHAR_AT -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.CHAR)
    JavaBuiltinOperation.STRING_EQUALS -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.STRING_SUBSTRING_FROM -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_SUBSTRING_RANGE -> BuiltinSignature(true, listOf(BuiltinType.INT, BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_INDEX_OF_CHAR -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.INT)
    JavaBuiltinOperation.STRING_INDEX_OF_STRING -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.INT)
    JavaBuiltinOperation.STRING_CONTAINS,
    JavaBuiltinOperation.STRING_STARTS_WITH,
    JavaBuiltinOperation.STRING_ENDS_WITH -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.MATH_ABS_INT -> BuiltinSignature(false, listOf(BuiltinType.INT), BuiltinType.INT)
    JavaBuiltinOperation.MATH_MIN_INT,
    JavaBuiltinOperation.MATH_MAX_INT -> BuiltinSignature(false, listOf(BuiltinType.INT, BuiltinType.INT), BuiltinType.INT)
    JavaBuiltinOperation.BOOLEAN_VALUE_OF -> BuiltinSignature(false, listOf(BuiltinType.BOOLEAN), BuiltinType.REFERENCE)
    JavaBuiltinOperation.BYTE_VALUE_OF -> BuiltinSignature(false, listOf(BuiltinType.BYTE), BuiltinType.REFERENCE)
    JavaBuiltinOperation.SHORT_VALUE_OF -> BuiltinSignature(false, listOf(BuiltinType.SHORT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.CHARACTER_VALUE_OF -> BuiltinSignature(false, listOf(BuiltinType.CHAR), BuiltinType.REFERENCE)
    JavaBuiltinOperation.INTEGER_VALUE_OF -> BuiltinSignature(false, listOf(BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.BOOLEAN_BOOLEAN_VALUE -> BuiltinSignature(true, emptyList(), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.BYTE_BYTE_VALUE -> BuiltinSignature(true, emptyList(), BuiltinType.BYTE)
    JavaBuiltinOperation.SHORT_SHORT_VALUE -> BuiltinSignature(true, emptyList(), BuiltinType.SHORT)
    JavaBuiltinOperation.CHARACTER_CHAR_VALUE -> BuiltinSignature(true, emptyList(), BuiltinType.CHAR)
    JavaBuiltinOperation.INTEGER_INT_VALUE,
    JavaBuiltinOperation.NUMBER_INT_VALUE -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.BOOLEAN_EQUALS,
    JavaBuiltinOperation.BYTE_EQUALS,
    JavaBuiltinOperation.SHORT_EQUALS,
    JavaBuiltinOperation.CHARACTER_EQUALS,
    JavaBuiltinOperation.INTEGER_EQUALS -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.BOOLEAN_HASH_CODE,
    JavaBuiltinOperation.BYTE_HASH_CODE,
    JavaBuiltinOperation.SHORT_HASH_CODE,
    JavaBuiltinOperation.CHARACTER_HASH_CODE,
    JavaBuiltinOperation.INTEGER_HASH_CODE -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.BOOLEAN_TO_STRING,
    JavaBuiltinOperation.BYTE_TO_STRING,
    JavaBuiltinOperation.SHORT_TO_STRING,
    JavaBuiltinOperation.CHARACTER_TO_STRING,
    JavaBuiltinOperation.INTEGER_TO_STRING -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_BOOLEAN -> BuiltinSignature(true, listOf(BuiltinType.BOOLEAN), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR -> BuiltinSignature(true, listOf(BuiltinType.CHAR), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY -> BuiltinSignature(true, listOf(BuiltinType.CHAR_ARRAY), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_INT -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_STRING -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_OBJECT -> BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_LENGTH -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.STRING_BUILDER_CHAR_AT -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.CHAR)
    JavaBuiltinOperation.STRING_BUILDER_SET_CHAR_AT -> BuiltinSignature(true, listOf(BuiltinType.INT, BuiltinType.CHAR), BuiltinType.VOID)
    JavaBuiltinOperation.STRING_BUILDER_REVERSE -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_FROM -> BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_RANGE -> BuiltinSignature(true, listOf(BuiltinType.INT, BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_TO_STRING -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_EMPTY,
    JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING,
    JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT,
    JavaBuiltinOperation.HASH_SET_CONSTRUCT,
    JavaBuiltinOperation.HASH_MAP_CONSTRUCT -> null
    JavaBuiltinOperation.LIST_SIZE,
    JavaBuiltinOperation.SET_SIZE,
    JavaBuiltinOperation.MAP_SIZE -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.LIST_IS_EMPTY,
    JavaBuiltinOperation.SET_IS_EMPTY,
    JavaBuiltinOperation.MAP_IS_EMPTY,
    JavaBuiltinOperation.ITERATOR_HAS_NEXT -> BuiltinSignature(true, emptyList(), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.LIST_ADD,
    JavaBuiltinOperation.LIST_REMOVE_OBJECT,
    JavaBuiltinOperation.LIST_CONTAINS,
    JavaBuiltinOperation.SET_ADD,
    JavaBuiltinOperation.SET_CONTAINS,
    JavaBuiltinOperation.SET_REMOVE,
    JavaBuiltinOperation.MAP_CONTAINS_KEY ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.LIST_GET,
    JavaBuiltinOperation.LIST_REMOVE_INDEX ->
      BuiltinSignature(true, listOf(BuiltinType.INT), BuiltinType.REFERENCE)
    JavaBuiltinOperation.LIST_SET ->
      BuiltinSignature(true, listOf(BuiltinType.INT, BuiltinType.REFERENCE_OR_NULL), BuiltinType.REFERENCE)
    JavaBuiltinOperation.LIST_INDEX_OF ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.INT)
    JavaBuiltinOperation.LIST_CLEAR,
    JavaBuiltinOperation.SET_CLEAR,
    JavaBuiltinOperation.MAP_CLEAR -> BuiltinSignature(true, emptyList(), BuiltinType.VOID)
    JavaBuiltinOperation.LIST_ITERATOR,
    JavaBuiltinOperation.SET_ITERATOR,
    JavaBuiltinOperation.ITERATOR_NEXT,
    JavaBuiltinOperation.MAP_KEY_SET -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.MAP_PUT -> BuiltinSignature(
      true,
      listOf(BuiltinType.REFERENCE_OR_NULL, BuiltinType.REFERENCE_OR_NULL),
      BuiltinType.REFERENCE,
    )
    JavaBuiltinOperation.MAP_GET,
    JavaBuiltinOperation.MAP_REMOVE ->
      BuiltinSignature(true, listOf(BuiltinType.REFERENCE_OR_NULL), BuiltinType.REFERENCE)
    JavaBuiltinOperation.MAP_GET_OR_DEFAULT -> BuiltinSignature(
      true,
      listOf(BuiltinType.REFERENCE_OR_NULL, BuiltinType.REFERENCE_OR_NULL),
      BuiltinType.REFERENCE,
    )
    JavaBuiltinOperation.SCANNER_HAS_NEXT,
    JavaBuiltinOperation.SCANNER_HAS_NEXT_INT,
    JavaBuiltinOperation.SCANNER_HAS_NEXT_LINE -> BuiltinSignature(true, emptyList(), BuiltinType.BOOLEAN)
    JavaBuiltinOperation.SCANNER_NEXT,
    JavaBuiltinOperation.SCANNER_NEXT_LINE -> BuiltinSignature(true, emptyList(), BuiltinType.REFERENCE)
    JavaBuiltinOperation.SCANNER_NEXT_INT -> BuiltinSignature(true, emptyList(), BuiltinType.INT)
    JavaBuiltinOperation.SCANNER_CLOSE -> BuiltinSignature(true, emptyList(), BuiltinType.VOID)
    JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM,
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY,
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING,
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE -> null
  }

  /** builtin receiver 的精确运行时角色；null 表示 static/field/constructor 或无需引用约束。 */
  private fun builtinReceiverRoles(operation: JavaBuiltinOperation): Set<JavaBuiltinTypeRole>? = when (operation) {
    JavaBuiltinOperation.OBJECT_EQUALS,
    JavaBuiltinOperation.OBJECT_HASH_CODE,
    JavaBuiltinOperation.OBJECT_TO_STRING,
    JavaBuiltinOperation.THROWABLE_GET_MESSAGE,
    JavaBuiltinOperation.THROWABLE_GET_CAUSE,
    JavaBuiltinOperation.THROWABLE_TO_STRING,
    JavaBuiltinOperation.AUTO_CLOSEABLE_CLOSE -> null

    JavaBuiltinOperation.PRINTSTREAM_PRINT_BOOLEAN,
    JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR,
    JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR_ARRAY,
    JavaBuiltinOperation.PRINTSTREAM_PRINT_INT,
    JavaBuiltinOperation.PRINTSTREAM_PRINT_STRING,
    JavaBuiltinOperation.PRINTSTREAM_PRINT_OBJECT,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_BOOLEAN,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR_ARRAY,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_INT,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_STRING,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_OBJECT -> setOf(JavaBuiltinTypeRole.PRINT_STREAM)

    JavaBuiltinOperation.STRING_LENGTH,
    JavaBuiltinOperation.STRING_IS_EMPTY,
    JavaBuiltinOperation.STRING_CHAR_AT,
    JavaBuiltinOperation.STRING_EQUALS,
    JavaBuiltinOperation.STRING_SUBSTRING_FROM,
    JavaBuiltinOperation.STRING_SUBSTRING_RANGE,
    JavaBuiltinOperation.STRING_INDEX_OF_CHAR,
    JavaBuiltinOperation.STRING_INDEX_OF_STRING,
    JavaBuiltinOperation.STRING_CONTAINS,
    JavaBuiltinOperation.STRING_STARTS_WITH,
    JavaBuiltinOperation.STRING_ENDS_WITH -> setOf(JavaBuiltinTypeRole.STRING)

    JavaBuiltinOperation.BOOLEAN_BOOLEAN_VALUE, JavaBuiltinOperation.BOOLEAN_EQUALS,
    JavaBuiltinOperation.BOOLEAN_HASH_CODE, JavaBuiltinOperation.BOOLEAN_TO_STRING -> setOf(JavaBuiltinTypeRole.BOOLEAN)
    JavaBuiltinOperation.BYTE_BYTE_VALUE, JavaBuiltinOperation.BYTE_EQUALS,
    JavaBuiltinOperation.BYTE_HASH_CODE, JavaBuiltinOperation.BYTE_TO_STRING -> setOf(JavaBuiltinTypeRole.BYTE)
    JavaBuiltinOperation.SHORT_SHORT_VALUE, JavaBuiltinOperation.SHORT_EQUALS,
    JavaBuiltinOperation.SHORT_HASH_CODE, JavaBuiltinOperation.SHORT_TO_STRING -> setOf(JavaBuiltinTypeRole.SHORT)
    JavaBuiltinOperation.CHARACTER_CHAR_VALUE, JavaBuiltinOperation.CHARACTER_EQUALS,
    JavaBuiltinOperation.CHARACTER_HASH_CODE, JavaBuiltinOperation.CHARACTER_TO_STRING -> setOf(JavaBuiltinTypeRole.CHARACTER)
    JavaBuiltinOperation.INTEGER_INT_VALUE, JavaBuiltinOperation.INTEGER_EQUALS,
    JavaBuiltinOperation.INTEGER_HASH_CODE, JavaBuiltinOperation.INTEGER_TO_STRING -> setOf(JavaBuiltinTypeRole.INTEGER)
    JavaBuiltinOperation.NUMBER_INT_VALUE -> setOf(
      JavaBuiltinTypeRole.NUMBER, JavaBuiltinTypeRole.BYTE,
      JavaBuiltinTypeRole.SHORT, JavaBuiltinTypeRole.INTEGER,
    )

    JavaBuiltinOperation.STRING_BUILDER_APPEND_BOOLEAN,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_INT,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_STRING,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_OBJECT,
    JavaBuiltinOperation.STRING_BUILDER_LENGTH,
    JavaBuiltinOperation.STRING_BUILDER_CHAR_AT,
    JavaBuiltinOperation.STRING_BUILDER_SET_CHAR_AT,
    JavaBuiltinOperation.STRING_BUILDER_REVERSE,
    JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_FROM,
    JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_RANGE,
    JavaBuiltinOperation.STRING_BUILDER_TO_STRING -> setOf(JavaBuiltinTypeRole.STRING_BUILDER)

    JavaBuiltinOperation.LIST_SIZE, JavaBuiltinOperation.LIST_IS_EMPTY,
    JavaBuiltinOperation.LIST_ADD, JavaBuiltinOperation.LIST_GET, JavaBuiltinOperation.LIST_SET,
    JavaBuiltinOperation.LIST_REMOVE_INDEX, JavaBuiltinOperation.LIST_REMOVE_OBJECT,
    JavaBuiltinOperation.LIST_CONTAINS, JavaBuiltinOperation.LIST_INDEX_OF,
    JavaBuiltinOperation.LIST_CLEAR, JavaBuiltinOperation.LIST_ITERATOR ->
      setOf(JavaBuiltinTypeRole.LIST, JavaBuiltinTypeRole.ARRAY_LIST)
    JavaBuiltinOperation.SET_ADD, JavaBuiltinOperation.SET_CONTAINS, JavaBuiltinOperation.SET_REMOVE,
    JavaBuiltinOperation.SET_SIZE, JavaBuiltinOperation.SET_IS_EMPTY,
    JavaBuiltinOperation.SET_CLEAR, JavaBuiltinOperation.SET_ITERATOR ->
      setOf(JavaBuiltinTypeRole.SET, JavaBuiltinTypeRole.HASH_SET)
    JavaBuiltinOperation.MAP_PUT, JavaBuiltinOperation.MAP_GET,
    JavaBuiltinOperation.MAP_GET_OR_DEFAULT, JavaBuiltinOperation.MAP_CONTAINS_KEY,
    JavaBuiltinOperation.MAP_REMOVE, JavaBuiltinOperation.MAP_SIZE,
    JavaBuiltinOperation.MAP_IS_EMPTY, JavaBuiltinOperation.MAP_CLEAR,
    JavaBuiltinOperation.MAP_KEY_SET -> setOf(JavaBuiltinTypeRole.MAP, JavaBuiltinTypeRole.HASH_MAP)
    JavaBuiltinOperation.ITERATOR_HAS_NEXT, JavaBuiltinOperation.ITERATOR_NEXT -> setOf(JavaBuiltinTypeRole.ITERATOR)
    JavaBuiltinOperation.SCANNER_HAS_NEXT, JavaBuiltinOperation.SCANNER_NEXT,
    JavaBuiltinOperation.SCANNER_HAS_NEXT_INT, JavaBuiltinOperation.SCANNER_NEXT_INT,
    JavaBuiltinOperation.SCANNER_HAS_NEXT_LINE, JavaBuiltinOperation.SCANNER_NEXT_LINE,
    JavaBuiltinOperation.SCANNER_CLOSE -> setOf(JavaBuiltinTypeRole.SCANNER)

    else -> null
  }

  /** 引用参数中需要精确 builtin class 身份的位置。 */
  private fun builtinParameterRoles(
    operation: JavaBuiltinOperation,
    index: Int,
  ): Set<JavaBuiltinTypeRole>? = when (operation) {
    JavaBuiltinOperation.PRINTSTREAM_PRINT_STRING,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_STRING,
    JavaBuiltinOperation.STRING_INDEX_OF_STRING,
    JavaBuiltinOperation.STRING_CONTAINS,
    JavaBuiltinOperation.STRING_STARTS_WITH,
    JavaBuiltinOperation.STRING_ENDS_WITH,
    JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_STRING -> if (index == 0) setOf(JavaBuiltinTypeRole.STRING) else null
    JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM -> if (index == 0) setOf(JavaBuiltinTypeRole.INPUT_STREAM) else null
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE -> when (index) {
      0 -> setOf(JavaBuiltinTypeRole.STRING)
      else -> null
    }
    else -> null
  }

  /** 仅对返回 builtin 引用的 operation 约束精确 class role。 */
  private fun builtinResultRoles(operation: JavaBuiltinOperation): Set<JavaBuiltinTypeRole>? = when (operation) {
    JavaBuiltinOperation.OBJECT_TO_STRING,
    JavaBuiltinOperation.THROWABLE_GET_MESSAGE,
    JavaBuiltinOperation.THROWABLE_TO_STRING,
    JavaBuiltinOperation.STRING_SUBSTRING_FROM,
    JavaBuiltinOperation.STRING_SUBSTRING_RANGE,
    JavaBuiltinOperation.BOOLEAN_TO_STRING,
    JavaBuiltinOperation.BYTE_TO_STRING,
    JavaBuiltinOperation.SHORT_TO_STRING,
    JavaBuiltinOperation.CHARACTER_TO_STRING,
    JavaBuiltinOperation.INTEGER_TO_STRING,
    JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_FROM,
    JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_RANGE,
    JavaBuiltinOperation.STRING_BUILDER_TO_STRING,
    JavaBuiltinOperation.SCANNER_NEXT,
    JavaBuiltinOperation.SCANNER_NEXT_LINE -> setOf(JavaBuiltinTypeRole.STRING)
    JavaBuiltinOperation.THROWABLE_GET_CAUSE -> setOf(JavaBuiltinTypeRole.THROWABLE)
    JavaBuiltinOperation.BOOLEAN_VALUE_OF -> setOf(JavaBuiltinTypeRole.BOOLEAN)
    JavaBuiltinOperation.BYTE_VALUE_OF -> setOf(JavaBuiltinTypeRole.BYTE)
    JavaBuiltinOperation.SHORT_VALUE_OF -> setOf(JavaBuiltinTypeRole.SHORT)
    JavaBuiltinOperation.CHARACTER_VALUE_OF -> setOf(JavaBuiltinTypeRole.CHARACTER)
    JavaBuiltinOperation.INTEGER_VALUE_OF -> setOf(JavaBuiltinTypeRole.INTEGER)
    JavaBuiltinOperation.STRING_BUILDER_APPEND_BOOLEAN,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_INT,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_STRING,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_OBJECT,
    JavaBuiltinOperation.STRING_BUILDER_REVERSE -> setOf(JavaBuiltinTypeRole.STRING_BUILDER)
    JavaBuiltinOperation.LIST_ITERATOR,
    JavaBuiltinOperation.SET_ITERATOR -> setOf(JavaBuiltinTypeRole.ITERATOR)
    JavaBuiltinOperation.MAP_KEY_SET -> setOf(JavaBuiltinTypeRole.SET)
    else -> null
  }

  /** ConstructBuiltin 的普通结果类与 operation 唯一对应；异常 family 由结果 role 进一步区分。 */
  private fun builtinConstructRole(operation: JavaBuiltinOperation): JavaBuiltinTypeRole? = when (operation) {
    JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_EMPTY,
    JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING -> JavaBuiltinTypeRole.STRING_BUILDER
    JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT -> JavaBuiltinTypeRole.ARRAY_LIST
    JavaBuiltinOperation.HASH_SET_CONSTRUCT -> JavaBuiltinTypeRole.HASH_SET
    JavaBuiltinOperation.HASH_MAP_CONSTRUCT -> JavaBuiltinTypeRole.HASH_MAP
    JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM -> JavaBuiltinTypeRole.SCANNER
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY,
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING,
    JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE -> null
    else -> null
  }

  /** 空 role 约束表示该位置可使用任意合法 Java 引用；非空时必须精确命中 catalog role。 */
  private fun matchesBuiltinRole(
    type: JavaIrType?,
    roles: Set<JavaBuiltinTypeRole>?,
  ): Boolean {
    if (roles.isNullOrEmpty()) return true
    if (type == JavaIrType.Null) return true
    val reference = type as? JavaIrType.Reference ?: return false
    return index.program.builtinTypeRoles[reference.classId] in roles
  }

  /** catch/throw 同时接受 catalog Throwable 与携带异常父链元数据的源码类型。 */
  private fun JavaIrType.hasThrowableRole(): Boolean {
    val reference = this as? JavaIrType.Reference ?: return false
    if (index.program.builtinTypeRoles[reference.classId]?.isThrowableRole() == true) return true
    var current = index.classes[reference.classId]
    val visited = mutableSetOf<JavaIrClassId>()
    while (current != null && visited.add(current.id)) {
      if (current.exceptionSuperQualifiedName != null) return true
      current = current.superClass?.let(index.classes::get)
    }
    return false
  }

  /** builtin 类型只按 typed IR 类别检查；内建类没有伪造为输出 JavaIrClass。 */
  private fun matchesBuiltinType(type: JavaIrType, expected: BuiltinType): Boolean = when (expected) {
    BuiltinType.BOOLEAN -> type is JavaIrType.Primitive && type.kind.name == "BOOLEAN"
    BuiltinType.BYTE -> type is JavaIrType.Primitive && type.kind.name == "BYTE"
    BuiltinType.SHORT -> type is JavaIrType.Primitive && type.kind.name == "SHORT"
    BuiltinType.CHAR -> type is JavaIrType.Primitive && type.kind.name == "CHAR"
    BuiltinType.CHAR_ARRAY -> type is JavaIrType.Array &&
      type.componentType is JavaIrType.Primitive && type.componentType.kind.name == "CHAR"
    BuiltinType.INT -> type is JavaIrType.Primitive && type.kind.name == "INT"
    BuiltinType.REFERENCE -> type is JavaIrType.Reference
    BuiltinType.REFERENCE_OR_NULL -> type is JavaIrType.Reference || type is JavaIrType.Array || type == JavaIrType.Null
    BuiltinType.VOID -> type == JavaIrType.Void
  }

  /** lowering 保证 Convert.type 是目标类型，因此 builtin 参数无需窥探 conversion 内部特判。 */
  private fun matchesBuiltinArgument(
    expression: JavaIrExpression,
    expected: BuiltinType,
  ): Boolean = matchesBuiltinType(expression.type, expected)

  private data class BuiltinSignature(
    val hasReceiver: Boolean,
    val parameters: List<BuiltinType>,
    val result: BuiltinType,
  )

  private enum class BuiltinType {
    BOOLEAN, BYTE, SHORT, CHAR, CHAR_ARRAY, INT, REFERENCE, REFERENCE_OR_NULL, VOID,
  }

  /** 当前运行子集支持 identity、引用拓宽与不涉及 long/浮点的整数 widening。 */
  private fun validateConversion(expression: JavaIrExpression.Convert) {
    val conversion = expression.conversion
    val sourceType = expression.expression.type
    val resultType = expression.type
    when (conversion) {
      JavaIrConversion.Identity -> if (sourceType != resultType) {
        invalid("Identity conversion must preserve its operand type.", expression.span)
      }
      is JavaIrConversion.ReferenceWidening -> if (
        conversion.from != sourceType || conversion.to != resultType ||
        !isValidReferenceWidening(sourceType, resultType)
      ) invalid("Reference conversion endpoints do not match typed IR.", expression.span)
      is JavaIrConversion.PrimitiveWidening -> {
        if (!conversion.from.name.isStage0IntegralPrimitiveName() ||
          !conversion.to.name.isStage0IntegralPrimitiveName()
        ) {
          unsupported("Only integral primitive widening is available in the current JavaScript backend.", expression.span)
        }
        if (sourceType != JavaIrType.Primitive(conversion.from) ||
          resultType != JavaIrType.Primitive(conversion.to)
        ) invalid("Primitive widening endpoints do not match typed IR.", expression.span)
      }
      is JavaIrConversion.PrimitiveNarrowing -> {
        if (!conversion.from.name.isStage0IntegralPrimitiveName() ||
          !conversion.to.name.isStage0IntegralPrimitiveName() ||
          sourceType != JavaIrType.Primitive(conversion.from) ||
          resultType != JavaIrType.Primitive(conversion.to)
        ) {
          invalid("Primitive narrowing endpoints do not match supported typed IR.", expression.span)
        }
      }
      is JavaIrConversion.Boxing -> if (
        sourceType != JavaIrType.Primitive(conversion.primitive) ||
        resultType != JavaIrType.Reference(conversion.boxedClass) ||
        index.program.builtinTypeRoles[conversion.boxedClass]?.boxedPrimitive != conversion.primitive
      ) invalid("Boxing endpoints do not match typed IR.", expression.span)
      is JavaIrConversion.Unboxing -> if (
        sourceType != JavaIrType.Reference(conversion.boxedClass) ||
        resultType != JavaIrType.Primitive(conversion.primitive) ||
        index.program.builtinTypeRoles[conversion.boxedClass]?.boxedPrimitive != conversion.primitive
      ) invalid("Unboxing endpoints do not match typed IR.", expression.span)
    }
  }

  /** 根据 conversion 的显式端点返回下一步类型，不从 JavaScript 值推断。 */
  private fun conversionResultType(
    conversion: JavaIrConversion,
    source: JavaIrType,
  ): JavaIrType = when (conversion) {
    JavaIrConversion.Identity -> source
    is JavaIrConversion.ReferenceWidening -> conversion.to
    is JavaIrConversion.PrimitiveWidening -> JavaIrType.Primitive(conversion.to)
    is JavaIrConversion.PrimitiveNarrowing -> JavaIrType.Primitive(conversion.to)
    is JavaIrConversion.Boxing -> JavaIrType.Reference(conversion.boxedClass)
    is JavaIrConversion.Unboxing -> JavaIrType.Primitive(conversion.primitive)
  }

  /** 增强 for 集合协议只接受 catalog 冻结的 builtin role。 */
  private fun requireBuiltinRole(
    type: JavaIrType,
    roles: Set<JavaBuiltinTypeRole>,
    span: JavaSourceSpan,
  ) {
    val role = (type as? JavaIrType.Reference)?.classId?.let(index.program.builtinTypeRoles::get)
    if (role !in roles) invalid("Enhanced for iterable has an invalid builtin role.", span)
  }

  /** validator 通过冻结的 builtin role 判断引用身份，不读取限定名。 */
  private fun JavaIrType.hasBuiltinRole(role: JavaBuiltinTypeRole): Boolean {
    val classId = (this as? JavaIrType.Reference)?.classId ?: return false
    return index.program.builtinTypeRoles[classId] == role
  }

  /**
   * 以 typed IR 用户类层级和 catalog builtin role 层级验证引用拓宽。
   *
   * null 可拓宽到任意引用/数组；数组只实现当前 Java 子集可证明的协变与 Object 根类型拓宽。
   * 任一 classId 无法在用户类或 builtin role 中证明时返回 false，禁止伪造端点绕过 builtin 校验。
   */
  private fun isValidReferenceWidening(source: JavaIrType, target: JavaIrType): Boolean = when {
    source == JavaIrType.Null -> target is JavaIrType.Reference || target is JavaIrType.Array
    source is JavaIrType.Reference && target is JavaIrType.Reference ->
      isReferenceAssignableByHierarchy(source.classId, target.classId)
    source is JavaIrType.Array && target is JavaIrType.Array ->
      isValidArrayComponentWidening(source.componentType, target.componentType)
    source is JavaIrType.Array && target is JavaIrType.Reference ->
      index.program.builtinTypeRoles[target.classId] == JavaBuiltinTypeRole.OBJECT
    else -> false
  }

  /** 数组协变只接受引用组件的可证明拓宽；primitive 数组不会伪装成另一 primitive 数组。 */
  private fun isValidArrayComponentWidening(source: JavaIrType, target: JavaIrType): Boolean = when {
    source is JavaIrType.Reference && target is JavaIrType.Reference ->
      isReferenceAssignableByHierarchy(source.classId, target.classId)
    source is JavaIrType.Array && target is JavaIrType.Array ->
      isValidArrayComponentWidening(source.componentType, target.componentType)
    source is JavaIrType.Array && target is JavaIrType.Reference ->
      index.program.builtinTypeRoles[target.classId] == JavaBuiltinTypeRole.OBJECT
    else -> false
  }

  /**
   * 沿用户 IR 父类/接口及 catalog role 父链验证可赋值关系。
   *
   * 擦除后相同 classId 也合法：泛型参数层级已由 semantic 冻结，运行时无需再次转换。
  */
  private fun isReferenceAssignableByHierarchy(source: JavaIrClassId, target: JavaIrClassId): Boolean {
    if (source == target) return source in index.classes || source in index.program.builtinTypeRoles
    val pending = ArrayDeque<JavaIrClassId>()
    pending.addAll(directSuperClasses(source))
    val visited = mutableSetOf<JavaIrClassId>()
    while (pending.isNotEmpty()) {
      val current = pending.removeFirst()
      if (!visited.add(current)) continue
      if (current == target) return true
      pending.addAll(directSuperClasses(current))
    }
    return false
  }

  /** builtin 父类由 catalog role 映射定位，用户类沿 JavaIrClass 已冻结层级。 */
  private fun directSuperClasses(classId: JavaIrClassId): List<JavaIrClassId> {
    index.classes[classId]?.let { clazz ->
      val rootObject = if (clazz.superClass == null) {
        index.program.builtinTypeRoles.entries
          .singleOrNull { (_, role) -> role == JavaBuiltinTypeRole.OBJECT }
          ?.key
      } else {
        null
      }
      // lowering 用 null 表示未发射的 Object 根，因此这里恢复该条可证明的直接父边。
      return listOfNotNull(clazz.superClass ?: rootObject) + clazz.interfaces
    }
    val role = index.program.builtinTypeRoles[classId] ?: return emptyList()
    val superRole = JavaBuiltinLibrary.directSuperRoles[role] ?: return emptyList()
    return index.program.builtinTypeRoles.entries
      .singleOrNull { (_, candidateRole) -> candidateRole == superRole }
      ?.let { (superClassId, _) -> listOf(superClassId) }
      .orEmpty()
  }

  /** 引用类型以运行时对象身份参与 null、相等性、字段和调用；内建类库仍未注入。 */
  private fun validateType(type: JavaIrType, span: JavaSourceSpan) {
    when (type) {
      is JavaIrType.Primitive -> when (type.kind.name) {
        "LONG" -> unsupported("Java long requires the later BigInt backend.", span)
        "FLOAT",
        "DOUBLE" -> unsupported(
          "Java floating-point values are not available in the current JavaScript backend.",
          span,
        )
        else -> Unit
      }
      is JavaIrType.Array -> validateArrayComponent(type.componentType, span)
      is JavaIrType.Reference,
      JavaIrType.Null,
      JavaIrType.Void -> Unit
    }
  }

  /** 入口必须唯一地指向完整、静态且已编译的方法。 */
  private fun validateEntryPoint() {
    val owner = index.program.classes.singleOrNull { clazz ->
      clazz.qualifiedName == entryPoint.qualifiedClassName
    }
    if (owner == null) {
      invalid("Java entry class '${entryPoint.qualifiedClassName}' is missing or ambiguous.", null)
      return
    }
    val target = owner.methods.singleOrNull { method ->
      method.name == entryPoint.methodName &&
        method.descriptor == entryPoint.descriptor &&
        method.dispatch == JavaIrDispatchKind.STATIC
    }
    if (target == null) {
      invalid(
        "Java entry method '${entryPoint.methodName}${entryPoint.descriptor}' is missing or ambiguous.",
        owner.span,
      )
    } else if (target.body == null) {
      unsupported("Java entry method has no executable body.", target.span)
    }
  }

  /** 检查局部变量编号在整个 lowered program 中可解析。 */
  private fun requireLocal(localId: JavaIrLocalId, span: JavaSourceSpan) {
    if (index.locals[localId] == null) {
      invalid("Java IR references an unknown local id ${localId.value}.", span)
    }
  }

  /** 检查调用 target 存在且被 lowering 标记为 static。 */
  private fun requireStaticMethod(methodId: JavaIrMethodId, span: JavaSourceSpan) {
    val method = index.methods[methodId]
    if (method == null) {
      invalid("Java IR references an unknown method id ${methodId.value}.", span)
    } else if (method.dispatch != JavaIrDispatchKind.STATIC || method.kind != JavaIrMethodKind.METHOD || method.body == null) {
      unsupported("Java static calls may only target executable static methods.", span)
    }
  }

  /** 字段操作必须服从 lowering 已完成的 static/instance 分类。 */
  private fun requireStaticField(fieldId: JavaIrFieldId, span: JavaSourceSpan) {
    val field = index.fields[fieldId]
    if (field == null) invalid("Java IR references an unknown field id ${fieldId.value}.", span)
    else if (!field.isStatic) invalid("Java IR static access targets an instance field.", span)
  }

  private fun requireInstanceField(fieldId: JavaIrFieldId, span: JavaSourceSpan) {
    val field = index.fields[fieldId]
    if (field == null) invalid("Java IR references an unknown field id ${fieldId.value}.", span)
    else if (field.isStatic) invalid("Java IR instance access targets a static field.", span)
  }

  private fun requireInstanceMethod(methodId: JavaIrMethodId, span: JavaSourceSpan) {
    val method = index.methods[methodId]
    if (method == null) invalid("Java IR references an unknown method id ${methodId.value}.", span)
    else if (method.dispatch == JavaIrDispatchKind.STATIC || method.kind != JavaIrMethodKind.METHOD || method.body == null) invalid("Java IR instance call targets a non-executable static method.", span)
  }

  /** 首批仅支持一维、当前运行时可表示的数组组件。 */
  private fun validateArrayComponent(type: JavaIrType, span: JavaSourceSpan) {
    if (type is JavaIrType.Array) {
      unsupported("Multidimensional Java arrays are not available in the current JavaScript backend.", span)
    } else {
      validateType(type, span)
    }
  }

  /** 数组读写与 length 都必须由明确 Array 类型驱动，不能把普通引用伪装为数组。 */
  private fun requireArray(type: JavaIrType, span: JavaSourceSpan) {
    if (type !is JavaIrType.Array) invalid("Java IR array operation requires an array receiver.", span)
  }

  /** 记录 typed IR 已知但当前后端尚未实现的稳定诊断。 */
  private fun unsupported(message: String, span: JavaSourceSpan?) {
    diagnostics += JavaCompilerDiagnostic(
      code = "JAVA_BACKEND_UNSUPPORTED",
      message = message,
      severity = JavaDiagnosticSeverity.ERROR,
      span = span,
    )
  }

  /** 记录违反 lowered IR 完整性约束的稳定诊断。 */
  private fun invalid(message: String, span: JavaSourceSpan?) {
    diagnostics += JavaCompilerDiagnostic(
      code = "JAVA_BACKEND_INVALID_IR",
      message = message,
      severity = JavaDiagnosticSeverity.ERROR,
      span = span,
    )
  }
}

/** 将已经验证的 typed IR 发射为单个 ES Module。 */
private class JavaScriptEmitter(
  private val index: JavaIrIndex,
  private val entryPoint: JavaCompilerEntryPoint,
) {
  private var writer = JsWriter()
  private val charArrayBuiltinOperations = setOf(
    JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR_ARRAY,
    JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR_ARRAY,
    JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY,
  )
  private var currentMethod: JavaIrMethod? = null
  private var currentThisName: String = "this"
  private val classesInParentFirstOrder: List<JavaIrClass> by lazy {
    val visited = mutableSetOf<JavaIrClassId>()
    buildList {
      fun visit(clazz: JavaIrClass) {
        if (!visited.add(clazz.id)) return
        clazz.superClass?.let(index.classes::get)?.let(::visit)
        clazz.interfaces.mapNotNull(index.classes::get).forEach(::visit)
        add(clazz)
      }
      index.program.classes.sortedBy { it.id.value }.forEach(::visit)
    }
  }

  /** 输出 runtime prelude、全部静态方法和稳定入口导出。 */
  fun emit(): JavaScriptProgramArtifact {
    JavaRuntimePrelude.source.lines().forEach(writer::line)
    if (usesRuntimeHelpers()) {
      writer.line()
      JavaRuntimePrelude.objectSource.lines().forEach(writer::line)
    }
    if (usesArrayOrStringRuntime()) {
      writer.line()
      JavaRuntimePrelude.arrayAndStringSource.lines().forEach(writer::line)
    }
    if (usesExceptionRuntime()) {
      writer.line()
      JavaRuntimePrelude.exceptionSource.lines().forEach(writer::line)
    }
    if (usesBuiltinRuntime()) {
      writer.line()
      emitBuiltinVirtualSlotConstants()
      JavaRuntimePrelude.builtinSource.lines().forEach(writer::line)
    }
    if (usesCollectionRuntime()) {
      writer.line()
      JavaRuntimePrelude.collectionSource.lines().forEach(writer::line)
    }
    if (usesScannerRuntime()) {
      writer.line()
      // Scanner 依赖模块内的 UTF-8/Base64 解码 reader；必须先声明 reader，再声明 cursor helper。
      DynamicProgramHostAbi.STANDARD_INPUT_READER_SOURCE.lines().forEach(writer::line)
      writer.line()
      JavaRuntimePrelude.scannerSource.lines().forEach(writer::line)
    }
    writer.line()
    val methods = index.program.classes
      .flatMap(JavaIrClass::methods)
      .filter { method -> method.body != null }
      .sortedBy { method -> method.id.value }
    if (usesObjectRuntime()) emitClassShells()
    methods.forEach(::emitMethod)
    if (usesObjectRuntime()) emitClassInitializers()
    emitEntryExport(resolveEntryMethod())

    return JavaScriptProgramArtifact(
      entryModuleName = JavaModuleLayout.ENTRY_MODULE_NAME,
      entryExportName = JavaModuleLayout.ENTRY_EXPORT_NAME,
      modules = listOf(
        JavaScriptModuleArtifact(
          moduleName = JavaModuleLayout.ENTRY_MODULE_NAME,
          source = writer.source,
          sourceMappings = writer.mappings,
        ),
      ),
    )
  }

  /** 将 semantic 冻结的 Object 虚槽写成模块内常量，runtime 不读取源码方法名。 */
  private fun emitBuiltinVirtualSlotConstants() {
    fun slot(operation: JavaBuiltinOperation): String = JsNameMangler.virtualSlot(
      index.program.builtinVirtualSlots.getValue(operation),
    )
    writer.line("const \$__j_object_equals_slot = ${writer.stringLiteral(slot(JavaBuiltinOperation.OBJECT_EQUALS))};")
    writer.line("const \$__j_object_hash_code_slot = ${writer.stringLiteral(slot(JavaBuiltinOperation.OBJECT_HASH_CODE))};")
    writer.line("const \$__j_object_to_string_slot = ${writer.stringLiteral(slot(JavaBuiltinOperation.OBJECT_TO_STRING))};")
  }

  /** 阶段 0 的纯 static 程序不发射对象壳，保持其既有稳定快照。 */
  private fun usesObjectRuntime(): Boolean = index.program.classes.any { clazz ->
    clazz.superClass != null || clazz.fields.isNotEmpty() || clazz.staticInitializer != null ||
      clazz.instanceInitializer != null || clazz.methods.any { it.dispatch != JavaIrDispatchKind.STATIC }
  }

  /** 数组和显式 StringConcat 可存在于纯 static 程序，仍需额外 runtime helper。 */
  private fun usesArrayOrStringRuntime(): Boolean = index.program.classes.any { clazz ->
    clazz.fields.any { field -> field.initializer?.requiresArrayOrStringRuntime() == true } ||
      clazz.methods.any { method -> method.body?.containsArrayOrStringRuntime() == true }
  }

  /** object helper 既服务字段调用，也服务数组的 null 检查。 */
  private fun usesRuntimeHelpers(): Boolean =
    usesObjectRuntime() || usesArrayOrStringRuntime() || usesBuiltinRuntime() || usesExceptionRuntime()

  /** throw、try/catch 与异常构造任一出现时注入类型匹配运行时。 */
  private fun usesExceptionRuntime(): Boolean = index.program.classes.any { clazz ->
    clazz.exceptionSuperQualifiedName != null ||
      clazz.fields.any { it.initializer?.requiresExceptionRuntime() == true } ||
      clazz.staticInitializer?.containsExceptionRuntime() == true ||
      clazz.instanceInitializer?.containsExceptionRuntime() == true ||
      clazz.methods.any { it.body?.containsExceptionRuntime() == true }
  }

  private fun JavaIrStatement.Block.containsExceptionRuntime(): Boolean =
    statements.any { it.requiresExceptionRuntime() }

  /** 异常结构自身即需要运行时，嵌套表达式继续递归以覆盖字段初始化中的异常构造。 */
  private fun JavaIrStatement.requiresExceptionRuntime(): Boolean = when (this) {
    is JavaIrStatement.Throw,
    is JavaIrStatement.Try,
    is JavaIrStatement.InitializeException -> true
    is JavaIrStatement.Block -> containsExceptionRuntime()
    is JavaIrStatement.DeclareLocal -> initializer?.requiresExceptionRuntime() == true
    is JavaIrStatement.Expression -> expression.requiresExceptionRuntime()
    is JavaIrStatement.If -> condition.requiresExceptionRuntime() ||
      thenBranch.requiresExceptionRuntime() || elseBranch?.requiresExceptionRuntime() == true
    is JavaIrStatement.While -> condition.requiresExceptionRuntime() || body.requiresExceptionRuntime()
    is JavaIrStatement.DoWhile -> body.requiresExceptionRuntime() || condition.requiresExceptionRuntime()
    is JavaIrStatement.For -> condition.requiresExceptionRuntime() ||
      updates.any { it.requiresExceptionRuntime() } || body.requiresExceptionRuntime()
    is JavaIrStatement.EnhancedFor -> iterable.requiresExceptionRuntime() || body.requiresExceptionRuntime()
    is JavaIrStatement.Switch -> selector.requiresExceptionRuntime() || entries.any { entry ->
      entry.statements.any { it.requiresExceptionRuntime() }
    }
    is JavaIrStatement.ConstructorInvocation -> arguments.any { it.requiresExceptionRuntime() }
    is JavaIrStatement.Return -> expression?.requiresExceptionRuntime() == true
    is JavaIrStatement.Break,
    is JavaIrStatement.Continue -> false
  }

  private fun JavaIrExpression.requiresExceptionRuntime(): Boolean = when (this) {
    is JavaIrExpression.ConstructBuiltin -> operation.isExceptionConstruction() ||
      arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.InvokeBuiltin -> receiver?.requiresExceptionRuntime() == true ||
      arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.SetLocal -> value.requiresExceptionRuntime()
    is JavaIrExpression.GetField -> receiver.requiresExceptionRuntime()
    is JavaIrExpression.SetField -> receiver.requiresExceptionRuntime() || value.requiresExceptionRuntime()
    is JavaIrExpression.SetStaticField -> value.requiresExceptionRuntime()
    is JavaIrExpression.Binary -> left.requiresExceptionRuntime() || right.requiresExceptionRuntime()
    is JavaIrExpression.Unary -> operand.requiresExceptionRuntime()
    is JavaIrExpression.Convert -> expression.requiresExceptionRuntime()
    is JavaIrExpression.InvokeStatic -> arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.InvokeSpecial -> receiver.requiresExceptionRuntime() || arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.InvokeVirtual -> receiver.requiresExceptionRuntime() || arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.InvokeVirtualSlot ->
      operation in setOf(
          JavaBuiltinOperation.THROWABLE_GET_MESSAGE,
          JavaBuiltinOperation.THROWABLE_GET_CAUSE,
          JavaBuiltinOperation.THROWABLE_TO_STRING,
        ) || receiver.requiresExceptionRuntime() || arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.Lambda -> body.containsExceptionRuntime() ||
      boundValues.any { it.requireNonNull || it.expression.requiresExceptionRuntime() }
    is JavaIrExpression.NewObject -> arguments.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.NewArray -> length.requiresExceptionRuntime()
    is JavaIrExpression.ArrayInitializer -> elements.any { it.requiresExceptionRuntime() }
    is JavaIrExpression.GetArrayElement -> array.requiresExceptionRuntime() || index.requiresExceptionRuntime()
    is JavaIrExpression.SetArrayElement -> array.requiresExceptionRuntime() ||
      index.requiresExceptionRuntime() || value.requiresExceptionRuntime()
    is JavaIrExpression.ArrayLength -> array.requiresExceptionRuntime()
    is JavaIrExpression.StringConcat -> parts.any { it.expression.requiresExceptionRuntime() }
    else -> false
  }

  /** builtin helper 仅在 typed IR 显式包含 builtin 节点时注入，普通快照保持不变。 */
  private fun usesBuiltinRuntime(): Boolean = index.program.classes.any { clazz ->
    clazz.fields.any { field -> field.initializer?.requiresBuiltinRuntime() == true } ||
      clazz.staticInitializer?.containsBuiltinRuntime() == true ||
      clazz.instanceInitializer?.containsBuiltinRuntime() == true ||
      clazz.methods.any { method -> method.body?.containsBuiltinRuntime() == true }
  }

  private fun JavaIrStatement.Block.containsBuiltinRuntime(): Boolean =
    statements.any { statement -> statement.requiresBuiltinRuntime() }

  /** 递归扫描控制流中的 builtin 节点，避免只看入口方法造成 helper 漏注入。 */
  private fun JavaIrStatement.requiresBuiltinRuntime(): Boolean = when (this) {
    is JavaIrStatement.Block -> containsBuiltinRuntime()
    is JavaIrStatement.DeclareLocal -> initializer?.requiresBuiltinRuntime() == true
    is JavaIrStatement.Expression -> expression.requiresBuiltinRuntime()
    is JavaIrStatement.If -> condition.requiresBuiltinRuntime() ||
      thenBranch.requiresBuiltinRuntime() || elseBranch?.requiresBuiltinRuntime() == true
    is JavaIrStatement.While -> condition.requiresBuiltinRuntime() || body.requiresBuiltinRuntime()
    is JavaIrStatement.DoWhile -> body.requiresBuiltinRuntime() || condition.requiresBuiltinRuntime()
    is JavaIrStatement.For -> condition.requiresBuiltinRuntime() ||
      updates.any { it.requiresBuiltinRuntime() } || body.requiresBuiltinRuntime()
    is JavaIrStatement.EnhancedFor -> iterable.requiresBuiltinRuntime() ||
      elementConversions.any { it is JavaIrConversion.Boxing || it is JavaIrConversion.Unboxing } ||
      body.requiresBuiltinRuntime()
    is JavaIrStatement.Switch -> selector.requiresBuiltinRuntime() || entries.any { entry ->
      entry.label?.requiresBuiltinRuntime() == true || entry.statements.any { it.requiresBuiltinRuntime() }
    }
    is JavaIrStatement.Break,
    is JavaIrStatement.Continue -> false
    is JavaIrStatement.ConstructorInvocation -> arguments.any { argument -> argument.requiresBuiltinRuntime() }
    is JavaIrStatement.InitializeException -> message?.requiresBuiltinRuntime() == true ||
      cause?.requiresBuiltinRuntime() == true
    is JavaIrStatement.Return -> expression?.requiresBuiltinRuntime() == true
    is JavaIrStatement.Throw -> expression.requiresBuiltinRuntime()
    is JavaIrStatement.Try -> body.containsBuiltinRuntime() ||
      catches.any { it.body.containsBuiltinRuntime() } ||
      finallyBlock?.containsBuiltinRuntime() == true || resources.any { resource ->
        resource.initializer.requiresBuiltinRuntime() || resource.closeExpression.requiresBuiltinRuntime()
      }
  }

  /** builtin 可嵌套在 receiver、参数、转换与数组表达式中，扫描必须覆盖完整表达式树。 */
  private fun JavaIrExpression.requiresBuiltinRuntime(): Boolean = when (this) {
    is JavaIrExpression.BuiltinValue,
    is JavaIrExpression.InvokeBuiltin,
    is JavaIrExpression.ConstructBuiltin -> true
    is JavaIrExpression.SetLocal -> value.requiresBuiltinRuntime()
    is JavaIrExpression.GetField -> receiver.requiresBuiltinRuntime()
    is JavaIrExpression.SetField -> receiver.requiresBuiltinRuntime() || value.requiresBuiltinRuntime()
    is JavaIrExpression.SetStaticField -> value.requiresBuiltinRuntime()
    is JavaIrExpression.Binary -> left.requiresBuiltinRuntime() || right.requiresBuiltinRuntime()
    is JavaIrExpression.Unary -> operand.requiresBuiltinRuntime()
    is JavaIrExpression.Convert -> conversion is JavaIrConversion.Boxing ||
      conversion is JavaIrConversion.Unboxing || expression.requiresBuiltinRuntime()
    is JavaIrExpression.InvokeStatic -> arguments.any { argument -> argument.requiresBuiltinRuntime() }
    is JavaIrExpression.InvokeSpecial -> receiver.requiresBuiltinRuntime() || arguments.any { argument -> argument.requiresBuiltinRuntime() }
    is JavaIrExpression.InvokeVirtual -> receiver.requiresBuiltinRuntime() || arguments.any { argument -> argument.requiresBuiltinRuntime() }
    is JavaIrExpression.InvokeVirtualSlot -> true
    is JavaIrExpression.Lambda -> body.containsBuiltinRuntime() ||
      boundValues.any { it.expression.requiresBuiltinRuntime() }
    is JavaIrExpression.NewObject -> arguments.any { argument -> argument.requiresBuiltinRuntime() }
    is JavaIrExpression.NewArray -> length.requiresBuiltinRuntime()
    is JavaIrExpression.ArrayInitializer -> elements.any { element -> element.requiresBuiltinRuntime() }
    is JavaIrExpression.GetArrayElement -> array.requiresBuiltinRuntime() || index.requiresBuiltinRuntime()
    is JavaIrExpression.SetArrayElement -> array.requiresBuiltinRuntime() || index.requiresBuiltinRuntime() || value.requiresBuiltinRuntime()
    is JavaIrExpression.ArrayLength -> array.requiresBuiltinRuntime()
    is JavaIrExpression.StringConcat -> parts.any { it.expression.requiresBuiltinRuntime() }
    else -> false
  }

  /** collection helper 单独按 operation 扫描，未使用集合的旧程序不会携带集合运行时。 */
  private fun usesCollectionRuntime(): Boolean = index.program.classes.any { clazz ->
    clazz.fields.any { field -> field.initializer?.requiresCollectionRuntime() == true } ||
      clazz.staticInitializer?.containsCollectionRuntime() == true ||
      clazz.instanceInitializer?.containsCollectionRuntime() == true ||
      clazz.methods.any { method -> method.body?.containsCollectionRuntime() == true }
  }

  private fun JavaIrStatement.Block.containsCollectionRuntime(): Boolean =
    statements.any { statement -> statement.requiresCollectionRuntime() }

  /** 遍历结构化语句，确保仅藏在分支或构造参数里的集合节点也能触发注入。 */
  private fun JavaIrStatement.requiresCollectionRuntime(): Boolean = when (this) {
    is JavaIrStatement.Block -> containsCollectionRuntime()
    is JavaIrStatement.DeclareLocal -> initializer?.requiresCollectionRuntime() == true
    is JavaIrStatement.Expression -> expression.requiresCollectionRuntime()
    is JavaIrStatement.If -> condition.requiresCollectionRuntime() ||
      thenBranch.requiresCollectionRuntime() || elseBranch?.requiresCollectionRuntime() == true
    is JavaIrStatement.While -> condition.requiresCollectionRuntime() || body.requiresCollectionRuntime()
    is JavaIrStatement.DoWhile -> body.requiresCollectionRuntime() || condition.requiresCollectionRuntime()
    is JavaIrStatement.For -> condition.requiresCollectionRuntime() ||
      updates.any { it.requiresCollectionRuntime() } || body.requiresCollectionRuntime()
    is JavaIrStatement.EnhancedFor -> kind != JavaIrEnhancedForKind.ARRAY ||
      iterable.requiresCollectionRuntime() || body.requiresCollectionRuntime()
    is JavaIrStatement.Switch -> selector.requiresCollectionRuntime() || entries.any { entry ->
      entry.statements.any { it.requiresCollectionRuntime() }
    }
    is JavaIrStatement.Break,
    is JavaIrStatement.Continue -> false
    is JavaIrStatement.ConstructorInvocation -> arguments.any { it.requiresCollectionRuntime() }
    is JavaIrStatement.InitializeException -> message?.requiresCollectionRuntime() == true ||
      cause?.requiresCollectionRuntime() == true
    is JavaIrStatement.Return -> expression?.requiresCollectionRuntime() == true
    is JavaIrStatement.Throw -> expression.requiresCollectionRuntime()
    is JavaIrStatement.Try -> body.containsCollectionRuntime() ||
      catches.any { it.body.containsCollectionRuntime() } ||
      finallyBlock?.containsCollectionRuntime() == true || resources.any { resource ->
        resource.initializer.requiresCollectionRuntime() || resource.closeExpression.requiresCollectionRuntime()
      }
  }

  /** operation 是唯一判据；receiver/参数仍递归扫描，禁止按 Java 类型名或成员名猜测。 */
  private fun JavaIrExpression.requiresCollectionRuntime(): Boolean = when (this) {
    is JavaIrExpression.InvokeBuiltin -> operation.isCollectionOperation() ||
      receiver?.requiresCollectionRuntime() == true || arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.ConstructBuiltin -> operation.isCollectionOperation() ||
      arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.SetLocal -> value.requiresCollectionRuntime()
    is JavaIrExpression.GetField -> receiver.requiresCollectionRuntime()
    is JavaIrExpression.SetField -> receiver.requiresCollectionRuntime() || value.requiresCollectionRuntime()
    is JavaIrExpression.SetStaticField -> value.requiresCollectionRuntime()
    is JavaIrExpression.Binary -> left.requiresCollectionRuntime() || right.requiresCollectionRuntime()
    is JavaIrExpression.Unary -> operand.requiresCollectionRuntime()
    is JavaIrExpression.Convert -> expression.requiresCollectionRuntime()
    is JavaIrExpression.InvokeStatic -> arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.InvokeSpecial -> receiver.requiresCollectionRuntime() || arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.InvokeVirtual -> receiver.requiresCollectionRuntime() || arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.InvokeVirtualSlot -> receiver.requiresCollectionRuntime() ||
      arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.Lambda -> body.containsCollectionRuntime() ||
      boundValues.any { it.expression.requiresCollectionRuntime() }
    is JavaIrExpression.NewObject -> arguments.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.NewArray -> length.requiresCollectionRuntime()
    is JavaIrExpression.ArrayInitializer -> elements.any { it.requiresCollectionRuntime() }
    is JavaIrExpression.GetArrayElement -> array.requiresCollectionRuntime() || index.requiresCollectionRuntime()
    is JavaIrExpression.SetArrayElement -> array.requiresCollectionRuntime() ||
      index.requiresCollectionRuntime() || value.requiresCollectionRuntime()
    is JavaIrExpression.ArrayLength -> array.requiresCollectionRuntime()
    is JavaIrExpression.StringConcat -> parts.any { it.expression.requiresCollectionRuntime() }
    else -> false
  }

  /** Scanner runtime 与其他 builtin 分离，普通输出、字符串和集合程序不会携带输入解析代码。 */
  private fun usesScannerRuntime(): Boolean = index.program.classes.any { clazz ->
    clazz.fields.any { field -> field.initializer?.requiresScannerRuntime() == true } ||
      clazz.staticInitializer?.containsScannerRuntime() == true ||
      clazz.instanceInitializer?.containsScannerRuntime() == true ||
      clazz.methods.any { method -> method.body?.containsScannerRuntime() == true }
  }

  private fun JavaIrStatement.Block.containsScannerRuntime(): Boolean =
    statements.any { statement -> statement.requiresScannerRuntime() }

  /** 遍历全部结构化语句，避免只在异常分支使用 Scanner 时漏注入 helper。 */
  private fun JavaIrStatement.requiresScannerRuntime(): Boolean = when (this) {
    is JavaIrStatement.Block -> containsScannerRuntime()
    is JavaIrStatement.DeclareLocal -> initializer?.requiresScannerRuntime() == true
    is JavaIrStatement.Expression -> expression.requiresScannerRuntime()
    is JavaIrStatement.If -> condition.requiresScannerRuntime() ||
      thenBranch.requiresScannerRuntime() || elseBranch?.requiresScannerRuntime() == true
    is JavaIrStatement.While -> condition.requiresScannerRuntime() || body.requiresScannerRuntime()
    is JavaIrStatement.DoWhile -> body.requiresScannerRuntime() || condition.requiresScannerRuntime()
    is JavaIrStatement.For -> condition.requiresScannerRuntime() ||
      updates.any { it.requiresScannerRuntime() } || body.requiresScannerRuntime()
    is JavaIrStatement.EnhancedFor -> iterable.requiresScannerRuntime() || body.requiresScannerRuntime()
    is JavaIrStatement.Switch -> selector.requiresScannerRuntime() || entries.any { entry ->
      entry.statements.any { it.requiresScannerRuntime() }
    }
    is JavaIrStatement.Break,
    is JavaIrStatement.Continue -> false
    is JavaIrStatement.ConstructorInvocation -> arguments.any { it.requiresScannerRuntime() }
    is JavaIrStatement.InitializeException -> message?.requiresScannerRuntime() == true ||
      cause?.requiresScannerRuntime() == true
    is JavaIrStatement.Return -> expression?.requiresScannerRuntime() == true
    is JavaIrStatement.Throw -> expression.requiresScannerRuntime()
    is JavaIrStatement.Try -> body.containsScannerRuntime() ||
      catches.any { it.body.containsScannerRuntime() } ||
      finallyBlock?.containsScannerRuntime() == true || resources.any { resource ->
        resource.initializer.requiresScannerRuntime() || resource.closeExpression.requiresScannerRuntime()
      }
  }

  /** 只认稳定 operation；嵌套 receiver、参数与转换继续递归检查。 */
  private fun JavaIrExpression.requiresScannerRuntime(): Boolean = when (this) {
    is JavaIrExpression.InvokeBuiltin -> operation.isScannerOperation() ||
      receiver?.requiresScannerRuntime() == true || arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.ConstructBuiltin -> operation.isScannerOperation() ||
      arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.SetLocal -> value.requiresScannerRuntime()
    is JavaIrExpression.GetField -> receiver.requiresScannerRuntime()
    is JavaIrExpression.SetField -> receiver.requiresScannerRuntime() || value.requiresScannerRuntime()
    is JavaIrExpression.SetStaticField -> value.requiresScannerRuntime()
    is JavaIrExpression.Binary -> left.requiresScannerRuntime() || right.requiresScannerRuntime()
    is JavaIrExpression.Unary -> operand.requiresScannerRuntime()
    is JavaIrExpression.Convert -> expression.requiresScannerRuntime()
    is JavaIrExpression.InvokeStatic -> arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.InvokeSpecial -> receiver.requiresScannerRuntime() || arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.InvokeVirtual -> receiver.requiresScannerRuntime() || arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.InvokeVirtualSlot -> receiver.requiresScannerRuntime() ||
      arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.Lambda -> body.containsScannerRuntime() ||
      boundValues.any { it.expression.requiresScannerRuntime() }
    is JavaIrExpression.NewObject -> arguments.any { it.requiresScannerRuntime() }
    is JavaIrExpression.NewArray -> length.requiresScannerRuntime()
    is JavaIrExpression.ArrayInitializer -> elements.any { it.requiresScannerRuntime() }
    is JavaIrExpression.GetArrayElement -> array.requiresScannerRuntime() || index.requiresScannerRuntime()
    is JavaIrExpression.SetArrayElement -> array.requiresScannerRuntime() ||
      index.requiresScannerRuntime() || value.requiresScannerRuntime()
    is JavaIrExpression.ArrayLength -> array.requiresScannerRuntime()
    is JavaIrExpression.StringConcat -> parts.any { it.expression.requiresScannerRuntime() }
    else -> false
  }

  /** 扫描表达式树决定是否注入数组/字符串 helper，不影响既有纯 static 快照。 */
  private fun JavaIrStatement.Block.containsArrayOrStringRuntime(): Boolean =
    statements.any { statement -> statement.requiresArrayOrStringRuntime() }

  /** 结构化语句的分支也必须参与扫描，否则条件路径可能缺失所需 runtime。 */
  private fun JavaIrStatement.requiresArrayOrStringRuntime(): Boolean = when (this) {
    is JavaIrStatement.Block -> containsArrayOrStringRuntime()
    is JavaIrStatement.DeclareLocal -> initializer?.requiresArrayOrStringRuntime() == true
    is JavaIrStatement.Expression -> expression.requiresArrayOrStringRuntime()
    is JavaIrStatement.If -> condition.requiresArrayOrStringRuntime() ||
      thenBranch.requiresArrayOrStringRuntime() ||
      (elseBranch?.requiresArrayOrStringRuntime() == true)
    is JavaIrStatement.While -> condition.requiresArrayOrStringRuntime() || body.requiresArrayOrStringRuntime()
    is JavaIrStatement.DoWhile -> body.requiresArrayOrStringRuntime() ||
      condition.requiresArrayOrStringRuntime()
    is JavaIrStatement.For -> condition.requiresArrayOrStringRuntime() ||
      updates.any { it.requiresArrayOrStringRuntime() } || body.requiresArrayOrStringRuntime()
    is JavaIrStatement.EnhancedFor -> kind == JavaIrEnhancedForKind.ARRAY ||
      iterable.requiresArrayOrStringRuntime() || body.requiresArrayOrStringRuntime()
    is JavaIrStatement.Switch -> selector.type.hasBuiltinRole(JavaBuiltinTypeRole.STRING) ||
      selector.requiresArrayOrStringRuntime() || entries.any { entry ->
      entry.label?.requiresArrayOrStringRuntime() == true ||
        entry.statements.any { it.requiresArrayOrStringRuntime() }
    }
    is JavaIrStatement.Break,
    is JavaIrStatement.Continue -> false
    is JavaIrStatement.ConstructorInvocation -> arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrStatement.InitializeException -> message?.requiresArrayOrStringRuntime() == true ||
      cause?.requiresArrayOrStringRuntime() == true
    is JavaIrStatement.Return -> expression?.requiresArrayOrStringRuntime() == true
    is JavaIrStatement.Throw -> expression.requiresArrayOrStringRuntime()
    is JavaIrStatement.Try -> body.containsArrayOrStringRuntime() ||
      catches.any { it.body.containsArrayOrStringRuntime() } ||
      finallyBlock?.containsArrayOrStringRuntime() == true || resources.any { resource ->
        resource.initializer.requiresArrayOrStringRuntime() ||
          resource.closeExpression.requiresArrayOrStringRuntime()
      }
  }

  /** 递归扫描嵌套表达式，避免例如数组索引藏在 static 调用参数中而漏注入 helper。 */
  private fun JavaIrExpression.requiresArrayOrStringRuntime(): Boolean = when (this) {
    is JavaIrExpression.NewArray,
    is JavaIrExpression.ArrayInitializer,
    is JavaIrExpression.GetArrayElement,
    is JavaIrExpression.SetArrayElement,
    is JavaIrExpression.ArrayLength,
    is JavaIrExpression.StringConcat,
    -> true
    is JavaIrExpression.InvokeBuiltin -> operation in charArrayBuiltinOperations ||
      receiver?.requiresArrayOrStringRuntime() == true ||
      arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrExpression.ConstructBuiltin -> arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrExpression.SetLocal -> value.requiresArrayOrStringRuntime()
    is JavaIrExpression.GetField -> receiver.requiresArrayOrStringRuntime()
    is JavaIrExpression.SetField -> receiver.requiresArrayOrStringRuntime() || value.requiresArrayOrStringRuntime()
    is JavaIrExpression.SetStaticField -> value.requiresArrayOrStringRuntime()
    is JavaIrExpression.Binary -> left.requiresArrayOrStringRuntime() || right.requiresArrayOrStringRuntime()
    is JavaIrExpression.Unary -> operand.requiresArrayOrStringRuntime()
    is JavaIrExpression.Convert -> expression.requiresArrayOrStringRuntime()
    is JavaIrExpression.InvokeStatic -> arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrExpression.InvokeSpecial -> receiver.requiresArrayOrStringRuntime() || arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrExpression.InvokeVirtual -> receiver.requiresArrayOrStringRuntime() || arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrExpression.InvokeVirtualSlot -> receiver.requiresArrayOrStringRuntime() ||
      arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrExpression.Lambda -> body.containsArrayOrStringRuntime() ||
      boundValues.any { it.expression.requiresArrayOrStringRuntime() }
    is JavaIrExpression.NewObject -> arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    else -> false
  }

  /** 先声明所有 prototype，允许跨文件父类和前向静态引用。 */
  private fun emitClassShells() {
    classesInParentFirstOrder.forEach { clazz ->
      val parent = clazz.superClass?.let(JsNameMangler::prototype) ?: "null"
      writer.line("const ${JsNameMangler.staticStorage(clazz.id)} = { state: 0, error: null, values: Object.create(null) };")
      // 类初始化尚未开始时，字段已按 Java 默认值可见；这也覆盖递归 clinit 的 in-progress 读取。
      clazz.fields.filter { it.isStatic }.forEach { field ->
        writer.line("${JsNameMangler.staticStorage(clazz.id)}.values[\"${JsNameMangler.field(field.id)}\"] = ${defaultValue(field.type)};")
      }
      writer.line("const ${JsNameMangler.prototype(clazz.id)} = Object.create($parent);")
      writer.line("${JsNameMangler.prototype(clazz.id)}[\"\$__j_class_name\"] = ${writer.stringLiteral(clazz.qualifiedName)};")
      clazz.exceptionSuperQualifiedName?.let { exceptionParent ->
        writer.line("\$__j_register_exception_type(${writer.stringLiteral(clazz.qualifiedName)}, ${writer.stringLiteral(exceptionParent)});")
        writer.line("${JsNameMangler.prototype(clazz.id)}[\"\$__j_exception_name\"] = ${writer.stringLiteral(clazz.qualifiedName)};")
      }
    }
    index.program.classes.flatMap { it.methods }
      .filter { it.virtualSlot != null && it.body != null }
      .sortedBy { it.id.value }
      .forEach { method ->
      writer.line("${JsNameMangler.prototype(method.owner)}[\"${JsNameMangler.virtualSlot(checkNotNull(method.virtualSlot))}\"] = ${JsNameMangler.method(method.id)};")
    }
    classesInParentFirstOrder.filter { it.kind == JavaIrTypeDeclarationKind.CLASS }.forEach { clazz ->
      clazz.interfaceDefaultMethods.entries.sortedBy { it.key }.forEach { (slot, method) ->
        val key = JsNameMangler.virtualSlot(slot)
        // 语义层已经解决 default 冲突；这里仅安装显式选择结果，class/superclass 自身实现优先。
        writer.line("if (!(\"$key\" in ${JsNameMangler.prototype(clazz.id)})) ${JsNameMangler.prototype(clazz.id)}[\"$key\"] = ${JsNameMangler.method(method)};")
      }
    }
    writer.line()
  }

  /** 每个类的 clinit 与实例初始化器均以函数声明输出，调用顺序由 constructor IR 保证。 */
  private fun emitClassInitializers() {
    classesInParentFirstOrder.forEach { clazz ->
      emitClassInitializer(clazz)
      emitInstanceDefaultInitializer(clazz)
      emitInstanceInitializer(clazz)
    }
  }

  /** 父类优先、至多一次、失败缓存的 lazy class initialization。 */
  private fun emitClassInitializer(clazz: JavaIrClass) {
    val storage = JsNameMangler.staticStorage(clazz.id)
    writer.line("function ${JsNameMangler.classInitializer(clazz.id)}() {")
    writer.indented {
      writer.line("const meta = $storage;")
      writer.line("if (meta.state === 2) return;")
      writer.line("if (meta.state === 3) throw meta.error;")
      writer.line("if (meta.state === 1) return;")
      writer.line("meta.state = 1;")
      writer.line("try {")
      writer.indented {
        clazz.superClass?.let { writer.line("${JsNameMangler.classInitializer(it)}();") }
        clazz.fields.filter { it.isStatic && it.initializer != null }.forEach { field ->
          writer.line("meta.values[\"${JsNameMangler.field(field.id)}\"] = ${renderExpression(checkNotNull(field.initializer))};")
        }
        clazz.staticInitializer?.let(::emitBlockContents)
        writer.line("meta.state = 2;")
      }
      writer.line("} catch (error) { meta.state = 3; meta.error = error; throw error; }")
    }
    writer.line("}")
  }

  /** 对象分配时按 parent 到 child 一次性填充整个实例字段集合的 Java 默认值。 */
  private fun emitInstanceDefaultInitializer(clazz: JavaIrClass) {
    writer.line("function ${JsNameMangler.instanceDefaultInitializer(clazz.id)}(self) {")
    writer.indented {
      clazz.superClass?.let { writer.line("${JsNameMangler.instanceDefaultInitializer(it)}(self);") }
      clazz.fields.filter { !it.isStatic }.forEach { field ->
        writer.line("self[\"${JsNameMangler.field(field.id)}\"] = ${defaultValue(field.type)};")
      }
    }
    writer.line("}")
  }

  /** 仅执行本类显式字段初始化与初始化块，默认值已在分配阶段完成。 */
  private fun emitInstanceInitializer(clazz: JavaIrClass) {
    writer.line("function ${JsNameMangler.instanceInitializer(clazz.id)}(self) {")
    writer.indented {
      currentThisName = "self"
      try {
        clazz.fields.filter { !it.isStatic && it.initializer != null }.forEach { field ->
          writer.line("self[\"${JsNameMangler.field(field.id)}\"] = ${renderExpression(checkNotNull(field.initializer))};")
        }
        clazz.instanceInitializer?.let(::emitBlockContents)
      } finally {
        currentThisName = "this"
      }
    }
    writer.line("}")
    writer.line()
  }

  /** 生成一个实现 Java static 方法、参数按 stable id 命名的普通 JS function。 */
  private fun emitMethod(method: JavaIrMethod) {
    val parameters = method.parameters.joinToString(", ") { parameter ->
      JsNameMangler.local(parameter.id)
    }
    writer.line("function ${JsNameMangler.method(method.id)}(${parameters}) {")
    writer.indented {
      method.parameters
        .filter { parameter -> parameter.type.isStage0Integral() }
        .forEach { parameter ->
          val name = JsNameMangler.local(parameter.id)
          writer.line("$name = $name | 0;")
        }
      currentMethod = method
      try {
        // 根类的 this() 链仅由终点构造器初始化一次，避免委托前后重复执行字段初始化。
        if (method.kind == JavaIrMethodKind.CONSTRUCTOR &&
          index.classes[method.owner]?.superClass == null &&
          constructorInvocationKind(method) != JavaIrConstructorInvocationKind.THIS
        ) {
          writer.line("${JsNameMangler.instanceInitializer(method.owner)}(this);")
        }
        emitBlockContents(checkNotNull(method.body))
      } finally { currentMethod = null }
    }
    writer.line("}")
    writer.line()
  }

  /** 读取 validator 已保证位于构造器直接首句的委托种类。 */
  private fun constructorInvocationKind(method: JavaIrMethod): JavaIrConstructorInvocationKind? =
    (method.body?.statements?.firstOrNull() as? JavaIrStatement.ConstructorInvocation)?.kind

  /** 输出稳定入口；剩余调用参数会由目标 static 方法的 JS 形参自然忽略。 */
  private fun emitEntryExport(entry: JavaIrMethod) {
    writer.line("export function ${JavaModuleLayout.ENTRY_EXPORT_NAME}(...args) {")
    writer.indented {
      if (usesObjectRuntime()) writer.line("${JsNameMangler.classInitializer(entry.owner)}();")
      writer.line("return ${JsNameMangler.method(entry.id)}(...args);")
    }
    writer.line("}")
  }

  /** 不额外生成一层根花括号，保持 Java 方法体和 JS function body 对齐。 */
  private fun emitBlockContents(block: JavaIrStatement.Block) {
    block.statements.forEach(::emitStatement)
  }

  /** 生成结构化语句，并在表达式起点记录最小 Java source mapping。 */
  private fun emitStatement(statement: JavaIrStatement) {
    when (statement) {
      is JavaIrStatement.Block -> {
        writer.line("{")
        writer.indented { emitBlockContents(statement) }
        writer.line("}")
      }
      is JavaIrStatement.DeclareLocal -> {
        val local = index.locals.getValue(statement.local)
        val initializer = statement.initializer
        val source = if (initializer == null) {
          "let ${JsNameMangler.local(local.id)};"
        } else {
          "let ${JsNameMangler.local(local.id)} = ${coerceToType(renderExpression(initializer), local.type)};"
        }
        writer.writeIndentation()
        writer.writeMapped(source, statement.span)
        writer.write("\n")
      }
      is JavaIrStatement.Expression -> {
        writer.writeIndentation()
        writer.writeMapped(renderExpression(statement.expression), statement.expression.span)
        writer.write(";\n")
      }
      is JavaIrStatement.If -> {
        writer.writeIndentation()
        writer.write("if (")
        writer.writeMapped(renderExpression(statement.condition), statement.condition.span)
        writer.write(") ")
        emitBranch(statement.thenBranch)
        statement.elseBranch?.let { elseBranch ->
          writer.writeIndentation()
          writer.write("else ")
          emitBranch(elseBranch)
        }
      }
      is JavaIrStatement.While -> {
        writer.writeIndentation()
        writer.write("while (")
        writer.writeMapped(renderExpression(statement.condition), statement.condition.span)
        writer.write(") ")
        emitBranch(statement.body)
      }
      is JavaIrStatement.EnhancedFor -> emitEnhancedFor(statement)
      is JavaIrStatement.Switch -> {
        writer.writeIndentation()
        writer.write("switch (")
        val selector = renderExpression(statement.selector)
        val selectorCode = if (statement.selector.type.hasBuiltinRole(JavaBuiltinTypeRole.STRING)) {
          "\$__j_non_null($selector)"
        } else {
          selector
        }
        writer.writeMapped(selectorCode, statement.selector.span)
        writer.write(") {\n")
        writer.indented {
          statement.entries.forEach { entry ->
            writer.writeIndentation()
            val label = entry.label
            if (label == null) writer.write("default:\n")
            else writer.write("case ${renderConstant(label.value)}:\n")
            writer.indented { entry.statements.forEach(::emitStatement) }
          }
        }
        writer.line("}")
      }
      is JavaIrStatement.DoWhile -> {
        writer.writeIndentation()
        writer.write("do ")
        emitBranch(statement.body)
        writer.writeIndentation()
        writer.write("while (")
        writer.writeMapped(renderExpression(statement.condition), statement.condition.span)
        writer.write(");\n")
      }
      is JavaIrStatement.For -> {
        writer.writeIndentation()
        writer.write("for (; ")
        writer.writeMapped(renderExpression(statement.condition), statement.condition.span)
        writer.write("; ")
        writer.write(statement.updates.joinToString(", ") { renderExpression(it) })
        writer.write(") ")
        emitBranch(statement.body)
      }
      is JavaIrStatement.Break -> writer.line("break;")
      is JavaIrStatement.Continue -> writer.line("continue;")
      is JavaIrStatement.ConstructorInvocation -> {
        val arguments = statement.arguments.joinToString(", ") { renderExpression(it) }
        writer.writeIndentation()
        writer.writeMapped(
          "${JsNameMangler.method(statement.constructor)}.call(this${if (arguments.isEmpty()) "" else ", $arguments"});",
          statement.span,
        )
        writer.write("\n")
        if (statement.kind == JavaIrConstructorInvocationKind.SUPER) {
          val owner = checkNotNull(currentMethod).owner
          writer.line("${JsNameMangler.instanceInitializer(owner)}(this);")
        }
      }
      is JavaIrStatement.InitializeException -> {
        val owner = checkNotNull(currentMethod).owner
        val clazz = index.classes.getValue(owner)
        val message = statement.message?.let(::renderExpression) ?: "null"
        val cause = statement.cause?.let(::renderExpression) ?: "null"
        writer.writeIndentation()
        writer.writeMapped(
          "\$__j_initialize_exception(this, ${writer.stringLiteral(clazz.qualifiedName)}, $message, $cause);",
          statement.span,
        )
        writer.write("\n")
        // builtin super 构造器完成后再执行当前类实例字段初始化，与普通 SUPER 委托保持一致。
        writer.line("${JsNameMangler.instanceInitializer(owner)}(this);")
      }
      is JavaIrStatement.Return -> {
        val expression = statement.expression
        if (expression == null) {
          writer.line("return;")
        } else {
          writer.writeIndentation()
          writer.write("return ")
          writer.writeMapped(renderExpression(expression), expression.span)
          writer.write(";\n")
        }
      }
      is JavaIrStatement.Throw -> {
        writer.writeIndentation()
        writer.writeMapped("\$__j_throw(${renderExpression(statement.expression)});", statement.span)
        writer.write("\n")
      }
      is JavaIrStatement.Try -> emitTry(statement)
    }
  }

  /**
   * 发射单个原生 try/catch/finally；原生 finally 会自然覆盖 return/break/continue/throw。
   * 多个 Java catch 在同一 JavaScript catch 中按源码顺序匹配，未命中值必须原样再次抛出。
   */
  private fun emitTry(statement: JavaIrStatement.Try) {
    writer.writeIndentation()
    writer.write("try ")
    if (statement.resources.isEmpty()) emitBranch(statement.body)
    else emitResourceScope(statement.resources, 0, statement.body)
    if (statement.catches.isNotEmpty()) {
      val errorName = "\$__j_caught_${currentMethod?.id?.value ?: 0}_${statement.span.from}"
      writer.writeIndentation()
      writer.write("catch ($errorName) {\n")
      writer.indented {
        statement.catches.forEachIndexed { position, clause ->
          val alternatives = clause.exceptionTypes.joinToString(" || ") { type ->
            val qualifiedName = exceptionQualifiedName(type)
            "\$__j_exception_is($errorName, ${writer.stringLiteral(qualifiedName)})"
          }
          writer.writeIndentation()
          writer.write(if (position == 0) "if" else "else if")
          writer.write(" ($alternatives) {\n")
          writer.indented {
            writer.line("let ${JsNameMangler.local(clause.local)} = $errorName;")
            emitBlockContents(clause.body)
          }
          writer.line("}")
        }
        writer.line("else { throw $errorName; }")
      }
      writer.line("}")
    }
    statement.finallyBlock?.let { finallyBlock ->
      writer.writeIndentation()
      writer.write("finally ")
      emitBranch(finallyBlock)
    }
  }

  /**
   * 将资源列表递归展开成嵌套 try/finally。
   *
   * 后声明资源位于更内层，因此天然先关闭；关闭失败仅在已有主异常时进入 suppressed 列表。
   */
  private fun emitResourceScope(
    resources: List<JavaIrResource>,
    index: Int,
    body: JavaIrStatement.Block,
  ) {
    if (index >= resources.size) {
      emitBranch(body)
      return
    }
    val resource = resources[index]
    val local = JsNameMangler.local(resource.local)
    val primary = "\$__j_primary_${resource.local.value}"
    val thrown = "\$__j_resource_thrown_${resource.local.value}"
    val closing = "\$__j_close_thrown_${resource.local.value}"
    writer.write("{\n")
    writer.indented {
      writer.line("let $local = ${renderExpression(resource.initializer)};")
      writer.line("let $primary = null;")
      writer.writeIndentation()
      writer.write("try ")
      emitResourceScope(resources, index + 1, body)
      writer.line("catch ($thrown) { $primary = $thrown; throw $thrown; }")
      writer.line("finally {")
      writer.indented {
        writer.line("if ($local !== null) {")
        writer.indented {
          writer.line("if ($primary !== null) {")
          writer.indented {
            writer.line("try { ${renderExpression(resource.closeExpression)}; } " +
              "catch ($closing) { \$__j_add_suppressed($primary, $closing); }")
          }
          writer.line("} else {")
          writer.indented { writer.line("${renderExpression(resource.closeExpression)};") }
          writer.line("}")
        }
        writer.line("}")
      }
      writer.line("}")
    }
    writer.line("}")
  }

  /** catch 目标可以是 builtin 异常，也可以是携带异常父链元数据的源码 class。 */
  private fun exceptionQualifiedName(type: JavaIrType.Reference): String {
    index.program.builtinTypeRoles[type.classId]?.exceptionQualifiedName()?.let { return it }
    val clazz = index.classes[type.classId]
    if (clazz?.exceptionSuperQualifiedName != null) return clazz.qualifiedName
    error("Validated catch type is missing exception metadata.")
  }

  /** 增强 for 只求值 iterable 一次，并用稳定数组/集合协议驱动循环。 */
  private fun emitEnhancedFor(statement: JavaIrStatement.EnhancedFor) {
    val local = index.locals.getValue(statement.local)
    val suffix = statement.local.value
    val source = "\$__j_enhanced_source_$suffix"
    val cursor = "\$__j_enhanced_cursor_$suffix"
    val indexName = "\$__j_enhanced_index_$suffix"
    writer.line("{")
    writer.indented {
      writer.writeIndentation()
      writer.write("const $source = ")
      writer.writeMapped(renderExpression(statement.iterable), statement.iterable.span)
      writer.write(";\n")
      writer.line("if ($source == null) throw new Error(\"java.lang.NullPointerException\");")
      val rawElement: String
      when (statement.kind) {
        JavaIrEnhancedForKind.ARRAY -> {
          writer.line("for (let $indexName = 0; $indexName < $source.length; $indexName++) {")
          rawElement = "$source[$indexName]"
        }
        JavaIrEnhancedForKind.LIST -> {
          writer.line("const $cursor = \$__j_list_iterator($source);")
          writer.line("while (\$__j_iterator_has_next($cursor)) {")
          rawElement = "\$__j_iterator_next($cursor)"
        }
        JavaIrEnhancedForKind.SET -> {
          writer.line("const $cursor = \$__j_set_iterator($source);")
          writer.line("while (\$__j_iterator_has_next($cursor)) {")
          rawElement = "\$__j_iterator_next($cursor)"
        }
      }
      writer.indented {
        val converted = renderConversions(
          statement.elementConversions, rawElement, statement.elementType,
        )
        writer.line("let ${JsNameMangler.local(local.id)} = ${coerceToType(converted, local.type)};")
        val body = statement.body
        if (body is JavaIrStatement.Block) emitBlockContents(body) else emitStatement(body)
      }
      writer.line("}")
    }
    writer.line("}")
  }

  /** 对非 block 分支补齐花括号，避免条件与循环生成时产生悬挂 else。 */
  private fun emitBranch(statement: JavaIrStatement) {
    if (statement is JavaIrStatement.Block) {
      writer.write("{\n")
      writer.indented { emitBlockContents(statement) }
      writer.line("}")
    } else {
      writer.write("{\n")
      writer.indented { emitStatement(statement) }
      writer.line("}")
    }
  }

  /** 将单个 typed IR 表达式渲染为带充分括号的 JavaScript 文本。 */
  private fun renderExpression(expression: JavaIrExpression): String {
    return when (expression) {
      is JavaIrExpression.Constant -> renderConstant(expression.value)
      is JavaIrExpression.GetLocal -> JsNameMangler.local(expression.local)
      is JavaIrExpression.This -> currentThisName
      is JavaIrExpression.SetLocal -> {
        val local = index.locals.getValue(expression.local)
        "(${JsNameMangler.local(expression.local)} = " +
          coerceToType(renderExpression(expression.value), local.type) + ")"
      }
      is JavaIrExpression.Binary -> renderBinary(expression)
      is JavaIrExpression.Unary -> renderUnary(expression)
      is JavaIrExpression.Convert -> renderConversion(expression)
      is JavaIrExpression.InvokeStatic -> {
        val arguments = expression.arguments.joinToString(", ") { argument -> renderExpression(argument) }
        index.ownerOf(expression.method)?.let { owner ->
          if (usesObjectRuntime()) {
            // Java 先完成全部实参求值，再在真正 invokestatic 前触发目标类初始化。
            "((values) => (${JsNameMangler.classInitializer(owner.id)}(), ${JsNameMangler.method(expression.method)}(...values)))([$arguments])"
          } else {
            "${JsNameMangler.method(expression.method)}($arguments)"
          }
        } ?: error("Validated Java IR references an unknown static method.")
      }
      is JavaIrExpression.GetField ->
        "\$__j_non_null(${renderExpression(expression.receiver)})[\"${JsNameMangler.field(expression.field)}\"]"
      is JavaIrExpression.SetField -> {
        val receiver = renderExpression(expression.receiver)
        val value = renderExpression(expression.value)
        "((receiver, value) => (\$__j_non_null(receiver)[\"${JsNameMangler.field(expression.field)}\"] = value))($receiver, $value)"
      }
      is JavaIrExpression.GetStaticField -> {
        val owner = index.fields.getValue(expression.field).owner
        "(${JsNameMangler.classInitializer(owner)}(), ${JsNameMangler.staticStorage(owner)}.values[\"${JsNameMangler.field(expression.field)}\"])"
      }
      is JavaIrExpression.BuiltinValue -> when (expression.operation) {
        JavaBuiltinOperation.SYSTEM_OUT -> "0"
        JavaBuiltinOperation.SYSTEM_ERR -> "1"
        JavaBuiltinOperation.SYSTEM_IN -> "2"
        else -> error("Validated Java IR builtin value has a non-stream operation.")
      }
      is JavaIrExpression.SetStaticField -> {
        val owner = index.fields.getValue(expression.field).owner
        // Java putstatic 的右值先求值，随后才初始化字段所属类并执行写入。
        "((value) => (${JsNameMangler.classInitializer(owner)}(), ${JsNameMangler.staticStorage(owner)}.values[\"${JsNameMangler.field(expression.field)}\"] = value))(${renderExpression(expression.value)})"
      }
      is JavaIrExpression.InvokeSpecial -> {
        val args = expression.arguments.joinToString(", ") { renderExpression(it) }
        "((receiver${if (args.isEmpty()) "" else ", values"}) => ${JsNameMangler.method(expression.method)}.call(\$__j_non_null(receiver)${if (args.isEmpty()) "" else ", ...values"}))(${renderExpression(expression.receiver)}${if (args.isEmpty()) "" else ", [$args]"})"
      }
      is JavaIrExpression.InvokeBuiltin -> renderBuiltinInvocation(expression)
      is JavaIrExpression.ConstructBuiltin -> when (expression.operation) {
        JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_EMPTY -> "\$__j_sb_new()"
        JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING ->
          "\$__j_sb_new_string(${renderExpression(expression.arguments.single())})"
        JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT -> "\$__j_list_new()"
        JavaBuiltinOperation.HASH_SET_CONSTRUCT -> "\$__j_set_new()"
        JavaBuiltinOperation.HASH_MAP_CONSTRUCT -> "\$__j_map_new()"
        JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM ->
          "\$__j_scanner_new(${renderExpression(expression.arguments.single())})"
        JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY,
        JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING,
        JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE -> {
          val reference = expression.type
          val role = index.program.builtinTypeRoles[reference.classId]
            ?: error("Validated exception construction is missing its builtin role.")
          val name = role.exceptionQualifiedName()
            ?: error("Validated exception construction has a non-exception role.")
          val message = expression.arguments.singleOrNull()?.let(::renderExpression) ?: "null"
          val cause = expression.arguments.getOrNull(1)?.let(::renderExpression) ?: "null"
          val resolvedMessage = expression.arguments.getOrNull(0)?.let(::renderExpression) ?: message
          "\$__j_new_exception(${writer.stringLiteral(name)}, $resolvedMessage, $cause)"
        }
        else -> error("Validated builtin construction has an unsupported operation.")
      }
      is JavaIrExpression.InvokeVirtual -> {
        val receiver = renderExpression(expression.receiver)
        val args = expression.arguments.joinToString(", ") { renderExpression(it) }
        "((receiver${if (args.isEmpty()) "" else ", values"}) => \$__j_non_null(receiver)[\"${JsNameMangler.virtualSlot(expression.virtualSlot)}\"](${if (args.isEmpty()) "" else "...values"}))($receiver${if (args.isEmpty()) "" else ", [$args]"})"
      }
      is JavaIrExpression.InvokeVirtualSlot -> renderVirtualSlotInvocation(expression)
      is JavaIrExpression.Lambda -> renderLambda(expression)
      is JavaIrExpression.NewObject -> {
        val args = expression.arguments.joinToString(", ") { renderExpression(it) }
        "(${JsNameMangler.classInitializer(expression.classId)}(), (() => { const value = Object.create(${JsNameMangler.prototype(expression.classId)}); ${JsNameMangler.instanceDefaultInitializer(expression.classId)}(value); ${JsNameMangler.method(expression.constructor)}.call(value${if (args.isEmpty()) "" else ", $args"}); return value; })())"
      }
      is JavaIrExpression.NewArray ->
        "\$__j_new_array(${renderExpression(expression.length)}, ${defaultValue(expression.componentType)}, ${renderArrayComponent(expression.componentType, expression.referenceComponentKind)})"
      is JavaIrExpression.ArrayInitializer -> {
        val writes = expression.elements.mapIndexed { index, element ->
          "\$__j_array_set(value, $index, ${renderExpression(element)})"
        }.joinToString("; ")
        "(() => { const value = \$__j_new_array(${expression.elements.size}, ${defaultValue(expression.componentType)}, ${renderArrayComponent(expression.componentType, expression.referenceComponentKind)}); $writes${if (writes.isEmpty()) "" else ";"} return value; })()"
      }
      is JavaIrExpression.GetArrayElement ->
        "((array, index) => \$__j_array(array)[\$__j_array_index(array, index)])(${renderExpression(expression.array)}, ${renderExpression(expression.index)})"
      is JavaIrExpression.SetArrayElement ->
        "((array, index, value) => \$__j_array_set(array, index, value))(${renderExpression(expression.array)}, ${renderExpression(expression.index)}, ${renderExpression(expression.value)})"
      is JavaIrExpression.ArrayLength ->
        "\$__j_array(${renderExpression(expression.array)}).length"
      is JavaIrExpression.StringConcat -> {
        val values = expression.parts.joinToString(", ") { part -> renderExpression(part.expression) }
        val kinds = expression.parts.joinToString(", ") { part -> writer.stringLiteral(part.conversion.name) }
        "\$__j_string_concat([$values], [$kinds])"
      }
    }
  }

  /**
   * 把 Lambda body 写入隔离 writer 后嵌入表达式；箭头函数确保 `this` 指向外围 Java 实例。
   *
   * Lambda 局部仍使用工作区唯一 local id，因此嵌套闭包可直接引用外围参数/局部而无需复制值。
   */
  private fun renderLambda(expression: JavaIrExpression.Lambda): String {
    val previousWriter = writer
    val bodyWriter = JsWriter()
    writer = bodyWriter
    try {
      emitBlockContents(expression.body)
    } finally {
      writer = previousWriter
    }
    val parameters = expression.parameters.joinToString(", ") { JsNameMangler.local(it) }
    val prototype = index.classes[expression.interfaceClass]
      ?.let { JsNameMangler.prototype(it.id) }
      ?: "null"
    val slot = JsNameMangler.virtualSlot(expression.virtualSlot)
    val body = bodyWriter.source.lineSequence().joinToString("\n") { line -> "  $line" }
    val objectBody = "{ const value = Object.create($prototype); " +
      "value[\"$slot\"] = ($parameters) => {\n$body}; return value; }"
    if (expression.boundValues.isEmpty()) return "(() => $objectBody)()"
    val boundParameters = expression.boundValues.joinToString(", ") { bound ->
      JsNameMangler.local(bound.local)
    }
    val boundArguments = expression.boundValues.joinToString(", ") { bound ->
      val rendered = renderExpression(bound.expression)
      if (bound.requireNonNull) "\$__j_non_null($rendered)" else rendered
    }
    return "(($boundParameters) => $objectBody)($boundArguments)"
  }

  /**
   * builtin 虚方法根优先调用用户 prototype 上的槽位；没有 override 时才进入确定的运行时默认实现。
   * receiver 和全部参数先作为 IIFE 实参求值，保持 Java 的 NPE 与参数副作用顺序。
   */
  private fun renderVirtualSlotInvocation(expression: JavaIrExpression.InvokeVirtualSlot): String {
    val operation = expression.operation
    val receiver = renderExpression(expression.receiver)
    val arguments = expression.arguments.joinToString(", ") { renderExpression(it) }
    val valuesArgument = if (arguments.isEmpty()) "[]" else "[$arguments]"
    val fallback = when (operation) {
      JavaBuiltinOperation.OBJECT_EQUALS -> "\$__j_object_equals(receiver, ...values)"
      JavaBuiltinOperation.OBJECT_HASH_CODE -> "\$__j_object_hash_code(receiver)"
      JavaBuiltinOperation.OBJECT_TO_STRING -> "\$__j_object_to_string(receiver)"
      JavaBuiltinOperation.THROWABLE_GET_MESSAGE -> "\$__j_exception_get_message(receiver)"
      JavaBuiltinOperation.THROWABLE_GET_CAUSE -> "\$__j_exception_get_cause(receiver)"
      JavaBuiltinOperation.THROWABLE_TO_STRING -> "\$__j_exception_to_string(receiver)"
      JavaBuiltinOperation.AUTO_CLOSEABLE_CLOSE ->
        "(() => { throw new Error(\"java.lang.AbstractMethodError: close\"); })()"
      JavaBuiltinOperation.RUNNABLE_RUN -> abstractMethodFallback("run")
      JavaBuiltinOperation.CONSUMER_ACCEPT -> abstractMethodFallback("accept")
      JavaBuiltinOperation.FUNCTION_APPLY -> abstractMethodFallback("apply")
      JavaBuiltinOperation.SUPPLIER_GET -> abstractMethodFallback("get")
      JavaBuiltinOperation.PREDICATE_TEST -> abstractMethodFallback("test")
      else -> error("Validated builtin virtual slot has an unsupported operation.")
    }
    val slot = JsNameMangler.virtualSlot(expression.virtualSlot)
    return "((receiver, values) => { receiver = \$__j_non_null(receiver); " +
      "const method = receiver[\"$slot\"]; " +
      "return typeof method === \"function\" ? method.call(receiver, ...values) : $fallback; " +
      "})($receiver, $valuesArgument)"
  }

  /** builtin 函数式接口没有默认实现，缺失 lambda/用户实现槽时抛出稳定 AbstractMethodError。 */
  private fun abstractMethodFallback(name: String): String =
    "(() => { throw new Error(\"java.lang.AbstractMethodError: $name\"); })()"

  /** operation 与 helper 一一对应；JS 参数求值先于 helper 函数体中的 receiver 空检查。 */
  private fun renderBuiltinInvocation(expression: JavaIrExpression.InvokeBuiltin): String {
    val receiver = expression.receiver?.let(::renderExpression)
    val arguments = expression.arguments.map(::renderExpression)
    fun instance(helper: String): String =
      "$helper(${listOfNotNull(receiver).plus(arguments).joinToString(", ")})"
    fun static(helper: String): String = "$helper(${arguments.joinToString(", ")})"
    return when (expression.operation) {
      JavaBuiltinOperation.SYSTEM_OUT,
      JavaBuiltinOperation.SYSTEM_ERR,
      JavaBuiltinOperation.SYSTEM_IN -> error("Validated callable builtin cannot be a stream value operation.")
      JavaBuiltinOperation.OBJECT_EQUALS -> instance("\$__j_object_equals")
      JavaBuiltinOperation.OBJECT_HASH_CODE -> instance("\$__j_object_hash_code")
      JavaBuiltinOperation.OBJECT_TO_STRING -> instance("\$__j_object_to_string")
      JavaBuiltinOperation.THROWABLE_GET_MESSAGE -> instance("\$__j_exception_get_message")
      JavaBuiltinOperation.THROWABLE_GET_CAUSE -> instance("\$__j_exception_get_cause")
      JavaBuiltinOperation.THROWABLE_TO_STRING -> instance("\$__j_exception_to_string")
      JavaBuiltinOperation.AUTO_CLOSEABLE_CLOSE ->
        error("AutoCloseable.close must use its virtual slot.")
      JavaBuiltinOperation.RUNNABLE_RUN,
      JavaBuiltinOperation.CONSUMER_ACCEPT,
      JavaBuiltinOperation.FUNCTION_APPLY,
      JavaBuiltinOperation.SUPPLIER_GET,
      JavaBuiltinOperation.PREDICATE_TEST,
      -> error("Functional interface calls must use their virtual slots.")
      JavaBuiltinOperation.PRINTSTREAM_PRINT_BOOLEAN -> instance("\$__j_print_boolean")
      JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR -> instance("\$__j_print_char")
      JavaBuiltinOperation.PRINTSTREAM_PRINT_CHAR_ARRAY -> instance("\$__j_print_char_array")
      JavaBuiltinOperation.PRINTSTREAM_PRINT_INT -> instance("\$__j_print_int")
      JavaBuiltinOperation.PRINTSTREAM_PRINT_STRING -> instance("\$__j_print_string")
      JavaBuiltinOperation.PRINTSTREAM_PRINT_OBJECT -> instance("\$__j_print_object")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN -> instance("\$__j_println")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN_BOOLEAN -> instance("\$__j_println_boolean")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR -> instance("\$__j_println_char")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN_CHAR_ARRAY -> instance("\$__j_println_char_array")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN_INT -> instance("\$__j_println_int")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN_STRING -> instance("\$__j_println_string")
      JavaBuiltinOperation.PRINTSTREAM_PRINTLN_OBJECT -> instance("\$__j_println_object")
      JavaBuiltinOperation.STRING_LENGTH -> instance("\$__j_string_length")
      JavaBuiltinOperation.STRING_IS_EMPTY -> instance("\$__j_string_is_empty")
      JavaBuiltinOperation.STRING_CHAR_AT -> instance("\$__j_string_char_at")
      JavaBuiltinOperation.STRING_EQUALS -> instance("\$__j_string_equals")
      JavaBuiltinOperation.STRING_SUBSTRING_FROM -> instance("\$__j_string_substring_from")
      JavaBuiltinOperation.STRING_SUBSTRING_RANGE -> instance("\$__j_string_substring_range")
      JavaBuiltinOperation.STRING_INDEX_OF_CHAR -> instance("\$__j_string_index_of_code_point")
      JavaBuiltinOperation.STRING_INDEX_OF_STRING -> instance("\$__j_string_index_of_string")
      JavaBuiltinOperation.STRING_CONTAINS -> instance("\$__j_string_contains")
      JavaBuiltinOperation.STRING_STARTS_WITH -> instance("\$__j_string_starts_with")
      JavaBuiltinOperation.STRING_ENDS_WITH -> instance("\$__j_string_ends_with")
      JavaBuiltinOperation.MATH_ABS_INT -> static("\$__j_math_abs_int")
      JavaBuiltinOperation.MATH_MIN_INT -> static("\$__j_math_min_int")
      JavaBuiltinOperation.MATH_MAX_INT -> static("\$__j_math_max_int")
      JavaBuiltinOperation.BOOLEAN_VALUE_OF -> "\$__j_box(\"BOOLEAN\", ${arguments.single()})"
      JavaBuiltinOperation.BYTE_VALUE_OF -> "\$__j_box(\"BYTE\", ${arguments.single()})"
      JavaBuiltinOperation.SHORT_VALUE_OF -> "\$__j_box(\"SHORT\", ${arguments.single()})"
      JavaBuiltinOperation.CHARACTER_VALUE_OF -> "\$__j_box(\"CHAR\", ${arguments.single()})"
      JavaBuiltinOperation.INTEGER_VALUE_OF -> "\$__j_box(\"INT\", ${arguments.single()})"
      JavaBuiltinOperation.BOOLEAN_BOOLEAN_VALUE -> "\$__j_unbox($receiver, \"BOOLEAN\")"
      JavaBuiltinOperation.BYTE_BYTE_VALUE -> "\$__j_unbox($receiver, \"BYTE\")"
      JavaBuiltinOperation.SHORT_SHORT_VALUE -> "\$__j_unbox($receiver, \"SHORT\")"
      JavaBuiltinOperation.CHARACTER_CHAR_VALUE -> "\$__j_unbox($receiver, \"CHAR\")"
      JavaBuiltinOperation.INTEGER_INT_VALUE -> "\$__j_unbox($receiver, \"INT\")"
      JavaBuiltinOperation.NUMBER_INT_VALUE -> instance("\$__j_number_int_value")
      JavaBuiltinOperation.BOOLEAN_EQUALS,
      JavaBuiltinOperation.BYTE_EQUALS,
      JavaBuiltinOperation.SHORT_EQUALS,
      JavaBuiltinOperation.CHARACTER_EQUALS,
      JavaBuiltinOperation.INTEGER_EQUALS -> instance("\$__j_box_equals")
      JavaBuiltinOperation.BOOLEAN_HASH_CODE,
      JavaBuiltinOperation.BYTE_HASH_CODE,
      JavaBuiltinOperation.SHORT_HASH_CODE,
      JavaBuiltinOperation.CHARACTER_HASH_CODE,
      JavaBuiltinOperation.INTEGER_HASH_CODE -> instance("\$__j_box_hash")
      JavaBuiltinOperation.BOOLEAN_TO_STRING,
      JavaBuiltinOperation.BYTE_TO_STRING,
      JavaBuiltinOperation.SHORT_TO_STRING,
      JavaBuiltinOperation.CHARACTER_TO_STRING,
      JavaBuiltinOperation.INTEGER_TO_STRING -> instance("\$__j_box_to_string")
      JavaBuiltinOperation.STRING_BUILDER_APPEND_BOOLEAN -> instance("\$__j_sb_append_boolean")
      JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR -> instance("\$__j_sb_append_char")
      JavaBuiltinOperation.STRING_BUILDER_APPEND_CHAR_ARRAY -> instance("\$__j_sb_append_char_array")
      JavaBuiltinOperation.STRING_BUILDER_APPEND_INT -> instance("\$__j_sb_append_int")
      JavaBuiltinOperation.STRING_BUILDER_APPEND_STRING -> instance("\$__j_sb_append_string")
      JavaBuiltinOperation.STRING_BUILDER_APPEND_OBJECT -> instance("\$__j_sb_append_object")
      JavaBuiltinOperation.STRING_BUILDER_LENGTH -> instance("\$__j_sb_length")
      JavaBuiltinOperation.STRING_BUILDER_CHAR_AT -> instance("\$__j_sb_char_at")
      JavaBuiltinOperation.STRING_BUILDER_SET_CHAR_AT -> instance("\$__j_sb_set_char_at")
      JavaBuiltinOperation.STRING_BUILDER_REVERSE -> instance("\$__j_sb_reverse")
      JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_FROM -> instance("\$__j_sb_substring_from")
      JavaBuiltinOperation.STRING_BUILDER_SUBSTRING_RANGE -> instance("\$__j_sb_substring_range")
      JavaBuiltinOperation.STRING_BUILDER_TO_STRING -> instance("\$__j_sb_to_string")
      JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_EMPTY,
      JavaBuiltinOperation.STRING_BUILDER_CONSTRUCT_STRING,
      JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT,
      JavaBuiltinOperation.HASH_SET_CONSTRUCT,
      JavaBuiltinOperation.HASH_MAP_CONSTRUCT,
      JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM,
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY,
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING,
      JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE ->
        error("Validated callable builtin cannot be a construction operation.")
      JavaBuiltinOperation.LIST_SIZE -> instance("\$__j_list_size")
      JavaBuiltinOperation.LIST_IS_EMPTY -> instance("\$__j_list_is_empty")
      JavaBuiltinOperation.LIST_ADD -> instance("\$__j_list_add")
      JavaBuiltinOperation.LIST_GET -> instance("\$__j_list_get")
      JavaBuiltinOperation.LIST_SET -> instance("\$__j_list_set")
      JavaBuiltinOperation.LIST_REMOVE_INDEX -> instance("\$__j_list_remove_index")
      JavaBuiltinOperation.LIST_REMOVE_OBJECT -> instance("\$__j_list_remove_object")
      JavaBuiltinOperation.LIST_CONTAINS -> instance("\$__j_list_contains")
      JavaBuiltinOperation.LIST_INDEX_OF -> instance("\$__j_list_index_of")
      JavaBuiltinOperation.LIST_CLEAR -> instance("\$__j_list_clear")
      JavaBuiltinOperation.LIST_ITERATOR -> instance("\$__j_list_iterator")
      JavaBuiltinOperation.ITERATOR_HAS_NEXT -> instance("\$__j_iterator_has_next")
      JavaBuiltinOperation.ITERATOR_NEXT -> instance("\$__j_iterator_next")
      JavaBuiltinOperation.SET_ADD -> instance("\$__j_set_add")
      JavaBuiltinOperation.SET_CONTAINS -> instance("\$__j_set_contains")
      JavaBuiltinOperation.SET_REMOVE -> instance("\$__j_set_remove")
      JavaBuiltinOperation.SET_SIZE -> instance("\$__j_set_size")
      JavaBuiltinOperation.SET_IS_EMPTY -> instance("\$__j_set_is_empty")
      JavaBuiltinOperation.SET_CLEAR -> instance("\$__j_set_clear")
      JavaBuiltinOperation.SET_ITERATOR -> instance("\$__j_set_iterator")
      JavaBuiltinOperation.MAP_PUT -> instance("\$__j_map_put")
      JavaBuiltinOperation.MAP_GET -> instance("\$__j_map_get")
      JavaBuiltinOperation.MAP_GET_OR_DEFAULT -> instance("\$__j_map_get_or_default")
      JavaBuiltinOperation.MAP_CONTAINS_KEY -> instance("\$__j_map_contains_key")
      JavaBuiltinOperation.MAP_REMOVE -> instance("\$__j_map_remove")
      JavaBuiltinOperation.MAP_SIZE -> instance("\$__j_map_size")
      JavaBuiltinOperation.MAP_IS_EMPTY -> instance("\$__j_map_is_empty")
      JavaBuiltinOperation.MAP_CLEAR -> instance("\$__j_map_clear")
      JavaBuiltinOperation.MAP_KEY_SET -> instance("\$__j_map_key_set")
      JavaBuiltinOperation.SCANNER_HAS_NEXT -> instance("\$__j_scanner_has_next")
      JavaBuiltinOperation.SCANNER_NEXT -> instance("\$__j_scanner_next")
      JavaBuiltinOperation.SCANNER_HAS_NEXT_INT -> instance("\$__j_scanner_has_next_int")
      JavaBuiltinOperation.SCANNER_NEXT_INT -> instance("\$__j_scanner_next_int")
      JavaBuiltinOperation.SCANNER_HAS_NEXT_LINE -> instance("\$__j_scanner_has_next_line")
      JavaBuiltinOperation.SCANNER_NEXT_LINE -> instance("\$__j_scanner_next_line")
      JavaBuiltinOperation.SCANNER_CLOSE -> instance("\$__j_scanner_close")
    }
  }

  /** 根据表达式 result type 选择 Java int helper 或 JavaScript 布尔/引用运算。 */
  private fun renderBinary(expression: JavaIrExpression.Binary): String {
    val left = renderExpression(expression.left)
    val right = renderExpression(expression.right)
    return when (expression.operator) {
      JavaIrBinaryOperator.MULTIPLY -> if (expression.type.isStage0Integral()) {
        "Math.imul($left, $right)"
      } else {
        "($left * $right)"
      }
      JavaIrBinaryOperator.DIVIDE -> if (expression.type.isStage0Integral()) {
        "\$__j_int_div($left, $right)"
      } else {
        "($left / $right)"
      }
      JavaIrBinaryOperator.REMAINDER -> if (expression.type.isStage0Integral()) {
        "\$__j_int_rem($left, $right)"
      } else {
        "($left % $right)"
      }
      JavaIrBinaryOperator.ADD -> if (expression.type.isStage0Integral()) {
        "(($left + $right) | 0)"
      } else {
        "($left + $right)"
      }
      JavaIrBinaryOperator.SUBTRACT -> "(($left - $right) | 0)"
      JavaIrBinaryOperator.SHIFT_LEFT -> "(($left << $right) | 0)"
      JavaIrBinaryOperator.SHIFT_RIGHT -> "(($left >> $right) | 0)"
      JavaIrBinaryOperator.UNSIGNED_SHIFT_RIGHT -> "(($left >>> $right) | 0)"
      JavaIrBinaryOperator.LESS_THAN -> "($left < $right)"
      JavaIrBinaryOperator.LESS_THAN_OR_EQUAL -> "($left <= $right)"
      JavaIrBinaryOperator.GREATER_THAN -> "($left > $right)"
      JavaIrBinaryOperator.GREATER_THAN_OR_EQUAL -> "($left >= $right)"
      JavaIrBinaryOperator.EQUAL -> "($left === $right)"
      JavaIrBinaryOperator.NOT_EQUAL -> "($left !== $right)"
      JavaIrBinaryOperator.BITWISE_AND -> "(($left & $right) | 0)"
      JavaIrBinaryOperator.BITWISE_XOR -> "(($left ^ $right) | 0)"
      JavaIrBinaryOperator.BITWISE_OR -> "(($left | $right) | 0)"
      JavaIrBinaryOperator.LOGICAL_AND -> "($left && $right)"
      JavaIrBinaryOperator.LOGICAL_OR -> "($left || $right)"
    }
  }

  /** 生成已消除前后缀副作用的一元表达式。 */
  private fun renderUnary(expression: JavaIrExpression.Unary): String {
    val operand = renderExpression(expression.operand)
    return when (expression.operator) {
      JavaIrUnaryOperator.NEGATE -> "(-$operand | 0)"
      JavaIrUnaryOperator.LOGICAL_NOT -> "(!$operand)"
      JavaIrUnaryOperator.BITWISE_NOT -> "(~$operand | 0)"
    }
  }

  /** 将 lowering 明确给出的转换渲染为 JavaScript 中对应的安全表示。 */
  private fun renderConversion(expression: JavaIrExpression.Convert): String {
    val code = renderExpression(expression.expression)
    return renderConversionCode(expression.conversion, code, expression.type)
  }

  /** 按顺序渲染增强 for 的元素赋值转换。 */
  private fun renderConversions(
    conversions: List<JavaIrConversion>,
    source: String,
    sourceType: JavaIrType,
  ): String {
    var code = source
    var type = sourceType
    conversions.forEach { conversion ->
      type = conversion.outputType(type)
      code = renderConversionCode(conversion, code, type)
    }
    return code
  }

  /** 转换渲染只依赖明确 operation 与结果类型，可复用于表达式和循环元素。 */
  private fun renderConversionCode(
    conversion: JavaIrConversion,
    code: String,
    resultType: JavaIrType,
  ): String = when (conversion) {
      JavaIrConversion.Identity,
      is JavaIrConversion.ReferenceWidening -> code
      is JavaIrConversion.PrimitiveWidening -> if (resultType.isStage0Integral()) {
        "($code | 0)"
      } else {
        code
      }
      is JavaIrConversion.PrimitiveNarrowing -> when (conversion.to.name) {
        "BYTE" -> "(($code << 24) >> 24)"
        "SHORT" -> "(($code << 16) >> 16)"
        "CHAR" -> "($code & 65535)"
        "INT" -> "($code | 0)"
        else -> error("Validated primitive narrowing has an unsupported target.")
      }
      is JavaIrConversion.Boxing ->
        "\$__j_box(\"${conversion.primitive.boxTag()}\", $code)"
      is JavaIrConversion.Unboxing ->
        "\$__j_unbox($code, \"${conversion.primitive.boxTag()}\")"
    }

  /** emitter 仅根据 conversion 明示端点推进类型。 */
  private fun JavaIrConversion.outputType(source: JavaIrType): JavaIrType = when (this) {
    JavaIrConversion.Identity -> source
    is JavaIrConversion.ReferenceWidening -> to
    is JavaIrConversion.PrimitiveWidening -> JavaIrType.Primitive(to)
    is JavaIrConversion.PrimitiveNarrowing -> JavaIrType.Primitive(to)
    is JavaIrConversion.Boxing -> JavaIrType.Reference(boxedClass)
    is JavaIrConversion.Unboxing -> JavaIrType.Primitive(primitive)
  }

  /** emitter 通过 typed IR 的 builtin role 判断 String，不依赖类名。 */
  private fun JavaIrType.hasBuiltinRole(role: JavaBuiltinTypeRole): Boolean {
    val classId = (this as? JavaIrType.Reference)?.classId ?: return false
    return index.program.builtinTypeRoles[classId] == role
  }

  /** 首批 primitive 与 tagged wrapper 的稳定运行时标签。 */
  private fun JavaAstPrimitiveType.boxTag(): String = when (this) {
    JavaAstPrimitiveType.BOOLEAN -> "BOOLEAN"
    JavaAstPrimitiveType.BYTE -> "BYTE"
    JavaAstPrimitiveType.SHORT -> "SHORT"
    JavaAstPrimitiveType.CHAR -> "CHAR"
    JavaAstPrimitiveType.INT -> "INT"
    else -> error("Validated boxing conversion contains an unsupported primitive.")
  }

  /** 常量使用 JSON 兼容转义，整数文本保持不经过 JavaScript Number 重新格式化。 */
  private fun renderConstant(constant: JavaIrConstant): String = when (constant) {
    is JavaIrConstant.BooleanValue -> constant.value.toString()
    is JavaIrConstant.IntValue -> constant.value.toString()
    is JavaIrConstant.StringValue -> writer.stringLiteral(constant.value)
    JavaIrConstant.NullValue -> "null"
    is JavaIrConstant.LongValue -> error("Validated Java IR cannot contain long constants.")
  }

  /** 对 int-like 本地变量或参数写入统一加上 Java 32 位截断。 */
  private fun coerceToType(code: String, type: JavaIrType): String {
    return if (type.isStage0Integral()) "($code | 0)" else code
  }

  /** Java 默认字段值必须独立于 JavaScript 的 undefined。 */
  private fun defaultValue(type: JavaIrType): String = when (type) {
    is JavaIrType.Primitive -> when (type.kind.name) {
      "BOOLEAN" -> "false"
      else -> "0"
    }
    is JavaIrType.Reference, is JavaIrType.Array, JavaIrType.Null -> "null"
    JavaIrType.Void -> error("Void fields are invalid Java IR.")
  }

  /**
   * 数组 component token 同时承载 primitive 窄化和引用存储检查。
   *
   * Object/primitive 是无条件写入，String 只接受 JS string，用户类使用 prototype 链；
   * 因此不会把任意 JS string 错误地放入某个用户类数组。
   */
  private fun renderArrayComponent(
    type: JavaIrType,
    referenceKind: JavaIrArrayReferenceComponentKind?,
  ): String = when (type) {
    is JavaIrType.Primitive -> writer.stringLiteral("primitive:${type.kind.name}")
    is JavaIrType.Reference -> when (referenceKind) {
      JavaIrArrayReferenceComponentKind.OBJECT -> writer.stringLiteral("object")
      JavaIrArrayReferenceComponentKind.STRING -> writer.stringLiteral("string")
      JavaIrArrayReferenceComponentKind.USER_CLASS ->
        index.classes[type.classId]?.let { JsNameMangler.prototype(it.id) } ?: "null"
      null -> index.classes[type.classId]?.let { JsNameMangler.prototype(it.id) } ?: "null"
    }
    else -> "null"
  }

  /** 入口绑定已由 validator 验证唯一性，这里只执行确定性索引读取。 */
  private fun resolveEntryMethod(): JavaIrMethod {
    val owner = index.program.classes.single { clazz ->
      clazz.qualifiedName == entryPoint.qualifiedClassName
    }
    return owner.methods.single { method ->
      method.name == entryPoint.methodName &&
        method.descriptor == entryPoint.descriptor &&
        method.dispatch == JavaIrDispatchKind.STATIC
    }
  }
}

/** 阶段 0 使用 JavaScript 的 signed int32 承载 byte、short、char 和 int 的运行时值。 */
private fun JavaIrType.isStage0Integral(): Boolean {
  return this is JavaIrType.Primitive && kind.name.isStage0IntegralPrimitiveName()
}

/** long、float、double 需要不同 runtime representation，不得静默按 Number 降级。 */
private fun String.isStage0IntegralPrimitiveName(): Boolean {
  return this == "BYTE" || this == "SHORT" || this == "CHAR" || this == "INT"
}

/** 集合 operation 的集中判定同时服务 helper 注入，不依赖 Java 类型名。 */
private fun JavaBuiltinOperation.isCollectionOperation(): Boolean = when (this) {
  JavaBuiltinOperation.ARRAY_LIST_CONSTRUCT,
  JavaBuiltinOperation.HASH_SET_CONSTRUCT,
  JavaBuiltinOperation.HASH_MAP_CONSTRUCT,
  JavaBuiltinOperation.LIST_SIZE,
  JavaBuiltinOperation.LIST_IS_EMPTY,
  JavaBuiltinOperation.LIST_ADD,
  JavaBuiltinOperation.LIST_GET,
  JavaBuiltinOperation.LIST_SET,
  JavaBuiltinOperation.LIST_REMOVE_INDEX,
  JavaBuiltinOperation.LIST_REMOVE_OBJECT,
  JavaBuiltinOperation.LIST_CONTAINS,
  JavaBuiltinOperation.LIST_INDEX_OF,
  JavaBuiltinOperation.LIST_CLEAR,
  JavaBuiltinOperation.LIST_ITERATOR,
  JavaBuiltinOperation.ITERATOR_HAS_NEXT,
  JavaBuiltinOperation.ITERATOR_NEXT,
  JavaBuiltinOperation.SET_ADD,
  JavaBuiltinOperation.SET_CONTAINS,
  JavaBuiltinOperation.SET_REMOVE,
  JavaBuiltinOperation.SET_SIZE,
  JavaBuiltinOperation.SET_IS_EMPTY,
  JavaBuiltinOperation.SET_CLEAR,
  JavaBuiltinOperation.SET_ITERATOR,
  JavaBuiltinOperation.MAP_PUT,
  JavaBuiltinOperation.MAP_GET,
  JavaBuiltinOperation.MAP_GET_OR_DEFAULT,
  JavaBuiltinOperation.MAP_CONTAINS_KEY,
  JavaBuiltinOperation.MAP_REMOVE,
  JavaBuiltinOperation.MAP_SIZE,
  JavaBuiltinOperation.MAP_IS_EMPTY,
  JavaBuiltinOperation.MAP_CLEAR,
  JavaBuiltinOperation.MAP_KEY_SET -> true
  else -> false
}

/** Scanner 构造与调用的集中判定，保证 runtime 注入不依赖类型名。 */
private fun JavaBuiltinOperation.isScannerOperation(): Boolean = when (this) {
  JavaBuiltinOperation.SCANNER_CONSTRUCT_INPUT_STREAM,
  JavaBuiltinOperation.SCANNER_HAS_NEXT,
  JavaBuiltinOperation.SCANNER_NEXT,
  JavaBuiltinOperation.SCANNER_HAS_NEXT_INT,
  JavaBuiltinOperation.SCANNER_NEXT_INT,
  JavaBuiltinOperation.SCANNER_HAS_NEXT_LINE,
  JavaBuiltinOperation.SCANNER_NEXT_LINE,
  JavaBuiltinOperation.SCANNER_CLOSE -> true
  else -> false
}

/** 两个异常构造 operation 共享实现，由 ConstructBuiltin 的精确结果 role 决定具体类型。 */
private fun JavaBuiltinOperation.isExceptionConstruction(): Boolean =
  this == JavaBuiltinOperation.EXCEPTION_CONSTRUCT_EMPTY ||
    this == JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING ||
    this == JavaBuiltinOperation.EXCEPTION_CONSTRUCT_STRING_CAUSE

/** 按 catalog 的唯一继承表判断 builtin role 子类型关系。 */
private fun JavaBuiltinTypeRole.isSubtypeOf(target: JavaBuiltinTypeRole): Boolean {
  var current: JavaBuiltinTypeRole? = this
  val visited = mutableSetOf<JavaBuiltinTypeRole>()
  while (current != null && visited.add(current)) {
    if (current == target) return true
    current = JavaBuiltinLibrary.directSuperRoles[current]
  }
  return false
}

private fun JavaBuiltinTypeRole.isThrowableRole(): Boolean =
  isSubtypeOf(JavaBuiltinTypeRole.THROWABLE)

/** exception runtime 使用稳定 Java 限定名，不读取源码名称或 JS constructor.name。 */
private fun JavaBuiltinTypeRole.exceptionQualifiedName(): String? =
  JavaBuiltinLibrary.types.firstOrNull { it.role == this }
    ?.qualifiedName
    ?.takeIf { isThrowableRole() }

/** Object 三个 builtin operation 是用户 override 的虚方法根。 */
private fun JavaBuiltinOperation.isBuiltinVirtualOperation(): Boolean = when (this) {
  JavaBuiltinOperation.OBJECT_EQUALS,
  JavaBuiltinOperation.OBJECT_HASH_CODE,
  JavaBuiltinOperation.OBJECT_TO_STRING,
  JavaBuiltinOperation.THROWABLE_GET_MESSAGE,
  JavaBuiltinOperation.THROWABLE_GET_CAUSE,
  JavaBuiltinOperation.THROWABLE_TO_STRING,
  JavaBuiltinOperation.AUTO_CLOSEABLE_CLOSE -> true
  JavaBuiltinOperation.RUNNABLE_RUN,
  JavaBuiltinOperation.CONSUMER_ACCEPT,
  JavaBuiltinOperation.FUNCTION_APPLY,
  JavaBuiltinOperation.SUPPLIER_GET,
  JavaBuiltinOperation.PREDICATE_TEST -> true
  else -> false
}

/** catalog 明确允许用户源码或 lambda 实现的 builtin interface facade。 */
private fun isUserImplementableBuiltinInterface(role: JavaBuiltinTypeRole): Boolean =
  JavaBuiltinLibrary.types.any { descriptor ->
    descriptor.role == role && descriptor.isInterfaceFacade && descriptor.allowsUserImplementation
  }
