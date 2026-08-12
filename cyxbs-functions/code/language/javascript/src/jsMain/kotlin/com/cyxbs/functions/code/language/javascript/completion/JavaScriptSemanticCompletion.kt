package com.cyxbs.functions.code.language.javascript.completion

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicRenameResult
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceLocation
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolReferencesResult
import com.cyxbs.functions.code.language.js.bridge.DynamicTextEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicTextRange
import com.cyxbs.functions.code.language.lezer.LezerSyntaxNode
import com.cyxbs.functions.code.language.lezer.LezerTree

/**
 * 面向一个 JavaScript 多文件工作区编辑会话的轻量语义补全器。
 *
 * 它使用 Lezer 错误恢复语法树建立词法作用域和声明索引，不执行用户代码，也不尝试覆盖 JavaScript
 * 的动态元编程语义。当前支持函数、参数、变量、类、导入符号，以及常见内置对象和可静态推断的
 * receiver 成员。每个文件按路径和最近源码独立缓存索引，Runtime 关闭后会随语言服务一同释放。
 */
internal class JavaScriptSemanticSession(
  private val syntaxTree: (filePath: String, source: String) -> LezerTree,
) {
  private val indexes = mutableMapOf<String, CachedJavaScriptSemanticIndex>()

  /**
   * 查询 [position] 位置可见的符号或 receiver 成员。
   *
   * @param workspace 包含未保存内容的完整工作区快照。
   * @param filePath 光标所在文件的工作区相对路径。
   * @param position 光标 UTF-16 偏移。
   * @param explicit 是否由用户主动触发；自动触发时空前缀不会弹出候选。
   * @return 可替换当前标识符前缀的补全结果；字符串、注释或未知 receiver 中返回 null。
   */
  fun complete(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    val source = workspace.requireSource(filePath)
    require(position in 0..source.length) { "position must be inside source." }
    val semanticIndex = indexFor(filePath, source)
    if (semanticIndex.isCompletionSuppressed(position)) return null

    val from = source.findIdentifierStart(position)
    val prefix = source.substring(from, position)
    val receiver = source.receiverBefore(from)
    if (!explicit && prefix.isEmpty() && receiver == null) return null
    val options = if (receiver == null) {
      semanticIndex.lexicalCompletions(position, prefix)
    } else {
      workspaceIndex(workspace).memberCompletions(filePath, position, receiver, prefix)
    }
    return options.takeIf { it.isNotEmpty() }?.let {
      DynamicCompletionResult(from = from, to = position, options = options)
    }
  }

  /** 查询 [position] 所在词法符号的定义。 */
  fun definition(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolDefinition? {
    return workspaceIndex(workspace).definition(filePath, position)
  }

  /** 查询 [position] 所在词法符号的工作区引用。 */
  fun references(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): DynamicSymbolReferencesResult? {
    return workspaceIndex(workspace).references(filePath, position)
  }

  /** 为 [position] 所在词法符号生成不会改变静态绑定关系的重命名修改。 */
  fun rename(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    newName: String,
  ): DynamicRenameResult? {
    return workspaceIndex(workspace).rename(filePath, position, newName)
  }

  /** 删除已不存在的文件索引，其余文件按内容继续复用。 */
  fun retainFiles(filePaths: Set<String>) {
    indexes.keys.retainAll(filePaths)
  }

  /** 仅在对应文件源码变化时重建语义索引。 */
  private fun indexFor(filePath: String, source: String): JavaScriptSemanticIndex {
    val cached = indexes[filePath]
    if (cached == null || cached.source != source) {
      indexes[filePath] = CachedJavaScriptSemanticIndex(
        source = source,
        index = JavaScriptSemanticIndex(source, syntaxTree(filePath, source)),
      )
    }
    return checkNotNull(indexes[filePath]).index
  }

  /** 用当前文件快照构建轻量跨文件关联层，单文件索引仍可复用。 */
  private fun workspaceIndex(workspace: DynamicLanguageWorkspace): JavaScriptWorkspaceSemanticIndex {
    require(workspace.files.map { file -> file.path }.distinct().size == workspace.files.size) {
      "Workspace file paths must be unique."
    }
    return JavaScriptWorkspaceSemanticIndex(
      files = workspace.files.associate { file -> file.path to file.source },
      indexes = workspace.files.associate { file -> file.path to indexFor(file.path, file.source) },
    )
  }
}

/** 按源码内容命中的单文件语义索引缓存。 */
private data class CachedJavaScriptSemanticIndex(
  val source: String,
  val index: JavaScriptSemanticIndex,
)

/**
 * 将各文件已缓存的词法索引按 ES Module 导入、导出关系临时关联。
 *
 * 该层不重新解析源码；工作区文件增删或依赖改变时重建成本仅为线性遍历绑定。
 */
private class JavaScriptWorkspaceSemanticIndex(
  private val files: Map<String, String>,
  private val indexes: Map<String, JavaScriptSemanticIndex>,
) {

  /**
   * 返回当前 receiver 的本地成员，并在静态类型来自 import 时跟随目标类补齐跨文件成员。
   *
   * 这里只关联 `new ImportedClass()` 可确定的实例类型；运行时改写原型等动态行为不作猜测。
   */
  fun memberCompletions(
    filePath: String,
    position: Int,
    receiver: String,
    prefix: String,
  ): List<DynamicCompletionItem> {
    val index = requireIndex(filePath, position)
    val receiverType = index.receiverType(position, receiver) ?: return emptyList()
    val localMembers = index.memberCompletions(receiverType, prefix)
    val importedType = index.imports.firstOrNull { binding ->
      binding.localSymbol.name == receiverType
    }?.let { binding ->
      resolveImport(filePath, index, binding)?.target
    }
    val importedMembers = importedType?.index
      ?.memberCompletions(importedType.symbol.name, prefix)
      .orEmpty()
    return (localMembers + importedMembers).distinctBy(DynamicCompletionItem::label)
  }

  /** 导入符号优先跳到目标模块的导出定义，无法解析时仍返回本地导入绑定。 */
  fun definition(filePath: String, position: Int): DynamicSymbolDefinition? {
    val index = requireIndex(filePath, position)
    val symbol = index.importedNameAt(position)?.localSymbol ?: index.symbolAt(position) ?: return null
    val local = JavaScriptWorkspaceSymbol(filePath, index, symbol)
    return resolveImportedTarget(local)?.toDefinition() ?: local.toDefinition()
  }

  /** 聚合导出符号本地引用、导入声明与导入文件中的本地引用。 */
  fun references(filePath: String, position: Int): DynamicSymbolReferencesResult? {
    val index = requireIndex(filePath, position)
    val importedName = index.importedNameAt(position)
    val symbol = importedName?.localSymbol ?: index.symbolAt(position) ?: return null
    val local = JavaScriptWorkspaceSymbol(filePath, index, symbol)
    val canonical = if (importedName != null || local.importBinding()?.isAliased != true) {
      resolveImportedTarget(local) ?: local
    } else {
      // 别名、默认和命名空间导入拥有独立的本地绑定，从本地名称查询时不扩散到远端公开符号。
      local
    }
    return DynamicSymbolReferencesResult(
      symbol = canonical.toDefinition(),
      references = linkedReferences(canonical),
    )
  }

  /**
   * 对本地绑定执行安全重命名，并在公开导出名跟随变化时同步所有命名导入。
   *
   * 默认导入和命名空间导入的本地名称与远端导出名无关，因此只修改当前文件。
   */
  fun rename(
    filePath: String,
    position: Int,
    newName: String,
  ): DynamicRenameResult? {
    val index = requireIndex(filePath, position)
    val importedName = index.importedNameAt(position)
    val symbol = importedName?.localSymbol ?: index.symbolAt(position) ?: return null
    val local = JavaScriptWorkspaceSymbol(filePath, index, symbol)
    val localImport = local.importBinding()
    if (importedName == null && localImport?.isAliased == true) {
      // 调用方选用的本地别名与远端公开名无关，只修改当前文件中的本地绑定及引用。
      return index.rename(filePath, symbol, newName)
    }
    val canonical = resolveImportedTarget(local) ?: local
    val canonicalRename = canonical.index.rename(canonical.filePath, canonical.symbol, newName)
    if (!canonicalRename.isSuccess) return canonicalRename

    val propagatedImports = importsForPublicRename(canonical)
    val importerResults = mutableListOf<DynamicRenameResult>()
    for (imported in propagatedImports) {
      if (!imported.binding.isAliased) {
        val result = imported.index.rename(
          filePath = imported.filePath,
          symbol = imported.binding.localSymbol,
          newName = newName,
        )
        if (!result.isSuccess) {
          return result.copy(symbol = canonical.toDefinition())
        }
        importerResults += result
      }
    }

    val edits = buildList {
      addAll(canonicalRename.edits)
      propagatedImports.forEach { imported ->
        if (imported.binding.isAliased) {
          add(
            DynamicSourceEdit(
              filePath = imported.filePath,
              edit = DynamicTextEdit(
                from = imported.binding.importedRange.from,
                to = imported.binding.importedRange.to,
                replacement = newName,
              ),
            ),
          )
        }
      }
      importerResults.forEach { result -> addAll(result.edits) }
    }.distinctBy { sourceEdit ->
      Triple(sourceEdit.filePath, sourceEdit.edit.from, sourceEdit.edit.to)
    }.sortedWith(compareBy(DynamicSourceEdit::filePath, { sourceEdit -> sourceEdit.edit.from }))
    return canonicalRename.copy(edits = edits)
  }

  /** 将一个公开导出符号的定义与所有直接导入点展开为去重位置。 */
  private fun linkedReferences(canonical: JavaScriptWorkspaceSymbol): List<DynamicSourceLocation> {
    val canonicalDefinition = canonical.symbol.definition
    return buildList {
      canonical.index.locations(canonical.filePath, canonical.symbol).forEach { location ->
        if (location.range != canonicalDefinition) add(location)
      }
      importsFor(canonical).forEach { imported ->
        if (imported.binding.isAliased) {
          add(DynamicSourceLocation(imported.filePath, imported.binding.importedRange))
        }
        addAll(imported.index.locations(imported.filePath, imported.binding.localSymbol))
      }
    }.distinctBy { location -> Triple(location.filePath, location.range.from, location.range.to) }
      .sortedWith(compareBy(DynamicSourceLocation::filePath, { location -> location.range.from }))
  }

  /** 只有未起别名的公开导出名才会随本地符号重命名而变化。 */
  private fun importsForPublicRename(
    canonical: JavaScriptWorkspaceSymbol,
  ): List<ResolvedJavaScriptImport> {
    val publicNames = canonical.index.exports
      .filter { export ->
        export.localSymbol.sameBinding(canonical.symbol) && export.exportedNameFollowsLocal
      }
      .mapTo(mutableSetOf()) { export -> export.exportedName }
    if (publicNames.isEmpty()) return emptyList()
    return importsFor(canonical).filter { imported -> imported.binding.importedName in publicNames }
  }

  /** 找到所有直接指向指定导出绑定的 import。 */
  private fun importsFor(canonical: JavaScriptWorkspaceSymbol): List<ResolvedJavaScriptImport> {
    return buildList {
      indexes.forEach { (importerPath, importerIndex) ->
        importerIndex.imports.forEach { binding ->
          val resolved = resolveImport(importerPath, importerIndex, binding)
          if (resolved?.target?.sameBinding(canonical) == true) add(resolved.imported)
        }
      }
    }
  }

  /** 若 [symbol] 是导入绑定，则跟随模块路径和导出名找到真实定义。 */
  private fun resolveImportedTarget(symbol: JavaScriptWorkspaceSymbol): JavaScriptWorkspaceSymbol? {
    val binding = symbol.importBinding() ?: return null
    if (binding.importedName == MODULE_NAMESPACE_IMPORT_NAME) return null
    return resolveImport(symbol.filePath, symbol.index, binding)?.target
  }

  /** 解析单个 import 绑定，对缺失文件或缺失导出保持 null 而不猜测。 */
  private fun resolveImport(
    importerPath: String,
    importerIndex: JavaScriptSemanticIndex,
    binding: JavaScriptImportBinding,
  ): ResolvedImportTarget? {
    val targetPath = resolveModulePath(importerPath, binding.moduleSpecifier, files.keys) ?: return null
    val targetIndex = indexes[targetPath] ?: return null
    val exported = targetIndex.exports.firstOrNull { export -> export.exportedName == binding.importedName }
      ?: return null
    return ResolvedImportTarget(
      target = JavaScriptWorkspaceSymbol(targetPath, targetIndex, exported.localSymbol),
      imported = ResolvedJavaScriptImport(importerPath, importerIndex, binding),
    )
  }

  /** 校验当前文件与光标区间，再返回对应索引。 */
  private fun requireIndex(filePath: String, position: Int): JavaScriptSemanticIndex {
    val source = files[filePath] ?: throw IllegalArgumentException("Workspace does not contain '$filePath'.")
    require(position in 0..source.length) { "position must be inside source." }
    return checkNotNull(indexes[filePath])
  }
}

/** 工作区内带文件身份的词法绑定。 */
private data class JavaScriptWorkspaceSymbol(
  val filePath: String,
  val index: JavaScriptSemanticIndex,
  val symbol: JavaScriptSymbol,
) {
  fun toDefinition(): DynamicSymbolDefinition = symbol.toDefinition(filePath)

  fun importBinding(): JavaScriptImportBinding? = index.imports.firstOrNull { binding ->
    binding.localSymbol.sameBinding(symbol)
  }

  fun sameBinding(other: JavaScriptWorkspaceSymbol): Boolean {
    return filePath == other.filePath && symbol.sameBinding(other.symbol)
  }
}

/** 已解析目标导出的 import 边。 */
private data class ResolvedImportTarget(
  val target: JavaScriptWorkspaceSymbol,
  val imported: ResolvedJavaScriptImport,
)

/** 跨文件传播时使用的 import 绑定及其文件索引。 */
private data class ResolvedJavaScriptImport(
  val filePath: String,
  val index: JavaScriptSemanticIndex,
  val binding: JavaScriptImportBinding,
)

/** 单文件索引中的导入绑定。 */
private data class JavaScriptImportBinding(
  val localSymbol: JavaScriptSymbol,
  val moduleSpecifier: String,
  val importedName: String,
  val importedRange: DynamicTextRange,
  val isAliased: Boolean,
)

/** 单文件索引中的导出名与本地绑定。 */
private data class JavaScriptExportBinding(
  val exportedName: String,
  val localSymbol: JavaScriptSymbol,
  val exportedRange: DynamicTextRange,
  val exportedNameFollowsLocal: Boolean,
)

/** 一份源码的作用域、声明和类成员快照。 */
private class JavaScriptSemanticIndex(
  private val source: String,
  private val tree: LezerTree,
) {
  private val rootScope = JavaScriptScope(
    kind = JavaScriptScopeKind.SCRIPT,
    from = 0,
    to = source.length,
    parent = null,
  )
  private val classMembers = mutableMapOf<String, MutableList<DynamicCompletionItem>>()
  private val occurrences = mutableListOf<JavaScriptOccurrence>()
  private val declarations = mutableListOf<JavaScriptSymbol>()
  val imports = mutableListOf<JavaScriptImportBinding>()
  val exports = mutableListOf<JavaScriptExportBinding>()

  init {
    visit(tree.topNode, rootScope)
    collectReferences(tree.topNode)
    collectModuleBindings(tree.topNode)
    occurrences.sortBy { occurrence -> occurrence.range.from }
  }

  /** 返回光标所在绑定的定义；属性名称和无法静态解析的动态引用返回 null。 */
  fun definition(filePath: String, position: Int): DynamicSymbolDefinition? {
    return symbolAt(position)?.toDefinition(filePath)
  }

  /** 返回光标所在绑定除定义外的所有当前文件引用。 */
  fun references(filePath: String, position: Int): DynamicSymbolReferencesResult? {
    val symbol = symbolAt(position) ?: return null
    return DynamicSymbolReferencesResult(
      symbol = symbol.toDefinition(filePath),
      references = occurrencesFor(symbol)
        .asSequence()
        .filterNot(JavaScriptOccurrence::isDefinition)
        .map { occurrence -> DynamicSourceLocation(filePath, occurrence.range) }
        .toList(),
    )
  }

  /** 校验新名称不会发生词法捕获后，返回基于原始源码位置的一组修改。 */
  fun rename(filePath: String, position: Int, newName: String): DynamicRenameResult? {
    val symbol = symbolAt(position) ?: return null
    return rename(filePath, symbol, newName)
  }

  /** 校验并重命名已解析的本地词法绑定。 */
  fun rename(
    filePath: String,
    symbol: JavaScriptSymbol,
    newName: String,
  ): DynamicRenameResult {
    val definition = symbol.toDefinition(filePath)
    if (newName == symbol.name) return DynamicRenameResult(symbol = definition)
    if (!newName.isJavaScriptIdentifier()) {
      return DynamicRenameResult(
        symbol = definition,
        rejectionCode = RENAME_REJECTION_INVALID_IDENTIFIER,
        rejectionMessage = "'$newName' 不是合法的 JavaScript 标识符。",
      )
    }
    if (newName in JAVASCRIPT_RENAME_RESERVED_WORDS) {
      return DynamicRenameResult(
        symbol = definition,
        rejectionCode = RENAME_REJECTION_RESERVED_WORD,
        rejectionMessage = "'$newName' 是 JavaScript 保留字，不能用于重命名。",
      )
    }
    if (newName in JAVASCRIPT_KNOWN_GLOBAL_NAMES) {
      return DynamicRenameResult(
        symbol = definition,
        rejectionCode = RENAME_REJECTION_NAME_CONFLICT,
        rejectionMessage = "'$newName' 会遮蔽 JavaScript 教学环境的全局符号。",
      )
    }
    if (hasRenameConflict(symbol, newName)) {
      return DynamicRenameResult(
        symbol = definition,
        rejectionCode = RENAME_REJECTION_NAME_CONFLICT,
        rejectionMessage = "'$newName' 会与现有词法绑定冲突或捕获引用。",
      )
    }
    return DynamicRenameResult(
      symbol = definition,
      edits = occurrencesFor(symbol).map { occurrence ->
        DynamicSourceEdit(
          filePath = filePath,
          edit = DynamicTextEdit(
            from = occurrence.range.from,
            to = occurrence.range.to,
            replacement = occurrence.replacement(newName),
          ),
        )
      },
    )
  }

  /** 返回光标所在的本地词法绑定。 */
  fun symbolAt(position: Int): JavaScriptSymbol? = occurrenceAt(position)?.symbol

  /** 返回光标覆盖的命名 import 公开名；默认和命名空间导入没有独立的远端名称区间。 */
  fun importedNameAt(position: Int): JavaScriptImportBinding? {
    return imports.firstOrNull { binding ->
      binding.importedName != MODULE_DEFAULT_EXPORT_NAME &&
        binding.importedName != MODULE_NAMESPACE_IMPORT_NAME &&
        binding.importedRange.containsPosition(position)
    }
  }

  /** 返回同一绑定的定义和引用位置。 */
  fun locations(filePath: String, symbol: JavaScriptSymbol): List<DynamicSourceLocation> {
    return occurrencesFor(symbol).map { occurrence -> DynamicSourceLocation(filePath, occurrence.range) }
  }

  /** 字符串、正则和注释中的普通标识符输入不触发代码补全。 */
  fun isCompletionSuppressed(position: Int): Boolean {
    var node: LezerSyntaxNode? = tree.resolveInner(position, -1)
    while (node != null) {
      if (node.name in COMPLETION_SUPPRESSED_NODES) return true
      node = node.parent
    }
    return false
  }

  /** 返回光标所在作用域可见的声明和 JavaScript 教学常用全局符号。 */
  fun lexicalCompletions(
    position: Int,
    prefix: String,
  ): List<DynamicCompletionItem> {
    val declarations = linkedMapOf<String, JavaScriptSymbol>()
    var scope: JavaScriptScope? = rootScope.innermost(position)
    var scopeDistance = 0
    while (scope != null) {
      scope.symbols
        .asReversed()
        .asSequence()
        .filter { symbol -> position >= symbol.visibleFrom }
        .forEach { symbol ->
          if (symbol.name !in declarations) {
            declarations[symbol.name] = symbol.copy(boost = SYMBOL_BOOST - scopeDistance)
          }
        }
      scopeDistance += 1
      scope = scope.parent
    }

    return buildList {
      declarations.values
        .asSequence()
        .filter { symbol -> symbol.name.startsWith(prefix) && symbol.name != prefix }
        .map(JavaScriptSymbol::toCompletionItem)
        .forEach(::add)
      JAVASCRIPT_GLOBALS
        .asSequence()
        .filter { item -> item.label.startsWith(prefix) && item.label != prefix }
        .filterNot { item -> declarations.containsKey(item.label) }
        .forEach(::add)
      JAVASCRIPT_KEYWORDS
        .asSequence()
        .filter { keyword -> keyword.startsWith(prefix) && keyword != prefix }
        .map { keyword ->
          DynamicCompletionItem(
            label = keyword,
            detail = "JavaScript 关键字",
            type = COMPLETION_TYPE_KEYWORD,
            boost = KEYWORD_BOOST,
            apply = keyword,
          )
        }
        .forEach(::add)
    }
  }

  /** 根据当前作用域中的静态绑定推断 receiver 类型；未知动态类型返回 null。 */
  fun receiverType(
    position: Int,
    receiver: String,
  ): String? {
    return when (receiver) {
      "this" -> enclosingClassName(position)
      else -> BUILTIN_RECEIVER_TYPES[receiver]
        ?: rootScope.innermost(position).visibleSymbol(receiver, position)?.receiverType
    }
  }

  /** 根据已经确定的类型返回内置或当前文件类成员。 */
  fun memberCompletions(receiverType: String, prefix: String): List<DynamicCompletionItem> {
    val members = BUILTIN_MEMBERS[receiverType] ?: classMembers[receiverType].orEmpty()
    return members.filter { item -> item.label.startsWith(prefix) && item.label != prefix }
  }

  /** 深度优先遍历语法树，并在进入语义边界时建立子作用域。 */
  private fun visit(
    node: LezerSyntaxNode,
    scope: JavaScriptScope,
  ) {
    val activeScope = if (node.name in SCOPE_NODE_NAMES && node.name != "Script") {
      JavaScriptScope(
        kind = node.name.toScopeKind(),
        from = node.from,
        to = node.to,
        parent = scope,
      ).also(scope.children::add)
    } else {
      scope
    }

    if (node.name == "VariableDefinition") collectDeclaration(node, activeScope)
    if (node.name == "PatternProperty") collectShorthandPatternDeclaration(node, activeScope)
    if (node.name == "MethodDeclaration" || node.name == "PropertyDeclaration") {
      collectClassMember(node)
    }

    var child = node.firstChild
    while (child != null) {
      visit(child, activeScope)
      child = child.nextSibling
    }
  }

  /** 将一个 Lezer 定义节点归入符合 JavaScript 词法规则的作用域。 */
  private fun collectDeclaration(
    node: LezerSyntaxNode,
    currentScope: JavaScriptScope,
  ) {
    val name = source.substring(node.from, node.to)
    if (!name.isJavaScriptIdentifier()) return
    val parentName = node.parent?.name
    val parameterOwner = node.nearestAncestor("ParamList")
      ?.nearestAncestor(FUNCTION_SCOPE_NODE_NAMES)
    val variableDeclaration = node.nearestAncestor("VariableDeclaration")
    val importDeclaration = node.nearestAncestor("ImportDeclaration")
    val targetScope = when {
      parameterOwner != null -> currentScope.nearestFunctionScope()
      parentName == "FunctionDeclaration" || parentName == "ClassDeclaration" ->
        currentScope.parent ?: rootScope
      parentName == "FunctionExpression" || parentName == "ClassExpression" -> currentScope
      importDeclaration != null -> rootScope
      variableDeclaration != null && source.declarationKind(variableDeclaration, node) == "var" ->
        currentScope.nearestFunctionScope()
      else -> currentScope
    }

    val symbol = when {
      parameterOwner != null -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_VARIABLE,
        detail = "函数参数",
        visibleFrom = currentScope.nearestFunctionScope().from,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )

      parentName == "FunctionDeclaration" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_FUNCTION,
        detail = "函数声明",
        visibleFrom = currentScope.parent?.from ?: 0,
        receiverType = RECEIVER_FUNCTION,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )

      parentName == "FunctionExpression" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_FUNCTION,
        detail = "具名函数表达式",
        visibleFrom = currentScope.from,
        receiverType = RECEIVER_FUNCTION,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )

      parentName == "ClassDeclaration" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_CLASS,
        detail = "类声明",
        visibleFrom = node.to,
        receiverType = name,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )

      parentName == "ClassExpression" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_CLASS,
        detail = "具名类表达式",
        visibleFrom = currentScope.from,
        receiverType = name,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )

      importDeclaration != null -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_VARIABLE,
        detail = "导入符号",
        visibleFrom = rootScope.from,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )

      variableDeclaration != null -> {
        val declarationKind = source.declarationKind(variableDeclaration, node)
        JavaScriptSymbol(
          name = name,
          type = node.inferredCompletionType(),
          detail = "$declarationKind 声明",
          visibleFrom = if (declarationKind == "var") {
            currentScope.nearestFunctionScope().from
          } else {
            node.to
          },
          receiverType = node.inferReceiverType(),
          definition = DynamicTextRange(node.from, node.to),
          bindingScope = targetScope,
        )
      }

      else -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_VARIABLE,
        detail = "局部符号",
        visibleFrom = node.to,
        definition = DynamicTextRange(node.from, node.to),
        bindingScope = targetScope,
      )
    }
    targetScope.symbols += symbol
    declarations += symbol
    occurrences += JavaScriptOccurrence(
      symbol = symbol,
      range = symbol.definition,
      kind = JavaScriptOccurrenceKind.DEFINITION,
    )

    // 类名在类体内部也可见，但在外层仍遵守声明前暂时性死区。
    if (parentName == "ClassDeclaration") {
      currentScope.symbols += symbol.copy(visibleFrom = currentScope.from)
    }
  }

  /**
   * 补齐对象解构中的简写绑定，例如 `{ value }`。
   *
   * 该节点同时表示属性键和新变量；重命名时需展开为 `{ value: newName }` 才能保持读取的属性名。
   */
  private fun collectShorthandPatternDeclaration(
    node: LezerSyntaxNode,
    currentScope: JavaScriptScope,
  ) {
    if (node.directChild(":") != null || node.directChild("VariableDefinition") != null) return
    val nameNode = node.directChild("PropertyName") ?: return
    val name = source.substring(nameNode.from, nameNode.to)
    if (!name.isJavaScriptIdentifier()) return
    val parameterOwner = node.nearestAncestor("ParamList")
      ?.nearestAncestor(FUNCTION_SCOPE_NODE_NAMES)
    val variableDeclaration = node.nearestAncestor("VariableDeclaration")
    if (parameterOwner == null && variableDeclaration == null) return
    val declarationKind = variableDeclaration?.let { source.declarationKind(it, nameNode) }
    val targetScope = when {
      parameterOwner != null -> currentScope.nearestFunctionScope()
      declarationKind == "var" -> currentScope.nearestFunctionScope()
      else -> currentScope
    }
    val symbol = JavaScriptSymbol(
      name = name,
      type = COMPLETION_TYPE_VARIABLE,
      detail = if (parameterOwner != null) "函数参数" else "$declarationKind 声明",
      visibleFrom = if (parameterOwner != null || declarationKind == "var") targetScope.from else nameNode.to,
      definition = DynamicTextRange(nameNode.from, nameNode.to),
      bindingScope = targetScope,
    )
    targetScope.symbols += symbol
    declarations += symbol
    occurrences += JavaScriptOccurrence(
      symbol = symbol,
      range = symbol.definition,
      kind = JavaScriptOccurrenceKind.PATTERN_SHORTHAND_DEFINITION,
    )
  }

  /** 收集类体直接声明的方法和属性，供 `instance.` 与 `this.` 的静态成员补全使用。 */
  private fun collectClassMember(node: LezerSyntaxNode) {
    val classNode = node.nearestAncestor(CLASS_SCOPE_NODE_NAMES) ?: return
    val classNameNode = classNode.directChild("VariableDefinition") ?: return
    val memberNameNode = node.directChild("PropertyDefinition") ?: return
    val className = source.substring(classNameNode.from, classNameNode.to)
    val memberName = source.substring(memberNameNode.from, memberNameNode.to)
    if (!className.isJavaScriptIdentifier() || !memberName.isJavaScriptIdentifier()) return
    val type = if (node.name == "MethodDeclaration") COMPLETION_TYPE_METHOD else COMPLETION_TYPE_PROPERTY
    classMembers.getOrPut(className, ::mutableListOf) += DynamicCompletionItem(
      label = memberName,
      detail = if (type == COMPLETION_TYPE_METHOD) "类方法" else "类属性",
      type = type,
      boost = MEMBER_BOOST,
      apply = memberName,
    )
  }

  /** 查找光标所在的最近具名类，用于 `this.` 补全。 */
  private fun enclosingClassName(position: Int): String? {
    var node: LezerSyntaxNode? = tree.resolveInner(position, -1)
    while (node != null) {
      if (node.name in CLASS_SCOPE_NODE_NAMES) {
        val nameNode = node.directChild("VariableDefinition") ?: return null
        return source.substring(nameNode.from, nameNode.to)
      }
      node = node.parent
    }
    return null
  }

  /** 第二次遍历将普通变量引用和对象字面量简写绑定到第一阶段已经收集完成的声明。 */
  private fun collectReferences(node: LezerSyntaxNode) {
    val occurrenceKind = when {
      node.name == "VariableName" && node.nearestAncestor("ImportDeclaration") == null ->
        JavaScriptOccurrenceKind.REFERENCE
      node.name == "PropertyDefinition" &&
        node.parent?.name == "Property" &&
        node.parent?.directChild(":") == null ->
        JavaScriptOccurrenceKind.OBJECT_SHORTHAND_REFERENCE
      else -> null
    }
    if (occurrenceKind != null) {
      val name = source.substring(node.from, node.to)
      if (name.isJavaScriptIdentifier()) {
        rootScope.innermost(node.from).resolveBinding(name)?.let { symbol ->
          occurrences += JavaScriptOccurrence(
            symbol = symbol,
            range = DynamicTextRange(node.from, node.to),
            kind = occurrenceKind,
          )
        }
      }
    }

    var child = node.firstChild
    while (child != null) {
      collectReferences(child)
      child = child.nextSibling
    }
  }

  /** 收集 ES Module 导入和导出绑定，供工作区关联层跨文件解析。 */
  private fun collectModuleBindings(node: LezerSyntaxNode) {
    when (node.name) {
      "ImportDeclaration" -> collectImportBindings(node)
      "ExportDeclaration" -> collectExportBindings(node)
    }
    var child = node.firstChild
    while (child != null) {
      collectModuleBindings(child)
      child = child.nextSibling
    }
  }

  /** 解析默认、命名和命名空间导入；副作用 import 没有词法绑定。 */
  private fun collectImportBindings(node: LezerSyntaxNode) {
    val children = node.directChildren()
    val moduleSpecifier = children.firstOrNull { child -> child.name == "String" }
      ?.let { child -> source.moduleSpecifier(child) }
      ?: return
    val namespaceDefinition = if (children.any { child -> child.name == "Star" }) {
      children.lastOrNull { child -> child.name == "VariableDefinition" }
    } else {
      null
    }
    if (namespaceDefinition != null) {
      symbolForDefinition(namespaceDefinition)?.let { symbol ->
        imports += JavaScriptImportBinding(
          localSymbol = symbol,
          moduleSpecifier = moduleSpecifier,
          importedName = MODULE_NAMESPACE_IMPORT_NAME,
          importedRange = DynamicTextRange(namespaceDefinition.from, namespaceDefinition.to),
          isAliased = true,
        )
      }
      return
    }

    // ImportGroup 外的 VariableDefinition 是默认导入。
    children.firstOrNull { child -> child.name == "VariableDefinition" }
      ?.let { definition ->
        symbolForDefinition(definition)?.let { symbol ->
          imports += JavaScriptImportBinding(
            localSymbol = symbol,
            moduleSpecifier = moduleSpecifier,
            importedName = MODULE_DEFAULT_EXPORT_NAME,
            importedRange = DynamicTextRange(definition.from, definition.to),
            isAliased = true,
          )
        }
      }

    val group = children.firstOrNull { child -> child.name == "ImportGroup" } ?: return
    val groupChildren = group.directChildren()
    var index = 0
    while (index < groupChildren.size) {
      val child = groupChildren[index]
      val aliasKeyword = groupChildren.getOrNull(index + 1)
      val aliasDefinition = groupChildren.getOrNull(index + 2)
      if (
        child.name == "VariableName" &&
        aliasKeyword?.name == "as" &&
        aliasDefinition?.name == "VariableDefinition"
      ) {
        symbolForDefinition(aliasDefinition)?.let { symbol ->
          imports += JavaScriptImportBinding(
            localSymbol = symbol,
            moduleSpecifier = moduleSpecifier,
            importedName = source.substring(child.from, child.to),
            importedRange = DynamicTextRange(child.from, child.to),
            isAliased = true,
          )
        }
        index += 3
      } else {
        if (child.name == "VariableDefinition") {
          symbolForDefinition(child)?.let { symbol ->
            imports += JavaScriptImportBinding(
              localSymbol = symbol,
              moduleSpecifier = moduleSpecifier,
              importedName = symbol.name,
              importedRange = DynamicTextRange(child.from, child.to),
              isAliased = false,
            )
          }
        }
        index += 1
      }
    }
  }

  /** 解析直接声明导出与本地 export group；跨文件 re-export 暂不猜测中间绑定。 */
  private fun collectExportBindings(node: LezerSyntaxNode) {
    val children = node.directChildren()
    if (children.any { child -> child.name == "from" }) return
    val isDefault = children.any { child -> child.name == "default" }
    val declaration = children.firstOrNull { child ->
      child.name == "VariableDeclaration" || child.name in FUNCTION_SCOPE_NODE_NAMES ||
        child.name in CLASS_SCOPE_NODE_NAMES
    }
    if (declaration != null) {
      val definitions = declaration.directChildren()
        .filter { child -> child.name == "VariableDefinition" }
      definitions.forEach { definition ->
        symbolForDefinition(definition)?.let { symbol ->
          exports += JavaScriptExportBinding(
            exportedName = if (isDefault) MODULE_DEFAULT_EXPORT_NAME else symbol.name,
            localSymbol = symbol,
            exportedRange = DynamicTextRange(definition.from, definition.to),
            exportedNameFollowsLocal = !isDefault,
          )
        }
      }
      return
    }

    val group = children.firstOrNull { child -> child.name == "ExportGroup" } ?: return
    val groupChildren = group.directChildren()
    var index = 0
    while (index < groupChildren.size) {
      val localName = groupChildren[index]
      val aliasKeyword = groupChildren.getOrNull(index + 1)
      val exportedName = groupChildren.getOrNull(index + 2)
      if (localName.name != "VariableName") {
        index += 1
        continue
      }
      val localSymbol = rootScope.innermost(localName.from)
        .resolveBinding(source.substring(localName.from, localName.to))
      if (localSymbol != null) {
        val hasAlias = aliasKeyword?.name == "as" && exportedName?.name == "VariableName"
        val publicNameNode = if (hasAlias) checkNotNull(exportedName) else localName
        exports += JavaScriptExportBinding(
          exportedName = source.substring(publicNameNode.from, publicNameNode.to),
          localSymbol = localSymbol,
          exportedRange = DynamicTextRange(publicNameNode.from, publicNameNode.to),
          exportedNameFollowsLocal = !hasAlias,
        )
        index += if (hasAlias) 3 else 1
      } else {
        index += 1
      }
    }
  }

  /** 根据 Lezer 定义节点找回索引中的词法符号。 */
  private fun symbolForDefinition(node: LezerSyntaxNode): JavaScriptSymbol? {
    return declarations.firstOrNull { symbol ->
      symbol.definition.from == node.from && symbol.definition.to == node.to
    }
  }

  /** 返回覆盖光标的最窄词法符号区间，并允许光标紧邻标识符末尾。 */
  private fun occurrenceAt(position: Int): JavaScriptOccurrence? {
    return occurrences
      .asSequence()
      .filter { occurrence ->
        position in occurrence.range.from until occurrence.range.to ||
          position == occurrence.range.to
      }
      .minByOrNull { occurrence -> occurrence.range.to - occurrence.range.from }
  }

  /** 返回同一声明身份的定义和引用，避免类名在类体内的可见副本产生两套结果。 */
  private fun occurrencesFor(symbol: JavaScriptSymbol): List<JavaScriptOccurrence> {
    return occurrences.filter { occurrence -> occurrence.symbol.sameBinding(symbol) }
  }

  /** 检查目标作用域重名及嵌套作用域捕获，确保重命名不会改变任何已解析引用的绑定。 */
  private fun hasRenameConflict(symbol: JavaScriptSymbol, newName: String): Boolean {
    if (symbol.bindingScope.symbols.any { candidate ->
        candidate.name == newName && !candidate.sameBinding(symbol)
      }
    ) {
      return true
    }
    return occurrencesFor(symbol)
      .asSequence()
      .filterNot(JavaScriptOccurrence::isDefinition)
      .any { occurrence ->
        var scope: JavaScriptScope? = rootScope.innermost(occurrence.range.from)
        while (scope != null && scope !== symbol.bindingScope) {
          if (scope.symbols.any { candidate ->
              candidate.name == newName && !candidate.sameBinding(symbol)
            }
          ) {
            return@any true
          }
          scope = scope.parent
        }
        scope == null
      }
  }

  /** 从变量初始化表达式推断少量稳定的 receiver 类型。 */
  private fun LezerSyntaxNode.inferReceiverType(): String? {
    var sibling = nextSibling
    while (sibling != null && sibling.name in INITIALIZER_SEPARATOR_NODES) {
      sibling = sibling.nextSibling
    }
    return when (sibling?.name) {
      "ArrayExpression" -> RECEIVER_ARRAY
      "String", "TemplateString" -> RECEIVER_STRING
      "Number" -> RECEIVER_NUMBER
      "ObjectExpression" -> RECEIVER_OBJECT
      "FunctionExpression", "ArrowFunction" -> RECEIVER_FUNCTION
      "NewExpression" -> source.substring(sibling.from, sibling.to)
        .let(NEW_EXPRESSION_REGEX::find)
        ?.groupValues
        ?.getOrNull(1)
      else -> null
    }
  }

  /** 箭头函数或函数表达式初始化的变量在补全列表中显示为函数。 */
  private fun LezerSyntaxNode.inferredCompletionType(): String {
    var sibling = nextSibling
    while (sibling != null && sibling.name in INITIALIZER_SEPARATOR_NODES) {
      sibling = sibling.nextSibling
    }
    return if (sibling?.name == "FunctionExpression" || sibling?.name == "ArrowFunction") {
      COMPLETION_TYPE_FUNCTION
    } else {
      COMPLETION_TYPE_VARIABLE
    }
  }
}

