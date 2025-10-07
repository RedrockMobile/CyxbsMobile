plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.glide)
      implementation(libs.rxpermissions)
      implementation(libs.okhttp.logging.interceptor)
      implementation(libs.retrofit.converter.gson)
      implementation(libs.retrofit.converter.kotlinxSerialization)
      implementation(libs.retrofit.adapter.rxjava)
    }
  }
}


// 网络请求相关依赖配置
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libs.kmp.ktorfit)
      implementation(libs.ktor.core)
      implementation(libs.ktor.json)
      implementation(libs.ktor.contentNegotiation)
    }
    androidMain.dependencies {
      implementation(libs.ktor.client.okhttp)
    }
    if (Multiplatform.enableDesktop(project)) {
      val desktopMain by getting
      desktopMain.dependencies {
        implementation(libs.ktor.client.okhttp)
      }
    }
    if (Multiplatform.enableIOS(project)) {
      iosMain.dependencies {
        implementation(libs.ktor.client.darwin)
      }
    }
    if (Multiplatform.enableWeb(project)) {
      wasmJsMain.dependencies {
        implementation(libs.ktor.client.js)
      }
    }
  }
}

android {
  buildFeatures {
    buildConfig = true
  }
  defaultConfig {
    // 写入版本信息到 BuildConfig，其他模块可以通过调用 getAppVersionCode() 和 getAppVersionName() 方法获得
    buildConfigField("long", "VERSION_CODE", Config.versionCode.toString())
    buildConfigField("String", "VERSION_NAME", "\"${Config.versionName}\"")
    // 写入版本更新信息到 BuildConfig
    buildConfigField("String", "VERSION_UPDATE_CONTENT", "\"${Config.updateContent.replace("\n", "\\n")}\"")
  }
}