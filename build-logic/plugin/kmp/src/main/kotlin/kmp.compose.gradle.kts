plugins {
  id("kmp.base")
  id(libsEx.plugins.kotlinCompose)
  id(libsEx.plugins.composeMultiplatform)
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libsEx.`compose-runtime`)
      implementation(libsEx.`compose-foundation`)
      implementation(libsEx.`compose-material`)
      implementation(libsEx.`compose-ui`)
      implementation(libsEx.`compose-resources`)
      implementation(libsEx.`compose-preview`)
      implementation(libsEx.`compose-material-icons`)
      implementation(libsEx.`compose-navigation`)
      implementation(libsEx.`compose-lifecycle-runtime-compose`)
      implementation(libsEx.`compose-lifecycle-viewmodel-compose`)
      implementation(libsEx.`compose-savedstate`)
      implementation(libsEx.`compose-constraintLayout`)
    }

    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting
      desktopMain.dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

plugins.withId("com.android.kotlin.multiplatform.library") {
  dependencies {
    add("androidRuntimeClasspath", libsEx.`compose-ui-tooling`)
  }
  configurations.getByName("androidMainImplementation") {
    // 目前第三方的 constraintlayout 在安卓上的实现与 constraintlayout-core 存在依赖冲突
    // 所以这里 exclude 掉对应的 -android 依赖，然后下面在安卓上单独依赖官方的 constraintlayout-compose
    exclude(group = "tech.annexflow.compose", module = "constraintlayout-compose-multiplatform-android")
  }
  kotlin {
    sourceSets {
      androidMain.dependencies {
        implementation(libsEx.`compose-activity`)
        implementation(libsEx.`compose-constraintLayout-android`)
      }
    }
  }
}

composeCompiler {
  // https://developer.android.com/jetpack/compose/performance/stability/diagnose#compose-compiler
  reportsDestination.set(
    layout.buildDirectory.get().asFile.resolve("compose_compiler")
  )

  // 对 Compose 配置外部类的稳定性
  // 只允许配置已有第三方库里面的类，如果是自己的类请打上 @Stable 注解
  // 配置规则可以查看 https://android-review.googlesource.com/c/platform/frameworks/support/+/2668595
  // 开启强跳过模式后可以不再设置外部类稳定性
//  stabilityConfigurationFile.set(
//    rootDir.resolve("config").resolve("compose-stability-config.txt")
//  )

  featureFlags.set(
    listOf(
      // 强跳过模式
      // https://developer.android.com/develop/ui/compose/performance/stability/strongskipping?hl=zh-cn
//      org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.OptimizeNonSkippingGroups,
    )
  )
}
