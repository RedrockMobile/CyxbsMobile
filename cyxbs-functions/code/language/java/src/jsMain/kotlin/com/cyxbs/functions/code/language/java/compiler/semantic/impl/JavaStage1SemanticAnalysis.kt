package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.diagnostic.*
import com.cyxbs.functions.code.language.java.compiler.semantic.*
import com.cyxbs.functions.code.language.java.compiler.source.JavaNodeId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceSpan

/**
 * Stage1 Java 语义分析实现。
 *
 * 分析按“类型骨架、继承与泛型、成员签名、override、方法体”执行。每一遍只消费前一遍已经
 * 冻结的事实，因此源码文件顺序不会影响名称绑定、重载或虚槽编号。
 */
internal class JavaStage1SemanticAnalysis(private val ast: JavaAstWorkspace) {
  private val diagnostics = mutableListOf<JavaCompilerDiagnostic>()
  private var nextSymbol = 0
  private var nextVirtualSlot = 0

  private val symbols = linkedMapOf<JavaSymbolId, JavaSemanticSymbol>()
  private val declarations = linkedMapOf<JavaNodeId, JavaSymbolId>()
  private val resolved = linkedMapOf<JavaNodeId, JavaSymbolId>()
  private val expressionTypes = linkedMapOf<JavaNodeId, JavaSemanticType>()
  private val conversions = linkedMapOf<JavaNodeId, JavaSemanticConversion>()
  private val calls = linkedMapOf<JavaNodeId, JavaCallableBinding>()
  private val constants = linkedMapOf<JavaNodeId, JavaConstantValue>()
  private val valueAccesses = linkedMapOf<JavaNodeId, JavaValueAccessBinding>()

  private val typeDeclarations = linkedMapOf<JavaSymbolId, JavaSemanticTypeDeclaration>()
  private val typeParameterDeclarations =
    linkedMapOf<JavaSymbolId, JavaSemanticTypeParameterDeclaration>()
  private val fieldDeclarations = linkedMapOf<JavaSymbolId, JavaSemanticFieldDeclaration>()
  private val callableDeclarations =
    linkedMapOf<JavaSymbolId, JavaSemanticCallableDeclaration>()
  private val virtualSlots = linkedMapOf<JavaSymbolId, JavaVirtualSlotId>()
  private val overriddenMethods = linkedMapOf<JavaSymbolId, List<JavaSymbolId>>()
  private val constructorDelegations =
    linkedMapOf<JavaSymbolId, JavaConstructorDelegation>()

  private val typesByQualifiedName = linkedMapOf<String, S1TypeInfo>()
  private val typesByNode = linkedMapOf<JavaNodeId, S1TypeInfo>()
  private val fieldsByOwnerAndName =
    linkedMapOf<Pair<JavaSymbolId, String>, S1FieldInfo>()
  private val methodsByOwnerAndName =
    linkedMapOf<Pair<JavaSymbolId, String>, MutableList<S1CallableInfo>>()
  private val constructorsByOwner =
    linkedMapOf<JavaSymbolId, MutableList<S1CallableInfo>>()
  private val callables = mutableListOf<S1CallableInfo>()
  private val fields = mutableListOf<S1FieldInfo>()

  private lateinit var hostNode: JavaAstCompilationUnit
  private lateinit var objectType: S1TypeInfo
  private lateinit var stringType: S1TypeInfo
  private var bodyContext: S1BodyContext? = null
  private var scope: S1Scope? = null
  private val definitelyAssigned = mutableSetOf<JavaSymbolId>()
  private val finalSymbols = mutableSetOf<JavaSymbolId>()
  private val assignedBlankFinalFields = mutableSetOf<JavaSymbolId>()
  private val unassignedBlankFinalFields = mutableSetOf<JavaSymbolId>()
  private var loopDepth = 0

  /** 执行全部语义遍次；错误结果不会泄露含恢复类型的半成品模型。 */
  fun analyze(): JavaCompilerPhaseResult<JavaSemanticModel> {
    val host = ast.units.firstOrNull()
    if (host == null) {
      error(null, "java.semantic.empty_workspace", "Java 工作区不包含可分析的编译单元。")
      return JavaCompilerPhaseResult.failure(diagnostics)
    }
    hostNode = host
    declareBuiltinTypes()
    declareSourceTypeSkeletons()
    declareTypeParameters()
    resolveHierarchy()
    validateHierarchy()
    declareMembers()
    validateOverridesAndAssignSlots()
    analyzeFieldInitializers()
    analyzeCallableBodies()
    validateConstructorCycles()
    return result()
  }

  /** 登记 Object 与 String；String 的直接父类固定为 Object。 */
  private fun declareBuiltinTypes() {
    val objectSymbol = symbol(
      JavaSymbolKind.TYPE,
      "Object",
      null,
      hostNode.nodeId,
      hostNode.span,
      null,
      registerDeclaration = false,
    )
    objectType = S1TypeInfo(
      unit = hostNode,
      declaration = null,
      qualifiedName = "java.lang.Object",
      symbol = objectSymbol,
      kind = JavaSemanticTypeDeclarationKind.BUILTIN,
      visibility = JavaVisibility.PUBLIC,
      isFinal = false,
    )
    typesByQualifiedName[objectType.qualifiedName] = objectType
    typeDeclarations[objectSymbol] = objectType.semanticDeclaration()

    val stringSymbol = symbol(
      JavaSymbolKind.TYPE,
      "String",
      null,
      hostNode.nodeId,
      hostNode.span,
      null,
      registerDeclaration = false,
    )
    stringType = S1TypeInfo(
      unit = hostNode,
      declaration = null,
      qualifiedName = "java.lang.String",
      symbol = stringSymbol,
      kind = JavaSemanticTypeDeclarationKind.BUILTIN,
      visibility = JavaVisibility.PUBLIC,
      isFinal = true,
      directSuperClass = JavaSemanticType.Declared(objectSymbol, emptyList()),
    )
    typesByQualifiedName[stringType.qualifiedName] = stringType
    typeDeclarations[stringSymbol] = stringType.semanticDeclaration()
  }

  /** 第一遍只登记 package-qualified 类型，防止继承与签名解析受源码顺序影响。 */
  private fun declareSourceTypeSkeletons() {
    ast.units.forEach { unit ->
      unit.imports.filter { it.isStatic }.forEach {
        error(it.span, "java.semantic.static_import_unsupported", "Stage1 暂不支持 static import。")
      }
      unit.types.forEach { declaration ->
        if (declaration.kind != JavaAstTypeDeclarationKind.CLASS) {
          error(
            declaration.span,
            "java.semantic.type_kind_unsupported",
            "当前 Stage1 稳定执行范围仅包含 class；interface 与 enum 暂不执行。",
          )
        }
        if (declaration.modifiers.any {
            it == JavaAstModifier.PRIVATE || it == JavaAstModifier.PROTECTED ||
              it == JavaAstModifier.STATIC
          }
        ) {
          error(
            declaration.span,
            "java.semantic.invalid_top_level_modifier",
            "顶层 class 不能声明为 private、protected 或 static。",
          )
        }
        if (JavaAstModifier.PUBLIC in declaration.modifiers &&
          javaStage1FileName(unit.sourceFile.path) != declaration.name
        ) {
          error(
            declaration.span,
            "java.semantic.public_type_file_mismatch",
            "public 顶层类型 ${declaration.name} 必须位于同名 Java 文件中。",
          )
        }
        val qualifiedName = qualifyStage1(unit.packageName, declaration.name)
        val previous = typesByQualifiedName[qualifiedName]
        if (previous != null) {
          error(
            declaration.span,
            "java.semantic.duplicate_type",
            "限定类型名 $qualifiedName 已在工作区声明。",
            listOf(JavaDiagnosticNote("首次声明位于此处。", previous.span)),
          )
          return@forEach
        }
        val id = symbol(
          JavaSymbolKind.TYPE,
          declaration.name,
          null,
          declaration.nodeId,
          declaration.span,
          null,
        )
        val info = S1TypeInfo(
          unit,
          declaration,
          qualifiedName,
          id,
          JavaSemanticTypeDeclarationKind.CLASS,
          visibility(declaration.modifiers),
          JavaAstModifier.FINAL in declaration.modifiers,
        )
        typesByQualifiedName[qualifiedName] = info
        typesByNode[declaration.nodeId] = info
        typeDeclarations[id] = info.semanticDeclaration()
      }
    }
    validateExplicitImports()
  }

  /** 显式 import 即使未被引用也必须指向可见类型。 */
  private fun validateExplicitImports() {
    ast.units.forEach { unit ->
      val imports = unit.imports.filter { !it.isStatic && !it.isWildcard }
      imports.groupBy { it.qualifiedName.substringAfterLast('.') }
        .filterValues { entries -> entries.map { it.qualifiedName }.distinct().size > 1 }
        .forEach { (name, entries) ->
          error(
            entries.last().span,
            "java.semantic.ambiguous_import",
            "类型名 $name 被多个显式 import 引入。",
            entries.dropLast(1).map { JavaDiagnosticNote("冲突的 import。", it.span) },
          )
        }
      imports.forEach { declaration ->
        val type = typesByQualifiedName[declaration.qualifiedName]
        if (type == null) {
          error(
            declaration.span,
            "java.semantic.unknown_import",
            "显式 import 的类型 ${declaration.qualifiedName} 不在当前编译工作区中。",
          )
        } else {
          ensureTypeAccessible(type, unit, declaration.span)
        }
      }
    }
  }

  /** 第二遍先登记所有 class 类型参数符号，再解析彼此引用的上界。 */
  private fun declareTypeParameters() {
    sourceTypes().forEach { type ->
      val declaration = checkNotNull(type.declaration)
      val names = linkedMapOf<String, JavaSymbolId>()
      declaration.typeParameters.forEach { parameter ->
        if (parameter.name in names) {
          error(parameter.span, "java.semantic.duplicate_type_parameter", "类型参数 ${parameter.name} 重复。")
        }
        val id = symbol(
          JavaSymbolKind.TYPE_PARAMETER,
          parameter.name,
          type.symbol,
          parameter.nodeId,
          parameter.span,
          null,
        )
        if (parameter.name !in names) names[parameter.name] = id
        type.typeParameters += id
      }
      type.typeParameterNames.putAll(names)
      typeDeclarations[type.symbol] = type.semanticDeclaration()
    }
    sourceTypes().forEach { type ->
      val declaration = checkNotNull(type.declaration)
      declaration.typeParameters.zip(type.typeParameters).forEach { (parameter, id) ->
        val bounds = if (parameter.upperBounds.isEmpty()) {
          listOf(objectSemanticType())
        } else {
          parameter.upperBounds.map {
            resolveType(it, type.unit, type.typeParameterNames, allowVoid = false)
          }
        }
        if (bounds.any { it is JavaSemanticType.Primitive || it == JavaSemanticType.Void }) {
          error(parameter.span, "java.semantic.invalid_type_parameter_bound", "泛型上界必须是引用类型。")
        }
        typeParameterDeclarations[id] =
          JavaSemanticTypeParameterDeclaration(id, type.symbol, bounds)
      }
    }
  }

  /** 解析直接父类；未显式声明的源码 class 默认继承 Object。 */
  private fun resolveHierarchy() {
    sourceTypes().forEach { type ->
      val declaration = checkNotNull(type.declaration)
      if (declaration.interfaces.isNotEmpty()) {
        error(
          declaration.interfaces.first().span,
          "java.semantic.interface_unsupported",
          "Stage1 当前稳定拒绝 implements/interface 语义。",
        )
      }
      val superType = declaration.superClass?.let {
        resolveType(it, type.unit, type.typeParameterNames, allowVoid = false)
      } ?: objectSemanticType()
      type.directSuperClass = superType as? JavaSemanticType.Declared
      if (type.directSuperClass == null && superType != JavaSemanticType.Error) {
        error(declaration.span, "java.semantic.invalid_superclass", "class 的直接父类必须是声明类型。")
      }
      typeDeclarations[type.symbol] = type.semanticDeclaration()
    }
  }

  /** DFS 校验继承环、final 父类与跨 package 可见性。 */
  private fun validateHierarchy() {
    val states = mutableMapOf<JavaSymbolId, Int>()
    fun visit(type: S1TypeInfo) {
      when (states[type.symbol]) {
        1 -> {
          error(type.span, "java.semantic.inheritance_cycle", "继承关系包含环：${type.qualifiedName}。")
          return
        }
        2 -> return
      }
      states[type.symbol] = 1
      val parent = type.directSuperClass?.symbol?.let(::typeInfo)
      if (parent != null) {
        if (parent.isFinal) {
          error(
            type.span,
            "java.semantic.extends_final_type",
            "类型 ${type.qualifiedName} 不能继承 final 类型 ${parent.qualifiedName}。",
          )
        }
        ensureTypeAccessible(parent, type.unit, type.span)
        visit(parent)
      }
      states[type.symbol] = 2
    }
    sourceTypes().forEach(::visit)
  }

  /** 第三遍登记字段、方法和构造器签名，并保持显式成员源码顺序。 */
  private fun declareMembers() {
    sourceTypes().forEach { owner ->
      val declaration = checkNotNull(owner.declaration)
      var fieldOrder = 0
      declaration.members.forEach { member ->
        when (member) {
          is JavaAstMemberDeclaration.Field -> {
            val fieldType = resolveType(
              member.type,
              owner.unit,
              owner.typeParameterNames,
              allowVoid = false,
            )
            member.declarators.forEach { declarator ->
              declareField(owner, member, declarator, fieldType, fieldOrder++)
            }
          }
          is JavaAstMemberDeclaration.Method -> declareCallable(owner, member)
          is JavaAstMemberDeclaration.Constructor -> declareCallable(owner, member)
        }
      }
      if (constructorsByOwner[owner.symbol].isNullOrEmpty()) synthesizeDefaultConstructor(owner)
      typeDeclarations[owner.symbol] = owner.semanticDeclaration()
    }
    synthesizeBuiltinConstructors()
  }

