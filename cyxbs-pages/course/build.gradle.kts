plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useNavigation() // navigation 跳转
useRoom(rxjava = true)

kotlin {
  sourceSets {
    commonMain.dependencies {
      subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.map.api)
      implementation(projects.cyxbsPages.schedule.api) // 课表通过 API 工厂注册 Schedule PageDecoration
      implementation(projects.cyxbsPages.widget.api) // 仅顶层课表适配并发布 Widget 快照
    }
    androidMain.dependencies {
      // 仅供设置页回退后的旧 Android View 课表事务链路使用。
      implementation(projects.cyxbsPages.affair.api)
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.slideShow)
    }
  }
}
