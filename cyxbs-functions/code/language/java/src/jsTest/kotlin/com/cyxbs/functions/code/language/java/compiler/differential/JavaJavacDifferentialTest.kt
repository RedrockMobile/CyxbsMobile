package com.cyxbs.functions.code.language.java.compiler.differential

import com.cyxbs.functions.code.language.java.compiler.JavaCompilerEntryPoint
import com.cyxbs.functions.code.language.java.compiler.JavaCompilerRequest
import com.cyxbs.functions.code.language.java.compiler.JavaScriptProgramArtifact
import com.cyxbs.functions.code.language.java.compiler.JavaToJavaScriptCompiler
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramHostAbi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 使用同一批源码对比 javac/java 与轻量 Java→JS 编译运行结果。
 *
 * javac 基准由 Gradle 在测试编译前生成；本测试不依赖宿主文件系统，因此 npm/Node 测试环境只消费
 * 已生成的不可变 Kotlin 数据。新增语料时无需在此处添加测试方法。
 */
class JavaJavacDifferentialTest {

  /** 比较编译结论、稳定诊断类别、stdout/stderr 与未捕获异常类型。 */
  @Test
  fun matchesJavacAndJavaReferenceCorpus() {
    assertTrue(
      generatedJavacDifferentialFixtures.size >= MINIMUM_REFERENCE_CASES,
      "The differential corpus must not silently shrink.",
    )
    assertEquals(
      generatedJavacDifferentialFixtures.size,
      generatedJavacDifferentialFixtures.map(JavacDifferentialFixture::id).distinct().size,
      "Differential case ids must remain unique.",
    )
    val categoryCounts = generatedJavacDifferentialFixtures.groupingBy { fixture ->
      fixture.category
    }.eachCount()
    assertEquals(
      REQUIRED_REFERENCE_CATEGORIES,
      categoryCounts.keys,
      "Every fixture must belong to one known coverage category.",
    )
    categoryCounts.forEach { (category, count) ->
      assertTrue(
        count >= MINIMUM_CASES_PER_CATEGORY,
        "Coverage category '$category' must contain at least $MINIMUM_CASES_PER_CATEGORY cases, actual: $count.",
      )
    }
    val failures = mutableListOf<String>()
    generatedJavacDifferentialFixtures.forEach { fixture ->
      try {
        val result = JavaToJavaScriptCompiler.compile(
          JavaCompilerRequest(
            workspace = JavaSourceWorkspace(
              fixture.sources.mapIndexed { index, source ->
                JavaSourceFile(
                  id = JavaSourceFileId(index),
                  path = source.first,
                  source = source.second,
                )
              },
            ),
            entryPoint = JavaCompilerEntryPoint(
              qualifiedClassName = fixture.entryClass,
              methodName = fixture.entryMethod,
              descriptor = fixture.descriptor,
            ),
          ),
        )
        if (!fixture.javacCompiled) {
          assertNull(result.value, "Case '${fixture.id}' should fail compilation.")
          assertEquals(
            expected = fixture.expectedDiagnosticCategories,
            actual = result.diagnostics.mapNotNull { diagnostic ->
              diagnosticCategory(diagnostic.code)
            }.toSet(),
            message = "Case '${fixture.id}' produced different diagnostic categories: ${result.diagnostics}",
          )
          return@forEach
        }

        val artifact = assertNotNull(
          result.value,
          "Case '${fixture.id}' should compile: ${result.diagnostics}",
        )
        val execution = execute(artifact, fixture.standardInput)
        assertEquals(
          fixture.expectedStandardOutput,
          execution.standardOutput,
          "Case '${fixture.id}' stdout differs from java.",
        )
        assertEquals(
          fixture.expectedStandardError,
          execution.standardError,
          "Case '${fixture.id}' stderr differs from java.",
        )
        if (fixture.expectedThrowableSimpleName == null) {
          assertNull(execution.failure, "Case '${fixture.id}' unexpectedly failed.")
        } else {
          val failure =
            assertNotNull(execution.failure, "Case '${fixture.id}' should fail at runtime.")
          assertTrue(
            failure.contains(fixture.expectedThrowableSimpleName),
            "Case '${fixture.id}' expected ${fixture.expectedThrowableSimpleName}, actual: $failure",
          )
        }
      } catch (failure: Throwable) {
        failures += "${fixture.id}: ${failure.message ?: failure}"
      }
    }
    assertTrue(
      failures.isEmpty(),
      "Differential corpus mismatches:\n" + failures.joinToString(separator = "\n"),
    )
  }