  /** 同一类型不能重复声明字段；字段 symbol 绑定到独立 declarator 节点。 */
  private fun declareField(
    owner: S1TypeInfo,
    declaration: JavaAstMemberDeclaration.Field,
    declarator: JavaAstVariableDeclarator,
    type: JavaSemanticType,
    order: Int,
  ) {
    val key = owner.symbol to declarator.name
    val previous = fieldsByOwnerAndName[key]
    if (previous != null) {
      error(
        declarator.span,
        "java.semantic.duplicate_field",
        "类型 ${owner.qualifiedName} 重复声明字段 ${declarator.name}。",
        listOf(JavaDiagnosticNote("首次声明位于此处。", previous.declarator.span)),
      )
    }
    val id = symbol(
      JavaSymbolKind.FIELD,
      declarator.name,
      owner.symbol,
      declarator.nodeId,
      declarator.span,
      type,
    )
    val field = S1FieldInfo(
      owner,
      declaration,
      declarator,
      id,
      type,
      visibility(declaration.modifiers),
      JavaAstModifier.STATIC in declaration.modifiers,
      JavaAstModifier.FINAL in declaration.modifiers,
      order,
    )
    fields += field
    if (key !in fieldsByOwnerAndName) fieldsByOwnerAndName[key] = field
    owner.membersInSourceOrder += id
    fieldDeclarations[id] = JavaSemanticFieldDeclaration(
      id,
      owner.symbol,
      type,
      field.visibility,
      field.isStatic,
      field.isFinal,
      order,
    )
    if (field.isFinal) finalSymbols += id
    if (field.isFinal && field.isStatic && field.declarator.initializer == null) {
      // 当前 AST 没有 static initializer block，声明初始化式是 static blank final 的唯一写入路径。
      error(
        field.declarator.span,
        "java.semantic.blank_final_field_not_initialized",
        "static final 字段 ${field.declarator.name} 必须提供声明初始化式。",
      )
    }
  }

  /** 登记普通方法或构造器；callable 类型参数先有 owner symbol，之后再解析签名。 */
  private fun declareCallable(
    owner: S1TypeInfo,
    declaration: JavaAstMemberDeclaration,
  ) {
    val methodDeclaration = declaration as? JavaAstMemberDeclaration.Method
    val constructorDeclaration = declaration as? JavaAstMemberDeclaration.Constructor
    if (constructorDeclaration != null && constructorDeclaration.name != owner.simpleName) {
      error(
        constructorDeclaration.span,
        "java.semantic.constructor_name_mismatch",
        "构造器名称必须与所属 class 一致。",
      )
    }
    val kind = if (methodDeclaration != null) {
      JavaSemanticCallableKind.METHOD
    } else {
      JavaSemanticCallableKind.CONSTRUCTOR
    }
    val name = methodDeclaration?.name ?: "<init>"
    val modifiers = methodDeclaration?.modifiers ?: checkNotNull(constructorDeclaration).modifiers
    val typeParameterNodes = methodDeclaration?.typeParameters ?: constructorDeclaration!!.typeParameters
    val parameterNodes = methodDeclaration?.parameters ?: constructorDeclaration!!.parameters
    val id = symbol(
      if (kind == JavaSemanticCallableKind.METHOD) JavaSymbolKind.METHOD else JavaSymbolKind.CONSTRUCTOR,
      name,
      owner.symbol,
      declaration.nodeId,
      declaration.span,
      null,
    )
    val typeParameterNames = linkedMapOf<String, JavaSymbolId>()
    val typeParameters = typeParameterNodes.map { parameter ->
      if (parameter.name in typeParameterNames || parameter.name in owner.typeParameterNames) {
        error(
          parameter.span,
          "java.semantic.duplicate_type_parameter",
          "callable 类型参数 ${parameter.name} 与同一泛型作用域中的名称重复。",
        )
      }
      symbol(
        JavaSymbolKind.TYPE_PARAMETER,
        parameter.name,
        id,
        parameter.nodeId,
        parameter.span,
        null,
      ).also { if (parameter.name !in typeParameterNames) typeParameterNames[parameter.name] = it }
    }
    val typeScope = LinkedHashMap(owner.typeParameterNames).apply { putAll(typeParameterNames) }
    typeParameterNodes.zip(typeParameters).forEach { (parameter, symbol) ->
      val bounds = if (parameter.upperBounds.isEmpty()) {
        listOf(objectSemanticType())
      } else {
        parameter.upperBounds.map {
          resolveType(it, owner.unit, typeScope, allowVoid = false)
        }
      }
      typeParameterDeclarations[symbol] =
        JavaSemanticTypeParameterDeclaration(symbol, id, bounds)
    }
    val returnType = if (methodDeclaration != null) {
      resolveType(methodDeclaration.returnType, owner.unit, typeScope, allowVoid = true)
    } else {
      owner.selfType()
    }
    symbols[id] = symbols.getValue(id).copy(type = returnType)
    val parameterTypes = parameterNodes.map { parameter ->
      if (parameter.isVararg) {
        error(parameter.span, "java.semantic.vararg_unsupported", "Stage1 暂不支持 vararg。")
      }
      resolveType(parameter.type, owner.unit, typeScope, allowVoid = false)
    }
    val parameterSymbols = parameterNodes.zip(parameterTypes).map { (parameter, type) ->
      symbol(
        JavaSymbolKind.PARAMETER,
        parameter.name,
        id,
        parameter.nodeId,
        parameter.span,
        type,
      )
    }
    if (parameterNodes.map { it.name }.distinct().size != parameterNodes.size) {
      error(declaration.span, "java.semantic.duplicate_parameter", "callable 参数名称不能重复。")
    }
    val erasedSignatureKey = erasedSignatureKey(name, parameterTypes)
    val erasedDescriptor = erasedMethodDescriptor(kind, parameterTypes, returnType)
    val info = S1CallableInfo(
      unit = owner.unit,
      owner = owner,
      declaration = declaration,
      symbol = id,
      kind = kind,
      name = name,
      typeParameters = typeParameters,
      typeParameterNames = typeParameterNames,
      parameterNodes = parameterNodes,
      parameterSymbols = parameterSymbols,
      parameterTypes = parameterTypes,
      returnType = returnType,
      visibility = visibility(modifiers),
      isStatic = JavaAstModifier.STATIC in modifiers,
      isFinal = JavaAstModifier.FINAL in modifiers,
      isAbstract = JavaAstModifier.ABSTRACT in modifiers,
      erasedSignatureKey = erasedSignatureKey,
      erasedDescriptor = erasedDescriptor,
    )
    validateCallableShape(info)
    val siblings = if (kind == JavaSemanticCallableKind.METHOD) {
      methodsByOwnerAndName.getOrPut(owner.symbol to name) { mutableListOf() }
    } else {
      constructorsByOwner.getOrPut(owner.symbol) { mutableListOf() }
    }
    val conflict = siblings.firstOrNull { it.erasedSignatureKey == erasedSignatureKey }
    if (conflict != null && parameterTypes.none { it == JavaSemanticType.Error }) {
      error(
        declaration.span,
        "java.semantic.duplicate_method_descriptor",
        "${if (kind == JavaSemanticCallableKind.METHOD) "方法 $name" else "构造器"} 的擦除 descriptor 重复。",
        listOf(JavaDiagnosticNote("冲突声明位于此处。", conflict.span)),
      )
    }
    siblings += info
    callables += info
    owner.membersInSourceOrder += id
    callableDeclarations[id] = info.semanticDeclaration()
  }

  /** 校验 modifier/body 组合，避免把抽象或静态构造器等非法形态写入成功模型。 */
  private fun validateCallableShape(info: S1CallableInfo) {
    if (info.kind == JavaSemanticCallableKind.CONSTRUCTOR) {
      val declaration = info.declaration as JavaAstMemberDeclaration.Constructor
      if (JavaAstModifier.STATIC in declaration.modifiers ||
        JavaAstModifier.FINAL in declaration.modifiers ||
        JavaAstModifier.ABSTRACT in declaration.modifiers
      ) {
        error(declaration.span, "java.semantic.invalid_constructor_modifier", "构造器不能是 static、final 或 abstract。")
      }
      return
    }
    val method = info.declaration as JavaAstMemberDeclaration.Method
    if (info.isAbstract != (method.body == null)) {
      error(
        method.span,
        "java.semantic.invalid_method_body",
        "abstract 方法不能有方法体，非 abstract 方法必须提供方法体。",
      )
    }
    if (info.isAbstract && (info.isStatic || info.isFinal || info.owner.isFinal)) {
      error(method.span, "java.semantic.invalid_abstract_method", "abstract 方法不能是 static/final，也不能位于 final class。")
    }
  }

  /** 没有显式构造器时合成零参数 default constructor，但不伪装成源码成员。 */
  private fun synthesizeDefaultConstructor(owner: S1TypeInfo) {
    val id = symbol(
      JavaSymbolKind.CONSTRUCTOR,
      "<init>",
      owner.symbol,
      owner.nodeId,
      owner.span,
      owner.selfType(),
      registerDeclaration = false,
    )
    val info = S1CallableInfo(
      owner.unit,
      owner,
      null,
      id,
      JavaSemanticCallableKind.CONSTRUCTOR,
      "<init>",
      emptyList(),
      emptyMap(),
      emptyList(),
      emptyList(),
      emptyList(),
      owner.selfType(),
      owner.visibility,
      isStatic = false,
      isFinal = false,
      isAbstract = false,
      erasedSignatureKey = "<init>()",
      erasedDescriptor = "()V",
      isSynthetic = true,
    )
    constructorsByOwner.getOrPut(owner.symbol) { mutableListOf() } += info
    callables += info
    callableDeclarations[id] = info.semanticDeclaration()
  }

  /** Object 与 String 也提供可被 implicit super/new 绑定的内建零参数构造器。 */
  private fun synthesizeBuiltinConstructors() {
    listOf(objectType, stringType).forEach { type ->
      if (constructorsByOwner[type.symbol].isNullOrEmpty()) synthesizeDefaultConstructor(type)
    }
  }

  /** 父类先于子类分配虚槽；合法 override 复用最近父方法的稳定槽。 */
  private fun validateOverridesAndAssignSlots() {
    val visited = mutableSetOf<JavaSymbolId>()
    fun process(type: S1TypeInfo) {
      if (!visited.add(type.symbol)) return
      type.directSuperClass?.symbol?.let(::typeInfo)?.let(::process)
      val methods = callables.filter {
        it.owner.symbol == type.symbol && it.kind == JavaSemanticCallableKind.METHOD
      }
      methods.forEach { method ->
        val inherited = inheritedMethods(type)
          .filter { it.name == method.name && isOverrideSubsignature(type, method, it) }
        val visibleInherited = inherited.filter { isMemberAccessible(it.visibility, it.owner, type) }
        val annotation = (method.declaration as? JavaAstMemberDeclaration.Method)
          ?.annotations?.any { it.qualifiedName == "Override" || it.qualifiedName == "java.lang.Override" }
          ?: false

        if (method.isStatic) {
          if (visibleInherited.any { !it.isStatic }) {
            error(
              method.span,
              "java.semantic.static_instance_override_mismatch",
              "static 方法不能隐藏同 descriptor 的实例方法。",
            )
          }
          if (annotation) {
            error(method.span, "java.semantic.override_annotation_mismatch", "static 方法不能使用 @Override。")
          }
          return@forEach
        }

        if (visibleInherited.any { it.isStatic }) {
          error(
            method.span,
            "java.semantic.static_instance_override_mismatch",
            "实例方法不能 override 同 descriptor 的 static 方法。",
          )
        }
        val overridden = visibleInherited.filter { !it.isStatic }
        overridden.forEach { parent ->
          if (parent.isFinal) {
            error(
              method.span,
              "java.semantic.override_final_method",
              "方法 ${method.name} 不能 override final 方法。",
              listOf(JavaDiagnosticNote("final 方法声明。", parent.span)),
            )
          }
          if (visibilityRank(method.visibility) < visibilityRank(parent.visibility)) {
            error(
              method.span,
              "java.semantic.override_visibility_reduced",
              "override 方法不能缩窄父方法可见性。",
            )
          }
          val parentReturn = inheritedReturnType(type, method, parent)
          val compatibleReturn = method.returnType == parentReturn ||
            method.returnType is JavaSemanticType.Declared &&
            parentReturn is JavaSemanticType.Declared &&
            relations().isSubtype(method.returnType, parentReturn)
          if (!compatibleReturn) {
            error(
              method.span,
              "java.semantic.override_return_type_mismatch",
              "override 方法返回类型必须相同或协变。",
            )
          }
        }
        if (annotation && overridden.isEmpty()) {
          error(
            method.span,
            "java.semantic.override_annotation_mismatch",
            "带 @Override 的方法没有可覆盖的父类实例方法。",
          )
        }
        val parentSlot = overridden.firstOrNull()?.let { virtualSlots[it.symbol] }
        virtualSlots[method.symbol] = parentSlot ?: JavaVirtualSlotId(nextVirtualSlot++)
        if (overridden.isNotEmpty()) overriddenMethods[method.symbol] = overridden.map { it.symbol }
      }
    }
    allTypes().forEach(::process)
  }

  /** 把父方法返回类型投影到当前子类的泛型视角。 */
  private fun inheritedReturnType(
    type: S1TypeInfo,
    method: S1CallableInfo,
    parent: S1CallableInfo,
  ): JavaSemanticType {
    val substitutions = overrideAdaptationSubstitutions(type, method, parent)
      ?: inheritedOwnerSubstitutions(type, parent)
      ?: return JavaSemanticType.Error
    return relations().substitute(parent.returnType, substitutions) ?: JavaSemanticType.Error
  }

