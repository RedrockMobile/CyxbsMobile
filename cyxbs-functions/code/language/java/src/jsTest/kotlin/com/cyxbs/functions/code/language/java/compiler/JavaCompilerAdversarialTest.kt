package com.cyxbs.functions.code.language.java.compiler

import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFile
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceFileId
import com.cyxbs.functions.code.language.java.compiler.source.JavaSourceWorkspace
import kotlin.test.Test
import kotlin.test.assertTrue

/** 验证畸形用户源码不会越过 Java 完整编译流水线的结构化失败边界。 */
class JavaCompilerAdversarialTest {

  /**
   * 固定的 256 个多点扰动样本应完整经过 CST、AST、语义、lowering 与后端入口。
   *
   * 样本不要求合法，但编译器必须返回程序或结构化诊断，不能抛出内部异常、返回空结果，或产生
   * 缺少入口 Module 的半成品。
   */
  @Test
  fun survivesDeterministicMalformedCompilerCorpus() {
    val base = """
      package fuzz;

      public class Main<T extends Number> {
        public static void main() {
          int[] values = new int[] { 1, 2, 3 };
          for (int value : values) {
            System.out.println(value);
          }
        }
      }
    """.trimIndent()

    deterministicMalformedJavaSources(base, count = 256).forEachIndexed { sample, source ->
      val result = JavaToJavaScriptCompiler.compile(
        JavaCompilerRequest(
          workspace = JavaSourceWorkspace(
            listOf(JavaSourceFile(JavaSourceFileId(0), "fuzz/Main.java", source)),
          ),
          entryPoint = JavaCompilerEntryPoint("fuzz.Main", "main", "()V"),
        ),
      )

      assertTrue(
        result.value != null || result.diagnostics.isNotEmpty(),
        "Malformed compiler sample $sample returned neither program nor diagnostic.",
      )
      assertTrue(
        result.diagnostics.all { diagnostic -> diagnostic.code.isNotBlank() },
        "Malformed compiler sample $sample returned a diagnostic without a stable code.",
      )
      result.value?.let { artifact ->
        assertTrue(
          artifact.modules.any { module -> module.moduleName == artifact.entryModuleName },
          "Malformed compiler sample $sample returned an incomplete Module graph.",
        )
      }
    }
  }
}
