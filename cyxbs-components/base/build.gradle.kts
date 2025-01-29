plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.login.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.bundles.network)
      implementation(libs.photoView)
      implementation(libs.slideShow)
      implementation(libs.glide)
    }
  }
}
