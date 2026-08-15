package npm

import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/**
 * 把 [PrepareDebugNpmBundleTask] 生成的统一本地 npm 依赖图安装到 Android 调试应用。
 *
 * 本任务不重新打包、不访问 Registry，也不自行解析项目依赖。它会先完整校验入口清单与共享源，
 * 再停止应用，并把清单中的全部 tgz 原子覆盖到 App 私有 debug 目录。稳定包也必须安装，用于在
 * 本地改动被撤销后覆盖设备上残留的旧 debug 包。安装完成后通过 launcher intent 重启应用。
 */
@DisableCachingByDefault(because = "任务会修改已连接 Android 设备的 App 私有目录")
abstract class InstallAndroidDebugNpmBundleTask : DefaultTask() {

  /** 当前入口由准备任务生成的精确包清单。 */
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val manifestFile: RegularFileProperty

  /** 根项目共享调试源；由多个入口共同维护，因此不作为本任务的独占输入目录快照。 */
  @get:Internal
  abstract val debugSourceDirectory: DirectoryProperty

  /** 需要停止、写入并重新启动的 debuggable Android application id。 */
  @get:Input
  abstract val applicationId: Property<String>

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * 校验并安装当前入口清单。
   *
   * @throws GradleException 当清单非法、共享 tgz 缺失、ADB 不可用、应用不支持 run-as 或设备
   * 写入与重启失败时抛出。校验阶段不会停止应用；ADB 阶段失败可能已安装部分包，再次执行即可
   * 按同一清单覆盖恢复。
   */
  @TaskAction
  fun install() {
    val manifest = readManifest()
    val sourceRoot = debugSourceDirectory.get().asFile.canonicalFile
    val archives = manifest.packages.map { packageInfo ->
      validatePackage(packageInfo)
      val archive = sourceRoot.resolve(packageInfo.relativeArchivePath).canonicalFile
      if (!archive.toPath().startsWith(sourceRoot.toPath()) || !archive.isFile) {
        throw GradleException(
          "Local debug npm archive '${packageInfo.relativeArchivePath}' is absent or escapes '$sourceRoot'.",
        )
      }
      packageInfo to archive
    }
    if (archives.isEmpty()) {
      throw GradleException("Local debug npm manifest for '${manifest.entryPackage}' contains no packages.")
    }

    // 只有所有输入均完成校验后才停止 App，避免构建目录问题打断当前调试会话。
    val appId = applicationId.get().also(::validateApplicationId)
    val adb = findAdbExecutable()
    val deviceArguments = selectedDeviceArguments()
    runAdb(adb, deviceArguments, "shell", "am", "force-stop", appId)
    archives.forEach { (packageInfo, archive) ->
      installArchive(adb, deviceArguments, appId, packageInfo.name, archive)
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
      "Installed local debug npm graph for {}; packages={}.",
      manifest.entryPackage,
      manifest.packages.joinToString { packageInfo -> "${packageInfo.name}@${packageInfo.version}" },
    )
  }

  /** 解析准备任务生成的 JSON，同时拒绝缺失关键字段的半写入或手工损坏清单。 */
  private fun readManifest(): DebugNpmBundleManifest {
    val file = manifestFile.get().asFile
    return try {
      val root = JsonParser.parseReader(file.reader()).asJsonObject
      val entryPackage = root.get("entryPackage")?.asString
        ?: throw GradleException("Local debug npm manifest has no entryPackage.")
      val packages = root.getAsJsonArray("packages")?.map { element ->
        val value = element.asJsonObject
        DebugNpmBundlePackage(
          name = value.get("name")?.asString
            ?: throw GradleException("Local debug npm manifest package has no name."),
          version = value.get("version")?.asString
            ?: throw GradleException("Local debug npm manifest package has no version."),
          relativeArchivePath = value.get("relativeArchivePath")?.asString
            ?: throw GradleException("Local debug npm manifest package has no archive path."),
          changed = value.get("changed")?.asBoolean ?: false,
        )
      } ?: throw GradleException("Local debug npm manifest has no packages.")
      DebugNpmBundleManifest(entryPackage, packages)
    } catch (exception: GradleException) {
      throw exception
    } catch (throwable: Throwable) {
      throw GradleException("Local debug npm manifest '$file' is invalid.", throwable)
    }
  }

  /** 包名、版本与固定相对路径必须一致，避免手工清单把任意文件推入应用目录。 */
  private fun validatePackage(packageInfo: DebugNpmBundlePackage) {
    if (!PACKAGE_NAME.matches(packageInfo.name)) {
      throw GradleException("Invalid npm package name '${packageInfo.name}' in debug manifest.")
    }
    if (!LOCAL_VERSION.matches(packageInfo.version)) {
      throw GradleException(
        "Invalid npm package version '${packageInfo.version}' for '${packageInfo.name}'.",
      )
    }
    val expectedPath = archiveRelativePath(packageInfo.name)
    if (packageInfo.relativeArchivePath != expectedPath) {
      throw GradleException(
        "Unexpected debug archive path '${packageInfo.relativeArchivePath}' for '${packageInfo.name}'.",
      )
    }
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
    val destination = "$DEVICE_DEBUG_DIRECTORY/${archiveRelativePath(packageName)}"
    val destinationParent = destination.substringBeforeLast('/')
    runAdb(adb, deviceArguments, "push", archive.absolutePath, temporary)
    try {
      // adb shell 不会保留宿主进程的参数边界，因此不使用 sh -c 拼接脚本，避免路径被拆成多个参数。
      runAdb(
        adb,
        deviceArguments,
        "shell",
        "run-as",
        applicationId,
        "mkdir",
        "-p",
        destinationParent,
      )
      runAdb(
        adb,
        deviceArguments,
        "shell",
        "run-as",
        applicationId,
        "cp",
        temporary,
        "$destination.tmp",
      )
      runAdb(
        adb,
        deviceArguments,
        "shell",
        "run-as",
        applicationId,
        "mv",
        "$destination.tmp",
        destination,
      )
    } finally {
      runAdbIgnoringFailure(adb, deviceArguments, "shell", "rm", "-f", temporary)
    }
  }

  /** npm 包名对应设备与共享源中的唯一固定相对路径。 */
  private fun archiveRelativePath(packageName: String): String {
    val segments = packageName.split('/')
    return if (segments.size == 1) {
      "${segments[0]}.tgz"
    } else {
      "${segments[0]}/${segments[1]}.tgz"
    }
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
        lines.firstOrNull { line -> line.startsWith("sdk.dir=") }
          ?.substringAfter('=')
          ?.replace("\\\\", "\\")
          ?.let(::File)
      }
      ?: throw GradleException("Android SDK was not found; configure ANDROID_SDK_ROOT or sdk.dir.")
    return sdkRoot.resolve("platform-tools/adb").takeIf(File::isFile)
      ?: throw GradleException("adb was not found under '${sdkRoot.absolutePath}'.")
  }

  /** application id 只允许 Android 标准点分标识，避免传给 adb shell 的参数包含控制字符。 */
  private fun validateApplicationId(value: String) {
    if (!APPLICATION_ID.matches(value)) {
      throw GradleException("Invalid Android application id '$value'.")
    }
  }

  private companion object {
    const val DEVICE_DEBUG_DIRECTORY = "cache/cyxbs-code/npm/debug"
    val PACKAGE_NAME = Regex("""(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*""")
    val LOCAL_VERSION = Regex(
      """(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-debug\.\d{14})?""",
    )
    val APPLICATION_ID = Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+""")
  }
}
