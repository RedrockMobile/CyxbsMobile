import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
  id("kmp.compose")
}

if (Multiplatform.enableIOS(project)) {
  kotlin {
    listOf(
      iosArm64(),
      iosSimulatorArm64()
    ).forEach { iosTarget ->
      iosTarget.binaries.framework {
        baseName = Config.getBaseName(project)
        isStatic = true
      }
      // 导出部分项目代码让 iOS 进行引用
      NativeBuildType.DEFAULT_BUILD_TYPES.forEach { type ->
        iosTarget.binaries.getFramework(type).apply {
          // 这里导出会导出很多东西，增加头文件体积，慎重选择导出的模块，能写在 pro iosMain 就尽量不导出
//        export(projects.)
        }
      }
    }
  }
}


if (Multiplatform.enableWeb(project)) {
  kotlin {
    js {
      binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      binaries.executable()
    }
  }
}

if (Multiplatform.enableDesktop(project)) {
  compose.desktop {
    application {
      mainClass = "CyxbsDesktopAppKt"
      nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        packageName = Config.getApplicationId(project)
        packageVersion = Config.composeDesktopVersion
      }
      buildTypes {
        release {
          proguard {
            isEnabled.set(true)
            configurationFiles.from(rootDir.resolve("build-logic")
              .resolve("manager")
              .resolve("proguard-rules.pro"))
          }
        }
      }
    }
  }
}