package com.cyxbs.functions.code.npm.diagnostic

import com.cyxbs.functions.code.npm.model.NpmPackageId
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Duration

/** 单个 registry metadata 请求的完成状态。 */
enum class NpmRegistrySourceOutcome {
  /** 响应已完成全部元数据校验；[NpmRegistrySourceTiming.selected] 表示是否被本次竞速采用。 */
  SUCCEEDED,

  /** HTTP、JSON、包身份、SRI 或 tarball URL 校验失败。 */
  FAILED,

  /** 其他 registry 已经获胜，因此取消当前请求。 */
  CANCELLED,
}

/**
 * 单个包访问一个 registry 源的耗时。
 *
 * @param packageName 请求的 npm 包名。
 * @param registryBaseUrl registry 根地址，不包含包名路径。
 * @param duration 从发起 HTTP 到响应完成校验、失败或取消的耗时。
 * @param outcome 请求结果。
 * @param selected 成功响应是否被竞速选中；单源请求成功时固定为 true。
 */
data class NpmRegistrySourceTiming(
  val packageName: String,
  val registryBaseUrl: String,
  val duration: Duration,
  val outcome: NpmRegistrySourceOutcome,
  val selected: Boolean,
)

/** 单个包完成多 registry 竞速和元数据校验的总耗时。 */
data class NpmPackageMetadataTiming(
  val packageName: String,
  val duration: Duration,
  val succeeded: Boolean,
)

/** 精确版本归档在包池中的处理结果。 */
enum class NpmPackageArchiveOutcome {
  /** 已存在通过 SRI 标识的本地归档，无需下载。 */
  CACHE_HIT,

  /** 本次已下载、校验 SRI 并写入包池。 */
  DOWNLOADED,

  /** 归档查找、下载、校验或写入失败。 */
  FAILED,

  /** 调用协程取消了归档处理。 */
  CANCELLED,
}

/** 单个精确版本归档的缓存检查或下载耗时。 */
data class NpmPackageArchiveTiming(
  val packageId: NpmPackageId,
  val duration: Duration,
  val outcome: NpmPackageArchiveOutcome,
)

/**
 * 收集一次 npm 入口准备过程中的包级耗时。
 *
 * 记录顺序与解析实际完成顺序一致。单次入口中的不同依赖允许并发写入，读取属性会返回加锁后的稳定
 * 快照；该对象仍只用于一次入口准备，不应由多个独立加载共享。不传入时包池不会创建这些明细记录，
 * 也不会输出日志或写入持久化状态。
 */
class NpmPackagePoolMetrics {
  private val lock = SynchronizedObject()
  private val mutableMetadataTimings = mutableListOf<NpmPackageMetadataTiming>()
  private val mutableRegistrySourceTimings = mutableListOf<NpmRegistrySourceTiming>()
  private val mutableArchiveTimings = mutableListOf<NpmPackageArchiveTiming>()

  /** 每个包完成 metadata 解析的总耗时快照。 */
  val metadataTimings: List<NpmPackageMetadataTiming>
    get() = synchronized(lock) { mutableMetadataTimings.toList() }

  /** 每个包访问各 registry 源的耗时快照。 */
  val registrySourceTimings: List<NpmRegistrySourceTiming>
    get() = synchronized(lock) { mutableRegistrySourceTimings.toList() }

  /** 每个精确版本归档的缓存检查或下载耗时快照。 */
  val archiveTimings: List<NpmPackageArchiveTiming>
    get() = synchronized(lock) { mutableArchiveTimings.toList() }

  /** 由 registry 客户端在单个源结束后记录。 */
  internal fun recordRegistrySource(timing: NpmRegistrySourceTiming) {
    synchronized(lock) {
      mutableRegistrySourceTimings += timing
    }
  }

  /** 由 registry 客户端在单个包的多源竞速结束后记录。 */
  internal fun recordMetadata(timing: NpmPackageMetadataTiming) {
    synchronized(lock) {
      mutableMetadataTimings += timing
    }
  }

  /** 由包池在精确归档处理结束后记录。 */
  internal fun recordArchive(timing: NpmPackageArchiveTiming) {
    synchronized(lock) {
      mutableArchiveTimings += timing
    }
  }
}
