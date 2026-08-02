import java.util.Properties

plugins {
  id("manager.lib")
}

val androidDeviceTestApplicationId = "com.cyxbs.functions.code.js.quickjs.test"
val androidDeviceTestRunner = "androidx.test.runner.AndroidJUnitRunner"

kotlin {
  android {
    // QuickJS 的 Android 产物包含 .so，必须在设备环境中验证动态库加载。
    withDeviceTestBuilder {
      sourceSetTreeName = "test"
    }.configure {
      instrumentationRunner = androidDeviceTestRunner
    }
  }

  sourceSets {
    noWebMain.dependencies {
      api(project(":cyxbs-functions:code:js"))
      implementation(libs.kmp.quickjs)
      implementation(libs.okio)
    }

    val noWebTest = create("noWebTest") {
      dependsOn(commonTest.get())
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
      }
    }
    desktopTest {
      dependsOn(noWebTest)
    }
    iosArm64Test {
      dependsOn(noWebTest)
    }
    iosSimulatorArm64Test {
      dependsOn(noWebTest)
    }
    getByName("androidDeviceTest") {
      dependsOn(noWebTest)
      dependencies {
        implementation(libs.androidx.test.runner)
      }
    }
  }
}

// 优先读取标准环境变量，并兼容 Android Studio 通常写入的 local.properties。
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
).firstOrNull { !it.isNullOrBlank() }
checkNotNull(sdkDirectory) {
  "未找到 Android SDK，请配置 ANDROID_SDK_ROOT、ANDROID_HOME 或 local.properties 的 sdk.dir"
}
val adbExecutable = rootProject.file("$sdkDirectory/platform-tools/adb").also {
  check(it.isFile) { "未找到 adb：${it.absolutePath}" }
}

val androidDeviceSerial = providers.gradleProperty("androidDeviceSerial")
  .orElse(providers.environmentVariable("ANDROID_SERIAL"))
val instrumentationArguments = buildList {
  androidDeviceSerial.orNull
    ?.takeIf { it.isNotBlank() }
    ?.let {
      add("-s")
      add(it)
    }
  addAll(
    listOf(
      "shell",
      "am",
      "instrument",
      "-w",
      "$androidDeviceTestApplicationId/$androidDeviceTestRunner",
    ),
  )
}

/**
 * 覆盖安装并运行 Android 真机测试，测试结束后保留测试 APK。
 *
 * 多设备连接时可通过 `-PandroidDeviceSerial=<serial>` 指定目标设备。
 */
tasks.register<Exec>("persistentAndroidDeviceTest") {
  group = "verification"
  description = "覆盖安装并运行 Android 真机测试，执行结束后保留测试 APK"
  dependsOn("installAndroidDeviceTest")
  executable(adbExecutable)
  args(instrumentationArguments)
}
