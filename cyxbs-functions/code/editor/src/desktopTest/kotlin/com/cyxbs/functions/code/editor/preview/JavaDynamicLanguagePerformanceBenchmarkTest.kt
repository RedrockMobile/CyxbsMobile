package com.cyxbs.functions.code.editor.preview

import com.cyxbs.components.config.ConfigApplicationInfo
import com.cyxbs.components.init.DesktopProjectEnvironment
import com.cyxbs.functions.code.language.DynamicLanguageManager
import com.cyxbs.functions.code.language.DynamicLanguageMemorySampler
import com.cyxbs.functions.code.language.DynamicLanguagePerformanceScenario
import com.cyxbs.functions.code.language.measurePerformance
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageWorkspace
import com.cyxbs.functions.code.language.js.bridge.DynamicProgramEntry
import com.cyxbs.functions.code.language.js.bridge.DynamicSourceFile
import com.g985892345.provider.api.init.KtProviderInitializer
import com.g985892345.provider.api.init.KtProviderRouter
import com.g985892345.provider.cyxbsmobile.cyxbsfunctions.code.js.quickjs.QuickjsKtProviderInitializer
import com.g985892345.provider.cyxbsmobile.cyxbsfunctions.code.language.jsbridge.JsBridgeKtProviderInitializer
import com.g985892345.provider.testing.withKtProviderTest
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 使用本地 debug npm 图和真实 QuickJS 采集 Desktop Java 性能基线。
 *
 * 默认 desktopTest 不执行昂贵基准；显式设置 CYXBS_JAVA_PERFORMANCE_BENCHMARK=true 后才会读取
 * 根项目 build/npm/debug-source，并把 JSON 报告写入 editor/build/reports/java-performance。
 */
class JavaDynamicLanguagePerformanceBenchmarkTest {

  /** 采集加载、三种编译缓存路径、隔离执行和 JVM heap 检查点。 */
  @Test
  fun capturesDesktopJavaBaseline() {
    if (System.getenv(BENCHMARK_ENABLED_ENV) != "true") return
    val projectDirectory = Path.of(
      checkNotNull(System.getenv(PROJECT_DIRECTORY_ENV)) {
        "$PROJECT_DIRECTORY_ENV must point to the repository root."
      },
    ).toAbsolutePath().normalize()
    DesktopProjectEnvironment.initialize(
      appId = "com.mredrock.cyxbs.performance",
      projectDirectory = projectDirectory.toString(),
    )

    withKtProviderTest(BenchmarkKtProviderInitializer) {
      // 基准测试不依赖应用壳，但 npm debug 本地源需要读取应用构建类型。
      overrideImpl<ConfigApplicationInfo> { BenchmarkConfigApplicationInfo }
      runBlocking {
        val baselineSource = javaSource(changedValue = 3)
        val incrementalSource = javaSource(changedValue = 4)
        val entryPosition = baselineSource.indexOf("public static void main")
        val manager = DynamicLanguageManager()
        val runtime = Runtime.getRuntime()
        val report = manager.measurePerformance(
          scenario = DynamicLanguagePerformanceScenario(
            languageId = "java",
            initialCompilation = compilation(baselineSource, entryPosition),
            incrementalCompilation = compilation(incrementalSource, entryPosition),
            standardInput = "",
            measuredIterations = 7,
            environment = mapOf(
              "platform" to "desktop",
              "osName" to System.getProperty("os.name"),
              "osArch" to System.getProperty("os.arch"),
              "javaVersion" to System.getProperty("java.version"),
            ),
          ),
          memorySampler = DynamicLanguageMemorySampler {
            runtime.totalMemory() - runtime.freeMemory()
          },
        )

        assertEquals(
          listOf(
            "language-load",
            "compile-full",
            "compile-incremental",
            "compile-exact",
            "execute-isolated-runtime",
          ),
          report.measurements.map { it.phase },
        )
        report.measurements.forEach { measurement ->
          assertTrue(measurement.wallMicroseconds.all { it >= 0 })
        }
        val exactCacheModes = report.measurements
          .first { it.phase == "compile-exact" }
          .cacheModes
        assertTrue(exactCacheModes.isEmpty() || exactCacheModes.all { it == "EXACT" })
        val reportFile = projectDirectory.resolve(
          "cyxbs-functions/code/editor/build/reports/java-performance/desktop.json",
        )
        Files.createDirectories(reportFile.parent)
        Files.writeString(reportFile, report.toPrettyJson())
        println("CYXBS_JAVA_PERFORMANCE_REPORT=$reportFile")
        println(report.toPrettyJson())
      }
    }
  }

  /** 创建路径和入口稳定、仅修改一个整数常量的两文件 Java 教学工作区。 */
  private fun compilation(source: String, entryPosition: Int): DynamicCompilationRequest {
    return DynamicCompilationRequest(
      workspace = DynamicLanguageWorkspace(
        files = listOf(
          DynamicSourceFile(path = "src/lesson/Main.java", source = source),
          DynamicSourceFile(
            path = "src/lesson/Score.java",
            source = """
              package lesson;
              final class Score {
                static int sum(int left, int right) {
                  return left + right;
                }
              }
            """.trimIndent(),
          ),
        ),
      ),
      entry = DynamicProgramEntry(
        filePath = "src/lesson/Main.java",
        position = entryPosition,
      ),
    )
  }

  /** 返回包含集合、循环、跨文件调用和标准输出的稳定基准源码。 */
  private fun javaSource(changedValue: Int): String = """
    package lesson;

    import java.util.ArrayList;
    import java.util.List;

    public class Main {
      public static void main() {
        List<Integer> values = new ArrayList<>();
        values.add(1);
        values.add(2);
        values.add($changedValue);
        int total = 0;
        for (int index = 0; index < values.size(); index++) {
          total = Score.sum(total, values.get(index));
        }
        System.out.println(total);
      }
    }
  """.trimIndent()

  private companion object {
    const val BENCHMARK_ENABLED_ENV = "CYXBS_JAVA_PERFORMANCE_BENCHMARK"
    const val PROJECT_DIRECTORY_ENV = "CYXBS_PROJECT_DIR"
  }

  /** 为独立 Desktop 测试提供最小应用配置，确保优先读取项目内 debug npm 包。 */
  private object BenchmarkConfigApplicationInfo : ConfigApplicationInfo {
    override fun isDebug(): Boolean = true
  }

  /** 组合语言宿主代理与 QuickJS，模拟应用启动时的模块初始化器聚合。 */
  private object BenchmarkKtProviderInitializer : KtProviderInitializer() {
    override val router: KtProviderRouter = KtProviderRouter.Empty
    override val otherModuleKtProvider: List<KtProviderInitializer> = listOf(
      JsBridgeKtProviderInitializer,
      QuickjsKtProviderInitializer,
    )
  }
}
