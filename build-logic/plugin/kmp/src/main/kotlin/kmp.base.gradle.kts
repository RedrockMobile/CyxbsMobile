import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
  id(libsEx.plugins.kotlinSerialization)
  id(libsEx.plugins.kotlinAtomicfu)
}

kotlin {

  compilerOptions {
    optIn.add("kotlin.time.ExperimentalTime") // 抑制 ExperimentalTime 的警告
  }

  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
    }
  }
  if (Multiplatform.enableDesktop(project)) {
    jvm("desktop")
  }
  if (Multiplatform.enableIOS(project)) {
    iosArm64()
    iosSimulatorArm64()
  }
  if (Multiplatform.enableWeb(project)) {
    js {
      browser {
        commonWebpackConfig {
          outputFileName = "${Config.getBaseName(project)}.js"
        }
      }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      browser {
        commonWebpackConfig {
          outputFileName = "${Config.getBaseName(project)}.js"
        }
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libsEx.`kotlinx-coroutines`)
      implementation(libsEx.`kotlinx-collections`)
      implementation(libsEx.`kotlinx-serialization`)
      implementation(libsEx.`kotlinx-datetime`)
      implementation(libsEx.`kmp-uri`)
      implementation(libsEx.`kmp-settings-core`)
      implementation(libsEx.`kmp-settings-serialization`)
      // atomicfu 显示依赖解决 strictly 0.23.2 问题 https://github.com/Kotlin/kotlinx-atomicfu/issues/469
      implementation(libsEx.`kotlinx-atomicfu`)
    }
    androidMain.dependencies {
      implementation(libsEx.`kotlinx-coroutines-android`)
      implementation(libsEx.`androidx-appcompat`)
    }
    // 桌面端
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting
      desktopMain.dependencies {
        implementation(libsEx.`kotlinx-coroutines-swing`)
      }
    }
    // iOS 端
    if (Multiplatform.enableIOS(project)) {
      val iosMain = create("iosMain") {
        dependsOn(commonMain.get())
      }
      iosArm64Main { dependsOn(iosMain) }
      iosSimulatorArm64Main { dependsOn(iosMain) }
    }

    // 移动端，建议 mobileMain 里面只放 UI
    // mobileMain 目前只用来兼容竖屏的手机端，如果后续需要全尺寸的话，放到 commonMain 即可
    val mobileMain = create("mobileMain") {
      dependsOn(commonMain.get())
    }
    androidMain { dependsOn(mobileMain) }
    if (Multiplatform.enableIOS(project)) {
      val iosMain by getting {
        dependsOn(mobileMain)
      }
    }

    // web 端配置
    if (Multiplatform.enableWeb(project)) {
      val webMain = create("webMain") {
        dependsOn(commonMain.get())
      }
      jsMain { dependsOn(webMain) }
      wasmJsMain { dependsOn(webMain) }
    }

    // noWeb 端配置
    val noWebMain = create("noWebMain") {
      dependsOn(commonMain.get())
    }
    androidMain { dependsOn(noWebMain) }
    if (Multiplatform.enableIOS(project)) {
      iosMain { dependsOn(noWebMain) }
    }
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting {
        dependsOn(noWebMain)
      }
    }

    // noMobile 端配置
    val noMobileMain = create("noMobileMain") {
      dependsOn(commonMain.get())
    }
    if (Multiplatform.enableWeb(project)) {
      jsMain { dependsOn(noMobileMain) }
      wasmJsMain { dependsOn(noMobileMain) }
    }
    if(Multiplatform.enableDesktop(project)) {
      val desktopMain by getting {
        dependsOn(noMobileMain)
      }
    }

    // 单独为 jb Compose 添加一个源集，区分安卓的 jetpack Compose
    val jbComposeMain = create("jbComposeMain") {
      dependsOn(commonMain.get())
    }
    if (Multiplatform.enableIOS(project)) {
      val iosMain by getting {
        dependsOn(jbComposeMain)
      }
    }
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting {
        dependsOn(jbComposeMain)
      }
    }
    if (Multiplatform.enableWeb(project)) {
      val webMain by getting {
        dependsOn(jbComposeMain)
      }
    }
  }
}