package npm

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.Inject

/**
 * 生成入口包及其本地项目依赖的 debug npm bundle，并汇总到根项目的 Desktop 调试源目录。
 *
 * ```text
 * prepare 入口包 + 本地依赖池 + 可选 Runtime
 *          │
 *          ├── Runtime 与 registry 稳定版一致 ──> 复用稳定坐标，不生成 Runtime tgz
 *          └── Runtime 与 registry 稳定版不同 ──> 生成同时间戳的新 Runtime tgz
 *                              │
 *                              ▼
 *        按依赖拓扑从叶子到入口解析每个包，并写入最终依赖精确坐标
 *                    │
 *          与 registry 稳定版逐包比较 SRI
 *                    │
 *                    ├── 一致：生成稳定坐标 tgz
 *                    └── 不同：与本地最新 debug tgz 比较
 *                                  │
 *                                  ├── 一致：复用已有 debug 坐标
 *                                  └── 不同：生成新时间戳 debug 坐标
 *                              │
 *      按 npm 包名与精确版本原子汇总到 root/build/npm/debug-source
 *                              │
 *              ├── Desktop：直接读取，不复制
 *              └── Android 安装任务：按入口清单注入同一批包并重启 App
 * ```
 *
 * debug 版本格式为 `<下一稳定补丁版本>-debug.<yyyyMMddHHmmss>`，时间固定使用上海时区。同次任务
 * 的入口包、依赖包与可选 Runtime 共用一个时间戳。变化检测始终使用稳定版本号和已经解析的下级
 * 精确坐标计算候选 tgz 的 SRI；Registry 不一致时还会以本地最新 debug 版本重新计算并比较实际
 * 归档 SRI，时间戳只在内容或依赖坐标相对本地最新版本也发生变化后写入。这样依赖代码变化会先
 * 生成新依赖版本，再自然推动上层包生成引用该版本的新产物。静态 npm 包不配置 Runtime 输入，仍
 * 复用相同的版本比较链路。根项目调试源会包含入口可达的全部本地包；其中内容与 Registry 一致的
 * 包保留稳定坐标，发生变化的包才使用 debug 坐标。共享源和 Android 设备都按精确版本保存，
 * 不同入口需要的历史版本可以并存；进入正常 npm 包池后的归档仍遵循包池 14 天可达性 GC。
 */
@DisableCachingByDefault(because = "任务需要查询 Registry，并修改多个入口共享的本地调试源")
abstract class PrepareDebugNpmBundleTask : DefaultTask() {

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageDirectory: DirectoryProperty

  /**
   * 构建中所有可独立发布模块的 prepare 目录。
   *
   * 任务只会从 [packageDirectory] 的 dependencies 开始遍历本地可达包；无关语言包即使出现在
   * 集合中也不会查询 Registry、打包或注入设备。
   */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val localPackageDirectories: ConfigurableFileCollection

  @get:InputDirectory
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val runtimePackageDirectory: DirectoryProperty

  @get:Input
  @get:Optional
  abstract val runtimePackageName: Property<String>

  @get:Input
  @get:Optional
  abstract val runtimeStableVersion: Property<String>

  @get:Input
  abstract val npmExecutable: Property<String>

  @get:Input
  abstract val registryUrl: Property<String>

  @get:Internal
  abstract val workingDirectory: DirectoryProperty

  /**
   * 根项目共享的 Desktop npm 调试源。多个入口按包名更新自身可达图，不清理其他入口的包。
   *
   * 该目录由多个入口任务共同维护，因此不声明为独占 OutputDirectory；任务始终执行远端比较，
   * 并使用临时文件加原子移动避免 Desktop 读到半写入的 tgz。
   */
  @get:Internal
  abstract val debugSourceDirectory: DirectoryProperty

