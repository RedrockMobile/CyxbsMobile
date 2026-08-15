package com.cyxbs.functions.code.language

import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random

/**
 * 跨编辑器会话持久保存动态语言文件图标的缓存。
 *
 * 缓存保存可序列化的 [DynamicLanguageIcon] 协议模型，而不是 Compose `ImageVector`，因此 Android、
 * iOS 与 Desktop 可以共享同一套存储逻辑，UI 仍可按当前主题转换矢量图。缓存条目同时记录稳定
 * 语言 ID、npm 包名和实际解析版本：
 *
 * ```text
 * 项目文件列表打开
 *   └─ restore(languages) ──命中──> 立即显示上次图标，不创建 JS Runtime
 *
 * 业务调用代理 Service.fileIcon()
 *   ├─ npm 包名和版本均相同 ──────> 复用缓存，不调用底层 JavaScript
 *   └─ 首次加载或版本变化 ────────> 调用底层 fileIcon() 并原子覆盖本地缓存
 * ```
 *
 * 缓存损坏或普通文件系统错误只会退化为未命中，不会阻止语言 Service 使用；协程取消仍会正常
 * 传播。默认实例使用与 npm 包池同属 App 缓存区域的独立目录，系统清理缓存后会重新获取图标。
 */
class DynamicLanguageIconCache internal constructor(
  private val store: DynamicLanguageIconCacheStore,
) {
  private val mutex = Mutex()
  private var loaded = false
  private var recordsByLanguageId = emptyMap<String, DynamicLanguageIconCacheRecord>()

  /**
   * 恢复当前 Catalog 仍认可的本地图标。
   *
   * npm 包名已经变化的旧条目不会返回，防止语言 ID 复用后展示另一实现的图标。这里不要求知道
   * 远端最新版本，因此项目文件列表可以在语言包尚未加载时立即使用上次成功保存的图标。
   * 返回值以当前 Catalog 的完整 [DynamicLanguageInfo] 为键，调用方无需再通过语言 ID 回查扩展名。
   */
  suspend fun restore(
    languages: List<DynamicLanguageInfo>,
  ): Map<DynamicLanguageInfo, DynamicLanguageIcon> = mutex.withLock {
    ensureLoaded()
    buildMap {
      languages.forEach { language ->
        val record = recordsByLanguageId[language.languageId]
          ?.takeIf { it.npmPackageName == language.npmPackageName }
          ?: return@forEach
        put(language, record.icon)
      }
    }
  }

  /** 返回一种语言与当前 Catalog 坐标匹配的完整缓存记录，仅供加载流程比较实际 npm 版本。 */
  internal suspend fun find(
    language: DynamicLanguageInfo,
  ): DynamicLanguageIconCacheRecord? = mutex.withLock {
    ensureLoaded()
    recordsByLanguageId[language.languageId]
      ?.takeIf { it.npmPackageName == language.npmPackageName }
  }

  /**
   * 更新一种语言的图标和实际 npm 版本。
   *
   * 内存结果会立即可见；磁盘写入失败时仍保留本次进程内缓存，并在下次启动退化为旧缓存或未命中。
   */
  internal suspend fun update(
    language: DynamicLanguageInfo,
    npmPackageVersion: String,
    icon: DynamicLanguageIcon,
  ) = mutex.withLock {
    ensureLoaded()
    val next = recordsByLanguageId + (
      language.languageId to DynamicLanguageIconCacheRecord(
        languageId = language.languageId,
        npmPackageName = language.npmPackageName,
        npmPackageVersion = npmPackageVersion,
        icon = icon,
      )
    )
    recordsByLanguageId = next
    persistBestEffort(next.values.toList())
  }

  /** 首次访问时读取一次磁盘；缓存文件异常时按空缓存继续。调用方已持有 [mutex]。 */
  private suspend fun ensureLoaded() {
    if (loaded) return
    recordsByLanguageId = try {
      store.read().entries.associateBy(DynamicLanguageIconCacheRecord::languageId)
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Throwable) {
      emptyMap()
    }
    loaded = true
  }

  /** 持久化失败不能影响语言功能；只有协程取消需要继续向上传播。 */
  private suspend fun persistBestEffort(records: List<DynamicLanguageIconCacheRecord>) {
    try {
      store.write(DynamicLanguageIconCacheState(entries = records))
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Throwable) {
      // 图标只是派生缓存，写入失败时保留进程内结果并允许语言 Service 正常工作。
    }
  }

  companion object {
    /** App 级默认持久缓存，多个编辑器页面共享内存快照和同一份本地文件。 */
    val Default: DynamicLanguageIconCache by lazy {
      DynamicLanguageIconCache(OkioDynamicLanguageIconCacheStore())
    }

    /** 测试或隔离会话使用的纯内存缓存，不访问默认持久目录。 */
    internal fun inMemory(): DynamicLanguageIconCache {
      return DynamicLanguageIconCache(InMemoryDynamicLanguageIconCacheStore())
    }
  }
}

