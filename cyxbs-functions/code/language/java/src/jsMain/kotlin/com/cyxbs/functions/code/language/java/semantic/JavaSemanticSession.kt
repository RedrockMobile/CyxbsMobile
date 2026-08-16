package com.cyxbs.functions.code.language.java.semantic

import com.cyxbs.functions.code.language.java.completion.JAVA_BUILTIN_MEMBERS
import com.cyxbs.functions.code.language.java.completion.JAVA_BUILTIN_TYPES
import com.cyxbs.functions.code.language.java.completion.JAVA_KEYWORDS
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicFileRename
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicRunTarget
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.js.bridge.DynamicTextEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.language.lezer.LezerSyntaxNode
import com.cyxbs.functions.code.language.lezer.LezerTree

/**
 * 面向 Java 多文件教学工作区的轻量语义会话。
 *
 * 每个文件仅在源码变化时重建索引；高亮与语义分析通过 [syntaxTree] 共享同一棵 Lezer 增量树。
 * 本实现不加载 JDK 或 Maven classpath，只对工作区内可以唯一解析的绑定提供跳转和重命名。
 */
internal class JavaSemanticSession(
  private val syntaxTree: (filePath: String, source: String) -> LezerTree,
) {
  private val indexes = mutableMapOf<String, CachedJavaSemanticIndex>()

  /**
   * 查询光标处的词法符号、工作区类型或可确定 receiver 的成员。
   *
   * 自动触发且没有前缀或 receiver 时不弹出候选，避免每次输入空白都执行无意义展示。
   */
  fun complete(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    val source = workspace.requireSource(filePath)
    require(position in 0..source.length) { "position must be inside source." }
    val index = indexFor(filePath, source)
    if (index.isCompletionSuppressed(position)) return null

    val from = source.findJavaIdentifierStart(position)
    val prefix = source.substring(from, position)
    val receiver = source.receiverBefore(from)
    if (!explicit && prefix.isEmpty() && receiver == null) return null

    val workspaceIndex = workspaceIndex(workspace)
    val options = if (receiver == null) {
      index.lexicalCompletions(position, prefix) +
        workspaceIndex.typeCompletions(filePath, prefix)
    } else {
      workspaceIndex.memberCompletions(filePath, position, receiver, prefix)
    }
    val distinctOptions = options
      .distinctBy(DynamicCompletionItem::label)
      .sortedWith(compareByDescending<DynamicCompletionItem> { item -> item.boost }.thenBy { item -> item.label })
    return distinctOptions.takeIf(List<DynamicCompletionItem>::isNotEmpty)?.let {
      DynamicCompletionResult(from = from, to = position, options = it)
    }
  }

  /** 返回光标所在且可唯一解析符号的定义。 */
  fun definition(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolDefinition? {
    return workspaceIndex(workspace).definition(filePath, position)
  }

  /** 返回光标符号在工作区内可静态确认的引用。 */
  fun references(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolReferencesResult? {
    return workspaceIndex(workspace).references(filePath, position)
  }

  /**
   * 为可唯一解析的符号生成工作区修改。
   *
   * public 顶层类型会同时返回 Java 文件重命名；可能发生名称捕获的修改会被明确拒绝。
   */
  fun rename(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    newName: String,
  ): DynamicRenameResult? {
    return workspaceIndex(workspace).rename(filePath, position, newName)
  }

  /**
   * 返回工作区中的 Java `main` 入口，并复用高亮、补全已经建立的单文件增量索引。
   *
   * 正式 Java 入口识别 `public static void main(String[]/String... args)`；阶段 0 另外允许无参数
   * `public static main()`，用于在数组和标准库桥接完成前运行当前编译器已经支持的教学子集。
   */
  fun runTargets(workspace: DynamicLanguageWorkspace): List<DynamicRunTarget> {
    require(workspace.files.map { file -> file.path }.distinct().size == workspace.files.size) {
      "Workspace file paths must be unique."
    }
    return workspace.files.flatMap { file ->
      val index = indexFor(file.path, file.source)
      index.symbols.mapNotNull { symbol ->
        if (!symbol.isJavaMainTarget()) return@mapNotNull null
        val ownerName = symbol.ownerTypeName ?: return@mapNotNull null
        val qualifiedOwnerName = listOfNotNull(
          index.packageName.takeIf(String::isNotEmpty),
          ownerName,
        ).joinToString(".")
        val location = DynamicSourceLocation(file.path, symbol.definition)
        DynamicRunTarget(
          displayName = "$qualifiedOwnerName.main",
          entry = DynamicProgramEntry(
            filePath = file.path,
            position = symbol.definition.from,
          ),
          location = location,
        )
      }
    }.sortedWith(
      compareBy<DynamicRunTarget> { target -> target.entry.filePath }
        .thenBy { target -> target.entry.position },
    )
  }

  /** 删除工作区中已不存在的文件缓存。 */
  fun retainFiles(filePaths: Set<String>) {
    indexes.keys.retainAll(filePaths)
  }

  /** 仅在文件内容变化时重建单文件语义索引。 */
  private fun indexFor(filePath: String, source: String): JavaSemanticIndex {
    val cached = indexes[filePath]
    if (cached == null || cached.source != source) {
      indexes[filePath] = CachedJavaSemanticIndex(
        source = source,
        index = JavaSemanticIndex(source, syntaxTree(filePath, source)),
      )
    }
    return checkNotNull(indexes[filePath]).index
  }

  /** 复用各文件索引，临时建立 package/import 关联层。 */
  private fun workspaceIndex(workspace: DynamicLanguageWorkspace): JavaWorkspaceSemanticIndex {
    require(workspace.files.map { file -> file.path }.distinct().size == workspace.files.size) {
      "Workspace file paths must be unique."
    }
    return JavaWorkspaceSemanticIndex(
      files = workspace.files.associate { file -> file.path to file.source },
      indexes = workspace.files.associate { file -> file.path to indexFor(file.path, file.source) },
    )
  }
}

/** 按源码内容命中的单文件缓存。 */
private data class CachedJavaSemanticIndex(
  val source: String,
  val index: JavaSemanticIndex,
)

/**
 * 将单文件索引按 Java package 和显式单类型 import 关联。
 *
 * 通配 import、static import 和外部 classpath 不会被猜测；泛型与重载只覆盖静态可唯一判定的常用写法。
 */
private class JavaWorkspaceSemanticIndex(
  private val files: Map<String, String>,
  private val indexes: Map<String, JavaSemanticIndex>,
) {
  private val typesByQualifiedName = mutableMapOf<String, MutableList<JavaWorkspaceSymbol>>().apply {
    indexes.forEach { (filePath, index) ->
      index.symbols
        .filter { symbol -> symbol.kind == JavaSymbolKind.TYPE }
        .forEach { symbol ->
          val typeName = listOfNotNull(symbol.ownerTypeName, symbol.name).joinToString(".")
          val qualifiedName = listOfNotNull(index.packageName.takeIf(String::isNotEmpty), typeName)
            .joinToString(".")
          getOrPut(qualifiedName) { mutableListOf() } += JavaWorkspaceSymbol(filePath, index, symbol)
        }
    }
  }

  /** 返回当前文件可直接使用的工作区类型和教学常用类型。 */
  fun typeCompletions(filePath: String, prefix: String): List<DynamicCompletionItem> {
    val index = requireIndex(filePath, 0)
    val workspaceTypes = indexes
      .asSequence()
      .flatMap { (path, candidate) ->
        candidate.symbols.asSequence()
          .filter { symbol -> symbol.kind == JavaSymbolKind.TYPE }
          .filter { symbol ->
            path == filePath ||
              candidate.packageName == index.packageName ||
              index.imports.any { import ->
                !import.isWildcard && import.qualifiedName.endsWith("." + symbol.name)
              }
          }
      }
      .filter { symbol -> symbol.name.startsWith(prefix) && symbol.name != prefix }
      .map(JavaSymbol::toCompletionItem)
      .toList()
    val builtins = JAVA_BUILTIN_TYPES.filter { item ->
      item.label.startsWith(prefix) && item.label != prefix
    }
    return workspaceTypes + builtins
  }

  /**
   * 返回 receiver 的稳定目录成员或工作区自定义类型成员。
   *
   * 支持简单变量、this、类型名、System.out，以及静态可确定返回类型的方法调用链。
   */
  fun memberCompletions(
    filePath: String,
    position: Int,
    receiver: String,
    prefix: String,
  ): List<DynamicCompletionItem> {
    val index = requireIndex(filePath, position)
    val receiverType = inferExpressionType(index, receiver, position) ?: return emptyList()

    val normalizedType = receiverType.readableType()
    val builtinMembers = JAVA_BUILTIN_MEMBERS[normalizedType.simpleName()].orEmpty()
    val workspaceType = (normalizedType as? JavaType.Named)?.let { type -> resolveType(index, type) }
    val customMembers = workspaceType
      ?.let(::membersForType)
      .orEmpty()
      .map { member -> member.toCompletionItem() }

    return (builtinMembers + customMembers)
      .filter { item -> item.label.startsWith(prefix) && item.label != prefix }
      .distinctBy(DynamicCompletionItem::label)
  }

  /** 查询光标绑定的定义；歧义或外部符号没有伪造位置。 */
  fun definition(filePath: String, position: Int): DynamicSymbolDefinition? {
    return resolveAt(filePath, position)?.toDefinition()
  }

  /** 聚合定义之外的全部已解析工作区引用。 */
  fun references(filePath: String, position: Int): DynamicSymbolReferencesResult? {
    val canonical = resolveAt(filePath, position) ?: return null
    return DynamicSymbolReferencesResult(
      symbol = canonical.toDefinition(),
      references = locationsFor(canonical)
        .filterNot { location -> location == canonical.definitionLocation() },
    )
  }

  /** 校验名称、歧义与文件重命名边界后生成一次性文本修改。 */
  fun rename(
    filePath: String,
    position: Int,
    newName: String,
  ): DynamicRenameResult? {
    val canonical = resolveAt(filePath, position) ?: return null
    val definition = canonical.toDefinition()
    if (newName == canonical.symbol.name) return DynamicRenameResult(symbol = definition)
    if (!newName.isJavaIdentifier()) {
      return rejected(definition, "invalid_identifier", "'$newName' 不是合法的 Java 标识符。")
    }
    if (newName in JAVA_RESERVED_WORDS) {
      return rejected(definition, "reserved_word", "'$newName' 是 Java 保留字，不能用于重命名。")
    }
    if (canonical.symbol.kind == JavaSymbolKind.METHOD && canonical.index.isOverloaded(canonical.symbol)) {
      return rejected(
        definition,
        "ambiguous_overload",
        "当前方法存在同名重载，轻量索引不会猜测调用绑定。",
      )
    }
    if (canonical.index.hasRenameConflict(canonical.symbol, newName)) {
      return rejected(
        definition,
        "name_conflict",
        "'$newName' 会与已有 Java 绑定冲突或捕获引用。",
      )
    }
    if (canonical.symbol.kind == JavaSymbolKind.TYPE) {
      val packagePrefix = canonical.index.packageName.takeIf(String::isNotEmpty)
      val qualifiedName = listOfNotNull(packagePrefix, newName).joinToString(".")
      if (typesByQualifiedName[qualifiedName].orEmpty().any { symbol -> !symbol.sameBinding(canonical) }) {
        return rejected(definition, "name_conflict", "'$newName' 已在同一 package 中声明。")
      }
    }

    val edits = locationsFor(canonical)
      .map { location ->
        DynamicSourceEdit(
          filePath = location.filePath,
          edit = DynamicTextEdit(
            from = location.range.from,
            to = location.range.to,
            replacement = newName,
          ),
        )
      }
    val fileRenames = if (canonical.symbol.isPublicTopLevelType) {
      listOf(DynamicFileRename(canonical.filePath, canonical.filePath.renameJavaFile(newName)))
    } else {
      emptyList()
    }
    return DynamicRenameResult(symbol = definition, edits = edits, fileRenames = fileRenames)
  }

  /** 将拒绝原因转换为协议结果，禁止调用方应用任何修改。 */
  private fun rejected(
    definition: DynamicSymbolDefinition,
    code: String,
    message: String,
  ): DynamicRenameResult {
    return DynamicRenameResult(
      symbol = definition,
      rejectionCode = code,
      rejectionMessage = message,
    )
  }

  /** 查找光标位置对应的本地绑定、类型引用、import 或 receiver 成员。 */
  private fun resolveAt(filePath: String, position: Int): JavaWorkspaceSymbol? {
    val index = requireIndex(filePath, position)
    index.localOccurrenceAt(position)?.let { occurrence ->
      return JavaWorkspaceSymbol(filePath, index, occurrence.symbol)
    }
    index.importAt(position)?.let { import ->
      return resolveQualifiedType(import.qualifiedName)
    }
    index.typeReferenceAt(position)?.let { reference ->
      return resolveType(index, reference.name)
    }
    index.memberReferenceAt(position)?.let { reference ->
      return resolveMember(index, reference)
    }
    return null
  }

  /** 解析一个 receiver 成员；类型或成员不唯一时保持 null。 */
  private fun resolveMember(
    index: JavaSemanticIndex,
    reference: JavaMemberReference,
  ): JavaWorkspaceSymbol? {
    val receiverType = inferExpressionType(index, reference.receiver, reference.range.from) ?: return null
    val namedType = receiverType.readableType() as? JavaType.Named ?: return null
    val type = resolveType(index, namedType) ?: return null
    val candidates = membersForType(type)
      .filter { member -> member.binding.symbol.name == reference.name }
    if (reference.argumentSources == null) {
      return candidates.singleOrNull()?.binding
    }
    val argumentTypes = reference.argumentSources.map { argument ->
      inferExpressionType(index, argument, reference.range.from) ?: return null
    }
    val match = resolveJavaOverload(
      candidates = candidates.map { member ->
        JavaCallableCandidate(
          value = member,
          typeParameters = member.typeParameters,
          parameterTypes = member.parameterTypes,
          returnType = member.returnType,
          isVararg = member.isVararg,
        )
      },
      argumentTypes = argumentTypes,
      relations = typeRelations(index),
    ) ?: return null
    return match.value.binding
  }

  /** 按当前文件显式 import、同 package 和当前文件声明解析简单类型名。 */
  private fun resolveType(index: JavaSemanticIndex, simpleName: String): JavaWorkspaceSymbol? {
    index.symbols
      .filter { symbol -> symbol.kind == JavaSymbolKind.TYPE && symbol.name == simpleName }
      .singleOrNull()
      ?.let { symbol ->
        val filePath = indexes.entries.firstOrNull { entry -> entry.value === index }?.key ?: return@let
        return JavaWorkspaceSymbol(filePath, index, symbol)
      }

    val imported = index.imports
      .filter { import -> !import.isStatic && !import.isWildcard && import.simpleName == simpleName }
    if (imported.size == 1) return resolveQualifiedType(imported.single().qualifiedName)
    if (imported.size > 1) return null

    val qualifiedName = listOfNotNull(index.packageName.takeIf(String::isNotEmpty), simpleName)
      .joinToString(".")
    return resolveQualifiedType(qualifiedName)
  }

  /** 解析带泛型实参的类型，并建立其类型形参代换。 */
  private fun resolveType(index: JavaSemanticIndex, type: JavaType.Named): JavaResolvedType? {
    val binding = resolveType(index, type.name) ?: return null
    val substitutions = binding.symbol.typeParameters
      .map(JavaTypeParameter::name)
      .zip(type.arguments)
      .toMap()
    return JavaResolvedType(binding, substitutions)
  }

  /** 仅在工作区中存在唯一声明时解析全限定类型。 */
  private fun resolveQualifiedType(qualifiedName: String): JavaWorkspaceSymbol? {
    return typesByQualifiedName[qualifiedName].orEmpty().singleOrNull()
  }

  /**
   * 合并类型与工作区内可唯一解析父类型的成员。
   *
   * [visited] 同时防止非法源码中的循环继承造成递归；工作区父类会传播泛型代换，外部父类不猜测。
   */
  private fun membersForType(
    type: JavaResolvedType,
    visited: MutableSet<String> = mutableSetOf(),
  ): List<JavaResolvedMember> {
    val identity = type.binding.filePath + ":" + type.binding.symbol.id + ":" +
      type.substitutions.values.joinToString(",", transform = JavaType::render)
    if (!visited.add(identity)) return emptyList()
    val members = buildList {
      type.binding.index.membersOf(type.binding.symbol.name).forEach { member ->
        add(
          JavaResolvedMember(
            binding = JavaWorkspaceSymbol(type.binding.filePath, type.binding.index, member),
            returnType = member.receiverType?.substitute(type.substitutions),
            parameterTypes = member.parameterTypes.map { parameter -> parameter.substitute(type.substitutions) },
            typeParameters = member.typeParameters,
            isVararg = member.isVararg,
          ),
        )
      }
      type.binding.symbol.superTypes.forEach { superTypeReference ->
        val substituted = superTypeReference.substitute(type.substitutions) as? JavaType.Named ?: return@forEach
        val superType = resolveType(type.binding.index, substituted) ?: return@forEach
        addAll(membersForType(superType, visited))
      }
    }
    return members.distinctBy { member -> member.overrideKey() }
  }

  /** 为工作区类型关系提供带泛型代换的直接父类型。 */
  private fun typeRelations(index: JavaSemanticIndex): JavaTypeRelations {
    return JavaTypeRelations { type ->
      val resolved = resolveType(index, type) ?: return@JavaTypeRelations emptyList()
      resolved.binding.symbol.superTypes.mapNotNull { superType ->
        superType.substitute(resolved.substitutions) as? JavaType.Named
      }
    }
  }

  /**
   * 推断教学代码中的常见表达式类型。
   *
   * 支持字面量、局部变量、new、数组访问以及连续字段/方法调用；超出静态可判定范围时返回 null，
   * 不会为了补全或跳转猜测运行时类型。
   */
  private fun inferExpressionType(
    index: JavaSemanticIndex,
    expression: String,
    position: Int,
  ): JavaType? {
    val text = expression.trim().removeRedundantParentheses()
    if (text.isEmpty()) return null
    when {
      text == "this" -> return index.enclosingTypeName(position)?.let(JavaType::Named)
      text == "System.out" || text == "System.err" -> return JavaType.Named("PrintStream")
      text == "null" -> return JavaType.Null
      text == "true" || text == "false" -> return JavaType.Named("boolean")
      text.startsWith('"') && text.endsWith('"') -> return JavaType.Named("String")
      text.startsWith('\'') && text.endsWith('\'') -> return JavaType.Named("char")
      text.matches(Regex("[-+]?\\d+[lL]")) -> return JavaType.Named("long")
      text.matches(Regex("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)[fF]")) -> return JavaType.Named("float")
      text.matches(Regex("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)[dD]?")) -> return JavaType.Named("double")
      text.matches(Regex("[-+]?\\d+")) -> return JavaType.Named("int")
      text.startsWith("new ") -> {
        val typeSource = text.removePrefix("new ").substringBeforeTopLevel('(').substringBeforeTopLevel('[').trim()
        return parseJavaType(typeSource).takeUnless { type -> type == JavaType.Unknown }
      }
      text.endsWith(']') -> {
        val bracket = text.findMatchingOpening(text.lastIndex, '[', ']')
        if (bracket > 0) {
          return (inferExpressionType(index, text.substring(0, bracket), position) as? JavaType.Array)?.component
        }
      }
    }

    parseTrailingCall(text)?.let { call ->
      val receiverType = if (call.receiver == null) {
        index.enclosingTypeName(position)?.let(JavaType::Named)
      } else {
        inferExpressionType(index, call.receiver, position)
      } ?: return null
      val arguments = call.arguments.map { argument ->
        inferExpressionType(index, argument, position) ?: return null
      }
      builtinMethodReturnType(receiverType, call.name)?.let { return it }
      val namedReceiver = receiverType.readableType() as? JavaType.Named ?: return null
      val resolvedType = resolveType(index, namedReceiver) ?: return null
      val candidates = membersForType(resolvedType)
        .filter { member -> member.binding.symbol.kind == JavaSymbolKind.METHOD }
        .filter { member -> member.binding.symbol.name == call.name }
        .map { member ->
          JavaCallableCandidate(
            value = member,
            typeParameters = member.typeParameters,
            parameterTypes = member.parameterTypes,
            returnType = member.returnType,
            isVararg = member.isVararg,
          )
        }
      return resolveJavaOverload(candidates, arguments, typeRelations(index))?.returnType
    }

    val memberSeparator = text.lastTopLevelDot()
    if (memberSeparator > 0) {
      val ownerType = inferExpressionType(index, text.substring(0, memberSeparator), position)
        ?.readableType() as? JavaType.Named ?: return null
      val resolvedType = resolveType(index, ownerType) ?: return null
      return membersForType(resolvedType)
        .filter { member -> member.binding.symbol.kind != JavaSymbolKind.METHOD }
        .singleOrNull { member -> member.binding.symbol.name == text.substring(memberSeparator + 1) }
        ?.returnType
    }

    index.receiverType(position, text)?.let { return it }
    if (text.firstOrNull()?.isUpperCase() == true) return parseJavaType(text)
    return null
  }

  /** 返回同一 canonical binding 的定义与全部引用位置。 */
  private fun locationsFor(canonical: JavaWorkspaceSymbol): List<DynamicSourceLocation> {
    return buildList {
      canonical.index.occurrencesFor(canonical.symbol).forEach { occurrence ->
        add(DynamicSourceLocation(canonical.filePath, occurrence.range))
      }
      if (canonical.symbol.kind == JavaSymbolKind.TYPE) {
        canonical.index.constructorsOf(canonical.symbol.name).forEach { constructor ->
          add(DynamicSourceLocation(canonical.filePath, constructor.definition))
        }
        indexes.forEach { (path, index) ->
          index.imports.forEach { import ->
            if (resolveQualifiedType(import.qualifiedName)?.sameBinding(canonical) == true) {
              add(DynamicSourceLocation(path, import.simpleRange))
            }
          }
          index.typeReferences.forEach { reference ->
            if (resolveType(index, reference.name)?.sameBinding(canonical) == true) {
              add(DynamicSourceLocation(path, reference.range))
            }
          }
        }
      }
      if (canonical.symbol.kind == JavaSymbolKind.FIELD || canonical.symbol.kind == JavaSymbolKind.METHOD) {
        indexes.forEach { (path, index) ->
          index.memberReferences.forEach { reference ->
            if (resolveMember(index, reference)?.sameBinding(canonical) == true) {
              add(DynamicSourceLocation(path, reference.range))
            }
          }
        }
      }
    }.distinctBy { location -> Triple(location.filePath, location.range.from, location.range.to) }
      .sortedWith(compareBy(DynamicSourceLocation::filePath, { location -> location.range.from }))
  }

  /** 校验文件与 UTF-16 光标位置。 */
  private fun requireIndex(filePath: String, position: Int): JavaSemanticIndex {
    val source = files[filePath] ?: throw IllegalArgumentException("Workspace does not contain '$filePath'.")
    require(position in 0..source.length) { "position must be inside source." }
    return checkNotNull(indexes[filePath])
  }
}

/** 工作区内带文件身份的 Java 绑定。 */
private data class JavaWorkspaceSymbol(
  val filePath: String,
  val index: JavaSemanticIndex,
  val symbol: JavaSymbol,
) {
  fun toDefinition(): DynamicSymbolDefinition = symbol.toDefinition(filePath)

  fun definitionLocation(): DynamicSourceLocation = DynamicSourceLocation(filePath, symbol.definition)

  fun sameBinding(other: JavaWorkspaceSymbol): Boolean {
    return filePath == other.filePath && symbol.id == other.symbol.id
  }
}

/** 带实际泛型实参的工作区类型。 */
private data class JavaResolvedType(
  val binding: JavaWorkspaceSymbol,
  val substitutions: Map<String, JavaType>,
)

/** 类型实参代换后的成员签名，供补全、继承去重与重载决议共同使用。 */
private data class JavaResolvedMember(
  val binding: JavaWorkspaceSymbol,
  val returnType: JavaType?,
  val parameterTypes: List<JavaType>,
  val typeParameters: List<JavaTypeParameter>,
  val isVararg: Boolean,
) {
  /** override 只覆盖同签名父方法，名字相同但参数不同的 overload 必须保留。 */
  fun overrideKey(): String {
    val symbol = binding.symbol
    return if (symbol.kind == JavaSymbolKind.METHOD) {
      symbol.name + "(" + parameterTypes.joinToString(",", transform = JavaType::render) + ")"
    } else {
      symbol.kind.name + ":" + symbol.name
    }
  }

  /** 将已代换的实际返回类型展示给补全调用方。 */
  fun toCompletionItem(): DynamicCompletionItem {
    val original = binding.symbol.toCompletionItem()
    return original.copy(
      detail = returnType?.let { type -> binding.symbol.kind.protocolName + ": " + type.render() }
        ?: original.detail,
    )
  }
}

/**
 * 一份 Java 源文件的作用域、声明和未决跨文件引用索引。
 *
 * 索引分两遍遍历：先建立作用域与 Definition，再绑定普通 Identifier；类型与 receiver 成员保留到
 * 工作区层解析，从而能使用其他文件的 package/import 信息。
 */
private class JavaSemanticIndex(
  private val source: String,
  private val tree: LezerTree,
) {
  private var nextSymbolId = 0
  private val rootScope = JavaScope(
    kind = JavaScopeKind.FILE,
    from = 0,
    to = source.length,
    parent = null,
    ownerTypeName = null,
  )
  val symbols = mutableListOf<JavaSymbol>()
  private val occurrences = mutableListOf<JavaOccurrence>()
  val typeReferences = mutableListOf<JavaTypeReference>()
  val memberReferences = mutableListOf<JavaMemberReference>()
  val imports = mutableListOf<JavaImport>()
  val packageName: String

  init {
    buildScopes(tree.topNode, rootScope)
    collectDefinitions(tree.topNode)
    packageName = collectPackageName(tree.topNode)
    collectImports(tree.topNode)
    collectReferences(tree.topNode)
    occurrences.sortBy { occurrence -> occurrence.range.from }
    typeReferences.sortBy { reference -> reference.range.from }
    memberReferences.sortBy { reference -> reference.range.from }
  }

  /** 返回字符串、字符、注释中的光标是否应屏蔽补全。 */
  fun isCompletionSuppressed(position: Int): Boolean {
    var node: LezerSyntaxNode? = tree.resolveInner(position, -1)
    while (node != null) {
      if (node.name in COMPLETION_SUPPRESSED_NODES) return true
      node = node.parent
    }
    return false
  }

  /** 返回当前作用域可见的声明与 Java 关键字。 */
  fun lexicalCompletions(position: Int, prefix: String): List<DynamicCompletionItem> {
    val visible = linkedMapOf<String, JavaSymbol>()
    var scope: JavaScope? = rootScope.innermost(position)
    var distance = 0
    while (scope != null) {
      scope.symbols
        .asReversed()
        .filter { symbol -> symbol.visibleFrom <= position }
        .forEach { symbol ->
          if (symbol.name !in visible) visible[symbol.name] = symbol.copy(boost = 100 - distance)
        }
      distance += 1
      scope = scope.parent
    }
    return buildList {
      visible.values
        .filter { symbol -> symbol.name.startsWith(prefix) && symbol.name != prefix }
        .mapTo(this, JavaSymbol::toCompletionItem)
      JAVA_KEYWORDS
        .filter { keyword -> keyword.startsWith(prefix) && keyword != prefix }
        .forEach { keyword ->
          add(
            DynamicCompletionItem(
              label = keyword,
              detail = "Java 关键字",
              type = "keyword",
              apply = keyword,
            ),
          )
        }
    }
  }

  /** 根据局部声明、this 或静态类型名推断简单 receiver 类型。 */
  fun receiverType(position: Int, receiver: String): JavaType? {
    if (receiver.endsWith("[]")) return JavaType.Named("array")
    val simpleReceiver = receiver.substringAfterLast('.')
    return rootScope.innermost(position)
      .resolve(simpleReceiver, position)
      ?.receiverType
  }

  /** 返回指定类型直接声明的字段、方法和嵌套类型。 */
  fun membersOf(typeName: String): List<JavaSymbol> {
    return symbols.filter { symbol ->
      symbol.ownerTypeName == typeName &&
        symbol.kind in MEMBER_SYMBOL_KINDS
    }
  }

  /** 返回类型内与类名绑定的显式构造器，供类型重命名同步更新声明。 */
  fun constructorsOf(typeName: String): List<JavaSymbol> {
    return symbols.filter { symbol ->
      symbol.kind == JavaSymbolKind.CONSTRUCTOR && symbol.ownerTypeName == typeName
    }
  }

  /** 返回当前位置所在的最内层类型。 */
  fun enclosingTypeName(position: Int): String? {
    var scope: JavaScope? = rootScope.innermost(position)
    while (scope != null) {
      scope.ownerTypeName?.let { return it }
      scope = scope.parent
    }
    return null
  }

  /** 返回位置覆盖的本地绑定。 */
  fun localOccurrenceAt(position: Int): JavaOccurrence? {
    return occurrences
      .filter { occurrence -> occurrence.range.containsPosition(position) }
      .minByOrNull { occurrence -> occurrence.range.to - occurrence.range.from }
  }

  /** 返回位置覆盖的显式 import 简单类型名。 */
  fun importAt(position: Int): JavaImport? {
    return imports.firstOrNull { import -> import.simpleRange.containsPosition(position) }
  }

  /** 返回位置覆盖的工作区类型引用。 */
  fun typeReferenceAt(position: Int): JavaTypeReference? {
    return typeReferences.firstOrNull { reference -> reference.range.containsPosition(position) }
  }

  /** 返回位置覆盖的 receiver 成员引用。 */
  fun memberReferenceAt(position: Int): JavaMemberReference? {
    return memberReferences.firstOrNull { reference -> reference.range.containsPosition(position) }
  }

  /** 返回同一文件绑定的定义和引用。 */
  fun occurrencesFor(symbol: JavaSymbol): List<JavaOccurrence> {
    return occurrences.filter { occurrence -> occurrence.symbol.id == symbol.id }
  }

  /** 判断方法声明是否属于重载集合，供保守的批量重命名策略使用。 */
  fun isOverloaded(symbol: JavaSymbol): Boolean {
    return symbol.kind == JavaSymbolKind.METHOD &&
      symbols.count { candidate ->
        candidate.kind == JavaSymbolKind.METHOD &&
          candidate.ownerTypeName == symbol.ownerTypeName &&
          candidate.name == symbol.name
      } > 1
  }

  /** 保守检查新名称是否会与声明作用域或任一引用位置的可见绑定冲突。 */
  fun hasRenameConflict(symbol: JavaSymbol, newName: String): Boolean {
    if (symbol.scope.symbols.any { candidate -> candidate.id != symbol.id && candidate.name == newName }) {
      return true
    }
    return occurrencesFor(symbol).any { occurrence ->
      rootScope.innermost(occurrence.range.from)
        .resolve(newName, occurrence.range.from)
        ?.id
        ?.let { id -> id != symbol.id }
        ?: false
    }
  }

  /** 第一遍遍历：建立类型、方法和块作用域。 */
  private fun buildScopes(node: LezerSyntaxNode, scope: JavaScope) {
    val activeScope = when {
      node.name in TYPE_SCOPE_NODE_NAMES -> {
        JavaScope(
          kind = JavaScopeKind.TYPE,
          from = node.from,
          to = node.to,
          parent = scope,
          ownerTypeName = node.directChild("Definition")?.text()?.takeIf(String::isNotBlank),
        ).also(scope.children::add)
      }
      node.name in METHOD_SCOPE_NODE_NAMES -> {
        JavaScope(
          kind = JavaScopeKind.METHOD,
          from = node.from,
          to = node.to,
          parent = scope,
          ownerTypeName = scope.nearestTypeName(),
        ).also(scope.children::add)
      }
      node.name in BLOCK_SCOPE_NODE_NAMES -> {
        JavaScope(
          kind = JavaScopeKind.BLOCK,
          from = node.from,
          to = node.to,
          parent = scope,
          ownerTypeName = scope.nearestTypeName(),
        ).also(scope.children::add)
      }
      else -> scope
    }

    var child = node.firstChild
    while (child != null) {
      buildScopes(child, activeScope)
      child = child.nextSibling
    }
  }

  /** 第二遍第一阶段：将 Lezer Definition 分类为 Java 声明。 */
  private fun collectDefinitions(node: LezerSyntaxNode) {
    if (node.name == "Definition") {
      classifyDefinition(node)?.let { definition ->
        val symbol = JavaSymbol(
          id = nextSymbolId++,
          name = node.text(),
          kind = definition.kind,
          definition = node.range(),
          scope = definition.scope,
          visibleFrom = definition.visibleFrom,
          receiverType = definition.receiverType,
          ownerTypeName = definition.ownerTypeName,
          typeParameters = definition.typeParameters,
          parameterTypes = definition.parameterTypes,
          superTypes = definition.superTypes,
          isStatic = definition.isStatic,
          isPublic = definition.isPublic,
          isVararg = definition.isVararg,
          isPublicTopLevelType = definition.isPublicTopLevelType,
        )
        symbols += symbol
        definition.scope.symbols += symbol
        occurrences += JavaOccurrence(node.range(), symbol, isDefinition = true)
      }
    }
    var child = node.firstChild
    while (child != null) {
      collectDefinitions(child)
      child = child.nextSibling
    }
  }

  /** 根据 Definition 的最近声明祖先确定绑定种类、作用域与静态类型。 */
  private fun classifyDefinition(node: LezerSyntaxNode): JavaDefinition? {
    val owner = node.nearestAncestor(DECLARATION_OWNER_NAMES) ?: return null
    val innermost = rootScope.innermost(node.from)
    return when (owner.name) {
      "ClassDeclaration", "InterfaceDeclaration", "EnumDeclaration", "AnnotationTypeDeclaration" -> {
        val target = innermost.parent ?: rootScope
        JavaDefinition(
          kind = JavaSymbolKind.TYPE,
          scope = target,
          visibleFrom = target.from,
          receiverType = JavaType.Named(node.text()),
          ownerTypeName = target.nearestTypeName(),
          typeParameters = owner.declaredTypeParameters(),
          superTypes = owner.declaredSuperTypes(),
          isStatic = owner.headerBefore(node).containsWord("static"),
          isPublic = owner.headerBefore(node).containsWord("public"),
          isPublicTopLevelType = target.kind == JavaScopeKind.FILE &&
            owner.headerBefore(node).containsWord("public"),
        )
      }
      "MethodDeclaration" -> {
        val target = innermost.nearestScope(JavaScopeKind.TYPE) ?: return null
        JavaDefinition(
          kind = JavaSymbolKind.METHOD,
          scope = target,
          visibleFrom = target.from,
          receiverType = owner.declaredTypeBefore(node),
          ownerTypeName = target.ownerTypeName,
          typeParameters = owner.declaredTypeParameters(),
          parameterTypes = owner.declaredParameterTypes(),
          isStatic = owner.headerBefore(node).containsWord("static"),
          isPublic = owner.headerBefore(node).containsWord("public"),
          isVararg = owner.hasVarargParameter(),
        )
      }
      "ConstructorDeclaration" -> {
        val target = innermost.nearestScope(JavaScopeKind.TYPE) ?: return null
        JavaDefinition(
          kind = JavaSymbolKind.CONSTRUCTOR,
          scope = target,
          visibleFrom = target.from,
          receiverType = target.ownerTypeName?.let(JavaType::Named),
          ownerTypeName = target.ownerTypeName,
          typeParameters = owner.declaredTypeParameters(),
          parameterTypes = owner.declaredParameterTypes(),
          isVararg = owner.hasVarargParameter(),
        )
      }
      "FormalParameter", "SpreadParameter", "CatchFormalParameter" -> {
        val target = if (owner.name == "CatchFormalParameter") {
          innermost.nearestScope(JavaScopeKind.BLOCK)
        } else {
          innermost.nearestScope(JavaScopeKind.METHOD)
            ?: innermost.nearestScope(JavaScopeKind.BLOCK)
        } ?: return null
        JavaDefinition(
          kind = JavaSymbolKind.PARAMETER,
          scope = target,
          visibleFrom = target.from,
          receiverType = owner.declaredTypeBefore(node),
          ownerTypeName = target.nearestTypeName(),
        )
      }
      "TypeParameter" -> {
        val target = innermost.nearestScope(JavaScopeKind.METHOD)
          ?: innermost.nearestScope(JavaScopeKind.TYPE)
          ?: return null
        JavaDefinition(
          kind = JavaSymbolKind.TYPE_PARAMETER,
          scope = target,
          visibleFrom = target.from,
          receiverType = JavaType.Named(node.text()),
          ownerTypeName = target.nearestTypeName(),
        )
      }
      "EnumConstant" -> {
        val target = innermost.nearestScope(JavaScopeKind.TYPE) ?: return null
        JavaDefinition(
          kind = JavaSymbolKind.FIELD,
          scope = target,
          visibleFrom = target.from,
          receiverType = target.ownerTypeName?.let(JavaType::Named),
          ownerTypeName = target.ownerTypeName,
          isStatic = true,
        )
      }
      "VariableDeclarator" -> classifyVariable(owner, node, innermost)
      else -> null
    }
  }

  /** 区分字段、参数和局部变量，并提取声明中的静态类型。 */
  private fun classifyVariable(
    owner: LezerSyntaxNode,
    definition: LezerSyntaxNode,
    innermost: JavaScope,
  ): JavaDefinition? {
    val declaration = owner.nearestAncestor(VARIABLE_CONTAINER_NODE_NAMES) ?: owner
    val isParameter = declaration.name in PARAMETER_CONTAINER_NODE_NAMES
    val isField = declaration.name == "FieldDeclaration" || declaration.name == "ConstantDeclaration"
    val target = when {
      isParameter -> innermost.nearestScope(JavaScopeKind.METHOD)
        ?: innermost.nearestScope(JavaScopeKind.BLOCK)
      isField -> innermost.nearestScope(JavaScopeKind.TYPE)
      else -> innermost
    } ?: return null
    return JavaDefinition(
      kind = when {
        isParameter -> JavaSymbolKind.PARAMETER
        isField -> JavaSymbolKind.FIELD
        else -> JavaSymbolKind.VARIABLE
      },
      scope = target,
      visibleFrom = if (isField || isParameter) target.from else definition.to,
      receiverType = declaration.declaredTypeBefore(definition),
      ownerTypeName = target.nearestTypeName(),
      isStatic = isField && declaration.headerBefore(definition).containsWord("static"),
    )
  }

  /** 第二遍第二阶段：绑定普通标识符，并保留类型与 receiver 成员供工作区解析。 */
  private fun collectReferences(node: LezerSyntaxNode) {
    when {
      node.name == "TypeName" && node.firstChild == null -> collectTypeReference(node)
      node.name == "Identifier" && node.firstChild == null -> collectIdentifierReference(node)
    }
    var child = node.firstChild
    while (child != null) {
      collectReferences(child)
      child = child.nextSibling
    }
  }

  /** 收集最后一级类型名，跳过 package/import 与限定名中的包路径片段。 */
  private fun collectTypeReference(node: LezerSyntaxNode) {
    if (node.hasAncestor("PackageDeclaration") || node.hasAncestor("ImportDeclaration")) return
    val scoped = node.nearestAncestor(setOf("ScopedTypeName"))
    if (scoped != null && node.to != scoped.to) return
    if (occurrences.any { occurrence -> occurrence.range == node.range() }) return
    typeReferences += JavaTypeReference(name = node.text().substringAfterLast('.'), range = node.range())
  }

  /** 绑定局部标识符；点号右侧成员延迟到工作区层按 receiver 类型解析。 */
  private fun collectIdentifierReference(node: LezerSyntaxNode) {
    val range = node.range()
    if (occurrences.any { occurrence -> occurrence.range == range }) return
    if (node.hasAncestor("PackageDeclaration") || node.hasAncestor("ImportDeclaration")) return
    if (node.hasAncestor("Annotation") || node.hasAncestor("MarkerAnnotation")) return

    val parentName = node.parent?.name
    val receiver = source.receiverForMember(range.from)
    if (receiver != null && parentName != "ScopedIdentifier") {
      memberReferences += JavaMemberReference(
        receiver = receiver,
        name = node.text(),
        range = range,
        argumentSources = source.callArgumentSourcesAfter(range.to),
      )
      return
    }

    val kindHint = if (parentName == "MethodName") JavaSymbolKind.METHOD else null
    if (kindHint == JavaSymbolKind.METHOD) {
      memberReferences += JavaMemberReference(
        receiver = "this",
        name = node.text(),
        range = range,
        argumentSources = source.callArgumentSourcesAfter(range.to),
      )
      return
    }
    val symbol = rootScope.innermost(range.from).resolve(node.text(), range.from, kindHint) ?: return
    occurrences += JavaOccurrence(range, symbol, isDefinition = false)
  }

  /** 读取 package 声明；缺省包使用空字符串。 */
  private fun collectPackageName(node: LezerSyntaxNode): String {
    var child = node.firstChild
    while (child != null) {
      if (child.name == "PackageDeclaration") {
        return PACKAGE_REGEX.find(child.text())?.groupValues?.get(1).orEmpty()
      }
      child = child.nextSibling
    }
    return ""
  }

  /** 收集显式 import 的全限定名与最后一级类型名位置。 */
  private fun collectImports(node: LezerSyntaxNode) {
    if (node.name == "ImportDeclaration") {
      val text = node.text()
      val match = IMPORT_REGEX.find(text)
      if (match != null) {
        val qualifiedName = match.groupValues[2]
        val isWildcard = match.groupValues[3].isNotEmpty()
        val simpleName = if (isWildcard) "*" else qualifiedName.substringAfterLast('.')
        val relativeStart = if (isWildcard) text.lastIndexOf('*') else text.lastIndexOf(simpleName)
        imports += JavaImport(
          qualifiedName = qualifiedName,
          simpleName = simpleName,
          simpleRange = DynamicTextRange(node.from + relativeStart, node.from + relativeStart + simpleName.length),
          isStatic = match.groupValues[1].isNotEmpty(),
          isWildcard = isWildcard,
        )
      }
    }
    var child = node.firstChild
    while (child != null) {
      collectImports(child)
      child = child.nextSibling
    }
  }

  /** 返回节点源码。 */
  private fun LezerSyntaxNode.text(): String = source.substring(from, to)

  /** 返回节点范围。 */
  private fun LezerSyntaxNode.range(): DynamicTextRange = DynamicTextRange(from, to)

  /** 读取声明头部，避免把方法体中的修饰符误判为当前声明。 */
  private fun LezerSyntaxNode.headerBefore(definition: LezerSyntaxNode): String {
    return source.substring(from, definition.from)
  }

  /** 从声明节点中选取 Definition 前的最外层类型节点并规范化。 */
  private fun LezerSyntaxNode.declaredTypeBefore(definition: LezerSyntaxNode): JavaType? {
    val candidate = descendants()
      .filter { child -> child.to <= definition.from && child.name in TYPE_NODE_NAMES }
      .filterNot { child -> child.hasAncestor("Annotation") || child.hasAncestor("MarkerAnnotation") }
      .minWithOrNull(compareBy<LezerSyntaxNode> { child -> child.from }.thenByDescending { child -> child.to })
      ?: return null
    return parseJavaType(candidate.text()).takeUnless { type -> type == JavaType.Unknown }
  }

  /** 提取类或接口声明中的直接父类型，并保留泛型实参供继承代换。 */
  private fun LezerSyntaxNode.declaredSuperTypes(): List<JavaType> {
    return descendants()
      .filter { child -> child.name in SUPER_TYPE_CONTAINER_NODE_NAMES }
      .filter { container ->
        val declaringType = container.nearestAncestor(TYPE_SCOPE_NODE_NAMES)
        declaringType?.from == from && declaringType.to == to
      }
      .flatMap { container ->
        container.text()
          .removePrefix("extends")
          .removePrefix("implements")
          .trim()
          .splitJavaTopLevel(',')
          .asSequence()
      }
      .map(String::trim)
      .filter(String::isNotEmpty)
      .map(::parseJavaType)
      .filterNot { type -> type == JavaType.Unknown }
      .distinctBy(JavaType::render)
      .toList()
  }

  /** 提取当前类型、方法或构造器直接声明的泛型形参。 */
  private fun LezerSyntaxNode.declaredTypeParameters(): List<JavaTypeParameter> {
    val container = descendants()
      .firstOrNull { child ->
        child.name == "TypeParameters" &&
          child.nearestAncestor(TYPE_SCOPE_NODE_NAMES + METHOD_SCOPE_NODE_NAMES)?.let { owner ->
            owner.from == from && owner.to == to
          } == true
      }
      ?: return emptyList()
    return parseJavaTypeParameters(container.text())
  }

  /** 按声明顺序提取方法或构造器参数类型。 */
  private fun LezerSyntaxNode.declaredParameterTypes(): List<JavaType> {
    return descendants()
      .filter { child -> child.name in PARAMETER_CONTAINER_NODE_NAMES }
      .filter { parameter ->
        parameter.nearestAncestor(METHOD_SCOPE_NODE_NAMES)?.let { owner ->
          owner.from == from && owner.to == to
        } == true
      }
      .mapNotNull { parameter ->
        val definition = parameter.descendants().firstOrNull { child -> child.name == "Definition" }
          ?: return@mapNotNull null
        val declaredType = parameter.declaredTypeBefore(definition) ?: return@mapNotNull null
        if (parameter.name == "SpreadParameter" && declaredType !is JavaType.Array) {
          JavaType.Array(declaredType)
        } else {
          declaredType
        }
      }
      .toList()
  }

  /** 判断最后一个参数是否使用 Java 可变参数语法。 */
  private fun LezerSyntaxNode.hasVarargParameter(): Boolean {
    return descendants().any { child ->
      child.name == "SpreadParameter" &&
        child.nearestAncestor(METHOD_SCOPE_NODE_NAMES)?.let { owner ->
          owner.from == from && owner.to == to
        } == true
    }
  }

  /** 深度优先返回当前节点的全部后代。 */
  private fun LezerSyntaxNode.descendants(): Sequence<LezerSyntaxNode> = sequence {
    var child = firstChild
    while (child != null) {
      yield(child)
      yieldAll(child.descendants())
      child = child.nextSibling
    }
  }

  /** 返回指定名称的直接子节点。 */
  private fun LezerSyntaxNode.directChild(name: String): LezerSyntaxNode? {
    var child = firstChild
    while (child != null) {
      if (child.name == name) return child
      child = child.nextSibling
    }
    return null
  }

  /** 返回最近的指定祖先。 */
  private fun LezerSyntaxNode.nearestAncestor(names: Set<String>): LezerSyntaxNode? {
    var current = parent
    while (current != null) {
      if (current.name in names) return current
      current = current.parent
    }
    return null
  }

  /** 判断节点是否位于指定语法结构中。 */
  private fun LezerSyntaxNode.hasAncestor(name: String): Boolean {
    var current = parent
    while (current != null) {
      if (current.name == name) return true
      current = current.parent
    }
    return false
  }
}

/** 作用域树中的一个 Java 词法边界。 */
private class JavaScope(
  val kind: JavaScopeKind,
  val from: Int,
  val to: Int,
  val parent: JavaScope?,
  val ownerTypeName: String?,
) {
  val children = mutableListOf<JavaScope>()
  val symbols = mutableListOf<JavaSymbol>()

  /** 返回覆盖位置的最内层作用域。 */
  fun innermost(position: Int): JavaScope {
    return children.firstOrNull { child -> position in child.from..child.to }
      ?.innermost(position)
      ?: this
  }

  /** 向外查找最近的指定作用域。 */
  fun nearestScope(expected: JavaScopeKind): JavaScope? {
    var current: JavaScope? = this
    while (current != null) {
      if (current.kind == expected) return current
      current = current.parent
    }
    return null
  }

  /** 返回最近的外层类型名。 */
  fun nearestTypeName(): String? {
    var current: JavaScope? = this
    while (current != null) {
      current.ownerTypeName?.let { return it }
      current = current.parent
    }
    return null
  }

  /**
   * 按 Java 遮蔽顺序解析简单名称。
   *
   * 不带调用实参信息的纯词法查询不会选择重载；调用表达式由工作区层使用参数类型解析。
   */
  fun resolve(
    name: String,
    position: Int,
    kindHint: JavaSymbolKind? = null,
  ): JavaSymbol? {
    var current: JavaScope? = this
    while (current != null) {
      val candidates = current.symbols.filter { symbol ->
        symbol.name == name &&
          symbol.visibleFrom <= position &&
          (kindHint == null || symbol.kind == kindHint)
      }
      if (candidates.isNotEmpty()) {
        return candidates.singleOrNull()
          ?: candidates.lastOrNull { symbol -> symbol.kind != JavaSymbolKind.METHOD }
      }
      current = current.parent
    }
    return null
  }
}

/** Java 轻量索引中的作用域类型。 */
private enum class JavaScopeKind {
  FILE,
  TYPE,
  METHOD,
  BLOCK,
}

/** 可参与补全、跳转、引用和重命名的符号种类。 */
private enum class JavaSymbolKind(val protocolName: String) {
  TYPE("class"),
  TYPE_PARAMETER("typeParameter"),
  FIELD("property"),
  METHOD("method"),
  CONSTRUCTOR("constructor"),
  PARAMETER("variable"),
  VARIABLE("variable"),
}

/** Java 源码中的一个 canonical 词法绑定。 */
private data class JavaSymbol(
  val id: Int,
  val name: String,
  val kind: JavaSymbolKind,
  val definition: DynamicTextRange,
  val scope: JavaScope,
  val visibleFrom: Int,
  val receiverType: JavaType?,
  val ownerTypeName: String?,
  val typeParameters: List<JavaTypeParameter> = emptyList(),
  val parameterTypes: List<JavaType> = emptyList(),
  val superTypes: List<JavaType> = emptyList(),
  val isStatic: Boolean = false,
  val isPublic: Boolean = false,
  val isVararg: Boolean = false,
  val isPublicTopLevelType: Boolean = false,
  val boost: Int = 100,
) {
  /** 转换为编辑器补全条目。 */
  fun toCompletionItem(): DynamicCompletionItem {
    return DynamicCompletionItem(
      label = name,
      detail = receiverType?.let { type -> kind.protocolName + ": " + type.render() } ?: kind.protocolName,
      type = kind.protocolName,
      boost = boost,
      apply = name,
    )
  }

  /** 转换为工作区定义协议。 */
  fun toDefinition(filePath: String): DynamicSymbolDefinition {
    return DynamicSymbolDefinition(
      name = name,
      kind = kind.protocolName,
      definition = DynamicSourceLocation(filePath, definition),
    )
  }
}

/** 判断词法方法符号是否是编辑器应展示的 Java 程序入口。 */
private fun JavaSymbol.isJavaMainTarget(): Boolean {
  if (kind != JavaSymbolKind.METHOD || name != "main" || !isPublic || !isStatic) return false
  if (parameterTypes.isEmpty()) return true
  // Lezer 的 void 是关键字节点，不会进入类型节点集合，因此轻量索引用 null 表示 void 返回值。
  val returnsVoid = receiverType == null || (receiverType as? JavaType.Named)?.name == "void"
  val arguments = parameterTypes.singleOrNull() as? JavaType.Array
  val componentName = (arguments?.component as? JavaType.Named)?.name
  return returnsVoid && componentName?.substringAfterLast('.') == "String"
}

/** 分类 Definition 时使用的中间结果。 */
private data class JavaDefinition(
  val kind: JavaSymbolKind,
  val scope: JavaScope,
  val visibleFrom: Int,
  val receiverType: JavaType?,
  val ownerTypeName: String?,
  val typeParameters: List<JavaTypeParameter> = emptyList(),
  val parameterTypes: List<JavaType> = emptyList(),
  val superTypes: List<JavaType> = emptyList(),
  val isStatic: Boolean = false,
  val isPublic: Boolean = false,
  val isVararg: Boolean = false,
  val isPublicTopLevelType: Boolean = false,
)

/** 一个已经绑定到单文件声明的定义或引用。 */
private data class JavaOccurrence(
  val range: DynamicTextRange,
  val symbol: JavaSymbol,
  val isDefinition: Boolean,
)

/** 等待工作区 package/import 解析的类型引用。 */
private data class JavaTypeReference(
  val name: String,
  val range: DynamicTextRange,
)

/** 等待工作区 receiver 类型解析的成员引用。 */
private data class JavaMemberReference(
  val receiver: String,
  val name: String,
  val range: DynamicTextRange,
  val argumentSources: List<String>?,
)

/** Java import 的最小索引模型。 */
private data class JavaImport(
  val qualifiedName: String,
  val simpleName: String,
  val simpleRange: DynamicTextRange,
  val isStatic: Boolean,
  val isWildcard: Boolean,
)

/** UTF-16 区间是否覆盖光标；允许光标紧邻标识符末尾。 */
private fun DynamicTextRange.containsPosition(position: Int): Boolean {
  return position in from until to || (position == to && to > from)
}

/** 读取工作区中的指定源码。 */
private fun DynamicLanguageWorkspace.requireSource(filePath: String): String {
  return files.firstOrNull { file -> file.path == filePath }?.source
    ?: throw IllegalArgumentException("Workspace does not contain '$filePath'.")
}

/** 从光标向前定位当前 Java 标识符的替换起点。 */
private fun String.findJavaIdentifierStart(position: Int): Int {
  var index = position
  while (index > 0 && this[index - 1].isJavaIdentifierPart()) index -= 1
  return index
}

/** 提取补全点号左侧的简单变量、类型或短成员链。 */
private fun String.receiverBefore(identifierStart: Int): String? {
  val dotIndex = identifierStart - 1
  if (dotIndex < 0 || this[dotIndex] != '.') return null
  val start = expressionStartBefore(dotIndex)
  return substring(start, dotIndex).takeIf(String::isNotEmpty)
}

/**
 * 判断标识符前是否是成员访问点号，并提取其 receiver。
 *
 * import/package 的限定名称由调用方提前排除；普通成员访问的右侧名称会按左侧静态类型解析。
 */
private fun String.receiverForMember(identifierStart: Int): String? {
  var cursor = identifierStart - 1
  while (cursor >= 0 && this[cursor].isWhitespace()) cursor -= 1
  if (cursor < 0 || this[cursor] != '.') return null
  val end = cursor
  val start = expressionStartBefore(end)
  return substring(start, end).trim().takeIf(String::isNotEmpty)
}

/** 从成员点号向前跨过配对括号，定位可供轻量推断的表达式起点。 */
private fun String.expressionStartBefore(endExclusive: Int): Int {
  var cursor = endExclusive - 1
  var parenthesisDepth = 0
  var bracketDepth = 0
  while (cursor >= 0) {
    when (val character = this[cursor]) {
      ')' -> parenthesisDepth += 1
      '(' -> if (parenthesisDepth > 0) parenthesisDepth -= 1 else break
      ']' -> bracketDepth += 1
      '[' -> if (bracketDepth > 0) bracketDepth -= 1 else break
      ';', '{', '}', '=', ',', ':' -> if (parenthesisDepth == 0 && bracketDepth == 0) break
      else -> if (parenthesisDepth == 0 && bracketDepth == 0 && character.isWhitespace()) break
    }
    cursor -= 1
  }
  return cursor + 1
}

/** 若标识符后紧跟调用括号，则按顶层逗号返回实参源码；普通字段访问返回 null。 */
private fun String.callArgumentSourcesAfter(identifierEnd: Int): List<String>? {
  var opening = identifierEnd
  while (getOrNull(opening)?.isWhitespace() == true) opening += 1
  if (getOrNull(opening) != '(') return null
  var depth = 0
  var closing = opening
  while (closing < length) {
    when (this[closing]) {
      '(' -> depth += 1
      ')' -> {
        depth -= 1
        if (depth == 0) {
          val body = substring(opening + 1, closing).trim()
          return if (body.isEmpty()) emptyList() else body.splitJavaTopLevel(',').map(String::trim)
        }
      }
    }
    closing += 1
  }
  return null
}

/** 判断字符串是否为当前轻量索引支持的 Java 标识符。 */
private fun String.isJavaIdentifier(): Boolean {
  return isNotEmpty() && first().isJavaIdentifierStart() && drop(1).all(Char::isJavaIdentifierPart)
}

/** 保留工作区相对目录，仅替换 public 顶层类型对应的 Java 文件名。 */
private fun String.renameJavaFile(newTypeName: String): String {
  val directory = substringBeforeLast('/', missingDelimiterValue = "")
  return if (directory.isEmpty()) "$newTypeName.java" else "$directory/$newTypeName.java"
}

private fun Char.isJavaIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$'

private fun Char.isJavaIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

/** 删除泛型实参、包限定和数组空白，得到补全目录使用的简单静态类型。 */
private fun JavaType.simpleName(): String {
  return when (this) {
    is JavaType.Named -> name
    is JavaType.Array -> "array"
    is JavaType.Wildcard -> readableType().simpleName()
    JavaType.Null -> "null"
    JavaType.Unknown -> ""
  }
}

/** 只剥离完整包裹表达式的外层括号，避免改变调用参数中的分组。 */
private fun String.removeRedundantParentheses(): String {
  var result = trim()
  while (result.startsWith('(') && result.endsWith(')') && result.findMatchingOpening(result.lastIndex, '(', ')') == 0) {
    result = result.substring(1, result.lastIndex).trim()
  }
  return result
}

/** 从闭合括号反向找到同层开放括号，找不到时返回 -1。 */
private fun String.findMatchingOpening(closing: Int, openingChar: Char, closingChar: Char): Int {
  var depth = 0
  for (index in closing downTo 0) {
    when (this[index]) {
      closingChar -> depth += 1
      openingChar -> {
        depth -= 1
        if (depth == 0) return index
      }
    }
  }
  return -1
}

/** 返回首个不位于泛型、调用或数组内部的分隔符之前文本。 */
private fun String.substringBeforeTopLevel(separator: Char): String {
  var angleDepth = 0
  var parenthesisDepth = 0
  var bracketDepth = 0
  forEachIndexed { index, character ->
    when (character) {
      '<' -> angleDepth += 1
      '>' -> if (angleDepth > 0) angleDepth -= 1
      '(' -> parenthesisDepth += 1
      ')' -> if (parenthesisDepth > 0) parenthesisDepth -= 1
      '[' -> bracketDepth += 1
      ']' -> if (bracketDepth > 0) bracketDepth -= 1
      separator -> if (angleDepth == 0 && parenthesisDepth == 0 && bracketDepth == 0) {
        return substring(0, index)
      }
    }
  }
  return this
}

/** 返回最后一个不位于调用或数组内部的成员点号。 */
private fun String.lastTopLevelDot(): Int {
  var parenthesisDepth = 0
  var bracketDepth = 0
  for (index in lastIndex downTo 0) {
    when (this[index]) {
      ')' -> parenthesisDepth += 1
      '(' -> if (parenthesisDepth > 0) parenthesisDepth -= 1
      ']' -> bracketDepth += 1
      '[' -> if (bracketDepth > 0) bracketDepth -= 1
      '.' -> if (parenthesisDepth == 0 && bracketDepth == 0) return index
    }
  }
  return -1
}

/** 解析以方法调用结尾的表达式；支持 receiver.method(args) 与当前类 method(args)。 */
private fun parseTrailingCall(expression: String): JavaParsedCall? {
  if (!expression.endsWith(')')) return null
  val opening = expression.findMatchingOpening(expression.lastIndex, '(', ')')
  if (opening <= 0) return null
  val methodEnd = opening
  var methodStart = methodEnd
  while (methodStart > 0 && expression[methodStart - 1].isJavaIdentifierPart()) methodStart -= 1
  val name = expression.substring(methodStart, methodEnd)
  if (name.isEmpty()) return null
  val receiver = expression.substring(0, methodStart).trim().removeSuffix(".").trim().takeIf(String::isNotEmpty)
  val body = expression.substring(opening + 1, expression.lastIndex).trim()
  return JavaParsedCall(
    receiver = receiver,
    name = name,
    arguments = if (body.isEmpty()) emptyList() else body.splitJavaTopLevel(',').map(String::trim),
  )
}

/** 常见 JDK 泛型容器与字符串方法的返回类型，避免为了轻量提示引入完整 classpath。 */
private fun builtinMethodReturnType(
  receiver: JavaType,
  methodName: String,
): JavaType? {
  val named = receiver.readableType() as? JavaType.Named ?: return null
  return when (named.name) {
    "List", "ArrayList" -> when (methodName) {
      "get", "set", "remove" -> named.arguments.firstOrNull() ?: JavaType.Named("Object")
      "size", "indexOf" -> JavaType.Named("int")
      "contains", "add", "addAll", "isEmpty" -> JavaType.Named("boolean")
      else -> null
    }
    "Collection", "Set", "HashSet" -> when (methodName) {
      "size" -> JavaType.Named("int")
      "contains", "add", "addAll", "isEmpty" -> JavaType.Named("boolean")
      else -> null
    }
    "Map", "HashMap" -> when (methodName) {
      "get", "getOrDefault", "put", "remove" -> named.arguments.getOrNull(1) ?: JavaType.Named("Object")
      "size" -> JavaType.Named("int")
      "containsKey", "containsValue", "isEmpty" -> JavaType.Named("boolean")
      else -> null
    }
    "String" -> when (methodName) {
      "substring", "trim", "toLowerCase", "toUpperCase" -> JavaType.Named("String")
      "charAt" -> JavaType.Named("char")
      "length", "indexOf", "lastIndexOf" -> JavaType.Named("int")
      "contains", "equals", "isEmpty", "startsWith", "endsWith" -> JavaType.Named("boolean")
      "split" -> JavaType.Array(JavaType.Named("String"))
      else -> null
    }
    "StringBuilder" -> when (methodName) {
      "append", "insert", "delete", "reverse" -> named
      "length" -> JavaType.Named("int")
      "toString" -> JavaType.Named("String")
      else -> null
    }
    else -> null
  }
}

/** 轻量表达式推断使用的方法调用拆分结果。 */
private data class JavaParsedCall(
  val receiver: String?,
  val name: String,
  val arguments: List<String>,
)

/** 判断声明头是否包含完整修饰符单词。 */
private fun String.containsWord(word: String): Boolean {
  return Regex("(^|\\W)" + Regex.escape(word) + "(\\W|$)").containsMatchIn(this)
}

private val TYPE_SCOPE_NODE_NAMES = setOf(
  "ClassDeclaration",
  "InterfaceDeclaration",
  "EnumDeclaration",
  "AnnotationTypeDeclaration",
)
private val METHOD_SCOPE_NODE_NAMES = setOf(
  "MethodDeclaration",
  "ConstructorDeclaration",
  "LambdaExpression",
)
private val BLOCK_SCOPE_NODE_NAMES = setOf(
  "Block",
  "ForStatement",
  "EnhancedForStatement",
  "CatchClause",
  "TryWithResourcesStatement",
)
private val DECLARATION_OWNER_NAMES = TYPE_SCOPE_NODE_NAMES + setOf(
  "MethodDeclaration",
  "ConstructorDeclaration",
  "FormalParameter",
  "SpreadParameter",
  "CatchFormalParameter",
  "TypeParameter",
  "EnumConstant",
  "VariableDeclarator",
)
private val VARIABLE_CONTAINER_NODE_NAMES = setOf(
  "FieldDeclaration",
  "ConstantDeclaration",
  "LocalVariableDeclaration",
  "FormalParameter",
  "SpreadParameter",
  "CatchFormalParameter",
  "EnhancedForStatement",
  "Resource",
)
private val PARAMETER_CONTAINER_NODE_NAMES = setOf(
  "FormalParameter",
  "SpreadParameter",
  "CatchFormalParameter",
)
private val TYPE_NODE_NAMES = setOf(
  "ArrayType",
  "GenericType",
  "ScopedTypeName",
  "TypeName",
  "PrimitiveType",
)
private val SUPER_TYPE_CONTAINER_NODE_NAMES = setOf(
  "Superclass",
  "SuperInterfaces",
  "ExtendsInterfaces",
)
private val COMPLETION_SUPPRESSED_NODES = setOf(
  "StringLiteral",
  "CharacterLiteral",
  "TextBlock",
  "LineComment",
  "BlockComment",
)
private val MEMBER_SYMBOL_KINDS = setOf(
  JavaSymbolKind.FIELD,
  JavaSymbolKind.METHOD,
  JavaSymbolKind.TYPE,
)
private val JAVA_RESERVED_WORDS = JAVA_KEYWORDS.toSet() + setOf("true", "false", "null", "_")
private val PACKAGE_REGEX = Regex("""package\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;""")
private val IMPORT_REGEX = Regex(
  """import\s+(static\s+)?([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)(\.\*)?\s*;""",
)