  /** 当前入口可达的本地包清单，Android 安装任务据此消费统一调试源。 */
  @get:Internal
  abstract val manifestFile: RegularFileProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * 构建并汇总本地 bundle，同时生成当前入口清单。
   *
   * @throws GradleException 当 npm 产物、Registry 响应或本地调试源写入失败时抛出。
   */
  @TaskAction
  fun prepare() {
    val outputRoot = workingDirectory.get().asFile
    val packageSource = packageDirectory.get().asFile
    fileSystemOperations.delete { delete(outputRoot) }
    outputRoot.mkdirs()

    val buildTimestamp = DEBUG_TIME_FORMATTER.format(LocalDateTime.now(DEBUG_ZONE_ID))
    val runtimeResolution = resolveOptionalRuntime(
      buildTimestamp = buildTimestamp,
      outputRoot = outputRoot,
    )
    val localPackages = readLocalPackageSources(packageSource)
    val packageResolutions = linkedMapOf<String, BusinessPackageResolution>()
    resolveLocalPackageOrder(localPackages, readPackageName(packageSource))
      .forEachIndexed { index, source ->
        val dependencyVersions = source.dependencies.mapNotNull { dependencyName ->
          packageResolutions[dependencyName]?.let { resolution ->
            dependencyName to resolution.version
          }
        }.toMap()
        packageResolutions[source.name] = resolveBusinessPackage(
          source = source,
          dependencyVersions = dependencyVersions,
          runtimeVersion = runtimeResolution?.version,
          buildTimestamp = buildTimestamp,
          outputRoot = outputRoot,
          outputIndex = index,
        )
      }
    val manifest = synchronizeDebugSource(runtimeResolution, packageResolutions.values)
    writeManifest(manifest)
    val runtimeSummary = runtimeResolution?.let { resolution ->
      "${runtimePackageName.get()}@${resolution.version}"
    } ?: "not required"
    logger.lifecycle(
      "Prepared local debug npm graph in {}; packages={}, Runtime {}.",
      debugSourceDirectory.get().asFile,
      packageResolutions.values.joinToString { "${it.name}@${it.version}" },
      runtimeSummary,
    )
  }

  /**
   * 解析可选 Runtime；JS 包必须同时配置目录、包名和稳定版本，静态包则三项都不配置。
   *
   * @return JS 包的 Runtime 解析结果；静态包返回 null。
   * @throws GradleException 当 Runtime 输入只配置了一部分时抛出，避免生成依赖坐标不完整的包。
   */
  private fun resolveOptionalRuntime(
    buildTimestamp: String,
    outputRoot: File,
  ): RuntimeResolution? {
    val runtimeSource = runtimePackageDirectory.orNull?.asFile
    val runtimeName = runtimePackageName.orNull
    val stableVersion = runtimeStableVersion.orNull
    if (runtimeSource == null && runtimeName == null && stableVersion == null) return null
    if (runtimeSource == null || runtimeName == null || stableVersion == null) {
      throw GradleException(
        "Runtime package directory, name and stable version must be configured together.",
      )
    }
    return resolveRuntime(
      runtimeSource = runtimeSource,
      runtimeName = runtimeName,
      stableVersion = stableVersion,
      buildTimestamp = buildTimestamp,
      outputRoot = outputRoot,
    )
  }

  /** 以稳定版本计算候选 SRI；与 registry 一致则复用稳定坐标，否则生成新 debug Runtime。 */
  private fun resolveRuntime(
    runtimeSource: File,
    runtimeName: String,
    stableVersion: String,
    buildTimestamp: String,
    outputRoot: File,
  ): RuntimeResolution {
    val stableDirectory = copyPackage(runtimeSource, outputRoot.resolve("runtime-stable"))
    updatePackageVersion(stableDirectory, stableVersion)
    val stableIntegrity = readPackIntegrity(stableDirectory)
    val remoteIntegrity = readRemoteIntegrity(stableDirectory, runtimeName, stableVersion)
    if (remoteIntegrity == stableIntegrity) {
      logger.lifecycle("Reuse registry npm Runtime {}@{}.", runtimeName, stableVersion)
      return RuntimeResolution(
        version = stableVersion,
        archive = pack(stableDirectory, outputRoot.resolve("runtime-tarball")),
        changed = false,
      )
    }

    findReusableLocalDebugArchive(
      packageName = runtimeName,
      stableVersion = stableVersion,
      candidateDirectory = stableDirectory,
    )?.let { existing ->
      logger.lifecycle("Reuse local debug npm Runtime {}@{}.", runtimeName, existing.version)
      return RuntimeResolution(existing.version, existing.archive, changed = true)
    }

    val debugVersion = debugVersionAfter(stableVersion, buildTimestamp)
    val debugDirectory = copyPackage(runtimeSource, outputRoot.resolve("runtime-debug"))
    updatePackageVersion(debugDirectory, debugVersion)
    val archive = pack(debugDirectory, outputRoot.resolve("runtime-tarball"))
    return RuntimeResolution(debugVersion, archive, changed = true)
  }

