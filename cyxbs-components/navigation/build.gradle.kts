plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider(isNeedKsp = false)

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.login.api)

      api(libs.compose.navigation3)
      implementation(libs.compose.adaptive)
      implementation(libs.compose.adaptive.layout)
      implementation(libs.compose.adaptive.navigation3)
      implementation(libs.compose.lifecycle.viewmodel.navigation3)
    }
  }
}
