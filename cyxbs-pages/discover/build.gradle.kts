plugins {
  id("manager.lib")
  id("kmp.compose")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
      implementation(projects.cyxbsPages.todo.api)
      implementation(projects.cyxbsPages.sport.api)
      implementation(projects.cyxbsPages.volunteer.api)
      implementation(projects.cyxbsPages.electricity.api)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
      implementation(libs.bundles.views)
      implementation(libs.bundles.network)
      implementation(libs.glide)
      implementation(libs.eventBus)
      implementation(libs.slideShow)
    }
  }
}

