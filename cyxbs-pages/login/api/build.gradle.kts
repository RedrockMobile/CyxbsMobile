plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(libs.compose.navigation)
    }
    androidMain.dependencies {
      implementation(libs.kotlinx.coroutines)
    }
  }
}

