package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.internal.DynamicLanguagePackageLoader
import com.cyxbs.functions.code.language.internal.NpmDynamicLanguagePackageLoader
import com.cyxbs.functions.code.language.internal.validatedLanguages
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageService
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
 * - 每次 [load] 都返回独立 [DynamicLanguageService]，调用方必须在编辑会话结束时关闭它；
 * - npm tgz、依赖图与字节码仍由底层全局包池复用，因此独立 Runtime 不会导致重复下载。
 *
 * Catalog 和语言包都使用 npm `latest`：每个包池实例首次使用对应入口时，会在 Runtime 创建前
 * 完成远端更新检查；网络失败且存在完整旧图时沿用底层 AUTO 策略回退，不进行定时刷新。
 */
class DynamicLanguageManager internal constructor(
  private val packageLoader: DynamicLanguagePackageLoader,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {

  /** 使用默认 npm 包池和 JavaScript Runtime 创建业务 Manager。 */
  constructor() : this(NpmDynamicLanguagePackageLoader())

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
   * 按稳定语言 ID 或别名加载一个独立的动态语言会话。
   *
   * @param languageId Catalog 中的语言 ID 或别名，匹配时忽略首尾空白和大小写。
   * @return 由独立 Runtime 支撑、需要由调用方关闭的语言 Service。
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
  suspend fun load(languageId: String): DynamicLanguageService {
    val lookupKey = languageId.trim().lowercase()
    val language = supportedLanguages().firstOrNull { candidate ->
      candidate.languageId == lookupKey || lookupKey in candidate.aliases
    } ?: throw DynamicLanguageNotFoundException(languageId)

    return packageLoader.loadLanguage(language.npmPackageName)
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
