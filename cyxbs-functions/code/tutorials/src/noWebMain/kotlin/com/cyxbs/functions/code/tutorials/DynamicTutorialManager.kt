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
 * Catalog 只在当前 Manager 首次读取时下载；每次 [load] 创建独立教程 Runtime。课程进度由上层按
 * 稳定 course/lesson/step ID 持久化，不写回动态 npm 包。
 */
class DynamicTutorialManager internal constructor(
  private val packageLoader: DynamicTutorialPackageLoader,
  private val json: Json = Json { ignoreUnknownKeys = true },
  private val progressStore: DynamicTutorialProgressStore = OkioDynamicTutorialProgressStore(),
) {
  /** 使用默认 npm 包池构造业务 Manager。 */
  constructor() : this(NpmDynamicTutorialPackageLoader())

  private val catalogMutex = Mutex()
  private var cachedTutorials: List<DynamicTutorialInfo>? = null
  private val progressMutex = Mutex()
  private var cachedProgressState: DynamicTutorialProgressState? = null

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

  /** 返回指定语言已经保存的课程进度；损坏或已超出当前存储边界的记录会被忽略。 */
  suspend fun savedProgress(languageId: String): List<DynamicTutorialProgress> {
    val normalizedLanguageId = languageId.trim().lowercase()
    return progressMutex.withLock {
      progressState().entries.filter { it.languageId == normalizedLanguageId }
    }
  }

  /**
   * 原子更新一门课程的学习进度和小型代码现场。
   *
   * 调用方必须使用当前 Catalog 的语言与 npm 包身份；每门课程只保存一条最新记录，防止连续编辑
   * 产生无上限历史快照。
   */
  suspend fun saveProgress(progress: DynamicTutorialProgress) {
    validateProgress(progress)
    val tutorial = supportedTutorials().firstOrNull { it.languageId == progress.languageId }
      ?: throw DynamicTutorialNotFoundException(progress.languageId)
    require(tutorial.npmPackageName == progress.npmPackageName) {
      "Tutorial progress package '${progress.npmPackageName}' does not match Catalog package " +
        "'${tutorial.npmPackageName}'."
    }
    progressMutex.withLock {
      val oldState = progressState()
      val replacementKey = progress.languageId to progress.courseId
      val entries = oldState.entries
        .filterNot { (it.languageId to it.courseId) == replacementKey }
        .plus(progress)
        .takeLast(MAX_PROGRESS_ENTRIES)
      val newState = oldState.copy(entries = entries)
      progressStore.write(newState)
      cachedProgressState = newState
    }
  }

  /** 清除指定语言的全部教程进度，供设置页或课程重置入口调用。 */
  suspend fun clearProgress(languageId: String) {
    val normalizedLanguageId = languageId.trim().lowercase()
    progressMutex.withLock {
      val oldState = progressState()
      val newState = oldState.copy(
        entries = oldState.entries.filterNot { it.languageId == normalizedLanguageId },
      )
      progressStore.write(newState)
      cachedProgressState = newState
    }
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

  /** 首次读取时清理越界记录，避免损坏 JSON 将异常数据带入编辑器。 */
  private suspend fun progressState(): DynamicTutorialProgressState {
    cachedProgressState?.let { return it }
    val storedState = progressStore.read()
    return storedState.copy(
      entries = storedState.entries
        .filter(::isValidProgress)
        .takeLast(MAX_PROGRESS_ENTRIES),
    ).also { cachedProgressState = it }
  }

  /** 对业务写入采用失败关闭，避免超大源码快照占满缓存目录。 */
  private fun validateProgress(progress: DynamicTutorialProgress) {
    require(isValidProgress(progress)) { "Dynamic tutorial progress exceeds its storage boundary." }
  }

  /** 保持校验无副作用，使读取损坏记录时可以安全丢弃单条数据。 */
  private fun isValidProgress(progress: DynamicTutorialProgress): Boolean {
    val identities = listOf(
      progress.languageId,
      progress.npmPackageName,
      progress.npmPackageVersion,
      progress.courseId,
      progress.lessonId,
      progress.stepId,
    )
    return identities.all { it.isNotBlank() && it.length <= MAX_IDENTITY_LENGTH } &&
      progress.languageId == progress.languageId.trim().lowercase() &&
      progress.completedSteps.size <= MAX_COMPLETED_STEPS &&
      progress.completedSteps.distinct().size == progress.completedSteps.size &&
      progress.completedSteps.all { completed ->
        completed.lessonId.isNotBlank() && completed.lessonId.length <= MAX_IDENTITY_LENGTH &&
          completed.stepId.isNotBlank() && completed.stepId.length <= MAX_IDENTITY_LENGTH
      } &&
      progress.workspace.size <= MAX_WORKSPACE_FILES &&
      progress.workspace.map { it.path }.distinct().size == progress.workspace.size &&
      progress.workspace.all { file ->
        file.path.isNotBlank() && file.path.length <= MAX_FILE_PATH_LENGTH
      } &&
      progress.workspace.sumOf { it.source.length.toLong() } <= MAX_WORKSPACE_SOURCE_CHARACTERS
  }

  private companion object {
    const val MAX_PROGRESS_ENTRIES = 128
    const val MAX_COMPLETED_STEPS = 2_048
    const val MAX_WORKSPACE_FILES = 128
    const val MAX_IDENTITY_LENGTH = 256
    const val MAX_FILE_PATH_LENGTH = 1_024
    const val MAX_WORKSPACE_SOURCE_CHARACTERS = 2L * 1024L * 1024L
  }
}

/** 请求的语言 ID 与别名均不在教程 Catalog 中。 */
class DynamicTutorialNotFoundException(
  languageId: String,
) : IllegalArgumentException("Dynamic tutorial '$languageId' is not supported.")
