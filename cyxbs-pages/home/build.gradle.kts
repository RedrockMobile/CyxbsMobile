plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useNavigation() // navigation 跳转

kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsFunctions.update.api)
      implementation(projects.cyxbsPages.login.api)
      implementation(projects.cyxbsPages.affair.api)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.notification.api)
      implementation(projects.cyxbsPages.map.api)
    }
    noWebMain.dependencies {
      // 仅把手动测试页面和对应 Provider 装入 Android、iOS 与 Desktop。
      implementation(projects.cyxbsFunctions.code.npm.serviceTest)
    }
    androidMain.dependencies {
      implementation(projects.cyxbsPages.course.widget)
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
    }
  }
}
