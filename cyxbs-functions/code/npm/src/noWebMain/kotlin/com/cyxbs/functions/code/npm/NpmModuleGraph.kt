package com.cyxbs.functions.code.npm

import com.cyxbs.functions.code.npm.internal.readNpmPackageFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.SYSTEM

/**
 * 已解包到内存、可直接连接 JavaScript 引擎 Module Loader 的 npm 依赖图。
 *
 * [normalize] 和 [load] 都只访问构建完成后的只读内存，可在引擎持锁期间同步调用。Module 标准名称
 * 包含包名、锁定版本和包内路径，不会与其他版本或普通业务 Module 冲突。
 */
class NpmModuleGraph internal constructor(
  val entryModuleName: String,
  private val packages: Map<NpmPackageId, NpmPackageModules>,
  private val modules: Map<String, NpmModuleRecord>,
) {

  /**
   * 将相对 import、裸包名或包子路径解析为标准 Module 名称。
   *
   * 不属于当前 npm 依赖图的名称保持原值，便于业务在外层组合其他 Module 来源。
   *
   * @throws NpmModuleResolutionException 已锁定包请求了不存在或未导出的文件。
   */
  @Throws(NpmModuleResolutionException::class)
  fun normalize(baseName: String, requestedName: String): String {
    return normalizeOrNull(baseName, requestedName) ?: when {
      requestedName.startsWith("./") || requestedName.startsWith("../") -> {
        normalizeRelativePath(baseName, requestedName)
      }
      else -> requestedName
    }
  }

  /**
   * 只解析当前 npm 图明确拥有的请求，无法处理时返回 null 交给组合 Loader。
   *
   * 该边界避免 npm Loader 抢先改写业务 Loader 中普通 Module 的相对路径。
   */
  internal fun normalizeOrNull(baseName: String, requestedName: String): String? {
    if (requestedName.startsWith("./") || requestedName.startsWith("../")) {
      val owner = modules[baseName]
      if (owner == null) return null
      val baseDirectory = owner.path.substringBeforeLast('/', missingDelimiterValue = "")
      val target = normalizePackagePath(
        if (baseDirectory.isEmpty()) requestedName else "$baseDirectory/$requestedName",
      )
      return owner.packageModules.resolveFile(target).canonicalName
    }
    if (requestedName.startsWith('/') || requestedName.contains(':')) {
      return requestedName.takeIf(modules::containsKey)
    }

    val request = parsePackageRequest(requestedName) ?: return null
    val owner = modules[baseName]?.packageModules
    val packageModules = when {
      owner?.name == request.packageName -> owner
      owner != null -> owner.dependencies[request.packageName]
      else -> packages.values.singleOrNull { it.name == request.packageName }
    } ?: return null
    return if (request.subpath.isEmpty()) {
      packageModules.entry.canonicalName
    } else {
      packageModules.resolveSubpath(request.subpath).canonicalName
    }
  }

  /** 返回标准 Module 名称对应的 JavaScript 源码，不属于当前依赖图时返回 null。 */
  fun load(name: String): String? = modules[name]?.source
}

/**
 * 从 [NpmPreparedEntry] 的已校验 tgz 构建只读 Module 图。
 *
 * 构建过程会解压归档、校验 package.json 坐标并解析 ESM 入口；完成后执行阶段不再访问文件系统。
 */
