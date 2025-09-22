plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.navigation)
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
    }
  }
}


