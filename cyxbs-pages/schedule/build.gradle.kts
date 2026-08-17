import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.w3c.dom.Element

plugins {
  id("manager.lib")
  id("kmp.compose")
}

// Host test 运行时排除设备专用 Android bundled driver，确保加载带 macOS JNI 的 JVM variant。
configurations.matching { it.name == "androidHostTestRuntimeClasspath" }.configureEach {
  exclude(group = "androidx.sqlite", module = "sqlite-bundled-android")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useNavigation() // navigation 跳转
useRoom3() // Room3 KMP 持久化

kotlin {
  android {
    // S24 因果握手只运行 host 单测；显式启用后仍不会安装应用、连接设备或访问真实 Calendar Provider。
    withHostTest {}
  }
  sourceSets {
    if (Multiplatform.enableIOS(project)) {
      // 共用 build logic 未创建 iosTest；仅将纯模拟器测试目录接入实际执行的 iOS target，避免误报未执行的 BUILD SUCCESSFUL。
      val iosSimulatorArm64Test by getting {
        kotlin.srcDir("src/iosTest/kotlin")
      }
    }

    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.course.view)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.ktor.contentNegotiation)
      implementation(libs.ktor.json)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.ktor.client.okhttp)
    }
    // Desktop durable Room owner 以 FileKit filesDir 固定业务数据库位置，不依赖 cwd 或临时目录。
    desktopMain.dependencies {
      implementation(libs.filekit.core)
      implementation(libs.ktor.client.okhttp)
    }
    if (Multiplatform.enableIOS(project)) {
      iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
      }
    }
    val desktopTest by getting {
      // semantic wire 的 JVM parity 测试显式承载共享后端 fixture，不把资源加载能力引入 commonMain。
      kotlin.srcDir("src/desktopTest/kotlin")
      resources.srcDir("src/commonTest/resources")
    }
    val androidHostTest by getting {
      // 复用任务约定的 androidUnitTest 目录，测试只运行 JVM host，不连接设备或真实 Provider。
      kotlin.srcDir("src/androidUnitTest/kotlin")
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        // Android host JVM 必须使用 JVM bundled driver；Android AAR 只携带设备 ABI，无法在 macOS JVM 加载。
        runtimeOnly("androidx.sqlite:sqlite-bundled-jvm:2.7.0")
      }
    }
  }
}

val scheduleAndroidDeviceTestApplicationId = "com.cyxbs.pages.schedule.test"
val scheduleAndroidDeviceTestRunner = "androidx.test.runner.AndroidJUnitRunner"

// 将 Android Studio 常用 local.properties 与标准 SDK 环境变量收敛为同一 adb 查找规则。
private val scheduleLocalSdkDirectory = rootProject.file("local.properties")
  .takeIf { it.isFile }
  ?.inputStream()
  ?.use { input ->
    Properties().apply { load(input) }.getProperty("sdk.dir")
  }
private val scheduleSdkDirectory = sequenceOf(
  providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
  providers.environmentVariable("ANDROID_HOME").orNull,
  scheduleLocalSdkDirectory,
).firstOrNull { !it.isNullOrBlank() }
checkNotNull(scheduleSdkDirectory) {
  "未找到 Android SDK，请配置 ANDROID_SDK_ROOT、ANDROID_HOME 或 local.properties 的 sdk.dir"
}
private val scheduleAdbExecutable = rootProject.file("$scheduleSdkDirectory/platform-tools/adb").also {
  check(it.isFile) { "未找到 adb：${it.absolutePath}" }
}

private val scheduleAndroidDeviceSerial = providers.gradleProperty("androidDeviceSerial")
  .orElse(providers.environmentVariable("ANDROID_SERIAL"))
private val scheduleAndroidDeviceTestClass = providers.gradleProperty("androidDeviceTestClass")
private val scheduleGeneratedDeviceTestManifest = layout.buildDirectory.file(
  "intermediates/packaged_manifests/androidDeviceTest/processAndroidDeviceTestManifest/AndroidManifest.xml",
)

