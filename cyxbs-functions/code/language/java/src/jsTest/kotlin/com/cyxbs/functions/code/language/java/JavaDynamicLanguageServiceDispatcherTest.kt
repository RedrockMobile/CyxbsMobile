package com.cyxbs.functions.code.language.java

import com.cyxbs.functions.code.language.js.bridge.DynamicCompletionResult
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightResult
import com.cyxbs.functions.code.language.js.bridge.DynamicHighlightSpan
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceEdit
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.cyxbs.functions.code.language.lezer.LezerSyntaxHighlighterSession
import com.cyxbs.generated.npmjs.__cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_java
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证 Java 动态语言包的高亮、轻量语义索引和 KSP 分发协议。 */
class JavaDynamicLanguageServiceDispatcherTest {

  /** Lezer 应识别 Java 关键字、类型、字符串、注释和数字。 */
  @Test
  fun lezerHighlightsJavaSyntax() = runTest {
    val source = """
      class Main {
        // comment
        String message = "hello";
        int answer = 42;
      }
    """.trimIndent()

    val spans = highlight(source).spans

    assertTrue(spans.stylesFor(source, "class").contains("tok-keyword"))
    assertTrue(spans.stylesFor(source, "\"hello\"").contains("tok-string"))
    assertTrue(spans.stylesFor(source, "// comment").contains("tok-comment"))
    assertTrue(spans.stylesFor(source, "42").contains("tok-number"))
    assertTrue(spans.any { span ->
      "tok-variableName" in span.styleIds && "tok-definition" in span.styleIds
    })
  }

  /** Java 高亮与 Kotlin/JS 都应保持 UTF-16 偏移。 */
  @Test
  fun highlightOffsetsUseUtf16() = runTest {
    val source = "class Main { String emoji = \"😀\"; int answer = 42; }"

    val answerFrom = source.indexOf("answer")
    val answerSpan = highlight(source).spans.firstOrNull { span ->
      span.from == answerFrom && span.to == answerFrom + "answer".length
    }

    assertNotNull(answerSpan)
    assertTrue(answerSpan.styleIds.contains("tok-variableName"))
  }

  /** 高亮会话应覆盖完整解析、完全命中和小范围增量解析。 */
  @Test
  fun highlighterReusesExactAndIncrementalTrees() {
    val session = LezerSyntaxHighlighterSession(parser)
    val original = buildString {
      appendLine("class Main {")
      repeat(20) { index -> appendLine("  int value$index = $index;") }
      appendLine("}")
    }
    val updated = original.replace("value10 = 10", "value10 = 100")

    val first = session.highlight(original)
    val exact = session.highlight(original)
    val incremental = session.highlight(updated)

    assertEquals(DynamicHighlightCacheMode.FULL, first.metrics.cacheMode)
    assertEquals(DynamicHighlightCacheMode.EXACT, exact.metrics.cacheMode)
    assertEquals(DynamicHighlightCacheMode.INCREMENTAL, incremental.metrics.cacheMode)
    assertTrue(incremental.metrics.reusableFragmentCount > 0)
  }

  /** 补全应遵守方法参数、局部块和声明位置的可见性。 */
  @Test
  fun completionRespectsJavaScopes() = runTest {
    val source = """
      class Main {
        void greet(String person) {
          if (person.isEmpty()) {
            int insideOnly = 1;
          }
          String message = person;
          mes
        }
      }
    """.trimIndent()

    val statementStart = source.indexOf("mes\n")
    val prefixLabels = assertNotNull(complete(source, statementStart + 3, explicit = false))
      .options
      .map { item -> item.label }
    val scopeLabels = assertNotNull(complete(source, statementStart, explicit = true))
      .options
      .map { item -> item.label }

    assertTrue("message" in prefixLabels)
    assertTrue("person" in scopeLabels)
    assertTrue("insideOnly" !in scopeLabels)
  }

  /** 显式补全应包含参数、字段、类型和 Java 关键字。 */
  @Test
  fun explicitCompletionIncludesLexicalAndTeachingCatalog() = runTest {
    val source = """
      class Main {
        int total;
        void run(String input) {

        }
      }
    """.trimIndent()
    val position = source.lastIndexOf("\n  }")

    val labels = assertNotNull(complete(source, position, explicit = true))
      .options
      .map { item -> item.label }

    assertTrue("input" in labels)
    assertTrue("total" in labels)
    assertTrue("String" in labels)
    assertTrue("return" in labels)
  }

