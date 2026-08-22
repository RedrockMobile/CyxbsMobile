plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation()
useNpmJsHost()

kotlin {
  sourceSets {
    noWebMain.dependencies {
      implementation(projects.cyxbsFunctions.code.npm)
      implementation(projects.cyxbsFunctions.code.npm.serviceTest.jsBridge)
    }
  }
}
