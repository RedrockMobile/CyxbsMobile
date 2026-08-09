plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation()
useKtProvider()

kotlin {
  sourceSets {
    noWebMain.dependencies {
      implementation(projects.cyxbsFunctions.code.npm)
    }
  }
}
