package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.js.bridge.DynamicSymbolDefinition
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession
import com.cyxbs.generated.npmjs.__cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证 KSP 生成的下发端分发器能够注册并调用 Kotlin/JS 业务实现。 */
class JavaScriptDynamicLanguageServiceDispatcherTest {

  /** Lezer 应覆盖关键词之外的常见 JavaScript 语法，并保留组合样式。 */
  @Test
  fun lezerHighlightsJavaScriptSyntax() = runTest {
    val source = """
      const message = "hello"
      // comment
      function greet() {
        return 42
      }
    """.trimIndent()

    val spans = highlight(source).spans

    assertTrue(spans.stylesFor(source, "const").contains("tok-keyword"))
    assertTrue(spans.stylesFor(source, "\"hello\"").contains("tok-string"))
    assertTrue(spans.stylesFor(source, "// comment").contains("tok-comment"))
    assertTrue(spans.stylesFor(source, "42").contains("tok-number"))
    assertTrue(spans.any {
      "tok-variableName" in it.styleIds && "tok-definition" in it.styleIds
    })
  }

  /** JavaScript 与 Kotlin/JS 都按 UTF-16 计数，表情后的区间不能被错误换算成码点偏移。 */
  @Test
  fun highlightOffsetsUseUtf16() = runTest {
    val source = "const emoji = \"😀\"; const answer = 42"

    val spans = highlight(source).spans
    val answerFrom = source.indexOf("answer")
    val answerSpan = spans.firstOrNull {
      it.from == answerFrom && it.to == answerFrom + "answer".length
    }

    assertNotNull(answerSpan)
    assertTrue(answerSpan.styleIds.contains("tok-variableName"))
  }

  /** 相同源码应直接命中结果缓存，不再次执行 Lezer 解析与高亮遍历。 */
  @Test
  fun repeatedSourceReusesExactHighlightResult() = runTest {
    val source = "const cachedValue = 42"
    val session = LezerSyntaxHighlighterSession(parser)

    val first = session.highlight(source)
    val cached = session.highlight(source)

    assertEquals(DynamicHighlightCacheMode.FULL, first.metrics.cacheMode)
    assertEquals(DynamicHighlightCacheMode.EXACT, cached.metrics.cacheMode)
    assertEquals(0, cached.metrics.parseMicroseconds)
    assertEquals(0, cached.metrics.collectMicroseconds)
    assertTrue(cached.spans.stylesFor(source, "42").contains("tok-number"))
  }

  /** 小范围编辑应复用未受影响的语法树片段，并输出新源码对应的高亮区间。 */
  @Test
  fun smallEditUsesIncrementalSyntaxTreeFragments() = runTest {
    val session = LezerSyntaxHighlighterSession(parser)
    val original = buildString {
      repeat(20) { index -> appendLine("const value$index = $index;") }
    }
    val updated = original.replace("value10 = 10", "value10 = \"ten\"")

    session.highlight(original)
    val result = session.highlight(updated)

    assertEquals(DynamicHighlightCacheMode.INCREMENTAL, result.metrics.cacheMode)
    assertNotNull(result.metrics.changedRange)
    assertTrue(result.metrics.reusableFragmentCount > 0)
    assertTrue(result.spans.stylesFor(updated, "\"ten\"").contains("tok-string"))
  }

  /** 补全应包含当前函数参数、局部声明和外层声明，同时屏蔽已经离开的内部块符号。 */
  @Test
  fun semanticCompletionRespectsLexicalScopes() = runTest {
    val source = """
      const globalValue = 1
      function greet(person) {
        if (person) {
          const insideOnly = person
        }
        const message = person
        return;
      }
      ins
    """.trimIndent()
    val insidePosition = source.indexOf("return;") + "return;".length

    val inside = complete(source, insidePosition, explicit = true)
    val insideLabels = assertNotNull(inside).options.map { it.label }
    assertTrue("person" in insideLabels)
    assertTrue("message" in insideLabels)
    assertTrue("globalValue" in insideLabels)
    assertTrue("greet" in insideLabels)
    assertFalse("insideOnly" in insideLabels)

    val outsidePosition = source.lastIndexOf("ins") + 3
    val outside = complete(source, outsidePosition, explicit = false)
    assertFalse(outside?.options.orEmpty().any { it.label == "insideOnly" })
  }

