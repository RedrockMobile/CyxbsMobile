plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    androidMain.dependencies {
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.lifecycle.process)
    }
  }
}


