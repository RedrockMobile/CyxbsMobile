plugins {
  id("manager.npmJs")
}

version = "0.1.0"

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.cyxbsFunctions.code.language.jsBridge)
    }
    jsMain.dependencies {
      implementation(npm("@lezer/highlight", "1.2.3"))
    }
  }
}
