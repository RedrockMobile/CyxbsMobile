package com.cyxbs.functions.code.tutorials

import com.cyxbs.functions.code.tutorials.internal.DynamicTutorialPackageLoader
import com.cyxbs.functions.code.tutorials.internal.NpmDynamicTutorialPackageLoader
import com.cyxbs.functions.code.tutorials.internal.validatedTutorials
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 动态教程发现与加载入口。
 *
 * Catalog 只在当前 Manager 首次读取时下载；每次 [load] 创建独立教程 Runtime。课程进度通过
 * [DynamicTutorialSession] 交给对应 npm 包持久化，使包升级时能够自行迁移稳定 ID 与数据结构。
 */
class DynamicTutorialManager internal constructor(
  private val packageLoader: DynamicTutorialPackageLoader,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {
  /** 使用默认 npm 包池构造业务 Manager。 */
  constructor() : this(NpmDynamicTutorialPackageLoader())

  private val catalogMutex = Mutex()
  private var cachedTutorials: List<DynamicTutorialInfo>? = null

  /** 返回 Catalog 当前登记的语言教程；并发首次调用只读取一次静态 JSON。 */
  suspend fun supportedTutorials(): List<DynamicTutorialInfo> {
    return catalogMutex.withLock {
      cachedTutorials ?: loadCatalog().also { cachedTutorials = it }
    }
  }

  /** 按语言 ID 或别名加载一个独立教程会话。 */
  suspend fun load(languageId: String): DynamicTutorialSession {
    val lookup = languageId.trim().lowercase()
    val tutorial = supportedTutorials().firstOrNull { candidate ->
      candidate.languageId == lookup || lookup in candidate.aliases
    } ?: throw DynamicTutorialNotFoundException(languageId)
    val loaded = packageLoader.loadTutorial(tutorial.npmPackageName)
    return DynamicTutorialSession(
      tutorial = tutorial,
      npmPackageVersion = loaded.npmPackageVersion,
      service = loaded.service,
    )
  }

  /** 宽容解码可追加字段的 Catalog，再严格校验稳定身份。 */
  private suspend fun loadCatalog(): List<DynamicTutorialInfo> {
    val catalog = try {
      json.decodeFromString<DynamicTutorialCatalog>(packageLoader.loadCatalog())
    } catch (exception: SerializationException) {
      throw DynamicTutorialProtocolException(
        "Dynamic tutorial Catalog does not contain a valid supported structure.",
        exception,
      )
    }
    return catalog.validatedTutorials()
  }

}

/** 请求的语言 ID 与别名均不在教程 Catalog 中。 */
class DynamicTutorialNotFoundException(
  languageId: String,
) : IllegalArgumentException("Dynamic tutorial '$languageId' is not supported.")
