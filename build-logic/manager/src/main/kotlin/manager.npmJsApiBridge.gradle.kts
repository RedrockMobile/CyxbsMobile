import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import rule.ModuleNamespaceCheckRule

plugins {
  kotlin("multiplatform")
  id("com.android.kotlin.multiplatform.library")
}

/**
 * npm JavaScript 动态实现与端上 Kotlin 调用方共同依赖的跨平台协议桥约定。
 *
 * Bridge 只负责声明稳定接口和 DTO，因此不引入 kmp.base 的完整业务依赖。Serialization、KSP 和
 * 具体 Service 协议由使用模块按需启用，底层 npm bridge 本身不会反向依赖上层实现。
 */
kotlin {
  android {
    namespace = ModuleNamespaceCheckRule.getCorrectNamespace(project)
    compileSdk = libsEx.versions.`android-compileSdk`.toInt()
    minSdk = libsEx.versions.`android-minSdk`.toInt()
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
    }
  }
  if (Multiplatform.enableDesktop(project)) {
    jvm("desktop") {
      compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
      }
    }
  }
  if (Multiplatform.enableIOS(project)) {
    iosArm64()
    iosSimulatorArm64()
  }
  js {
    nodejs()
    binaries.library()
    useEsModules()
    generateTypeScriptDefinitions()
    compilations["main"].packageJson {
      customField("type", "module")
      customField("files", listOf("**/*.mjs", "**/*.d.mts"))
    }
  }
  if (Multiplatform.enableWeb(project)) {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      nodejs()
    }
  }

  sourceSets {
    // 端上代理代码共享同一个 noWebMain，JS/Wasm 只消费 commonMain 中的稳定协议。
    val noWebMain = create("noWebMain") {
      dependsOn(commonMain.get())
    }
    androidMain { dependsOn(noWebMain) }
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting { dependsOn(noWebMain) }
      val desktopTest by getting
      desktopTest.dependencies {
        implementation(kotlin("test"))
      }
    }
    if (Multiplatform.enableIOS(project)) {
      val iosMain = create("iosMain") {
        dependsOn(noWebMain)
      }
      iosArm64Main { dependsOn(iosMain) }
      iosSimulatorArm64Main { dependsOn(iosMain) }
    }
  }
}
