package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.internal.DynamicLanguagePackageLoader
import com.cyxbs.functions.code.language.internal.IconCachingDynamicLanguageService
import com.cyxbs.functions.code.language.internal.NpmDynamicLanguagePackageLoader
import com.cyxbs.functions.code.language.internal.validatedLanguages
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.js.runtime.JsRuntimeFactory
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInvocationException
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceProtocolException
import com.cyxbs.functions.code.npm.model.NpmException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 发现并加载动态语言 npm 包的统一入口。
 *
 * ```text
 * DynamicLanguageManager
 *   ├─ supportedLanguages()
 *   │    └─ @cyxbs-mobile/language-catalog@latest
 *   │         └─ 读取 catalog.json → 解析、校验并缓存目录快照
 *   └─ load(languageId)
 *        └─ 查找目录 → @cyxbs-mobile/language-<id>@latest
 *             └─ 独立 Service / Runtime → 调用方 close()
 * ```
 *
 * - Catalog 是不包含 Kotlin/JS Runtime 的静态 npm 包，读取目录不会创建 JavaScript Runtime；
 * - Manager 只缓存体积很小的 Catalog 快照，不共享正在执行的语言 Runtime；
 * - 每次 [load] 都返回独立 [DynamicLanguageSession]，调用方必须在编辑会话结束时关闭它；
 * - npm tgz、依赖图与字节码仍由底层全局包池复用，因此独立 Runtime 不会导致重复下载。
 *
 * Catalog 和语言包都使用 npm `latest`：每个包池实例首次使用对应入口时，会在 Runtime 创建前
 * 完成远端更新检查；网络失败且存在完整旧图时沿用底层 AUTO 策略回退，不进行定时刷新。
 */
