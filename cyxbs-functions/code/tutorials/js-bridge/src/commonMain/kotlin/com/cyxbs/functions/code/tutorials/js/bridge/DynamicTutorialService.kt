package com.cyxbs.functions.code.tutorials.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import kotlin.coroutines.cancellation.CancellationException

/**
 * 由每门语言的单个 npm 包实现的动态教程服务。
 *
 * 课程正文、初始文件、校验逻辑和进度迁移都由语言教程包提供；端上负责下载、提供通用 Storage
 * 宿主桥和统一 UI，避免教程包依赖 Compose 或具体平台资源。
 *
 * 旧教程包缺少新增方法时失败值为 `NpmJsServiceMethodNotImplementedException`；JSON 协议不匹配、
 * JavaScript 实现执行失败、Runtime 调用失败或显式拒绝请求时，失败值为
 * `NpmJsServiceInvocationException`。bundle 不存在、下载或初始化失败发生在 Service 创建阶段，
 * 由 Loader 直接抛出加载异常。只有 [CancellationException] 会绕过 [NpmJsResult] 继续抛出。
 */
@NpmJsService
interface DynamicTutorialService : NpmJsServiceInstance {

  /** 返回适合侧边栏卡片式课程路径展示的轻量目录。 */
  @Throws(CancellationException::class)
  suspend fun manifest(): NpmJsResult<DynamicTutorialManifest>

  /** 按稳定课程 ID 返回完整课程；目录变化或课程不存在时返回 null。 */
  @Throws(CancellationException::class)
  suspend fun course(courseId: String): NpmJsResult<DynamicTutorialCourse?>

  /** 根据当前工作区与运行结果判断某一步是否完成。 */
  @Throws(CancellationException::class)
  suspend fun evaluate(
    request: DynamicTutorialEvaluationRequest,
  ): NpmJsResult<DynamicTutorialEvaluationResult>

  /**
   * 返回当前教程包保存并迁移后的全部课程进度。
   *
   * 包升级、课程增删或稳定 ID 调整时，应在实现内先把旧存储与源码现场迁移为当前结构；客户端会
   * 把成功返回的快照视为已适配当前包版本，只校验边界，不读取教程包私有的 Storage schema。
   */
  @Throws(CancellationException::class)
  suspend fun savedProgress(): NpmJsResult<List<DynamicTutorialProgress>>

  /** 保存或替换一门课程的最新进度；具体 Settings/File schema 由教程包自行维护。 */
  @Throws(CancellationException::class)
  suspend fun saveProgress(progress: DynamicTutorialProgress): NpmJsResult<Unit>

  /** 清除当前语言教程包保存的全部进度。 */
  @Throws(CancellationException::class)
  suspend fun clearProgress(): NpmJsResult<Unit>

  /** 按稳定课程 ID 清除单门课程进度，其他课程必须保持不变。 */
  @Throws(CancellationException::class)
  suspend fun clearCourseProgress(courseId: String): NpmJsResult<Unit>
}
