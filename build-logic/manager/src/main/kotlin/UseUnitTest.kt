import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.w3c.dom.Element

/**
 * 为 KMP library 模块启用通用单元测试基建。
 *
 * 默认只引入 `kotlin-test`、协程测试和各平台标准测试源集，不会加入 Mock、Robolectric 或业务测试依赖。
 * Android Host Test 默认保持严格的 Android stub 行为；只有模块确实会调用 `Log` 等无副作用 API 时，才应显式
 * 开启 [androidReturnDefaultValues]。使用 Room3 的模块通过 [withRoom3] 切换到 JVM SQLite driver，避免
 * macOS Host JVM 错误加载仅包含设备 ABI 的 Android driver。
 *
 * iOS 使用共享的 `src/iosTest`，并同时接入 `iosArm64Test` 与 `iosSimulatorArm64Test`，防止测试源码只在
 * 单一 target 中编译而产生误判。
 *
 * @param androidReturnDefaultValues Android 框架 stub 是否返回默认值，而不是抛出未实现异常。
 * @param withRoom3 是否配置 Room3 Android Host Test 所需的 JVM SQLite driver。
 * @param withKtorFixture 是否为共享测试注入 Ktor JSON Client 依赖，供网络协议 fixture 与 Fake Gateway 使用。
 * @param withPersistentAndroidDeviceTest 是否注册执行后保留测试 APK 的真机测试任务，防止每次安装测试包都需要审批。
 */
fun Project.useUnitTest(
  androidReturnDefaultValues: Boolean = false,
  withRoom3: Boolean = false,
  withKtorFixture: Boolean = false,
  withPersistentAndroidDeviceTest: Boolean = false,
) {
  enableCommonUnitTestDependencies()
  enableAndroidHostTest(androidReturnDefaultValues)
  enableIosUnitTestSourceSet()
  if (withRoom3) enableRoom3HostTestDriver()
  if (withKtorFixture) enableKtorFixtureDependencies()
  if (withPersistentAndroidDeviceTest) enablePersistentAndroidDeviceTest()
}

/** 开启各平台共享的基础测试依赖。 */
private fun Project.enableCommonUnitTestDependencies() {
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("commonTest").dependencies {
      implementation(kotlin("test"))
      implementation(libsEx.`kotlinx-coroutines-test`)
    }
  }
}

/** 开启 Android Host Test，并独立控制 Android stub 的默认值行为。 */
private fun Project.enableAndroidHostTest(returnDefaultValues: Boolean) {
  extensions.configure<KotlinMultiplatformExtension> {
    targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
      withHostTest {
        isReturnDefaultValues = returnDefaultValues
      }
    }
  }
}

/** 开启 Room3 Android Host Test 所需的 JVM SQLite driver 替换。 */
private fun Project.enableRoom3HostTestDriver() {
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("androidHostTest").dependencies {
      runtimeOnly(libsEx.`androidx-sqlite-bundled-jvm`)
    }
  }

  // Room3 的 Android bundled driver 只包含设备 ABI，不能被 macOS 上运行的 Host JVM 加载。
  configurations.matching { it.name == "androidHostTestRuntimeClasspath" }.configureEach {
    exclude(
      mapOf(
        "group" to "androidx.sqlite",
        "module" to "sqlite-bundled-android",
      ),
    )
  }
}

/** 开启网络协议 fixture 与 Fake Gateway 所需的 Ktor JSON 测试依赖。 */
private fun Project.enableKtorFixtureDependencies() {
  extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("commonTest").dependencies {
      implementation(libsEx.`ktor-contentNegotiation`)
      implementation(libsEx.`ktor-json`)
    }
  }
}

/** 开启 iOS 真机与模拟器共用的 `iosTest` 测试源集。 */
private fun Project.enableIosUnitTestSourceSet() {
  if (Multiplatform.enableIOS(project)) {
    extensions.configure<KotlinMultiplatformExtension> {
      val iosTest = sourceSets.maybeCreate("iosTest").apply {
        dependsOn(sourceSets.getByName("commonTest"))
      }
      sourceSets.getByName("iosArm64Test").dependsOn(iosTest)
      sourceSets.getByName("iosSimulatorArm64Test").dependsOn(iosTest)
    }
  }
}

/**
 * 注册覆盖安装后保留测试 APK 的 Android instrumentation 测试任务。
 *
 * 任务依赖 AGP/KMP 生成的 `installAndroidDeviceTest`，再从本次构建产出的 Manifest 读取测试包名和
 * instrumentation runner，因此不会绑定具体业务模块，也不会误用设备上残留测试包的 component。
 * 可通过 `-PandroidDeviceSerial` 或 `ANDROID_SERIAL` 指定设备，通过 `-PandroidDeviceTestClass` 仅运行
 * 某个测试类。任务结束后不卸载测试 APK，方便继续进行真机调试和数据检查。
 *
 * Android 的 `am instrument` 在 JUnit 断言失败时仍可能返回 shell exit 0；任务会将完整输出写入
 * `build/test-results/persistentAndroidDeviceTest/instrumentation.txt`，执行后回显并检查 runner 终态，
 * 避免将失败用例误报为成功。
 */