class NpmModuleGraphFactory(
  private val fileSystem: FileSystem = FileSystem.SYSTEM,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val json: Json = Json { ignoreUnknownKeys = true },
) {

  /**
   * @throws NpmModuleResolutionException tgz、package.json、入口或 exports 无法解析。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(NpmModuleResolutionException::class, CancellationException::class)
  suspend fun create(preparedEntry: NpmPreparedEntry): NpmModuleGraph {
    return try {
      withContext(ioDispatcher) {
        buildGraph(preparedEntry)
      }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: NpmModuleResolutionException) {
      throw exception
    } catch (throwable: Throwable) {
      throw NpmModuleResolutionException("Failed to build npm Module graph.", throwable)
    }
  }

  /** 解压完整闭包并在返回前完成所有入口校验。 */
  private fun buildGraph(preparedEntry: NpmPreparedEntry): NpmModuleGraph {
    val extracted = preparedEntry.archives.associate { archive ->
      val files = readNpmPackageFiles(fileSystem, archive.archivePath)
      val packageJsonBytes = files[PACKAGE_JSON]
        ?: throw NpmModuleResolutionException(
          "Npm package '${archive.packageName}@${archive.version}' has no package.json.",
        )
      val packageJson = try {
        json.parseToJsonElement(packageJsonBytes.decodeToString()).jsonObject
      } catch (throwable: Throwable) {
        throw NpmModuleResolutionException(
          "Npm package '${archive.packageName}@${archive.version}' has invalid package.json.",
          throwable,
        )
      }
      validatePackageIdentity(archive.packageName, archive.version, packageJson)
      val sources = files.mapNotNull { (path, bytes) ->
        path.takeIf(::isJavaScriptModulePath)?.let { it to bytes.decodeToString() }
      }.toMap()
      if (sources.isEmpty()) {
        throw NpmModuleResolutionException(
          "Npm package '${archive.packageName}@${archive.version}' contains no JavaScript modules.",
        )
      }
      NpmPackageId(archive.packageName, archive.version) to ExtractedNpmPackage(
        name = archive.packageName,
        version = archive.version,
        packageJson = packageJson,
        sources = sources,
      )
    }
    if (extracted.size != preparedEntry.archives.size) {
      throw NpmModuleResolutionException("Prepared npm entry contains duplicate package archives.")
    }

    val rootId = preparedEntry.entryPackage
    val resolvedById = preparedEntry.resolvedPackages.associateBy(NpmResolvedPackage::id)
    if (resolvedById.keys != extracted.keys || resolvedById.size != preparedEntry.resolvedPackages.size) {
      throw NpmModuleResolutionException(
        "Prepared npm entry archives do not match its resolved package graph.",
      )
    }
    preparedEntry.archives.forEach { archive ->
      val resolved = resolvedById[NpmPackageId(archive.packageName, archive.version)]
      if (resolved != null && resolved.integrity != archive.integrity) {
        throw NpmModuleResolutionException(
          "Prepared npm package '${archive.packageName}@${archive.version}' has inconsistent SRI.",
        )
      }
    }
    val packages = extracted.mapValues { (id, item) ->
      val entryOverride = preparedEntry.entryModule?.takeIf {
        id == rootId && it.isNotBlank()
      }
      item.toPackageModules(entryOverride)
    }
    packages.forEach { (id, packageModules) ->
      packageModules.dependencies = resolvedById.getValue(id).dependencies.mapValues {
        (dependencyName, dependencyId) ->
        packages[dependencyId]
          ?: throw NpmModuleResolutionException(
            "Npm package '${id.name}@${id.version}' dependency " +
              "'$dependencyName@${dependencyId.version}' is missing.",
          )
      }
    }
    val entryPackage = packages[rootId]
      ?: throw NpmModuleResolutionException(
        "Prepared npm entry package '${rootId.name}@${rootId.version}' is missing.",
      )
    val modules = packages.values
      .flatMap { it.modules.values }
      .associateBy(NpmModuleRecord::canonicalName)
    return NpmModuleGraph(
      entryModuleName = entryPackage.entry.canonicalName,
      packages = packages,
      modules = modules,
    )
  }

  /** 防止缓存坐标与归档内部身份不一致。 */
  private fun validatePackageIdentity(
    expectedName: String,
    expectedVersion: String,
    packageJson: JsonObject,
  ) {
    val actualName = packageJson.string("name")
    val actualVersion = packageJson.string("version")
    if (actualName != expectedName || actualVersion != expectedVersion) {
      throw NpmModuleResolutionException(
        "Npm package identity '$actualName@$actualVersion' does not match " +
          "'$expectedName@$expectedVersion'.",
      )
    }
  }
}

/** 解包后尚未建立标准名称的单个包。 */
private data class ExtractedNpmPackage(
  val name: String,
  val version: String,
  val packageJson: JsonObject,
  val sources: Map<String, String>,
) {
  fun toPackageModules(entryOverride: String?): NpmPackageModules {
    val records = sources.mapValues { (path, source) ->
      NpmModuleRecord(
        canonicalName = "npm:$name@$version/$path",
        path = path,
        source = source,
      )
    }
    val packageModules = NpmPackageModules(
      name = name,
      version = version,
      packageJson = packageJson,
      modules = records,
    )
    records.values.forEach { it.packageModules = packageModules }
    packageModules.entry = packageModules.resolveFile(
      entryOverride ?: packageModules.resolveRootExport(),
    )
    return packageModules
  }
}

/** 完整建立后的包索引。 */
internal class NpmPackageModules(
  val name: String,
  val version: String,
  private val packageJson: JsonObject,
  val modules: Map<String, NpmModuleRecord>,
) {
  lateinit var entry: NpmModuleRecord
  /** 当前入口解析结果为本包锁定的直接依赖；相同包版本在不同入口中可以指向不同兼容版本。 */
  lateinit var dependencies: Map<String, NpmPackageModules>

  fun resolveRootExport(): String {
    val exports = packageJson["exports"]
    return resolveExportsTarget(exports, ".")
      ?: packageJson.string("module")
      ?: packageJson.string("main")
      ?: "index.js"
  }

  fun resolveSubpath(subpath: String): NpmModuleRecord {
    val exports = packageJson["exports"]
    val exported = resolveExportsTarget(exports, "./$subpath")
    if (exports != null && exported == null) {
      throw NpmModuleResolutionException(
        "Npm package '$name@$version' does not export './$subpath'.",
      )
    }
    return resolveFile(exported ?: subpath)
  }

  fun resolveFile(path: String): NpmModuleRecord {
    val normalized = normalizePackagePath(path)
    val candidates = buildList {
      add(normalized)
      if (!normalized.hasJavaScriptExtension()) {
        add("$normalized.js")
        add("$normalized.mjs")
      }
      add("$normalized/index.js")
      add("$normalized/index.mjs")
    }
    return candidates.firstNotNullOfOrNull(modules::get)
      ?: throw NpmModuleResolutionException(
        "Npm package '$name@$version' has no JavaScript Module '$normalized'.",
      )
  }
}

