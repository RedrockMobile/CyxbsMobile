package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.internal.NpmIntegrity
import com.cyxbs.functions.code.npm.internal.NpmPackagePoolState
import com.cyxbs.functions.code.npm.internal.NpmRegistryPackageClient
import com.cyxbs.functions.code.npm.internal.NpmRegistryVersion
import com.cyxbs.functions.code.npm.internal.NpmSemver
import com.cyxbs.functions.code.npm.internal.NpmVersionRange
import com.cyxbs.functions.code.npm.internal.OkioNpmPackagePoolStateStore
import com.cyxbs.functions.code.npm.internal.PersistedNpmEntry
import com.cyxbs.functions.code.npm.internal.PersistedNpmPackage
import com.cyxbs.functions.code.npm.internal.PersistedNpmPackageId
import com.cyxbs.functions.code.npm.internal.PersistedNpmResolvedNode
import com.cyxbs.functions.code.npm.storage.NpmPackageArchiveStore
import com.cyxbs.functions.code.npm.storage.OkioNpmPackageArchiveStore
import com.cyxbs.functions.code.npm.transport.NpmHttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * 直接基于 npm registry 的全局包池与入口解析器。
 *
 * 核心状态流转：
 *
 * ```
 * ① 入口首次加载 / 请求变更 / 入口已被 GC
 *    EntryRequest ──请求 registry 元数据──> 递归锁定精确版本
 *         └──────────────────────────────> 复用同坐标本地 tgz，缺失才下载
 *                                           └──原子保存 EntryResolution
 *
 * ② 同一入口后续加载
 *    saved EntryResolution ──poolGeneration 未变──> 直接复用，全程不请求 registry
 *
 * ③ 其他入口向全局池加入了新版本
 *    saved generation != pool generation
 *         └──只在本地池按原 semver 范围重新解析
 *             ├──完整闭包存在：原子切换新 EntryResolution
 *             └──任一节点不存在：保留旧 Resolution，不访问 registry
 *
 * ④ 14 天 GC（mark-and-sweep）
 *    过期且无运行租约的 Entry ──移除 root
 *         └──从剩余 Entry 的精确节点做可达性标记
 *             └──删除不可达 tgz；共享依赖和有环依赖均按图可达性处理
 * ```
 *
 * 重要边界：
 *
 * 1. 首次解析的版本选择只看远端元数据，本地池不会把旧版本“吸附”进新入口。
 * 2. registry 完成精确版本选择后，同名同版本同 SRI 的归档仍从全局池复用。
 * 3. [NpmEntryVersion.Latest] 在每个包池实例首次使用该入口时刷新一次；[NpmEntryVersion.Exact]
 *    跨实例固定复用，不做启动刷新。
 * 4. 其他入口引起的池变化只触发本地重解析，不会额外请求 registry。
 * 5. 入口解析结果保存父包到具体依赖版本的边，允许同一个池中存在同名包的多个版本。
 * 6. 同一 [rootDirectory] 应复用一个长生命周期实例；运行租约是进程内状态，不跨多个实例协调。
 *
 * @param transport npm 元数据与 tgz 请求实现。
 * @param rootDirectory App 私有缓存目录；状态和归档会存放在其下。
 * @param registryBaseUrl npm registry 根地址。
 * @param clock 提供当前时间；默认使用系统时钟，测试可注入。
 * @param backgroundScope 执行更新后异步 GC 的长生命周期作用域；取消只会跳过本次清理，后续触发会重试。
 */