  /**
   * 按子类视角判断父方法 subsignature。
   *
   * `A<T>.f(T)` 在 `B extends A<String>` 中继承后的签名是 `f(String)`，因此 B 自身的
   * `f(String)` 必须识别为 override，而不能比较父声明尚未代换的 Object 擦除键。
   */
  private fun isOverrideSubsignature(
    type: S1TypeInfo,
    method: S1CallableInfo,
    parent: S1CallableInfo,
  ): Boolean {
    val ownerSubstitutions = inheritedOwnerSubstitutions(type, parent) ?: return false
    val adaptedSubstitutions = overrideAdaptationSubstitutions(type, method, parent)
    if (adaptedSubstitutions != null) {
      val adaptedParentParameters = parent.parameterTypes.map {
        relations().substitute(it, adaptedSubstitutions) ?: return false
      }
      if (method.parameterTypes == adaptedParentParameters &&
        hasSameMethodTypeParameterBounds(method, parent, adaptedSubstitutions)
      ) {
        return true
      }
    }

    // Java 的 erasure subsignature 只允许非泛型子方法承接泛型父方法；不能把双方都擦除后比较，
    // 否则 f(Box<String>) 会被错误视为可覆盖 f(Box<Object>)。
    if (method.typeParameters.isNotEmpty() || parent.typeParameters.isEmpty()) return false
    val parentParameters = parent.parameterTypes.map { parameter ->
      val ownerSubstituted = relations().substitute(parameter, ownerSubstitutions) ?: return false
      relations().erasure(ownerSubstituted) ?: return false
    }
    return method.parameterTypes == parentParameters
  }

  /** 将父方法类型参数按位置 alpha-renaming 到子方法符号，并合并 owner 代换。 */
  private fun overrideAdaptationSubstitutions(
    type: S1TypeInfo,
    method: S1CallableInfo,
    parent: S1CallableInfo,
  ): Map<JavaSymbolId, JavaSemanticType>? {
    if (method.typeParameters.size != parent.typeParameters.size) return null
    val ownerSubstitutions = inheritedOwnerSubstitutions(type, parent) ?: return null
    val alphaRenaming = parent.typeParameters.zip(method.typeParameters).associate { (from, to) ->
      from to JavaSemanticType.TypeVariable(to)
    }
    return ownerSubstitutions + alphaRenaming
  }

  /** alpha-renaming 后的方法类型参数上界也必须逐项相同。 */
  private fun hasSameMethodTypeParameterBounds(
    method: S1CallableInfo,
    parent: S1CallableInfo,
    substitutions: Map<JavaSymbolId, JavaSemanticType>,
  ): Boolean = parent.typeParameters.zip(method.typeParameters).all { (parentSymbol, methodSymbol) ->
    val parentBounds = typeParameterDeclarations[parentSymbol]?.upperBounds?.map {
      relations().substitute(it, substitutions) ?: return false
    } ?: return false
    val methodBounds = typeParameterDeclarations[methodSymbol]?.upperBounds ?: return false
    parentBounds == methodBounds
  }

  /** 把父 callable 所属 class 投影到当前子类并生成 owner 类型参数代换。 */
  private fun inheritedOwnerSubstitutions(
    type: S1TypeInfo,
    parent: S1CallableInfo,
  ): Map<JavaSymbolId, JavaSemanticType>? {
    val projected = relations().asSupertype(type.selfType(), parent.owner.symbol) ?: return null
    return parent.owner.typeParameters.zip(projected.arguments).toMap()
  }

  /** 字段初始化式在声明所属 static/instance 上下文中按源码顺序分析。 */
  private fun analyzeFieldInitializers() {
    fields.sortedWith(compareBy<S1FieldInfo>({ it.owner.symbol.value }, { it.order })).forEach { field ->
      val initializer = field.declarator.initializer ?: return@forEach
      withBodyContext(S1BodyContext(field.unit, field.owner, null, field.isStatic)) {
        val actual = analyzeExpression(initializer)
        assign(initializer.nodeId, actual, field.type, initializer.span)
      }
    }
  }

  /** 分析显式方法与构造器；合成构造器只需要建立 implicit super 边。 */
  private fun analyzeCallableBodies() {
    callables.forEach { callable ->
      if (callable.owner.kind == JavaSemanticTypeDeclarationKind.BUILTIN) return@forEach
      withBodyContext(S1BodyContext(callable.unit, callable.owner, callable, callable.isStatic)) {
        callable.parameterNodes.zip(callable.parameterSymbols).forEach { (parameter, symbol) ->
          val previous = scope?.resolve(parameter.name)
          if (previous != null) {
            error(parameter.span, "java.semantic.duplicate_parameter", "参数 ${parameter.name} 重复。")
          }
          if (parameter.name !in checkNotNull(scope).values) {
            checkNotNull(scope).values[parameter.name] = symbol
          }
          definitelyAssigned += symbol
          if (JavaAstModifier.FINAL in parameter.modifiers) finalSymbols += symbol
        }
        when (val declaration = callable.declaration) {
          is JavaAstMemberDeclaration.Method -> analyzeMethodBody(callable, declaration)
          is JavaAstMemberDeclaration.Constructor -> analyzeConstructorBody(callable, declaration)
          null -> {
            establishImplicitSuper(callable)
            checkBlankFinalFieldsAtExit(callable.span)
          }
          else -> Unit
        }
      }
    }
  }

  /** 普通方法保留 Stage0 的所有返回路径检查。 */
  private fun analyzeMethodBody(
    callable: S1CallableInfo,
    declaration: JavaAstMemberDeclaration.Method,
  ) {
    val body = declaration.body ?: return
    analyzeStatements(body.statements)
    if (callable.returnType != JavaSemanticType.Void &&
      callable.returnType != JavaSemanticType.Error && canCompleteNormally(body)
    ) {
      error(body.span, "java.semantic.missing_return", "非 void 方法存在可以正常结束而不返回值的路径。")
    }
  }

  /** 构造器委托必须是首句；没有显式委托时绑定父类零参数构造器。 */
  private fun analyzeConstructorBody(
    callable: S1CallableInfo,
    declaration: JavaAstMemberDeclaration.Constructor,
  ) {
    val first = declaration.body.statements.firstOrNull()
    if (first is JavaAstStatement.ConstructorInvocation) {
      analyzeConstructorInvocation(callable, first)
      analyzeStatements(declaration.body.statements.drop(1))
    } else {
      establishImplicitSuper(callable)
      analyzeStatements(declaration.body.statements)
    }
    if (canCompleteNormally(declaration.body)) {
      checkBlankFinalFieldsAtExit(declaration.body.span)
    }
  }

  /** 为 default constructor 或无显式首句的构造器选择父类零参数构造器。 */
  private fun establishImplicitSuper(callable: S1CallableInfo) {
    val parentType = callable.owner.directSuperClass ?: return
    val target = selectConstructor(
      parentType,
      emptyList(),
      callable.span,
      invocationNode = null,
    ) ?: return
    constructorDelegations[callable.symbol] = JavaConstructorDelegation(
      callable.symbol,
      target.info.symbol,
      JavaConstructorDelegationKind.SUPER,
      isImplicit = true,
      invocationNode = null,
    )
  }

  /** 分析显式 this/super 参数并写入 callable binding 与委托边。 */
  private fun analyzeConstructorInvocation(
    source: S1CallableInfo,
    invocation: JavaAstStatement.ConstructorInvocation,
  ) {
    val argumentTypes = invocation.arguments.map(::analyzeExpression)
    if (argumentTypes.any { it == JavaSemanticType.Error }) return
    val targetOwner = when (invocation.kind) {
      JavaAstConstructorInvocationKind.THIS -> source.owner.selfType()
      JavaAstConstructorInvocationKind.SUPER -> source.owner.directSuperClass
    }
    if (targetOwner == null) {
      error(invocation.span, "java.semantic.invalid_super_constructor", "当前类型没有可委托的父类构造器。")
      return
    }
    val target = selectConstructor(targetOwner, argumentTypes, invocation.span, invocation.nodeId)
      ?: return
    recordArgumentConversions(invocation.arguments, argumentTypes, target.parameterTypes)
    calls[invocation.nodeId] = target.binding(
      JavaDispatchKind.SPECIAL,
      JavaReceiverKind.NONE,
      virtualSlot = null,
    )
    resolved[invocation.nodeId] = target.info.symbol
    constructorDelegations[source.symbol] = JavaConstructorDelegation(
      source.symbol,
      target.info.symbol,
      if (invocation.kind == JavaAstConstructorInvocationKind.THIS) {
        JavaConstructorDelegationKind.THIS
      } else {
        JavaConstructorDelegationKind.SUPER
      },
      isImplicit = false,
      invocationNode = invocation.nodeId,
    )
    if (invocation.kind == JavaAstConstructorInvocationKind.THIS) {
      // this(...) 返回后，同类目标构造器已经初始化全部 blank final，当前构造器不得再次写入。
      assignedBlankFinalFields += blankFinalFields(source.owner)
      unassignedBlankFinalFields.clear()
    }
  }

  /** this(...) 委托图必须无环；super 边终止于父类型，不参与同类环。 */
  private fun validateConstructorCycles() {
    val state = mutableMapOf<JavaSymbolId, Int>()
    fun visit(symbol: JavaSymbolId) {
      when (state[symbol]) {
        1 -> {
          error(
            symbols[symbol]?.declarationSpan,
            "java.semantic.constructor_delegation_cycle",
            "构造器 this(...) 委托形成循环。",
          )
          return
        }
        2 -> return
      }
      state[symbol] = 1
      constructorDelegations[symbol]
        ?.takeIf { it.kind == JavaConstructorDelegationKind.THIS }
        ?.targetConstructor
        ?.let(::visit)
      state[symbol] = 2
    }
    constructorsByOwner.values.flatten().forEach { visit(it.symbol) }
  }

  /** 顺序分析语句，确保同一声明中的后续 declarator 能看到前一项。 */
  private fun analyzeStatements(statements: List<JavaAstStatement>) {
    statements.forEach(::analyzeStatement)
  }

  /** 分析控制流并维护 Stage0 已有的 definite-assignment 分支交集规则。 */
  private fun analyzeStatement(statement: JavaAstStatement) {
    when (statement) {
      is JavaAstStatement.Block -> withScope { analyzeStatements(statement.statements) }
      is JavaAstStatement.VariableDeclaration ->
        analyzeVariables(statement.modifiers, statement.type, statement.declarators)
      is JavaAstStatement.ConstructorInvocation -> error(
        statement.span,
        "java.semantic.constructor_invocation_not_first",
        "this(...) 或 super(...) 只能是构造器方法体第一条语句。",
      )
      is JavaAstStatement.Expression -> analyzeExpression(statement.expression)
      is JavaAstStatement.If -> {
        requireType(statement.condition, booleanType(), "if 条件必须是 boolean。")
        val entry = definitelyAssigned.toSet()
        val blankEntryAssigned = assignedBlankFinalFields.toSet()
        val blankEntryUnassigned = unassignedBlankFinalFields.toSet()
        analyzeStatement(statement.thenBranch)
        val thenExit = definitelyAssigned.toSet()
        val blankThenAssigned = assignedBlankFinalFields.toSet()
        val blankThenUnassigned = unassignedBlankFinalFields.toSet()
        replaceAssigned(entry)
        replaceBlankFinalState(blankEntryAssigned, blankEntryUnassigned)
        statement.elseBranch?.let(::analyzeStatement)
        val elseExit = definitelyAssigned.toSet()
        val blankElseAssigned = assignedBlankFinalFields.toSet()
        val blankElseUnassigned = unassignedBlankFinalFields.toSet()
        val completing = buildList {
          if (canCompleteNormally(statement.thenBranch)) add(thenExit)
          if (statement.elseBranch == null || canCompleteNormally(statement.elseBranch)) add(elseExit)
        }
        replaceAssigned(completing.reduceOrNull { left, right -> left.intersect(right) } ?: entry)
        val completingBlankAssigned = buildList {
          if (canCompleteNormally(statement.thenBranch)) add(blankThenAssigned)
          if (statement.elseBranch == null || canCompleteNormally(statement.elseBranch)) {
            add(blankElseAssigned)
          }
        }
        val completingBlankUnassigned = buildList {
          if (canCompleteNormally(statement.thenBranch)) add(blankThenUnassigned)
          if (statement.elseBranch == null || canCompleteNormally(statement.elseBranch)) {
            add(blankElseUnassigned)
          }
        }
        replaceBlankFinalState(
          completingBlankAssigned.reduceOrNull { left, right -> left.intersect(right) }
            ?: blankEntryAssigned,
          completingBlankUnassigned.reduceOrNull { left, right -> left.intersect(right) }
            ?: blankEntryUnassigned,
        )
      }
      is JavaAstStatement.While -> {
        requireType(statement.condition, booleanType(), "while 条件必须是 boolean。")
        val afterCondition = definitelyAssigned.toSet()
        val blankEntryAssigned = assignedBlankFinalFields.toSet()
        val blankEntryUnassigned = unassignedBlankFinalFields.toSet()
        loopDepth++
        try {
          analyzeStatement(statement.body)
        } finally {
          loopDepth--
        }
        val blankBodyUnassigned = unassignedBlankFinalFields.toSet()
        replaceAssigned(afterCondition)
        replaceBlankFinalState(
          blankEntryAssigned,
          blankEntryUnassigned.intersect(blankBodyUnassigned),
        )
      }
      is JavaAstStatement.For -> withScope {
        when (val initializer = statement.initializer) {
          is JavaAstForInitializer.VariableDeclaration ->
            analyzeVariables(initializer.modifiers, initializer.type, initializer.declarators)
          is JavaAstForInitializer.Expressions -> initializer.expressions.forEach(::analyzeExpression)
          null -> Unit
        }
        statement.condition?.let { requireType(it, booleanType(), "for 条件必须是 boolean。") }
        val afterCondition = definitelyAssigned.toSet()
        val blankEntryAssigned = assignedBlankFinalFields.toSet()
        val blankEntryUnassigned = unassignedBlankFinalFields.toSet()
        loopDepth++
        try {
          analyzeStatement(statement.body)
          statement.updates.forEach(::analyzeExpression)
        } finally {
          loopDepth--
        }
        val blankLoopUnassigned = unassignedBlankFinalFields.toSet()
        replaceAssigned(afterCondition)
        replaceBlankFinalState(
          blankEntryAssigned,
          blankEntryUnassigned.intersect(blankLoopUnassigned),
        )
      }
      is JavaAstStatement.Return -> analyzeReturn(statement)
      is JavaAstStatement.Empty -> Unit
    }
  }