  /**
   * 先以远端稳定版本和最终 Runtime 坐标计算 SRI；一致时不生成，存在代码或依赖变化时才加时间戳。
   */
  private fun resolveBusinessPackage(
    source: LocalPackageSource,
    dependencyVersions: Map<String, String>,
    runtimeVersion: String?,
    buildTimestamp: String,
    outputRoot: File,
    outputIndex: Int,
  ): BusinessPackageResolution {
    val directory = copyPackage(
      source.directory,
      outputRoot.resolve("business-$outputIndex-candidate"),
    )
    val packageJsonFile = directory.resolve(PACKAGE_JSON)
    val packageJson = readPackageJson(packageJsonFile)
    packageJson.addProperty("version", source.stableVersion)
    val dependencies = packageJson.getAsJsonObject("dependencies") ?: JsonObject()
    val runtimeName = runtimePackageName.orNull
    if (runtimeName != null && runtimeVersion != null && dependencies.has(runtimeName)) {
      dependencies.addProperty(runtimeName, runtimeVersion)
    }
    dependencyVersions.forEach { (packageName, version) ->
      dependencies.addProperty(packageName, version)
    }
    packageJson.add("dependencies", dependencies)
    writePackageJson(packageJsonFile, packageJson)
    val localStableIntegrity = readPackIntegrity(directory)
    val remoteStableIntegrity = readRemoteIntegrity(
      directory,
      source.name,
      source.stableVersion,
    )
    if (remoteStableIntegrity == localStableIntegrity) {
      logger.lifecycle(
        "Reuse registry npm business package {}@{}.",
        source.name,
        source.stableVersion,
      )
      return BusinessPackageResolution(
        name = source.name,
        version = source.stableVersion,
        archive = pack(directory, outputRoot.resolve("business-$outputIndex-tarball")),
        changed = false,
      )
    }

    findReusableLocalDebugArchive(
      packageName = source.name,
      stableVersion = source.stableVersion,
      candidateDirectory = directory,
    )?.let { existing ->
      logger.lifecycle("Reuse local debug npm business package {}@{}.", source.name, existing.version)
      return BusinessPackageResolution(
        name = source.name,
        version = existing.version,
        archive = existing.archive,
        changed = true,
      )
    }

    val debugVersion = debugVersionAfter(source.stableVersion, buildTimestamp)
    packageJson.addProperty("version", debugVersion)
    writePackageJson(packageJsonFile, packageJson)
    return BusinessPackageResolution(
      name = source.name,
      version = debugVersion,
      archive = pack(directory, outputRoot.resolve("business-$outputIndex-tarball")),
      changed = true,
    )
  }

  /**
   * 读取入口及构建内其他可发布包，按 npm 包名建立本地源码池。
   *
   * 同名包指向不同目录时立即失败，避免调试注入选择到不确定的构建产物。Runtime 由独立流程
   * 处理，因此不会作为普通业务依赖再次遍历。
   */
  private fun readLocalPackageSources(entryDirectory: File): Map<String, LocalPackageSource> {
    val result = linkedMapOf<String, LocalPackageSource>()
    (localPackageDirectories.files + entryDirectory)
      .map(File::getCanonicalFile)
      .distinct()
      .forEach { directory ->
        val packageJson = readPackageJson(directory.resolve(PACKAGE_JSON))
        val name = packageJson.string("name")
          ?: throw GradleException("Prepared npm package '$directory' has no name.")
        if (name == runtimePackageName.orNull) return@forEach
        val version = packageJson.string("version")
          ?: throw GradleException("Prepared npm package '$name' has no version.")
        val dependencies = packageJson.getAsJsonObject("dependencies")
          ?.keySet()
          ?.toSet()
          .orEmpty()
        val source = LocalPackageSource(name, version, directory, dependencies)
        val previous = result.put(name, source)
        if (previous != null && previous.directory != directory) {
          throw GradleException(
            "npm package '$name' has multiple local prepare directories: " +
                "'${previous.directory}' and '$directory'.",
          )
        }
      }
    return result
  }

