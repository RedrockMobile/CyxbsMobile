import rule.ModuleNamespaceCheckRule

plugins {
  id("com.android.application")
}

ProjectChecker.config(project) // 项目检查工具

android {
  namespace = ModuleNamespaceCheckRule.getCorrectNamespace(project)
  compileSdk = libsEx.versions.`android-compileSdk`.toInt()
  defaultConfig {
    applicationId = Config.getApplicationId(project)
    minSdk = libsEx.versions.`android-minSdk`.toInt()
    targetSdk = libsEx.versions.`android-targetSdk`.toInt()
    versionCode = Config.versionCode
    versionName = Config.versionName
    // 添加以下两句代码，这是 LPhotoPicker 需要设置的东西
    renderscriptTargetApi = libsEx.versions.`android-targetSdk`.toInt()  //版本号请与compileSdkVersion保持一致
    renderscriptSupportModeEnabled = true
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        rootDir.resolve("build-logic")
          .resolve("manager")
          .resolve("proguard-rules.pro")
      )

      ndk {
        abiFilters += Config.releaseAbiFilters
      }
    }
    debug {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        rootDir.resolve("build-logic")
          .resolve("manager")
          .resolve("proguard-rules.pro")
      )

      ndk {
        abiFilters += Config.debugAbiFilters
      }
    }
  }
  compileOptions {
    val javaVersion = libsEx.versions.javaTarget
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
  }
  lint {
    abortOnError = false // 编译遇到错误不退出，可以一次检查多个错误，并且已执行的 task 下次执行会直接走缓存
  }
  // 命名规范设置，因为多模块相同资源名在打包时会合并，所以必须强制开启
  val paths = project.path.split(":").drop(1)
  if (paths.size == 1) {
    resourcePrefix = project.name.substringAfter("_")
  } else if (paths.first().contains("cyxbs-")) {
    resourcePrefix = project.name
  } else {
    resourcePrefix = paths[paths.size - 2] + "_" + paths.last()
  }
  packaging {
    jniLibs.excludes += Config.jniExclude
    resources.excludes += Config.resourcesExclude
  }
  buildFeatures {
    buildConfig = true
  }
}