  /** 初始化式先于局部名称入作用域，避免错误接受局部变量自引用。 */
  private fun analyzeVariables(
    modifiers: Set<JavaAstModifier>,
    reference: JavaAstTypeReference,
    declarators: List<JavaAstVariableDeclarator>,
  ) {
    val context = context()
    val declaredType = resolveType(
      reference,
      context.unit,
      currentTypeParameterScope(),
      allowVoid = false,
    )
    declarators.forEach { declarator ->
      declarator.initializer?.let { initializer ->
        val actual = analyzeExpression(initializer)
        assign(initializer.nodeId, actual, declaredType, initializer.span)
      }
      val id = declareLocal(
        JavaSymbolKind.LOCAL_VARIABLE,
        declarator.name,
        context.callable?.symbol ?: context.owner.symbol,
        declarator.nodeId,
        declarator.span,
        declaredType,
      )
      if (declarator.initializer != null) definitelyAssigned += id
      if (JavaAstModifier.FINAL in modifiers) {
        finalSymbols += id
        if (declarator.initializer == null) {
          error(
            declarator.span,
            "java.semantic.blank_final_unsupported",
            "Stage1 暂不支持稍后赋值的空白 final 局部变量。",
          )
        }
      }
    }
  }

  /** 校验 return 的有值/无值形态以及赋值转换。 */
  private fun analyzeReturn(statement: JavaAstStatement.Return) {
    val callable = context().callable
    if (callable == null || callable.kind != JavaSemanticCallableKind.METHOD) {
      if (statement.expression != null) {
        error(statement.span, "java.semantic.return_in_constructor", "构造器只能使用无值 return。")
        analyzeExpression(statement.expression)
      } else {
        checkBlankFinalFieldsAtExit(statement.span)
      }
      return
    }
    val value = statement.expression
    if (value == null) {
      if (callable.returnType != JavaSemanticType.Void && callable.returnType != JavaSemanticType.Error) {
        error(statement.span, "java.semantic.return_type_mismatch", "非 void 方法必须返回兼容值。")
      }
      return
    }
    val actual = analyzeExpression(value)
    if (callable.returnType == JavaSemanticType.Void) {
      error(value.span, "java.semantic.return_type_mismatch", "void 方法不能返回值。")
    } else {
      assign(
        value.nodeId,
        actual,
        callable.returnType,
        value.span,
        code = "java.semantic.return_type_mismatch",
      )
    }
  }

  /** 每个访问到的表达式都必须写入 expressionTypes。 */
  private fun analyzeExpression(expression: JavaAstExpression): JavaSemanticType {
    val type = when (expression) {
      is JavaAstExpression.Literal -> literal(expression)
      is JavaAstExpression.Name -> name(expression)
      is JavaAstExpression.This -> thisExpression(expression)
      is JavaAstExpression.Super -> superExpression(expression)
      is JavaAstExpression.Parenthesized -> analyzeExpression(expression.expression)
      is JavaAstExpression.Binary -> binary(expression)
      is JavaAstExpression.Unary -> unary(expression)
      is JavaAstExpression.Assignment -> assignment(expression)
      is JavaAstExpression.MethodInvocation -> invocation(expression)
      is JavaAstExpression.NewObject -> newObject(expression)
      is JavaAstExpression.FieldAccess -> fieldAccess(expression)
    }
    expressionTypes[expression.nodeId] = type
    return type
  }

  /** 值名称按 local/parameter、字段、类型名顺序解析，允许局部变量遮蔽字段。 */
  private fun name(
    expression: JavaAstExpression.Name,
    requireAssigned: Boolean = true,
  ): JavaSemanticType {
    if ('.' !in expression.qualifiedName) {
      scope?.resolve(expression.qualifiedName)?.let { id ->
        val symbol = symbols.getValue(id)
        resolved[expression.nodeId] = id
        valueAccesses[expression.nodeId] = JavaValueAccessBinding(
          id,
          if (symbol.kind == JavaSymbolKind.PARAMETER) {
            JavaValueAccessKind.PARAMETER
          } else {
            JavaValueAccessKind.LOCAL
          },
          JavaReceiverKind.NONE,
        )
        if (requireAssigned && id !in definitelyAssigned) {
          error(
            expression.span,
            "java.semantic.variable_not_initialized",
            "局部变量 ${expression.qualifiedName} 可能尚未初始化。",
          )
          return JavaSemanticType.Error
        }
        return symbol.type ?: JavaSemanticType.Error
      }
      val field = lookupField(context().owner.selfType(), expression.qualifiedName)
      if (field != null && isFieldAccessible(field.info)) {
        if (!field.info.isStatic && context().isStatic) {
          error(expression.span, "java.semantic.instance_field_in_static_context", "static 上下文不能隐式访问实例字段。")
          return JavaSemanticType.Error
        }
        bindField(
          expression.nodeId,
          field,
          if (field.info.isStatic) JavaReceiverKind.NONE else JavaReceiverKind.IMPLICIT_THIS,
        )
        return field.type
      }
    }
    val type = visibleType(expression.qualifiedName, context().unit, expression.span)
    if (type == null) {
      error(
        expression.span,
        "java.semantic.undefined_name",
        "名称 ${expression.qualifiedName} 在当前值或类型作用域中未定义。",
      )
      return JavaSemanticType.Error
    }
    resolved[expression.nodeId] = type.symbol
    return type.selfType()
  }

  /** this 产生当前参数化 owner 类型；static 上下文稳定拒绝。 */
  private fun thisExpression(expression: JavaAstExpression.This): JavaSemanticType {
    if (context().isStatic) {
      error(expression.span, "java.semantic.this_in_static_context", "static 上下文不能使用 this。")
      return JavaSemanticType.Error
    }
    return context().owner.selfType()
  }

  /** super 产生已经完成 owner 参数代换的直接父类类型。 */
  private fun superExpression(expression: JavaAstExpression.Super): JavaSemanticType {
    if (context().isStatic) {
      error(expression.span, "java.semantic.super_in_static_context", "static 上下文不能使用 super。")
      return JavaSemanticType.Error
    }
    val parent = context().owner.directSuperClass
    if (parent == null) {
      error(expression.span, "java.semantic.super_without_parent", "当前类型没有可用的直接父类。")
      return JavaSemanticType.Error
    }
    return parent
  }

  /** 二元数值运算使用 Java binary numeric promotion，不借用 JavaScript 强制转换。 */
  private fun binary(expression: JavaAstExpression.Binary): JavaSemanticType {
    val left = analyzeExpression(expression.left)
    val right = analyzeExpression(expression.right)
    if (left == JavaSemanticType.Error || right == JavaSemanticType.Error) return JavaSemanticType.Error
    return when (expression.operator) {
      JavaAstBinaryOperator.ADD -> {
        if (isString(left) || isString(right)) stringType.selfType()
        else numericBinary(expression.span, left, right, relational = false)
      }
      JavaAstBinaryOperator.MULTIPLY,
      JavaAstBinaryOperator.DIVIDE,
      JavaAstBinaryOperator.REMAINDER,
      JavaAstBinaryOperator.SUBTRACT,
      -> numericBinary(expression.span, left, right, relational = false)
      JavaAstBinaryOperator.SHIFT_LEFT,
      JavaAstBinaryOperator.SHIFT_RIGHT,
      JavaAstBinaryOperator.UNSIGNED_SHIFT_RIGHT,
      -> {
        if (!isIntegral(left) || !isIntegral(right)) {
          error(expression.span, "java.semantic.invalid_binary_operands", "位移操作数必须是 integral primitive。")
          JavaSemanticType.Error
        } else if ((left as JavaSemanticType.Primitive).kind == JavaAstPrimitiveType.LONG) {
          longType()
        } else {
          intType()
        }
      }
      JavaAstBinaryOperator.LESS_THAN,
      JavaAstBinaryOperator.LESS_THAN_OR_EQUAL,
      JavaAstBinaryOperator.GREATER_THAN,
      JavaAstBinaryOperator.GREATER_THAN_OR_EQUAL,
      -> numericBinary(expression.span, left, right, relational = true)
      JavaAstBinaryOperator.EQUAL,
      JavaAstBinaryOperator.NOT_EQUAL,
      -> if (equalityCompatible(left, right)) {
        booleanType()
      } else {
        error(expression.span, "java.semantic.invalid_binary_operands", "等值比较的操作数类型不兼容。")
        JavaSemanticType.Error
      }
      JavaAstBinaryOperator.BITWISE_AND,
      JavaAstBinaryOperator.BITWISE_XOR,
      JavaAstBinaryOperator.BITWISE_OR,
      -> when {
        left == booleanType() && right == booleanType() -> booleanType()
        isIntegral(left) && isIntegral(right) -> checkNotNull(numericPromotion(left, right))
        else -> {
          error(expression.span, "java.semantic.invalid_binary_operands", "位运算要求两个 integral 或两个 boolean 操作数。")
          JavaSemanticType.Error
        }
      }
      JavaAstBinaryOperator.LOGICAL_AND,
      JavaAstBinaryOperator.LOGICAL_OR,
      -> if (left == booleanType() && right == booleanType()) {
        booleanType()
      } else {
        error(expression.span, "java.semantic.invalid_binary_operands", "逻辑操作数必须是 boolean。")
        JavaSemanticType.Error
      }
    }
  }

  /** 统一实现算术与关系运算的数值提升。 */
  private fun numericBinary(
    span: JavaSourceSpan,
    left: JavaSemanticType,
    right: JavaSemanticType,
    relational: Boolean,
  ): JavaSemanticType {
    val promoted = numericPromotion(left, right)
    if (promoted == null) {
      error(span, "java.semantic.invalid_binary_operands", "数值运算要求两个 numeric primitive 操作数。")
      return JavaSemanticType.Error
    }
    return if (relational) booleanType() else promoted
  }

  /** 一元数值操作先做 unary numeric promotion；递增递减保留左值声明类型。 */
  private fun unary(expression: JavaAstExpression.Unary): JavaSemanticType {
    val operand = analyzeExpression(expression.operand)
    if (operand == JavaSemanticType.Error) return JavaSemanticType.Error
    return when (expression.operator) {
      JavaAstUnaryOperator.POSITIVE,
      JavaAstUnaryOperator.NEGATIVE,
      -> unaryNumericPromotion(operand) ?: invalidUnary(expression.span, "正负号操作数必须是 numeric primitive。")
      JavaAstUnaryOperator.BITWISE_NOT ->
        if (isIntegral(operand)) unaryNumericPromotion(operand)!!
        else invalidUnary(expression.span, "按位取反操作数必须是 integral primitive。")
      JavaAstUnaryOperator.LOGICAL_NOT ->
        if (operand == booleanType()) booleanType()
        else invalidUnary(expression.span, "逻辑取反操作数必须是 boolean。")
      JavaAstUnaryOperator.PRE_INCREMENT,
      JavaAstUnaryOperator.PRE_DECREMENT,
      JavaAstUnaryOperator.POST_INCREMENT,
      JavaAstUnaryOperator.POST_DECREMENT,
      -> when {
        !writable(expression.operand) -> invalidUnary(expression.span, "递增递减目标必须是可写值。")
        isFinal(expression.operand) -> {
          error(expression.span, "java.semantic.final_assignment", "final 值不能递增或递减。")
          JavaSemanticType.Error
        }
        !isNumeric(operand) -> invalidUnary(expression.span, "递增递减目标必须是 numeric primitive。")
        else -> operand
      }
    }
  }

  /** 简单赋值登记目标转换；复合赋值只允许 Java 数值/位/字符串常见形态。 */
  private fun assignment(expression: JavaAstExpression.Assignment): JavaSemanticType {
    val targetType = when (val target = expression.target) {
      is JavaAstExpression.Name -> name(
        target,
        requireAssigned = expression.operator != JavaAstAssignmentOperator.ASSIGN,
      ).also { expressionTypes[target.nodeId] = it }
      else -> analyzeExpression(target)
    }
    val valueType = analyzeExpression(expression.value)
    if (!writable(expression.target)) {
      error(expression.target.span, "java.semantic.invalid_assignment_target", "赋值目标必须是局部、参数或字段。")
      return JavaSemanticType.Error
    }
    var blankFinalTarget: JavaSymbolId? = null
    if (isFinal(expression.target)) {
      val targetSymbol = resolved[expression.target.nodeId]
      if (targetSymbol == null || !canAssignBlankFinalField(targetSymbol, expression.operator)) {
        error(expression.target.span, "java.semantic.final_assignment", "final 值不能再次赋值。")
        return JavaSemanticType.Error
      }
      blankFinalTarget = targetSymbol
    }
    if (targetType == JavaSemanticType.Error || valueType == JavaSemanticType.Error) {
      return JavaSemanticType.Error
    }
    val result = if (expression.operator == JavaAstAssignmentOperator.ASSIGN) {
      if (assign(expression.value.nodeId, valueType, targetType, expression.value.span)) {
        targetType
      } else {
        JavaSemanticType.Error
      }
    } else {
      compoundAssignmentType(expression.operator, targetType, valueType, expression.span)
    }
    if (result != JavaSemanticType.Error) {
      blankFinalTarget?.let { symbol ->
        assignedBlankFinalFields += symbol
        unassignedBlankFinalFields -= symbol
      }
      resolved[expression.target.nodeId]?.let { symbol ->
        if (symbols[symbol]?.kind == JavaSymbolKind.LOCAL_VARIABLE) definitelyAssigned += symbol
      }
    }
    return result
  }

  /** 复合赋值按 Java 隐式回转到左值类型处理，boxing 与字符串外的引用组合稳定拒绝。 */
  private fun compoundAssignmentType(
    operator: JavaAstAssignmentOperator,
    target: JavaSemanticType,
    value: JavaSemanticType,
    span: JavaSourceSpan,
  ): JavaSemanticType {
    val valid = when (operator) {
      JavaAstAssignmentOperator.ADD_ASSIGN ->
        isString(target) || numericPromotion(target, value) != null
      JavaAstAssignmentOperator.AND_ASSIGN,
      JavaAstAssignmentOperator.XOR_ASSIGN,
      JavaAstAssignmentOperator.OR_ASSIGN,
      -> target == booleanType() && value == booleanType() || isIntegral(target) && isIntegral(value)
      JavaAstAssignmentOperator.SHIFT_LEFT_ASSIGN,
      JavaAstAssignmentOperator.SHIFT_RIGHT_ASSIGN,
      JavaAstAssignmentOperator.UNSIGNED_SHIFT_RIGHT_ASSIGN,
      -> isIntegral(target) && isIntegral(value)
      JavaAstAssignmentOperator.MULTIPLY_ASSIGN,
      JavaAstAssignmentOperator.DIVIDE_ASSIGN,
      JavaAstAssignmentOperator.REMAINDER_ASSIGN,
      JavaAstAssignmentOperator.SUBTRACT_ASSIGN,
      -> numericPromotion(target, value) != null
      JavaAstAssignmentOperator.ASSIGN -> true
    }
    if (!valid) {
      error(span, "java.semantic.invalid_compound_assignment", "复合赋值两侧类型不兼容。")
      return JavaSemanticType.Error
    }
    return target
  }

