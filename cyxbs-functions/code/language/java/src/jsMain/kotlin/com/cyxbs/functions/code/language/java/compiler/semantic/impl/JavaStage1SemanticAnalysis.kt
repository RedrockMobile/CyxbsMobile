package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.ast.*
import com.cyxbs.functions.code.language.java.compiler.builtin.*
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
  private val updateWriteConversions = linkedMapOf<JavaNodeId, JavaSemanticConversion>()
  private val calls = linkedMapOf<JavaNodeId, JavaCallableBinding>()
  private val constants = linkedMapOf<JavaNodeId, JavaConstantValue>()
  private val valueAccesses = linkedMapOf<JavaNodeId, JavaValueAccessBinding>()
  private val stringConcatenations =
    linkedMapOf<JavaNodeId, JavaStringConcatenationBinding>()
  private val arrayLengthExpressions = linkedSetOf<JavaNodeId>()
  private val builtinMembers = linkedMapOf<JavaSymbolId, JavaBuiltinMemberDescriptor>()
  private val builtinTypeRoles = linkedMapOf<JavaSymbolId, JavaBuiltinTypeRole>()
  private val wrapperPrimitiveTypes = linkedMapOf<JavaSymbolId, JavaAstPrimitiveType>()
  private val expectedExpressionTypes = linkedMapOf<JavaNodeId, JavaSemanticType>()
  private val typeDeclarations = linkedMapOf<JavaSymbolId, JavaSemanticTypeDeclaration>()
  private val typeParameterDeclarations =
    linkedMapOf<JavaSymbolId, JavaSemanticTypeParameterDeclaration>()
  private val fieldDeclarations = linkedMapOf<JavaSymbolId, JavaSemanticFieldDeclaration>()
  private val callableDeclarations =
    linkedMapOf<JavaSymbolId, JavaSemanticCallableDeclaration>()
  private val virtualSlots = linkedMapOf<JavaSymbolId, JavaVirtualSlotId>()
  private val overriddenMethods = linkedMapOf<JavaSymbolId, List<JavaSymbolId>>()
  private val interfaceDefaultMethods =
    linkedMapOf<JavaSymbolId, Map<JavaVirtualSlotId, JavaSymbolId>>()
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
  private val loopFlows = mutableListOf<S1LoopFlow>()

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
    declareAdditionalBuiltinTypes()
    validateExplicitImports()
    declareTypeParameters()
    resolveHierarchy()
    validateHierarchy()
    declareMembers()
    declareBuiltinMembers()
    validateOverridesAndAssignSlots()
    resolveInterfaceDefaultMethods()
    analyzeFieldInitializers()
    analyzeCallableBodies()
    validateConstructorCycles()
    return result()
  }

  /**
   * 两遍登记 allowlist 类型及继承边。
   *
   * 第一遍先冻结全部类型 symbol，第二遍才能按限定名建立父类关系，结果不依赖 catalog 顺序。
   */
  private fun declareBuiltinTypes() {
    val descriptors = JavaBuiltinLibrary.types.filter { descriptor ->
      descriptor.qualifiedName == "java.lang.Object" ||
        descriptor.qualifiedName == "java.lang.String"
    }
    declareBuiltinTypeDescriptors(descriptors)
    objectType = checkNotNull(typesByQualifiedName["java.lang.Object"])
    stringType = checkNotNull(typesByQualifiedName["java.lang.String"])
  }

  /**
   * 在用户类型 symbol 已冻结后登记额外 builtin，避免改变既有 typed IR 编号和生成代码快照。
   *
   * 此步骤仍早于类型参数、继承和成员签名解析，因此 System/Math/PrintStream 对源码完全可见。
   */
  private fun declareAdditionalBuiltinTypes() {
    declareBuiltinTypeDescriptors(JavaBuiltinLibrary.types.filter { descriptor ->
      descriptor.qualifiedName != "java.lang.Object" &&
        descriptor.qualifiedName != "java.lang.String"
    })
  }

  /** 为一组已经过顺序选择的 catalog 类型建立 symbol 与父类边。 */
  private fun declareBuiltinTypeDescriptors(descriptors: List<JavaBuiltinTypeDescriptor>) {
    descriptors.forEach { descriptor ->
      check(descriptor.qualifiedName !in typesByQualifiedName) {
        "Duplicate Java builtin type ${descriptor.qualifiedName}."
      }
      val typeSymbol = symbol(
        JavaSymbolKind.TYPE,
        descriptor.qualifiedName.substringAfterLast('.'),
        null,
        hostNode.nodeId,
        hostNode.span,
        null,
        registerDeclaration = false,
      )
      typesByQualifiedName[descriptor.qualifiedName] = S1TypeInfo(
        unit = hostNode,
        declaration = null,
        qualifiedName = descriptor.qualifiedName,
        symbol = typeSymbol,
        kind = JavaSemanticTypeDeclarationKind.BUILTIN,
        visibility = JavaVisibility.PUBLIC,
        isFinal = descriptor.isFinal,
      )
      builtinTypeRoles[typeSymbol] = descriptor.role
      descriptor.role.boxedPrimitive?.let { wrapperPrimitiveTypes[typeSymbol] = it }
    }
    descriptors.forEach { descriptor ->
      val type = checkNotNull(typesByQualifiedName[descriptor.qualifiedName])
      descriptor.typeParameters.forEach { name ->
        check(name !in type.typeParameterNames) { "Duplicate Java builtin type parameter $name." }
        val parameter = symbol(
          JavaSymbolKind.TYPE_PARAMETER,
          name,
          type.symbol,
          hostNode.nodeId,
          hostNode.span,
          null,
          registerDeclaration = false,
        )
        type.typeParameters += parameter
        type.typeParameterNames[name] = parameter
        typeParameterDeclarations[parameter] = JavaSemanticTypeParameterDeclaration(
          parameter,
          type.symbol,
          listOf(JavaSemanticType.Declared(objectType.symbol, emptyList())),
        )
      }
      type.directSuperClass = descriptor.directSuperQualifiedName?.let { parentName ->
        val parent = checkNotNull(typesByQualifiedName[parentName]) {
          "Java builtin parent $parentName is not declared."
        }
        JavaSemanticType.Declared(
          parent.symbol,
          descriptor.directSuperArguments.map { argument -> argument.toSemanticType(type) },
        )
      }
      typeDeclarations[type.symbol] = type.semanticDeclaration()
    }
  }

  /**
   * 把 catalog 成员转成普通字段/callable 候选，同时保存 symbol 到 builtin 描述的确定映射。
   *
   * 这些记录没有 AST 声明节点，不进入源码声明顺序，也不会被字段初始化或方法体分析消费。
   */
  private fun declareBuiltinMembers() {
    JavaBuiltinLibrary.members.forEachIndexed { order, descriptor ->
      val owner = checkNotNull(typesByQualifiedName[descriptor.ownerQualifiedName]) {
        "Java builtin owner ${descriptor.ownerQualifiedName} is not declared."
      }
      when (descriptor) {
        is JavaBuiltinMemberDescriptor.Field ->
          declareBuiltinField(owner, descriptor, order)
        is JavaBuiltinMemberDescriptor.Callable ->
          declareBuiltinCallable(owner, descriptor)
      }
    }
  }

  /** 登记一个没有源码 initializer 的内建字段。 */
  private fun declareBuiltinField(
    owner: S1TypeInfo,
    descriptor: JavaBuiltinMemberDescriptor.Field,
    order: Int,
  ) {
    val type = descriptor.type.toSemanticType(owner)
    val id = symbol(
      JavaSymbolKind.FIELD,
      descriptor.name,
      owner.symbol,
      hostNode.nodeId,
      hostNode.span,
      type,
      registerDeclaration = false,
    )
    val field = S1FieldInfo(
      owner = owner,
      declaration = null,
      declarator = null,
      symbol = id,
      name = descriptor.name,
      type = type,
      visibility = JavaVisibility.PUBLIC,
      isStatic = descriptor.isStatic,
      isFinal = descriptor.isFinal,
      order = order,
      span = hostNode.span,
      isSynthetic = true,
    )
    check(fieldsByOwnerAndName.put(owner.symbol to descriptor.name, field) == null) {
      "Duplicate Java builtin field ${descriptor.ownerQualifiedName}.${descriptor.name}."
    }
    fields += field
    fieldDeclarations[id] = JavaSemanticFieldDeclaration(
      symbol = id,
      owner = owner.symbol,
      type = type,
      visibility = JavaVisibility.PUBLIC,
      isStatic = descriptor.isStatic,
      isFinal = descriptor.isFinal,
      declarationOrder = order,
    )
    if (descriptor.isFinal) finalSymbols += id
    builtinMembers[id] = descriptor
  }

  /** 登记一个无需方法体、但完整参与重载选择的内建 callable。 */
  private fun declareBuiltinCallable(
    owner: S1TypeInfo,
    descriptor: JavaBuiltinMemberDescriptor.Callable,
  ) {
    val parameterTypes = descriptor.parameterTypes.map { it.toSemanticType(owner) }
    val returnType = descriptor.returnType.toSemanticType(owner)
    val kind = if (descriptor.isConstructor) {
      JavaSemanticCallableKind.CONSTRUCTOR
    } else {
      JavaSemanticCallableKind.METHOD
    }
    val id = symbol(
      if (descriptor.isConstructor) JavaSymbolKind.CONSTRUCTOR else JavaSymbolKind.METHOD,
      descriptor.name,
      owner.symbol,
      hostNode.nodeId,
      hostNode.span,
      returnType,
      registerDeclaration = false,
    )
    val parameterSymbols = parameterTypes.mapIndexed { index, type ->
      symbol(
        JavaSymbolKind.PARAMETER,
        "argument$index",
        id,
        hostNode.nodeId,
        hostNode.span,
        type,
        registerDeclaration = false,
      )
    }
    val info = S1CallableInfo(
      unit = hostNode,
      owner = owner,
      declaration = null,
      symbol = id,
      kind = kind,
      name = descriptor.name,
      typeParameters = emptyList(),
      typeParameterNames = emptyMap(),
      parameterNodes = emptyList(),
      parameterSymbols = parameterSymbols,
      parameterTypes = parameterTypes,
      returnType = returnType,
      visibility = JavaVisibility.PUBLIC,
      isStatic = if (descriptor.isConstructor) false else descriptor.isStatic,
      isFinal = if (descriptor.isConstructor) false else descriptor.isFinal,
      isAbstract = false,
      erasedSignatureKey = erasedSignatureKey(descriptor.name, parameterTypes),
      erasedDescriptor = erasedMethodDescriptor(
        kind,
        parameterTypes,
        returnType,
      ),
      isSynthetic = true,
      isBuiltin = true,
      isBuiltinVirtualRoot = descriptor.isVirtualRoot,
    )
    val siblings = if (descriptor.isConstructor) {
      constructorsByOwner.getOrPut(owner.symbol) { mutableListOf() }
    } else {
      methodsByOwnerAndName.getOrPut(owner.symbol to descriptor.name) { mutableListOf() }
    }
    check(siblings.none { it.erasedSignatureKey == info.erasedSignatureKey }) {
      "Duplicate Java builtin callable ${descriptor.ownerQualifiedName}.${info.erasedSignatureKey}."
    }
    siblings += info
    callables += info
    callableDeclarations[id] = info.semanticDeclaration()
    builtinMembers[id] = descriptor
  }

  /** 将 catalog 类型引用解析为本次编译内的稳定 semantic type。 */
  private fun JavaBuiltinTypeReference.toSemanticType(owner: S1TypeInfo): JavaSemanticType = when (this) {
    is JavaBuiltinTypeReference.Primitive -> JavaSemanticType.Primitive(kind)
    is JavaBuiltinTypeReference.Array -> JavaSemanticType.Array(componentType.toSemanticType(owner))
    is JavaBuiltinTypeReference.Declared -> JavaSemanticType.Declared(
      checkNotNull(typesByQualifiedName[qualifiedName]) {
        "Java builtin member references unknown type $qualifiedName."
      }.symbol,
      arguments.map { argument -> argument.toSemanticType(owner) },
    )
    is JavaBuiltinTypeReference.TypeParameter -> JavaSemanticType.TypeVariable(
      checkNotNull(owner.typeParameterNames[name]) {
        "Java builtin member references unknown owner type parameter $name."
      },
    )
    JavaBuiltinTypeReference.Void -> JavaSemanticType.Void
  }

  /** 第一遍只登记 package-qualified 类型，防止继承与签名解析受源码顺序影响。 */
  private fun declareSourceTypeSkeletons() {
    ast.units.forEach { unit ->
      unit.imports.filter { it.isStatic }.forEach {
        error(it.span, "java.semantic.static_import_unsupported", "Stage1 暂不支持 static import。")
      }
      unit.types.forEach { declaration ->
        if (declaration.kind == JavaAstTypeDeclarationKind.ENUM) {
          error(
            declaration.span,
            "java.semantic.type_kind_unsupported",
            "enum 将在阶段 2A 后续批次开放。",
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
            "顶层 class/interface 不能声明为 private、protected 或 static。",
          )
        }
        if (declaration.kind == JavaAstTypeDeclarationKind.INTERFACE &&
          JavaAstModifier.FINAL in declaration.modifiers
        ) {
          error(declaration.span, "java.semantic.final_interface", "interface 不能声明为 final。")
        }
        if (declaration.kind == JavaAstTypeDeclarationKind.CLASS &&
          JavaAstModifier.ABSTRACT in declaration.modifiers &&
          JavaAstModifier.FINAL in declaration.modifiers
        ) {
          error(declaration.span, "java.semantic.abstract_final_class", "class 不能同时声明为 abstract 和 final。")
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
        val conflictsWithBuiltin = JavaBuiltinLibrary.types.any { descriptor ->
          descriptor.qualifiedName == qualifiedName
        }
        if (previous != null || conflictsWithBuiltin) {
          error(
            declaration.span,
            "java.semantic.duplicate_type",
            "限定类型名 $qualifiedName 已在工作区声明。",
            previous?.let { existing ->
              listOf(JavaDiagnosticNote("首次声明位于此处。", existing.span))
            }.orEmpty(),
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
          when (declaration.kind) {
            JavaAstTypeDeclarationKind.CLASS -> JavaSemanticTypeDeclarationKind.CLASS
            JavaAstTypeDeclarationKind.INTERFACE -> JavaSemanticTypeDeclarationKind.INTERFACE
            JavaAstTypeDeclarationKind.ENUM -> JavaSemanticTypeDeclarationKind.ENUM
          },
          visibility(declaration.modifiers),
          JavaAstModifier.FINAL in declaration.modifiers,
          declaration.kind == JavaAstTypeDeclarationKind.INTERFACE ||
            JavaAstModifier.ABSTRACT in declaration.modifiers,
        )
        typesByQualifiedName[qualifiedName] = info
        typesByNode[declaration.nodeId] = info
        typeDeclarations[id] = info.semanticDeclaration()
      }
    }
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
          val simpleName = declaration.qualifiedName.substringAfterLast('.')
          val currentUnitType = unit.types.firstOrNull { it.name == simpleName }
            ?.let { typesByNode[it.nodeId] }
          if (currentUnitType != null && currentUnitType.symbol != type.symbol) {
            error(
              declaration.span,
              "java.semantic.import_conflicts_with_top_level_type",
              "显式 import 的类型 ${declaration.qualifiedName} 与当前 compilation unit 的顶层类型 $simpleName 冲突。",
              listOf(JavaDiagnosticNote("冲突的顶层类型声明。", currentUnitType.span)),
            )
          }
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

  /** 解析 class 父类、implements 与 interface extends；未显式父类的引用类型均连接 Object 根。 */
  private fun resolveHierarchy() {
    sourceTypes().forEach { type ->
      val declaration = checkNotNull(type.declaration)
      if (type.kind == JavaSemanticTypeDeclarationKind.INTERFACE && declaration.superClass != null) {
        error(declaration.superClass.span, "java.semantic.interface_superclass", "interface 不能声明 class 父类。")
      }
      val superType = declaration.superClass?.let {
        resolveType(it, type.unit, type.typeParameterNames, allowVoid = false)
      } ?: objectSemanticType()
      type.directSuperClass = superType as? JavaSemanticType.Declared
      val selectedParent = type.directSuperClass?.symbol?.let(::typeInfo)
      if (selectedParent != null && JavaBuiltinLibrary.types.any { descriptor ->
          descriptor.qualifiedName == selectedParent.qualifiedName && descriptor.isInterfaceFacade
        }
      ) {
        error(
          declaration.superClass?.span ?: declaration.span,
          "java.semantic.builtin_facade_inheritance_unsupported",
          "精选集合接口仅供变量声明与内建实现使用，用户 class 暂不能继承或实现它。",
        )
      }
      if (type.directSuperClass == null && superType != JavaSemanticType.Error) {
        error(declaration.span, "java.semantic.invalid_superclass", "class 的直接父类必须是声明类型。")
      }
      if (selectedParent?.kind == JavaSemanticTypeDeclarationKind.INTERFACE) {
        error(declaration.superClass?.span ?: declaration.span, "java.semantic.interface_as_superclass", "class 不能通过 extends 继承 interface。")
      }
      type.directInterfaces += declaration.interfaces.mapNotNull { reference ->
        val resolved = resolveType(reference, type.unit, type.typeParameterNames, allowVoid = false)
        val declared = resolved as? JavaSemanticType.Declared
        if (declared == null) {
          if (resolved != JavaSemanticType.Error) {
            error(reference.span, "java.semantic.invalid_superinterface", "接口继承边必须指向声明类型。")
          }
          return@mapNotNull null
        }
        val target = typeInfo(declared.symbol)
        if (target?.kind != JavaSemanticTypeDeclarationKind.INTERFACE) {
          error(reference.span, "java.semantic.not_an_interface", "implements/extends interface 只能指向 interface。")
        }
        if (target != null && JavaBuiltinLibrary.types.any { descriptor ->
            descriptor.qualifiedName == target.qualifiedName && descriptor.isInterfaceFacade
          }
        ) {
          error(
            reference.span,
            "java.semantic.builtin_facade_inheritance_unsupported",
            "精选集合接口当前只供内建实现使用，用户类型暂不能 implements 它。",
          )
        }
        declared
      }
      if (type.directInterfaces.map { it.symbol }.distinct().size != type.directInterfaces.size) {
        error(declaration.span, "java.semantic.duplicate_superinterface", "类型不能重复声明同一个直接接口。")
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
      type.directInterfaces.forEach { interfaceType ->
        typeInfo(interfaceType.symbol)?.let { inherited ->
          ensureTypeAccessible(inherited, type.unit, type.span)
          visit(inherited)
        }
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
      if (owner.kind == JavaSemanticTypeDeclarationKind.CLASS &&
        constructorsByOwner[owner.symbol].isNullOrEmpty()
      ) synthesizeDefaultConstructor(owner)
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
    val isInterfaceField = owner.kind == JavaSemanticTypeDeclarationKind.INTERFACE
    if (isInterfaceField && declaration.modifiers.any { modifier ->
        modifier == JavaAstModifier.PRIVATE || modifier == JavaAstModifier.PROTECTED
      }
    ) {
      error(declaration.span, "java.semantic.invalid_interface_field_modifier", "Java 8 interface 字段只能是 public static final。")
    }
    val key = owner.symbol to declarator.name
    val previous = fieldsByOwnerAndName[key]
    if (previous != null) {
      error(
        declarator.span,
        "java.semantic.duplicate_field",
        "类型 ${owner.qualifiedName} 重复声明字段 ${declarator.name}。",
        listOf(JavaDiagnosticNote("首次声明位于此处。", previous.span)),
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
      declarator.name,
      type,
      if (isInterfaceField) JavaVisibility.PUBLIC else visibility(declaration.modifiers),
      isInterfaceField || JavaAstModifier.STATIC in declaration.modifiers,
      isInterfaceField || JavaAstModifier.FINAL in declaration.modifiers,
      order,
      declarator.span,
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
    if (field.isFinal && field.isStatic && field.declarator?.initializer == null) {
      // 当前 AST 没有 static initializer block，声明初始化式是 static blank final 的唯一写入路径。
      error(
        field.span,
        "java.semantic.blank_final_field_not_initialized",
        "static final 字段 ${field.name} 必须提供声明初始化式。",
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
    val isInterfaceMethod = methodDeclaration != null &&
      owner.kind == JavaSemanticTypeDeclarationKind.INTERFACE
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
      visibility = if (isInterfaceMethod) JavaVisibility.PUBLIC else visibility(modifiers),
      isStatic = JavaAstModifier.STATIC in modifiers,
      isFinal = JavaAstModifier.FINAL in modifiers,
      isAbstract = JavaAstModifier.ABSTRACT in modifiers ||
        isInterfaceMethod && methodDeclaration.body == null,
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
      if (info.owner.kind == JavaSemanticTypeDeclarationKind.INTERFACE) {
        error(declaration.span, "java.semantic.interface_constructor", "interface 不能声明构造器。")
      }
      if (JavaAstModifier.STATIC in declaration.modifiers ||
        JavaAstModifier.FINAL in declaration.modifiers ||
        JavaAstModifier.ABSTRACT in declaration.modifiers
      ) {
        error(declaration.span, "java.semantic.invalid_constructor_modifier", "构造器不能是 static、final 或 abstract。")
      }
      return
    }
    val method = info.declaration as JavaAstMemberDeclaration.Method
    val isInterface = info.owner.kind == JavaSemanticTypeDeclarationKind.INTERFACE
    val isDefault = JavaAstModifier.DEFAULT in method.modifiers
    if (!isInterface && info.isAbstract != (method.body == null)) {
      error(
        method.span,
        "java.semantic.invalid_method_body",
        "abstract 方法不能有方法体，非 abstract 方法必须提供方法体。",
      )
    }
    if (info.isAbstract && (info.isStatic || info.isFinal || info.owner.isFinal)) {
      error(method.span, "java.semantic.invalid_abstract_method", "abstract 方法不能是 static/final，也不能位于 final class。")
    }
    if (isInterface) {
      if (method.modifiers.any { modifier ->
          modifier == JavaAstModifier.PRIVATE || modifier == JavaAstModifier.PROTECTED ||
            modifier == JavaAstModifier.FINAL
        }
      ) {
        error(method.span, "java.semantic.invalid_interface_method_modifier", "Java 8 interface 方法不能是 private、protected 或 final。")
      }
      if (isDefault && (info.isStatic || info.isAbstract || method.body == null)) {
        error(method.span, "java.semantic.invalid_default_method", "default 方法必须是带方法体的 interface 实例方法。")
      }
      if (method.body != null && !isDefault && !info.isStatic) {
        error(method.span, "java.semantic.interface_method_body_requires_default", "interface 实例方法带方法体时必须声明 default。")
      }
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

  /** 仅为 catalog 明确允许构造的内建类型合成零参数构造器。 */
  private fun synthesizeBuiltinConstructors() {
    JavaBuiltinLibrary.types.filter { it.hasDefaultConstructor }.forEach { descriptor ->
      val type = checkNotNull(typesByQualifiedName[descriptor.qualifiedName])
      if (constructorsByOwner[type.symbol].isNullOrEmpty()) synthesizeDefaultConstructor(type)
    }
  }

  /** 父类先于子类分配虚槽；合法 override 复用最近父方法的稳定槽。 */
  private fun validateOverridesAndAssignSlots() {
    val visited = mutableSetOf<JavaSymbolId>()
    val slotsByErasedSignature = linkedMapOf<String, JavaVirtualSlotId>()
    fun process(type: S1TypeInfo) {
      if (!visited.add(type.symbol)) return
      type.directSuperClass?.symbol?.let(::typeInfo)?.let(::process)
      type.directInterfaces.forEach { inherited -> inherited.symbol.let(::typeInfo)?.let(::process) }
      val methods = callables.filter {
        it.owner.symbol == type.symbol && it.kind == JavaSemanticCallableKind.METHOD
      }
      methods.forEach { method ->
        // 普通 builtin 由显式 operation 执行；只有 Object 三个虚方法根需要参与 override 槽位。
        if (method.isBuiltin && !method.isBuiltinVirtualRoot) return@forEach
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
        // 已确认 override 时必须继承父槽，不能再按当前声明的擦除签名分配：泛型父方法的
        // Object 擦除与子类具体参数可能不同，但 Java 动态分派仍属于同一个方法族。
        val inheritedSlots = overridden.mapNotNull { virtualSlots[it.symbol] }.distinct()
        if (inheritedSlots.size > 1) {
          error(
            method.span,
            "java.semantic.incompatible_override_slots",
            "方法 ${method.name} 覆盖的父声明属于不兼容的虚槽。",
          )
        }
        // 全新虚方法继续使用“擦除签名 -> 全工作区稳定槽”，让一个实现可以同时满足
        // 多个无继承关系但签名相同的接口声明。
        virtualSlots[method.symbol] = inheritedSlots.firstOrNull()
          ?: slotsByErasedSignature.getOrPut(method.erasedSignatureKey) {
            JavaVirtualSlotId(nextVirtualSlot++)
          }
        if (overridden.isNotEmpty()) overriddenMethods[method.symbol] = overridden.map { it.symbol }
      }
    }
    allTypes().forEach(::process)
  }

  /**
   * 为 class 选择最终继承的 interface default method，并检查未实现抽象方法与菱形冲突。
   *
   * class 层级中的声明始终优先于接口默认实现；接口侧只保留最具体声明，因此子接口重新声明
   * abstract 方法可以压住父接口 default，两个互不相关的 default 则必须由 class 显式解决。
   */
  private fun resolveInterfaceDefaultMethods() {
    sourceTypes().filter { it.kind == JavaSemanticTypeDeclarationKind.CLASS }.forEach { type ->
      val classMethods = classHierarchyMethods(type)
      val interfaceMethods = interfaceHierarchyMethods(type)
      val defaults = linkedMapOf<JavaVirtualSlotId, JavaSymbolId>()
      val signatures = interfaceMethods.map(S1CallableInfo::erasedSignatureKey).distinct()
      signatures.forEach { signature ->
        val classDeclaration = classMethods.firstOrNull { it.erasedSignatureKey == signature }
        if (classDeclaration != null) {
          if (classDeclaration.isAbstract && !type.isAbstract) {
            error(type.span, "java.semantic.abstract_method_not_implemented", "非 abstract class ${type.simpleName} 未实现方法 ${classDeclaration.name}。")
          }
          return@forEach
        }
        val declarations = interfaceMethods.filter { it.erasedSignatureKey == signature }
        val mostSpecific = declarations.filter { candidate ->
          declarations.none { other ->
            other !== candidate && isInterfaceSubtype(other.owner, candidate.owner)
          }
        }
        val concreteDefaults = mostSpecific.filter { !it.isAbstract && !it.isStatic }
        when {
          concreteDefaults.size > 1 -> error(
            type.span,
            "java.semantic.conflicting_interface_defaults",
            "类型 ${type.simpleName} 继承了多个冲突的 default 方法 ${concreteDefaults.first().name}。",
            concreteDefaults.map { JavaDiagnosticNote("冲突的 default 方法。", it.span) },
          )
          concreteDefaults.size == 1 -> {
            val method = concreteDefaults.single()
            val slot = virtualSlots[method.symbol]
            if (slot != null) defaults[slot] = method.symbol
          }
          mostSpecific.any { it.isAbstract } && !type.isAbstract -> error(
            type.span,
            "java.semantic.abstract_method_not_implemented",
            "非 abstract class ${type.simpleName} 未实现接口方法 ${mostSpecific.first().name}。",
          )
        }
      }
      if (defaults.isNotEmpty()) interfaceDefaultMethods[type.symbol] = defaults
    }
  }

  /** class 自身到父类的声明顺序；最近声明用于执行 Java 的 class-wins 规则。 */
  private fun classHierarchyMethods(type: S1TypeInfo): List<S1CallableInfo> {
    val result = mutableListOf<S1CallableInfo>()
    var current: S1TypeInfo? = type
    val visited = mutableSetOf<JavaSymbolId>()
    while (current != null && visited.add(current.symbol)) {
      result += callables.filter { callable ->
        callable.owner.symbol == current.symbol && callable.kind == JavaSemanticCallableKind.METHOD &&
          !callable.isStatic
      }
      current = current.directSuperClass?.symbol?.let(::typeInfo)
    }
    return result
  }

  /** 收集 class 及其父类直接实现的全部接口方法，重复接口边只访问一次。 */
  private fun interfaceHierarchyMethods(type: S1TypeInfo): List<S1CallableInfo> {
    val result = mutableListOf<S1CallableInfo>()
    val pending = ArrayDeque<S1TypeInfo>()
    val classVisited = mutableSetOf<JavaSymbolId>()
    var currentClass: S1TypeInfo? = type
    while (currentClass != null && classVisited.add(currentClass.symbol)) {
      currentClass.directInterfaces.mapNotNullTo(pending) { it.symbol.let(::typeInfo) }
      currentClass = currentClass.directSuperClass?.symbol?.let(::typeInfo)
    }
    val visited = mutableSetOf<JavaSymbolId>()
    while (pending.isNotEmpty()) {
      val current = pending.removeFirst()
      if (!visited.add(current.symbol)) continue
      result += callables.filter { callable ->
        callable.owner.symbol == current.symbol && callable.kind == JavaSemanticCallableKind.METHOD &&
          !callable.isStatic
      }
      current.directInterfaces.mapNotNullTo(pending) { it.symbol.let(::typeInfo) }
    }
    return result
  }

  /** 判断接口声明之间的继承方向，用于 default method 的“最具体”选择。 */
  private fun isInterfaceSubtype(child: S1TypeInfo, parent: S1TypeInfo): Boolean =
    child.symbol != parent.symbol && relations().asSupertype(child.selfType(), parent.symbol) != null

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
      val initializer = field.declarator?.initializer ?: return@forEach
      withBodyContext(S1BodyContext(field.unit, field.owner, null, field.isStatic)) {
        val actual = analyzeExpressionExpected(initializer, field.type)
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
        val flow = S1LoopFlow()
        loopFlows += flow
        loopDepth++
        try {
          analyzeStatement(statement.body)
        } finally {
          loopDepth--
          loopFlows.removeAt(loopFlows.lastIndex)
        }
        val blankBodyUnassigned = unassignedBlankFinalFields.toSet()
        val exits = flow.breakExits.toMutableList()
        if (!isConstantTrue(statement.condition)) {
          exits += S1FlowState(afterCondition, blankEntryAssigned, blankEntryUnassigned)
        }
        replaceFlowState(exits.intersectionOrNull() ?: S1FlowState(
          afterCondition, blankEntryAssigned, blankEntryUnassigned.intersect(blankBodyUnassigned),
        ))
      }
      is JavaAstStatement.DoWhile -> {
        val flow = S1LoopFlow()
        loopFlows += flow
        loopDepth++
        try {
          analyzeStatement(statement.body)
        } finally {
          loopDepth--
          loopFlows.removeAt(loopFlows.lastIndex)
        }
        requireType(statement.condition, booleanType(), "do-while 条件必须是 boolean。")
        val exits = flow.breakExits.toMutableList()
        if (!isConstantTrue(statement.condition)) exits += currentFlowState()
        replaceFlowState(exits.intersectionOrNull() ?: currentFlowState())
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
        val flow = S1LoopFlow()
        loopFlows += flow
        loopDepth++
        try {
          analyzeStatement(statement.body)
          statement.updates.forEach(::analyzeExpression)
        } finally {
          loopDepth--
          loopFlows.removeAt(loopFlows.lastIndex)
        }
        val blankLoopUnassigned = unassignedBlankFinalFields.toSet()
        val exits = flow.breakExits.toMutableList()
        if (statement.condition?.let(::isConstantTrue) != true) {
          exits += S1FlowState(afterCondition, blankEntryAssigned, blankEntryUnassigned)
        }
        replaceFlowState(exits.intersectionOrNull() ?: S1FlowState(
          afterCondition, blankEntryAssigned, blankEntryUnassigned.intersect(blankLoopUnassigned),
        ))
      }
      is JavaAstStatement.Break -> {
        val flow = loopFlows.lastOrNull()
        if (flow == null) {
          error(statement.span, "java.semantic.break_outside_loop", "break 只能出现在循环体内。")
        } else flow.breakExits += currentFlowState()
      }
      is JavaAstStatement.Continue -> if (loopFlows.isEmpty()) {
        error(statement.span, "java.semantic.continue_outside_loop", "continue 只能出现在循环体内。")
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
        val actual = analyzeExpressionExpected(initializer, declaredType)
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
    val actual = analyzeExpressionExpected(value, callable.returnType)
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
      is JavaAstExpression.Parenthesized -> expectedExpressionTypes[expression.nodeId]?.let { expected ->
        analyzeExpressionExpected(expression.expression, expected)
      } ?: analyzeExpression(expression.expression)
      is JavaAstExpression.Binary -> binary(expression)
      is JavaAstExpression.Unary -> unary(expression)
      is JavaAstExpression.Assignment -> assignment(expression)
      is JavaAstExpression.MethodInvocation -> invocation(expression)
      is JavaAstExpression.NewObject -> newObject(expression)
      is JavaAstExpression.NewArray -> newArray(expression)
      is JavaAstExpression.ArrayAccess -> arrayAccess(expression)
      is JavaAstExpression.FieldAccess -> fieldAccess(expression)
    }
    expressionTypes[expression.nodeId] = type
    return type
  }

  /** 仅把赋值上下文目标绑定到当前表达式；普通子表达式不会意外继承 target type。 */
  private fun analyzeExpressionExpected(
    expression: JavaAstExpression,
    expected: JavaSemanticType,
  ): JavaSemanticType {
    expectedExpressionTypes[expression.nodeId] = expected
    return analyzeExpression(expression)
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
    val rawLeft = analyzeExpression(expression.left)
    val rawRight = analyzeExpression(expression.right)
    if (rawLeft == JavaSemanticType.Error || rawRight == JavaSemanticType.Error) return JavaSemanticType.Error
    return when (expression.operator) {
      JavaAstBinaryOperator.ADD -> {
        if (isString(rawLeft) || isString(rawRight)) {
          stringConcatenation(expression.nodeId, rawLeft, rawRight, expression.span)
        }
        else numericBinary(
          expression.span,
          primitiveOperand(expression.left, rawLeft),
          primitiveOperand(expression.right, rawRight),
          relational = false,
        )
      }
      JavaAstBinaryOperator.MULTIPLY,
      JavaAstBinaryOperator.DIVIDE,
      JavaAstBinaryOperator.REMAINDER,
      JavaAstBinaryOperator.SUBTRACT,
      -> numericBinary(
        expression.span,
        primitiveOperand(expression.left, rawLeft),
        primitiveOperand(expression.right, rawRight),
        relational = false,
      )
      JavaAstBinaryOperator.SHIFT_LEFT,
      JavaAstBinaryOperator.SHIFT_RIGHT,
      JavaAstBinaryOperator.UNSIGNED_SHIFT_RIGHT,
      -> {
        val left = primitiveOperand(expression.left, rawLeft)
        val right = primitiveOperand(expression.right, rawRight)
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
      -> numericBinary(
        expression.span,
        primitiveOperand(expression.left, rawLeft),
        primitiveOperand(expression.right, rawRight),
        relational = true,
      )
      JavaAstBinaryOperator.EQUAL,
      JavaAstBinaryOperator.NOT_EQUAL,
      -> {
        // 两个引用（包括 wrapper）保持 Java 身份比较；仅 primitive 与 wrapper 混合时拆箱。
        val left = if (rawLeft is JavaSemanticType.Primitive || rawRight is JavaSemanticType.Primitive) {
          primitiveOperand(expression.left, rawLeft)
        } else rawLeft
        val right = if (rawLeft is JavaSemanticType.Primitive || rawRight is JavaSemanticType.Primitive) {
          primitiveOperand(expression.right, rawRight)
        } else rawRight
        if (equalityCompatible(left, right)) {
        booleanType()
        } else {
          error(expression.span, "java.semantic.invalid_binary_operands", "等值比较的操作数类型不兼容。")
          JavaSemanticType.Error
        }
      }
      JavaAstBinaryOperator.BITWISE_AND,
      JavaAstBinaryOperator.BITWISE_XOR,
      JavaAstBinaryOperator.BITWISE_OR,
      -> {
        val left = primitiveOperand(expression.left, rawLeft)
        val right = primitiveOperand(expression.right, rawRight)
        when {
        left == booleanType() && right == booleanType() -> booleanType()
        isIntegral(left) && isIntegral(right) -> checkNotNull(numericPromotion(left, right))
        else -> {
          error(expression.span, "java.semantic.invalid_binary_operands", "位运算要求两个 integral 或两个 boolean 操作数。")
          JavaSemanticType.Error
        }
        }
      }
      JavaAstBinaryOperator.LOGICAL_AND,
      JavaAstBinaryOperator.LOGICAL_OR,
      -> if (
        primitiveOperand(expression.left, rawLeft) == booleanType() &&
        primitiveOperand(expression.right, rawRight) == booleanType()
      ) {
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
    val declaredOperand = analyzeExpression(expression.operand)
    if (declaredOperand == JavaSemanticType.Error) return JavaSemanticType.Error
    val operand = primitiveOperand(expression.operand, declaredOperand)
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
        !isNumeric(operand) -> invalidUnary(expression.span, "递增递减目标必须是 numeric primitive 或首批 wrapper。")
        else -> {
          if (declaredOperand != operand) {
            updateWriteConversion(
              expression.nodeId,
              checkNotNull(unaryNumericPromotion(operand)),
              declaredOperand,
            )
          }
          declaredOperand
        }
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
    val valueType = analyzeExpressionExpected(expression.value, targetType)
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
    } else if (expression.operator == JavaAstAssignmentOperator.ADD_ASSIGN && isString(targetType)) {
      stringConcatenation(expression.nodeId, targetType, valueType, expression.span)
    } else {
      val readTarget = primitiveOperand(expression.target, targetType)
      val readValue = primitiveOperand(expression.value, valueType)
      compoundAssignmentType(expression.operator, readTarget, readValue, expression.span).let { computedType ->
        if (computedType == JavaSemanticType.Error) {
          JavaSemanticType.Error
        } else {
          if (readTarget != targetType) {
            updateWriteConversion(expression.nodeId, computedType, targetType)
          }
          // Java 复合赋值表达式最终仍具有左值声明类型；计算中间类型只进入回写 side table。
          targetType
        }
      }
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

  /** 复合赋值先在拆箱后的 primitive 上计算；wrapper 回写转换由独立 side table 冻结。 */
  private fun compoundAssignmentType(
    operator: JavaAstAssignmentOperator,
    target: JavaSemanticType,
    value: JavaSemanticType,
    span: JavaSourceSpan,
  ): JavaSemanticType {
    val computed = when (operator) {
      JavaAstAssignmentOperator.ADD_ASSIGN ->
        numericPromotion(target, value)
      JavaAstAssignmentOperator.AND_ASSIGN,
      JavaAstAssignmentOperator.XOR_ASSIGN,
      JavaAstAssignmentOperator.OR_ASSIGN,
      -> when {
        target == booleanType() && value == booleanType() -> booleanType()
        isIntegral(target) && isIntegral(value) -> numericPromotion(target, value)
        else -> null
      }
      JavaAstAssignmentOperator.SHIFT_LEFT_ASSIGN,
      JavaAstAssignmentOperator.SHIFT_RIGHT_ASSIGN,
      JavaAstAssignmentOperator.UNSIGNED_SHIFT_RIGHT_ASSIGN,
      -> if (isIntegral(target) && isIntegral(value)) unaryNumericPromotion(target) else null
      JavaAstAssignmentOperator.MULTIPLY_ASSIGN,
      JavaAstAssignmentOperator.DIVIDE_ASSIGN,
      JavaAstAssignmentOperator.REMAINDER_ASSIGN,
      JavaAstAssignmentOperator.SUBTRACT_ASSIGN,
      -> numericPromotion(target, value)
      JavaAstAssignmentOperator.ASSIGN -> target
    }
    if (computed == null) {
      error(span, "java.semantic.invalid_compound_assignment", "复合赋值两侧类型不兼容。")
      return JavaSemanticType.Error
    }
    return computed
  }

  /**
   * 分析一维数组创建，并把大小或初始化元素的赋值转换写入通用转换表。
   *
   * 首批只接受一个有大小且无初始化器的维度，或一个无大小且携带一维初始化器的维度；
   * 多维、嵌套初始化器与不可具体化泛型 component 均稳定拒绝。
   */
  private fun newArray(expression: JavaAstExpression.NewArray): JavaSemanticType {
    val component = resolveType(
      expression.componentType,
      context().unit,
      currentTypeParameterScope(),
      allowVoid = false,
    )
    if (component == JavaSemanticType.Error) return JavaSemanticType.Error
    if (!isReifiableArrayComponent(component)) {
      error(
        expression.componentType.span,
        "java.semantic.generic_array_creation_unsupported",
        "数组创建的 component 必须是 primitive 或可具体化的非泛型声明类型。",
      )
      return JavaSemanticType.Error
    }
    if (expression.dimensions.size != 1) {
      expression.dimensions.forEach { dimension -> dimension.size?.let(::analyzeExpression) }
      analyzeArrayInitializerElements(expression.initializer, component)
      error(
        expression.span,
        "java.semantic.multidimensional_array_creation_unsupported",
        "Stage2A 首批只支持一维数组创建。",
      )
      return JavaSemanticType.Error
    }

    val dimension = expression.dimensions.single()
    val initializer = expression.initializer
    return when {
      dimension.size != null && initializer == null -> {
        val sizeType = analyzeExpression(dimension.size)
        if (assignArrayIndex(dimension.size, sizeType, "数组长度")) {
          JavaSemanticType.Array(component)
        } else {
          JavaSemanticType.Error
        }
      }
      dimension.size == null && initializer != null -> {
        if (analyzeArrayInitializerElements(initializer, component)) {
          JavaSemanticType.Array(component)
        } else {
          JavaSemanticType.Error
        }
      }
      else -> {
        dimension.size?.let(::analyzeExpression)
        analyzeArrayInitializerElements(initializer, component)
        error(
          expression.span,
          "java.semantic.invalid_array_creation_shape",
          "数组创建必须提供一个长度，或使用一个无长度维度配合初始化器。",
        )
        JavaSemanticType.Error
      }
    }
  }

  /** 分析一维数组初始化器；嵌套花括号属于尚未开放的多维能力。 */
  private fun analyzeArrayInitializerElements(
    initializer: JavaAstArrayInitializer?,
    component: JavaSemanticType,
  ): Boolean {
    if (initializer == null) return true
    var valid = true
    initializer.elements.forEach { element ->
      when (element) {
        is JavaAstArrayInitializerElement.Expression -> {
          val actual = analyzeExpression(element.expression)
          if (!assign(
              element.expression.nodeId,
              actual,
              component,
              element.expression.span,
              code = "java.semantic.array_initializer_type_mismatch",
              message = "数组初始化元素类型与 component 类型不兼容。",
            )
          ) {
            valid = false
          }
        }
        is JavaAstArrayInitializerElement.Nested -> {
          error(
            element.initializer.span,
            "java.semantic.nested_array_initializer_unsupported",
            "Stage2A 首批不支持嵌套数组初始化器。",
          )
          valid = false
        }
      }
    }
    return valid
  }

  /** 数组索引读取返回 component 类型；索引按赋值 widening 转成 int 并登记转换。 */
  private fun arrayAccess(expression: JavaAstExpression.ArrayAccess): JavaSemanticType {
    val arrayType = analyzeExpression(expression.array)
    val indexType = analyzeExpression(expression.index)
    val array = arrayType as? JavaSemanticType.Array
    if (array == null) {
      if (arrayType != JavaSemanticType.Error) {
        error(
          expression.array.span,
          "java.semantic.array_receiver_required",
          "下标访问的 receiver 必须是数组。",
        )
      }
      return JavaSemanticType.Error
    }
    return if (assignArrayIndex(expression.index, indexType, "数组下标")) {
      array.componentType
    } else {
      JavaSemanticType.Error
    }
  }

  /** 只接受 byte/short/char/int 数组长度或索引，并把 widening 明确记录到 int。 */
  private fun assignArrayIndex(
    expression: JavaAstExpression,
    actual: JavaSemanticType,
    displayName: String,
  ): Boolean = assign(
    expression.nodeId,
    actual,
    intType(),
    expression.span,
    code = "java.semantic.array_index_type",
    message = "$displayName 必须是 byte、short、char 或 int。",
  )

  /**
   * 判断数组运行时 component 是否可具体化。
   *
   * 非泛型声明和仅含无界 wildcard 的声明可按擦除保留运行时身份；具体类型实参与类型变量
   * 会丢失必要信息，必须拒绝创建。
   */
  private fun isReifiableArrayComponent(type: JavaSemanticType): Boolean = when (type) {
    is JavaSemanticType.Primitive -> true
    is JavaSemanticType.Declared -> type.arguments.all { argument ->
      argument is JavaSemanticType.Wildcard &&
        argument.upperBound == null && argument.lowerBound == null
    }
    else -> false
  }

  /** 显式字段访问区分数组 length、type-qualified、super 与普通实例 receiver。 */
  private fun fieldAccess(expression: JavaAstExpression.FieldAccess): JavaSemanticType {
    val receiver = resolveReceiver(expression.receiver)
    if (receiver == null || receiver.type == JavaSemanticType.Error) return JavaSemanticType.Error
    if (receiver.type is JavaSemanticType.Array) {
      if (expression.fieldName == "length" && receiver.kind == JavaReceiverKind.EXPLICIT) {
        arrayLengthExpressions += expression.nodeId
        return intType()
      }
      error(expression.span, "java.semantic.unknown_array_member", "数组只提供只读 length 成员。")
      return JavaSemanticType.Error
    }
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
    val declaredReceiver = receiver.type as? JavaSemanticType.Declared
    if (declaredReceiver == null) {
      expression.arguments.forEach(::analyzeExpression)
      if (receiver.type == JavaSemanticType.Error) return JavaSemanticType.Error
      error(expression.span, "java.semantic.method_receiver_not_declared", "方法 receiver 必须是 class 或 interface 类型。")
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
    val argumentTypes = analyzeInvocationArguments(
      expression.arguments,
      accessible,
      explicitTypeArguments,
    )
    if (argumentTypes.any { it == JavaSemanticType.Error }) return JavaSemanticType.Error
    if (modeCandidates.isNotEmpty() && accessible.isEmpty()) {
      error(expression.span, "java.semantic.inaccessible_method", "方法 ${expression.methodName} 在当前上下文不可访问。")
      return JavaSemanticType.Error
    }
    val instantiated = instantiateApplicablePhase(
      accessible,
      argumentTypes,
      explicitTypeArguments,
    )
    val selected = selectMostSpecific(instantiated, expression.span, expression.methodName)
      ?: return JavaSemanticType.Error
    recordArgumentConversions(expression.arguments, argumentTypes, selected.parameterTypes)
    val dispatch = when {
      selected.info.isStatic -> JavaDispatchKind.STATIC
      selected.info.isBuiltinVirtualRoot -> JavaDispatchKind.VIRTUAL
      selected.info.isBuiltin -> JavaDispatchKind.SPECIAL
      receiver.kind == JavaReceiverKind.SUPER -> JavaDispatchKind.SPECIAL
      selected.info.owner.kind == JavaSemanticTypeDeclarationKind.INTERFACE -> JavaDispatchKind.INTERFACE
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
      if (dispatch == JavaDispatchKind.VIRTUAL || dispatch == JavaDispatchKind.INTERFACE) {
        virtualSlots[selected.info.symbol]
      } else {
        null
      },
    )
    return selected.returnType
  }

  /** new 表达式支持显式参数化类型与从构造器实参推断的 diamond。 */
  private fun newObject(expression: JavaAstExpression.NewObject): JavaSemanticType {
    val named = expression.type as? JavaAstTypeReference.Named
    if (named == null) {
      expression.arguments.forEach(::analyzeExpression)
      resolveType(expression.type, context().unit, currentTypeParameterScope(), allowVoid = false)
      error(expression.span, "java.semantic.invalid_new_type", "new 只能创建 class 声明类型。")
      return JavaSemanticType.Error
    }
    val explicitCreatedType = if (named.usesDiamond) {
      null
    } else {
      resolveType(named, context().unit, currentTypeParameterScope(), allowVoid = false)
        as? JavaSemanticType.Declared
    }
    val argumentTypes = if (explicitCreatedType != null) {
      analyzeInvocationArguments(
        expression.arguments,
        constructorCandidates(explicitCreatedType),
      )
    } else {
      expression.arguments.map(::analyzeExpression)
    }
    if (argumentTypes.any { it == JavaSemanticType.Error }) return JavaSemanticType.Error
    val createdType = if (named.usesDiamond) {
      inferDiamondType(
        named,
        argumentTypes,
        expression.span,
        expectedExpressionTypes[expression.nodeId],
      )
    } else {
      explicitCreatedType
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

  /**
   * 为 diamond 推断 class 类型参数。
   *
   * Java 8 允许 `List<Integer> values = new ArrayList<>()` 由目标类型提供约束；集合
   * facade 没有伪造 JDK 源码，因此这里直接使用 catalog 声明的 generic direct-super
   * 关系反推实现类参数。若目标类型无法给出完整约束，再退回已有的构造器实参推断。
   */
  private fun inferDiamondType(
    reference: JavaAstTypeReference.Named,
    argumentTypes: List<JavaSemanticType>,
    span: JavaSourceSpan,
    expectedType: JavaSemanticType?,
  ): JavaSemanticType.Declared? {
    if (reference.arguments.isNotEmpty()) {
      error(reference.span, "java.semantic.invalid_diamond", "diamond 不能同时携带显式类型实参。")
      return null
    }
    val owner = visibleType(reference.qualifiedName, context().unit, reference.span) ?: return null
    if (owner.typeParameters.isEmpty()) return JavaSemanticType.Declared(owner.symbol, emptyList())
    val inference = JavaGenericInference(relations(), typeParameterDeclarations)
    val expectedDeclared = expectedType as? JavaSemanticType.Declared
    val targetSubstitutions = expectedDeclared?.let { expected ->
      when {
        expected.symbol == owner.symbol && expected.arguments.size == owner.typeParameters.size ->
          owner.typeParameters.zip(expected.arguments).toMap()

        owner.directSuperClass != null -> inference.infer(
          owner.typeParameters,
          listOfNotNull(owner.directSuperClass),
          listOf(expected),
        )

        else -> null
      }
    }
    if (targetSubstitutions != null && owner.typeParameters.all(targetSubstitutions::containsKey)) {
      return JavaSemanticType.Declared(
        owner.symbol,
        owner.typeParameters.map(targetSubstitutions::getValue),
      )
    }
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
        when {
          inferredTypes.size > 1 -> "java.semantic.ambiguous_overload"
          argumentTypes.isEmpty() -> "java.semantic.diamond_target_required"
          else -> "java.semantic.diamond_inference_failed"
        },
        if (argumentTypes.isEmpty()) {
          "零参 diamond 需要可代换到实现类泛型参数的目标类型。"
        } else {
          "无法从目标类型或构造器实参唯一推断 diamond 类型实参。"
        },
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
    val candidates = constructorCandidates(ownerType)
    val applicable = instantiateApplicablePhase(candidates, argumentTypes, emptyList())
    val selected = selectMostSpecific(applicable, span, owner.simpleName)
    if (selected == null && invocationNode == null && applicable.isEmpty()) {
      // selectMostSpecific 已产生稳定诊断；该分支只强调 implicit super 的来源。
      return null
    }
    return selected
  }

  /** 返回已完成 class 类型实参代换的可见构造器候选。 */
  private fun constructorCandidates(ownerType: JavaSemanticType.Declared): List<S1CallableCandidate> {
    val owner = typeInfo(ownerType.symbol) ?: return emptyList()
    val classSubstitutions = owner.typeParameters.zip(ownerType.arguments).toMap()
    return constructorsByOwner[owner.symbol].orEmpty()
      .filter(::isCallableAccessible)
      .map { S1CallableCandidate(it, classSubstitutions) }
  }

  /**
   * 以候选签名为参数位置的 target type 分析实参。
   *
   * 先正常分析所有非 poly 实参，并用这些已冻结类型排除明显不适用的候选；只有剩余候选在
   * diamond 位置给出一致或唯一最具体的具体引用类型时，才提交 expected side table。这样不会
   * 以某个候选试探性分析表达式，也不会让失败候选的诊断、转换或绑定污染最终模型。
   */
  private fun analyzeInvocationArguments(
    arguments: List<JavaAstExpression>,
    candidates: List<S1CallableCandidate>,
    explicitTypeArguments: List<JavaSemanticType> = emptyList(),
  ): List<JavaSemanticType> {
    val types = MutableList<JavaSemanticType?>(arguments.size) { null }
    arguments.forEachIndexed { index, argument ->
      if (!argument.isTargetTypedDiamond()) types[index] = analyzeExpression(argument)
    }
    val prepared = candidates.mapNotNull { candidate ->
      if (candidate.info.parameterTypes.size != arguments.size) return@mapNotNull null
      val ownerParameters = candidate.info.parameterTypes.map { parameter ->
        relations().substitute(parameter, candidate.ownerSubstitutions) ?: return@mapNotNull null
      }
      val fixed = if (candidate.info.typeParameters.isEmpty()) {
        if (explicitTypeArguments.isNotEmpty()) return@mapNotNull null
        emptyMap()
      } else {
        val knownIndices = types.indices.filter { types[it] != null }
        when (val partial = JavaGenericInference(relations(), typeParameterDeclarations).inferPartial(
          candidate.info.typeParameters,
          knownIndices.map(ownerParameters::get),
          knownIndices.map { checkNotNull(types[it]) },
          explicitTypeArguments,
          fixedSubstitutions = candidate.ownerSubstitutions,
        )) {
          JavaPartialGenericInferenceResult.Conflict -> return@mapNotNull null
          is JavaPartialGenericInferenceResult.Success -> partial.substitutions
        }
      }
      val parameters = ownerParameters.map { parameter ->
        relations().substitute(parameter, fixed) ?: return@mapNotNull null
      }
      S1PolyCandidate(parameters)
    }
    fun applicable(candidate: S1PolyCandidate, allowBoxing: Boolean): Boolean =
      types.indices.all { index ->
        val actual = types[index] ?: return@all true
        val parameter = candidate.parameterTypes[index]
        parameter.containsUnresolvedTypeVariable() ||
          relations().invocationConversion(actual, parameter, allowBoxing) != null
      }
    val strict = prepared.filter { applicable(it, allowBoxing = false) }
    // Java overload phase 不能把 loose 候选与已有 strict 候选混在一起。
    val remaining = strict.ifEmpty { prepared.filter { applicable(it, allowBoxing = true) } }
    arguments.forEachIndexed { index, argument ->
      if (types[index] != null) return@forEachIndexed
      val targets = remaining.mapNotNull { candidate ->
        candidate.parameterTypes[index] as? JavaSemanticType.Declared
      }.filter(::isConcreteInvocationTarget).distinct()
      val target = uniqueMostSpecificInvocationTarget(targets)
      types[index] = if (target != null) {
        analyzeExpressionExpected(argument, target)
      } else {
        analyzeExpression(argument)
      }
    }
    return types.map { checkNotNull(it) }
  }

  /** Java 8 本批唯一需要 target type 的 poly expression 是可被括号包裹的 diamond new。 */
  private fun JavaAstExpression.isTargetTypedDiamond(): Boolean = when (this) {
    is JavaAstExpression.NewObject -> (type as? JavaAstTypeReference.Named)?.usesDiamond == true
    is JavaAstExpression.Parenthesized -> expression.isTargetTypedDiamond()
    else -> false
  }

  /** 方法自身类型变量尚未实例化时不能据此排除候选，也不能作为 diamond 的最终目标。 */
  private fun JavaSemanticType.containsUnresolvedTypeVariable(): Boolean = when (this) {
    is JavaSemanticType.TypeVariable, is JavaSemanticType.Wildcard -> true
    is JavaSemanticType.Declared -> arguments.any { it.containsUnresolvedTypeVariable() }
    is JavaSemanticType.Array -> componentType.containsUnresolvedTypeVariable()
    else -> false
  }

  /** 参数 target 仍含 type variable/wildcard 时不能安全驱动 poly expression。 */
  private fun isConcreteInvocationTarget(type: JavaSemanticType.Declared): Boolean =
    type.arguments.all { argument ->
      when (argument) {
        is JavaSemanticType.Declared -> isConcreteInvocationTarget(argument)
        is JavaSemanticType.Array -> isConcreteInvocationTargetComponent(argument.componentType)
        is JavaSemanticType.Primitive -> true
        JavaSemanticType.Null -> true
        else -> false
      }
    }

  /** 递归检查数组 component，拒绝仍待推断的类型变量与 wildcard。 */
  private fun isConcreteInvocationTargetComponent(type: JavaSemanticType): Boolean = when (type) {
    is JavaSemanticType.Declared -> isConcreteInvocationTarget(type)
    is JavaSemanticType.Array -> isConcreteInvocationTargetComponent(type.componentType)
    is JavaSemanticType.Primitive -> true
    else -> false
  }

  /** 从候选参数目标中选择能 strict widening 到其余目标的唯一最具体者。 */
  private fun uniqueMostSpecificInvocationTarget(
    targets: List<JavaSemanticType.Declared>,
  ): JavaSemanticType.Declared? {
    if (targets.size == 1) return targets.single()
    val maximal = targets.filter { candidate ->
      targets.all { other ->
        candidate == other ||
          relations().invocationConversion(candidate, other, allowBoxing = false) != null
      }
    }
    return maximal.singleOrNull()
  }

  /** 把 owner 代换与方法类型推断合并，得到可参与 overload 的实际签名。 */
  private fun instantiateCallable(
    candidate: S1CallableCandidate,
    argumentTypes: List<JavaSemanticType>,
    explicitTypeArguments: List<JavaSemanticType>,
    allowBoxing: Boolean,
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
    if (!argumentTypes.zip(parameterTypes).all { (actual, expected) ->
        relations().invocationConversion(actual, expected, allowBoxing) != null
      }
    ) {
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
   * Java overload 先运行 strict invocation；仅在该阶段没有候选时进入 boxing/unboxing loose phase。
   * 这保证 `byte -> int` 的 primitive widening 总是优先于 `byte -> Byte`。
   */
  private fun instantiateApplicablePhase(
    candidates: List<S1CallableCandidate>,
    argumentTypes: List<JavaSemanticType>,
    explicitTypeArguments: List<JavaSemanticType>,
  ): List<S1InstantiatedCallable> {
    val strict = candidates.mapNotNull { candidate ->
      instantiateCallable(candidate, argumentTypes, explicitTypeArguments, allowBoxing = false)
    }
    if (strict.isNotEmpty()) return strict
    return candidates.mapNotNull { candidate ->
      instantiateCallable(candidate, argumentTypes, explicitTypeArguments, allowBoxing = true)
    }
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
    left == right || relations().invocationConversion(left, right, allowBoxing = false) != null
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

  /** 从 receiver 类型沿父类和接口图收集方法；最近声明的同擦除 descriptor 遮蔽祖先候选。 */
  private fun methodCandidates(
    receiverType: JavaSemanticType.Declared,
    name: String,
  ): List<S1CallableCandidate> {
    val result = mutableListOf<S1CallableCandidate>()
    val seenDescriptors = mutableSetOf<String>()
    val pending = ArrayDeque<JavaSemanticType.Declared>()
    pending += receiverType
    val visited = mutableSetOf<JavaSymbolId>()
    while (pending.isNotEmpty()) {
      val current = pending.removeFirst()
      if (!visited.add(current.symbol)) continue
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
      owner.directSuperClass?.let { relations().substitute(it, substitutions) as? JavaSemanticType.Declared }
        ?.let(pending::addLast)
      owner.directInterfaces.mapNotNullTo(pending) { inherited ->
        relations().substitute(inherited, substitutions) as? JavaSemanticType.Declared
      }
    }
    return result
  }

  /** 返回父类与接口图中的全部祖先方法，供 override 校验使用。 */
  private fun inheritedMethods(type: S1TypeInfo): List<S1CallableInfo> {
    val result = mutableListOf<S1CallableInfo>()
    val pending = ArrayDeque<S1TypeInfo>()
    type.directSuperClass?.symbol?.let(::typeInfo)?.let(pending::addLast)
    type.directInterfaces.mapNotNullTo(pending) { it.symbol.let(::typeInfo) }
    val visited = mutableSetOf<JavaSymbolId>()
    while (pending.isNotEmpty()) {
      val parent = pending.removeFirst()
      if (!visited.add(parent.symbol)) continue
      result += callables.filter {
        it.owner.symbol == parent.symbol && it.kind == JavaSemanticCallableKind.METHOD
      }
      parent.directSuperClass?.symbol?.let(::typeInfo)?.let(pending::addLast)
      parent.directInterfaces.mapNotNullTo(pending) { it.symbol.let(::typeInfo) }
    }
    return result
  }

  /** 查找字段并沿继承链代换 generic owner 类型参数。 */
  private fun lookupField(
    receiverType: JavaSemanticType.Declared,
    name: String,
  ): S1FieldView? {
    val pending = ArrayDeque<JavaSemanticType.Declared>()
    pending += receiverType
    val visited = mutableSetOf<JavaSymbolId>()
    while (pending.isNotEmpty()) {
      val current = pending.removeFirst()
      if (!visited.add(current.symbol)) continue
      val owner = typeInfo(current.symbol) ?: return null
      val substitutions = owner.typeParameters.zip(current.arguments).toMap()
      fieldsByOwnerAndName[owner.symbol to name]?.let { field ->
        val type = relations().substitute(field.type, substitutions) ?: JavaSemanticType.Error
        return S1FieldView(field, type)
      }
      owner.directSuperClass?.let { relations().substitute(it, substitutions) as? JavaSemanticType.Declared }
        ?.let(pending::addLast)
      owner.directInterfaces.mapNotNullTo(pending) { inherited ->
        relations().substitute(inherited, substitutions) as? JavaSemanticType.Declared
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

  /** 登记常用 Java 字面量；浮点当前只需要类型，char 以无损 UTF-16 code unit 保存。 */
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

  /**
   * char literal 必须解码为一个 UTF-16 code unit。
   *
   * 冻结常量契约没有独立 char 值，因此使用 [JavaConstantValue.IntValue] 无损保存 0..65535；
   * lowering 必须读取该 side table，不能重新解析源码 token。
   */
  private fun characterLiteral(expression: JavaAstExpression.Literal): JavaSemanticType {
    val value = decodeStage1Character(expression.tokenText)
    if (value == null) {
      error(expression.span, "java.semantic.invalid_character_literal", "char literal 必须包含一个合法字符。")
      return JavaSemanticType.Error
    }
    constants[expression.nodeId] = JavaConstantValue.IntValue(value.code)
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

  /** 按限定名、当前 CU 顶层声明、显式单类型 import、同 package、java.lang、通配 import 顺序解析。 */
  private fun visibleTypeOrNull(
    name: String,
    unit: JavaAstCompilationUnit,
    span: JavaSourceSpan,
  ): S1TypeInfo? {
    if ('.' in name) return typesByQualifiedName[name]?.let { ensureTypeAccessible(it, unit, span) }
    val samePackage = typesByQualifiedName[qualifyStage1(unit.packageName, name)]
    // builtin 只为 span 复用首个 unit，必须同时存在源码 declaration 才能认定为当前 CU 顶层声明。
    // 真实当前 CU 声明即使与 import 冲突仍保持最高绑定优先级；冲突由 import 校验稳定报告。
    samePackage?.takeIf { it.declaration != null && it.unit === unit }?.let { return it }
    val explicit = unit.imports
      .filter { !it.isStatic && !it.isWildcard && it.qualifiedName.substringAfterLast('.') == name }
      .mapNotNull { typesByQualifiedName[it.qualifiedName] }
      .distinctBy { it.symbol }
    if (explicit.size == 1) return ensureTypeAccessible(explicit.single(), unit, span)
    if (explicit.size > 1) {
      error(span, "java.semantic.ambiguous_type", "类型名 $name 被多个显式 import 引入。")
      return null
    }
    samePackage?.let { return it }
    typesByQualifiedName["java.lang.$name"]?.let { return it }
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

  /** Name、FieldAccess 与 ArrayAccess 是 Stage2A 可写左值；数组 length 仍保持只读。 */
  private fun writable(expression: JavaAstExpression): Boolean {
    if (expression is JavaAstExpression.ArrayAccess) return true
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
      field.declarator?.initializer == null && !field.isSynthetic
  }

  /** 返回 owner 自身需要由构造器初始化的 instance blank final 字段。 */
  private fun blankFinalFields(owner: S1TypeInfo): Set<JavaSymbolId> = fields.asSequence()
    .filter {
      it.owner.symbol == owner.symbol && it.isFinal && !it.isStatic &&
        it.declarator?.initializer == null && !it.isSynthetic
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
    is JavaAstStatement.While -> !isConstantTrue(statement.condition) ||
      containsBreakForCurrentLoop(statement.body)
    is JavaAstStatement.DoWhile -> !isConstantTrue(statement.condition) ||
      containsBreakForCurrentLoop(statement.body)
    is JavaAstStatement.For -> statement.condition?.let(::isConstantTrue) != true ||
      containsBreakForCurrentLoop(statement.body)
    is JavaAstStatement.Break,
    is JavaAstStatement.Continue,
    is JavaAstStatement.Return -> false
    else -> true
  }

  /** 仅查找直接属于当前循环的 break，嵌套循环会消费自己的跳转。 */
  private fun containsBreakForCurrentLoop(statement: JavaAstStatement): Boolean = when (statement) {
    is JavaAstStatement.Break -> true
    is JavaAstStatement.Block -> statement.statements.any(::containsBreakForCurrentLoop)
    is JavaAstStatement.If -> containsBreakForCurrentLoop(statement.thenBranch) ||
      statement.elseBranch?.let(::containsBreakForCurrentLoop) == true
    is JavaAstStatement.While,
    is JavaAstStatement.DoWhile,
    is JavaAstStatement.For -> false
    else -> false
  }

  /** 捕获当前 DA/DU 状态，供循环 break 出口参与交集。 */
  private fun currentFlowState() = S1FlowState(
    definitelyAssigned.toSet(),
    assignedBlankFinalFields.toSet(),
    unassignedBlankFinalFields.toSet(),
  )

  /** 同时恢复普通局部与 blank-final 的流状态。 */
  private fun replaceFlowState(state: S1FlowState) {
    replaceAssigned(state.assigned)
    replaceBlankFinalState(state.blankAssigned, state.blankUnassigned)
  }

  /** 所有可达出口均满足时才保留 definite-assigned/definite-unassigned。 */
  private fun List<S1FlowState>.intersectionOrNull(): S1FlowState? {
    val first = firstOrNull() ?: return null
    return drop(1).fold(first) { left, right ->
      S1FlowState(
        left.assigned.intersect(right.assigned),
        left.blankAssigned.intersect(right.blankAssigned),
        left.blankUnassigned.intersect(right.blankUnassigned),
      )
    }
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
    wrapperPrimitiveTypes,
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

  /**
   * 运算上下文只对 catalog 中明确支持的 wrapper 执行拆箱，并把转换绑定在原操作数节点上。
   * 普通引用保持不变，随后由既有 numeric/boolean 检查给出源码位置诊断。
   */
  private fun primitiveOperand(
    expression: JavaAstExpression,
    type: JavaSemanticType,
  ): JavaSemanticType {
    val primitive = relations().unboxedPrimitive(type) ?: return type
    val target = JavaSemanticType.Primitive(primitive)
    val conversion = checkNotNull(
      relations().invocationConversion(type, target, allowBoxing = true),
    ) { "A declared wrapper must have a deterministic unboxing conversion." }
    conversions[expression.nodeId] = conversion
    return target
  }

  /**
   * 冻结 wrapper 更新后的隐式回写步骤：byte/short/char 的算术结果先窄化，再按精确 wrapper 装箱。
   * 该窄化只属于 Java 的 ++/-- 与复合赋值规则，绝不会被普通赋值或 overload 复用。
   */
  private fun updateWriteConversion(
    nodeId: JavaNodeId,
    computedType: JavaSemanticType,
    declaredTargetType: JavaSemanticType,
  ) {
    val primitive = relations().unboxedPrimitive(declaredTargetType)
      ?: error("An update write conversion requires a supported wrapper target.")
    val computedPrimitive = (computedType as? JavaSemanticType.Primitive)?.kind
      ?: error("An update write conversion requires a primitive computed value.")
    val boxed = (declaredTargetType as JavaSemanticType.Declared).symbol
    val steps = buildList {
      if (computedPrimitive != primitive) {
        add(JavaSemanticConversion.PrimitiveNarrowing(computedPrimitive, primitive))
      }
      add(JavaSemanticConversion.Boxing(primitive, boxed))
    }
    updateWriteConversions[nodeId] = if (steps.size == 1) steps.single()
    else JavaSemanticConversion.Sequence(steps)
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

  /**
   * 校验并登记本阶段支持的 String 拼接转换。
   *
   * 首批 wrapper 使用冻结的 tagged 字符串化；任意对象、数组、long 与浮点仍稳定拒绝。
   */
  private fun stringConcatenation(
    nodeId: JavaNodeId,
    left: JavaSemanticType,
    right: JavaSemanticType,
    span: JavaSourceSpan,
  ): JavaSemanticType {
    val leftKind = stringConversionKind(left)
    val rightKind = stringConversionKind(right)
    if (leftKind == null || rightKind == null) {
      error(
        span,
        "java.semantic.string_concat_operand_unsupported",
        "String 拼接暂只支持 String、null、首批 wrapper、boolean、byte、short、char 与 int。",
      )
      return JavaSemanticType.Error
    }
    stringConcatenations[nodeId] = JavaStringConcatenationBinding(leftKind, rightKind)
    return stringType.selfType()
  }

  /** 将操作数类型映射为 lowering 可直接消费的 String 转换类别。 */
  private fun stringConversionKind(type: JavaSemanticType): JavaStringConversionKind? = when {
    isString(type) -> JavaStringConversionKind.STRING
    type == JavaSemanticType.Null -> JavaStringConversionKind.NULL
    type == booleanType() -> JavaStringConversionKind.BOOLEAN
    type == JavaSemanticType.Primitive(JavaAstPrimitiveType.CHAR) ->
      JavaStringConversionKind.CHAR
    type is JavaSemanticType.Primitive && type.kind in setOf(
      JavaAstPrimitiveType.BYTE,
      JavaAstPrimitiveType.SHORT,
      JavaAstPrimitiveType.INT,
    ) -> JavaStringConversionKind.INT_LIKE
    relations().unboxedPrimitive(type) != null -> JavaStringConversionKind.BOXED
    else -> null
  }

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
        stringConcatenations.toMap(),
        arrayLengthExpressions.toSet(),
        updateWriteConversions.toMap(),
        builtinMembers.toMap(),
        builtinTypeRoles.toMap(),
        wrapperPrimitiveTypes.toMap(),
        interfaceDefaultMethods.toMap(),
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
  val isAbstract: Boolean = false,
  var directSuperClass: JavaSemanticType.Declared? = null,
) {
  val typeParameters = mutableListOf<JavaSymbolId>()
  val typeParameterNames = linkedMapOf<String, JavaSymbolId>()
  val membersInSourceOrder = mutableListOf<JavaSymbolId>()
  val directInterfaces = mutableListOf<JavaSemanticType.Declared>()
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
    directInterfaces.toList(),
  )
}

/**
 * 分析期字段签名。
 *
 * builtin 字段没有 AST [declaration]/[declarator]，但仍提供稳定 [name]/[span]；源码专属的
 * 初始化与 blank-final 逻辑必须先排除 [isSynthetic]。
 */
private data class S1FieldInfo(
  val owner: S1TypeInfo,
  val declaration: JavaAstMemberDeclaration.Field?,
  val declarator: JavaAstVariableDeclarator?,
  val symbol: JavaSymbolId,
  val name: String,
  val type: JavaSemanticType,
  val visibility: JavaVisibility,
  val isStatic: Boolean,
  val isFinal: Boolean,
  val order: Int,
  val span: JavaSourceSpan,
  val isSynthetic: Boolean = false,
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
  /** builtin callable 由稳定 operation 执行；除 Object 虚方法根外不参与用户类槽位。 */
  val isBuiltin: Boolean = false,
  /** Object.equals/hashCode/toString 作为虚方法根，源码 override 必须继承它的槽位。 */
  val isBuiltinVirtualRoot: Boolean = false,
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

/** 仅用于 poly 实参目标推断的临时候选；不会写入 semantic side table。 */
private data class S1PolyCandidate(val parameterTypes: List<JavaSemanticType>)

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

/** 循环分析期间收集的 break 正常出口。 */
private data class S1LoopFlow(
  val breakExits: MutableList<S1FlowState> = mutableListOf(),
)

/** 局部 definite-assignment 与 blank-final DA/DU 的不可变快照。 */
private data class S1FlowState(
  val assigned: Set<JavaSymbolId>,
  val blankAssigned: Set<JavaSymbolId>,
  val blankUnassigned: Set<JavaSymbolId>,
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