  /** 常用 JDK 类型和短 receiver 链无需 classpath 也应提供稳定成员。 */
  @Test
  fun completionProvidesBuiltinReceiverMembers() = runTest {
    val stringSource = """
      class Main {
        void run(String text) {
          text.sub
        }
      }
    """.trimIndent()
    val printSource = """
      class Main {
        void run() {
          System.out.pr
        }
      }
    """.trimIndent()

    assertTrue(
      assertNotNull(complete(stringSource, stringSource.indexOf("sub") + 3, explicit = false))
        .options
        .any { item -> item.label == "substring" },
    )
    assertTrue(
      assertNotNull(complete(printSource, printSource.indexOf("pr") + 2, explicit = false))
        .options
        .any { item -> item.label == "println" },
    )
  }

  /** 声明注解的类型名不得覆盖字段自身的静态类型。 */
  @Test
  fun annotatedFieldRetainsReceiverType() = runTest {
    val source = """
      class Main {
        @Deprecated String text;
        void run() { text.sub }
      }
    """.trimIndent()
    val result = complete(
      source,
      source.indexOf("text.sub") + "text.sub".length,
      explicit = false,
    )

    assertTrue(assertNotNull(result).options.any { item -> item.label == "substring" })
  }

  /** 可确定静态类型的工作区对象应提供其字段和方法。 */
  @Test
  fun completionProvidesWorkspaceReceiverMembers() = runTest {
    val source = """
      class Student {
        int score;
        int average() { return score; }
      }
      class Main {
        void run(Student student) {
          student.av
        }
      }
    """.trimIndent()

    val completion = complete(source, source.indexOf("student.av") + "student.av".length, explicit = false)

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "average" })
  }

  /** 局部定义和引用应按遮蔽作用域绑定。 */
  @Test
  fun definitionAndReferencesRespectShadowing() = runTest {
    val source = """
      class Main {
        void run() {
          int value = 1;
          {
            int value = 2;
            System.out.println(value);
          }
          System.out.println(value);
        }
      }
    """.trimIndent()
    val innerUse = source.indexOf("value);")
    val outerUse = source.lastIndexOf("value);")

    val innerDefinition = assertNotNull(definition(source, innerUse + 2))
    val outerDefinition = assertNotNull(definition(source, outerUse + 2))

    assertEquals(source.indexOf("value = 2"), innerDefinition.definition.range.from)
    assertEquals(source.indexOf("value = 1"), outerDefinition.definition.range.from)
    assertEquals(
      listOf(outerUse),
      assertNotNull(references(source, source.indexOf("value = 1") + 2))
        .references
        .map { location -> location.range.from },
    )
  }

  /** 安全重命名应更新同一绑定并拒绝保留字和名称捕获。 */
  @Test
  fun renameIsScopeSafe() = runTest {
    val source = """
      class Main {
        void run(int count) {
          int total = count;
          System.out.println(count);
        }
      }
    """.trimIndent()
    val position = source.indexOf("count") + 2

    val renamed = assertNotNull(rename(source, position, "amount"))
    assertTrue(renamed.isSuccess)
    assertEquals(3, renamed.edits.size)
    assertTrue(source.applyEdits(renamed.edits, MAIN_FILE_PATH).contains("run(int amount)"))
    assertEquals("reserved_word", rename(source, position, "class")?.rejectionCode)
    assertEquals("name_conflict", rename(source, position, "total")?.rejectionCode)
  }

  /** catch 参数只在对应 catch 块内可见。 */
  @Test
  fun catchParameterDoesNotLeakIntoMethodScope() = runTest {
    val source = """
      class Main {
        void run() {
          try {
            System.out.println("run");
          } catch (Exception error) {
            System.out.println(error);
          }
          System.out.println(error);
        }
      }
    """.trimIndent()

    val definition = JavaDynamicLanguageService.definition(
      workspaceOf(MAIN_FILE_PATH to source),
      MAIN_FILE_PATH,
      source.lastIndexOf("error") + 2,
    )

    assertNull(definition)
  }

  /** 显式 import 的类型使用应跳转到目标 package 中的工作区声明。 */
  @Test
  fun importedTypeDefinitionJumpsAcrossFiles() = runTest {
    val studentSource = """
      package school.model;
      class Student {
        int average() { return 100; }
      }
    """.trimIndent()
    val mainSource = """
      package school.app;
      import school.model.Student;
      class Main {
        Student student = new Student();
      }
    """.trimIndent()
    val workspace = workspaceOf(
      "school/model/Student.java" to studentSource,
      MAIN_FILE_PATH to mainSource,
    )

    val definition = JavaDynamicLanguageService.definition(
      workspace,
      MAIN_FILE_PATH,
      mainSource.lastIndexOf("Student") + 2,
    )

    val location = assertNotNull(definition).definition
    assertEquals("school/model/Student.java", location.filePath)
    assertEquals(studentSource.indexOf("Student"), location.range.from)
  }

  /** imported 类型的实例应复用目标文件成员索引。 */
  @Test
  fun importedTypeProvidesMemberCompletion() = runTest {
    val studentSource = """
      package school.model;
      class Student {
        int average() { return 100; }
      }
    """.trimIndent()
    val mainSource = """
      package school.app;
      import school.model.Student;
      class Main {
        void run(Student student) {
          student.av
        }
      }
    """.trimIndent()
    val workspace = workspaceOf(
      "school/model/Student.java" to studentSource,
      MAIN_FILE_PATH to mainSource,
    )

    val completion = JavaDynamicLanguageService.complete(
      workspace,
      MAIN_FILE_PATH,
      mainSource.indexOf("student.av") + "student.av".length,
      explicit = false,
    )

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "average" })
  }

  /** 工作区内可唯一解析的父类成员应参与补全和定义跳转。 */
  @Test
  fun workspaceInheritanceProvidesMembersAndDefinition() = runTest {
    val baseSource = "package school.model; class Person { void study() {} }"
    val studentSource = "package school.model; class Student extends Person {}"
    val mainSource = """
      package school.app;
      import school.model.Student;
      class Main {
        void run(Student student) {
          student.study();
        }
      }
    """.trimIndent()
    val workspace = workspaceOf(
      "school/model/Person.java" to baseSource,
      "school/model/Student.java" to studentSource,
      MAIN_FILE_PATH to mainSource,
    )
    val completionPosition = mainSource.indexOf("student.stu") + "student.stu".length

    val completion = JavaDynamicLanguageService.complete(
      workspace,
      MAIN_FILE_PATH,
      completionPosition,
      explicit = false,
    )
    val definition = JavaDynamicLanguageService.definition(
      workspace,
      MAIN_FILE_PATH,
      mainSource.indexOf("study") + 2,
    )

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "study" })
    assertEquals("school/model/Person.java", assertNotNull(definition).definition.filePath)
  }

  /** 自定义泛型类型的实际类型参数应传播到方法返回值和 receiver 补全。 */
  @Test
  fun genericTypeArgumentPropagatesToMethodResult() = runTest {
    val source = """
      class Student { void study() {} }
      class Box<T> { T get() { return null; } }
      class Main {
        void run(Box<Student> box) { box.get().stu }
      }
    """.trimIndent()

    val completion = complete(source, source.indexOf(".stu") + 4, explicit = false)

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "study" })
  }

  /** 泛型父类型的实参应沿继承链代换到继承成员。 */
  @Test
  fun genericInheritanceSubstitutesParentMembers() = runTest {
    val source = """
      class Student { void study() {} }
      class Parent<T> { T value() { return null; } }
      class Child extends Parent<Student> {}
      class Main {
        void run(Child child) { child.value().stu }
      }
    """.trimIndent()

    val completion = complete(source, source.indexOf(".stu") + 4, explicit = false)

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "study" })
  }

  /** 泛型方法应从实参推断返回类型，常见链式调用无需显式类型实参。 */
  @Test
  fun genericMethodInfersReturnTypeFromArgument() = runTest {
    val source = """
      class Student { void study() {} }
      class Helper { <T> T identity(T value) { return value; } }
      class Main {
        void run(Helper helper, Student student) { helper.identity(student).stu }
      }
    """.trimIndent()

    val completion = complete(source, source.indexOf(".stu") + 4, explicit = false)

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "study" })
  }

  /** extends 通配符读取结果应使用上界，以支持日常只读容器提示。 */
  @Test
  fun wildcardUpperBoundProvidesReadableMemberType() = runTest {
    val source = """
      class Student { void study() {} }
      class Box<T> { T get() { return null; } }
      class Main {
        void run(Box<? extends Student> box) { box.get().stu }
      }
    """.trimIndent()

    val completion = complete(source, source.indexOf(".stu") + 4, explicit = false)

    assertTrue(assertNotNull(completion).options.any { item -> item.label == "study" })
  }

  /** 重载应按实参类型选择定义，而非仅依赖方法名或声明顺序。 */
  @Test
  fun overloadResolutionUsesArgumentType() = runTest {
    val source = """
      class Helper {
        String parse(String value) { return value; }
        Object parse(Object value) { return value; }
      }
      class Main { Object run(Helper helper) { return helper.parse("text"); } }
    """.trimIndent()

    val definition = definition(source, source.lastIndexOf("parse") + 2)

    assertEquals(source.indexOf("parse(String"), assertNotNull(definition).definition.range.from)
  }

  /** null 实参应选择引用层级中更具体的重载。 */
  @Test
  fun nullOverloadSelectsMoreSpecificReferenceType() = runTest {
    val source = """
      class Student {}
      class Helper {
        Student choose(Student value) { return value; }
        Object choose(Object value) { return value; }
      }
      class Main { Object run(Helper helper) { return helper.choose(null); } }
    """.trimIndent()

    val definition = definition(source, source.lastIndexOf("choose") + 2)

    assertEquals(source.indexOf("choose(Student"), assertNotNull(definition).definition.range.from)
  }

  /** 子类 override 应遮蔽父类同签名方法，同时保留参数不同的 overload。 */
  @Test
  fun overrideAndOverloadRemainDistinct() = runTest {
    val source = """
      class Parent { Object pick(Object value) { return value; } }
      class Child extends Parent {
        Object pick(Object value) { return value; }
        String pick(String value) { return value; }
      }
      class Main { Object run(Child child, Object value) { return child.pick(value); } }
    """.trimIndent()

    val definition = definition(source, source.lastIndexOf("pick") + 2)

    assertEquals(source.indexOf("pick(Object", source.indexOf("class Child")), assertNotNull(definition).definition.range.from)
  }

  /** 泛型方法边界不满足时应排除候选，而不是错误吸收任意类型。 */
  @Test
  fun genericMethodBoundsFilterOverloadCandidates() = runTest {
    val source = """
      class Helper {
        <T extends Number> T select(T value) { return value; }
        String select(String value) { return value; }
      }
      class Main { String run(Helper helper) { return helper.select("text"); } }
    """.trimIndent()

    val definition = definition(source, source.lastIndexOf("select") + 2)

    assertEquals(source.indexOf("select(String"), assertNotNull(definition).definition.range.from)
  }

  /** receiver 类型存在同名重载时，应按实参数量选择唯一声明。 */
  @Test
  fun overloadedReceiverMemberResolvesByArity() = runTest {
    val studentSource = """
      package school.model;
      class Student {
        int score() { return 0; }
        int score(int bonus) { return bonus; }
      }
    """.trimIndent()
    val mainSource = """
      package school.app;
      import school.model.Student;
      class Main {
        int run(Student student) { return student.score(); }
      }
    """.trimIndent()
    val workspace = workspaceOf(
      "school/model/Student.java" to studentSource,
      MAIN_FILE_PATH to mainSource,
    )

    val definition = JavaDynamicLanguageService.definition(
      workspace,
      MAIN_FILE_PATH,
      mainSource.lastIndexOf("score") + 2,
    )

    assertEquals(studentSource.indexOf("score()"), assertNotNull(definition).definition.range.from)
  }

  /** 可变参数应按实际参数数量展开，并在固定参数不匹配时参与重载选择。 */
  @Test
  fun varargOverloadResolvesByExpandedArity() = runTest {
    val source = """
      class Helper {
        void log(String label) {}
        void log(String label, Object... values) {}
      }
      class Main { void run(Helper helper) { helper.log("count", 1); } }
    """.trimIndent()

    val definition = definition(source, source.lastIndexOf("log") + 1)

    assertEquals(source.indexOf("log(String label, Object"), assertNotNull(definition).definition.range.from)
  }

  /** 嵌套类型的父类不得被误认为外层类型的父类。 */
  @Test
  fun nestedTypeInheritanceDoesNotLeakToOuterType() = runTest {
    val typesSource = """
      package school.model;
      class Base { void baseOnly() {} }
      class Outer { class Inner extends Base {} }
    """.trimIndent()
    val mainSource = """
      package school.app;
      import school.model.Outer;
      class Main {
        void run(Outer outer) { outer.ba }
      }
    """.trimIndent()
    val completion = JavaDynamicLanguageService.complete(
      workspaceOf("school/model/Types.java" to typesSource, MAIN_FILE_PATH to mainSource),
      MAIN_FILE_PATH,
      mainSource.indexOf("outer.ba") + "outer.ba".length,
      explicit = false,
    )

    assertNull(completion)
  }

  /** package-private 顶层类型可跨 import 统一查询引用和生成源码重命名。 */
  @Test
  fun workspaceTypeReferencesAndRenameStayConsistent() = runTest {
    val studentSource = "package school.model; class Student {}"
    val mainSource = """
      package school.app;
      import school.model.Student;
      class Main {
        Student first;
        Student second;
      }
    """.trimIndent()
    val workspace = workspaceOf(
      "school/model/Student.java" to studentSource,
      MAIN_FILE_PATH to mainSource,
    )

    val references = JavaDynamicLanguageService.references(
      workspace,
      "school/model/Student.java",
      studentSource.indexOf("Student") + 2,
    )
    val renamed = JavaDynamicLanguageService.rename(
      workspace,
      "school/model/Student.java",
      studentSource.indexOf("Student") + 2,
      "Learner",
    )

    assertEquals(3, assertNotNull(references).references.size)
    assertTrue(assertNotNull(renamed).isSuccess)
    assertEquals(4, renamed.edits.size)
    assertTrue(mainSource.applyEdits(renamed.edits, MAIN_FILE_PATH).contains("import school.model.Learner;"))
  }

  /** 类型重命名必须同步修改显式构造器声明。 */
  @Test
  fun typeRenameIncludesExplicitConstructor() = runTest {
    val source = """
      package school.model;
      class Student {
        Student() {}
        static Student create() { return new Student(); }
      }
    """.trimIndent()

    val renamed = JavaDynamicLanguageService.rename(
      workspaceOf(MAIN_FILE_PATH to source),
      MAIN_FILE_PATH,
      source.indexOf("Student") + 2,
      "Learner",
    )
    val edits = assertNotNull(renamed).edits

    assertEquals(4, edits.size)
    assertTrue(source.applyEdits(edits, MAIN_FILE_PATH).contains("Learner() {}"))
  }

  /** public 顶层类型应原子返回源码编辑和 Java 文件路径重命名。 */
  @Test
  fun publicTopLevelTypeRenameIncludesFileRename() = runTest {
    val source = "public class Student {}"
    val filePath = "school/model/Student.java"

    val result = JavaDynamicLanguageService.rename(
      workspaceOf(filePath to source),
      filePath,
      source.indexOf("Student") + 2,
      "Learner",
    )

    val renamed = assertNotNull(result)
    assertTrue(renamed.isSuccess)
    assertEquals("school/model/Learner.java", renamed.fileRenames.single().newPath)
    assertEquals(filePath, renamed.fileRenames.single().oldPath)
    assertTrue(source.applyEdits(renamed.edits, filePath).contains("public class Learner"))
  }

  /** 存在方法重载时不得猜测调用目标并批量改名。 */
  @Test
  fun overloadedMethodRenameIsRejected() = runTest {
    val source = """
      class Main {
        void print(int value) {}
        void print(String value) {}
        void run() { print(1); }
      }
    """.trimIndent()

    val result = rename(source, source.indexOf("print") + 2, "show")

    assertEquals("ambiguous_overload", result?.rejectionCode)
  }

  /** 字符串和注释中的普通输入不应触发补全。 */
  @Test
  fun completionIsSuppressedInsideCommentsAndStrings() = runTest {
    val source = """
      class Main {
        // ret
        String value = "ret";
      }
    """.trimIndent()

    assertNull(complete(source, source.indexOf("ret") + 3, explicit = true))
    assertNull(complete(source, source.lastIndexOf("ret") + 3, explicit = true))
  }

  /** 入口发现应同时识别标准 main 和阶段 0 可执行的无参数 main，并忽略普通 static 方法。 */
  @Test
  fun runTargetsFindJavaMainMethodsAcrossWorkspace() = runTest {
    val standardSource = """
      package lesson;
      public class Main {
        public static void main(String[] args) {}
        public static void helper() {}
      }
    """.trimIndent()
    val stageZeroSource = """
      package lesson;
      public class Counter {
        public static int main() { return 15; }
      }
    """.trimIndent()
    val workspace = DynamicLanguageWorkspace(
      listOf(
        DynamicSourceFile("Main.java", standardSource),
        DynamicSourceFile("Counter.java", stageZeroSource),
      ),
    )

    val targets = JavaDynamicLanguageService.runTargets(workspace, "Main.java")

    assertEquals(listOf("lesson.Counter.main", "lesson.Main.main"), targets.map { it.displayName }.sorted())
    assertTrue(targets.all { target -> target.location != null && target.entry.position != null })
  }

  /** 完全相同的运行请求应命中结果缓存，改动源码则复用独立编译语法树的增量片段。 */
  @Test
  fun cachesCompilationAndReportsIncrementalRebuilds() = runTest {
    fun request(returnValue: Int) = DynamicCompilationRequest(
      workspace = DynamicLanguageWorkspace(
        listOf(
          DynamicSourceFile(
            "CacheMetrics.java",
            "class CacheMetrics { static int run() { return $returnValue; } }",
          ),
        ),
      ),
      entry = DynamicProgramEntry("CacheMetrics.java", position = 32),
    )

    val first = JavaDynamicLanguageService.compile(request(1))
    val exact = JavaDynamicLanguageService.compile(request(1))
    val changed = JavaDynamicLanguageService.compile(request(2))

    assertNotNull(first.program)
    assertTrue(first.metrics?.cacheMode != DynamicCompilationCacheMode.EXACT)
    assertEquals(DynamicCompilationCacheMode.EXACT, exact.metrics?.cacheMode)
    assertEquals(0, exact.metrics?.totalMicroseconds)
    assertEquals(DynamicCompilationCacheMode.INCREMENTAL, changed.metrics?.cacheMode)
    assertNotNull(changed.program)
  }

  /** 超过语言包上限的工作区必须在 parser 前返回结构化诊断。 */
  @Test
  fun rejectsCompilationWorkspaceOverFileLimit() = runTest {
    val files = List(129) { index ->
      DynamicSourceFile("Limit$index.java", "class Limit$index {}")
    }
    val result = JavaDynamicLanguageService.compile(
      DynamicCompilationRequest(
        workspace = DynamicLanguageWorkspace(files),
        entry = DynamicProgramEntry(files.first().path),
      ),
    )

    assertNull(result.program)
    assertEquals("java.compilation.too_many_files", result.diagnostics.single().code)
  }

  /** 生成分发器应完整暴露 DynamicLanguageService 协议并支持重复初始化。 */
  @Test
  fun generatedDispatcherInvokesService() = runTest {
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_java()
    __cyxbsNpmJsServiceInitialize__cyxbs_mobile_language_java()
    val bridge: dynamic = js("globalThis.CyxbsNpmJsService")

    assertTrue(bridge != undefined)
    assertEquals(SERVICE_ID, _JavaDynamicLanguageServiceNpmJsDispatcher.serviceId)
    assertEquals(
      setOf("compile", "complete", "definition", "fileIcon", "highlight", "references", "rename", "runTargets"),
      _JavaDynamicLanguageServiceNpmJsDispatcher.methodNames,
    )
    val describedMethods = Json.decodeFromString<List<String>>(bridge.describe(SERVICE_ID) as String)
    assertEquals(_JavaDynamicLanguageServiceNpmJsDispatcher.methodNames, describedMethods.toSet())

    val icon = Json.decodeFromString<DynamicLanguageIcon>(
      _JavaDynamicLanguageServiceNpmJsDispatcher.invoke("fileIcon", "[]"),
    )
    assertEquals(24F, icon.viewportWidth)
    assertTrue(icon.paths.all { path -> path.pathData.isNotBlank() })

    val highlighted = Json.decodeFromString<DynamicHighlightResult>(
      _JavaDynamicLanguageServiceNpmJsDispatcher.invoke(
        "highlight",
        """[{"files":[{"path":"Main.java","source":"class Main { int answer = 42; }"}]},"Main.java"]""",
      ),
    )
    assertTrue(highlighted.spans.stylesFor("class Main { int answer = 42; }", "42").contains("tok-number"))

    val completed = Json.decodeFromString<DynamicCompletionResult?>(
      _JavaDynamicLanguageServiceNpmJsDispatcher.invoke(
        "complete",
        """[{"files":[{"path":"Main.java","source":"ret"}]},"Main.java",3,false]""",
      ),
    )
    assertTrue(assertNotNull(completed).options.any { item -> item.label == "return" })
  }

  private companion object {
    const val MAIN_FILE_PATH = "Main.java"
    const val SERVICE_ID = "com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService"
  }

  /** 构造单文件工作区。 */
  private fun singleFileWorkspace(source: String): DynamicLanguageWorkspace {
    return DynamicLanguageWorkspace(listOf(DynamicSourceFile(MAIN_FILE_PATH, source)))
  }

  /** 按相对路径构造多文件工作区。 */
  private fun workspaceOf(vararg files: Pair<String, String>): DynamicLanguageWorkspace {
    return DynamicLanguageWorkspace(
      files.map { (path, source) -> DynamicSourceFile(path = path, source = source) },
    )
  }

  /** 调用单文件高亮。 */
  private suspend fun highlight(source: String): DynamicHighlightResult {
    return JavaDynamicLanguageService.highlight(singleFileWorkspace(source), MAIN_FILE_PATH)
  }

  /** 调用单文件补全。 */
  private suspend fun complete(
    source: String,
    position: Int,
    explicit: Boolean,
  ): DynamicCompletionResult? {
    return JavaDynamicLanguageService.complete(
      singleFileWorkspace(source),
      MAIN_FILE_PATH,
      position,
      explicit,
    )
  }

  /** 调用单文件定义查询。 */
  private suspend fun definition(source: String, position: Int) =
    JavaDynamicLanguageService.definition(singleFileWorkspace(source), MAIN_FILE_PATH, position)

  /** 调用单文件引用查询。 */
  private suspend fun references(source: String, position: Int) =
    JavaDynamicLanguageService.references(singleFileWorkspace(source), MAIN_FILE_PATH, position)

  /** 调用单文件安全重命名。 */
  private suspend fun rename(source: String, position: Int, newName: String) =
    JavaDynamicLanguageService.rename(singleFileWorkspace(source), MAIN_FILE_PATH, position, newName)

  /** 返回与指定源码文本完全重合的高亮样式。 */
  private fun List<DynamicHighlightSpan>.stylesFor(source: String, text: String): List<String> {
    val from = source.indexOf(text)
    require(from >= 0) { "Text not found in source: $text" }
    return firstOrNull { span -> span.from == from && span.to == from + text.length }
      ?.styleIds
      .orEmpty()
  }

  /** 按位置倒序应用协议修改，保持后续 UTF-16 偏移不变。 */
  private fun String.applyEdits(edits: List<DynamicSourceEdit>, filePath: String): String {
    return edits.asSequence()
      .filter { sourceEdit -> sourceEdit.filePath == filePath }
      .map(DynamicSourceEdit::edit)
      .sortedByDescending { edit -> edit.from }
      .fold(this) { source, edit -> source.replaceRange(edit.from, edit.to, edit.replacement) }
  }
}