/** 一层词法作用域。 */
private class JavaScriptScope(
  val kind: JavaScriptScopeKind,
  val from: Int,
  val to: Int,
  val parent: JavaScriptScope?,
) {
  val children = mutableListOf<JavaScriptScope>()
  val symbols = mutableListOf<JavaScriptSymbol>()

  /** 找到覆盖光标的最内层作用域。 */
  fun innermost(position: Int): JavaScriptScope {
    // Lezer 节点与编辑器协议都使用半开区间；光标位于 `}` 之后时应回到外层作用域。
    return children.firstOrNull { child -> position >= child.from && position < child.to }
      ?.innermost(position)
      ?: this
  }

  /** `var` 和参数归入最近函数、方法或顶层作用域。 */
  fun nearestFunctionScope(): JavaScriptScope {
    var scope = this
    while (scope.kind !in FUNCTION_SCOPE_KINDS) {
      scope = scope.parent ?: return scope
    }
    return scope
  }

  /** 从内向外解析一个当前可见符号，遵循词法遮蔽。 */
  fun visibleSymbol(name: String, position: Int): JavaScriptSymbol? {
    var scope: JavaScriptScope? = this
    while (scope != null) {
      scope.symbols.asReversed().firstOrNull { symbol ->
        symbol.name == name && position >= symbol.visibleFrom
      }?.let { return it }
      scope = scope.parent
    }
    return null
  }

  /**
   * 解析标识符实际绑定，不应用补全使用的声明顺序过滤。
   *
   * `let`、`const` 和 `class` 在声明前仍会遮蔽外层同名绑定，只是运行时处于暂时性死区；定义、
   * 引用和重命名必须指向该内层声明。
   */
  fun resolveBinding(name: String): JavaScriptSymbol? {
    var scope: JavaScriptScope? = this
    while (scope != null) {
      scope.symbols.firstOrNull { symbol -> symbol.name == name }?.let { return it }
      scope = scope.parent
    }
    return null
  }
}