  /** 块级变量遵守声明顺序，函数声明则可以在源码声明位置之前补全。 */
  @Test
  fun semanticCompletionAppliesDeclarationVisibilityRules() = runTest {
    val source = """
      function demo() {
        fut
        gre
        const futureValue = 1
      }
      function greet() {}
    """.trimIndent()
    val futurePosition = source.indexOf("fut") + 3
    val greetPosition = source.indexOf("gre") + 3

    assertNull(complete(source, futurePosition, explicit = false))
    val greetCompletion = complete(source, greetPosition, explicit = false)
    assertTrue(assertNotNull(greetCompletion).options.any { it.label == "greet" })
  }

  /** 数组和自定义类的简单静态类型应提供 receiver 成员，而未知动态 receiver 不猜测。 */
  @Test
  fun semanticCompletionProvidesReceiverMembers() = runTest {
    val source = """
      class User {
        greet(name) { return name }
      }
      const names = ["Ada"]
      const user = new User()
      names.ma
      user.gr
      unknown.wh
    """.trimIndent()

    val arrayPosition = source.indexOf("names.ma") + "names.ma".length
    val classPosition = source.indexOf("user.gr") + "user.gr".length
    val unknownPosition = source.indexOf("unknown.wh") + "unknown.wh".length

    val arrayCompletion = complete(source, arrayPosition, false)
    assertTrue(assertNotNull(arrayCompletion).options.any { it.label == "map" })
    val classCompletion = complete(source, classPosition, false)
    assertTrue(assertNotNull(classCompletion).options.any { it.label == "greet" })
    assertNull(complete(source, unknownPosition, false))
  }

  /** 注释和字符串内容不应触发普通代码符号补全。 */
  @Test
  fun semanticCompletionIgnoresCommentsAndStrings() = runTest {
    val comment = "const value = 1; // val"
    val string = "const text = \"val\""

    assertNull(complete(comment, comment.length, false))
    assertNull(complete(string, string.length - 1, false))
  }

  /** 定义和引用查询必须区分同名参数与外层变量，并且引用列表不重复包含定义。 */
  @Test
  fun semanticIndexResolvesDefinitionsAndReferencesByScope() = runTest {
    val source = """
      const value = 1
      function increment(value) {
        return value + 1
      }
      console.log(value)
    """.trimIndent()
    val parameterDefinition = source.indexOf("value) {")
    val parameterReference = source.indexOf("value +")
    val outerDefinition = source.indexOf("value =")
    val outerReference = source.lastIndexOf("value)")

    val inner = definition(source, parameterReference + 2)
    assertEquals(parameterDefinition, assertNotNull(inner).definition.range.from)
    val innerReferences = references(source, parameterDefinition + 1)
    assertEquals(listOf(parameterReference), assertNotNull(innerReferences).references.map { it.range.from })

    val outer = definition(source, outerReference + 2)
    assertEquals(outerDefinition, assertNotNull(outer).definition.range.from)
    val outerReferences = references(source, outerDefinition + 1)
    assertEquals(listOf(outerReference), assertNotNull(outerReferences).references.map { it.range.from })
  }

  /** 声明前的块级引用仍绑定内层暂时性死区变量，不能错误跳到外层同名定义。 */
  @Test
  fun semanticIndexKeepsTemporalDeadZoneBinding() = runTest {
    val source = """
      const value = 1
      {
        console.log(value)
        const value = 2
      }
    """.trimIndent()
    val reference = source.indexOf("value)")
    val innerDefinition = source.lastIndexOf("value =")

    val definition = definition(source, reference + 2)

    assertEquals(innerDefinition, assertNotNull(definition).definition.range.from)
  }