  /** 返回本地项目依赖优先、入口最后的稳定拓扑；循环依赖会阻止生成不确定的 debug 坐标。 */
  private fun resolveLocalPackageOrder(
    packages: Map<String, LocalPackageSource>,
    entryName: String,
  ): List<LocalPackageSource> {
    if (entryName !in packages) {
      throw GradleException("Entry npm package '$entryName' is absent from the local package pool.")
    }
    val result = mutableListOf<LocalPackageSource>()
    val resolved = mutableSetOf<String>()
    val resolving = linkedSetOf<String>()
    fun visit(packageName: String) {
      if (packageName in resolved) return
      if (!resolving.add(packageName)) {
        val cycle = (resolving.dropWhile { it != packageName } + packageName).joinToString(" -> ")
        throw GradleException("Local npm project dependency cycle detected: $cycle")
      }
      val source = packages.getValue(packageName)
      source.dependencies
        .asSequence()
        .filter(packages::containsKey)
        .sorted()
        .forEach(::visit)
      resolving.remove(packageName)
      resolved.add(packageName)
      result.add(source)
    }
    visit(entryName)
    return result
  }

  /** 读取 prepare 目录对应的 npm 包名，供依赖拓扑确定入口。 */
  private fun readPackageName(directory: File): String {
    return readPackageJson(directory.resolve(PACKAGE_JSON)).string("name")
      ?: throw GradleException("Prepared npm entry package has no name.")
  }

  /** 把固定版本改为下一补丁版本的 debug 预发布，保证未来同版本正式包仍高于它。 */
  private fun debugVersionAfter(stableVersion: String, timestamp: String): String {
    val match = STABLE_VERSION.matchEntire(stableVersion)
      ?: throw GradleException("npm debug bundle requires a stable x.y.z version: '$stableVersion'.")
    val major = match.groupValues[1]
    val minor = match.groupValues[2]
    val patch = match.groupValues[3].toInt() + 1
    return "$major.$minor.$patch-debug.$timestamp"
  }

  /**
   * 将当前候选改为本地最新 debug 版本后重新计算 npm pack SRI；归档字节完全一致时复用旧坐标。
   *
   * 候选最初使用稳定版本完成 Registry 比较，而 npm 归档的 package.json 包含自身版本，不能把稳定
   * 候选 SRI 直接与 debug tgz 比较。这里临时写入现有 debug 版本后再 dry-run，既排除了时间戳差异，
   * 又保留源码、文件列表、Runtime 以及全部精确依赖版本的变化。只比较最新版本符合本地调试的线性
   * 演进模型；代码回退到更早状态时会生成新坐标，不会意外复活旧依赖图。
   *
   * @return 内容一致的最新本地归档；不存在或内容不同则返回 null。
   */
  private fun findReusableLocalDebugArchive(
    packageName: String,
    stableVersion: String,
    candidateDirectory: File,
  ): ExistingDebugArchive? {
    val versionPrefix = debugVersionAfter(stableVersion, timestamp = "")
    val packageDirectory = debugSourceDirectory.get().asFile
      .resolve(debugNpmArchiveRelativePath(packageName, stableVersion))
      .parentFile
    val latest = packageDirectory.listFiles { file ->
      file.isFile && file.extension == "tgz" &&
        file.nameWithoutExtension.startsWith(versionPrefix) &&
        DEBUG_TIMESTAMP.matches(file.nameWithoutExtension.removePrefix(versionPrefix))
    }
      ?.maxByOrNull(File::getName)
      ?: return null
    val version = latest.nameWithoutExtension

    updatePackageVersion(candidateDirectory, version)
    val candidateIntegrity = readPackIntegrity(candidateDirectory)
    return if (candidateIntegrity == readArchiveIntegrity(latest)) {
      ExistingDebugArchive(version = version, archive = latest)
    } else {
      null
    }
  }

