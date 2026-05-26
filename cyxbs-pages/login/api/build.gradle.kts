plugins {
  id("manager.lib")
}

useNavigation()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
    }
  }
}

