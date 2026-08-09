package com.cyxbs.functions.code.npm.diagnostic

import kotlin.time.Duration

/**
 * NpmJsServiceLoader 单次加载过程中可诊断的阶段。
 *
 * 阶段只覆盖 Service 创建链路，不包含业务方法调用和 [com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance.close]。
 */
enum class NpmJsServiceLoadStage {
  /** 查找并校验 KSP 生成的 Service 代理工厂。 */
  RESOLVE_PROXY_FACTORY,

  /** 获取 npm 入口租约，可能包含 metadata 刷新、依赖解析、下载、SRI 校验与状态落盘。 */
  ACQUIRE_NPM_ENTRY,

  /** 解压已校验归档并构建可供 Module Loader 使用的内存 Module 图。 */
  BUILD_MODULE_GRAPH,

  /** 创建 JavaScript Runtime 并安装当前 npm 依赖图对应的 Module Loader。 */
  CREATE_RUNTIME,

  /** 导入入口 Module、注册 Service 并核对端上与 JavaScript 侧的协议摘要。 */
  INITIALIZE_SERVICE,

  /** 使用生成工厂创建最终返回给业务的 Kotlin Service 代理。 */
  CREATE_SERVICE_PROXY,
}

/**
 * [NpmJsServiceLoadStage] 的单次完成记录。
 *
 * @param stage 加载阶段。
 * @param duration 从进入阶段到正常返回或抛出异常的单调时钟耗时。
 * @param succeeded 阶段是否正常完成；为 false 时，原始异常仍由 load 方法抛给调用方。
 */
data class NpmJsServiceLoadStageTiming(
  val stage: NpmJsServiceLoadStage,
  val duration: Duration,
  val succeeded: Boolean,
)

/**
 * 收集一次 NpmJsServiceLoader.load 调用的阶段耗时。
 *
 * 每次加载应创建独立实例，并在 load 返回或抛出异常后读取 [stageTimings]。本类型不会调用业务回调，
 * 因而不会改变租约、Runtime 和失败清理流程；同一个实例不支持被多个并发加载共享。
 */
class NpmJsServiceLoadMetrics {

  private val mutableStageTimings = mutableListOf<NpmJsServiceLoadStageTiming>()

  /** `准备 npm 入口` 阶段内部的包、registry 源与归档明细。 */
  val packagePoolMetrics: NpmPackagePoolMetrics = NpmPackagePoolMetrics()

  /** 返回当前记录的只读快照，顺序与阶段实际完成顺序一致。 */
  val stageTimings: List<NpmJsServiceLoadStageTiming>
    get() = mutableStageTimings.toList()

  /** 仅由 npm 加载器在阶段退出后追加记录。 */
  internal fun record(
    stage: NpmJsServiceLoadStage,
    duration: Duration,
    succeeded: Boolean,
  ) {
    mutableStageTimings += NpmJsServiceLoadStageTiming(stage, duration, succeeded)
  }
}
