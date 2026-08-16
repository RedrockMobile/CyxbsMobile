package com.cyxbs.functions.code.language.java.compiler

import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 使用真实 Lezer CST 验证源码到可执行 JavaScript 的完整 Stage1 链路。 */
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

  /** 构造器、父类字段和 override 必须从源码贯穿到 JavaScript 虚分派结果。 */
  @Test
  fun compilesConstructorsInheritanceAndVirtualDispatch() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        class Base {
          int value;
          Base(int value) { this.value = value; }
          int score() { return value; }
        }

        class Child extends Base {
          Child(int value) { super(value); }
          @Override int score() { return value + 2; }
        }

        class Main {
          static int run(int value) {
            Base item = new Child(value);
            return item.score();
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(7, executeEntry(artifact, 5))
  }

  /** 参数化字段、继承代换和泛型返回值必须在擦除后保持正确运行结果。 */
  @Test
  fun compilesGenericClassAndInheritedSubstitution() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()Z",
      "demo/Main.java" to """
        package demo;

        class Box<T> {
          T value;
          Box(T value) { this.value = value; }
          T get() { return value; }
        }

        class StringBox extends Box<String> {
          StringBox(String value) { super(value); }
        }

        class Main {
          static boolean run() {
            Box<String> box = new StringBox("ok");
            return box.get() != null;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(true, executeEntryValue(artifact, null) as Boolean)
  }

  /** 泛型父类的方法代换后仍应被子类协变返回的 override 复用同一虚槽。 */
  @Test
  fun compilesGenericOverrideWithCovariantReturn() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()Z",
      "demo/Main.java" to """
        package demo;

        class Base<T> {
          T value(T input) { return null; }
        }

        class Child extends Base<String> {
          @Override String value(String input) { return input; }
        }

        class Main {
          static boolean run() {
            Base<String> item = new Child();
            return item.value("child") != null;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(true, executeEntryValue(artifact, null) as Boolean)
  }

  /** 方法类型参数的上界必须先应用所属类的类型实参，再执行调用点推断。 */
  @Test
  fun compilesMethodTypeBoundUsingOwnerSubstitution() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()Z",
      "demo/Main.java" to """
        package demo;

        class Converter<T> {
          <U extends T> U identity(U value) { return value; }
        }

        class Main {
          static boolean run() {
            Converter<Object> converter = new Converter<Object>();
            return converter.identity("ok") != null;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(true, executeEntryValue(artifact, null) as Boolean)
  }

  /** static 调用必须先求值实参，再于实际调用点触发目标类初始化。 */
  @Test
  fun evaluatesStaticArgumentsBeforeTargetClassInitialization() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        class Target {
          static int trigger = Main.markInitialized();
          static int accept(int value) { return value; }
        }

        class Main {
          static int state = 1;
          static int readState() { return state; }
          static int markInitialized() { state = 2; return 0; }

          static int run() {
            int argument = Target.accept(readState());
            return argument * 10 + state;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(12, (executeEntryValue(artifact, null) as Number).toInt())
  }

  /** static 字段写入必须先求值右侧表达式，再于实际写入点初始化字段所属类。 */
  @Test
  fun evaluatesStaticFieldValueBeforeTargetClassInitialization() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        class Target {
          static int trigger = Main.markInitialized();
          static int value;
        }

        class Main {
          static int state = 1;
          static int readState() { return state; }
          static int markInitialized() { state = 2; return 0; }

          static int run() {
            Target.value = readState();
            return Target.value * 10 + state;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(12, (executeEntryValue(artifact, null) as Number).toInt())
  }

  /** 一维数组的初始化器、默认值、索引读写、复合赋值与 length 应贯穿完整编译链。 */
  @Test
  fun compilesOneDimensionalArrayCoreOperations() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int run() {
            int[] values = new int[]{2, 3, 4};
            boolean[] flags = new boolean[1];
            String[] labels = new String[1];
            values[1] += values[0];
            values[2]++;
            if (!flags[0] && labels[0] == null) {
              return values.length * 100 + values[0] * 10 + values[1] + values[2];
            }
            return -1;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(330, (executeEntryValue(artifact, null) as Number).toInt())
  }

  /** 数组写入必须按 receiver、index、右值顺序各求值一次。 */
  @Test
  fun preservesArrayWriteEvaluationOrder() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int state = 0;
          static int[] values = new int[1];
          static int[] array() { state = state * 10 + 1; return values; }
          static int index() { state = state * 10 + 2; return 0; }
          static int value() { state = state * 10 + 3; return 7; }

          static int run() {
            array()[index()] = value();
            return state * 10 + values[0];
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(1237, (executeEntryValue(artifact, null) as Number).toInt())
  }

  /** String 拼接应保持 Java 左结合以及 null、boolean、char 和 int 的转换规则。 */
  @Test
  fun compilesJavaStringConcatenation() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()Ljava/lang/String;",
      "demo/Main.java" to """
        package demo;

        class Main {
          static String run() {
            String value = "" + 1 + true + null;
            value += 'A';
            return value + ":" + (1 + 2);
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals("1truenullA:3", executeEntryValue(artifact, null) as String)
  }

  /** 数组复合赋值先按 int 运算，再在 store 边界窄化为 byte/char。 */
  @Test
  fun compilesPrimitiveArrayNarrowing() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int run() {
            byte[] bytes = new byte[1];
            char[] chars = new char[1];
            bytes[0] += 128;
            chars[0]--;
            return bytes[0] + chars[0];
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(65407, executeEntry(artifact, 0))
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

  /** 执行任意 Stage1 入口，供引用、对象与 boolean descriptor 用例复用。 */
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
