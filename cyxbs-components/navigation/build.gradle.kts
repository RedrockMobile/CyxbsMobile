plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider(isNeedKsp = false)

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.account.api)

      api(libs.compose.navigation3)
      implementation(libs.compose.adaptive)
      implementation(libs.compose.adaptive.layout)
      api(libs.compose.adaptive.navigation3)
      implementation(libs.compose.lifecycle.viewmodel.navigation3)

      // 因为不能反向依赖 config，所以单独依赖 ktProvider.manager
      implementation(libs.kmp.ktProvider.manager)
    }
  }
}
