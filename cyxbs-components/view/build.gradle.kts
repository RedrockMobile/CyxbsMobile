plugins {
  id("manager.lib")
  id("kmp.compose")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
    }
    androidMain.dependencies {
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.constraintlayout)
      implementation(libs.material)
    }
  }
}