class NpmPackagePool(
  private val transport: NpmHttpTransport,
  rootDirectory: Path,
  registryBaseUrl: String = DEFAULT_REGISTRY_BASE_URL,
  fileSystem: FileSystem = FileSystem.SYSTEM,
  ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val backgroundScope: CoroutineScope = CoroutineScope(ioDispatcher),
  json: Json = Json { ignoreUnknownKeys = true },
  private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
  private val archiveStore: NpmPackageArchiveStore = OkioNpmPackageArchiveStore(
    rootDirectory = rootDirectory,
    fileSystem = fileSystem,
    ioDispatcher = ioDispatcher,
  )
  private val stateStore = OkioNpmPackagePoolStateStore(
    rootDirectory = rootDirectory,
    fileSystem = fileSystem,
    ioDispatcher = ioDispatcher,
    json = json,
  )
  private val mutex = Mutex()
  private val registryClient = NpmRegistryPackageClient(
    transport = transport,
    registryBaseUrl = registryBaseUrl,
    json = json,
  )
  private val activeLeaseCount = mutableMapOf<String, Int>()
  private val activeLeaseTokens = mutableMapOf<Long, ActiveNpmEntryLease>()
  private val latestRefreshAttempts = mutableSetOf<NpmEntryRefreshKey>()

  /**
   * 获取一个完整入口并持有运行租约。
   *
   * 调用方至少应在 [NpmModuleGraphFactory.create] 完成前持有租约；如果运行期仍会回读归档，则应持有到
   * Runtime 关闭。使用完必须调用 [NpmPreparedEntryLease.release]，否则该入口不会被 GC。
   * `Latest` 的远端刷新、完整闭包下载与校验全部在本方法返回前完成，不会在 Runtime 执行期间更新。
   *
   * @param refreshPolicy [NpmRefreshPolicy.AUTO] 按包池生命周期低频刷新并允许回退旧图；
   * [NpmRefreshPolicy.FORCE] 本次必须请求远端，失败时保留旧图但直接抛出异常。
   * @throws NpmResolutionException 包名、版本范围或 registry 依赖范围无效。
   * @throws NpmRegistryMismatchException registry 元数据身份、SRI 或 tarball URL 无效。
   * @throws NpmDownloadException 元数据或归档下载失败。
   * @throws NpmIntegrityException tgz 与 registry SRI 不一致。
   * @throws NpmStorageException 状态或归档读写失败。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(
    NpmResolutionException::class,
    NpmRegistryMismatchException::class,
    NpmDownloadException::class,
    NpmIntegrityException::class,
    NpmStorageException::class,
    CancellationException::class,
  )
  suspend fun acquireEntry(
    request: NpmEntryRequest,
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
  ): NpmPreparedEntryLease {
    return mutex.withLock {
      validateRequest(request)
      val now = clock()
      var state = stateStore.read()
      val scheduledGcDue = now - state.lastGcAtEpochMillis >= GC_INTERVAL_MILLIS
      val saved = state.entries.firstOrNull { it.entryName == request.entryName }
      val requestMatches = saved?.matches(request) == true

      /** 使用旧解析结果时，仍允许其他入口扩充的本地池触发一次纯本地重解析。 */
      fun selectSavedEntry(): PersistedNpmEntry {
        val reusable = checkNotNull(saved)
        return if (reusable.poolGeneration == state.generation) {
          reusable.touch(now)
        } else {
          resolveFromLocalPool(request, state.packages, now)
            ?.copy(poolGeneration = state.generation)
            ?: reusable.touch(now)
        }
      }

      val automaticLatestRefresh = request.version is NpmEntryVersion.Latest &&
        latestRefreshAttempts.add(request.toRefreshKey())
      val resolveRemotely = !requestMatches || automaticLatestRefresh ||
        refreshPolicy == NpmRefreshPolicy.FORCE
      var selectedEntry: PersistedNpmEntry
      var remoteApplied = false
      var uncommittedRemote: RemoteResolution? = null

      if (resolveRemotely) {
        try {
          val remote = resolveFromRegistry(request, now).also { uncommittedRemote = it }
          ensureRemoteArchives(remote.packages)
          val merged = mergePackages(state.packages, remote.packages)
          val nextGeneration = if (merged.size == state.packages.size) {
            state.generation
          } else {
            state.generation + 1
          }
          selectedEntry = remote.entry.copy(poolGeneration = nextGeneration)
          state = state.copy(
            generation = nextGeneration,
            packages = merged,
          )
          remoteApplied = true
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: NpmStorageException) {
          throw exception
        } catch (exception: NpmException) {
          uncommittedRemote?.let { cleanupUncommittedArchives(it.packages, state.packages) }
          // latest 刷新失败时只回退已经完整可执行的旧图；首次加载或固定版本变化必须直接失败。
          if (refreshPolicy == NpmRefreshPolicy.FORCE ||
            !automaticLatestRefresh || !requestMatches
          ) {
            throw exception
          }
          selectedEntry = selectSavedEntry()
        }
      } else {
        selectedEntry = selectSavedEntry()
      }

      if (!remoteApplied) ensureSavedArchives(selectedEntry, state.packages)

      state = state.copy(
        entries = state.entries
          .filterNot { it.entryName == request.entryName } + selectedEntry,
      )
      stateStore.write(state)

      val preparedEntry = selectedEntry.toPreparedEntry(state.packages, archiveStore)
      val token = nextLeaseToken()
      activeLeaseTokens[token] = ActiveNpmEntryLease(
        entryName = request.entryName,
        packages = selectedEntry.nodes
          .mapTo(mutableSetOf()) { PersistedNpmPackageId(it.name, it.version) },
      )
      activeLeaseCount[request.entryName] = activeLeaseCount.getOrElse(request.entryName) { 0 } + 1
      if (remoteApplied || scheduledGcDue) scheduleGarbageCollection()
      NpmPreparedEntryLease(
        preparedEntry = preparedEntry,
        releaseAction = { releaseLease(token) },
      )
    }
  }

  /**
   * 在租约保护下使用入口，并在 block 成功、失败或取消时自动释放。
   *
   * block 返回后若仍持有 [NpmPreparedEntry] 中的文件路径，调用方不得继续访问这些路径。
   *
   * @param refreshPolicy 本次入口的远端刷新策略，具体差异见 [NpmRefreshPolicy]。
   * @throws NpmException 准备入口期间的版本、网络、校验、存储或 Module 数据异常。
   * @throws CancellationException 调用协程或 [block] 被取消。
   */
  @Throws(NpmException::class, CancellationException::class)
  suspend fun <T> withEntry(
    request: NpmEntryRequest,
    refreshPolicy: NpmRefreshPolicy = NpmRefreshPolicy.AUTO,
    block: suspend (NpmPreparedEntry) -> T,
  ): T {
    val lease = acquireEntry(request, refreshPolicy)
    return try {
      block(lease.preparedEntry)
    } finally {
      lease.release()
    }
  }

  /**
   * 立即执行入口可达性 GC。
   *
   * 过期时间按入口最后一次成功获取计算。正在使用的入口只会更新本轮 GC 时间，不会被移除；包删除使用
   * 剩余 EntryResolution 的节点集合做标记，而不是维护易失真的整数引用计数。
   */
  @Throws(NpmStorageException::class, CancellationException::class)
  suspend fun collectGarbage() {
    mutex.withLock {
      val now = clock()
      stateStore.write(collectGarbage(stateStore.read(), now))
    }
  }

  /**
   * 异步触发一次尽力而为的可达性 GC。
   *
   * 调度发生在入口新图落盘且新租约登记之后；后台任务会重新获取包池锁，因此不会看到半提交状态。
   * 清理失败不影响已经准备完成的运行链路，后续更新、定时检查或手动 GC 会再次尝试。
   */
  private fun scheduleGarbageCollection() {
    backgroundScope.launch {
      try {
        collectGarbage()
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: NpmException) {
        // GC 只回收缓存，失败时保留现状并等待下一次触发，不中断 JavaScript 执行。
      }
    }
  }

  /** 先删除不可达归档，再提交不再引用这些归档的新状态。 */
  private suspend fun collectGarbage(
    state: NpmPackagePoolState,
    now: Long,
  ): NpmPackagePoolState {
    val retainedEntries = state.entries.filter { entry ->
      val active = activeLeaseCount.getOrElse(entry.entryName) { 0 } > 0
      active || now - entry.lastUsedAtEpochMillis < ENTRY_TTL_MILLIS
    }
    val retained = state.copy(entries = retainedEntries)
    val pruned = pruneUnreachablePackages(retained)
    return pruned.copy(
      generation = if (pruned.packages.size == state.packages.size) {
        state.generation
      } else {
        state.generation + 1
      },
      lastGcAtEpochMillis = now,
    )
  }

  /** 删除当前入口图与活动租约均不可达的归档，不改变入口解析代际。 */
  private suspend fun pruneUnreachablePackages(
    state: NpmPackagePoolState,
  ): NpmPackagePoolState {
    val reachable = state.entries
      .asSequence()
      .flatMap { it.nodes.asSequence() }
      .map { PersistedNpmPackageId(it.name, it.version) }
      .toMutableSet()
    activeLeaseTokens.values.forEach { reachable += it.packages }
    val removed = state.packages.filterNot { packageInfo ->
      PersistedNpmPackageId(packageInfo.name, packageInfo.version) in reachable
    }
    removed.forEach { packageInfo ->
      archiveStore.remove(packageInfo.name, packageInfo.version, packageInfo.integrity)
    }
    return state.copy(packages = state.packages - removed.toSet())
  }

  /**
   * 首次解析完全以 registry 为准。
   *
   * metadata 按包名去重请求；节点按 name/version 去重，因此依赖环不会无限递归，同名包的不同版本仍会
   * 分别保存。
   */
  private suspend fun resolveFromRegistry(
    request: NpmEntryRequest,
    now: Long,
  ): RemoteResolution {
    val metadata = mutableMapOf<String, com.cyxbs.functions.code.npm.internal.NpmRegistryPackageMetadata>()
    val nodes = linkedMapOf<NpmPackageId, PersistedNpmResolvedNode>()
    val packages = linkedMapOf<NpmPackageId, PersistedNpmPackage>()
    val visiting = mutableSetOf<NpmPackageId>()

    suspend fun resolve(packageName: String, versionSpec: String): NpmPackageId {
      val packageMetadata = metadata.getOrPutSuspending(packageName) {
        registryClient.fetch(packageName)
      }
      val selected = packageMetadata.select(versionSpec)
      val id = selected.id
      if (id in nodes || !visiting.add(id)) return id

      val dependencies = linkedMapOf<String, PersistedNpmPackageId>()
      for ((dependencyName, dependencySpec) in selected.dependencies.entries.sortedBy { it.key }) {
        val dependency = resolve(dependencyName, dependencySpec)
        dependencies[dependencyName] = dependency.toPersisted()
      }
      visiting.remove(id)
      nodes[id] = PersistedNpmResolvedNode(
        name = id.name,
        version = id.version,
        dependencies = dependencies,
      )
      packages[id] = selected.toPersistedPackage()
      return id
    }

    val root = resolve(request.packageName, request.versionSpec())
    return RemoteResolution(
      entry = PersistedNpmEntry(
        entryName = request.entryName,
        packageName = request.packageName,
        versionSpec = request.versionSpec(),
        entryModule = request.entryModule,
        rootName = root.name,
        rootVersion = root.version,
        resolvedAtEpochMillis = now,
        lastUsedAtEpochMillis = now,
        poolGeneration = 0,
        nodes = orderNodes(root, nodes),
      ),
      packages = packages.values.toList(),
    )
  }

  /**
   * 池代际变化后的纯本地重解析。
   *
   * 每条 semver 边都从池内最高版本开始尝试；若候选的任一传递依赖无法在池中满足，则回退到较低候选。
   * 整个根闭包失败时返回 null，调用方继续使用旧解析结果，不会访问 registry。
   */
  private fun resolveFromLocalPool(
    request: NpmEntryRequest,
    packages: List<PersistedNpmPackage>,
    now: Long,
  ): PersistedNpmEntry? {
    val byName = packages.groupBy(PersistedNpmPackage::name)
    val nodes = linkedMapOf<NpmPackageId, PersistedNpmResolvedNode>()
    val visiting = mutableSetOf<NpmPackageId>()

    fun candidates(packageName: String, versionSpec: String): List<PersistedNpmPackage> {
      val range = runCatching {
        if (versionSpec == "latest") null else NpmVersionRange.parse(versionSpec)
      }.getOrNull()
      if (versionSpec != "latest" && range == null) return emptyList()
      return byName[packageName].orEmpty()
        .mapNotNull { item -> NpmSemver.parseOrNull(item.version)?.let { it to item } }
        .filter { (version) ->
          version.prerelease.isEmpty() && (range == null || range.matches(version))
        }
        .sortedByDescending { it.first }
        .map { it.second }
    }

    fun resolve(packageName: String, versionSpec: String): NpmPackageId? {
      candidates(packageName, versionSpec).forEach { candidate ->
        val id = NpmPackageId(candidate.name, candidate.version)
        if (id in nodes || id in visiting) return id
        val nodeSnapshot = LinkedHashMap(nodes)
        visiting += id
        val dependencies = linkedMapOf<String, PersistedNpmPackageId>()
        var complete = true
        for ((dependencyName, dependencySpec) in candidate.dependencySpecs.entries.sortedBy { it.key }) {
          val dependency = resolve(dependencyName, dependencySpec)
          if (dependency == null) {
            complete = false
            break
          }
          dependencies[dependencyName] = dependency.toPersisted()
        }
        visiting -= id
        if (complete) {
          nodes[id] = PersistedNpmResolvedNode(id.name, id.version, dependencies)
          return id
        }
        nodes.clear()
        nodes.putAll(nodeSnapshot)
      }
      return null
    }

    val root = resolve(request.packageName, request.versionSpec()) ?: return null
    return PersistedNpmEntry(
      entryName = request.entryName,
      packageName = request.packageName,
      versionSpec = request.versionSpec(),
      entryModule = request.entryModule,
      rootName = root.name,
      rootVersion = root.version,
      resolvedAtEpochMillis = now,
      lastUsedAtEpochMillis = now,
      poolGeneration = 0,
      nodes = orderNodes(root, nodes),
    )
  }

  /** 为远端本轮锁定的精确版本复用或下载归档；版本选择不会读取本地池。 */
  private suspend fun ensureRemoteArchives(packages: List<PersistedNpmPackage>) {
    packages.forEach { packageInfo ->
      if (archiveStore.find(
          packageInfo.name,
          packageInfo.version,
          packageInfo.integrity,
        ) == null
      ) {
        downloadAndStore(packageInfo)
      }
    }
  }

  /** 远端刷新未能提交时，删除本轮新下载且未被当前池状态登记的归档。 */
  private suspend fun cleanupUncommittedArchives(
    remotePackages: List<PersistedNpmPackage>,
    currentPackages: List<PersistedNpmPackage>,
  ) {
    val retained = currentPackages
      .mapTo(mutableSetOf()) { Triple(it.name, it.version, it.integrity) }
    remotePackages.forEach { packageInfo ->
      if (Triple(packageInfo.name, packageInfo.version, packageInfo.integrity) !in retained) {
        archiveStore.remove(packageInfo.name, packageInfo.version, packageInfo.integrity)
      }
    }
  }

  /** 已保存入口只使用本地精确元数据；归档损坏或丢失时按保存的 tarball URL 恢复。 */
  private suspend fun ensureSavedArchives(
    entry: PersistedNpmEntry,
    packages: List<PersistedNpmPackage>,
  ) {
    val metadata = packages.associateBy { NpmPackageId(it.name, it.version) }
    entry.nodes.forEach { node ->
      val packageInfo = metadata[NpmPackageId(node.name, node.version)]
        ?: throw NpmStorageException(
          "Npm entry '${entry.entryName}' references missing package metadata " +
            "'${node.name}@${node.version}'.",
        )
      if (archiveStore.find(
          packageInfo.name,
          packageInfo.version,
          packageInfo.integrity,
        ) == null
      ) {
        downloadAndStore(packageInfo)
      }
    }
  }

  /** 下载并再次执行 SRI 校验，避免 registry 元数据与真实 tgz 内容不一致。 */
  private suspend fun downloadAndStore(packageInfo: PersistedNpmPackage) {
    val bytes = transport.get(packageInfo.tarballUrl)
    val integrity = NpmIntegrity.parse(packageInfo.integrity, packageInfo.name)
    if (!integrity.matches(bytes)) {
      throw NpmIntegrityException(
        "Downloaded npm package '${packageInfo.name}@${packageInfo.version}' " +
          "does not match registry integrity.",
      )
    }
    archiveStore.write(
      packageInfo.name,
      packageInfo.version,
      packageInfo.integrity,
      bytes,
    )
  }

  /** 同坐标元数据必须不可变；只有真正加入新坐标时才推进池代际。 */
  private fun mergePackages(
    current: List<PersistedNpmPackage>,
    added: List<PersistedNpmPackage>,
  ): List<PersistedNpmPackage> {
    val merged = current.associateByTo(linkedMapOf()) {
      NpmPackageId(it.name, it.version)
    }
    added.forEach { packageInfo ->
      val id = NpmPackageId(packageInfo.name, packageInfo.version)
      val existing = merged[id]
      if (existing != null && existing != packageInfo) {
        throw NpmRegistryMismatchException(
          "Npm registry changed immutable metadata for '${id.name}@${id.version}'.",
        )
      }
      merged[id] = packageInfo
    }
    return merged.values.sortedWith(compareBy(PersistedNpmPackage::name, PersistedNpmPackage::version))
  }

  /** 释放租约时只修改内存计数；持久化 lastUsed 已在 acquire 成功事务中完成。 */
  private suspend fun releaseLease(token: Long) {
    mutex.withLock {
      val lease = activeLeaseTokens.remove(token) ?: return@withLock
      val remaining = activeLeaseCount.getOrElse(lease.entryName) { 1 } - 1
      if (remaining <= 0) activeLeaseCount.remove(lease.entryName)
      else activeLeaseCount[lease.entryName] = remaining
    }
  }

  /** 生成当前实例内不重复的租约 token。 */
  private fun nextLeaseToken(): Long {
    var token: Long
    do {
      token = Random.nextLong()
    } while (token in activeLeaseTokens)
    return token
  }

  /** 拒绝会造成状态 key 或 registry 路由歧义的入口请求。 */
  private fun validateRequest(request: NpmEntryRequest) {
    if (!PACKAGE_NAME_REGEX.matches(request.packageName) || request.packageName.length > 214) {
      throw NpmResolutionException("Invalid npm package name '${request.packageName}'.")
    }
    if (request.entryName.isBlank() || '\u0000' in request.entryName) {
      throw NpmResolutionException("Npm entry name must not be blank or contain NUL.")
    }
    request.entryModule?.let { module ->
      if (module.isBlank() || module.startsWith('/') || '\\' in module ||
        '\u0000' in module || module.split('/').any { it == ".." }
      ) {
        throw NpmResolutionException("Invalid npm entry Module '$module'.")
      }
    }
    val exactVersion = (request.version as? NpmEntryVersion.Exact)?.value
    if (exactVersion != null && NpmSemver.parseOrNull(exactVersion) == null) {
      throw NpmResolutionException(
        "Npm exact entry version '$exactVersion' is not a valid semantic version.",
      )
    }
  }

  private companion object {
    const val DEFAULT_REGISTRY_BASE_URL = "https://registry.npmjs.org"
    val ENTRY_TTL_MILLIS = 14.days.inWholeMilliseconds
    val GC_INTERVAL_MILLIS = 1.days.inWholeMilliseconds
    val PACKAGE_NAME_REGEX = Regex(
      """(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*""",
    )
  }
}

