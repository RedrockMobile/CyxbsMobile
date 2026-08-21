package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialCourse
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationRequest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialEvaluationResult
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialManifest
import com.cyxbs.functions.code.tutorials.js.bridge.DynamicTutorialService

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
  /** 读取侧边栏课程路径目录。 */
  override suspend fun manifest(): DynamicTutorialManifest = service.manifest()

  /** 加载一门课程的完整课时和步骤。 */
  override suspend fun course(courseId: String): DynamicTutorialCourse? = service.course(courseId)

  /** 委托教程包判断当前步骤是否完成。 */
  override suspend fun evaluate(
    request: DynamicTutorialEvaluationRequest,
  ): DynamicTutorialEvaluationResult = service.evaluate(request)

  /** 释放教程 JavaScript Runtime 与 npm 入口租约。 */
  override suspend fun close() = service.close()
}
