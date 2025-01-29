plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
    }
  }
}


