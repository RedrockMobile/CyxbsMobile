package npm

import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/**
 * 将 [PrepareNpmJsPackageTask] 生成的发布目录打包为 npm tgz。
 *
 * 每次执行会先清理当前模块自己的 tarball 输出目录，避免旧版本文件与本次产物混在一起。
 * 任务只调用本机 npm CLI 的 `npm pack`，不查询 Registry，也不会产生远端副作用。
 *
 * 对业务模块执行 `packNpmJsPackage` 时，插件会额外添加对 distribution 同名任务的依赖，最终
 * 同时得到共享 Runtime tgz 和当前业务包 tgz；本任务类自身始终只负责打包 [packageDirectory]
 * 指向的一个 npm 包。
 */
@DisableCachingByDefault(because = "npm pack 的外部进程输出由 npm CLI 决定")
abstract class PackNpmJsPackageTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageDirectory: DirectoryProperty

  @get:OutputDirectory
  abstract val destinationDirectory: DirectoryProperty

  @get:Input
  abstract val npmExecutable: Property<String>

  @get:Inject
  abstract val fileSystemOperations: FileSystemOperations

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * 清理旧 tarball，并在 [destinationDirectory] 中生成当前 package.json 对应的 tgz。
   *
   * @throws org.gradle.process.internal.ExecException 当 npm CLI 不可用或 `npm pack` 失败时抛出。
   */
  @TaskAction
  fun pack() {
    val destination = destinationDirectory.get().asFile
    fileSystemOperations.delete {
      delete(destination)
    }
    destination.mkdirs()
    execOperations.exec {
      workingDir(packageDirectory.get().asFile)
      commandLine(
        npmExecutable.get(),
        "pack",
        "--pack-destination",
        destination.absolutePath,
      )
    }.assertNormalExitValue()
  }
}

/**
 * 在发布业务包前确保共享 Kotlin/JS Runtime 已存在于目标 Registry。
 *
 * 相同版本已存在时必须与本地 npm pack integrity 完全一致，否则说明 Runtime 内容
 * 已变化但未提升版本，任务会中止以防止业务包引用错误 ABI。
 *
 * 这是 `publishNpmJsPackage` 的内部前置任务，未加入 npm task group，业务方不需要直接执行。
 * 它会联网执行 `npm view`：目标版本不存在时执行 `npm publish`，存在且完整性一致时复用，
 * 存在但完整性不一致时失败并要求提升 Runtime 版本。因此本任务可能修改远端 Registry。
 */
