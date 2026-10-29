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
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.store.api)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.course.view)
      implementation(projects.cyxbsPages.course.widget)
    }
    androidMain.dependencies {
      // 没课约仅通过旧 Android View 事务入口跳转。
      implementation(projects.cyxbsPages.affair.api)
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
    }
  }
}
