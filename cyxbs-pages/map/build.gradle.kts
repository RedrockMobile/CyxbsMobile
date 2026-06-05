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
      implementation(libs.okio)
      implementation(libs.filekit.core)
      implementation(libs.filekit.dialogs.compose)
      implementation(projects.cyxbsComponents.account.api)
    }
noWebMain.dependencies {
      implementation(libs.scale.image.viewer)
      implementation(libs.scale.sampling.decoder)
    }
  }
}
