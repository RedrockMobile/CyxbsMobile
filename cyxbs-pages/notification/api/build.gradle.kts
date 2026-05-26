plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation() // navigation 跳转

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.config)
    }
  }
}