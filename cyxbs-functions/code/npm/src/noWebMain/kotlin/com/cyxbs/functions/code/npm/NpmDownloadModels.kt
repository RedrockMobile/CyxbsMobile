package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.storage.NpmPackageArchive

/**
 * 已经完整准备、可供后续解包和 Module 解析的入口。
 *
 * [archives] 与 [resolvedPackages] 按依赖优先顺序排列，最后一个元素为 [entryPackage]。只有整个
 * 闭包全部解析、校验并准备成功时包池才会返回该对象；失败时调用方不能执行入口。
 */
data class NpmPreparedEntry(
  val resolvedAtEpochMillis: Long,
  val entryPackage: NpmPackageId,
  val entryModule: String?,
  val archives: List<NpmPackageArchive>,
  /**
   * 每个包已经锁定的直接依赖。
   *
   * Module Loader 只按该精确关系解析依赖，不在执行阶段重新选择版本。
   */
  val resolvedPackages: List<NpmResolvedPackage>,
)

/** npm 包在全局池中的稳定身份；同名不同版本是两个独立节点。 */
data class NpmPackageId(
  val name: String,
  val version: String,
)

/**
 * 已锁定包节点及其直接依赖边。
 *
 * [dependencies] 的 key 是源码 import 使用的包名，value 是最终选中的具体包版本。Module Loader
 * 不再现场执行 semver 选择，只按该映射做确定性解析。
 */
data class NpmResolvedPackage(
  val id: NpmPackageId,
  val integrity: String,
  val dependencies: Map<String, NpmPackageId>,
)

/**
 * 一个可复用 npm 入口的请求。
 *
 * [version] 明确区分固定版本与最新版本，避免调用方依赖字符串约定推断更新策略。
 */
data class NpmEntryRequest(
  val packageName: String,
  val version: NpmEntryVersion = NpmEntryVersion.Latest,
  val entryModule: String? = null,
  /** 入口在本地状态中的稳定名称；允许同一 npm 包以不同配置注册为多个入口。 */
  val entryName: String = packageName,
)

/**
 * npm 入口根包的版本策略。
 *
 * [Exact] 一旦成功解析便固定复用，不在后续进程启动时请求 registry；[Latest] 在每个
 * [NpmPackagePool] 实例首次使用该入口时刷新一次，实例生命周期内不重复检查。
 */
sealed interface NpmEntryVersion {

  /** 每次创建新的包池实例后，在该入口首次运行前检查 registry 的 latest。 */
  data object Latest : NpmEntryVersion

  /** 固定使用一个完整且精确的 npm semver，不接受 caret、tilde 或其他版本范围。 */
  data class Exact(val value: String) : NpmEntryVersion
}

/** npm 解析、校验、网络或缓存链路的稳定异常基类。 */
open class NpmException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

/** npm 版本范围、依赖关系、SRI 或入口请求不满足客户端约束。 */
class NpmResolutionException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** npm registry 返回的包身份、版本目录或不可变元数据内部不一致。 */
class NpmRegistryMismatchException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** npm metadata 或 tarball HTTP 请求失败。 */
class NpmDownloadException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** 实际 tarball 内容与 registry 元数据声明的 SRI 不一致。 */
class NpmIntegrityException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** npm tarball 缓存读取、校验或原子写入失败。 */
class NpmStorageException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)

/** npm tgz 格式、package.json 或 Module 导出结构无法安全解析。 */
class NpmModuleResolutionException(
  message: String,
  cause: Throwable? = null,
) : NpmException(message, cause)