  /** 同名内层声明不能混入外层符号的引用结果。 */
  @Test
  fun semanticReferencesKeepShadowedBindingsSeparate() = runTest {
    val source = """
      let count = 1
      {
        let count = 2
        console.log(count)
      }
      console.log(count)
    """.trimIndent()
    val outerDefinition = source.indexOf("count =")
    val outerReference = source.lastIndexOf("count)")

    val references = references(source, outerDefinition + 2)

    assertEquals(listOf(outerReference), assertNotNull(references).references.map { it.range.from })
  }

  /** 安全重命名应修改定义和引用，并展开对象简写以保持原属性键不变。 */
  @Test
  fun semanticRenameProducesScopeSafeTextEdits() = runTest {
    val source = """
      function greet(name) {
        const message = name
        return { name }
      }
    """.trimIndent()
    val position = source.indexOf("name)") + 2

    val result = rename(source, position, "studentName")

    val rename = assertNotNull(result)
    assertTrue(rename.isSuccess)
    assertEquals(3, rename.edits.size)
    assertEquals(
      """
        function greet(studentName) {
          const message = studentName
          return { name: studentName }
        }
      """.trimIndent(),
      source.applyEdits(rename.edits, MAIN_FILE_PATH),
    )
  }

  /** 对象解构简写中的属性键必须保留，只重命名新建的词法绑定。 */
  @Test
  fun semanticRenamePreservesDestructuringPropertyKey() = runTest {
    val source = """
      const source = { value: 1 }
      const { value } = source
      console.log(value)
    """.trimIndent()
    val definition = source.indexOf("value } =")

    val rename = assertNotNull(
      rename(source, definition + 2, "score"),
    )

    assertTrue(rename.isSuccess)
    assertEquals(
      """
        const source = { value: 1 }
        const { value: score } = source
        console.log(score)
      """.trimIndent(),
      source.applyEdits(rename.edits, MAIN_FILE_PATH),
    )
  }

  /** 内层声明会捕获外层符号的引用时，必须拒绝重命名。 */
  @Test
  fun semanticRenameRejectsNestedCapture() = runTest {
    val source = """
      const item = 1
      function read() {
        const value = 2
        return item + value
      }
    """.trimIndent()
    val position = source.indexOf("item =") + 2

    val rename = rename(source, position, "value")

    assertEquals("name_conflict", assertNotNull(rename).rejectionCode)
  }

  /** 非法名称、保留字和会捕获引用的同名绑定必须返回结构化拒绝结果。 */
  @Test
  fun semanticRenameRejectsUnsafeNames() = runTest {
    val source = """
      function demo(item) {
        const value = 1
        return item + value
      }
    """.trimIndent()
    val position = source.indexOf("item)") + 2

    assertEquals(
      "invalid_identifier",
      rename(source, position, "not-valid")?.rejectionCode,
    )
    assertEquals(
      "reserved_word",
      rename(source, position, "class")?.rejectionCode,
    )
    assertEquals(
      "name_conflict",
      rename(source, position, "value")?.rejectionCode,
    )
    assertEquals(
      "name_conflict",
      rename(source, position, "console")?.rejectionCode,
    )
  }