  /** 显式字段访问区分 type-qualified、super 与普通实例 receiver。 */
  private fun fieldAccess(expression: JavaAstExpression.FieldAccess): JavaSemanticType {
    val receiver = resolveReceiver(expression.receiver)
    if (receiver == null || receiver.type == JavaSemanticType.Error) return JavaSemanticType.Error
    val declared = receiver.type as? JavaSemanticType.Declared
    if (declared == null) {
      error(expression.span, "java.semantic.field_receiver_not_declared", "字段 receiver 必须是 class 类型。")
      return JavaSemanticType.Error
    }
    val field = lookupField(declared, expression.fieldName)
    if (field == null) {
      error(expression.span, "java.semantic.unknown_field", "类型不包含可见字段 ${expression.fieldName}。")
      return JavaSemanticType.Error
    }
    if (!isFieldAccessible(field.info)) {
      error(expression.span, "java.semantic.inaccessible_field", "字段 ${expression.fieldName} 在当前上下文不可访问。")
      return JavaSemanticType.Error
    }
    if (receiver.kind == JavaReceiverKind.TYPE_QUALIFIED && !field.info.isStatic) {
      error(expression.span, "java.semantic.instance_field_through_type", "实例字段不能通过类型名访问。")
      return JavaSemanticType.Error
    }
    if (receiver.kind != JavaReceiverKind.TYPE_QUALIFIED && field.info.isStatic) {
      error(expression.span, "java.semantic.static_field_through_instance", "static 字段必须通过类型名或非限定名称访问。")
      return JavaSemanticType.Error
    }
    bindField(expression.nodeId, field, receiver.kind)
    return field.type
  }

  /** 写入字段的最终符号、读写种类和 receiver 形态。 */
  private fun bindField(nodeId: JavaNodeId, field: S1FieldView, receiverKind: JavaReceiverKind) {
    resolved[nodeId] = field.info.symbol
    valueAccesses[nodeId] = JavaValueAccessBinding(
      field.info.symbol,
      if (field.info.isStatic) JavaValueAccessKind.STATIC_FIELD else JavaValueAccessKind.INSTANCE_FIELD,
      receiverKind,
    )
  }

  /** 方法调用同时处理继承成员代换、方法泛型推断、访问性与唯一最具体重载。 */
  private fun invocation(expression: JavaAstExpression.MethodInvocation): JavaSemanticType {
    val receiver = expression.receiver?.let(::resolveReceiver) ?: S1Receiver(
      context().owner.selfType(),
      if (context().isStatic) JavaReceiverKind.NONE else JavaReceiverKind.IMPLICIT_THIS,
      isImplicit = true,
    )
    val argumentTypes = expression.arguments.map(::analyzeExpression)
    if (receiver.type == JavaSemanticType.Error ||
      argumentTypes.any { it == JavaSemanticType.Error }
    ) {
      return JavaSemanticType.Error
    }
    val declaredReceiver = receiver.type as? JavaSemanticType.Declared
    if (declaredReceiver == null) {
      error(expression.span, "java.semantic.method_receiver_not_declared", "方法 receiver 必须是 class 类型。")
      return JavaSemanticType.Error
    }
    val explicitTypeArguments = expression.typeArguments.map {
      resolveType(it, context().unit, currentTypeParameterScope(), allowVoid = false)
    }
    val named = methodCandidates(declaredReceiver, expression.methodName)
    val modeCandidates = named.filter { candidate ->
      when {
        receiver.kind == JavaReceiverKind.TYPE_QUALIFIED -> candidate.info.isStatic
        receiver.isImplicit && context().isStatic -> candidate.info.isStatic
        receiver.kind == JavaReceiverKind.SUPER -> !candidate.info.isStatic
        else -> !candidate.info.isStatic
      }
    }
    if (named.isNotEmpty() && modeCandidates.isEmpty()) {
      error(
        expression.span,
        "java.semantic.static_instance_call_mismatch",
        "方法 ${expression.methodName} 的 static/instance receiver 形态不匹配。",
      )
      return JavaSemanticType.Error
    }
    val accessible = modeCandidates.filter { isCallableAccessible(it.info) }
    if (modeCandidates.isNotEmpty() && accessible.isEmpty()) {
      error(expression.span, "java.semantic.inaccessible_method", "方法 ${expression.methodName} 在当前上下文不可访问。")
      return JavaSemanticType.Error
    }
    val instantiated = accessible.mapNotNull { candidate ->
      instantiateCallable(candidate, argumentTypes, explicitTypeArguments)
    }
    val selected = selectMostSpecific(instantiated, expression.span, expression.methodName)
      ?: return JavaSemanticType.Error
    recordArgumentConversions(expression.arguments, argumentTypes, selected.parameterTypes)
    val dispatch = when {
      selected.info.isStatic -> JavaDispatchKind.STATIC
      receiver.kind == JavaReceiverKind.SUPER -> JavaDispatchKind.SPECIAL
      else -> JavaDispatchKind.VIRTUAL
    }
    val bindingReceiver = when {
      selected.info.isStatic && receiver.kind != JavaReceiverKind.TYPE_QUALIFIED -> JavaReceiverKind.NONE
      else -> receiver.kind
    }
    resolved[expression.nodeId] = selected.info.symbol
    calls[expression.nodeId] = selected.binding(
      dispatch,
      bindingReceiver,
      if (dispatch == JavaDispatchKind.VIRTUAL) virtualSlots[selected.info.symbol] else null,
    )
    return selected.returnType
  }

  /** new 表达式支持显式参数化类型与从构造器实参推断的 diamond。 */
  private fun newObject(expression: JavaAstExpression.NewObject): JavaSemanticType {
    val argumentTypes = expression.arguments.map(::analyzeExpression)
    if (argumentTypes.any { it == JavaSemanticType.Error }) return JavaSemanticType.Error
    val named = expression.type as? JavaAstTypeReference.Named
    if (named == null) {
      resolveType(expression.type, context().unit, currentTypeParameterScope(), allowVoid = false)
      error(expression.span, "java.semantic.invalid_new_type", "new 只能创建 class 声明类型。")
      return JavaSemanticType.Error
    }
    val createdType = if (named.usesDiamond) {
      inferDiamondType(named, argumentTypes, expression.span)
    } else {
      resolveType(named, context().unit, currentTypeParameterScope(), allowVoid = false)
        as? JavaSemanticType.Declared
    }
    if (createdType == null) return JavaSemanticType.Error
    val owner = typeInfo(createdType.symbol)
    if (owner == null || owner.kind != JavaSemanticTypeDeclarationKind.CLASS &&
      owner.kind != JavaSemanticTypeDeclarationKind.BUILTIN
    ) {
      error(expression.span, "java.semantic.invalid_new_type", "new 的目标必须是可实例化 class。")
      return JavaSemanticType.Error
    }
    if (owner.declaration?.modifiers?.contains(JavaAstModifier.ABSTRACT) == true) {
      error(expression.span, "java.semantic.instantiate_abstract_type", "不能实例化 abstract class。")
      return JavaSemanticType.Error
    }
    val selected = selectConstructor(createdType, argumentTypes, expression.span, expression.nodeId)
      ?: return JavaSemanticType.Error
    recordArgumentConversions(expression.arguments, argumentTypes, selected.parameterTypes)
    resolved[expression.nodeId] = selected.info.symbol
    calls[expression.nodeId] = selected.binding(
      JavaDispatchKind.SPECIAL,
      JavaReceiverKind.NONE,
      virtualSlot = null,
    )
    resolved[named.nodeId] = owner.symbol
    return createdType
  }

  /** diamond 仅从构造器实参约束推断 class 类型参数，不执行 target-only inference。 */
  private fun inferDiamondType(
    reference: JavaAstTypeReference.Named,
    argumentTypes: List<JavaSemanticType>,
    span: JavaSourceSpan,
  ): JavaSemanticType.Declared? {
    if (reference.arguments.isNotEmpty()) {
      error(reference.span, "java.semantic.invalid_diamond", "diamond 不能同时携带显式类型实参。")
      return null
    }
    val owner = visibleType(reference.qualifiedName, context().unit, reference.span) ?: return null
    if (owner.typeParameters.isEmpty()) return JavaSemanticType.Declared(owner.symbol, emptyList())
    val inference = JavaGenericInference(relations(), typeParameterDeclarations)
    val inferredTypes = constructorsByOwner[owner.symbol].orEmpty().mapNotNull { constructor ->
      val substitutions = inference.infer(
        owner.typeParameters,
        constructor.parameterTypes,
        argumentTypes,
      ) ?: return@mapNotNull null
      owner.typeParameters.map { substitutions.getValue(it) }
    }.distinct()
    if (inferredTypes.size != 1) {
      error(
        span,
        if (inferredTypes.isEmpty()) "java.semantic.diamond_inference_failed" else "java.semantic.ambiguous_overload",
        "无法从构造器实参唯一推断 diamond 类型实参。",
      )
      return null
    }
    return JavaSemanticType.Declared(owner.symbol, inferredTypes.single())
  }

  /** 选择构造器并完成 class/constructor 两层类型参数代换。 */
  private fun selectConstructor(
    ownerType: JavaSemanticType.Declared,
    argumentTypes: List<JavaSemanticType>,
    span: JavaSourceSpan,
    invocationNode: JavaNodeId?,
  ): S1InstantiatedCallable? {
    val owner = typeInfo(ownerType.symbol) ?: return null
    val classSubstitutions = owner.typeParameters.zip(ownerType.arguments).toMap()
    val candidates = constructorsByOwner[owner.symbol].orEmpty()
      .filter(::isCallableAccessible)
      .map { S1CallableCandidate(it, classSubstitutions) }
      .mapNotNull { instantiateCallable(it, argumentTypes, emptyList()) }
    val selected = selectMostSpecific(candidates, span, owner.simpleName)
    if (selected == null && invocationNode == null && candidates.isEmpty()) {
      // selectMostSpecific 已产生稳定诊断；该分支只强调 implicit super 的来源。
      return null
    }
    return selected
  }

  /** 把 owner 代换与方法类型推断合并，得到可参与 overload 的实际签名。 */
  private fun instantiateCallable(
    candidate: S1CallableCandidate,
    argumentTypes: List<JavaSemanticType>,
    explicitTypeArguments: List<JavaSemanticType>,
  ): S1InstantiatedCallable? {
    if (candidate.info.parameterTypes.size != argumentTypes.size) return null
    val ownerParameters = candidate.info.parameterTypes.map {
      relations().substitute(it, candidate.ownerSubstitutions) ?: return null
    }
    val ownerReturn = relations().substitute(candidate.info.returnType, candidate.ownerSubstitutions)
      ?: return null
    val callableSubstitutions = if (candidate.info.typeParameters.isEmpty()) {
      if (explicitTypeArguments.isNotEmpty()) return null
      emptyMap()
    } else {
      JavaGenericInference(relations(), typeParameterDeclarations).infer(
        candidate.info.typeParameters,
        ownerParameters,
        argumentTypes,
        explicitTypeArguments,
        fixedSubstitutions = candidate.ownerSubstitutions,
      ) ?: return null
    }
    val parameterTypes = ownerParameters.map {
      relations().substitute(it, callableSubstitutions) ?: return null
    }
    if (!argumentTypes.zip(parameterTypes).all { (actual, expected) -> compatibility(actual, expected) != null }) {
      return null
    }
    val returnType = relations().substitute(ownerReturn, callableSubstitutions) ?: return null
    return S1InstantiatedCallable(
      candidate.info,
      parameterTypes,
      returnType,
      candidate.ownerSubstitutions + callableSubstitutions,
    )
  }

  /**
   * 在同一 strict-invocation applicability phase 内按参数逐位关系选择唯一最具体候选。
   *
   * 禁止把各参数 widening 距离求和：`f(short,int)` 与 `f(int,byte)` 对 `(byte,byte)` 各自在
   * 不同参数上更具体，Java 必须报告歧义。
   */
  private fun selectMostSpecific(
    candidates: List<S1InstantiatedCallable>,
    span: JavaSourceSpan,
    displayName: String,
  ): S1InstantiatedCallable? {
    if (candidates.isEmpty()) {
      error(span, "java.semantic.no_applicable_overload", "找不到参数数量和类型兼容的 callable $displayName。")
      return null
    }
    if (candidates.size == 1) return candidates.single()
    val maximal = candidates.filter { candidate ->
      candidates.all { other -> candidate === other || isAtLeastAsSpecific(candidate, other) }
    }
    if (maximal.size == 1) return maximal.single()
    error(
      span,
      "java.semantic.ambiguous_overload",
      "callable $displayName 存在多个同等适用且无法唯一确定最具体者的重载。",
      candidates.map { JavaDiagnosticNote("可适用候选。", it.info.span) },
    )
    return null
  }

  /** A 的每个参数都能赋值拓宽到 B 时，A 至少与 B 一样具体。 */
  private fun isAtLeastAsSpecific(
    candidate: S1InstantiatedCallable,
    other: S1InstantiatedCallable,
  ): Boolean = candidate.parameterTypes.zip(other.parameterTypes).all { (left, right) ->
    left == right || compatibility(left, right) != null
  }

  /** 对最终选中的参数逐一写入转换 side table。 */
  private fun recordArgumentConversions(
    arguments: List<JavaAstExpression>,
    actualTypes: List<JavaSemanticType>,
    parameterTypes: List<JavaSemanticType>,
  ) {
    arguments.zip(actualTypes.zip(parameterTypes)).forEach { (argument, pair) ->
      compatibility(pair.first, pair.second)?.let { conversions[argument.nodeId] = it }
    }
  }