/** 作用域类别只用于决定 `var`、参数和普通块级声明的可见边界。 */
private enum class JavaScriptScopeKind {
  SCRIPT,
  FUNCTION,
  CLASS,
  BLOCK,
  LOOP,
  CATCH,
}

/** 已归一化的 JavaScript 声明。 */
private data class JavaScriptSymbol(
  val name: String,
  val type: String,
  val detail: String,
  val visibleFrom: Int,
  val definition: DynamicTextRange,
  val bindingScope: JavaScriptScope,
  val receiverType: String? = null,
  val boost: Int = SYMBOL_BOOST,
) {
  fun toCompletionItem(): DynamicCompletionItem = DynamicCompletionItem(
    label = name,
    detail = detail,
    type = type,
    boost = boost,
    apply = name,
  )

  /** 转换为带工作区文件位置的动态符号定义。 */
  fun toDefinition(filePath: String): DynamicSymbolDefinition = DynamicSymbolDefinition(
    name = name,
    kind = type,
    definition = DynamicSourceLocation(filePath, definition),
  )

  /** 同一定义区间的作用域可见副本仍属于同一个词法绑定。 */
  fun sameBinding(other: JavaScriptSymbol): Boolean = definition == other.definition
}

/** 语法树中的一次定义或引用出现。 */
private data class JavaScriptOccurrence(
  val symbol: JavaScriptSymbol,
  val range: DynamicTextRange,
  val kind: JavaScriptOccurrenceKind,
) {
  val isDefinition: Boolean
    get() = kind == JavaScriptOccurrenceKind.DEFINITION ||
      kind == JavaScriptOccurrenceKind.PATTERN_SHORTHAND_DEFINITION

  /** 对象或解构简写需展开属性键，普通标识符则直接替换。 */
  fun replacement(newName: String): String = when (kind) {
    JavaScriptOccurrenceKind.OBJECT_SHORTHAND_REFERENCE,
    JavaScriptOccurrenceKind.PATTERN_SHORTHAND_DEFINITION,
    -> "${symbol.name}: $newName"
    else -> newName
  }
}