  /** 命名 import 的定义查询应解析相对路径，并跳转到目标文件的导出声明。 */
  @Test
  fun namedImportDefinitionJumpsAcrossFiles() = runTest {
    val modelSource = "export class Student {}"
    val mainSource = """
      import { Student } from "./models/student.js"
      const student = new Student()
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val definition = JavaScriptDynamicLanguageService.definition(
      workspace,
      MAIN_FILE_PATH,
      mainSource.lastIndexOf("Student") + 2,
    )

    val location = assertNotNull(definition).definition
    assertEquals("models/student.js", location.filePath)
    assertEquals(modelSource.indexOf("Student"), location.range.from)
  }

  /** `new` 导入类得到的实例应复用目标文件类成员，提供跨文件 receiver 补全。 */
  @Test
  fun importedClassProvidesReceiverMembers() = runTest {
    val modelSource = """
      export class Student {
        average() { return 100 }
      }
    """.trimIndent()
    val mainSource = """
      import { Student as Learner } from "./models/student.js"
      const student = new Learner()
      student.av
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val completion = JavaScriptDynamicLanguageService.complete(
      workspace,
      MAIN_FILE_PATH,
      mainSource.length,
      explicit = false,
    )

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "average" })
  }

  /** 导出符号的引用查询应聚合 import 声明和各导入文件内的本地使用。 */
  @Test
  fun namedExportReferencesIncludeImporters() = runTest {
    val modelSource = "export class Student {}"
    val mainSource = """
      import { Student } from "./models/student.js"
      const first = new Student()
      const second = Student
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val result = JavaScriptDynamicLanguageService.references(
      workspace,
      "models/student.js",
      modelSource.indexOf("Student") + 2,
    )

    val locations = assertNotNull(result).references
    assertEquals(3, locations.size)
    assertTrue(locations.all { location -> location.filePath == MAIN_FILE_PATH })
    assertEquals(
      listOf(
        mainSource.indexOf("Student"),
        mainSource.indexOf("Student", mainSource.indexOf("new")),
        mainSource.lastIndexOf("Student"),
      ),
      locations.map { location -> location.range.from },
    )
  }

  /** 重命名公开导出应以工作区修改返回，并同步未起别名的 import 与引用。 */
  @Test
  fun namedExportRenameUpdatesEveryAffectedFile() = runTest {
    val modelSource = "export class Student {}"
    val mainSource = """
      import { Student } from "./models/student.js"
      const student = new Student()
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val result = assertNotNull(
      JavaScriptDynamicLanguageService.rename(
        workspace,
        "models/student.js",
        modelSource.indexOf("Student") + 2,
        "Learner",
      ),
    )

    assertTrue(result.isSuccess)
    assertEquals(
      "export class Learner {}",
      modelSource.applyEdits(result.edits, "models/student.js"),
    )
    assertEquals(
      """
        import { Learner } from "./models/student.js"
        const student = new Learner()
      """.trimIndent(),
      mainSource.applyEdits(result.edits, MAIN_FILE_PATH),
    )
  }

  /** 别名 import 只跟随公开导出名，调用方选用的本地别名和引用保持不变。 */
  @Test
  fun aliasedImportKeepsLocalBindingWhenExportIsRenamed() = runTest {
    val modelSource = "export class Student {}"
    val mainSource = """
      import { Student as Learner } from "./models/student.js"
      const student = new Learner()
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val result = assertNotNull(
      JavaScriptDynamicLanguageService.rename(
        workspace,
        "models/student.js",
        modelSource.indexOf("Student") + 2,
        "Person",
      ),
    )

    assertTrue(result.isSuccess)
    assertEquals(
      """
        import { Person as Learner } from "./models/student.js"
        const student = new Learner()
      """.trimIndent(),
      mainSource.applyEdits(result.edits, MAIN_FILE_PATH),
    )
  }

  /** 从别名或其本地引用发起重命名时只修改本文件，不改变远端公开导出名。 */
  @Test
  fun aliasedImportLocalRenameDoesNotChangeExport() = runTest {
    val modelSource = "export class Student {}"
    val mainSource = """
      import { Student as Learner } from "./models/student.js"
      const student = new Learner()
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val result = assertNotNull(
      JavaScriptDynamicLanguageService.rename(
        workspace,
        MAIN_FILE_PATH,
        mainSource.lastIndexOf("Learner") + 2,
        "Person",
      ),
    )

    assertTrue(result.isSuccess)
    assertEquals(modelSource, modelSource.applyEdits(result.edits, "models/student.js"))
    assertEquals(
      """
        import { Student as Person } from "./models/student.js"
        const student = new Person()
      """.trimIndent(),
      mainSource.applyEdits(result.edits, MAIN_FILE_PATH),
    )
  }

  /** 光标位于别名 import 的公开名称时也应跳到远端定义，而不是误认本地别名。 */
  @Test
  fun aliasedImportPublicNameJumpsToExport() = runTest {
    val modelSource = "export class Student {}"
    val mainSource = "import { Student as Learner } from \"./models/student.js\""
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "models/student.js" to modelSource,
    )

    val definition = JavaScriptDynamicLanguageService.definition(
      workspace,
      MAIN_FILE_PATH,
      mainSource.indexOf("Student") + 2,
    )

    val location = assertNotNull(definition).definition
    assertEquals("models/student.js", location.filePath)
    assertEquals(modelSource.indexOf("Student"), location.range.from)
  }

  /** 无法解析的模块仍保留本地 import 绑定，定义查询不会错误跳到其他同名文件。 */
  @Test
  fun unresolvedImportFallsBackToLocalDefinition() = runTest {
    val source = """
      import { Student } from "./missing.js"
      const student = new Student()
    """.trimIndent()

    val definition = JavaScriptDynamicLanguageService.definition(
      workspaceOf(MAIN_FILE_PATH to source),
      MAIN_FILE_PATH,
      source.lastIndexOf("Student") + 2,
    )

    val location = assertNotNull(definition).definition
    assertEquals(MAIN_FILE_PATH, location.filePath)
    assertEquals(source.indexOf("Student"), location.range.from)
  }

  /** 越出工作区根目录的相对 import 不得被折叠成根目录同名文件。 */
  @Test
  fun importOutsideWorkspaceDoesNotMatchRootFile() = runTest {
    val externalSource = "export class Student {}"
    val mainSource = """
      import { Student } from "../../student.js"
      const student = new Student()
    """.trimIndent()
    val workspace = workspaceOf(
      MAIN_FILE_PATH to mainSource,
      "student.js" to externalSource,
    )

    val definition = JavaScriptDynamicLanguageService.definition(
      workspace,
      MAIN_FILE_PATH,
      mainSource.lastIndexOf("Student") + 2,
    )

    val location = assertNotNull(definition).definition
    assertEquals(MAIN_FILE_PATH, location.filePath)
    assertEquals(mainSource.indexOf("Student"), location.range.from)
  }

  /** 显式初始化应可重复调用，并按 commonMain 协议完成参数及返回值的 JSON 转换。 */
  @Test
  fun generatedDispatcherInvokesService() = runTest {
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript()
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_javascript()
    val bridge: dynamic = js("globalThis.CyxbsNpmJsService")
    assertTrue(bridge != undefined)
    assertEquals(SERVICE_ID, _JavaScriptDynamicLanguageServiceNpmJsDispatcher.serviceId)
    val describedMethods = Json.decodeFromString<List<String>>(bridge.describe(SERVICE_ID) as String)
    assertEquals(
      setOf("complete", "definition", "highlight", "references", "rename"),
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.methodNames,
    )
    assertEquals(
      setOf("complete", "definition", "highlight", "references", "rename"),
      describedMethods.toSet(),
    )

    val highlightResult = Json.decodeFromString<DynamicHighlightResult>(
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.invoke(
        method = "highlight",
        argumentsJson =
          """[{"files":[{"path":"main.js","source":"const answer = 42"}]},"main.js"]""",
      ),
    )
    assertTrue(highlightResult.spans.stylesFor("const answer = 42", "42").contains("tok-number"))

    val completion = Json.decodeFromString<DynamicCompletionResult?>(
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.invoke(
        method = "complete",
        argumentsJson = """[{"files":[{"path":"main.js","source":"co"}]},"main.js",2,false]""",
      ),
    )
    assertNotNull(completion)
    assertEquals(0, completion.from)
    assertTrue(completion.options.any { it.label == "const" })

    val definitionSource = "const value = 1; console.log(value)"
    val definition = Json.decodeFromString<DynamicSymbolDefinition?>(
      _JavaScriptDynamicLanguageServiceNpmJsDispatcher.invoke(
        method = "definition",
        argumentsJson = """[{"files":[{"path":"main.js","source":"$definitionSource"}]},"main.js",${definitionSource.lastIndexOf("value") + 2}]""",
      ),
    )
    val definitionLocation = assertNotNull(definition).definition
    assertEquals(MAIN_FILE_PATH, definitionLocation.filePath)
    assertEquals(definitionSource.indexOf("value"), definitionLocation.range.from)
  }

  private companion object {
    const val MAIN_FILE_PATH = "main.js"
    const val SERVICE_ID =
      "com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService"
  }

  /** 把既有单文件断言包装为新的工作区协议，测试关注点仍保持在原语义能力。 */
  private fun singleFileWorkspace(source: String): DynamicLanguageWorkspace {
    return DynamicLanguageWorkspace(listOf(DynamicSourceFile(MAIN_FILE_PATH, source)))
  }

  /** 按文件相对路径构建可序列化工作区快照。 */
  private fun workspaceOf(vararg files: Pair<String, String>): DynamicLanguageWorkspace {
    return DynamicLanguageWorkspace(
      files.map { (path, source) -> DynamicSourceFile(path = path, source = source) },
    )
  }

  /** 调用工作区高亮协议。 */
  private suspend fun highlight(source: String): DynamicHighlightResult {
    return JavaScriptDynamicLanguageService.highlight(singleFileWorkspace(source), MAIN_FILE_PATH)
  }

  /** 调用工作区补全协议。 */
  private suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    return JavaScriptDynamicLanguageService.complete(
      singleFileWorkspace(source),
      MAIN_FILE_PATH,
      position,
      explicit,
    )
  }

  /** 调用工作区定义查询协议。 */
  private suspend fun definition(source: String, position: Int): DynamicSymbolDefinition? {
    return JavaScriptDynamicLanguageService.definition(
      singleFileWorkspace(source),
      MAIN_FILE_PATH,
      position,
    )
  }

  /** 调用工作区引用查询协议。 */
  private suspend fun references(source: String, position: Int) =
    JavaScriptDynamicLanguageService.references(
      singleFileWorkspace(source),
      MAIN_FILE_PATH,
      position,
    )

  /** 调用工作区重命名协议。 */
  private suspend fun rename(source: String, position: Int, newName: String) =
    JavaScriptDynamicLanguageService.rename(
      singleFileWorkspace(source),
      MAIN_FILE_PATH,
      position,
      newName,
    )

  /** 返回与指定源码片段完全重合的样式集合，避免测试依赖整棵语法树的节点数量。 */
  private fun List<DynamicHighlightSpan>.stylesFor(
    source: String,
    text: String,
  ): List<String> {
    val from = source.indexOf(text)
    require(from >= 0) { "Text not found in source: $text" }
    return firstOrNull { it.from == from && it.to == from + text.length }?.styleIds.orEmpty()
  }

  /** 按位置倒序应用协议文本修改，避免前方替换改变后续 UTF-16 偏移。 */
  private fun String.applyEdits(edits: List<DynamicSourceEdit>, filePath: String): String {
    return edits.asSequence()
      .filter { sourceEdit -> sourceEdit.filePath == filePath }
      .map(DynamicSourceEdit::edit)
      .sortedByDescending { edit -> edit.from }
      .fold(this) { source, edit ->
        source.replaceRange(edit.from, edit.to, edit.replacement)
    }
  }
}
