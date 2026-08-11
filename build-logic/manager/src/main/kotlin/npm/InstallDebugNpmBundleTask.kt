package npm

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 生成入口包及其本地项目依赖的 debug npm bundle，并通过 ADB 注入 App 私有 debug 目录。
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
 *                    ├── 不同：生成 debug tgz
 *                    └── 一致：继续使用稳定坐标
 *                              │
 *                 至少一个包变化后才 force-stop App
 *                              │
 *                              ▼
 *        adb 临时文件 ──run-as 原子替换──> cache/cyxbs-code/npm/debug
 *                              │
 *                              ▼
 *                           重启 App
 * ```
 *
 * debug 版本格式为 `<下一稳定补丁版本>-debug.<yyyyMMddHHmmss>`，时间固定使用上海时区。同次任务
 * 的入口包、依赖包与可选 Runtime 共用一个时间戳。变化检测始终使用稳定版本号和已经解析的下级
 * 精确坐标计算候选 tgz 的 SRI，时间戳只在已确认内容或依赖坐标变化后写入。这样依赖代码变化会先
 * 生成新依赖版本，再自然推动上层包生成引用该版本的新产物。静态 npm 包不配置 Runtime 输入，仍
 * 复用相同的版本比较与 ADB 注入链路。设备旧源不参与检测，有变化时直接原子覆盖固定路径；进入
 * 正常 npm 包池后的归档仍遵循包池 14 天可达性 GC。
 */
@DisableCachingByDefault(because = "任务需要读取并修改已连接 Android 设备的 App 私有目录")
abstract class InstallDebugNpmBundleTask : DefaultTask() {

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

  @get:Input
  abstract val applicationId: Property<String>

  @get:Internal
  abstract val workingDirectory: DirectoryProperty

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * 构建、比较并注入本地 bundle。
   *
   * @throws GradleException 当 npm 产物、ADB、设备身份、run-as 写入或 App 重启失败时抛出；构建
   * 阶段失败不会停止 App，注入阶段失败会保留已经成功构建的本地 tgz 供排查。
   */
  @TaskAction
  fun install() {
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
    val changedPackages = packageResolutions.values.filter { it.archive != null }

    if (runtimeResolution?.archive == null && changedPackages.isEmpty()) {
      logger.lifecycle("Local npm package graph matches registry stable versions; skip ADB.")
      return
    }

    // App 只在所有 npm pack 成功后停止，避免构建错误打断当前调试会话。
    val appId = applicationId.get().also(::validateApplicationId)
    val adb = findAdbExecutable()
    val deviceArguments = selectedDeviceArguments()
    runAdb(adb, deviceArguments, "shell", "am", "force-stop", appId)
    runtimeResolution?.archive?.let { archive ->
      installArchive(adb, deviceArguments, appId, runtimePackageName.get(), archive)
    }
    changedPackages.forEach { packageInfo ->
      installArchive(
        adb,
        deviceArguments,
        appId,
        packageInfo.name,
        checkNotNull(packageInfo.archive),
      )
    }
    runAdb(
      adb,
      deviceArguments,
      "shell",
      "monkey",
      "-c",
      "android.intent.category.LAUNCHER",
      "-p",
      appId,
      "1",
    )
    val runtimeSummary = runtimeResolution?.let { resolution ->
      "${runtimePackageName.get()}@${resolution.version}"
    } ?: "not required"
    logger.lifecycle(
      "Installed changed local debug npm graph; packages={}, Runtime {}.",
      changedPackages.joinToString { "${it.name}@${it.version}" }.ifEmpty { "unchanged" },
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
      return RuntimeResolution(stableVersion, null)
    }

    val debugVersion = debugVersionAfter(stableVersion, buildTimestamp)
    val debugDirectory = copyPackage(runtimeSource, outputRoot.resolve("runtime-debug"))
    updatePackageVersion(debugDirectory, debugVersion)
    val archive = pack(debugDirectory, outputRoot.resolve("runtime-tarball"))
    return RuntimeResolution(debugVersion, archive)
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
      return BusinessPackageResolution(source.name, source.stableVersion, null)
    }

    val debugVersion = debugVersionAfter(source.stableVersion, buildTimestamp)
    packageJson.addProperty("version", debugVersion)
    writePackageJson(packageJsonFile, packageJson)
    return BusinessPackageResolution(
      name = source.name,
      version = debugVersion,
      archive = pack(directory, outputRoot.resolve("business-$outputIndex-tarball")),
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

  /** 先推到设备公共临时目录，再在 run-as 权限内原子替换 App 私有文件。 */
  private fun installArchive(
    adb: File,
    deviceArguments: List<String>,
    applicationId: String,
    packageName: String,
    archive: File,
  ) {
    val safeName = packageName.replace('@', '_').replace('/', '_')
    val temporary = "/data/local/tmp/cyxbs-npm-$safeName-${archive.lastModified()}.tgz"
    val destination = deviceArchivePath(packageName)
    val destinationParent = destination.substringBeforeLast('/')
    runAdb(adb, deviceArguments, "push", archive.absolutePath, temporary)
    try {
      // adb shell 不会保留宿主进程的参数边界，因此不使用 sh -c 拼接脚本，避免路径被拆成多个参数。
      runAdb(adb, deviceArguments, "shell", "run-as", applicationId, "mkdir", "-p", destinationParent)
      runAdb(adb, deviceArguments, "shell", "run-as", applicationId, "cp", temporary, "$destination.tmp")
      runAdb(adb, deviceArguments, "shell", "run-as", applicationId, "mv", "$destination.tmp", destination)
    } finally {
      runAdbIgnoringFailure(adb, deviceArguments, "shell", "rm", "-f", temporary)
    }
  }

  /** App 私有缓存内的固定包路径；同名包每次 ADB 注入只保留一个源 tgz。 */
  private fun deviceArchivePath(packageName: String): String {
    val segments = packageName.split('/')
    if (segments.size !in 1..2 || segments.any { !PACKAGE_SEGMENT.matches(it) }) {
      throw GradleException("Invalid npm package name '$packageName'.")
    }
    val relative = if (segments.size == 1) {
      "${segments[0]}.tgz"
    } else {
      "${segments[0]}/${segments[1]}.tgz"
    }
    return "$DEVICE_DEBUG_DIRECTORY/$relative"
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

  private fun runAdb(adb: File, deviceArguments: List<String>, vararg arguments: String) {
    execOperations.exec {
      commandLine(listOf(adb.absolutePath) + deviceArguments + arguments)
    }.assertNormalExitValue()
  }

  private fun runAdbIgnoringFailure(
    adb: File,
    deviceArguments: List<String>,
    vararg arguments: String,
  ) {
    execOperations.exec {
      commandLine(listOf(adb.absolutePath) + deviceArguments + arguments)
      isIgnoreExitValue = true
    }
  }

  /** `-PandroidDeviceSerial` 优先于 ANDROID_SERIAL，未指定时交由 adb 选择唯一设备。 */
  private fun selectedDeviceArguments(): List<String> {
    val serial = project.providers.gradleProperty("androidDeviceSerial").orNull
      ?: System.getenv("ANDROID_SERIAL")
    return serial?.takeIf(String::isNotBlank)?.let { listOf("-s", it) }.orEmpty()
  }

  /** 按 Android SDK 环境变量或 local.properties 定位 adb。 */
  private fun findAdbExecutable(): File {
    val sdkRoot = sequenceOf(System.getenv("ANDROID_SDK_ROOT"), System.getenv("ANDROID_HOME"))
      .filterNotNull()
      .map(::File)
      .firstOrNull(File::isDirectory)
      ?: project.rootProject.file("local.properties").takeIf(File::isFile)?.useLines { lines ->
        lines.firstOrNull { it.startsWith("sdk.dir=") }
          ?.substringAfter('=')
          ?.replace("\\\\", "\\")
          ?.let(::File)
      }
      ?: throw GradleException("Android SDK was not found; configure ANDROID_SDK_ROOT or sdk.dir.")
    return sdkRoot.resolve("platform-tools/adb").takeIf(File::isFile)
      ?: throw GradleException("adb was not found under '${sdkRoot.absolutePath}'.")
  }

  private fun validateApplicationId(value: String) {
    if (!APPLICATION_ID.matches(value)) {
      throw GradleException("Invalid Android application id '$value'.")
    }
  }

  private data class RuntimeResolution(val version: String, val archive: File?)

  /** 一个可独立发布的本地业务包及其稳定依赖声明。 */
  private data class LocalPackageSource(
    val name: String,
    val stableVersion: String,
    val directory: File,
    val dependencies: Set<String>,
  )

  /** 本次调试解析后的精确版本；[archive] 为空表示继续复用 Registry 稳定包。 */
  private data class BusinessPackageResolution(
    val name: String,
    val version: String,
    val archive: File?,
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
    const val DEVICE_DEBUG_DIRECTORY = "cache/cyxbs-code/npm/debug"
    const val MAX_ERROR_OUTPUT_LENGTH = 4_000
    val DEBUG_ZONE_ID: ZoneId = ZoneId.of("Asia/Shanghai")
    val DEBUG_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    val STABLE_VERSION = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")
    val PACKAGE_SEGMENT = Regex("""@?[a-z0-9][a-z0-9._~-]*""")
    val APPLICATION_ID = Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+""")
  }
}

private fun JsonObject.string(name: String): String? {
  return get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