  /**
   * 在 Node Function 中安装与端上 Runner 一致的文本 ABI 并执行无参入口。
   *
   * 每个 case 使用新的 Function 闭包，避免 Java runtime 的静态字段、集合或 Scanner cursor 跨 case
   * 泄漏。异常只归一为文本中的 Java 简单类名，不比较 JS 引擎私有 stack 格式。
   */
  private fun execute(
    artifact: JavaScriptProgramArtifact,
    standardInput: String,
  ): DifferentialExecution {
    assertEquals(1, artifact.modules.size)
    val standardOutput = StringBuilder()
    val standardError = StringBuilder()
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
    val entry: dynamic = constructor(
      "stdout",
      "stderr",
      "readInputBase64",
      executableSource,
    )(
      { text: String -> standardOutput.append(text) },
      { text: String -> standardError.append(text) },
      { encodeUtf8Base64(standardInput) },
    )
    val failure = try {
      entry()
      null
    } catch (error: Throwable) {
      error.toString()
    }
    return DifferentialExecution(
      standardOutput = standardOutput.toString(),
      standardError = standardError.toString(),
      failure = failure,
    )
  }

  /** Node 侧仅提供 Runner 的 Base64 getter，Java 产物仍负责严格 UTF-8 解码。 */
  private fun encodeUtf8Base64(value: String): String {
    val buffer: dynamic = js("Buffer")
    return buffer.from(value, "utf8").toString("base64") as String
  }

  /** 将两套编译器的细粒度诊断压缩到稳定、可对比的语义类别。 */
  private fun diagnosticCategory(code: String): String? = when (code) {
    "java.semantic.undefined_name",
    "java.semantic.unknown_type",
      -> "UNRESOLVED_SYMBOL"

    "java.semantic.type_mismatch",
    "java.semantic.no_applicable_overload",
    "java.semantic.invalid_return_type",
      -> "TYPE_MISMATCH"

    "java.semantic.ambiguous_overload" -> "AMBIGUOUS_CALL"
    "java.semantic.final_assignment" -> "FINAL_ASSIGNMENT"
    "java.semantic.missing_return" -> "MISSING_RETURN"
    else -> null
  }

  private companion object {
    const val MINIMUM_REFERENCE_CASES = 200
    const val MINIMUM_CASES_PER_CATEGORY = 10

    val REQUIRED_REFERENCE_CATEGORIES = setOf(
      "control-flow",
      "numeric",
      "array",
      "text-and-wrapper",
      "collection",
      "generic-and-overload",
      "object-model",
      "exception",
      "functional-and-enum",
      "io-and-multi-file",
      "compiler-diagnostic",
    )
  }
}

/** 一项由 javac/java 生成的不可变差分基准。 */
internal data class JavacDifferentialFixture(
  val id: String,
  val category: String,
  val entryClass: String,
  val entryMethod: String,
  val descriptor: String,
  val standardInput: String,
  val sources: List<Pair<String, String>>,
  val javacCompiled: Boolean,
  val expectedStandardOutput: String,
  val expectedStandardError: String,
  val expectedThrowableSimpleName: String?,
  val expectedDiagnosticCategories: Set<String>,
)

/** 轻量执行结果；返回值不是教学 main 的可观察行为，因此不纳入差分。 */
private data class DifferentialExecution(
  val standardOutput: String,
  val standardError: String,
  val failure: String?,
)
