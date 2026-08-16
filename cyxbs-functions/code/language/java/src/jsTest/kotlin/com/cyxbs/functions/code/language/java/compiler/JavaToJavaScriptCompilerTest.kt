package com.cyxbs.functions.code.language.java.compiler

import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 使用真实 Lezer CST 验证源码到可执行 JavaScript 的完整阶段 0 链路。 */
class JavaToJavaScriptCompilerTest {
  /** classic for、局部变量和 int 运算应贯穿所有阶段并产生正确运行结果。 */
  @Test
  fun compilesAndExecutesSumProgram() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "sum",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int sum(int limit) {
            int result = 0;
            for (int index = 0; index < limit; index++) {
              result += index;
            }
            return result;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(10, executeEntry(artifact, 5))
  }

  /** 多文件 TypeName static 调用以及 if/while/void 必须由同一默认流水线处理。 */
  @Test
  fun compilesCrossFileControlFlow() {
    val result = compile(
      entryClass = "app.lesson.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "app/lesson/Main.java" to """
        package app.lesson;

        import library.Helper;

        class Main {
          static int run(int value) {
            return Helper.normalize(value);
          }
        }
      """.trimIndent(),
      "library/Helper.java" to """
        package library;

        public class Helper {
          public static int normalize(int value) {
            int result = 0;
            while (value > 0) {
              if (value == 2) {
                value--;
              } else {
                value -= 2;
              }
              result++;
            }
            return result;
          }

          public static void noOperation() {
            return;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(3, executeEntry(artifact, 5))
  }

  /** import 后的引用参数必须使用语义限定名生成入口 descriptor，而不是源码 simple name。 */
  @Test
  fun compilesImportedReferenceDescriptor() {
    val result = compile(
      entryClass = "app.Main",
      entryMethod = "isMissing",
      descriptor = "(Llibrary/Helper;)Z",
      "app/Main.java" to """
        package app;

        import library.Helper;

        class Main {
          static boolean isMissing(Helper helper) {
            return helper == null;
          }
        }
      """.trimIndent(),
      "library/Helper.java" to "package library; public class Helper { }",
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(true, executeEntryValue(artifact, null) as Boolean)
  }

  /** 编辑器入口位置应选择所在 static 方法，并在 Java 包内部推导 descriptor。 */
  @Test
  fun compilesEntrySelectedBySourcePosition() {
    val source = """
      package demo;
      class Main {
        static int first() { return 1; }
        static int selected() { return 2; }
      }
    """.trimIndent()
    val workspace = JavaSourceWorkspace(
      listOf(JavaSourceFile(JavaSourceFileId(0), "demo/Main.java", source)),
    )

    val result = JavaToJavaScriptCompiler.compile(
      workspace = workspace,
      entryPoint = JavaCompilerSourceEntryPoint(
        filePath = "demo/Main.java",
        position = source.indexOf("selected") + 2,
      ),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(2, (executeEntryValue(artifact, null) as Number).toInt())
  }

  /** 未指定位置且入口文件含多个 static 方法时不得猜测执行目标。 */
  @Test
  fun rejectsAmbiguousSourceEntry() {
    val source = "class Main { static int first(){return 1;} static int second(){return 2;} }"
    val result = JavaToJavaScriptCompiler.compile(
      workspace = JavaSourceWorkspace(
        listOf(JavaSourceFile(JavaSourceFileId(0), "Main.java", source)),
      ),
      entryPoint = JavaCompilerSourceEntryPoint(filePath = "Main.java", position = null),
    )

    assertEquals(null, result.value)
    assertTrue(result.diagnostics.any { diagnostic ->
      diagnostic.code == "JAVA_ENTRY_AMBIGUOUS"
    })
  }

  /** 创建包含稳定入口 descriptor 的完整编译请求。 */
  private fun compile(
    entryClass: String,
    entryMethod: String,
    descriptor: String,
    vararg sources: Pair<String, String>,
  ) = JavaToJavaScriptCompiler.compile(
    JavaCompilerRequest(
      workspace = JavaSourceWorkspace(
        sources.mapIndexed { index, source ->
          JavaSourceFile(JavaSourceFileId(index), source.first, source.second)
        },
      ),
      entryPoint = JavaCompilerEntryPoint(entryClass, entryMethod, descriptor),
    ),
  )

  /**
   * Node 测试中把唯一 ES Module 的导出声明转换为 Function 返回值并执行。
   *
   * 这里只验证生成源码的真实 JavaScript 语义；端上仍由 QuickJS Module Loader 原样加载 ES Module。
   */
  private fun executeEntry(
    artifact: JavaScriptProgramArtifact,
    argument: Int,
  ): Int {
    val value = executeEntryValue(artifact, argument)
    assertTrue(value is Number, "Generated entry must return a JavaScript number.")
    return (value as Number).toInt()
  }

  /** 执行任意阶段 0 入口，供引用与 boolean descriptor 用例复用。 */
  private fun executeEntryValue(
    artifact: JavaScriptProgramArtifact,
    argument: dynamic,
  ): dynamic {
    assertEquals(1, artifact.modules.size)
    val moduleSource = artifact.modules.single().source
    val executableSource = moduleSource.replace(
      "export function " + artifact.entryExportName,
      "function " + artifact.entryExportName,
    ) + "\nreturn " + artifact.entryExportName + ";"
    val constructor: dynamic = js("Function")
    val entry: dynamic = constructor(executableSource)()
    return entry(argument)
  }
}
