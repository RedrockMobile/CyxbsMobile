package com.cyxbs.functions.code.tutorials.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInvocationException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 由每门语言的单个 npm 包实现的动态教程服务。
 *
 * 课程正文、初始文件和校验逻辑都由语言教程包提供；端上只负责下载、进度持久化和统一 UI，避免
 * 教程包依赖 Compose 或具体平台资源。
 */
@NpmJsService
interface DynamicTutorialService : NpmJsServiceInstance {

  /** 返回适合侧边栏卡片式课程路径展示的轻量目录。 */
  @Throws(
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
  )
  suspend fun manifest(): DynamicTutorialManifest

  /** 按稳定课程 ID 返回完整课程；目录变化或课程不存在时返回 null。 */
  @Throws(
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
  )
  suspend fun course(courseId: String): DynamicTutorialCourse?

  /** 根据当前工作区与运行结果判断某一步是否完成。 */
  @Throws(
    NpmJsServiceInvocationException::class,
    SerializationException::class,
    CancellationException::class,
  )
  suspend fun evaluate(
    request: DynamicTutorialEvaluationRequest,
  ): DynamicTutorialEvaluationResult
}
