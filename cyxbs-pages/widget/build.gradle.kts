plugins {
  id("manager.lib")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useRoom(rxjava = true)

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.affair.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)

      // 985892345 写的桌面小组件 https://github.com/985892345/CQUPTCourseWidget
      // 目前只实现了单个透明的小组件，后续没精力维护了，让学弟重构吧
      implementation("io.github.985892345:course-widget:0.0.1-alpha06-SNAPSHOT")
    }
  }
}