/** 一条持久化图标记录；npm 版本用于语言加载完成后的精确失效判断。 */
@Serializable
internal data class DynamicLanguageIconCacheRecord(
  val languageId: String,
  val npmPackageName: String,
  val npmPackageVersion: String,
  val icon: DynamicLanguageIcon,
)

/** 图标缓存文件的完整快照；schema 不兼容时直接视为空缓存。 */
@Serializable
internal data class DynamicLanguageIconCacheState(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val entries: List<DynamicLanguageIconCacheRecord> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1
  }
}

/** 持久化边界，测试可以注入纯内存实现。 */
internal interface DynamicLanguageIconCacheStore {
  suspend fun read(): DynamicLanguageIconCacheState
  suspend fun write(state: DynamicLanguageIconCacheState)
}

/** 不访问文件系统的缓存存储，供 Manager 单元测试与隔离实例使用。 */
internal class InMemoryDynamicLanguageIconCacheStore(
  initialState: DynamicLanguageIconCacheState = DynamicLanguageIconCacheState(),
) : DynamicLanguageIconCacheStore {
  var state = initialState
    private set

  override suspend fun read(): DynamicLanguageIconCacheState = state

  override suspend fun write(state: DynamicLanguageIconCacheState) {
    this.state = state
  }
}

/** 使用 Okio JSON 文件原子保存跨平台图标缓存。 */
internal class OkioDynamicLanguageIconCacheStore(
  rootDirectory: Path = DEFAULT_DYNAMIC_LANGUAGE_ICON_CACHE_DIRECTORY,
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
  },
) : DynamicLanguageIconCacheStore {
  private val statePath = rootDirectory / "icon-cache.json"

  override suspend fun read(): DynamicLanguageIconCacheState = withContext(ioDispatcher) {
    if (!fileSystem.exists(statePath)) return@withContext DynamicLanguageIconCacheState()
    val state = try {
      json.decodeFromString<DynamicLanguageIconCacheState>(fileSystem.read(statePath) { readUtf8() })
    } catch (_: SerializationException) {
      return@withContext DynamicLanguageIconCacheState()
    }
    if (state.schemaVersion == DynamicLanguageIconCacheState.CURRENT_SCHEMA_VERSION) {
      state
    } else {
      DynamicLanguageIconCacheState()
    }
  }

  override suspend fun write(state: DynamicLanguageIconCacheState) = withContext(ioDispatcher) {
    val parent = requireNotNull(statePath.parent)
    fileSystem.createDirectories(parent)
    val temporary = parent / ".${statePath.name}.${Random.nextLong().toString(16)}.tmp"
    try {
      fileSystem.write(temporary, mustCreate = true) {
        writeUtf8(json.encodeToString(state))
      }
      fileSystem.atomicMove(temporary, statePath)
    } finally {
      fileSystem.delete(temporary, mustExist = false)
    }
  }
}

/** 默认缓存目录与 npm 包池同属系统缓存区域，但使用独立版本目录。 */
private val DEFAULT_DYNAMIC_LANGUAGE_ICON_CACHE_DIRECTORY =
  FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cyxbs-code" / "language-icons" / "v1"
