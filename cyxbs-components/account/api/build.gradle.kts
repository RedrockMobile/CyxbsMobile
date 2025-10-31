plugins {
  id("manager.lib")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
    }
  }
}
