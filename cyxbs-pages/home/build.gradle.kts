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
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.notification.api)
      implementation(projects.cyxbsPages.map.api)
      implementation(projects.cyxbsPages.emptyroom.api)
      implementation(projects.cyxbsPages.schoolcar.api)
    }
    androidMain.dependencies {
      // 旧首页 View 课表仍需读取 Android 事务服务。
      implementation(projects.cyxbsPages.affair.api)
      implementation(projects.cyxbsPages.course.widget)
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
    }
  }
}
