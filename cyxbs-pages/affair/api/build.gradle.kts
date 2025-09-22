plugins {
  id("manager.lib")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.config)
    }
    androidMain.dependencies {
      implementation(libs.rxjava)
    }
  }
}
