package com.cyxbs.functions.code.tutorials

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
  override suspend fun manifest(): DynamicTutorialManifest {
    return serviceMutex.withLock {
      ensureOpen()
      cachedManifest ?: service.manifest().validatedFor(tutorial).also { cachedManifest = it }
    }
  }

  /**
   * 加载并验证一门课程的完整课时和步骤。
   *
   * Manifest 未声明的 ID 直接返回 `null`；已声明课程若缺少正文或返回了不同摘要，则视为 npm 包协议损坏。
   */
  override suspend fun course(courseId: String): DynamicTutorialCourse? {
    val expectedSummary = manifest().courses.firstOrNull { it.courseId == courseId } ?: return null
    return serviceMutex.withLock {
      ensureOpen()
      cachedCourses[courseId] ?: run {
        val loaded = service.course(courseId) ?: throw DynamicTutorialProtocolException(
          "Tutorial package does not contain declared course '$courseId'.",
        )
        loaded.validatedAgainst(expectedSummary).also { cachedCourses[courseId] = it }
      }
    }
  }

  /** 串行调用教程 Runtime，并限制动态反馈大小。 */
  override suspend fun evaluate(
    request: DynamicTutorialEvaluationRequest,
  ): DynamicTutorialEvaluationResult {
    return serviceMutex.withLock {
      ensureOpen()
      service.evaluate(request).validated()
    }
  }

  /** 幂等释放教程 JavaScript Runtime 与 npm 入口租约；释放后不再允许读取缓存。 */
  override suspend fun close() {
    serviceMutex.withLock {
      if (isClosed) return
      isClosed = true
      cachedManifest = null
      cachedCourses.clear()
      service.close()
    }
  }

  /** 防止调用方在 Runtime 释放后误用已缓存的协议对象。 */
  private fun ensureOpen() {
    check(!isClosed) { "Dynamic tutorial session is already closed." }
  }
}