private fun Project.enablePersistentAndroidDeviceTest() {
  val taskName = "persistentAndroidDeviceTest"
  // 配置阶段只计算候选路径，存在性校验留到执行阶段，避免本机没配 Android SDK 时 sync 直接失败。
  val adbCandidates = findAndroidAdbCandidates()
  val deviceSerial = providers.gradleProperty("androidDeviceSerial")
    .orElse(providers.environmentVariable("ANDROID_SERIAL"))
  val deviceTestClass = providers.gradleProperty("androidDeviceTestClass")
  val generatedManifest = layout.buildDirectory.file(
    "intermediates/packaged_manifests/androidDeviceTest/processAndroidDeviceTestManifest/AndroidManifest.xml",
  )
  val instrumentationOutputFile = layout.buildDirectory.file(
    "test-results/$taskName/instrumentation.txt",
  )

  tasks.register<Exec>(taskName) {
    group = "verification"
    description = "覆盖安装并运行 Android 真机测试，执行结束后保留测试 APK"
    dependsOn("installAndroidDeviceTest")

    isIgnoreExitValue = true
    doFirst {
      // 安装任务生成 Manifest 后再解析 component，避免在 Gradle 配置阶段读取尚不存在的产物。
      val component = readInstrumentationComponent(generatedManifest.get().asFile)
      val outputFile = instrumentationOutputFile.get().asFile
      outputFile.parentFile.mkdirs()
      standardOutput = outputFile.outputStream()
      val instrumentationArguments = buildList {
        deviceSerial.orNull?.takeIf { it.isNotBlank() }?.let {
          add("-s")
          add(it)
        }
        addAll(listOf("shell", "am", "instrument", "-w"))
        deviceTestClass.orNull?.takeIf { it.isNotBlank() }?.let {
          addAll(listOf("-e", "class", it))
        }
        add(component)
      }
      executable(adbCandidates.resolveAndroidAdb())
      args(instrumentationArguments)
    }
    doLast {
      standardOutput.flush()
      standardOutput.close()
      val output = instrumentationOutputFile.get().asFile.readText()
      logger.lifecycle(output.trimEnd())
      val terminalFailureSummary = Regex(
        """(?s)FAILURES!!!\s*\RTests run:\s*\d+,\s*Failures:\s*[1-9]\d*\b(?=\s*(?:INSTRUMENTATION_CODE:\s*-?\d+\s*)?\z)""",
      )
      if (executionResult.get().exitValue != 0) {
        throw GradleException("adb instrumentation 执行失败；详见上方 runner 输出")
      }
      if (terminalFailureSummary.containsMatchIn(output)) {
        throw GradleException("Android instrumentation 报告测试失败；详见上方 runner 输出")
      }
    }
  }
}

/**
 * 列出 Android Studio local.properties 或标准环境变量所配置 SDK 下可能的 adb 路径。
 *
 * Windows 的 platform-tools 中可执行文件名为 `adb.exe`，macOS/Linux 为 `adb`。这里不做宿主系统判断，
 * 而是同时列出两种命名，交给 [resolveAndroidAdb] 挑选真正存在的那个，三种系统共用一个实现。
 *
 * 配置阶段只计算路径、不校验文件是否存在，因此未配置 Android SDK 时 sync 同样不会失败。
 */
private fun Project.findAndroidAdbCandidates(): List<java.io.File> {
  val localSdkDirectory = rootProject.file("local.properties")
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { input ->
      Properties().apply { load(input) }.getProperty("sdk.dir")
    }
  val sdkDirectory = sequenceOf(
    providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
    providers.environmentVariable("ANDROID_HOME").orNull,
    localSdkDirectory,
  ).firstOrNull { !it.isNullOrBlank() } ?: return emptyList()
  return listOf("adb.exe", "adb").map {
    rootProject.file("$sdkDirectory/platform-tools/$it")
  }
}

/** 从候选路径中取出实际存在的 adb 可执行文件，都不存在时给出可定位的错误信息。 */
private fun List<java.io.File>.resolveAndroidAdb(): java.io.File = firstOrNull { it.isFile }
  ?: throw GradleException(
    if (isEmpty()) {
      "未找到 Android SDK，请配置 ANDROID_SDK_ROOT、ANDROID_HOME 或 local.properties 的 sdk.dir"
    } else {
      "未找到 adb，已尝试：${joinToString { it.absolutePath }}"
    },
  )

/** 从本次生成的测试 Manifest 返回 adb 可直接使用的 `package/runner` component。 */
private fun readInstrumentationComponent(manifestFile: java.io.File): String {
  check(manifestFile.isFile) {
    "未找到本次 androidDeviceTest manifest：${manifestFile.absolutePath}"
  }
  val manifest = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .parse(manifestFile)
  val packageName = manifest.documentElement.getAttribute("package")
  val instrumentation = manifest.getElementsByTagName("instrumentation").item(0) as? Element
  val runner = instrumentation?.getAttribute("android:name").orEmpty()
  check(packageName.isNotBlank() && runner.isNotBlank()) {
    "androidDeviceTest manifest 缺少 package 或 instrumentation runner：${manifestFile.absolutePath}"
  }
  return "$packageName/$runner"
}
