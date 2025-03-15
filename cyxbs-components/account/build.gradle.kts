plugins {
  id("manager.lib")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供

kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsPages.login.api)
    }
    androidMain.dependencies {
      implementation(libs.dialog)
    }
  }
}
