plugins {
  id("manager.lib")
  id("kmp.compose")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.config)
    }
  }
}