  /** 解析 receiver；类型名限定符不会产生运行时求值。 */
  private fun resolveReceiver(expression: JavaAstExpression): S1Receiver? {
    if (expression is JavaAstExpression.Name &&
      ('.' in expression.qualifiedName || scope?.resolve(expression.qualifiedName) == null) &&
      lookupField(context().owner.selfType(), expression.qualifiedName) == null
    ) {
      val type = visibleTypeOrNull(expression.qualifiedName, context().unit, expression.span)
      if (type != null) {
        resolved[expression.nodeId] = type.symbol
        expressionTypes[expression.nodeId] = type.selfType()
        return S1Receiver(type.selfType(), JavaReceiverKind.TYPE_QUALIFIED)
      }
    }
    val type = analyzeExpression(expression)
    return when (expression) {
      is JavaAstExpression.Super -> S1Receiver(type, JavaReceiverKind.SUPER)
      else -> S1Receiver(type, JavaReceiverKind.EXPLICIT)
    }
  }

  /** 从 receiver class 向上收集方法；子类同擦除 descriptor 会遮蔽父候选。 */
  private fun methodCandidates(
    receiverType: JavaSemanticType.Declared,
    name: String,
  ): List<S1CallableCandidate> {
    val result = mutableListOf<S1CallableCandidate>()
    val seenDescriptors = mutableSetOf<String>()
    var current: JavaSemanticType.Declared? = receiverType
    val visited = mutableSetOf<JavaSymbolId>()
    while (current != null && visited.add(current.symbol)) {
      val owner = typeInfo(current.symbol) ?: break
      val substitutions = owner.typeParameters.zip(current.arguments).toMap()
      methodsByOwnerAndName[owner.symbol to name].orEmpty().forEach { method ->
        val visibleParameters = method.parameterTypes.map {
          relations().substitute(it, substitutions) ?: return@forEach
        }
        val visibleSignatureKey = erasedSignatureKey(method.name, visibleParameters)
        if (seenDescriptors.add(visibleSignatureKey)) {
          result += S1CallableCandidate(method, substitutions)
        }
      }
      current = owner.directSuperClass?.let {
        relations().substitute(it, substitutions) as? JavaSemanticType.Declared
      }
    }
    return result
  }

  /** 返回全部祖先方法，供 override 校验使用；不做子类 descriptor 遮蔽。 */
  private fun inheritedMethods(type: S1TypeInfo): List<S1CallableInfo> {
    val result = mutableListOf<S1CallableInfo>()
    var parent = type.directSuperClass?.symbol?.let(::typeInfo)
    val visited = mutableSetOf<JavaSymbolId>()
    while (parent != null && visited.add(parent.symbol)) {
      result += callables.filter {
        it.owner.symbol == parent.symbol && it.kind == JavaSemanticCallableKind.METHOD
      }
      parent = parent.directSuperClass?.symbol?.let(::typeInfo)
    }
    return result
  }

  /** 查找字段并沿继承链代换 generic owner 类型参数。 */
  private fun lookupField(
    receiverType: JavaSemanticType.Declared,
    name: String,
  ): S1FieldView? {
    var current: JavaSemanticType.Declared? = receiverType
    val visited = mutableSetOf<JavaSymbolId>()
    while (current != null && visited.add(current.symbol)) {
      val owner = typeInfo(current.symbol) ?: return null
      val substitutions = owner.typeParameters.zip(current.arguments).toMap()
      fieldsByOwnerAndName[owner.symbol to name]?.let { field ->
        val type = relations().substitute(field.type, substitutions) ?: JavaSemanticType.Error
        return S1FieldView(field, type)
      }
      current = owner.directSuperClass?.let {
        relations().substitute(it, substitutions) as? JavaSemanticType.Declared
      }
    }
    return null
  }

  /** 字段可见性使用声明 owner 与当前 owner 的 package/subclass 关系。 */
  private fun isFieldAccessible(field: S1FieldInfo): Boolean =
    isMemberAccessible(field.visibility, field.owner, context().owner)

  /** callable 可见性规则与字段一致；构造器 private 仅所属类型自身可见。 */
  private fun isCallableAccessible(callable: S1CallableInfo): Boolean =
    isMemberAccessible(callable.visibility, callable.owner, context().owner)

  /** Java 四级成员可见性；protected 接受同 package 或子类。 */
  private fun isMemberAccessible(
    visibility: JavaVisibility,
    declaringOwner: S1TypeInfo,
    accessingOwner: S1TypeInfo,
  ): Boolean = when (visibility) {
    JavaVisibility.PUBLIC -> true
    JavaVisibility.PRIVATE -> declaringOwner.symbol == accessingOwner.symbol
    JavaVisibility.PACKAGE_PRIVATE -> declaringOwner.packageName == accessingOwner.packageName
    JavaVisibility.PROTECTED -> declaringOwner.packageName == accessingOwner.packageName ||
      isSubclass(accessingOwner, declaringOwner)
  }

  /** 沿父类链判断 subclass，损坏或循环模型保守返回 false。 */
  private fun isSubclass(child: S1TypeInfo, parent: S1TypeInfo): Boolean {
    var current = child.directSuperClass?.symbol
    val visited = mutableSetOf<JavaSymbolId>()
    while (current != null && visited.add(current)) {
      if (current == parent.symbol) return true
      current = typeInfo(current)?.directSuperClass?.symbol
    }
    return false
  }

  /** 解析 AST 类型；raw、primitive 泛型实参、非法 wildcard 与 target-only diamond 均稳定拒绝。 */
  private fun resolveType(
    reference: JavaAstTypeReference,
    unit: JavaAstCompilationUnit,
    typeParameters: Map<String, JavaSymbolId>,
    allowVoid: Boolean,
    insideTypeArgument: Boolean = false,
  ): JavaSemanticType = when (reference) {
    is JavaAstTypeReference.Primitive -> {
      if (insideTypeArgument) {
        error(reference.span, "java.semantic.primitive_type_argument", "泛型类型实参不能是 primitive。")
        JavaSemanticType.Error
      } else {
        JavaSemanticType.Primitive(reference.kind)
      }
    }
    is JavaAstTypeReference.Void -> if (allowVoid && !insideTypeArgument) {
      JavaSemanticType.Void
    } else {
      error(reference.span, "java.semantic.void_type_not_allowed", "该类型位置不能使用 void。")
      JavaSemanticType.Error
    }
    is JavaAstTypeReference.Array -> {
      var component = resolveType(
        reference.componentType,
        unit,
        typeParameters,
        allowVoid = false,
      )
      repeat(reference.dimensions) { component = JavaSemanticType.Array(component) }
      component
    }
    is JavaAstTypeReference.Wildcard -> {
      if (!insideTypeArgument) {
        error(reference.span, "java.semantic.wildcard_outside_type_argument", "wildcard 只能出现在泛型类型实参位置。")
        JavaSemanticType.Error
      } else {
        val upper = reference.upperBound?.let {
          resolveType(it, unit, typeParameters, allowVoid = false)
        }
        val lower = reference.lowerBound?.let {
          resolveType(it, unit, typeParameters, allowVoid = false)
        }
        JavaSemanticType.Wildcard(upper, lower)
      }
    }
    is JavaAstTypeReference.Named -> {
      if ('.' !in reference.qualifiedName && reference.qualifiedName in typeParameters) {
        if (reference.arguments.isNotEmpty() || reference.usesDiamond) {
          error(reference.span, "java.semantic.type_variable_arguments", "类型变量不能携带类型实参。")
          JavaSemanticType.Error
        } else {
          val symbol = typeParameters.getValue(reference.qualifiedName)
          resolved[reference.nodeId] = symbol
          JavaSemanticType.TypeVariable(symbol)
        }
      } else {
        val type = visibleType(reference.qualifiedName, unit, reference.span)
        if (type == null) {
          JavaSemanticType.Error
        } else if (reference.usesDiamond) {
          error(reference.span, "java.semantic.diamond_context_required", "diamond 仅允许在 new 表达式并由构造器实参推断。")
          JavaSemanticType.Error
        } else {
          val arguments = reference.arguments.map {
            resolveType(it, unit, typeParameters, allowVoid = false, insideTypeArgument = true)
          }
          if (arguments.size != type.typeParameters.size) {
            error(
              reference.span,
              if (arguments.isEmpty() && type.typeParameters.isNotEmpty()) {
                "java.semantic.raw_type_unsupported"
              } else {
                "java.semantic.type_argument_arity"
              },
              "类型 ${type.qualifiedName} 需要 ${type.typeParameters.size} 个类型实参，实际为 ${arguments.size}。",
            )
            JavaSemanticType.Error
          } else {
            val declared = JavaSemanticType.Declared(type.symbol, arguments)
            resolved[reference.nodeId] = type.symbol
            validateTypeArgumentBounds(type, declared, reference.span)
            declared
          }
        }
      }
    }
  }

  /** 将声明类型实参代入 class 上界并做常用 bound 校验。 */
  private fun validateTypeArgumentBounds(
    type: S1TypeInfo,
    declared: JavaSemanticType.Declared,
    span: JavaSourceSpan,
  ) {
    if (declared.arguments.any { it == JavaSemanticType.Error || it is JavaSemanticType.Wildcard }) return
    val substitutions = type.typeParameters.zip(declared.arguments).toMap()
    type.typeParameters.zip(declared.arguments).forEach { (parameter, actual) ->
      val declaration = typeParameterDeclarations[parameter] ?: return@forEach
      declaration.upperBounds.forEach { bound ->
        val substituted = relations().substitute(bound, substitutions) ?: return@forEach
        if (!relations().isAssignmentCompatible(actual, substituted)) {
          error(span, "java.semantic.type_argument_bound", "类型实参不满足声明的泛型上界。")
        }
      }
    }
  }

  /** 登记常用 Java 字面量；浮点和 char 当前只需要类型，不写不可表达的常量值。 */
  private fun literal(expression: JavaAstExpression.Literal): JavaSemanticType =
    when (expression.kind) {
      JavaAstLiteralKind.BOOLEAN -> booleanType().also {
        constants[expression.nodeId] = JavaConstantValue.BooleanValue(expression.tokenText == "true")
      }
      JavaAstLiteralKind.INTEGER -> integerLiteral(expression)
      JavaAstLiteralKind.FLOATING_POINT -> floatingLiteral(expression)
      JavaAstLiteralKind.CHARACTER -> characterLiteral(expression)
      JavaAstLiteralKind.STRING -> stringLiteral(expression)
      JavaAstLiteralKind.NULL -> JavaSemanticType.Null.also {
        constants[expression.nodeId] = JavaConstantValue.NullValue
      }
    }

