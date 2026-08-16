package com.cyxbs.functions.code.language.java.compiler

import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi
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

  /** 精选类库必须贯穿源码、语义、IR 和 JS runtime，并通过稳定 ABI 分流输出。 */
  @Test
  fun compilesAndExecutesBuiltinJavaLibrary() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int run() {
            String text = "A😀B";
            String missing = null;
            System.out.print(true);
            System.out.print('A');
            System.out.print(-7);
            System.out.println(missing);
            System.err.println("bad");
            int score = text.length() + text.indexOf(128512);
            if (text.substring(1, 3).equals("😀")) score += 1;
            return score + Math.abs(-7) + Math.min(3, 4) + Math.max(3, 4);
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    var stdout = ""
    var stderr = ""
    val entry = createEntry(
      artifact,
      stdout = { text -> stdout += text },
      stderr = { text -> stderr += text },
    )
    assertEquals(20, (entry() as Number).toInt())
    assertEquals("trueA-7null\n", stdout)
    assertEquals("bad\n", stderr)
  }

  /** String 边界必须抛 Java 风格异常，且 null receiver 要在参数副作用完成后检查。 */
  @Test
  fun preservesBuiltinStringFailuresAndEvaluationOrder() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static String mark() { System.out.print("arg"); return "x"; }
          static int run(int mode) {
            String value = "a";
            if (mode == 0) return value.charAt(1);
            if (mode == 1) return value.substring(0, 2).length();
            if (mode == 2) return value.indexOf(null);
            if (mode == 3) { value.contains(null); return 0; }
            if (mode == 4) { value.startsWith(null); return 0; }
            if (mode == 5) { value.endsWith(null); return 0; }
            value = null;
            value.contains(mark());
            return 0;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    for (mode in 0..5) {
      val entry = createEntry(artifact, {}, {})
      val message = executionFailure { entry(mode) }
      val expected = if (mode <= 1) "StringIndexOutOfBoundsException" else "NullPointerException"
      assertTrue(expected in message, "mode=$mode, message=$message")
    }
    var stdout = ""
    val entry = createEntry(artifact, { text -> stdout += text }, {})
    val message = executionFailure { entry(6) }
    assertTrue("NullPointerException" in message)
    assertEquals("arg", stdout)
  }

  /** wrapper 缓存、自动装拆箱与 StringBuilder 可观察行为应贯穿完整源码编译链。 */
  @Test
  fun compilesBoxingAndStringBuilderRuntime() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int run(int input) {
            Integer first = input;
            Integer second = Integer.valueOf(input);
            if (first != second) return -1;
            Integer farA = 1000;
            Integer farB = 1000;
            if (farA == farB) return -2;
            Number number = first;
            first++;
            first += 2;

            Boolean flag = Boolean.valueOf(true);
            Character letter = Character.valueOf('A');
            if (!flag.booleanValue() || letter.charValue() != 'A') return -3;

            StringBuilder builder = new StringBuilder("x");
            String nullable = null;
            builder.append(true).append('A').append(first).append(nullable);
            builder.setCharAt(0, 'X');
            if (!builder.reverse().reverse().toString().equals("XtrueA10null")) return -4;
            String boxedText = "value=" + first;
            if (!boxedText.equals("value=10")) return -5;
            return builder.length() + builder.charAt(0) + number.intValue() + first.intValue();
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(117, executeEntry(artifact, 7))
  }

  /** StringBuilder 的 null 构造参数与越界 API 必须保持 Java 风格异常。 */
  @Test
  fun preservesStringBuilderFailures() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        class Main {
          static int run(int mode) {
            if (mode == 0) { new StringBuilder(null); return 0; }
            StringBuilder builder = new StringBuilder("a");
            if (mode == 1) return builder.charAt(1);
            builder.setCharAt(-1, 'x');
            return 0;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    for (mode in 0..2) {
      val message = executionFailure { createEntry(artifact, {}, {})(mode) }
      assertTrue(
        (if (mode == 0) "NullPointerException" else "StringIndexOutOfBoundsException") in message,
        "mode=$mode, message=$message",
      )
    }
  }

  /** 精选泛型集合必须贯穿 diamond、继承成员代换、装箱和可变别名语义。 */
  @Test
  fun compilesBuiltinGenericCollections() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;

        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.HashSet;
        import java.util.Iterator;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        class Key { }

        class Main {
          static int run() {
            List<Integer> values = new ArrayList<>();
            List<Integer> alias = values;
            values.add(1);
            alias.add(2);
            if (values.set(1, 3) != 2) return -1;
            if (!values.remove(Integer.valueOf(3))) return -2;
            values.add(4);
            if (values.remove(0) != 1) return -3;
            Iterator<Integer> iterator = values.iterator();
            values.add(5);
            int sum = 0;
            while (iterator.hasNext()) sum += iterator.next();
            if (!values.contains(Integer.valueOf(4)) || values.indexOf(Integer.valueOf(4)) != 0) return -9;
            values.clear();
            if (!values.isEmpty()) return -10;

            Set<Integer> numbers = new HashSet<>();
            if (!numbers.add(1000)) return -4;
            if (numbers.add(Integer.valueOf(1000))) return -5;
            numbers.add(null);
            if (!numbers.contains(null)) return -6;
            if (!numbers.remove(null)) return -11;
            numbers.add(null);
            Iterator<Integer> numberIterator = numbers.iterator();
            int numberCount = 0;
            while (numberIterator.hasNext()) { numberIterator.next(); numberCount++; }
            if (numberCount != 2 || numbers.isEmpty()) return -12;

            Set<String> temporarySet = new HashSet<>();
            temporarySet.add("x");
            temporarySet.clear();
            if (!temporarySet.isEmpty()) return -13;

            Map<String, Integer> scores = new HashMap<>();
            scores.put("Aa", 10);
            scores.put("BB", 20);
            scores.put(null, 30);
            if (scores.getOrDefault("missing", 7) != 7) return -7;
            if (scores.get("BB") != 20 || scores.isEmpty()) return -14;
            if (scores.remove("BB") != 20) return -15;
            scores.put("BB", 20);
            Set<String> keys = scores.keySet();
            if (!keys.remove("Aa") || scores.containsKey("Aa")) return -8;

            Map<String, Integer> temporaryMap = new HashMap<>();
            temporaryMap.put("x", 1);
            temporaryMap.clear();
            if (!temporaryMap.isEmpty()) return -16;

            Set<Key> identities = new HashSet<>();
            identities.add(new Key());
            identities.add(new Key());
            return sum + numbers.size() * 100 + scores.size() * 10 + identities.size();
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    assertEquals(231, executeEntry(artifact, 0))
  }

  /** 集合越界、iterator 耗尽与只读 keySet.add 必须抛出稳定 Java 命名异常。 */
  @Test
  fun preservesBuiltinCollectionFailures() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.Iterator;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        class Main {
          static int run(int mode) {
            List<Integer> values = new ArrayList<>();
            if (mode == 0) return values.get(0);
            Iterator<Integer> iterator = values.iterator();
            if (mode == 1) return iterator.next();
            Map<String, Integer> map = new HashMap<>();
            Set<String> keys = map.keySet();
            keys.add("x");
            return 0;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    for (mode in 0..2) {
      val message = executionFailure { createEntry(artifact, {}, {})(mode) }
      val expected = when (mode) {
        0 -> "IndexOutOfBoundsException"
        1 -> "NoSuchElementException"
        else -> "UnsupportedOperationException"
      }
      assertTrue(expected in message, "mode=$mode, message=$message")
    }
  }

  /** Object 输出覆盖 wrapper/集合；直接 self-reference 不递归，未知用户对象保持稳定拒绝。 */
  @Test
  fun formatsSupportedObjectValuesAndRejectsUnknownRuntimeObjects() {
    val supported = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()Ljava/lang/String;",
      "demo/Main.java" to """
        package demo;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.List;
        import java.util.Map;
        class Main {
          static String run() {
            List<Object> list = new ArrayList<>();
            list.add(list);
            Map<Object, Object> map = new HashMap<>();
            map.put(map, map);
            Integer missing = null;
            return new StringBuilder().append(missing).append(list).append(map).toString();
          }
        }
      """.trimIndent(),
    )
    val supportedArtifact = assertNotNull(supported.value, supported.diagnostics.toString())
    assertEquals(
      "null[(this Collection)]{(this Map)=(this Map)}",
      executeEntryValue(supportedArtifact, null) as String,
    )

    val unknown = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "()I",
      "demo/Main.java" to """
        package demo;
        class User {}
        class Main {
          static int run() {
            Object value = new User();
            System.out.println(value);
            return 0;
          }
        }
      """.trimIndent(),
    )
    val unknownArtifact = assertNotNull(unknown.value, unknown.diagnostics.toString())
    assertTrue(
      "UnsupportedOperationException" in executionFailure { createEntry(unknownArtifact, {}, {})() },
    )
  }

  /** PrintStream 与 StringBuilder 的 char[] 重载输出 UTF-16 字符，null 数组保持 NPE。 */
  @Test
  fun compilesCharArrayOutputOverloads() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;
        class Main {
          static int run(int mode) {
            if (mode == 1) {
              char[] missing = null;
              System.out.print(missing);
              return 0;
            }
            char[] text = {'o', 'k'};
            System.out.print(text);
            System.out.println(text);
            return new StringBuilder().append(text).length();
          }
        }
      """.trimIndent(),
    )
    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    var output = ""
    assertEquals(2, (createEntry(artifact, { output += it }, {})(0) as Number).toInt())
    assertEquals("okok\n", output)
    assertTrue("NullPointerException" in executionFailure { createEntry(artifact, {}, {})(1) })
  }

  /** Scanner 应共享 System.in cursor，并保持 Unicode token、负整数、CRLF 与空行读取语义。 */
  @Test
  fun compilesAndExecutesBuiltinScanner() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        import java.util.Scanner;

        class Main {
          static int run(int mode) {
            Scanner first = new Scanner(System.in);
            Scanner second = new Scanner(System.in);
            if (mode == 1) {
              if (first.hasNext() || first.hasNextInt() || first.hasNextLine()) return -1;
              return 0;
            }
            if (!first.hasNext() || !"hello".equals(first.next())) return -2;
            if (!"世界".equals(second.next())) return -3;
            if (!first.hasNextInt()) return -4;
            int number = second.nextInt();
            if (number != -12) return -5;
            if (!"".equals(first.nextLine())) return -6;
            if (!"line two".equals(second.nextLine())) return -7;
            if (!"".equals(first.nextLine())) return -8;
            if (!"end".equals(second.nextLine())) return -9;
            if (!"ls".equals(first.nextLine())) return -10;
            if (!"ps".equals(second.nextLine())) return -11;
            if (!"tail".equals(first.nextLine())) return -12;
            if (first.hasNext() || second.hasNextLine()) return -13;
            return 6;
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    val input = "hello 世界 -12\r\nline two\r\n\r\nend\u0085ls\u2028ps\u2029tail"
    assertEquals(6, (createEntry(artifact, {}, {}, input)(0) as Number).toInt())
    assertEquals(0, (createEntry(artifact, {}, {})(1) as Number).toInt())
  }

  /** invalid nextInt 不消费 token；EOF、非法 token 与 null 输入源使用稳定 Java 命名异常。 */
  @Test
  fun preservesBuiltinScannerFailuresAndInvalidTokenCursor() {
    val result = compile(
      entryClass = "demo.Main",
      entryMethod = "run",
      descriptor = "(I)I",
      "demo/Main.java" to """
        package demo;

        import java.util.Scanner;

        class Main {
          static int run(int mode) {
            if (mode == 5) { Scanner invalid = new Scanner(null); return 0; }
            Scanner scanner = new Scanner(System.in);
            if (mode == 6) { if (scanner.hasNextInt()) return -1; return scanner.next().length(); }
            if (mode == 0 || mode == 3) return scanner.nextInt();
            if (mode == 1 || mode == 2) return scanner.next().length();
            return scanner.nextLine().length();
          }
        }
      """.trimIndent(),
    )

    val artifact = assertNotNull(result.value, result.diagnostics.toString())
    val shared = createEntry(artifact, {}, {}, "oops")
    assertTrue("InputMismatchException" in executionFailure { shared(0) })
    assertEquals(4, (shared(1) as Number).toInt())
    for (mode in 2..4) {
      val message = executionFailure { createEntry(artifact, {}, {})(mode) }
      assertTrue("NoSuchElementException" in message, "mode=$mode, message=$message")
    }
    assertTrue("NullPointerException" in executionFailure { createEntry(artifact, {}, {})(5) })
    assertEquals(2, (createEntry(artifact, {}, {}, "١٢")(6) as Number).toInt())
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

  /** 安装动态程序 ABI 并返回真实 JS 入口；输入是一次执行期间不可追加的预加载文本。 */
  private fun createEntry(
    artifact: JavaScriptProgramArtifact,
    stdout: (String) -> Unit,
    stderr: (String) -> Unit,
    standardInput: String = "",
  ): dynamic {
    assertEquals(1, artifact.modules.size)
    val moduleSource = artifact.modules.single().source
    val executableSource = """
      globalThis["${DynamicProgramHostAbi.WRITE_STANDARD_OUTPUT}"] = stdout;
      globalThis["${DynamicProgramHostAbi.WRITE_STANDARD_ERROR}"] = stderr;
      globalThis["${DynamicProgramHostAbi.READ_STANDARD_INPUT_UTF8_BASE64}"] = readInputBase64;
    """.trimIndent() + "\n" + moduleSource.replace(
      "export function " + artifact.entryExportName,
      "function " + artifact.entryExportName,
    ) + "\nreturn " + artifact.entryExportName + ";"
    val constructor: dynamic = js("Function")
    return constructor("stdout", "stderr", "readInputBase64", executableSource)(
      stdout,
      stderr,
      { encodeUtf8Base64(standardInput) },
    )
  }

  /** Node 测试只模拟 Runner 真正安装的 Base64 host getter，不绕过产物内的 ABI 解码 reader。 */
  private fun encodeUtf8Base64(value: String): String {
    val buffer: dynamic = js("Buffer")
    return buffer.from(value, "utf8").toString("base64") as String
  }

  /** 将生成 JS 的失败稳定转换为文本，避免测试依赖 JS Error 的具体 Kotlin 包装类型。 */
  private fun executionFailure(block: () -> Unit): String = try {
    block()
    "<no failure>"
  } catch (error: Throwable) {
    error.toString()
  }
}