/** 区分直接替换与需要保持属性键的 JavaScript 简写语法。 */
private enum class JavaScriptOccurrenceKind {
  DEFINITION,
  PATTERN_SHORTHAND_DEFINITION,
  REFERENCE,
  OBJECT_SHORTHAND_REFERENCE,
}

/** 返回当前节点向上的第一个指定类型祖先。 */
private fun LezerSyntaxNode.nearestAncestor(name: String): LezerSyntaxNode? =
  nearestAncestor(setOf(name))

/** 返回当前节点向上的第一个指定类型祖先。 */
private fun LezerSyntaxNode.nearestAncestor(names: Set<String>): LezerSyntaxNode? {
  var current = parent
  while (current != null) {
    if (current.name in names) return current
    current = current.parent
  }
  return null
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

/** 按 Lezer 稳定的先后顺序返回所有直接子节点。 */
private fun LezerSyntaxNode.directChildren(): List<LezerSyntaxNode> = buildList {
  var child = firstChild
  while (child != null) {
    add(child)
    child = child.nextSibling
  }
}

/** 读取 import/export 字符串字面量中的模块名，当前仅接受常规引号形式。 */
private fun String.moduleSpecifier(node: LezerSyntaxNode): String? {
  val literal = substring(node.from, node.to)
  if (literal.length < 2) return null
  val quote = literal.first()
  if ((quote != '\'' && quote != '"') || literal.last() != quote) return null
  return literal.substring(1, literal.lastIndex)
}

/** 读取工作区文件，语义请求不允许引用快照外的隐式源码。 */
private fun DynamicLanguageWorkspace.requireSource(filePath: String): String {
  return files.firstOrNull { file -> file.path == filePath }?.source
    ?: throw IllegalArgumentException("Workspace does not contain '$filePath'.")
}

/**
 * 使用导入文件目录解析相对模块名，并按 ES Module 常见扩展名与 `index` 规则查找。
 *
 * 裸模块名只会匹配工作区中的精确路径，不在语言服务内复制 npm 依赖解析。
 */
private fun resolveModulePath(
  importerPath: String,
  requestedName: String,
  availablePaths: Set<String>,
): String? {
  val requestedPath = if (requestedName.startsWith('.')) {
    val importerDirectory = importerPath.substringBeforeLast('/', missingDelimiterValue = "")
    normalizeModulePath(
      listOf(importerDirectory, requestedName)
        .filter(String::isNotEmpty)
        .joinToString("/"),
    )
  } else {
    normalizeModulePath(requestedName)
  } ?: return null
  return buildList {
    add(requestedPath)
    if (requestedPath.substringAfterLast('/').contains('.').not()) {
      MODULE_FILE_EXTENSIONS.forEach { extension -> add("$requestedPath$extension") }
      MODULE_FILE_EXTENSIONS.forEach { extension -> add("$requestedPath/index$extension") }
    }
  }.firstOrNull(availablePaths::contains)
}

/** 折叠 `.` 与 `..` 路径段；尝试越出工作区根目录时返回 null，避免错误命中同名文件。 */
private fun normalizeModulePath(path: String): String? {
  val segments = mutableListOf<String>()
  path.replace('\\', '/').split('/').forEach { segment ->
    when (segment) {
      "", "." -> Unit
      ".." -> if (segments.isNotEmpty()) {
        segments.removeAt(segments.lastIndex)
      } else {
        return null
      }
      else -> segments += segment
    }
  }
  return segments.joinToString("/")
}

/** 标识符光标既可以位于区间内部，也可以紧邻区间末尾。 */
private fun DynamicTextRange.containsPosition(position: Int): Boolean {
  return position in from until to || position == to
}

/** 将 Lezer 作用域节点转换为索引内部类别。 */
private fun String.toScopeKind(): JavaScriptScopeKind = when (this) {
  "FunctionDeclaration", "FunctionExpression", "ArrowFunction", "MethodDeclaration" ->
    JavaScriptScopeKind.FUNCTION
  "ClassDeclaration", "ClassExpression" -> JavaScriptScopeKind.CLASS
  "ForStatement" -> JavaScriptScopeKind.LOOP
  "CatchClause" -> JavaScriptScopeKind.CATCH
  else -> JavaScriptScopeKind.BLOCK
}

/** 读取变量声明关键字；语法树已确定边界，因此无需扫描完整源码。 */
private fun String.declarationKind(
  declaration: LezerSyntaxNode,
  definition: LezerSyntaxNode,
): String {
  return substring(declaration.from, definition.from)
    .trimStart()
    .substringBefore(' ')
    .substringBefore('\n')
    .takeIf { it in VARIABLE_DECLARATION_KINDS }
    ?: "let"
}

/** 从光标前向后定位当前标识符替换区间。 */
private fun String.findIdentifierStart(position: Int): Int {
  var index = position
  while (index > 0 && this[index - 1].isJavaScriptIdentifierPart()) index -= 1
  return index
}

/** 提取 `receiver.<prefix>` 中只含简单标识符的 receiver。 */
private fun String.receiverBefore(identifierStart: Int): String? {
  val dotIndex = identifierStart - 1
  if (dotIndex < 0 || this[dotIndex] != '.') return null
  var receiverEnd = dotIndex
  if (receiverEnd > 0 && this[receiverEnd - 1] == '?') receiverEnd -= 1
  var receiverStart = receiverEnd
  while (receiverStart > 0 && this[receiverStart - 1].isJavaScriptIdentifierPart()) receiverStart -= 1
  return substring(receiverStart, receiverEnd).takeIf(String::isNotEmpty)
}

private fun String.isJavaScriptIdentifier(): Boolean {
  return isNotEmpty() && first().isJavaScriptIdentifierStart() && drop(1).all(Char::isJavaScriptIdentifierPart)
}

private fun Char.isJavaScriptIdentifierStart(): Boolean = isLetter() || this == '_' || this == '$'

private fun Char.isJavaScriptIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '$'

internal const val COMPLETION_TYPE_KEYWORD = "keyword"
internal const val COMPLETION_TYPE_VARIABLE = "variable"
internal const val COMPLETION_TYPE_FUNCTION = "function"
internal const val COMPLETION_TYPE_CLASS = "class"
internal const val COMPLETION_TYPE_METHOD = "method"
internal const val COMPLETION_TYPE_PROPERTY = "property"
internal const val SYMBOL_BOOST = 100
internal const val MEMBER_BOOST = 80
internal const val KEYWORD_BOOST = 0
internal const val RECEIVER_ARRAY = "Array"
internal const val RECEIVER_ARRAY_CONSTRUCTOR = "ArrayConstructor"
internal const val RECEIVER_STRING = "String"
internal const val RECEIVER_NUMBER = "Number"
internal const val RECEIVER_OBJECT = "Object"
internal const val RECEIVER_OBJECT_CONSTRUCTOR = "ObjectConstructor"
internal const val RECEIVER_FUNCTION = "Function"
internal const val RECEIVER_PROMISE = "Promise"
internal const val RECEIVER_PROMISE_CONSTRUCTOR = "PromiseConstructor"

private val FUNCTION_SCOPE_NODE_NAMES = setOf(
  "FunctionDeclaration",
  "FunctionExpression",
  "ArrowFunction",
  "MethodDeclaration",
)
private val CLASS_SCOPE_NODE_NAMES = setOf("ClassDeclaration", "ClassExpression")
private val SCOPE_NODE_NAMES = FUNCTION_SCOPE_NODE_NAMES + CLASS_SCOPE_NODE_NAMES + setOf(
  "Script",
  "Block",
  "ForStatement",
  "CatchClause",
)
private val FUNCTION_SCOPE_KINDS = setOf(JavaScriptScopeKind.SCRIPT, JavaScriptScopeKind.FUNCTION)
private val COMPLETION_SUPPRESSED_NODES = setOf(
  "String",
  "TemplateString",
  "RegExp",
  "LineComment",
  "BlockComment",
)
private val INITIALIZER_SEPARATOR_NODES = setOf("Equals", "TypeAnnotation", "Optional")
private val VARIABLE_DECLARATION_KINDS = setOf("const", "let", "var", "using", "await")
private val NEW_EXPRESSION_REGEX = Regex("new\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
private val MODULE_FILE_EXTENSIONS = listOf(".js", ".mjs", ".cjs")
private val JAVASCRIPT_RENAME_RESERVED_WORDS = JAVASCRIPT_KEYWORDS - "undefined"
private val JAVASCRIPT_KNOWN_GLOBAL_NAMES = JAVASCRIPT_GLOBALS.mapTo(mutableSetOf()) { item -> item.label }
  .apply { add("undefined") }
private const val RENAME_REJECTION_INVALID_IDENTIFIER = "invalid_identifier"
private const val RENAME_REJECTION_RESERVED_WORD = "reserved_word"
private const val RENAME_REJECTION_NAME_CONFLICT = "name_conflict"
private const val MODULE_DEFAULT_EXPORT_NAME = "default"
private const val MODULE_NAMESPACE_IMPORT_NAME = "*"
