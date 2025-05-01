plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useRoom(rxjava = true)


kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.login.api)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.notification.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.wheelView)
    }
  }
}

