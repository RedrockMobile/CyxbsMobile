package com.cyxbs.functions.code.language.javascript.completion

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionItem
import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.lezer.LezerSyntaxNode
import com.cyxbs.functions.code.language.lezer.LezerTree

/**
 * 面向单个 JavaScript 编辑会话的轻量语义补全器。
 *
 * 它使用 Lezer 错误恢复语法树建立词法作用域和声明索引，不执行用户代码，也不尝试覆盖 JavaScript
 * 的动态元编程语义。当前支持函数、参数、变量、类、导入符号，以及常见内置对象和可静态推断的
 * receiver 成员。索引只缓存最近一份源码，Runtime 关闭后会随语言服务一同释放。
 */
internal class JavaScriptSemanticCompletionSession(
  private val syntaxTree: (String) -> LezerTree,
) {
  private var indexedSource: String? = null
  private var index: JavaScriptSemanticIndex? = null

  /**
   * 查询 [position] 位置可见的符号或 receiver 成员。
   *
   * @param source 当前完整源码。
   * @param position 光标 UTF-16 偏移。
   * @param explicit 是否由用户主动触发；自动触发时空前缀不会弹出候选。
   * @return 可替换当前标识符前缀的补全结果；字符串、注释或未知 receiver 中返回 null。
   */
  fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    require(position in 0..source.length) { "position must be inside source." }
    val semanticIndex = indexFor(source)
    if (semanticIndex.isCompletionSuppressed(position)) return null

    val from = source.findIdentifierStart(position)
    val prefix = source.substring(from, position)
    val receiver = source.receiverBefore(from)
    if (!explicit && prefix.isEmpty() && receiver == null) return null
    val options = if (receiver == null) {
      semanticIndex.lexicalCompletions(position, prefix)
    } else {
      semanticIndex.memberCompletions(position, receiver, prefix)
    }
    return options.takeIf { it.isNotEmpty() }?.let {
      DynamicCompletionResult(from = from, to = position, options = options)
    }
  }

  /** 仅在源码变化时重建语义索引；Lezer 语法树本身由高亮会话增量维护。 */
  private fun indexFor(source: String): JavaScriptSemanticIndex {
    if (indexedSource != source || index == null) {
      index = JavaScriptSemanticIndex(source, syntaxTree(source))
      indexedSource = source
    }
    return checkNotNull(index)
  }
}

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

  init {
    visit(tree.topNode, rootScope)
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

  /** 根据静态 receiver 类型返回常见成员；未知动态类型不猜测候选。 */
  fun memberCompletions(
    position: Int,
    receiver: String,
    prefix: String,
  ): List<DynamicCompletionItem> {
    val receiverType = when (receiver) {
      "this" -> enclosingClassName(position)
      else -> BUILTIN_RECEIVER_TYPES[receiver]
        ?: rootScope.innermost(position).visibleSymbol(receiver, position)?.receiverType
    } ?: return emptyList()
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

    val symbol = when {
      parameterOwner != null -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_VARIABLE,
        detail = "函数参数",
        visibleFrom = currentScope.nearestFunctionScope().from,
      )

      parentName == "FunctionDeclaration" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_FUNCTION,
        detail = "函数声明",
        visibleFrom = currentScope.parent?.from ?: 0,
        receiverType = RECEIVER_FUNCTION,
      )

      parentName == "FunctionExpression" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_FUNCTION,
        detail = "具名函数表达式",
        visibleFrom = currentScope.from,
        receiverType = RECEIVER_FUNCTION,
      )

      parentName == "ClassDeclaration" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_CLASS,
        detail = "类声明",
        visibleFrom = node.to,
        receiverType = name,
      )

      parentName == "ClassExpression" -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_CLASS,
        detail = "具名类表达式",
        visibleFrom = currentScope.from,
        receiverType = name,
      )

      importDeclaration != null -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_VARIABLE,
        detail = "导入符号",
        visibleFrom = rootScope.from,
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
        )
      }

      else -> JavaScriptSymbol(
        name = name,
        type = COMPLETION_TYPE_VARIABLE,
        detail = "局部符号",
        visibleFrom = node.to,
      )
    }

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
    targetScope.symbols += symbol

    // 类名在类体内部也可见，但在外层仍遵守声明前暂时性死区。
    if (parentName == "ClassDeclaration") {
      currentScope.symbols += symbol.copy(visibleFrom = currentScope.from)
    }
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
    return children.firstOrNull { child -> position in child.from..child.to }
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
