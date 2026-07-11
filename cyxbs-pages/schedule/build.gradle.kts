plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useNavigation() // navigation 跳转

kotlin {
  android {
    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }
  sourceSets {
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
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
    }
    val androidDeviceTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation("androidx.test:core:1.7.0")
        implementation("androidx.test:runner:1.7.0")
        implementation("androidx.test:rules:1.7.0")
        implementation("androidx.test.ext:junit:1.3.0")
      }
    }
  }
}