@DisableCachingByDefault(because = "任务需要查询并可能修改远程 npm Registry")
abstract class EnsureNpmJsRuntimePublishedTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val runtimePackageDirectory: DirectoryProperty

  @get:Input
  abstract val runtimePackageName: Property<String>

  @get:Input
  abstract val runtimePackageVersion: Property<String>

  @get:Input
  abstract val npmExecutable: Property<String>

  @get:Input
  abstract val registryUrl: Property<String>

  @get:Input
  abstract val publishTag: Property<String>

  @get:Input
  abstract val publishAccess: Property<String>

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * 校验或发布 Runtime。
   *
   * 精确版本不存在时才执行 publish；已存在且 integrity 一致时直接复用。
   * 任务不会隐藏 npm CLI 异常，调用方可直接根据 Gradle 任务失败中止发布流水线。
   *
   * @throws GradleException 当 Registry 查询失败、同版本内容不一致、npm 输出无法解析或发布失败时
   * 抛出；依赖它的业务包 publish 不会继续执行。
   */
  @TaskAction
  fun ensurePublished() {
    val runtimeDirectory = runtimePackageDirectory.get().asFile
    val localIntegrity = readLocalIntegrity(runtimeDirectory)
    val remoteIntegrity = readRemoteIntegrity(runtimeDirectory)
    when {
      remoteIntegrity == localIntegrity -> {
        logger.lifecycle(
          "Reuse published npm Runtime {}@{} because integrity matches.",
          runtimePackageName.get(),
          runtimePackageVersion.get(),
        )
      }
      remoteIntegrity != null -> {
        throw GradleException(
          "npm Runtime '${runtimeCoordinate()}' already exists with different integrity. " +
              "Bump npmJsPackage.runtimePackageVersion before publishing the business package.",
        )
      }
      else -> {
        val result = executeNpm(
          workingDirectory = runtimeDirectory,
          "publish",
          "--registry",
          registryUrl.get(),
          "--tag",
          publishTag.get(),
          "--access",
          publishAccess.get(),
        )
        requireSuccess("publish npm Runtime", result)
        logger.lifecycle("Published missing npm Runtime {}.", runtimeCoordinate())
      }
    }
  }

  /** 通过 npm pack 的 dry-run 结果获取与真实上传 tarball 一致的本地 integrity。 */
  private fun readLocalIntegrity(runtimeDirectory: File): String {
    val result = executeNpm(runtimeDirectory, "pack", "--dry-run", "--json")
    requireSuccess("calculate local npm Runtime integrity", result)
    return try {
      JsonParser.parseString(result.standardOutput)
        .asJsonArray
        .first()
        .asJsonObject
        .get("integrity")
        .asString
    } catch (throwable: Throwable) {
      throw GradleException("npm pack returned no valid Runtime integrity.", throwable)
    }
  }

  /** 查询远程精确版本；404 表示未发布，其他 npm 失败不得降级为重新发布。 */
  private fun readRemoteIntegrity(runtimeDirectory: File): String? {
    val result = executeNpm(
      workingDirectory = runtimeDirectory,
      "view",
      runtimeCoordinate(),
      "dist.integrity",
      "--json",
      "--registry",
      registryUrl.get(),
    )
    if (result.exitCode == 0) {
      return try {
        JsonParser.parseString(result.standardOutput).asString
      } catch (throwable: Throwable) {
        throw GradleException("npm view returned no valid Runtime integrity.", throwable)
      }
    }
    val error = result.combinedOutput()
    if (error.contains("E404") || error.contains("404 Not Found", ignoreCase = true)) {
      return null
    }
    throw commandFailure("query npm Runtime", result)
  }

  /** 使用同一 npm CLI 与用户认证环境执行命令，并分别保留标准输出和错误输出。 */
  private fun executeNpm(
    workingDirectory: File,
    vararg arguments: String,
  ): NpmCommandResult {
    val standardOutput = ByteArrayOutputStream()
    val errorOutput = ByteArrayOutputStream()
    val result = execOperations.exec {
      workingDir(workingDirectory)
      commandLine(listOf(npmExecutable.get()) + arguments)
      this.standardOutput = standardOutput
      this.errorOutput = errorOutput
      isIgnoreExitValue = true
    }
    return NpmCommandResult(
      exitCode = result.exitValue,
      standardOutput = standardOutput.toString(StandardCharsets.UTF_8),
      errorOutput = errorOutput.toString(StandardCharsets.UTF_8),
    )
  }

  /** 将 npm 失败转换为包含 CLI 输出的 Gradle 错误，便于 CI 直接诊断。 */
  private fun requireSuccess(operation: String, result: NpmCommandResult) {
    if (result.exitCode != 0) throw commandFailure(operation, result)
  }

  private fun commandFailure(operation: String, result: NpmCommandResult): GradleException {
    return GradleException(
      "Failed to $operation (npm exit ${result.exitCode}):\n" +
          result.combinedOutput().trim().takeLast(MAX_ERROR_OUTPUT_LENGTH),
    )
  }

  private fun runtimeCoordinate(): String {
    return "${runtimePackageName.get()}@${runtimePackageVersion.get()}"
  }

  /** npm 命令的最小输出模型，避免判断 404 时丢失 stderr。 */
  private data class NpmCommandResult(
    val exitCode: Int,
    val standardOutput: String,
    val errorOutput: String,
  ) {
    fun combinedOutput(): String = "$standardOutput\n$errorOutput"
  }

  private companion object {
    const val MAX_ERROR_OUTPUT_LENGTH = 4_000
  }
}

/**
 * 将 [PrepareNpmJsPackageTask] 生成的发布目录上传到指定 npm Registry。
 *
 * 该任务会产生远端副作用且不会参与普通 build；调用方应在执行前通过 npm CLI 完成身份认证。
 * 本任务类一次只发布 [packageDirectory] 指向的当前包。业务模块表现出的“先检查/发布 Runtime，
 * 再发布业务包”由 [NpmJsPackageExtension] 注册任务时建立的 Gradle 依赖保证，不是在本类内部
 * 隐式发布两个包。
 */
@DisableCachingByDefault(because = "npm publish 会修改远端 Registry")
abstract class PublishNpmJsPackageTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val packageDirectory: DirectoryProperty

  @get:Input
  abstract val npmExecutable: Property<String>

  @get:Input
  abstract val registryUrl: Property<String>

  @get:Input
  abstract val publishTag: Property<String>

  @get:Input
  abstract val publishAccess: Property<String>

  @get:Inject
  abstract val execOperations: ExecOperations

  /**
   * 发布当前 npm 包。
   *
   * Registry、dist-tag 与 access 由 [NpmJsPackageExtension] 统一配置；认证失败和版本已存在等错误
   * 直接保留 npm CLI 的失败结果，防止流水线误判为发布成功。
   *
   * @throws org.gradle.process.internal.ExecException 当 npm CLI 不可用、认证失败、版本已存在或
   * Registry 拒绝发布时抛出。
   */
  @TaskAction
  fun publish() {
    execOperations.exec {
      workingDir(packageDirectory.get().asFile)
      commandLine(
        npmExecutable.get(),
        "publish",
        "--registry",
        registryUrl.get(),
        "--tag",
        publishTag.get(),
        "--access",
        publishAccess.get(),
      )
    }.assertNormalExitValue()
  }
}
