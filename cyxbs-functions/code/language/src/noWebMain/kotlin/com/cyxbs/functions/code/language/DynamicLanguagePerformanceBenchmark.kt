package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationCacheMode
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationDiagnosticSeverity
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationMetrics
import com.cyxbs.functions.code.language.js.bridge.DynamicCompilationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.time.TimeSource

/**
 * 一套跨 Android、iOS 和 Desktop 共用的动态语言性能场景。
 *
 * [initialCompilation] 与 [incrementalCompilation] 应保持文件路径和入口不变，只修改少量源码，
 * 这样第二次编译才能测量增量 CST 路径；[measuredIterations] 同时控制 EXACT 与独立 Runtime
 * 执行的重复次数，避免单次调度抖动被误认为稳定结果。
 */
data class DynamicLanguagePerformanceScenario(
  val languageId: String,
  val initialCompilation: DynamicCompilationRequest,
  val incrementalCompilation: DynamicCompilationRequest,
  val standardInput: String = "",
  val measuredIterations: Int = 5,
  val environment: Map<String, String> = emptyMap(),
) {
  init {
    require(measuredIterations > 0) { "measuredIterations must be greater than 0." }
  }
}

/**
 * 平台内存采样边界。
 *
 * 返回当前进程或目标子系统的已用字节数；平台无法稳定获得时返回 null。采样发生在各阶段之间，
 * 不参与阶段计时，因此 Android 可接 Runtime/PSS、Desktop 可接 JVM heap，iOS 也可只依赖
 * Instruments 而不提供实现。
 */
fun interface DynamicLanguageMemorySampler {

  /** 返回当前已用字节数；无可靠数据时返回 null。 */
  fun usedBytes(): Long?
}

/** 一类性能阶段的墙钟和语言包内部编译指标。 */
@Serializable
data class DynamicLanguagePerformanceMeasurement(
  val phase: String,
  val wallMicroseconds: List<Long>,
  val medianWallMicroseconds: Long,
  val p95WallMicroseconds: Long,
  val serviceMicroseconds: List<Long> = emptyList(),
  val cacheModes: List<String> = emptyList(),
)

/** 本次基准运行观测到的内存范围。 */
@Serializable
data class DynamicLanguageMemorySummary(
  val initialUsedBytes: Long,
  val peakUsedBytes: Long,
  val finalUsedBytes: Long,
  /** 峰值相对基准开始时增长的字节数；GC 造成下降时最小为 0。 */
  val peakGrowthBytes: Long = (peakUsedBytes - initialUsedBytes).coerceAtLeast(0),
)

/** 可跨平台持久化和比较的动态语言性能报告。 */
@Serializable
data class DynamicLanguagePerformanceReport(
  val languageId: String,
  val measuredIterations: Int,
  val environment: Map<String, String>,
  val measurements: List<DynamicLanguagePerformanceMeasurement>,
  val memory: DynamicLanguageMemorySummary?,
) {

  /** 输出稳定缩进 JSON，供 CI artifact、Profiler 记录和人工基线共同使用。 */
  fun toPrettyJson(): String = PERFORMANCE_JSON.encodeToString(this)
}

/**
 * 使用真实语言 npm 会话和独立用户 Runtime 执行统一性能基准。
 *
 * 该函数不设置硬阈值，避免开发机负载导致脆弱测试；调用方应持久化 [DynamicLanguagePerformanceReport]
 * 并在同平台、同构建类型下比较趋势。编译或运行失败会立即抛出，不能把失败耗时写成有效基线。
 */
