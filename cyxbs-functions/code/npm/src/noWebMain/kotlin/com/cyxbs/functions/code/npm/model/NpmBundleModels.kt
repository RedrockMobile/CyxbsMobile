package com.cyxbs.functions.code.npm.model

/**
 * npm Bundle 管理页读取的一次性包池快照。
 *
 * [dependencyOccurrenceCount] 按每个入口的完整图分别累计依赖，同一依赖被多个入口使用时会重复计数；
 * [deduplicatedDependencyCount] 再按 `包名@版本` 跨入口合并，可用于展示共享依赖带来的复用收益。
 * 所有字段来自同一包池锁内读取；后续下载、删除或 GC 不会主动修改已返回对象，需要重新刷新。
 */
data class NpmBundleSnapshot(
  val bundles: List<NpmBundleInfo>,
  val totalSizeBytes: Long,
  val entryCount: Int,
  val dependencyOccurrenceCount: Int = 0,
  val deduplicatedDependencyCount: Int = 0,
)

/**
 * 全局包池中的一个具体 npm 包版本。
 *
 * 依赖和反向依赖均来自入口保存的精确解析图，而不是重新根据 semver 猜测。一个包版本可能同时是
 * 某些入口的根 Bundle、另一些入口的普通依赖，因此 [entryNames] 和关系集合彼此独立。
 */
data class NpmBundleInfo(
  val id: NpmPackageId,
  val source: NpmPackageSource,
  val sizeBytes: Long,
  val downloadedAtEpochMillis: Long?,
  val lastLoadedAtEpochMillis: Long?,
  val entryNames: List<String>,
  val dependencies: List<NpmPackageId>,
  val dependents: List<NpmPackageId>,
  val isAvailable: Boolean,
  val isInUse: Boolean,
) {

  /** 当前包版本是否至少被一个入口作为根 Bundle 使用。 */
  val isEntryBundle: Boolean
    get() = entryNames.isNotEmpty()
}

/**
 * 删除一个 Bundle 后的事务结果。
 *
 * 删除直接或间接使入口图失效时，这些入口会在下次使用时重新解析；[deletedBundles] 还包括本次
 * 可达性 GC 一并回收的孤立依赖。
 */
data class NpmBundleDeleteResult(
  val deletedBundles: List<NpmPackageId>,
  val invalidatedEntryNames: List<String>,
)

/** 全局清空完成后的结果，仅统计实际登记在包池中的 Bundle 与入口。 */
data class NpmBundleClearResult(
  val deletedBundleCount: Int,
  val invalidatedEntryCount: Int,
)

/**
 * 管理操作命中了仍被 JavaScript Runtime 租约保护的 Bundle。
 *
 * 调用方应先关闭相关 Service/Runtime 后重试；管理器不会强制终止正在执行的 JavaScript。
 */
class NpmBundleInUseException(
  message: String,
) : NpmException(message)
