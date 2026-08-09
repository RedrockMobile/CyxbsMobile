plugins {
  id("manager.npmJs")
}

version = "0.1.0"

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsFunctions.code.language.jsBridge)
      implementation(projects.cyxbsFunctions.code.language.lezer)
    }
    jsMain.dependencies {
      implementation(npm("@lezer/javascript", "1.5.4"))
    }
    jsTest.dependencies {
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