/** 单个可加载 Module；所属包在图构建阶段回填。 */
internal class NpmModuleRecord(
  val canonicalName: String,
  val path: String,
  val source: String,
) {
  lateinit var packageModules: NpmPackageModules
}

private data class NpmPackageRequest(
  val packageName: String,
  val subpath: String,
)

/** 解析 scoped/unscoped 裸包名，非包请求返回 null。 */
private fun parsePackageRequest(requestedName: String): NpmPackageRequest? {
  val segments = requestedName.split('/')
  if (segments.isEmpty() || segments.first().isEmpty()) return null
  return if (requestedName.startsWith('@')) {
    if (segments.size < 2 || segments[1].isEmpty()) return null
    NpmPackageRequest(
      packageName = segments.take(2).joinToString("/"),
      subpath = segments.drop(2).joinToString("/"),
    )
  } else {
    NpmPackageRequest(
      packageName = segments.first(),
      subpath = segments.drop(1).joinToString("/"),
    )
  }
}

/** 按 Node ESM 常见优先级读取根导出、精确子路径和单星号子路径。 */
private fun resolveExportsTarget(exports: JsonElement?, request: String): String? {
  if (exports == null) return null
  if (exports is JsonPrimitive) return exports.contentOrNull.takeIf { request == "." }
  if (exports is JsonArray) {
    return exports.firstNotNullOfOrNull { resolveExportsTarget(it, request) }
  }
  if (exports !is JsonObject) return null

  val subpathExports = exports.keys.any { it.startsWith('.') }
  if (!subpathExports) return selectConditionalTarget(exports).takeIf { request == "." }
  exports[request]?.let { return selectTarget(it) }
  exports.forEach { (pattern, target) ->
    val wildcard = pattern.indexOf('*')
    if (wildcard < 0) return@forEach
    val prefix = pattern.substring(0, wildcard)
    val suffix = pattern.substring(wildcard + 1)
    if (!request.startsWith(prefix) || !request.endsWith(suffix)) return@forEach
    val value = request.substring(prefix.length, request.length - suffix.length)
    val selected = selectTarget(target)?.replace("*", value)
    if (selected != null) return selected
  }
  return null
}

/** 从 import/browser/default 条件中选择 QuickJS 可执行的 ESM 目标。 */
private fun selectTarget(element: JsonElement): String? {
  return when (element) {
    is JsonPrimitive -> element.contentOrNull
    is JsonArray -> element.firstNotNullOfOrNull(::selectTarget)
    is JsonObject -> selectConditionalTarget(element)
  }
}

private fun selectConditionalTarget(conditions: JsonObject): String? {
  listOf("import", "browser", "default").forEach { condition ->
    conditions[condition]?.let { target -> selectTarget(target)?.let { return it } }
  }
  return null
}

/** 规范化包内路径并拒绝越出包根目录。 */
private fun normalizePackagePath(path: String): String {
  val result = mutableListOf<String>()
  path.replace('\\', '/').removePrefix("./").split('/').forEach { segment ->
    when (segment) {
      "", "." -> Unit
      ".." -> if (result.isEmpty()) {
        throw NpmModuleResolutionException("Npm Module path escapes its package root.")
      } else {
        result.removeAt(result.lastIndex)
      }
      else -> result += segment
    }
  }
  if (result.isEmpty()) throw NpmModuleResolutionException("Npm Module path is empty.")
  return result.joinToString("/")
}

/** 为不属于 npm 图的普通 Module 保留基础相对路径解析能力。 */
private fun normalizeRelativePath(baseName: String, requestedName: String): String {
  val baseDirectory = baseName.substringBeforeLast('/', missingDelimiterValue = "")
  return normalizePackagePath(
    if (baseDirectory.isEmpty()) requestedName else "$baseDirectory/$requestedName",
  )
}

private fun JsonObject.string(name: String): String? {
  return (get(name) as? JsonPrimitive)?.contentOrNull
}

private fun String.hasJavaScriptExtension(): Boolean {
  return endsWith(".js") || endsWith(".mjs") || endsWith(".cjs")
}

private fun isJavaScriptModulePath(path: String): Boolean = path.hasJavaScriptExtension()

private const val PACKAGE_JSON = "package.json"
