package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.storage.NpmPackageArchive

/**
 * 已经完整准备、可供后续解包和 Module 解析的入口。
 *
 * [archives] 按依赖优先顺序排列，最后一个元素为 [entryPackage] 本身。只有整个闭包全部命中缓存或
 * 下载成功时下载器才会返回该对象；失败时调用方不能执行入口。
 */
data class NpmPreparedEntry(
  val releaseTime: String,
  val entryPackage: String,
  val entryModule: String,
  val archives: List<NpmPackageArchive>,
)

/** npm 快照、校验、网络或缓存链路的稳定异常基类。 */
open class NpmException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

/** 后端快照结构、精确版本或依赖关系不满足约束。 */
class NpmSnapshotException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** npm registry 返回的名称、版本或 SRI 与后端快照不一致。 */
class NpmRegistryMismatchException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** HTTP 请求失败或全部后端下载地址均不可用。 */
class NpmDownloadException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** 实际 tarball 内容与后端锁定的 SRI 不一致。 */
class NpmIntegrityException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** npm tarball 缓存读取、校验或原子写入失败。 */
class NpmStorageException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)