class DynamicLanguageManager internal constructor(
  private val packageLoader: DynamicLanguagePackageLoader,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val iconCache: DynamicLanguageIconCache = DynamicLanguageIconCache.inMemory(),
  private val unsupportedCapabilityStatistics: DynamicLanguageUnsupportedCapabilityStatistics =
    DynamicLanguageUnsupportedCapabilityStatistics.inMemory(),
  private val runtimeFactoryProvider: () -> JsRuntimeFactory? = { JsRuntimeFactory.implOrNull() },
) {

  /** 使用默认 npm 包池和 JavaScript Runtime 创建业务 Manager。 */
  constructor() : this(
    packageLoader = NpmDynamicLanguagePackageLoader(),
    iconCache = DynamicLanguageIconCache.Default,
    unsupportedCapabilityStatistics = DynamicLanguageUnsupportedCapabilityStatistics.Default,
  )

  private val catalogMutex = Mutex()
  private var cachedLanguages: List<DynamicLanguageInfo>? = null

  /**
   * 返回 Catalog 中当前支持的语言。
   *
   * 首次调用会下载并解析固定 Catalog npm 包中的 JSON，后续调用复用当前 Manager 内存快照。读取
   * 都经过同一把锁，确保不同平台的并发调用只会创建一份可见的快照。
   *
   * @throws DynamicLanguageProtocolException Catalog 协议、语言身份或 npm 坐标不合法。
   * @throws NpmException Catalog npm 包解析、下载、校验、存储或资源读取失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    DynamicLanguageProtocolException::class,
    NpmException::class,
    CancellationException::class,
  )
  suspend fun supportedLanguages(): List<DynamicLanguageInfo> {
    return catalogMutex.withLock {
      cachedLanguages ?: loadCatalog().also { cachedLanguages = it }
    }
  }

  /**
   * 返回当前 Catalog 对应的持久化语言图标。
   *
   * 本方法只读取本地图标缓存，不加载语言 npm 包、不创建 JavaScript Runtime，也不检查远端版本。
   * 因此项目文件列表可以先显示上次成功加载的图标；后续 [load] 会用实际 npm 版本校验并更新。
   * 缓存损坏或普通文件系统错误按空结果处理，协程取消仍会传播。
   *
   * @return 以当前 Catalog 的完整语言定义为键的图标映射。
   */
  @Throws(
    DynamicLanguageProtocolException::class,
    NpmException::class,
    CancellationException::class,
  )
  suspend fun cachedIcons(): Map<DynamicLanguageInfo, DynamicLanguageIcon> {
    return iconCache.restore(supportedLanguages())
  }

  /**
   * 返回本机按语言、npm 版本和稳定诊断 code 聚合的不支持能力统计。
   *
   * @param languageId 可选语言 ID 或别名；为空时返回全部语言。
   */
  suspend fun unsupportedCapabilityStatistics(
    languageId: String? = null,
  ): List<DynamicLanguageUnsupportedCapabilityStatistic> {
    val canonicalLanguageId = languageId?.let { resolveLanguage(it).languageId }
    return unsupportedCapabilityStatistics.snapshot(canonicalLanguageId)
  }

  /** 清空全部语言或指定语言的不支持能力统计，不影响 npm 包、图标或编译缓存。 */
  suspend fun clearUnsupportedCapabilityStatistics(languageId: String? = null) {
    val canonicalLanguageId = languageId?.let { resolveLanguage(it).languageId }
    unsupportedCapabilityStatistics.clear(canonicalLanguageId)
  }

  /**
   * 按稳定语言 ID 或别名加载一个独立的动态语言会话。
   *
   * @param languageId Catalog 中的语言 ID 或别名，匹配时忽略首尾空白和大小写。
   * 返回的 Service 会透明代理 [DynamicLanguageService.fileIcon]：业务首次读取图标时才比较本地
   * 缓存与包池最终选择的 npm 根包版本，版本一致时不进入 JavaScript，首次读取或版本变化时
   * 获取并持久化图标。仅加载语言包不会主动读取或保存图标。
   *
   * @return 由独立分析 Runtime 支撑、可按次创建用户程序 Runtime 且需要由调用方关闭的会话。
   * @throws DynamicLanguageNotFoundException Catalog 中不存在该语言。
   * @throws DynamicLanguageProtocolException Catalog 数据不合法。
   * @throws NpmJsServiceProtocolException Service 代理、入口或方法清单协议不合法。
   * @throws NpmJsServiceInvocationException JavaScript Runtime 创建或 npm 入口初始化失败。
   * @throws NpmException npm 包解析、下载、校验或存储失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    DynamicLanguageNotFoundException::class,
    DynamicLanguageProtocolException::class,
    NpmJsServiceProtocolException::class,
    NpmJsServiceInvocationException::class,
    NpmException::class,
    CancellationException::class,
  )
  suspend fun load(languageId: String): DynamicLanguageSession {
    val language = resolveLanguage(languageId)

    val loaded = packageLoader.loadLanguage(language.npmPackageName)
    return DynamicLanguageSession(
      delegate = IconCachingDynamicLanguageService(
        delegate = loaded.service,
        language = language,
        npmPackageVersion = loaded.npmPackageVersion,
        iconCache = iconCache,
      ),
      runtimeFactoryProvider = runtimeFactoryProvider,
      onCompilationDiagnostics = { diagnostics ->
        unsupportedCapabilityStatistics.record(
          language = language,
          npmPackageVersion = loaded.npmPackageVersion,
          diagnostics = diagnostics,
        )
      },
    )
  }

  /** 把语言 ID 或别名解析为 Catalog 中的规范语言定义。 */
  private suspend fun resolveLanguage(languageId: String): DynamicLanguageInfo {
    val lookupKey = languageId.trim().lowercase()
    return supportedLanguages().firstOrNull { candidate ->
      candidate.languageId == lookupKey || lookupKey in candidate.aliases
    } ?: throw DynamicLanguageNotFoundException(languageId)
  }

  /** 加载、宽容反序列化并校验静态 Catalog JSON；未知新增字段由 [json] 忽略。 */
  private suspend fun loadCatalog(): List<DynamicLanguageInfo> {
    val catalogJson = packageLoader.loadCatalog()
    val catalog = try {
      json.decodeFromString<DynamicLanguageCatalog>(catalogJson)
    } catch (exception: SerializationException) {
      throw DynamicLanguageProtocolException(
        message = "Dynamic language Catalog does not contain a valid supported structure.",
        cause = exception,
      )
    }
    return catalog.validatedLanguages()
  }
}

/** 请求的语言 ID 和别名均不在当前 Catalog 中。 */
class DynamicLanguageNotFoundException(
  languageId: String,
) : IllegalArgumentException("Dynamic language '$languageId' is not supported.")
