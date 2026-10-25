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
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.schedule.api)
      implementation(projects.cyxbsPages.sport.api)
      implementation(projects.cyxbsPages.volunteer.api)
      implementation(projects.cyxbsPages.electricity.api)
      implementation(projects.cyxbsPages.notification.api)
      implementation(projects.cyxbsPages.map.api)
      implementation(projects.cyxbsPages.emptyroom.api)
      implementation(projects.cyxbsPages.schoolcar.api)
      implementation(projects.cyxbsPages.course.api)
      implementation(projects.cyxbsPages.home.api)
      implementation(projects.cyxbsPages.noclass.api)
      // 校历页直接通过 ImageRequest.Builder 配置图片缓存身份。
      implementation(libs.coil.compose)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
