package com.cyxbs.functions.code.language.java.compiler.semantic.impl

import com.cyxbs.functions.code.language.java.compiler.diagnostic.JavaCompilerPhaseResult
import com.cyxbs.functions.code.language.java.compiler.frontend.JavaLezerAstFrontend
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaConstantValue
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticModel
import com.cyxbs.functions.code.language.java.compiler.semantic.JavaSemanticType
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 阶段 0 三遍语义分析、词法作用域、重载与 literal 的边界测试。 */
class JavaSemanticAnalyzerImplTest {
  /** sum 覆盖参数、局部变量、classic for、比较、复合赋值、递增和返回。 */
  @Test
  fun analyzesSum() {
    val result = analyze(
      "Main.java" to
        "class Main { static int sum(int limit) { int result = 0; " +
          "for (int i = 0; i < limit; i++) { result += i; } return result; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val model = assertNotNull(result.value)
    assertTrue(model.declarations.isNotEmpty())
    assertTrue(model.expressionTypes.values.none { it == JavaSemanticType.Error })
  }

  /** 内层 block 不得遮蔽仍处于作用域内的参数或局部变量。 */
  @Test
  fun rejectsOverlappingLocalShadowing() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value(int item) { { int item = 2; } return item; } }",
    )

    assertDiagnostic(result, "java.semantic.duplicate_local_declaration")
  }

  /** 前一个 block 生命周期结束后可以复用同名局部变量。 */
  @Test
  fun allowsReuseAfterScopeEnds() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value() { { int item = 1; } " +
          "{ int item = 2; return item; } } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
  }

  /** block 和 classic for 的局部变量在离开作用域后不可见。 */
  @Test
  fun rejectsOutOfScopeNames() {
    val block = analyze(
      "Main.java" to
        "class Main { static int value() { { int item = 1; } return item; } }",
    )
    val loop = analyze(
      "Main.java" to
        "class Main { static int value() { for (int i = 0; i < 1; i++) { } return i; } }",
    )

    assertDiagnostic(block, "java.semantic.undefined_name")
    assertDiagnostic(loop, "java.semantic.undefined_name")
  }

  /** 未声明名称不得被猜测成其他 package 的同名类型或局部变量。 */
  @Test
  fun rejectsUndefinedName() {
    val result = analyze(
      "Main.java" to "class Main { static int value() { return missing; } }",
    )

    assertDiagnostic(result, "java.semantic.undefined_name")
  }

  /** 返回值必须与当前方法返回类型兼容。 */
  @Test
  fun rejectsReturnMismatch() {
    val result = analyze(
      "Main.java" to "class Main { static boolean value() { return 1; } }",
    )

    assertDiagnostic(result, "java.semantic.return_type_mismatch")
  }

  /** 非 void 方法不能沿任意可达路径正常结束并返回 JavaScript undefined。 */
  @Test
  fun rejectsMissingReturnPath() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value(boolean enabled) { if (enabled) return 1; } }",
    )

    assertDiagnostic(result, "java.semantic.missing_return")
  }

  /** 未初始化局部变量即使在 JavaScript 中会得到 undefined，也必须在语义阶段拒绝。 */
  @Test
  fun rejectsReadBeforeDefiniteAssignment() {
    val directRead = analyze(
      "Main.java" to "class Main { static int value() { int item; return item; } }",
    )
    val oneBranch = analyze(
      "Main.java" to
        "class Main { static int value(boolean enabled) { int item; " +
          "if (enabled) item = 1; return item; } }",
    )

    assertDiagnostic(directRead, "java.semantic.variable_not_initialized")
    assertDiagnostic(oneBranch, "java.semantic.variable_not_initialized")
  }

  /** 两个可正常结束的 if 分支都赋值后，局部变量才是 definitely assigned。 */
  @Test
  fun acceptsAssignmentOnEveryBranch() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value(boolean enabled) { int item; " +
          "if (enabled) item = 1; else item = 2; return item; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
  }

  /** 简单赋值会把此前未初始化的局部变量标记为 definitely assigned。 */
  @Test
  fun acceptsAssignmentBeforeRead() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value() { int item; item = 1; return item; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
  }

  /** 不能正常结束的分支不参与交集，另一分支赋值后即可安全读取。 */
  @Test
  fun acceptsAssignmentOnOnlyCompletingBranch() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value(boolean enabled) { int item; " +
          "if (enabled) return 1; else item = 2; return item; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
  }

  /** 已初始化 final 局部变量和 final 参数都不能再次赋值或执行自增。 */
  @Test
  fun rejectsFinalReassignment() {
    val local = analyze(
      "Main.java" to
        "class Main { static int value() { final int item = 1; item = 2; return item; } }",
    )
    val parameter = analyze(
      "Main.java" to
        "class Main { static int value(final int item) { item++; return item; } }",
    )

    assertDiagnostic(local, "java.semantic.final_assignment")
    assertDiagnostic(parameter, "java.semantic.final_assignment")
  }

  /** 空白 final 需要完整的 definitely-unassigned 分支分析，阶段 0 先明确拒绝。 */
  @Test
  fun rejectsBlankFinalLocal() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value() { final int item; item = 1; return item; } }",
    )

    assertDiagnostic(result, "java.semantic.blank_final_unsupported")
  }

  /** overload 按 arity 与参数类型选择唯一精确候选，并保存 callable binding。 */
  @Test
  fun selectsExactOverload() {
    val result = analyze(
      "Main.java" to
        "class Main { static int pick(int value) { return 1; } " +
          "static int pick(String value) { return 2; } " +
          "static int run() { return pick(7); } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val selected = result.value!!.selectedCallables.values.single()
    assertEquals(listOf(JavaSemanticType.Primitive(
      com.cyxbs.functions.code.language.java.compiler.ast.JavaAstPrimitiveType.INT,
    )), selected.parameterTypes)
    assertEquals("(I)I", selected.erasedDescriptor)
  }

  /** null 同时兼容多个引用重载时必须报告歧义而不是任选一个。 */
  @Test
  fun rejectsAmbiguousNullOverload() {
    val result = analyze(
      "Main.java" to
        "class Box { } class Main { " +
          "static int pick(Box value) { return 1; } " +
          "static int pick(String value) { return 2; } " +
          "static int run() { return pick(null); } }",
    )

    assertDiagnostic(result, "java.semantic.ambiguous_overload")
  }

  /** 第一遍签名收集使调用可以位于被调用方法之前。 */
  @Test
  fun resolvesForwardCall() {
    val result = analyze(
      "Main.java" to
        "class Main { static int run() { return twice(3); } " +
          "static int twice(int value) { return value + value; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    assertEquals(1, result.value!!.selectedCallables.size)
  }

  /** TypeName receiver 可以跨文件调用同 package 中后声明的 static 方法。 */
  @Test
  fun resolvesCrossFileStaticCall() {
    val result = analyze(
      "demo/Main.java" to
        "package demo; class Main { static int run() { return Helper.twice(3); } }",
      "demo/Helper.java" to
        "package demo; class Helper { static int twice(int value) { return value + value; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    assertEquals(1, result.value!!.selectedCallables.size)
  }

  /** 跨 package 调用必须同时满足 public 顶层类型和 public 方法可见性。 */
  @Test
  fun enforcesCrossPackageAccess() {
    val hiddenType = analyze(
      "app/Main.java" to
        "package app; import library.Helper; class Main { static int run() { return Helper.value(); } }",
      "library/Helper.java" to
        "package library; class Helper { public static int value() { return 1; } }",
    )
    val hiddenMethod = analyze(
      "app/Main.java" to
        "package app; import library.Helper; class Main { static int run() { return Helper.value(); } }",
      "library/Helper.java" to
        "package library; public class Helper { static int value() { return 1; } }",
    )
    val visible = analyze(
      "app/Main.java" to
        "package app; import library.Helper; class Main { static int run() { return Helper.value(); } }",
      "library/Helper.java" to
        "package library; public class Helper { public static int value() { return 1; } }",
    )

    assertDiagnostic(hiddenType, "java.semantic.inaccessible_type")
    assertDiagnostic(hiddenMethod, "java.semantic.inaccessible_method")
    assertTrue(visible.isSuccess, visible.diagnostics.toString())
  }

  /** 显式 import 即使没有被引用，也必须验证类型存在性和跨 package 可见性。 */
  @Test
  fun validatesUnusedExplicitImports() {
    val hidden = analyze(
      "app/Main.java" to
        "package app; import library.Hidden; class Main { static int run() { return 0; } }",
      "library/Hidden.java" to
        "package library; class Hidden { }",
    )
    val missing = analyze(
      "app/Main.java" to
        "package app; import library.Missing; class Main { static int run() { return 0; } }",
    )

    assertDiagnostic(hidden, "java.semantic.inaccessible_type")
    assertDiagnostic(missing, "java.semantic.unknown_import")
  }

  /** 顶层 class 的非法 modifier 应在严格 Java 方言前端直接拒绝。 */
  @Test
  fun rejectsInvalidTopLevelModifiers() {
    listOf("private", "protected", "static").forEach { modifier ->
      val result = JavaLezerAstFrontend.parse(
        JavaSourceWorkspace(
          listOf(
            JavaSourceFile(
              JavaSourceFileId(0),
              "Main.java",
              "$modifier class Main { static int value() { return 1; } }",
            ),
          ),
        ),
      )
      assertFalse(result.isSuccess)
      assertTrue(result.diagnostics.any { it.code == "java.frontend.unsupported" })
    }
  }

  /** public 顶层类型文件名不一致时必须在生成 JS 前失败。 */
  @Test
  fun rejectsPublicTypeFileNameMismatch() {
    val result = analyze(
      "Wrong.java" to "public class Main { public static int value() { return 1; } }",
    )

    assertDiagnostic(result, "java.semantic.public_type_file_mismatch")
  }

  /** 不同 package 的 simple name 不会因全局后缀匹配而串线。 */
  @Test
  fun doesNotLeakTypesAcrossPackages() {
    val result = analyze(
      "a/Main.java" to
        "package a; class Main { static int run() { return Helper.value(); } }",
      "b/Helper.java" to
        "package b; class Helper { static int value() { return 1; } }",
    )

    assertDiagnostic(result, "java.semantic.unknown_type")
  }

  /** boolean 与 int 的算术操作必须给出稳定 operand 诊断。 */
  @Test
  fun rejectsInvalidBinaryOperands() {
    val result = analyze(
      "Main.java" to "class Main { static int run() { return true + 1; } }",
    )

    assertDiagnostic(result, "java.semantic.invalid_binary_operands")
  }

  /** String 常量表保存解码后的换行、Unicode 与八进制转义。 */
  @Test
  fun decodesStringLiteral() {
    val result = analyze(
      "Main.java" to
        """class Main { static String text() { return "line\n\u0041\101"; } }""",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val constant = result.value!!.constants.values
      .filterIsInstance<JavaConstantValue.StringValue>()
      .single()
    assertEquals("line\nAA", constant.value)
  }

  /** 常用二、八、十六进制 int 会无损进入常量表。 */
  @Test
  fun parsesIntegerFormats() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value() { int a = 0b1010; " +
          "int b = 077; int c = 0x20; return a + b + c; } }",
    )

    assertTrue(result.isSuccess, result.diagnostics.toString())
    val values = result.value!!.constants.values
      .filterIsInstance<JavaConstantValue.IntValue>()
      .map { it.value }
    assertTrue(values.containsAll(listOf(10, 63, 32)))
  }

  /** 超出阶段 0 int 范围的 token 不得静默截断。 */
  @Test
  fun rejectsOutOfRangeInteger() {
    val result = analyze(
      "Main.java" to "class Main { static int value() { return 2147483648; } }",
    )

    assertDiagnostic(result, "java.semantic.integer_out_of_range")
  }

  /** 相同参数 descriptor 的方法不是合法 overload。 */
  @Test
  fun rejectsDuplicateMethodDescriptor() {
    val result = analyze(
      "Main.java" to
        "class Main { static int value(int item) { return item; } " +
          "static boolean value(int item) { return true; } }",
    )

    assertDiagnostic(result, "java.semantic.duplicate_method_descriptor")
  }

  /** 解析多文件源码并确认前端成功，避免语义测试误吞 CST 失败。 */
  private fun analyze(
    vararg sources: Pair<String, String>,
  ): JavaCompilerPhaseResult<JavaSemanticModel> {
    val workspace = JavaSourceWorkspace(
      sources.mapIndexed { index, (path, source) ->
        JavaSourceFile(JavaSourceFileId(index), path, source)
      },
    )
    val frontend = JavaLezerAstFrontend.parse(workspace)
    assertTrue(frontend.isSuccess, frontend.diagnostics.toString())
    return JavaSemanticAnalyzerImpl.analyze(assertNotNull(frontend.value))
  }

  /** 断言失败结果包含指定稳定诊断 code。 */
  private fun assertDiagnostic(
    result: JavaCompilerPhaseResult<JavaSemanticModel>,
    code: String,
  ) {
    assertFalse(result.isSuccess, "Expected failure but got a semantic model.")
    assertTrue(
      result.diagnostics.any { it.code == code },
      "Expected " + code + ", actual diagnostics: " + result.diagnostics,
    )
  }
}
