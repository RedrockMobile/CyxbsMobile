package com.cyxbs.functions.code.language.java.compiler.backend.js

import com.cyxbs.functions.code.language.java.compiler.JavaCompilerEntryPoint
import com.cyxbs.functions.code.language.java.compiler.JavaScriptBackend
import com.cyxbs.functions.code.language.java.compiler.JavaScriptModuleArtifact
import com.cyxbs.functions.code.language.java.compiler.JavaScriptProgramArtifact
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerDiagnostic
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaDiagnosticSeverity
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClass
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrClassId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstant
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConversion
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrDispatchKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrExpression
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrField
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrFieldId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocal
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrLocalId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethod
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodId
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrMethodKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrProgram
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrStatement
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrConstructorInvocationKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrType
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrBinaryOperator
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrArrayReferenceComponentKind
import com.cyxbs.functions.code.language.java.compiler.ir.JavaIrUnaryOperator
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

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
    index.program.classes.sortedBy { it.id.value }.forEach(::validateClass)
    index.program.classes.forEach { clazz ->
      val seen = mutableSetOf<JavaIrClassId>()
      var current: JavaIrClass? = clazz
      while (current != null && seen.add(current.id)) current = current.superClass?.let(index.classes::get)
      if (current != null) invalid("Java IR inheritance graph contains a cycle.", clazz.span)
    }
    validateEntryPoint()
    return diagnostics.toList()
  }

  /** 校验阶段 1 类关系、字段归属和初始化块。接口默认方法仍未实现。 */
  private fun validateClass(clazz: JavaIrClass) {
    if (clazz.interfaces.isNotEmpty()) unsupported("Java interfaces are not available in the stage 1 JavaScript backend.", clazz.span)
    clazz.superClass?.let { parent -> if (index.classes[parent] == null) invalid("Java IR references an unknown superclass id ${parent.value}.", clazz.span) }
    clazz.fields.forEach { field ->
      if (field.owner != clazz.id) invalid("Java IR field owner does not match its class.", field.span)
      validateType(field.type, field.span)
      field.initializer?.let(::validateExpression)
    }
    clazz.staticInitializer?.let(::validateStatement)
    clazz.instanceInitializer?.let(::validateStatement)
    clazz.methods.forEach { method ->
      if (method.owner != clazz.id) invalid("Java IR method owner does not match its class.", method.span)
      validateMethod(method)
    }
    validateVirtualSlots(clazz)
  }

  /** 校验方法签名、构造器首句和局部变量引用。 */
  private fun validateMethod(method: JavaIrMethod) {
    if (method.body == null) {
      unsupported("Abstract or native Java methods cannot be emitted as JavaScript.", method.span)
    }
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
      validateStatement(body)
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
      if (method.dispatch != JavaIrDispatchKind.VIRTUAL || method.kind != JavaIrMethodKind.METHOD) {
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
        } else if (root.owner != method.owner) {
          invalid("Java IR virtual slot $slot is reused by unrelated override owners.", method.span)
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
  private fun validateStatement(statement: JavaIrStatement) {
    when (statement) {
      is JavaIrStatement.Block -> statement.statements.forEach(::validateStatement)
      is JavaIrStatement.DeclareLocal -> {
        requireLocal(statement.local, statement.span)
        statement.initializer?.let(::validateExpression)
      }
      is JavaIrStatement.Expression -> validateExpression(statement.expression)
      is JavaIrStatement.If -> {
        validateExpression(statement.condition)
        validateStatement(statement.thenBranch)
        statement.elseBranch?.let(::validateStatement)
      }
      is JavaIrStatement.While -> {
        validateExpression(statement.condition)
        validateStatement(statement.body)
      }
      is JavaIrStatement.ConstructorInvocation -> {
        val target = index.methods[statement.constructor]
        if (target == null || target.kind != JavaIrMethodKind.CONSTRUCTOR) {
          invalid("Java IR constructor invocation must target a constructor.", statement.span)
        }
        statement.arguments.forEach(::validateExpression)
      }
      is JavaIrStatement.Return -> statement.expression?.let(::validateExpression)
      is JavaIrStatement.Throw -> unsupported(
        "Java throw statements are not available in the current JavaScript backend.",
        statement.span,
      )
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
      is JavaIrExpression.SetStaticField -> { requireStaticField(expression.field, expression.span); validateExpression(expression.value) }
      is JavaIrExpression.Binary -> {
        validateExpression(expression.left)
        validateExpression(expression.right)
      }
      is JavaIrExpression.Unary -> validateExpression(expression.operand)
      is JavaIrExpression.Convert -> {
        validateExpression(expression.expression)
        validateConversion(expression.conversion, expression.span)
      }
      is JavaIrExpression.InvokeStatic -> {
        requireStaticMethod(expression.method, expression.span)
        expression.arguments.forEach(::validateExpression)
      }
      is JavaIrExpression.InvokeSpecial -> { requireInstanceMethod(expression.method, expression.span); validateExpression(expression.receiver); expression.arguments.forEach(::validateExpression) }
      is JavaIrExpression.InvokeVirtual -> {
        val target = index.methods[expression.method]
        if (target == null || target.dispatch != JavaIrDispatchKind.VIRTUAL || target.virtualSlot != expression.virtualSlot) invalid("Java IR virtual call slot does not match its selected method.", expression.span)
        validateExpression(expression.receiver); expression.arguments.forEach(::validateExpression)
      }
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

  /** 当前运行子集支持 identity、引用拓宽与不涉及 long/浮点的整数 widening。 */
  private fun validateConversion(conversion: JavaIrConversion, span: JavaSourceSpan) {
    when (conversion) {
      JavaIrConversion.Identity,
      is JavaIrConversion.ReferenceWidening -> Unit
      is JavaIrConversion.PrimitiveWidening -> {
        if (!conversion.from.name.isStage0IntegralPrimitiveName() ||
          !conversion.to.name.isStage0IntegralPrimitiveName()
        ) {
          unsupported("Only integral primitive widening is available in the current JavaScript backend.", span)
        }
      }
      is JavaIrConversion.Boxing,
      is JavaIrConversion.Unboxing -> unsupported(
        "Boxing and unboxing are not available in the current JavaScript backend.",
        span,
      )
    }
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
  private val writer = JsWriter()
  private var currentMethod: JavaIrMethod? = null
  private var currentThisName: String = "this"
  private val classesInParentFirstOrder: List<JavaIrClass> by lazy {
    val visited = mutableSetOf<JavaIrClassId>()
    buildList {
      fun visit(clazz: JavaIrClass) {
        if (!visited.add(clazz.id)) return
        clazz.superClass?.let(index.classes::get)?.let(::visit)
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
    writer.line()
    val methods = index.program.classes
      .flatMap(JavaIrClass::methods)
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
  private fun usesRuntimeHelpers(): Boolean = usesObjectRuntime() || usesArrayOrStringRuntime()

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
    is JavaIrStatement.ConstructorInvocation -> arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    is JavaIrStatement.Return -> expression?.requiresArrayOrStringRuntime() == true
    is JavaIrStatement.Throw -> expression.requiresArrayOrStringRuntime()
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
    is JavaIrExpression.NewObject -> arguments.any { argument -> argument.requiresArrayOrStringRuntime() }
    else -> false
  }

  /** 先声明所有 prototype 与 class metadata，允许跨文件父类和前向静态引用。 */
  private fun emitClassShells() {
    classesInParentFirstOrder.forEach { clazz ->
      val parent = clazz.superClass?.let(JsNameMangler::prototype) ?: "null"
      writer.line("const ${JsNameMangler.staticStorage(clazz.id)} = { state: 0, error: null, values: Object.create(null) };")
      // 类初始化尚未开始时，字段已按 Java 默认值可见；这也覆盖递归 clinit 的 in-progress 读取。
      clazz.fields.filter { it.isStatic }.forEach { field ->
        writer.line("${JsNameMangler.staticStorage(clazz.id)}.values[\"${JsNameMangler.field(field.id)}\"] = ${defaultValue(field.type)};")
      }
      writer.line("const ${JsNameMangler.prototype(clazz.id)} = Object.create($parent);")
    }
    index.program.classes.flatMap { it.methods }.filter { it.virtualSlot != null }.sortedBy { it.id.value }.forEach { method ->
      writer.line("${JsNameMangler.prototype(method.owner)}[\"${JsNameMangler.virtualSlot(checkNotNull(method.virtualSlot))}\"] = ${JsNameMangler.method(method.id)};")
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
      is JavaIrStatement.Throw -> error("Validated Java IR cannot contain throw statements.")
    }
  }

  /** 对非 block 分支补齐花括号，避免 if/while 生成时产生悬挂 else。 */
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
      is JavaIrExpression.SetStaticField -> {
        val owner = index.fields.getValue(expression.field).owner
        // Java putstatic 的右值先求值，随后才初始化字段所属类并执行写入。
        "((value) => (${JsNameMangler.classInitializer(owner)}(), ${JsNameMangler.staticStorage(owner)}.values[\"${JsNameMangler.field(expression.field)}\"] = value))(${renderExpression(expression.value)})"
      }
      is JavaIrExpression.InvokeSpecial -> {
        val args = expression.arguments.joinToString(", ") { renderExpression(it) }
        "((receiver${if (args.isEmpty()) "" else ", values"}) => ${JsNameMangler.method(expression.method)}.call(\$__j_non_null(receiver)${if (args.isEmpty()) "" else ", ...values"}))(${renderExpression(expression.receiver)}${if (args.isEmpty()) "" else ", [$args]"})"
      }
      is JavaIrExpression.InvokeVirtual -> {
        val receiver = renderExpression(expression.receiver)
        val args = expression.arguments.joinToString(", ") { renderExpression(it) }
        "((receiver${if (args.isEmpty()) "" else ", values"}) => \$__j_non_null(receiver)[\"${JsNameMangler.virtualSlot(expression.virtualSlot)}\"](${if (args.isEmpty()) "" else "...values"}))($receiver${if (args.isEmpty()) "" else ", [$args]"})"
      }
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
    return when (expression.conversion) {
      JavaIrConversion.Identity,
      is JavaIrConversion.ReferenceWidening -> code
      is JavaIrConversion.PrimitiveWidening -> if (expression.type.isStage0Integral()) {
        "($code | 0)"
      } else {
        code
      }
      is JavaIrConversion.Boxing,
      is JavaIrConversion.Unboxing -> error("Validated Java IR cannot contain boxing conversions.")
    }
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