  /** 解析常用十/十六/八/二进制 int/long；超范围或错误下划线稳定诊断。 */
  private fun integerLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val parsed = parseStage1Integer(expression.tokenText)
    if (parsed == null) {
      error(expression.span, "java.semantic.invalid_integer_literal", "整数 literal 格式无效或超出 long 范围。")
      return JavaSemanticType.Error
    }
    val isLong = expression.tokenText.endsWith('l', ignoreCase = true)
    if (!isLong && parsed !in Int.MIN_VALUE..Int.MAX_VALUE) {
      error(expression.span, "java.semantic.integer_out_of_range", "无 long 后缀的整数 literal 超出 int 范围。")
      return JavaSemanticType.Error
    }
    return if (isLong) {
      constants[expression.nodeId] = JavaConstantValue.LongValue(parsed)
      longType()
    } else {
      constants[expression.nodeId] = JavaConstantValue.IntValue(parsed.toInt())
      intType()
    }
  }

  /** 浮点 token 根据 f/F 后缀区分 float，其余为 double。 */
  private fun floatingLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val compact = expression.tokenText.replace("_", "")
    val suffix = compact.lastOrNull()?.lowercaseChar()
    val number = if (suffix == 'f' || suffix == 'd') compact.dropLast(1) else compact
    if (number.toDoubleOrNull() == null) {
      error(expression.span, "java.semantic.invalid_floating_literal", "浮点 literal 格式无效。")
      return JavaSemanticType.Error
    }
    return JavaSemanticType.Primitive(
      if (suffix == 'f') JavaAstPrimitiveType.FLOAT else JavaAstPrimitiveType.DOUBLE,
    )
  }

  /** char literal 必须解码为一个 UTF-16 code unit。 */
  private fun characterLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val value = decodeStage1Character(expression.tokenText)
    if (value == null) {
      error(expression.span, "java.semantic.invalid_character_literal", "char literal 必须包含一个合法字符。")
      return JavaSemanticType.Error
    }
    return JavaSemanticType.Primitive(JavaAstPrimitiveType.CHAR)
  }

  /** 解码 String token 并保存去引号后的常量值。 */
  private fun stringLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val value = decodeStage1String(expression.tokenText)
    if (value == null) {
      error(expression.span, "java.semantic.invalid_string_literal", "String literal 包含无效 Java 转义。")
      return JavaSemanticType.Error
    }
    constants[expression.nodeId] = JavaConstantValue.StringValue(value)
    return stringType.selfType()
  }

  /** visible type 解析失败时产生 unknown_type；内部探测可使用无诊断版本。 */
  private fun visibleType(
    name: String,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): S1TypeInfo? {
    val before = diagnostics.size
    val result = visibleTypeOrNull(name, unit, span)
    if (result == null && diagnostics.size == before) {
      error(span, "java.semantic.unknown_type", "当前 compilation unit 无法解析类型名 $name。")
    }
    return result
  }

  /** 按限定名、同 package、java.lang、显式 import、通配 import 顺序解析。 */
  private fun visibleTypeOrNull(
    name: String,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): S1TypeInfo? {
    if ('.' in name) return typesByQualifiedName[name]?.let { ensureTypeAccessible(it, unit, span) }
    typesByQualifiedName[qualifyStage1(unit.packageName, name)]?.let { return it }
    typesByQualifiedName["java.lang.$name"]?.let { return it }
    val explicit = unit.imports
      .filter { !it.isStatic && !it.isWildcard && it.qualifiedName.substringAfterLast('.') == name }
      .mapNotNull { typesByQualifiedName[it.qualifiedName] }
      .distinctBy { it.symbol }
    if (explicit.size == 1) return ensureTypeAccessible(explicit.single(), unit, span)
    if (explicit.size > 1) {
      error(span, "java.semantic.ambiguous_type", "类型名 $name 被多个显式 import 引入。")
      return null
    }
    val wildcard = unit.imports.filter { !it.isStatic && it.isWildcard }
      .mapNotNull { typesByQualifiedName[qualifyStage1(it.qualifiedName, name)] }
      .distinctBy { it.symbol }
    if (wildcard.size == 1) return ensureTypeAccessible(wildcard.single(), unit, span)
    if (wildcard.size > 1) {
      error(span, "java.semantic.ambiguous_type", "类型名 $name 在多个通配 import 中可见。")
    }
    return null
  }

  /** 顶层类型跨 package 仅 public 可见。 */
  private fun ensureTypeAccessible(
    type: S1TypeInfo,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): S1TypeInfo? {
    if (type.kind == JavaSemanticTypeDeclarationKind.BUILTIN ||
      type.packageName == unit.packageName || type.visibility == JavaVisibility.PUBLIC
    ) {
      return type
    }
    error(
      span,
      "java.semantic.inaccessible_type",
      "类型 ${type.qualifiedName} 在当前 package 中不可访问。",
      listOf(JavaDiagnosticNote("不可访问的类型声明。", type.span)),
    )
    return null
  }

  /** 赋值与 overload 共用冻结的 JavaTypeRelations。 */
  private fun compatibility(
    actual: JavaSemanticType,
    expected: JavaSemanticType,
  ): JavaSemanticConversion? = relations().assignmentConversion(actual, expected)

  /** 校验并登记表达式到目标类型的转换。 */
  private fun assign(
    nodeId: JavaNodeId,
    actual: JavaSemanticType,
    expected: JavaSemanticType,
    span: JavaSourceSpan,
    code: String = "java.semantic.type_mismatch",
    message: String = "表达式类型与目标类型不兼容。",
  ): Boolean {
    if (actual == JavaSemanticType.Error || expected == JavaSemanticType.Error) return false
    val conversion = compatibility(actual, expected)
    if (conversion == null) {
      error(span, code, message)
      return false
    }
    conversions[nodeId] = conversion
    return true
  }

  /** 要求表达式具备指定类型。 */
  private fun requireType(
    expression: JavaAstExpression,
    expected: JavaSemanticType,
    message: String,
  ): JavaSemanticType {
    val actual = analyzeExpression(expression)
    return if (assign(expression.nodeId, actual, expected, expression.span, message = message)) {
      expected
    } else {
      JavaSemanticType.Error
    }
  }

  /** 等值比较接受数值 primitive、boolean 同类或相互可转换的引用/null。 */
  private fun equalityCompatible(left: JavaSemanticType, right: JavaSemanticType): Boolean = when {
    left == booleanType() || right == booleanType() -> left == right
    isNumeric(left) && isNumeric(right) -> true
    left == JavaSemanticType.Null -> isReference(right)
    right == JavaSemanticType.Null -> isReference(left)
    isReference(left) && isReference(right) ->
      relations().isSubtype(left, right) || relations().isSubtype(right, left)
    else -> false
  }

  /** Name/FieldAccess 是 Stage1 可写左值。 */
  private fun writable(expression: JavaAstExpression): Boolean {
    if (expression !is JavaAstExpression.Name && expression !is JavaAstExpression.FieldAccess) return false
    val kind = resolved[expression.nodeId]?.let(symbols::get)?.kind
    return kind == JavaSymbolKind.PARAMETER || kind == JavaSymbolKind.LOCAL_VARIABLE ||
      kind == JavaSymbolKind.FIELD
  }

  /** 查询已经完成绑定的目标是否为 final。 */
  private fun isFinal(expression: JavaAstExpression): Boolean =
    resolved[expression.nodeId] in finalSymbols

  /**
   * 允许所属构造器对未带初始化式的 instance final 字段执行一次简单赋值。
   *
   * 只有在所有到达路径上仍 definite-unassigned 时允许写入；复合赋值和其他 owner 的写入拒绝。
   */
  private fun canAssignBlankFinalField(
    symbol: JavaSymbolId,
    operator: JavaAstAssignmentOperator,
  ): Boolean {
    if (operator != JavaAstAssignmentOperator.ASSIGN || loopDepth > 0 ||
      symbol !in unassignedBlankFinalFields
    ) {
      return false
    }
    val field = fields.firstOrNull { it.symbol == symbol } ?: return false
    val callable = context().callable ?: return false
    return callable.kind == JavaSemanticCallableKind.CONSTRUCTOR &&
      callable.owner.symbol == field.owner.symbol && !field.isStatic &&
      field.declarator.initializer == null
  }

  /** 返回 owner 自身需要由构造器初始化的 instance blank final 字段。 */
  private fun blankFinalFields(owner: S1TypeInfo): Set<JavaSymbolId> = fields.asSequence()
    .filter {
      it.owner.symbol == owner.symbol && it.isFinal && !it.isStatic &&
        it.declarator.initializer == null
    }
    .map { it.symbol }
    .toSet()

  /** 每个构造器正常出口都必须 definite-assigned 全部 instance blank final。 */
  private fun checkBlankFinalFieldsAtExit(span: JavaSourceSpan) {
    val callable = context().callable ?: return
    if (callable.kind != JavaSemanticCallableKind.CONSTRUCTOR) return
    val missing = blankFinalFields(callable.owner) - assignedBlankFinalFields
    if (missing.isNotEmpty()) {
      error(
        span,
        "java.semantic.blank_final_field_not_initialized",
        "构造器正常出口前未初始化全部 blank final 字段：" +
          missing.joinToString { symbols.getValue(it).name } + "。",
      )
    }
  }

  /** 保守判断语句能否正常结束，用于所有返回路径检查。 */
  private fun canCompleteNormally(statement: JavaAstStatement): Boolean = when (statement) {
    is JavaAstStatement.Block -> statement.statements.fold(true) { reachable, child ->
      reachable && canCompleteNormally(child)
    }
    is JavaAstStatement.If -> statement.elseBranch == null ||
      canCompleteNormally(statement.thenBranch) || canCompleteNormally(statement.elseBranch)
    is JavaAstStatement.While -> !isConstantTrue(statement.condition)
    is JavaAstStatement.For -> statement.condition?.let(::isConstantTrue) != true
    is JavaAstStatement.Return -> false
    else -> true
  }

  /** 识别 true 与括号包装。 */
  private fun isConstantTrue(expression: JavaAstExpression): Boolean = when (expression) {
    is JavaAstExpression.Literal ->
      expression.kind == JavaAstLiteralKind.BOOLEAN && expression.tokenText == "true"
    is JavaAstExpression.Parenthesized -> isConstantTrue(expression.expression)
    else -> false
  }

  /** 登记局部声明；Java 不允许遮蔽仍处于作用域内的参数或局部。 */
  private fun declareLocal(
    kind: JavaSymbolKind,
    name: String,
    owner: JavaSymbolId,
    nodeId: JavaNodeId,
    span: JavaSourceSpan,
    type: JavaSemanticType,
  ): JavaSymbolId {
    val current = checkNotNull(scope)
    current.resolve(name)?.let { previous ->
      error(
        span,
        "java.semantic.duplicate_local_declaration",
        "名称 $name 与仍处于作用域内的局部声明重复。",
        listOf(JavaDiagnosticNote("原声明位于此处。", symbols.getValue(previous).declarationSpan)),
      )
    }
    val id = symbol(kind, name, owner, nodeId, span, type)
    if (name !in current.values) current.values[name] = id
    return id
  }

  /** 创建 symbol 并按需登记声明节点。 */
  private fun symbol(
    kind: JavaSymbolKind,
    name: String,
    owner: JavaSymbolId?,
    nodeId: JavaNodeId,
    span: JavaSourceSpan,
    type: JavaSemanticType?,
    registerDeclaration: Boolean = true,
  ): JavaSymbolId {
    val id = JavaSymbolId(nextSymbol++)
    symbols[id] = JavaSemanticSymbol(id, kind, name, owner, nodeId, span, type)
    if (registerDeclaration) declarations[nodeId] = id
    return id
  }

  /** 切换方法/字段初始化上下文并在退出时恢复全部局部状态。 */
  private inline fun withBodyContext(context: S1BodyContext, block: () -> Unit) {
    val previousContext = bodyContext
    val previousScope = scope
    val previousAssigned = definitelyAssigned.toSet()
    val previousBlankFinalFields = assignedBlankFinalFields.toSet()
    val previousUnassignedBlankFinalFields = unassignedBlankFinalFields.toSet()
    bodyContext = context
    scope = S1Scope(null)
    definitelyAssigned.clear()
    assignedBlankFinalFields.clear()
    unassignedBlankFinalFields.clear()
    if (context.callable?.kind == JavaSemanticCallableKind.CONSTRUCTOR) {
      unassignedBlankFinalFields += blankFinalFields(context.owner)
    }
    try {
      block()
    } finally {
      bodyContext = previousContext
      scope = previousScope
      replaceAssigned(previousAssigned)
      assignedBlankFinalFields.clear()
      assignedBlankFinalFields += previousBlankFinalFields
      unassignedBlankFinalFields.clear()
      unassignedBlankFinalFields += previousUnassignedBlankFinalFields
    }
  }

  /** 创建并在退出后恢复嵌套 block/for 作用域。 */
  private inline fun withScope(block: () -> Unit) {
    val previous = scope
    scope = S1Scope(previous)
    try {
      block()
    } finally {
      scope = previous
    }
  }

  /** 替换当前 definite-assignment 集合。 */
  private fun replaceAssigned(symbols: Set<JavaSymbolId>) {
    definitelyAssigned.clear()
    definitelyAssigned += symbols
  }

  /** 同时替换 blank final 的 definite-assigned 与 definite-unassigned 状态。 */
  private fun replaceBlankFinalState(
    assigned: Set<JavaSymbolId>,
    unassigned: Set<JavaSymbolId>,
  ) {
    assignedBlankFinalFields.clear()
    assignedBlankFinalFields += assigned
    unassignedBlankFinalFields.clear()
    unassignedBlankFinalFields += unassigned
  }

  /** 当前表达式的 owner/callable 上下文。 */
  private fun context(): S1BodyContext =
    checkNotNull(bodyContext) { "Stage1 expression analysis requires a body context." }

  /** 合并 class 与 callable 的类型参数名称作用域。 */
  private fun currentTypeParameterScope(): Map<String, JavaSymbolId> =
    LinkedHashMap(context().owner.typeParameterNames).apply {
      putAll(context().callable?.typeParameterNames.orEmpty())
    }

  /** 根据当前声明表创建纯类型关系计算器。 */
  private fun relations() = JavaTypeRelations(
    typeDeclarations,
    typeParameterDeclarations,
    objectType.symbol,
  )

  /**
   * 生成只含名称和参数擦除的源码身份键。
   *
   * overload、override 和继承候选遮蔽不得使用 JVM descriptor，因为协变返回会让合法 override
   * 具有不同返回 descriptor。
   */
  private fun erasedSignatureKey(
    name: String,
    parameterTypes: List<JavaSemanticType>,
  ): String = "$name(${parameterTypes.joinToString(separator = "") { erasedTypeDescriptor(it) }})"

  /** 生成 lowering 可直接写入 IR 的 JVM method descriptor；构造器返回类型固定为 V。 */
  private fun erasedMethodDescriptor(
    kind: JavaSemanticCallableKind,
    parameterTypes: List<JavaSemanticType>,
    returnType: JavaSemanticType,
  ): String = buildString {
    append('(')
    parameterTypes.forEach { append(erasedTypeDescriptor(it)) }
    append(')')
    if (kind == JavaSemanticCallableKind.CONSTRUCTOR) append('V')
    else append(erasedTypeDescriptor(returnType))
  }

  /** 将擦除后的语义类型编码为稳定、与源码别名无关的 descriptor。 */
  private fun erasedTypeDescriptor(type: JavaSemanticType): String {
    val erased = relations().erasure(type)
    return when (erased) {
      is JavaSemanticType.Primitive -> when (erased.kind) {
        JavaAstPrimitiveType.BOOLEAN -> "Z"
        JavaAstPrimitiveType.BYTE -> "B"
        JavaAstPrimitiveType.SHORT -> "S"
        JavaAstPrimitiveType.INT -> "I"
        JavaAstPrimitiveType.LONG -> "J"
        JavaAstPrimitiveType.CHAR -> "C"
        JavaAstPrimitiveType.FLOAT -> "F"
        JavaAstPrimitiveType.DOUBLE -> "D"
      }
      is JavaSemanticType.Declared -> typeInfo(erased.symbol)?.qualifiedName
        ?.replace('.', '/')?.let { "L$it;" } ?: "<error>"
      is JavaSemanticType.Array -> "[${erasedTypeDescriptor(erased.componentType)}"
      JavaSemanticType.Void -> "V"
      else -> "<error>"
    }
  }

  /** Java binary numeric promotion 的结果。 */
  private fun numericPromotion(
    left: JavaSemanticType,
    right: JavaSemanticType,
  ): JavaSemanticType.Primitive? {
    if (!isNumeric(left) || !isNumeric(right)) return null
    val kinds = listOf(
      (left as JavaSemanticType.Primitive).kind,
      (right as JavaSemanticType.Primitive).kind,
    )
    val result = when {
      JavaAstPrimitiveType.DOUBLE in kinds -> JavaAstPrimitiveType.DOUBLE
      JavaAstPrimitiveType.FLOAT in kinds -> JavaAstPrimitiveType.FLOAT
      JavaAstPrimitiveType.LONG in kinds -> JavaAstPrimitiveType.LONG
      else -> JavaAstPrimitiveType.INT
    }
    return JavaSemanticType.Primitive(result)
  }

  /** unary numeric promotion 把 byte/short/char 提升到 int。 */
  private fun unaryNumericPromotion(type: JavaSemanticType): JavaSemanticType.Primitive? {
    if (!isNumeric(type)) return null
    val kind = (type as JavaSemanticType.Primitive).kind
    return JavaSemanticType.Primitive(
      if (kind == JavaAstPrimitiveType.BYTE || kind == JavaAstPrimitiveType.SHORT ||
        kind == JavaAstPrimitiveType.CHAR
      ) JavaAstPrimitiveType.INT else kind,
    )
  }

  /** 统一产生一元操作数诊断。 */
  private fun invalidUnary(span: JavaSourceSpan, message: String): JavaSemanticType {
    error(span, "java.semantic.invalid_unary_operand", message)
    return JavaSemanticType.Error
  }

  private fun isNumeric(type: JavaSemanticType): Boolean =
    type is JavaSemanticType.Primitive && type.kind != JavaAstPrimitiveType.BOOLEAN

  private fun isIntegral(type: JavaSemanticType): Boolean =
    type is JavaSemanticType.Primitive && type.kind in setOf(
      JavaAstPrimitiveType.BYTE,
      JavaAstPrimitiveType.SHORT,
      JavaAstPrimitiveType.CHAR,
      JavaAstPrimitiveType.INT,
      JavaAstPrimitiveType.LONG,
    )

  private fun isReference(type: JavaSemanticType): Boolean =
    type is JavaSemanticType.Declared || type is JavaSemanticType.Array ||
      type is JavaSemanticType.TypeVariable

  private fun isString(type: JavaSemanticType): Boolean =
    type is JavaSemanticType.Declared && type.symbol == stringType.symbol

  private fun objectSemanticType() = JavaSemanticType.Declared(objectType.symbol, emptyList())
  private fun intType() = JavaSemanticType.Primitive(JavaAstPrimitiveType.INT)
  private fun longType() = JavaSemanticType.Primitive(JavaAstPrimitiveType.LONG)
  private fun booleanType() = JavaSemanticType.Primitive(JavaAstPrimitiveType.BOOLEAN)

  private fun visibility(modifiers: Set<JavaAstModifier>): JavaVisibility = when {
    JavaAstModifier.PUBLIC in modifiers -> JavaVisibility.PUBLIC
    JavaAstModifier.PROTECTED in modifiers -> JavaVisibility.PROTECTED
    JavaAstModifier.PRIVATE in modifiers -> JavaVisibility.PRIVATE
    else -> JavaVisibility.PACKAGE_PRIVATE
  }

  /** visibility rank 仅用于 override 缩窄检查。 */
  private fun visibilityRank(visibility: JavaVisibility): Int = when (visibility) {
    JavaVisibility.PRIVATE -> 0
    JavaVisibility.PACKAGE_PRIVATE -> 1
    JavaVisibility.PROTECTED -> 2
    JavaVisibility.PUBLIC -> 3
  }

  private fun typeInfo(symbol: JavaSymbolId): S1TypeInfo? =
    typesByQualifiedName.values.firstOrNull { it.symbol == symbol }

  private fun sourceTypes(): List<S1TypeInfo> = typesByNode.values.toList()
  private fun allTypes(): List<S1TypeInfo> = typesByQualifiedName.values.toList()

  /** 成功模型必须填满 Stage1 声明与绑定 side table，且不能包含 ERROR。 */
  private fun result(): JavaCompilerPhaseResult<JavaSemanticModel> {
    if (diagnostics.none { it.severity == JavaDiagnosticSeverity.ERROR }) {
      // 这里属于分析器内部契约破坏；携带首个来源便于定位，而不是把恢复类型泄漏给 lowering。
      val incompleteSource = when {
        expressionTypes.values.any { it == JavaSemanticType.Error } ->
          "expression=${expressionTypes.entries.first { it.value == JavaSemanticType.Error }.key}"
        symbols.values.any { it.type == JavaSemanticType.Error } ->
          "symbol=${symbols.values.first { it.type == JavaSemanticType.Error }.name}"
        fieldDeclarations.values.any { it.type == JavaSemanticType.Error } ->
          "field=${fieldDeclarations.values.first { it.type == JavaSemanticType.Error }.symbol.value}"
        callableDeclarations.values.any {
          it.returnType == JavaSemanticType.Error || it.parameterTypes.any { type -> type == JavaSemanticType.Error }
        } -> "callable=${callableDeclarations.values.first {
          it.returnType == JavaSemanticType.Error || it.parameterTypes.any { type -> type == JavaSemanticType.Error }
        }.symbol.value}"
        else -> null
      }
      if (incompleteSource != null) {
        error(
          null,
          "java.semantic.incomplete_model",
          "语义分析产生了未诊断的恢复类型：$incompleteSource。",
        )
      }
    }
    if (diagnostics.any { it.severity == JavaDiagnosticSeverity.ERROR }) {
      return JavaCompilerPhaseResult.failure(diagnostics)
    }
    return JavaCompilerPhaseResult.success(
      JavaSemanticModel(
        ast,
        symbols.toMap(),
        declarations.toMap(),
        resolved.toMap(),
        expressionTypes.toMap(),
        conversions.toMap(),
        calls.toMap(),
        constants.toMap(),
        typeDeclarations.toMap(),
        typeParameterDeclarations.toMap(),
        fieldDeclarations.toMap(),
        callableDeclarations.toMap(),
        valueAccesses.toMap(),
        virtualSlots.toMap(),
        overriddenMethods.toMap(),
        constructorDelegations.toMap(),
      ),
      diagnostics,
    )
  }

  /** 添加稳定 ERROR 诊断。 */
  private fun error(
    span: JavaSourceSpan?,
    code: String,
    message: String,
    notes: List<JavaDiagnosticNote> = emptyList(),
  ) {
    diagnostics += JavaCompilerDiagnostic(
      code,
      message,
      JavaDiagnosticSeverity.ERROR,
      span,
      notes,
    )
  }
}