  /** 计算现有 tgz 的 npm SRI；使用流式摘要避免大包产生额外完整字节副本。 */
  private fun readArchiveIntegrity(archive: File): String {
    return try {
      val digest = MessageDigest.getInstance("SHA-512")
      archive.inputStream().buffered().use { input ->
        val buffer = ByteArray(16 * 1024)
        while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          digest.update(buffer, 0, count)
        }
      }
      "sha512-${Base64.getEncoder().encodeToString(digest.digest())}"
    } catch (throwable: Throwable) {
      throw GradleException("Failed to calculate local debug npm archive integrity.", throwable)
    }
  }

  /** npm dry-run integrity 与真实 pack 使用同一 CLI 算法，可直接和 registry 的 SRI 比较。 */
  private fun readPackIntegrity(directory: File): String {
    val result = execute(npmExecutable.get(), directory, "pack", "--dry-run", "--json")
    requireSuccess("calculate npm package integrity", result)
    return try {
      JsonParser.parseString(result.standardOutput).asJsonArray.first().asJsonObject
        .get("integrity").asString
    } catch (throwable: Throwable) {
      throw GradleException("npm pack returned no valid integrity.", throwable)
    }
  }

  /** 查询精确稳定版本的 SRI；断网、未发布或 Registry 拒绝时均按本地内容存在变化处理。 */
  private fun readRemoteIntegrity(directory: File, packageName: String, version: String): String? {
    val result = execute(
      npmExecutable.get(),
      directory,
      "view",
      "$packageName@$version",
      "dist.integrity",
      "--json",
      "--registry",
      registryUrl.get(),
    )
    if (result.exitCode != 0) {
      logger.info("Cannot reuse registry package {}@{}: {}", packageName, version, result.combinedOutput().trim())
      return null
    }
    return runCatching { JsonParser.parseString(result.standardOutput).asString }.getOrNull()
  }

  /** 执行 npm pack 并要求目标目录只产生一个 tgz。 */
  private fun pack(directory: File, destination: File): File {
    destination.mkdirs()
    val result = execute(
      npmExecutable.get(),
      directory,
      "pack",
      "--pack-destination",
      destination.absolutePath,
    )
    requireSuccess("pack local debug npm package", result)
    return destination.listFiles { file -> file.extension == "tgz" }
      ?.singleOrNull()
      ?: throw GradleException("npm pack did not produce exactly one tgz.")
  }

  /**
   * 将当前入口可达的全部本地包原子写入共享调试源，并返回 Android 安装所需的精确清单。
   *
   * 每个包按精确版本写入独立路径。多个入口即使在不同时间生成共同依赖，也不会互相覆盖；
   * Registry 稳定版本与本地 debug 版本同样可以并存，由 npm metadata 的 semver 规则选择 latest。
   */
  private fun synchronizeDebugSource(
    runtimeResolution: RuntimeResolution?,
    packageResolutions: Collection<BusinessPackageResolution>,
  ): DebugNpmBundleManifest {
    val artifacts = buildList {
      if (runtimeResolution != null) {
        add(
          PackageArtifact(
            name = runtimePackageName.get(),
            version = runtimeResolution.version,
            archive = runtimeResolution.archive,
            changed = runtimeResolution.changed,
          ),
        )
      }
      packageResolutions.forEach { resolution ->
        add(
          PackageArtifact(
            name = resolution.name,
            version = resolution.version,
            archive = resolution.archive,
            changed = resolution.changed,
          ),
        )
      }
    }
    val sourceRoot = debugSourceDirectory.get().asFile.canonicalFile
    sourceRoot.mkdirs()
    val packages = artifacts.map { artifact ->
      val relativePath = debugNpmArchiveRelativePath(artifact.name, artifact.version)
      val destination = sourceRoot.resolve(relativePath).canonicalFile
      requireInsideRoot(sourceRoot, destination, artifact.name)
      copyAtomically(artifact.archive, destination)
      DebugNpmBundlePackage(
        name = artifact.name,
        version = artifact.version,
        relativeArchivePath = relativePath.replace(File.separatorChar, '/'),
        changed = artifact.changed,
      )
    }
    return DebugNpmBundleManifest(
      entryPackage = readPackageName(packageDirectory.get().asFile),
      packages = packages,
    )
  }

  /** 把入口清单写入模块自身 build 目录；清单不与其他入口共享，避免并发覆盖。 */
  private fun writeManifest(manifest: DebugNpmBundleManifest) {
    val target = manifestFile.get().asFile
    target.parentFile.mkdirs()
    val temporary = target.resolveSibling("${target.name}.tmp")
    temporary.writeText(GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n")
    moveAtomically(temporary, target)
  }

  /**
   * 先复制到同目录临时文件再替换，防止 Desktop 在任务执行中读取到半个 tgz。
   *
   * 同一版本已经存在时只允许内容完全一致；不同内容复用相同版本会破坏 npm 的不可变版本语义，
   * 因此直接终止构建，而不是让后执行的入口静默覆盖先前依赖图。
   */
  private fun copyAtomically(source: File, destination: File) {
    destination.parentFile.mkdirs()
    // 多个入口任务可并行写共同依赖；同一 Gradle 进程内串行完成“比较或创建”，避免检查后覆盖竞态。
    synchronized(DEBUG_SOURCE_WRITE_LOCK) {
      if (destination.isFile) {
        if (Files.mismatch(source.toPath(), destination.toPath()) == -1L) return
        throw GradleException(
          "Local debug npm archive version collision at '$destination': contents differ.",
        )
      }
      val temporary = destination.resolveSibling("${destination.name}.tmp")
      try {
        Files.copy(source.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        moveAtomically(temporary, destination)
      } finally {
        Files.deleteIfExists(temporary.toPath())
      }
    }
  }

  /** 在文件系统支持时执行原子移动；跨平台不支持时退化为同目录覆盖。 */
  private fun moveAtomically(source: File, destination: File) {
    try {
      Files.move(
        source.toPath(),
        destination.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  /** 防止异常包名或清单路径逃逸到共享调试源之外。 */
  private fun requireInsideRoot(root: File, destination: File, packageName: String) {
    if (destination.toPath() != root.toPath() && !destination.toPath().startsWith(root.toPath())) {
      throw GradleException("npm package '$packageName' escapes the shared debug source.")
    }
  }

  private fun copyPackage(source: File, destination: File): File {
    fileSystemOperations.copy {
      from(source)
      into(destination)
    }
    return destination
  }

  private fun updatePackageVersion(directory: File, version: String) {
    val file = directory.resolve(PACKAGE_JSON)
    val packageJson = readPackageJson(file)
    packageJson.addProperty("version", version)
    writePackageJson(file, packageJson)
  }

  private fun readPackageJson(file: File): JsonObject {
    if (!file.isFile) throw GradleException("npm package has no package.json.")
    return try {
      JsonParser.parseString(file.readText()).asJsonObject
    } catch (throwable: Throwable) {
      throw GradleException("npm package.json is invalid.", throwable)
    }
  }

  private fun writePackageJson(file: File, packageJson: JsonObject) {
    file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(packageJson) + "\n")
  }

  /** 运行外部命令并完整保留 stdout/stderr，供 integrity 与错误诊断共用。 */
  private fun execute(executable: String, directory: File, vararg arguments: String): CommandResult {
    val standardOutput = ByteArrayOutputStream()
    val errorOutput = ByteArrayOutputStream()
    val result = execOperations.exec {
      workingDir(directory)
      commandLine(listOf(executable) + arguments)
      this.standardOutput = standardOutput
      this.errorOutput = errorOutput
      isIgnoreExitValue = true
    }
    return CommandResult(
      result.exitValue,
      standardOutput.toString(StandardCharsets.UTF_8),
      errorOutput.toString(StandardCharsets.UTF_8),
    )
  }

  private fun requireSuccess(operation: String, result: CommandResult) {
    if (result.exitCode == 0) return
    throw GradleException(
      "Failed to $operation (exit ${result.exitCode}):\n" +
        result.combinedOutput().trim().takeLast(MAX_ERROR_OUTPUT_LENGTH),
    )
  }

  /** Runtime 的最终精确版本与本地归档；[changed] 表示是否偏离 Registry 稳定内容。 */
  private data class RuntimeResolution(
    val version: String,
    val archive: File,
    val changed: Boolean,
  )

  /** 一个可独立发布的本地业务包及其稳定依赖声明。 */
  private data class LocalPackageSource(
    val name: String,
    val stableVersion: String,
    val directory: File,
    val dependencies: Set<String>,
  )

  /** 本次调试解析后的精确版本与归档；[changed] 表示是否需要 debug 预发布坐标。 */
  private data class BusinessPackageResolution(
    val name: String,
    val version: String,
    val archive: File,
    val changed: Boolean,
  )

  /** 写入共享调试源前的内部产物描述。 */
  private data class PackageArtifact(
    val name: String,
    val version: String,
    val archive: File,
    val changed: Boolean,
  )

  /** 已存在于共享调试源、可被新入口依赖图继续复用的精确 debug 归档。 */
  private data class ExistingDebugArchive(
    val version: String,
    val archive: File,
  )

  private data class CommandResult(
    val exitCode: Int,
    val standardOutput: String,
    val errorOutput: String,
  ) {
    fun combinedOutput(): String = "$standardOutput\n$errorOutput"
  }

  private companion object {
    const val PACKAGE_JSON = "package.json"
    const val MAX_ERROR_OUTPUT_LENGTH = 4_000
    val DEBUG_ZONE_ID: ZoneId = ZoneId.of("Asia/Shanghai")
    val DEBUG_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    val DEBUG_TIMESTAMP = Regex("""\d{14}""")
    val STABLE_VERSION = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")
    val DEBUG_SOURCE_WRITE_LOCK = Any()
  }
}

private fun JsonObject.string(name: String): String? {
  return get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
