package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import com.cyxbs.functions.code.npm.js.bridge.npmJsCatching
import com.cyxbs.functions.code.tutorials.internal.validated
import com.cyxbs.functions.code.tutorials.internal.validatedAgainst
import com.cyxbs.functions.code.tutorials.internal.validatedFor
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 一次独立的语言教程会话。
 *
 * 调用方离开教程编辑页时必须调用 [close]；同一 npm tgz 仍由全局包池复用，不会重复下载。
 */
class DynamicTutorialSession internal constructor(
  val tutorial: DynamicTutorialInfo,
  val npmPackageVersion: String,
  private val service: DynamicTutorialService,
) : DynamicTutorialService {
  private val serviceMutex = Mutex()
  private var cachedManifest: DynamicTutorialManifest? = null
  private val cachedCourses = mutableMapOf<String, DynamicTutorialCourse>()
  private var isClosed = false

  /** 首次读取并验证侧边栏课程路径目录，后续调用复用不可变缓存。 */
  override suspend fun manifest(): NpmJsResult<DynamicTutorialManifest> = npmJsCatching {
    serviceMutex.withLock {
      ensureOpen()
      cachedManifest ?: service.manifest().getOrThrow().validatedFor(tutorial)
        .also { cachedManifest = it }
    }
  }

  /**
   * 加载并验证一门课程的完整课时和步骤。
   *
   * Manifest 未声明的 ID 直接返回 `null`；已声明课程若缺少正文或返回了不同摘要，则视为 npm 包协议损坏。
   */
  override suspend fun course(courseId: String): NpmJsResult<DynamicTutorialCourse?> = npmJsCatching {
    val expectedSummary = manifest().getOrThrow().courses
      .firstOrNull { it.courseId == courseId } ?: return@npmJsCatching null
    serviceMutex.withLock {
      ensureOpen()
      cachedCourses[courseId] ?: run {
        val loaded = service.course(courseId).getOrThrow() ?: throw DynamicTutorialProtocolException(
          "Tutorial package does not contain declared course '$courseId'.",
        )
        loaded.validatedAgainst(expectedSummary).also { cachedCourses[courseId] = it }
      }
    }
  }

  /** 串行调用教程 Runtime，并限制动态反馈大小。 */
  override suspend fun evaluate(
    request: DynamicTutorialEvaluationRequest,
  ): NpmJsResult<DynamicTutorialEvaluationResult> = npmJsCatching {
    serviceMutex.withLock {
      ensureOpen()
      service.evaluate(request).getOrThrow().validated()
    }
  }

  /**
   * 每次从 npm 包读取最新进度，使包升级后的迁移结果立即对客户端生效。
   *
   * `savedProgress()` 成功即表示当前包已完成自己的 schema 和源码迁移，因此会话会把返回快照标记为
   * 当前实际加载版本；若旧源码不能复用，npm 实现必须在返回前清空或替换对应 workspace。
   */
  override suspend fun savedProgress(): NpmJsResult<List<DynamicTutorialProgress>> = npmJsCatching {
    serviceMutex.withLock {
      ensureOpen()
      service.savedProgress().getOrThrow()
        .validatedFor(tutorial)
        .map { progress -> progress.copy(npmPackageVersion = npmPackageVersion) }
    }
  }

  /** 校验动态数据边界后，把进度持久化完全委托给当前教程 npm 包。 */
  override suspend fun saveProgress(
    progress: DynamicTutorialProgress,
  ): NpmJsResult<Unit> = npmJsCatching {
    serviceMutex.withLock {
      ensureOpen()
      service.saveProgress(progress.validatedFor(tutorial)).getOrThrow()
    }
  }

  /** 清除当前教程包的全部进度，不再访问客户端私有文件。 */
  override suspend fun clearProgress(): NpmJsResult<Unit> = npmJsCatching {
    serviceMutex.withLock {
      ensureOpen()
      service.clearProgress().getOrThrow()
    }
  }

  /** 清除一门课程的包内进度；课程 ID 的存在性由可动态更新的 npm 包决定。 */
  override suspend fun clearCourseProgress(courseId: String): NpmJsResult<Unit> = npmJsCatching {
    serviceMutex.withLock {
      ensureOpen()
      require(courseId.isNotBlank() && courseId == courseId.trim() && courseId.length <= 256) {
        "Tutorial course ID is invalid."
      }
      service.clearCourseProgress(courseId).getOrThrow()
    }
  }

  /** 幂等释放教程 JavaScript Runtime 与 npm 入口租约；释放后不再允许读取缓存。 */
  override suspend fun close(): NpmJsResult<Unit> = npmJsCatching {
    serviceMutex.withLock {
      if (isClosed) return@withLock
      isClosed = true
      cachedManifest = null
      cachedCourses.clear()
      service.close().getOrThrow()
    }
  }

  /** 防止调用方在 Runtime 释放后误用已缓存的协议对象。 */
  private fun ensureOpen() {
    check(!isClosed) { "Dynamic tutorial session is already closed." }
  }
}
