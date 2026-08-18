package com.cyxbs.functions.code.language.internal

import com.cyxbs.functions.code.js.quickjs.QuickJsRuntimeFactory
import com.cyxbs.functions.code.js.runtime.JsRuntime
import com.cyxbs.functions.code.js.runtime.JsRuntimeConfig
import com.cyxbs.functions.code.js.runtime.JsRuntimeOptions
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 Java 受限真并行方案的 Desktop 技术闸门。
 *
 * 该原型不接入公开 Java API：每个任务始终在所属宿主线程创建、进入并关闭独立 QuickJS Runtime，
 * 共享基础字段和 monitor 则放在宿主堆。测试结论用于决定是否值得继续实现 Thread lowering 与 ABI。
 */
class JavaThreadRuntimePrototypeTest {

  /** 分离的宿主读取与写回会产生可复现的丢失更新，证明原型没有伪装成串行交错。 */
  @Test
  fun observesLostUpdatesWithoutMonitor() = withWorkerPool { pool ->
    val iterations = 256
    val sharedCounter = AtomicInteger()
    val readsCompleted = CyclicBarrier(WORKER_COUNT)
    val tasks = List(WORKER_COUNT) {
      pool.submitRuntime(
        bind = { runtime ->
          runtime.bindFunction("__shared_read") {
            val snapshot = sharedCounter.get()
            // 仅用于让原型稳定重现两个线程都读到旧值；生产 ABI 不会在同步回调中等待。
            readsCompleted.await(5, TimeUnit.SECONDS)
            snapshot
          }
          runtime.bindFunction("__shared_write") { arguments ->
            sharedCounter.set((arguments.single() as Number).toInt())
            null
          }
        },
        code = """
          (() => {
            for (let index = 0; index < $iterations; index++) {
              const before = __shared_read();
              __shared_write((before + 1) | 0);
            }
            return 0;
          })()
        """.trimIndent(),
      )
    }

    tasks.awaitAll()
    assertEquals(iterations, sharedCounter.get())
    assertTrue(sharedCounter.get() < iterations * WORKER_COUNT)
  }

  /** 将读改写放入同一宿主 monitor 后，两个 Runtime 的全部更新都必须可见且不丢失。 */
  @Test
  fun preservesUpdatesWithHostMonitor() = withWorkerPool { pool ->
    val iterations = 2_000
    val sharedCounter = AtomicInteger()
    val monitor = Any()
    val tasks = List(WORKER_COUNT) {
      pool.submitRuntime(
        bind = { runtime ->
          runtime.bindFunction("__shared_increment_synchronized") {
            synchronized(monitor) {
              sharedCounter.incrementAndGet()
            }
          }
        },
        code = """
          (() => {
            for (let index = 0; index < $iterations; index++) {
              __shared_increment_synchronized();
            }
            return 0;
          })()
        """.trimIndent(),
      )
    }

    tasks.awaitAll()
    assertEquals(iterations * WORKER_COUNT, sharedCounter.get())
  }

  /**
   * 两个独立 QuickJS CPU 任务应落到不同宿主线程，并在本机多核环境下快于同任务串行执行。
   *
   * 比值写入测试输出供不同平台原型对比；这里只在 Desktop 且可见至少两个处理器时执行加速断言，
   * 避免单核 CI 把“平台没有并行资源”误判为 Runtime 架构失败。
   */
  @Test
  fun executesIndependentCpuWorkInParallel() = withWorkerPool { pool ->
    val workerThreads = ConcurrentHashMap.newKeySet<String>()
    val code = cpuWorkSource()

    // 先完成一次极小任务，排除 native 库加载与首个 Runtime 初始化对串行基线的放大。
    pool.submitRuntime(code = cpuWorkSource(iterations = 1_000)).get(30, TimeUnit.SECONDS)
    val serialResults = mutableListOf<Int>()
    val serialMillis = measureTimeMillis {
      repeat(WORKER_COUNT) {
        serialResults += pool.submitRuntime(code = code).get(30, TimeUnit.SECONDS)
      }
    }

    val parallelMillis = measureTimeMillis {
      val parallelTasks = List(WORKER_COUNT) {
        pool.submitRuntime(
          bind = { runtime ->
            runtime.bindFunction("__record_worker_thread") {
              workerThreads += Thread.currentThread().name
              null
            }
          },
          code = code.replace("const value = 0;", "__record_worker_thread(); const value = 0;"),
        )
      }
      val parallelResults = parallelTasks.awaitAll()
      assertEquals(serialResults, parallelResults)
    }

    println(
      "JAVA_THREAD_PROTOTYPE desktop serial=${serialMillis}ms " +
        "parallel=${parallelMillis}ms processors=${Runtime.getRuntime().availableProcessors()}",
    )
    assertEquals(WORKER_COUNT, workerThreads.size)
    if (Runtime.getRuntime().availableProcessors() >= WORKER_COUNT) {
      assertTrue(
        parallelMillis < serialMillis,
        "Independent QuickJS CPU tasks did not accelerate: serial=${serialMillis}ms, " +
          "parallel=${parallelMillis}ms.",
      )
    }
  }

  /** 在固定大小宿主线程池内执行测试，并保证异常路径释放全部线程。 */
  private inline fun withWorkerPool(block: (ExecutorService) -> Unit) {
    val pool = Executors.newFixedThreadPool(WORKER_COUNT)
    try {
      block(pool)
    } finally {
      pool.shutdownNow()
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
    }
  }

  /**
   * 在线程池任务内创建、使用并关闭唯一 Runtime。
   *
   * 这条线程亲和约束是后续 Thread 实现的硬边界，Runtime 与 JS 对象都不会跨宿主线程传递。
   */
  private fun ExecutorService.submitRuntime(
    bind: (JsRuntime) -> Unit = {},
    code: String,
  ): Future<Int> = submit<Int> {
    runBlocking {
      val runtime = QuickJsRuntimeFactory.create(
        JsRuntimeOptions(
          config = JsRuntimeConfig(
            memoryLimitBytes = 16L * 1024L * 1024L,
            maxStackSizeBytes = 256L * 1024L,
            evaluationTimeoutMillis = 10_000L,
          ),
          allowBytecodeCache = false,
        ),
      )
      try {
        bind(runtime)
        (runtime.evaluateValue(code = code, filename = "java-thread-prototype.js", asModule = false) as Number)
          .toInt()
      } finally {
        runtime.close()
      }
    }
  }

  /** 等待同批线程任务并保持提交顺序，便于对比确定性结果。 */
  private fun List<Future<Int>>.awaitAll(): List<Int> = map { future ->
    future.get(30, TimeUnit.SECONDS)
  }

  /** 生成不依赖宿主睡眠的确定性整数计算，用于验证 QuickJS 本身是否并行占用 CPU。 */
  private fun cpuWorkSource(iterations: Int = CPU_WORK_ITERATIONS): String = """
    (() => {
      const value = 0;
      let checksum = value;
      for (let index = 0; index < $iterations; index++) {
        checksum = (Math.imul(checksum ^ index, 1664525) + 1013904223) | 0;
      }
      return checksum;
    })()
  """.trimIndent()

  private companion object {
    const val CPU_WORK_ITERATIONS = 8_000_000
    const val WORKER_COUNT = 2
  }
}