/** 校验本次 AGP 生成的 instrumentation component，禁止误跑设备上残留的旧测试 APK。 */
private fun verifyScheduleDeviceTestInstrumentationComponent() {
  val manifestFile = scheduleGeneratedDeviceTestManifest.get().asFile
  check(manifestFile.isFile) {
    "未找到本次 androidDeviceTest manifest：${manifestFile.absolutePath}"
  }
  val manifest = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .parse(manifestFile)
  val generatedPackage = manifest.documentElement.getAttribute("package")
  val instrumentation = manifest.getElementsByTagName("instrumentation")
    .item(0) as? Element
  val generatedRunner = instrumentation?.getAttribute("android:name")
  check(generatedPackage == scheduleAndroidDeviceTestApplicationId) {
    "androidDeviceTest package 与 persistentAndroidDeviceTest 不一致：" +
      "生成=$generatedPackage，任务=$scheduleAndroidDeviceTestApplicationId"
  }
  check(generatedRunner == scheduleAndroidDeviceTestRunner) {
    "androidDeviceTest runner 与 persistentAndroidDeviceTest 不一致：" +
      "生成=$generatedRunner，任务=$scheduleAndroidDeviceTestRunner"
  }
}

private val scheduleInstrumentationArguments = buildList {
  scheduleAndroidDeviceSerial.orNull
    ?.takeIf { it.isNotBlank() }
    ?.let {
      add("-s")
      add(it)
    }
  addAll(listOf("shell", "am", "instrument", "-w"))
  scheduleAndroidDeviceTestClass.orNull
    ?.takeIf { it.isNotBlank() }
    ?.let {
      addAll(listOf("-e", "class", it))
    }
  add("$scheduleAndroidDeviceTestApplicationId/$scheduleAndroidDeviceTestRunner")
}

/**
 * 覆盖安装并运行 Schedule 的 Android instrumentation 测试，结束后保留测试 APK。
 *
 * 此任务依赖 AGP/KMP 的 installAndroidDeviceTest 而非 connectedAndroidDeviceTest，避免后者的
 * 常规清理路径卸载测试 APK。可用 -PandroidDeviceSerial 指定设备，并用 -PandroidDeviceTestClass
 * 限制单个测试类。由于 adb 对 instrumentation 断言失败仍可能返回 shell exit 0，任务会额外校验 runner
 * 的 JUnit 终态，避免把失败结果误报为 Gradle 成功。
 */
tasks.register<Exec>("persistentAndroidDeviceTest") {
  group = "verification"
  description = "覆盖安装并运行 Schedule 真机测试，执行结束后保留测试 APK"
  val instrumentationOutput = ByteArrayOutputStream()
  val instrumentationTerminalFailureSummary = Regex(
    """(?s)FAILURES!!!\s*\RTests run:\s*\d+,\s*Failures:\s*[1-9]\d*\b(?=\s*(?:INSTRUMENTATION_CODE:\s*-?\d+\s*)?\z)""",
  )
  val instrumentationOutputTee = object : OutputStream() {
    override fun write(byte: Int) {
      System.out.write(byte)
      instrumentationOutput.write(byte)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
      System.out.write(bytes, offset, length)
      instrumentationOutput.write(bytes, offset, length)
    }

    override fun flush() {
      System.out.flush()
      instrumentationOutput.flush()
    }
  }
  // 运行前需读取 installAndroidDeviceTest 产出的 manifest，Gradle 配置缓存不能序列化该延迟 XML 校验。
  notCompatibleWithConfigurationCache("验证本次生成的 instrumentation component 后才允许 adb 启动")
  dependsOn("installAndroidDeviceTest")
  doFirst {
    verifyScheduleDeviceTestInstrumentationComponent()
  }
  executable(scheduleAdbExecutable)
  args(scheduleInstrumentationArguments)
  // Android 的 am instrument 在 JUnit 断言失败时仍可能以 0 退出；镜像 stdout 后校验 runner 的终态失败摘要。
  standardOutput = instrumentationOutputTee
  doLast {
    val output = instrumentationOutput.toString(StandardCharsets.UTF_8)
    if (instrumentationTerminalFailureSummary.containsMatchIn(output)) {
      throw GradleException("Android instrumentation 报告测试失败；详见上方 runner 输出")
    }
  }
}
