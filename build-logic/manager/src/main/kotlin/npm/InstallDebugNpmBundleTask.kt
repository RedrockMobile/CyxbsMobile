package npm

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
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
 * 生成当前业务包的本地 debug npm bundle，并通过 ADB 注入 App 私有 debug 目录。
 *
 * ```text
 * prepare 业务包 + Runtime
 *          │
 *          ├── Runtime 与 registry 稳定版一致 ──> 复用稳定坐标，不生成 Runtime tgz
 *          └── Runtime 与 registry 稳定版不同 ──> 生成同时间戳的新 Runtime tgz
 *                              │
 *                              ▼
 *        业务包写入最终 Runtime 精确坐标，再与 registry 稳定版比较 SRI
 *                    │                            │
 *                    └── 不同：生成 debug tgz       └──  一致：不生成
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
 * 的业务包与 Runtime 共用一个时间戳。变化检测始终使用稳定版本号计算候选 tgz 的 SRI，时间戳只在
 * 已确认内容或依赖坐标变化后写入。设备旧源不参与检测，有变化时直接原子覆盖固定路径；进入正常
 * npm 包池后的归档仍遵循包池 14 天可达性 GC。
 */
@DisableCachingByDefault(because = "任务需要读取并修改已连接 Android 设备的 App 私有目录")
abstract class InstallDebugNpmBundleTask : DefaultTask() {

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageDirectory: DirectoryProperty

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val runtimePackageDirectory: DirectoryProperty

  @get:Input
  abstract val runtimePackageName: Property<String>

  @get:Input
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
    val runtimeSource = runtimePackageDirectory.get().asFile
    val packageSource = packageDirectory.get().asFile
    fileSystemOperations.delete { delete(outputRoot) }
    outputRoot.mkdirs()

    val buildTimestamp = DEBUG_TIME_FORMATTER.format(LocalDateTime.now(DEBUG_ZONE_ID))
    val runtimeResolution = resolveRuntime(
      runtimeSource = runtimeSource,
      buildTimestamp = buildTimestamp,
      outputRoot = outputRoot,
    )
    val business = prepareBusinessPackage(
      source = packageSource,
      runtimeVersion = runtimeResolution.version,
      buildTimestamp = buildTimestamp,
      outputRoot = outputRoot,
    )

    if (runtimeResolution.archive == null && business == null) {
      logger.lifecycle("Local npm package and Runtime match their registry stable versions; skip ADB.")
      return
    }

    // App 只在所有 npm pack 成功后停止，避免构建错误打断当前调试会话。
    val appId = applicationId.get().also(::validateApplicationId)
    val adb = findAdbExecutable()
    val deviceArguments = selectedDeviceArguments()
    runAdb(adb, deviceArguments, "shell", "am", "force-stop", appId)
    runtimeResolution.archive?.let { archive ->
      installArchive(adb, deviceArguments, appId, runtimePackageName.get(), archive)
    }
    business?.let { packageInfo ->
      installArchive(adb, deviceArguments, appId, packageInfo.name, packageInfo.archive)
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
    logger.lifecycle(
      "Installed changed local debug npm bundle; business={}, Runtime {}@{}.",
      business?.let { "${it.name}@${it.version}" } ?: "unchanged",
      runtimePackageName.get(),
      runtimeResolution.version,
    )
  }

  /** 以稳定版本计算候选 SRI；与 registry 一致则复用稳定坐标，否则生成新 debug Runtime。 */
  private fun resolveRuntime(
    runtimeSource: File,
    buildTimestamp: String,
    outputRoot: File,
  ): RuntimeResolution {
    val stableVersion = runtimeStableVersion.get()
    val stableDirectory = copyPackage(runtimeSource, outputRoot.resolve("runtime-stable"))
    updatePackageVersion(stableDirectory, stableVersion)
    val stableIntegrity = readPackIntegrity(stableDirectory)
    val remoteIntegrity = readRemoteIntegrity(stableDirectory, runtimePackageName.get(), stableVersion)
    if (remoteIntegrity == stableIntegrity) {
      logger.lifecycle("Reuse registry npm Runtime {}@{}.", runtimePackageName.get(), stableVersion)
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
  private fun prepareBusinessPackage(
    source: File,
    runtimeVersion: String,
    buildTimestamp: String,
    outputRoot: File,
  ): BusinessDebugPackage? {
    val directory = copyPackage(source, outputRoot.resolve("business-candidate"))
    val packageJsonFile = directory.resolve(PACKAGE_JSON)
    val packageJson = readPackageJson(packageJsonFile)
    val name = packageJson.string("name")
      ?: throw GradleException("Prepared npm business package has no name.")
    val stableVersion = packageJson.string("version")
      ?: throw GradleException("Prepared npm business package has no version.")
    packageJson.addProperty("version", stableVersion)
    val dependencies = packageJson.getAsJsonObject("dependencies") ?: JsonObject()
    dependencies.addProperty(runtimePackageName.get(), runtimeVersion)
    packageJson.add("dependencies", dependencies)
    writePackageJson(packageJsonFile, packageJson)
    val localStableIntegrity = readPackIntegrity(directory)
    val remoteStableIntegrity = readRemoteIntegrity(directory, name, stableVersion)
    if (remoteStableIntegrity == localStableIntegrity) {
      logger.lifecycle("Reuse registry npm business package {}@{}.", name, stableVersion)
      return null
    }

    val debugVersion = debugVersionAfter(stableVersion, buildTimestamp)
    packageJson.addProperty("version", debugVersion)
    writePackageJson(packageJsonFile, packageJson)
    return BusinessDebugPackage(
      name = name,
      version = debugVersion,
      archive = pack(directory, outputRoot.resolve("business-tarball")),
    )
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

  private data class BusinessDebugPackage(val name: String, val version: String, val archive: File)

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