suspend fun DynamicLanguageManager.measurePerformance(
  scenario: DynamicLanguagePerformanceScenario,
  memorySampler: DynamicLanguageMemorySampler? = null,
): DynamicLanguagePerformanceReport {
  val memorySamples = mutableListOf<Long>()
  fun sampleMemory() {
    memorySampler?.usedBytes()?.let(memorySamples::add)
  }

  sampleMemory()
  val (session, loadWall) = measureWall { load(scenario.languageId) }
  sampleMemory()
  lateinit var measurements: List<DynamicLanguagePerformanceMeasurement>
  try {
    val (fullCompilation, fullWall) = measureWall {
      session.compile(scenario.initialCompilation)
    }
    fullCompilation.requireSuccessful("FULL compilation")
    sampleMemory()

    val (incrementalCompilation, incrementalWall) = measureWall {
      session.compile(scenario.incrementalCompilation)
    }
    incrementalCompilation.requireSuccessful("INCREMENTAL compilation")
    sampleMemory()

    val exactWalls = mutableListOf<Long>()
    val exactMetrics = mutableListOf<DynamicCompilationMetrics>()
    repeat(scenario.measuredIterations) {
      val (compilation, wall) = measureWall {
        session.compile(scenario.incrementalCompilation)
      }
      compilation.requireSuccessful("EXACT compilation")
      exactWalls += wall
      compilation.metrics?.let(exactMetrics::add)
      sampleMemory()
    }

    val executionWalls = mutableListOf<Long>()
    val executionMetrics = mutableListOf<DynamicCompilationMetrics>()
    repeat(scenario.measuredIterations) {
      val (execution, wall) = measureWall {
        session.run(
          DynamicProgramRunRequest(
            compilation = scenario.incrementalCompilation,
            standardInput = scenario.standardInput,
          ),
        )
      }
      check(execution.executed) {
        "Dynamic language benchmark execution did not run: ${execution.diagnostics}"
      }
      executionWalls += wall
      execution.compilationMetrics?.let(executionMetrics::add)
      sampleMemory()
    }

    measurements = listOf(
      measurement("language-load", listOf(loadWall)),
      measurement(
        phase = "compile-full",
        walls = listOf(fullWall),
        metrics = listOfNotNull(fullCompilation.metrics),
      ),
      measurement(
        phase = "compile-incremental",
        walls = listOf(incrementalWall),
        metrics = listOfNotNull(incrementalCompilation.metrics),
      ),
      measurement("compile-exact", exactWalls, exactMetrics),
      measurement("execute-isolated-runtime", executionWalls, executionMetrics),
    )
  } finally {
    session.close()
  }
  // finalUsedBytes 明确表示 npm 会话与隔离 Runtime 全部释放后的观测值。
  sampleMemory()
  return DynamicLanguagePerformanceReport(
    languageId = scenario.languageId,
    measuredIterations = scenario.measuredIterations,
    environment = scenario.environment.entries
      .sortedBy { it.key }
      .associate { it.toPair() },
    measurements = measurements,
    memory = memorySamples.takeIf(List<Long>::isNotEmpty)?.let { samples ->
      DynamicLanguageMemorySummary(
        initialUsedBytes = samples.first(),
        peakUsedBytes = samples.max(),
        finalUsedBytes = samples.last(),
      )
    },
  )
}

/** 把一次阶段执行转换为微秒墙钟，并保留原返回值。 */
private suspend inline fun <T> measureWall(crossinline block: suspend () -> T): Pair<T, Long> {
  val mark = TimeSource.Monotonic.markNow()
  val value = block()
  return value to mark.elapsedNow().inWholeMicroseconds.coerceAtLeast(0)
}

/** 拒绝把编译失败、空产物或 ERROR 诊断记录成性能样本。 */
private fun com.cyxbs.functions.code.language.js.bridge.DynamicCompilationResult.requireSuccessful(
  phase: String,
) {
  check(program != null && diagnostics.none { it.severity == DynamicCompilationDiagnosticSeverity.ERROR }) {
    "$phase failed: $diagnostics"
  }
}

/** 汇总一组墙钟样本及语言包内部服务耗时。 */
private fun measurement(
  phase: String,
  walls: List<Long>,
  metrics: List<DynamicCompilationMetrics> = emptyList(),
): DynamicLanguagePerformanceMeasurement {
  check(walls.isNotEmpty()) { "Performance phase '$phase' contains no samples." }
  val sorted = walls.sorted()
  val median = if (sorted.size % 2 == 0) {
    (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
  } else {
    sorted[sorted.size / 2]
  }
  val p95Index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)
  return DynamicLanguagePerformanceMeasurement(
    phase = phase,
    wallMicroseconds = walls,
    medianWallMicroseconds = median,
    p95WallMicroseconds = sorted[p95Index],
    serviceMicroseconds = metrics.map(DynamicCompilationMetrics::totalMicroseconds),
    cacheModes = metrics.map(DynamicCompilationMetrics::cacheMode)
      .map(DynamicCompilationCacheMode::name),
  )
}

private val PERFORMANCE_JSON = Json {
  prettyPrint = true
  encodeDefaults = true
}