/** 分析期类型记录；可变字段只在严格分遍阶段写入。 */
private class S1TypeInfo(
  val unit: JavaAstCompilationUnit,
  val declaration: JavaAstTypeDeclaration?,
  val qualifiedName: String,
  val symbol: JavaSymbolId,
  val kind: JavaSemanticTypeDeclarationKind,
  val visibility: JavaVisibility,
  val isFinal: Boolean,
  var directSuperClass: JavaSemanticType.Declared? = null,
) {
  val typeParameters = mutableListOf<JavaSymbolId>()
  val typeParameterNames = linkedMapOf<String, JavaSymbolId>()
  val membersInSourceOrder = mutableListOf<JavaSymbolId>()
  val packageName: String? get() = qualifiedName.substringBeforeLast('.', "").ifEmpty { null }
  val simpleName: String get() = qualifiedName.substringAfterLast('.')
  val nodeId: JavaNodeId get() = declaration?.nodeId ?: unit.nodeId
  val span: JavaSourceSpan get() = declaration?.span ?: unit.span

  /** 当前 owner 的自类型保留其 class 类型变量，供继承成员代换。 */
  fun selfType(): JavaSemanticType.Declared = JavaSemanticType.Declared(
    symbol,
    typeParameters.map(JavaSemanticType::TypeVariable),
  )

  /** 将当前分析记录冻结为公共 Stage1 类型声明。 */
  fun semanticDeclaration() = JavaSemanticTypeDeclaration(
    symbol,
    qualifiedName,
    kind,
    visibility,
    isFinal,
    typeParameters.toList(),
    directSuperClass,
    membersInSourceOrder.toList(),
  )
}

/** 分析期字段签名。 */
private data class S1FieldInfo(
  val owner: S1TypeInfo,
  val declaration: JavaAstMemberDeclaration.Field,
  val declarator: JavaAstVariableDeclarator,
  val symbol: JavaSymbolId,
  val type: JavaSemanticType,
  val visibility: JavaVisibility,
  val isStatic: Boolean,
  val isFinal: Boolean,
  val order: Int,
) {
  val unit: JavaAstCompilationUnit get() = owner.unit
}

/** 字段经 receiver owner 参数代换后的视图。 */
private data class S1FieldView(
  val info: S1FieldInfo,
  val type: JavaSemanticType,
)

/** 分析期 callable 签名，显式和合成构造器共用。 */
private data class S1CallableInfo(
  val unit: JavaAstCompilationUnit,
  val owner: S1TypeInfo,
  val declaration: JavaAstMemberDeclaration?,
  val symbol: JavaSymbolId,
  val kind: JavaSemanticCallableKind,
  val name: String,
  val typeParameters: List<JavaSymbolId>,
  val typeParameterNames: Map<String, JavaSymbolId>,
  val parameterNodes: List<JavaAstParameter>,
  val parameterSymbols: List<JavaSymbolId>,
  val parameterTypes: List<JavaSemanticType>,
  val returnType: JavaSemanticType,
  val visibility: JavaVisibility,
  val isStatic: Boolean,
  val isFinal: Boolean,
  val isAbstract: Boolean,
  val erasedSignatureKey: String,
  val erasedDescriptor: String,
  val isSynthetic: Boolean = false,
) {
  val span: JavaSourceSpan get() = declaration?.span ?: owner.span

  /** 将分析签名冻结为公共 callable 声明。 */
  fun semanticDeclaration() = JavaSemanticCallableDeclaration(
    symbol,
    owner.symbol,
    kind,
    typeParameters,
    parameterSymbols,
    parameterTypes,
    returnType,
    visibility,
    isStatic,
    isFinal,
    isAbstract,
    erasedDescriptor,
  )
}

/** 从某个 receiver 泛型视角观察到的 callable 候选。 */
private data class S1CallableCandidate(
  val info: S1CallableInfo,
  val ownerSubstitutions: Map<JavaSymbolId, JavaSemanticType>,
)

/** 完成 owner 与 callable 类型代换后可参与 overload 的签名。 */
private data class S1InstantiatedCallable(
  val info: S1CallableInfo,
  val parameterTypes: List<JavaSemanticType>,
  val returnType: JavaSemanticType,
  val substitutions: Map<JavaSymbolId, JavaSemanticType>,
) {
  /** 构造 lowering 可直接消费的最终 callable binding。 */
  fun binding(
    dispatch: JavaDispatchKind,
    receiverKind: JavaReceiverKind,
    virtualSlot: JavaVirtualSlotId?,
  ) = JavaCallableBinding(
    info.symbol,
    dispatch,
    parameterTypes,
    returnType,
    substitutions,
    receiverKind,
    info.erasedDescriptor,
    virtualSlot,
  )
}

/** 当前表达式 body 的 owner、callable 与 static 状态。 */
private data class S1BodyContext(
  val unit: JavaAstCompilationUnit,
  val owner: S1TypeInfo,
  val callable: S1CallableInfo?,
  val isStatic: Boolean,
)

/** receiver 的运行时形态；[isImplicit] 区分无 receiver 调用。 */
private data class S1Receiver(
  val type: JavaSemanticType,
  val kind: JavaReceiverKind,
  val isImplicit: Boolean = false,
)

/** 方法体局部值命名空间；字段与类型命名空间保持独立。 */
private class S1Scope(val parent: S1Scope?) {
  val values = linkedMapOf<String, JavaSymbolId>()
  fun resolve(name: String): JavaSymbolId? = values[name] ?: parent?.resolve(name)
}

/** 解析 Stage1 常见整数 token；极端二补数无符号写法保守拒绝。 */
private fun parseStage1Integer(token: String): Long? {
  if (token.isEmpty() || token.first() == '_' || token.last() == '_' || "__" in token) return null
  val noSuffix = if (token.endsWith('l', ignoreCase = true)) token.dropLast(1) else token
  val compact = noSuffix.replace("_", "")
  val radix: Int
  val digits: String
  when {
    compact.startsWith("0x", ignoreCase = true) -> {
      radix = 16
      digits = compact.drop(2)
    }
    compact.startsWith("0b", ignoreCase = true) -> {
      radix = 2
      digits = compact.drop(2)
    }
    compact.length > 1 && compact.startsWith('0') -> {
      radix = 8
      digits = compact.drop(1)
    }
    else -> {
      radix = 10
      digits = compact
    }
  }
  if (digits.isEmpty()) return null
  return digits.toLongOrNull(radix)
}

/** 解码 char token；复用 String 转义规则但要求最终只有一个 code unit。 */
private fun decodeStage1Character(token: String): Char? {
  if (token.length < 3 || token.first() != '\'' || token.last() != '\'') return null
  val decoded = decodeStage1Escapes(token.substring(1, token.lastIndex)) ?: return null
  return decoded.singleOrNull()
}

/** 解码带双引号的 Java String token。 */
private fun decodeStage1String(token: String): String? {
  if (token.length < 2 || token.first() != '"' || token.last() != '"') return null
  return decodeStage1Escapes(token.substring(1, token.lastIndex))
}

/** 解码 Java 常用、Unicode 与八进制转义。 */
private fun decodeStage1Escapes(content: String): String? {
  val result = StringBuilder()
  var index = 0
  while (index < content.length) {
    val current = content[index++]
    if (current != '\\') {
      result.append(current)
      continue
    }
    if (index >= content.length) return null
    when (val escaped = content[index++]) {
      'b' -> result.append('\b')
      't' -> result.append('\t')
      'n' -> result.append('\n')
      'f' -> result.append('\u000C')
      'r' -> result.append('\r')
      '"' -> result.append('"')
      '\'' -> result.append('\'')
      '\\' -> result.append('\\')
      'u' -> {
        while (index < content.length && content[index] == 'u') index++
        if (index + 4 > content.length) return null
        val value = content.substring(index, index + 4).toIntOrNull(16) ?: return null
        result.append(value.toChar())
        index += 4
      }
      in '0'..'7' -> {
        var value = escaped - '0'
        var remaining = if (escaped <= '3') 2 else 1
        while (remaining > 0 && index < content.length && content[index] in '0'..'7') {
          value = value * 8 + (content[index++] - '0')
          remaining--
        }
        result.append(value.toChar())
      }
      else -> return null
    }
  }
  return result.toString()
}

/** 默认 package 保持简单名，否则用点号连接。 */
private fun qualifyStage1(packageName: String?, simpleName: String): String =
  if (packageName.isNullOrEmpty()) simpleName else "$packageName.$simpleName"

/** 取得不含 `.java` 后缀的规范文件名。 */
private fun javaStage1FileName(path: String): String? {
  val fileName = path.substringAfterLast('/').substringAfterLast('\\')
  return fileName.takeIf { it.endsWith(".java") }?.removeSuffix(".java")
}