/**
 * 带运行租约的完整入口。
 *
 * [release] 幂等；释放后 [preparedEntry] 仍是普通数据对象，但其中路径可能在后续 GC 中失效。
 */
class NpmPreparedEntryLease internal constructor(
  val preparedEntry: NpmPreparedEntry,
  private val releaseAction: suspend () -> Unit,
) {
  private var released = false

  /**
   * 释放入口的 GC 保护；允许重复调用。
   *
   * @throws CancellationException 等待包池状态锁时调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun release() {
    if (released) return
    released = true
    releaseAction()
  }
}

private data class RemoteResolution(
  val entry: PersistedNpmEntry,
  val packages: List<PersistedNpmPackage>,
)

/** 活动租约精确持有的包集合，防止 latest 切换后旧归档在运行期间被回收。 */
private data class ActiveNpmEntryLease(
  val entryName: String,
  val packages: Set<PersistedNpmPackageId>,
)

/** 同一包池实例内 latest 刷新去重使用的完整入口身份。 */
private data class NpmEntryRefreshKey(
  val entryName: String,
  val packageName: String,
  val entryModule: String?,
)

/** 把公开版本策略转换为 registry 和持久化状态使用的版本文本。 */
private fun NpmEntryRequest.versionSpec(): String {
  return when (val requested = version) {
    NpmEntryVersion.Latest -> "latest"
    is NpmEntryVersion.Exact -> requested.value
  }
}

/** latest 的入口配置变化后应当在同一包池实例内重新尝试一次。 */
private fun NpmEntryRequest.toRefreshKey(): NpmEntryRefreshKey {
  return NpmEntryRefreshKey(
    entryName = entryName,
    packageName = packageName,
    entryModule = entryModule,
  )
}

private fun PersistedNpmEntry.matches(request: NpmEntryRequest): Boolean {
  return packageName == request.packageName &&
    versionSpec == request.versionSpec() &&
    entryModule == request.entryModule
}

private fun PersistedNpmEntry.touch(now: Long): PersistedNpmEntry {
  return copy(lastUsedAtEpochMillis = now)
}

private fun NpmPackageId.toPersisted() = PersistedNpmPackageId(name, version)

private fun PersistedNpmPackageId.toPublic() = NpmPackageId(name, version)

private fun NpmRegistryVersion.toPersistedPackage() = PersistedNpmPackage(
  name = id.name,
  version = id.version,
  integrity = integrity.encoded,
  tarballUrl = tarballUrl,
  dependencySpecs = dependencies,
)

/** DFS 后序保证依赖优先；visiting 集合使依赖环只输出一次。 */
private fun orderNodes(
  root: NpmPackageId,
  nodes: Map<NpmPackageId, PersistedNpmResolvedNode>,
): List<PersistedNpmResolvedNode> {
  val visiting = mutableSetOf<NpmPackageId>()
  val visited = mutableSetOf<NpmPackageId>()
  val result = mutableListOf<PersistedNpmResolvedNode>()

  fun visit(id: NpmPackageId) {
    if (id in visited || !visiting.add(id)) return
    val node = nodes[id] ?: return
    node.dependencies.values.forEach { visit(it.toPublic()) }
    visiting -= id
    visited += id
    result += node
  }
  visit(root)
  return result
}

/** 把持久化精确图转换为执行层数据，并重新确认每个归档仍存在。 */
private suspend fun PersistedNpmEntry.toPreparedEntry(
  packages: List<PersistedNpmPackage>,
  archiveStore: NpmPackageArchiveStore,
): NpmPreparedEntry {
  val metadata = packages.associateBy { NpmPackageId(it.name, it.version) }
  val archives = nodes.map { node ->
    val id = NpmPackageId(node.name, node.version)
    val packageInfo = metadata[id]
      ?: throw NpmStorageException("Npm package metadata '${id.name}@${id.version}' is missing.")
    archiveStore.find(id.name, id.version, packageInfo.integrity)
      ?: throw NpmStorageException("Npm package archive '${id.name}@${id.version}' is missing.")
  }
  return NpmPreparedEntry(
    resolvedAtEpochMillis = resolvedAtEpochMillis,
    entryPackage = NpmPackageId(rootName, rootVersion),
    entryModule = entryModule,
    archives = archives,
    resolvedPackages = nodes.map { node ->
      val id = NpmPackageId(node.name, node.version)
      val packageInfo = checkNotNull(metadata[id])
      NpmResolvedPackage(
        id = id,
        integrity = packageInfo.integrity,
        dependencies = node.dependencies.mapValues { (_, value) -> value.toPublic() },
      )
    },
  )
}

/** MutableMap.getOrPut 的 suspend 版本，避免 metadata 重复请求。 */
private suspend inline fun <K, V> MutableMap<K, V>.getOrPutSuspending(
  key: K,
  crossinline defaultValue: suspend () -> V,
): V {
  get(key)?.let { return it }
  return defaultValue().also { put(key, it) }
}
