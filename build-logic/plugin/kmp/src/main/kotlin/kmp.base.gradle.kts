import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  kotlin("multiplatform")
  id(libsEx.plugins.kotlinSerialization)
  id(libsEx.plugins.kotlinAtomicfu)
}

kotlin {
  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(libsEx.versions.kotlinJvmTarget))
    }
  }
  if (Multiplatform.enableDesktop(project)) {
    jvm("desktop")
    jvmToolchain(libsEx.versions.kotlinJvmTarget.toInt())
  }
  if (Multiplatform.enableIOS(project)) {
    listOf(
      iosX64(),
      iosArm64(),
      iosSimulatorArm64()
    ).forEach { iosTarget ->
      iosTarget.binaries.framework {
        baseName = Config.getBaseName(project)
        isStatic = true
      }
    }
  }
  if (Multiplatform.enableWasm(project)) {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      outputModuleName.set(Config.getBaseName(project))
      browser {
        val rootDirPath = project.rootDir.path
        val projectDirPath = project.projectDir.path
        commonWebpackConfig {
          outputFileName = "${Config.getBaseName(project)}.js"
          devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
            static = (static ?: mutableListOf()).apply {
              // Serve sources to debug inside browser
              add(rootDirPath)
              add(projectDirPath)
            }
          }
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
    }
    androidMain.dependencies {
      implementation(libsEx.`kotlinx-coroutines-android`)
      implementation(libsEx.`androidx-appcompat`)
    }
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting
      desktopMain.dependencies {
        implementation(libsEx.`kotlinx-coroutines-swing`)
      }
    }
    if (Multiplatform.enableIOS(project)) {
      val iosMain = create("iosMain") {
        dependsOn(commonMain.get())
      }
      iosX64Main { dependsOn(iosMain) }
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
    if (Multiplatform.enableWasm(project)) {
      val wasmJsMain by getting {
        dependsOn(jbComposeMain)
      }
    }
  }
}